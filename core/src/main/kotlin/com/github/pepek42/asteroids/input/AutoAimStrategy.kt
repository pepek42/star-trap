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
import com.github.pepek42.asteroids.physics.pilotHullToWorld
import com.github.pepek42.asteroids.physics.trueLeadDirection
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import com.github.pepek42.asteroids.weapon.ROCKET_ACCEL_FACTOR
import com.github.pepek42.asteroids.weapon.ROCKET_BURN_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_INTERCEPT_MAX_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_LENGTH
import com.github.pepek42.asteroids.weapon.WeaponType
import com.github.pepek42.asteroids.weapon.rocketInterceptDir
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The auto-aim scheme (the default). The mouse steers the hull (it faces the cursor) and RMB thrusts along the hull.
 * The guns pick the nearest target in a forward cone and fire on their own. LMB still fires along the hull when
 * nothing is in the cone.
 *
 * In pilot view the cursor direction is an angle in the pilot's frame (P), where the hull is drawn at
 * `worldHullToPilot(body.angle)`. The body angle is a W angle, so the target is `pilotHullToWorld(θ, betaShip)`.
 *
 * Computer fire control (owner 2026-11-07: all targeting uses the ship computer and the TRUE target position): candidates
 * are the true current arena positions, and the aim is [trueLeadDirection] from the true position and velocity, so a
 * constant-velocity target is hit. With the pod rocket selected (ST-182) the aim is instead [rocketInterceptDir], which
 * solves the intercept for the rocket's own accelerating flight; the guns only open fire when that solve finds a hit
 * within [ROCKET_INTERCEPT_MAX_SECONDS]. The pilot's light-delayed picture plays no part (physics-lies row 34:
 * supercomputer). Only [EntityType.ASTEROID] and enemy [EntityType.SHIP] entities are candidates (debris, planets and
 * the rest are never locked, ST-142).
 *
 * A null [targets] list (no light history or clock in the engine) gives movement only: no target is ever picked.
 */
class AutoAimStrategy(
    private val targets: ImmutableArray<Entity>?,
    private val observer: ObserverState,
    private val clock: GameClock?,
    private val settings: RelativitySettings,
    private val coneHalfRad: Float = AUTO_AIM_CONE_HALF_RAD,
    private val range: Float = AUTO_AIM_RANGE,
) : ControlStrategy {
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private val lead = Vector2()
    private val shipU = Vector2()
    private var entities = arrayOfNulls<Entity>(INITIAL_CAPACITY)

    /** The entity the guns currently aim at, or null (ST-133). Valid for the frame; do not keep it. */
    var target: Entity? = null
        private set

    /** Drops the current target; [apply] picks a new one. */
    fun clearTarget() {
        target = null
    }

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
        steerHull(input, shipPos, move, weapon)
        target = null
        if (weapon == null) return
        val list = targets ?: return
        val c = settings.c
        var n = 0
        for (k in 0 until list.size()) {
            val entity = list[k]
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
        val targetVelocity = entities[i]!!.bodyCmp.body.linearVelocity
        val hitTime = if (weapon.weaponType == WeaponType.ROCKET) {
            rocketAim(shipPos, shipAngleRad, weapon, xs[i], ys[i], targetVelocity.x, targetVelocity.y)
        } else {
            trueLeadDirection(
                shipPos.x, shipPos.y, betaX, betaY, xs[i], ys[i], targetVelocity.x, targetVelocity.y, c, lead,
            )
        }
        if (hitTime.isNaN()) return
        target = entities[i]
        aimDir.set(lead)
        weapon.aimAngleRad = atan2(lead.y, lead.x)
        weapon.doAttack = true
    }

    /**
     * Pod-rocket intercept (ST-182), launched where `RocketProvider` really puts the rocket: the solver starts at the
     * muzzle, then the aim is solved again from `muzzle + dir·ROCKET_LENGTH` (the provider's spawn offset). The offset is
     * small, but at short range or against a fast target it is a measurable part of the lead. The rocket inherits the
     * ship's proper velocity, rebuilt from [ObserverState.betaRaw] the way the provider takes it from the ship's
     * `RelativisticComponent`. Returns the hit time, or NaN when the rocket cannot reach the target in time.
     */
    private fun rocketAim(
        shipPos: Vector2,
        shipAngleRad: Float,
        weapon: WeaponComponent,
        tx: Float,
        ty: Float,
        tvx: Float,
        tvy: Float,
    ): Float {
        val c = settings.c
        val betaX = observer.betaRaw.x
        val betaY = observer.betaRaw.y
        uFromV(betaX * c, betaY * c, c, settings.betaMaxMassive, shipU)
        val w = weapon.weaponPosition
        val cosA = cos(shipAngleRad)
        val sinA = sin(shipAngleRad)
        val muzzleX = shipPos.x + w.x * cosA - w.y * sinA
        val muzzleY = shipPos.y + w.x * sinA + w.y * cosA
        val accel = ROCKET_ACCEL_FACTOR * settings.engineProperAccel
        var hitTime = rocketInterceptDir(
            muzzleX, muzzleY, shipU.x, shipU.y, tx, ty, tvx, tvy,
            accel, ROCKET_BURN_SECONDS, c, ROCKET_INTERCEPT_MAX_SECONDS, PHYSICS_UPDATE_INTERVAL, lead,
        )
        if (hitTime.isNaN()) return Float.NaN
        hitTime = rocketInterceptDir(
            muzzleX + lead.x * ROCKET_LENGTH,
            muzzleY + lead.y * ROCKET_LENGTH,
            shipU.x, shipU.y, tx, ty, tvx, tvy,
            accel, ROCKET_BURN_SECONDS, c, ROCKET_INTERCEPT_MAX_SECONDS, PHYSICS_UPDATE_INTERVAL, lead,
        )
        return hitTime
    }

    /** The hull faces the cursor, RMB thrusts, LMB fires along the hull (until the guns pick a target). */
    private fun steerHull(input: PlayerInputState, shipPos: Vector2, move: MoveComponent, weapon: WeaponComponent?) {
        move.thrusters = input.thrustHeld
        val cursorAngle = Math.atan2(
            (input.aimWorld.y - shipPos.y).toDouble(),
            (input.aimWorld.x - shipPos.x).toDouble(),
        ).toFloat()
        move.targetAngleRad = if (input.pilotView) {
            pilotHullToWorld(cursorAngle, input.betaShip.x, input.betaShip.y)
        } else {
            cursorAngle
        }
        weapon?.let {
            it.doAttack = input.fireHeld
            it.aimAngleRad = null
        }
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
