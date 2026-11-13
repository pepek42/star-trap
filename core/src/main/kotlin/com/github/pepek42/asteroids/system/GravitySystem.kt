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
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.healthMapper
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.transformMapper
import com.github.pepek42.asteroids.physics.mergeSources
import com.badlogic.gdx.physics.box2d.CircleShape
import ktx.ashley.addComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.photonMapper
import com.github.pepek42.asteroids.component.relativisticMapper
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.physics.PHOTON_SUBSTEPS
import com.github.pepek42.asteroids.physics.TIDAL_DPS
import com.github.pepek42.asteroids.physics.TIDAL_MIN_R
import com.github.pepek42.asteroids.physics.TIDAL_TOLERANCE_ENGINES
import com.github.pepek42.asteroids.physics.tidalAccel
import com.github.pepek42.asteroids.physics.PHOTON_SUBSTEP_RADIUS_PER_RS
import com.github.pepek42.asteroids.physics.PLAYER_PULL_SOFTENING
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SOURCE_PULL_MIN_SOFTENING
import com.github.pepek42.asteroids.physics.bendPhoton
import com.github.pepek42.asteroids.physics.gravTimeFactor
import com.github.pepek42.asteroids.physics.gravityAccelTau
import com.github.pepek42.asteroids.physics.newtonAccel
import ktx.ashley.allOf
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

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
 * [afterStep] also runs the black-hole horizon (bodies inside `rs` are removed, the player's health goes to 0) and merges
 * holes that touch (ST-085) and eats planets whose centre is within `rs + 0.5 radius` of a hole (ST-093), which grows the hole
 * by the planet's `rs` with momentum conserved (ST-200); these run even with `gravityEnabled` off.
 *
 * Each relativistic body also gets `RelativisticComponent.gravFactor` (ST-086, [gravTimeFactor] over all sources, 1 without
 * gravity or sources); the player's own pull does not count.
 *
 * [afterStep] ends with the tidal stress of ST-153 ([applyTidal]): `tidalRatio` and the hull damage, gated by
 * `RelativitySettings.tidalEnabled` only (it also works with `gravityEnabled` off, so a held body can be tested).
 *
 * Pull does nothing with `gravityEnabled` off or without sources. Must be registered before `RelativisticDynamics`.
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
    private val mergeOut = FloatArray(MERGE_OUT_SIZE)
    private var engine: Engine? = null

    override fun addedToEngine(engine: Engine) {
        this.engine = engine
        sources = engine.getEntitiesFor(allOf(GravitySourceComponent::class, BodyComponent::class).get())
        bodies = engine.getEntitiesFor(
            allOf(BodyComponent::class, BaseInfoComponent::class).exclude(GravitySourceComponent::class.java).get()
        )
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) {
        if (!settings.gravityEnabled || sources.size() == 0) {
            resetGravFactors()
            return
        }
        val c = settings.c
        pullSources()
        for (i in 0 until bodies.size()) pullBody(bodies[i], c, dt)
    }

    /** No gravity: every clock ticks at the arena rate again. */
    private fun resetGravFactors() {
        for (i in 0 until bodies.size()) {
            val rel = relativisticMapper.get(bodies[i]) ?: continue
            rel.gravFactor = 1f
        }
    }

    /**
     * `prod gravTimeFactor(r, rs)` over the live sources at [x], [y] (ST-086): the clock rate of a body there. The product
     * is an APPROXIMATION for several sources (the exact factor uses the summed potential), exact for one.
     */
    private fun gravClockFactor(x: Float, y: Float, c: Float): Float {
        var factor = 1f
        for (i in 0 until sources.size()) {
            if (removeMapper.has(sources[i])) continue
            val sp = sources[i].bodyCmp.body.position
            val dx = x - sp.x
            val dy = y - sp.y
            factor *= gravTimeFactor(sqrt(dx * dx + dy * dy), sources[i].gravitySourceCmp.effectiveRs(c))
        }
        return factor
    }

    override fun afterStep(dt: Float) {
        if (sources.size() > 0) {
            mergeHoles()
            absorbBodies()
            eatPlanets()
        }
        applyTidal(dt)
    }

    /**
     * Tidal stress (ST-153, research E19) on every live relativistic body with a hull size: the largest `tidalAccel` over
     * the live sources across `2 * boundingRadius`, as a ratio of `engineProperAccel * TIDAL_TOLERANCE_ENGINES`, stored in
     * `RelativisticComponent.tidalRatio` with its direction. Above 1 a body with health loses
     * `TIDAL_DPS * (ratio - 1) * dtau` (`dtau = dt * gravFactor / gUsed`). With `tidalEnabled` off or no sources the ratio is 0.
     */
    private fun applyTidal(dt: Float) {
        val c = settings.c
        val threshold = settings.engineProperAccel * TIDAL_TOLERANCE_ENGINES
        val active = settings.tidalEnabled && sources.size() > 0 && threshold > 0f
        for (i in 0 until bodies.size()) {
            val e = bodies[i]
            val rel = relativisticMapper.get(e) ?: continue
            rel.tidalRatio = 0f
            if (!active || removeMapper.has(e)) continue
            val bodyCmp = e.bodyCmp
            if (bodyCmp.boundingRadius < 0f) bodyCmp.boundingRadius = computeBoundingRadius(bodyCmp.body)
            if (bodyCmp.boundingRadius <= 0f) continue
            val length = 2f * bodyCmp.boundingRadius
            val pos = bodyCmp.body.position
            var best = 0f
            for (k in 0 until sources.size()) {
                if (removeMapper.has(sources[k])) continue
                val sp = sources[k].bodyCmp.body.position
                val dx = pos.x - sp.x
                val dy = pos.y - sp.y
                val r = sqrt(dx * dx + dy * dy)
                val da = tidalAccel(r, sources[k].gravitySourceCmp.effectiveGm(c), length)
                if (da <= best) continue
                best = da
                if (r > TIDAL_MIN_R) {
                    rel.tidalDirX = dx / r
                    rel.tidalDirY = dy / r
                } else {
                    rel.tidalDirX = 1f
                    rel.tidalDirY = 0f
                }
            }
            val ratio = best / threshold
            rel.tidalRatio = ratio
            if (ratio <= 1f) continue
            val health = healthMapper.get(e) ?: continue
            if (health.health > 0f) health.health -= TIDAL_DPS * (ratio - 1f) * dt * rel.gravFactor / rel.gUsed
        }
    }

    /**
     * Two black holes with centre distance `< rs1 + rs2` merge: the one with the lower id survives with `rs = rs1 + rs2` at the
     * centre of mass with the merged velocity ([mergeSources] with `rs` as the mass), the other gets a `RemoveComponent`.
     */
    private fun mergeHoles() {
        for (i in 0 until sources.size()) {
            val a = sources[i]
            if (!isLiveHole(a)) continue
            for (j in i + 1 until sources.size()) {
                val b = sources[j]
                if (!isLiveHole(a) || !isLiveHole(b)) continue
                val pa = a.bodyCmp.body.position
                val pb = b.bodyCmp.body.position
                val reach = a.gravitySourceCmp.rs + b.gravitySourceCmp.rs
                val dx = pa.x - pb.x
                val dy = pa.y - pb.y
                if (dx * dx + dy * dy >= reach * reach) continue
                if (a.baseInfoCmp.id < b.baseInfoCmp.id) mergeInto(a, b) else mergeInto(b, a)
            }
        }
    }

    private fun isLiveHole(e: Entity): Boolean =
        e.gravitySourceCmp.kind == GravitySourceKind.BLACK_HOLE && !removeMapper.has(e)

    private fun mergeInto(survivor: Entity, gone: Entity) {
        val sb = survivor.bodyCmp.body
        val gb = gone.bodyCmp.body
        val sg = survivor.gravitySourceCmp
        val gg = gone.gravitySourceCmp
        mergeSources(
            sg.rs, sb.position.x, sb.position.y, sb.linearVelocity.x, sb.linearVelocity.y,
            gg.rs, gb.position.x, gb.position.y, gb.linearVelocity.x, gb.linearVelocity.y,
            mergeOut,
        )
        sg.rs = mergeOut[0]
        sb.setTransform(mergeOut[1], mergeOut[2], 0f)
        sb.setLinearVelocity(mergeOut[3], mergeOut[4])
        resizeHoleFixture(sb, sg.rs)
        survivorTransformReset(survivor)
        gone.addComponent<RemoveComponent>(engine!!)
    }

    /** Rebuilds a hole's sensor disc with radius [rs] (no collisions, the horizon is drawn and tested by distance). */
    private fun resizeHoleFixture(body: Body, rs: Float) {
        while (body.fixtureList.size > 0) body.destroyFixture(body.fixtureList[0])
        val shape = CircleShape().apply { radius = rs }
        val fixture = body.createFixture(shape, 1f)
        shape.dispose()
        fixture.isSensor = true
        fixture.filterData = fixture.filterData.apply {
            categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
            maskBits = 0
        }
    }

    /** Keeps the render interpolation from smearing the hole from its old position to the centre of mass. */
    private fun survivorTransformReset(e: Entity) {
        val p = e.bodyCmp.body.position
        transformMapper.get(e)?.let {
            it.prevPosition.set(p)
            it.interpolatedPosition.set(p)
        }
    }

    /** A planet whose centre is within `rs + 0.5 * radius` of a live hole is absorbed by it ([absorbPlanet]) and removed. */
    private fun eatPlanets() {
        for (i in 0 until sources.size()) {
            val hole = sources[i]
            if (!isLiveHole(hole)) continue
            val hp = hole.bodyCmp.body.position
            for (k in 0 until sources.size()) {
                val planet = sources[k]
                val src = planet.gravitySourceCmp
                if (src.kind != GravitySourceKind.PLANET || removeMapper.has(planet)) continue
                val pp = planet.bodyCmp.body.position
                val reach = hole.gravitySourceCmp.rs + PLANET_EATEN_RADIUS_FRACTION * src.radius
                val dx = pp.x - hp.x
                val dy = pp.y - hp.y
                if (dx * dx + dy * dy < reach * reach) {
                    absorbPlanet(hole, planet)
                    planet.addComponent<RemoveComponent>(engine!!)
                }
            }
        }
    }

    /**
     * The hole grows by the planet's Schwarzschild radius `2 GM / c^2` (ST-200): [mergeSources] with `rs` as the mass, so
     * momentum is conserved and the hole takes the merged velocity. It keeps its position, since the planet is within
     * `rs + 0.5 R` of it.
     */
    private fun absorbPlanet(hole: Entity, planet: Entity) {
        val c = settings.c
        val hb = hole.bodyCmp.body
        val pb = planet.bodyCmp.body
        val hg = hole.gravitySourceCmp
        mergeSources(
            hg.rs, hb.position.x, hb.position.y, hb.linearVelocity.x, hb.linearVelocity.y,
            planet.gravitySourceCmp.effectiveRs(c), pb.position.x, pb.position.y, pb.linearVelocity.x, pb.linearVelocity.y,
            mergeOut,
        )
        hg.rs = mergeOut[0]
        hb.setLinearVelocity(mergeOut[3], mergeOut[4])
        resizeHoleFixture(hb, hg.rs)
    }

    /** Any non-source body whose centre is inside a hole's horizon dies: the player's health goes to 0, the rest get a `RemoveComponent`. */
    private fun absorbBodies() {
        for (i in 0 until sources.size()) {
            val hole = sources[i]
            if (!isLiveHole(hole)) continue
            val hp = hole.bodyCmp.body.position
            val rs = hole.gravitySourceCmp.rs
            for (k in 0 until bodies.size()) {
                val e = bodies[k]
                if (removeMapper.has(e)) continue
                val p = e.bodyCmp.body.position
                val dx = p.x - hp.x
                val dy = p.y - hp.y
                if (dx * dx + dy * dy >= rs * rs) continue
                if (playerMapper.has(e)) {
                    healthMapper.get(e)?.let { if (it.health != 0f) it.health = 0f }
                } else {
                    e.addComponent<RemoveComponent>(engine!!)
                }
            }
        }
    }

    /** n-body pull between the sources plus the player's pull on them, as Box2D forces. */
    private fun pullSources() {
        val c = settings.c
        val coupling = settings.sourceCoupling
        val player = if (players.size() > 0) players[0].bodyCmp.body.position else null
        for (i in 0 until sources.size()) {
            if (removeMapper.has(sources[i])) continue
            val si = sources[i].gravitySourceCmp
            val bi = sources[i].bodyCmp.body
            if (bi.type != BodyDef.BodyType.DynamicBody) continue
            val pi = bi.position
            var ax = 0f
            var ay = 0f
            if (coupling > 0f) {
                for (j in 0 until sources.size()) {
                    if (j == i || removeMapper.has(sources[j])) continue
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
        // Clock rate from the potential; with relativity off the clocks stay on arena time.
        relativisticMapper.get(entity)?.gravFactor = if (rel != null) gravClockFactor(pos.x, pos.y, c) else 1f
        accel.setZero()
        for (i in 0 until sources.size()) {
            if (removeMapper.has(sources[i])) continue
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
                if (removeMapper.has(sources[i])) continue
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
            if (src.kind != GravitySourceKind.BLACK_HOLE || removeMapper.has(sources[i])) continue
            val sp = sources[i].bodyCmp.body.position
            val limit = PHOTON_SUBSTEP_RADIUS_PER_RS * src.rs
            val dx = x - sp.x
            val dy = y - sp.y
            if (dx * dx + dy * dy < limit * limit) return true
        }
        return false
    }

    private companion object {
        /** Size of the `[m, x, y, vx, vy]` result of [mergeSources]. */
        const val MERGE_OUT_SIZE = 5

        /** A planet is eaten once its centre is within `rs` plus this fraction of its radius of a hole. */
        const val PLANET_EATEN_RADIUS_FRACTION = 0.5f
    }
}
