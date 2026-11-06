package com.github.pepek42.asteroids.input

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.RetardedState
import com.github.pepek42.asteroids.physics.leadDirection
import com.github.pepek42.asteroids.physics.retarded
import com.github.pepek42.asteroids.system.LightHistorySystem
import kotlin.math.atan2

/**
 * Scheme (c): movement as [MouseHullStrategy] (the mouse steers the hull, RMB thrusts), but the guns pick the nearest
 * target in a forward cone and fire on their own. LMB still fires along the hull when nothing is in the cone.
 *
 * Honest fire control: candidates are the retarded positions the pilot sees ([retarded] from the ship's position at
 * [GameClock.worldTime]), and the aim is [leadDirection] (extrapolated at constant velocity), never a true position.
 * [targets] holds every entity with health, a body and a faction; the player's faction is skipped here.
 */
class AutoAimStrategy(
    private val targets: ImmutableArray<Entity>,
    private val lightHistory: LightHistorySystem,
    private val observer: ObserverState,
    private val clock: GameClock,
    private val settings: RelativitySettings,
    private val coneHalfRad: Float = AUTO_AIM_CONE_HALF_RAD,
    private val range: Float = AUTO_AIM_RANGE,
) : ControlStrategy {
    private val movement = MouseHullStrategy()
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var states = Array(INITIAL_CAPACITY) { RetardedState() }
    private val scratch = RetardedState()
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
        val now = clock.worldTime
        val c = settings.c
        var n = 0
        for (k in 0 until targets.size()) {
            val entity = targets[k]
            if (entity.baseInfoCmp.faction == FactionEnum.PLAYER || removeMapper.get(entity) != null) continue
            val history = lightHistory.historyOf(entity.baseInfoCmp.id) ?: continue
            if (!history.retarded(shipPos.x, shipPos.y, now, c, scratch)) continue
            if (n == xs.size) grow()
            xs[n] = scratch.x
            ys[n] = scratch.y
            states[n].copyFrom(scratch)
            entities[n] = entity
            n++
        }
        val i = pickTargetIndex(shipPos.x, shipPos.y, shipAngleRad, coneHalfRad, range, xs, ys, n)
        if (i < 0) return
        val betaX = if (settings.relativityEnabled) observer.betaRaw.x else 0f
        val betaY = if (settings.relativityEnabled) observer.betaRaw.y else 0f
        if (!leadDirection(shipPos.x, shipPos.y, betaX, betaY, states[i], now, c, lead)) return
        target = entities[i]
        aimDir.set(lead)
        weapon.aimAngleRad = atan2(lead.y, lead.x)
        weapon.doAttack = true
    }

    private fun grow() {
        val newSize = xs.size * 2
        xs = xs.copyOf(newSize)
        ys = ys.copyOf(newSize)
        entities = entities.copyOf(newSize)
        states = Array(newSize) { if (it < states.size) states[it] else RetardedState() }
    }

    private fun RetardedState.copyFrom(o: RetardedState) {
        t = o.t
        x = o.x
        y = o.y
        angle = o.angle
        vx = o.vx
        vy = o.vy
    }

    private companion object {
        const val INITIAL_CAPACITY = 32
    }
}
