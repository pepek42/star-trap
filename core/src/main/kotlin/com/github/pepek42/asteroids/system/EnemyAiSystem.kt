package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.ai.ENEMY_STANDOFF_LIGHT_SECONDS
import com.github.pepek42.asteroids.ai.ENEMY_VMAX_BETA
import com.github.pepek42.asteroids.ai.PilotCommand
import com.github.pepek42.asteroids.ai.pursuitCommand
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.shipHandlingCmpOptional
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.RetardedState
import com.github.pepek42.asteroids.physics.leadDirection
import com.github.pepek42.asteroids.physics.pilotHullToWorld
import com.github.pepek42.asteroids.physics.retarded
import com.github.pepek42.asteroids.physics.worldHullToPilot
import ktx.ashley.allOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Enemies open fire when the estimated player is within this many light-seconds: `range = ENEMY_FIRE_RANGE_LIGHT_SECONDS * c`. */
const val ENEMY_FIRE_RANGE_LIGHT_SECONDS = 2.5f

/** An enemy fires only when its hull is within this cone (15 degrees) of the lead direction. */
const val ENEMY_FIRE_CONE_RAD = 15f * Math.PI.toFloat() / 180f

/**
 * Enemy pilot (ST-156, layer: sim). Enemies hunt the player using only the light that has reached them (research E13):
 * the player's state is the retarded one from [LightHistorySystem] (`retarded` from the enemy's position at
 * [GameClock.worldTime]), extrapolated at constant velocity to now. It steers with `pursuitCommand` and fires along the
 * honest `leadDirection` once the hull points within [ENEMY_FIRE_CONE_RAD] of it. It never reads the player's true body
 * state, so a juking player dodges for real reasons. Runs right after `PlayerInputSystem`, before `MoveSystem`; it only
 * writes the same controls a player writes ([MoveComponent], [WeaponComponent]). No allocation per frame.
 */
class EnemyAiSystem(
    private val lightHistory: LightHistorySystem,
    private val clock: GameClock,
    private val settings: RelativitySettings,
) : IteratingSystem(allOf(EnemyComponent::class, MoveComponent::class, WeaponComponent::class, BodyComponent::class).get()) {
    private var players: ImmutableArray<Entity>? = null
    private var playerId = NO_PLAYER
    private val ret = RetardedState()
    private val cmd = PilotCommand()
    private val lead = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        val list = players
        playerId = if (list != null && list.size() > 0) list[0].baseInfoCmp.id else NO_PLAYER
        super.update(deltaTime)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val move = entity.moveCmp
        val weapon = entity.weaponCmp
        move.thrusters = 0f
        weapon.doAttack = false
        if (playerId == NO_PLAYER) return
        val history = lightHistory.historyOf(playerId) ?: return
        val body = entity.bodyCmp.body
        val c = settings.c
        val now = clock.worldTime
        val ex = body.position.x
        val ey = body.position.y
        if (!history.retarded(ex, ey, now, c, ret)) return

        // The player as the enemy's eyes see it, extrapolated at constant velocity to now.
        val age = now - ret.t
        val tx = ret.x + ret.vx * age
        val ty = ret.y + ret.vy * age

        val rel = entity.relativisticCmpOptional
        val vx = rel?.vSet?.x ?: body.linearVelocity.x
        val vy = rel?.vSet?.y ?: body.linearVelocity.y
        val factor = entity.shipHandlingCmpOptional?.engineFactor ?: 1f
        pursuitCommand(
            ex, ey, vx, vy, body.angle, tx, ty, ret.vx, ret.vy,
            ENEMY_STANDOFF_LIGHT_SECONDS * c, settings.engineProperAccel * factor, ENEMY_VMAX_BETA * c, cmd,
        )
        move.thrusters = cmd.thrust
        move.targetAngleRad = cmd.headingRad

        val betaX = if (settings.relativityEnabled) vx / c else 0f
        val betaY = if (settings.relativityEnabled) vy / c else 0f
        if (!leadDirection(ex, ey, betaX, betaY, ret, now, c, lead)) return
        if (hypot(tx - ex, ty - ey) > ENEMY_FIRE_RANGE_LIGHT_SECONDS * c) return
        // lead is the muzzle direction in the shooter frame; the hull angle is a W angle, so convert between them.
        val aim = atan2(lead.y, lead.x)
        if (cmd.aiming) move.targetAngleRad = pilotHullToWorld(aim, betaX, betaY)
        val hullShooter = worldHullToPilot(body.angle, betaX, betaY)
        if (abs(shortestAngleDiffRad(hullShooter, aim)) < ENEMY_FIRE_CONE_RAD) {
            weapon.aimAngleRad = aim
            weapon.doAttack = true
        }
    }

    private companion object {
        const val NO_PLAYER = -1
    }
}
