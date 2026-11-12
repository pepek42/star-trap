package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.healthMapper
import com.github.pepek42.asteroids.component.relativisticMapper
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH
import com.github.pepek42.asteroids.render.DISK_INNER
import com.github.pepek42.asteroids.render.DISK_OUTER
import com.github.pepek42.asteroids.render.diskAffects
import com.github.pepek42.asteroids.render.inDiskAnnulus
import ktx.ashley.allOf

/** Fraction of [PLAYER_START_HEALTH] that crossing the disk once costs (ST-175). */
const val DISK_CROSSING_COST_FRACTION = 0.25f

/** Reference speed of a crossing, as a fraction of c: `0.4 c` = 8 u/s at the default c. */
const val DISK_CROSSING_SPEED_PER_C = 0.4f

/**
 * Path of one reference crossing: over both halves of the annulus, `2 * (DISK_OUTER - DISK_INNER)` = 14.4 u at the default
 * hole (the disk scales with `r_s`, the damage rate does not).
 */
const val DISK_CROSSING_LENGTH = 2f * (DISK_OUTER - DISK_INNER)

/**
 * Health per second of proper time lost inside the disk annulus (APPROX stand-in for heating and impacts by disk gas,
 * physics-lies). Derived so that a crossing at [DISK_CROSSING_SPEED_PER_C] c takes `14.4 / 8` = 1.8 s and costs
 * [DISK_CROSSING_COST_FRACTION] of [PLAYER_START_HEALTH]: `25 / 1.8` = 13.9 per second.
 */
const val DISK_DAMAGE_PER_SECOND =
    DISK_CROSSING_COST_FRACTION * PLAYER_START_HEALTH / (DISK_CROSSING_LENGTH / (DISK_CROSSING_SPEED_PER_C * SPEED_OF_LIGHT))

/** Health lost by a body that spent [properDt] seconds of its own time inside the disk. */
fun diskDamage(properDt: Float): Float = DISK_DAMAGE_PER_SECOND * properDt

/**
 * The accretion disk hurts (ST-175): every step, a ship (player or enemy) or asteroid whose true position is inside the annulus
 * `[3 rs, DISK_OUTER_PER_RS rs]` of a live black hole loses [diskDamage] of its proper time `dt * gravFactor / γ` (like the tidal
 * stress). Projectiles, debris and the hole itself are ignored; no drag. Death stays with `DeathSystem`.
 *
 * Runs in `afterStep` (the position and γ of the step that just ended). Register it with the step listeners after
 * `GravitySystem`.
 */
class DiskDamageSystem : EntitySystem(), PhysicsStepListener {
    private var sources: ImmutableArray<Entity> = ImmutableArray(Array())
    private var bodies: ImmutableArray<Entity> = ImmutableArray(Array())

    override fun addedToEngine(engine: Engine) {
        sources = engine.getEntitiesFor(allOf(GravitySourceComponent::class, BodyComponent::class).get())
        bodies = engine.getEntitiesFor(
            allOf(BodyComponent::class, BaseInfoComponent::class, HealthComponent::class).exclude(GravitySourceComponent::class.java).get()
        )
    }

    override fun update(deltaTime: Float) = Unit

    override fun beforeStep(dt: Float) = Unit

    override fun afterStep(dt: Float) {
        for (i in 0 until sources.size()) {
            val hole = sources[i]
            if (hole.gravitySourceCmp.kind != GravitySourceKind.BLACK_HOLE || removeMapper.has(hole)) continue
            val hp = hole.bodyCmp.body.position
            val rs = hole.gravitySourceCmp.rs
            for (k in 0 until bodies.size()) {
                val e = bodies[k]
                if (removeMapper.has(e) || !diskAffects(e.baseInfoCmp.entityType)) continue
                val p = e.bodyCmp.body.position
                if (!inDiskAnnulus(p.x - hp.x, p.y - hp.y, rs)) continue
                val health = healthMapper.get(e)
                if (health.health <= 0f) continue
                val rel = relativisticMapper.get(e)
                val properDt = if (rel != null) dt * rel.gravFactor / rel.gUsed else dt
                health.health -= diskDamage(properDt)
            }
        }
    }
}
