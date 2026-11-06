package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.photonMapper
import com.github.pepek42.asteroids.component.relativisticMapper
import com.github.pepek42.asteroids.physics.PHOTON_SUBSTEPS
import com.github.pepek42.asteroids.physics.PHOTON_SUBSTEP_RADIUS_PER_RS
import com.github.pepek42.asteroids.physics.PLAYER_PULL_SOFTENING
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SOURCE_PULL_MIN_SOFTENING
import com.github.pepek42.asteroids.physics.bendPhoton
import com.github.pepek42.asteroids.physics.gravityAccelTau
import com.github.pepek42.asteroids.physics.newtonAccel
import ktx.ashley.allOf
import kotlin.math.atan2
import kotlin.math.max

/**
 * Gravity in the per-step physics (research E14, ST-083). Every step, before `RelativisticDynamics`:
 *
 * - **Sources** ([GravitySourceComponent] on dynamic bodies) pull each other with `settings.sourceCoupling` times the
 *   Newtonian law and feel the player's pull (`settings.playerGm`), as Box2D forces. Asteroids, debris and photons
 *   don't pull anything.
 * - **Relativistic bodies** (relativity on) get the proper acceleration [gravityAccelTau] (black holes, with the
 *   strong-field term) or [newtonAccel] (planets) divided by their last gamma added to `RelativisticComponent.accel`.
 * - **Other dynamic bodies** (or relativity off) get a Box2D force, kinematic non-photon projectiles a velocity kick.
 * - **Photons** are bent by [bendPhoton] per source and flown at exactly c along the new direction; within
 *   `PHOTON_SUBSTEP_RADIUS_PER_RS * r_s` of a black hole the bend is split into `PHOTON_SUBSTEPS` substeps and the body
 *   flies the chord of that path (the last direction would overshoot the bend by about 8 % at b = 3 `r_s`).
 *
 * Does nothing with `gravityEnabled` off or without sources. Must be registered before `RelativisticDynamics`.
 */
