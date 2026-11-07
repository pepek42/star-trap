package com.github.pepek42.asteroids.input

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.physics.trueLeadDirection
import kotlin.math.atan2

/**
 * Scheme (c): movement as [MouseHullStrategy] (the mouse steers the hull, RMB thrusts), but the guns pick the nearest
 * target in a forward cone and fire on their own. LMB still fires along the hull when nothing is in the cone.
 *
 * Computer fire control (owner 2026-11-07: all targeting uses the ship computer and the TRUE target position): candidates
 * are the true current arena positions, and the aim is [trueLeadDirection] from the true position and velocity, so a
 * constant-velocity target is hit. The pilot's light-delayed picture plays no part (physics-lies row 34: supercomputer).
 * Only [EntityType.ASTEROID] and enemy [EntityType.SHIP] entities are candidates (debris, planets and the rest are never locked, ST-142).
 */
class AutoAimStrategy(
    private val targets: ImmutableArray<Entity>,
    private val observer: ObserverState,
    private val clock: GameClock,
    private val settings: RelativitySettings,
    private val coneHalfRad: Float = AUTO_AIM_CONE_HALF_RAD,
    private val range: Float = AUTO_AIM_RANGE,
) : ControlStrategy {
    private val movement = MouseHullStrategy()
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private val lead = Vector2()
    private var entities = arrayOfNulls<Entity>(INITIAL_CAPACITY)

    /** The entity the guns currently aim at, or null (ST-133). Valid for the frame; do not keep it. */
    var target: Entity? = null
        private set

    /** Arena direction of the last lead shot, valid while [target] is not null. */
    val aimDir = Vector2()

    override fun apply(
        input: PlayerInputState,
        shipPos: Vector2,
        shipAngleRad: Float,
        move: MoveComponent,
        weapon: WeaponComponent?,
        dt: Float,
    ) {
        movement.apply(input, shipPos, shipAngleRad, move, weapon, dt)
        target = null
        if (weapon == null) return
        val c = settings.c
        var n = 0
        for (k in 0 until targets.size()) {
            val entity = targets[k]
            if (!isAutoAimTarget(entity) || removeMapper.get(entity) != null) continue
            val pos = entity.bodyCmp.body.position
            if (n == xs.size) grow()
            xs[n] = pos.x
            ys[n] = pos.y
            entities[n] = entity
            n++
        }
        val i = pickTargetIndex(shipPos.x, shipPos.y, shipAngleRad, coneHalfRad, range, xs, ys, n)
        if (i < 0) return
        val betaX = if (settings.relativityEnabled) observer.betaRaw.x else 0f
        val betaY = if (settings.relativityEnabled) observer.betaRaw.y else 0f
        val v = entities[i]!!.bodyCmp.body.linearVelocity
        if (trueLeadDirection(shipPos.x, shipPos.y, betaX, betaY, xs[i], ys[i], v.x, v.y, c, lead).isNaN()) return
        target = entities[i]
        aimDir.set(lead)
        weapon.aimAngleRad = atan2(lead.y, lead.x)
        weapon.doAttack = true
    }

    private fun isAutoAimTarget(entity: Entity): Boolean {
        val info = entity.baseInfoCmp
        return info.entityType == EntityType.ASTEROID ||
            (info.entityType == EntityType.SHIP && info.faction == FactionEnum.ENEMY)
    }

    private fun grow() {
        val newSize = xs.size * 2
        xs = xs.copyOf(newSize)
        ys = ys.copyOf(newSize)
        entities = entities.copyOf(newSize)
    }

    private companion object {
        const val INITIAL_CAPACITY = 32
    }
}