class GravitySystem(
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), PhysicsStepListener {
    private var sources: ImmutableArray<Entity> = ImmutableArray(Array())
    private var bodies: ImmutableArray<Entity> = ImmutableArray(Array())
    private var players: ImmutableArray<Entity> = ImmutableArray(Array())
    private val accel = Vector2()
    private val tmp = Vector2()
    private val dir = Vector2()

    override fun addedToEngine(engine: Engine) {
        sources = engine.getEntitiesFor(allOf(GravitySourceComponent::class, BodyComponent::class).get())
        bodies = engine.getEntitiesFor(
            allOf(BodyComponent::class, BaseInfoComponent::class).exclude(GravitySourceComponent::class.java).get()
        )
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) {
        if (!settings.gravityEnabled || sources.size() == 0) return
        val c = settings.c
        pullSources()
        for (i in 0 until bodies.size()) pullBody(bodies[i], c, dt)
    }

    override fun afterStep(dt: Float) = Unit

    /** n-body pull between the sources plus the player's pull on them, as Box2D forces. */
    private fun pullSources() {
        val c = settings.c
        val coupling = settings.sourceCoupling
        val player = if (players.size() > 0) players[0].bodyCmp.body.position else null
        for (i in 0 until sources.size()) {
            val si = sources[i].gravitySourceCmp
            val bi = sources[i].bodyCmp.body
            if (bi.type != BodyDef.BodyType.DynamicBody) continue
            val pi = bi.position
            var ax = 0f
            var ay = 0f
            if (coupling > 0f) {
                for (j in 0 until sources.size()) {
                    if (j == i) continue
                    val sj = sources[j].gravitySourceCmp
                    val pj = sources[j].bodyCmp.body.position
                    val soft = max(si.radius + sj.radius, SOURCE_PULL_MIN_SOFTENING)
                    newtonAccel(pi.x - pj.x, pi.y - pj.y, sj.effectiveGm(c), soft, tmp)
                    ax += coupling * tmp.x
                    ay += coupling * tmp.y
                }
            }
            if (player != null && settings.playerGm > 0f) {
                newtonAccel(pi.x - player.x, pi.y - player.y, settings.playerGm, PLAYER_PULL_SOFTENING, tmp)
                ax += tmp.x
                ay += tmp.y
            }
            val mass = bi.mass
            bi.applyForceToCenter(ax * mass, ay * mass, true)
        }
    }

    private fun pullBody(entity: Entity, c: Float, dt: Float) {
        val body = entity.bodyCmp.body
        if (body.type == BodyDef.BodyType.StaticBody) return
        val photon = photonMapper.get(entity)
        if (photon != null) {
            bendPhotonBody(body, photon.dir, c, dt)
            return
        }
        val rel = if (settings.relativityEnabled) relativisticMapper.get(entity) else null
        val pos = body.position
        accel.setZero()
        for (i in 0 until sources.size()) {
            val src = sources[i].gravitySourceCmp
            val sp = sources[i].bodyCmp.body.position
            val gm = src.effectiveGm(c)
            val rx = pos.x - sp.x
            val ry = pos.y - sp.y
            val soft = bodySoftening(src)
            if (rel != null && src.kind == GravitySourceKind.BLACK_HOLE) {
                gravityAccelTau(rx, ry, rel.u.x, rel.u.y, gm, c, soft, tmp)
            } else {
                newtonAccel(rx, ry, gm, soft, tmp)
            }
            accel.add(tmp)
        }
        if (rel != null) {
            // du/dt = (du/dtau) / gamma.
            rel.accel.mulAdd(accel, 1f / rel.gUsed)
        } else if (body.type == BodyDef.BodyType.DynamicBody) {
            val mass = body.mass
            body.applyForceToCenter(accel.x * mass, accel.y * mass, true)
        } else {
            tmp.set(body.linearVelocity).mulAdd(accel, dt)
            body.setLinearVelocity(tmp)
        }
    }

    /** Softening of a source's pull on a test body: the source's own, at least the surface radius for a planet. */
    private fun bodySoftening(src: GravitySourceComponent): Float =
        if (src.kind == GravitySourceKind.PLANET) max(src.radius, src.softening) else src.softening

    /**
     * Bends [photonDir] (a unit vector, updated in place) around every source and gives the body velocity `c * dir`
     * and the matching angle. Near a hole the path is integrated in substeps, advancing a local position along the way.
     */
    private fun bendPhotonBody(body: Body, photonDir: Vector2, c: Float, dt: Float) {
        val pos = body.position
        val startX = pos.x
        val startY = pos.y
        var px = startX
        var py = startY
        val steps = if (nearHole(px, py)) PHOTON_SUBSTEPS else 1
        val subDt = dt / steps
        dir.set(photonDir)
        repeat(steps) {
            for (i in 0 until sources.size()) {
                val src = sources[i].gravitySourceCmp
                val sp = sources[i].bodyCmp.body.position
                bendPhoton(px - sp.x, py - sp.y, dir.x, dir.y, src.effectiveGm(c), c, subDt, dir)
            }
            px += c * dir.x * subDt
            py += c * dir.y * subDt
        }
        photonDir.set(dir)
        // Fly the chord of the substepped path, so the Box2D step ends where the substeps did.
        tmp.set(px - startX, py - startY)
        if (steps > 1 && tmp.len2() > 0f) tmp.nor().scl(c) else tmp.set(dir).scl(c)
        body.setLinearVelocity(tmp)
        val angle = atan2(dir.y, dir.x)
        if (angle != body.angle) body.setTransform(body.position, angle)
    }

    /** True within `PHOTON_SUBSTEP_RADIUS_PER_RS * r_s` of any black hole. */
    private fun nearHole(x: Float, y: Float): Boolean {
        for (i in 0 until sources.size()) {
            val src = sources[i].gravitySourceCmp
            if (src.kind != GravitySourceKind.BLACK_HOLE) continue
            val sp = sources[i].bodyCmp.body.position
            val limit = PHOTON_SUBSTEP_RADIUS_PER_RS * src.rs
            val dx = x - sp.x
            val dy = y - sp.y
            if (dx * dx + dy * dy < limit * limit) return true
        }
        return false
    }
}
