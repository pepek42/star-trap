package com.github.pepek42.asteroids.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.audio.ComputerSoundQueue
import com.github.pepek42.asteroids.audio.HullSoundQueue
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.LockOnProvider
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH
import com.github.pepek42.asteroids.provider.RocketProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.BoundaryFieldSystem
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.DiskDamageSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import com.github.pepek42.asteroids.system.EnemyAiSystem
import com.github.pepek42.asteroids.system.GravitySystem
import com.github.pepek42.asteroids.system.LifetimeSystem
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.LockOnSteeringSystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.ObserverSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.ProjectileSweepSystem
import com.github.pepek42.asteroids.system.RelativisticDynamics
import com.github.pepek42.asteroids.system.RemoveSystem
import com.github.pepek42.asteroids.system.RocketMotorSystem
import com.github.pepek42.asteroids.system.TimeWarpSystem
import com.github.pepek42.asteroids.system.WeaponSystem

/** Everything [buildGameplaySystems] needs. The headless balance runner and `PlayScreen` fill it the same way. */
class GameplayDeps(
    val engine: Engine,
    val world: World,
    val settings: RelativitySettings,
    val mapProvider: MapProvider,
    val clock: GameClock,
    val observerState: ObserverState,
    val gameEventManager: GameEventManager,
    val camera: Camera,
    val controlSettings: ControlSettings,
    val weaponProjectileProvider: WeaponProjectileProvider,
    /** Lock-on shot bodies (ST-164); null in tests and harness runs that never fire slot 2. */
    val lockOnProvider: LockOnProvider? = null,
    /** Rocket bodies (ST-181); null in tests and harness runs that never fire slot 3. */
    val rocketProvider: RocketProvider? = null,
    val asteroidProvider: AsteroidProvider,
    val random: RandomXS128,
    /** Shared by the boundary field (emits) and the shimmer renderer (draws). */
    val shimmerImpacts: ShimmerImpacts,
    val onLevelCleared: () -> Unit,
    val onPlayerDeath: () -> Unit,
    /** Hull sound events (ST-135); null in the headless balance runner, which must not touch audio. */
    val hullSounds: HullSoundQueue? = null,
    /** Events for the ship's computer to sonify when their light arrives (ST-136); null in the headless balance runner. */
    val computerSounds: ComputerSoundQueue? = null,
    /** Harness flag `--invulnerable`: the player never dies. */
    val invulnerablePlayer: Boolean = false,
    /** Health `--invulnerable` heals the player to (the start health). */
    val invulnerableHealth: () -> Float = { PLAYER_START_HEALTH },
)

/**
 * Systems that stop while the arena is frozen (ST-170): everything that advances world state or reads input. Left running:
 * time warp (zeroes the clock), `PhysicsSystem` (worldDt 0 means no steps, but it still interpolates the transforms the renderer reads), observer, camera, render, HUD and the other presentation systems, removal and death (they only
 * act on pending flags), so the frozen scene and the UI keep drawing.
 */
private val FROZEN_WHILE_PAUSED = listOf(
    EndLevelSystem::class.java, PlayerInputSystem::class.java, EnemyAiSystem::class.java, MoveSystem::class.java,
    WeaponSystem::class.java, ContactSystem::class.java, BoundaryFieldSystem::class.java, GravitySystem::class.java,
    DiskDamageSystem::class.java, LockOnSteeringSystem::class.java, RocketMotorSystem::class.java,
    ProjectileSweepSystem::class.java, RelativisticDynamics::class.java, LightHistorySystem::class.java, LifetimeSystem::class.java,
)

/**
 * Freezes or resumes world time: no physics steps, AI, weapon cooldowns or lifetime ticks, while the render and UI systems
 * keep running on the frozen state. Sets [GameClock.paused] (so `TimeWarpSystem` reports zero time) and stops the systems in
 * [FROZEN_WHILE_PAUSED]. Used by the scenario briefing (ST-170) and later by objective "read this first" steps (ST-169).
 */
fun setWorldPaused(engine: Engine, clock: GameClock, paused: Boolean) {
    clock.paused = paused
    for (type in FROZEN_WHILE_PAUSED) engine.getSystem(type)?.setProcessing(!paused)
}

/** Systems the presentation layer needs a handle on. */
class GameplaySystems(val lightHistory: LightHistorySystem)

/**
 * Registers the gameplay systems on `deps.engine` in the one true execution order (CLAUDE.md): time warp, end level,
 * input, enemy AI, move, weapons, contact, boundary field, gravity, disk damage, lock-on steering, rocket motor, projectile sweep, relativistic
 * dynamics, light history, physics, observer, [presentation], lifetime, death, remove. Registration order is execution
 * order.
 *
 * [presentation] runs after the observer system and registers camera, render, debug, HUD and minimap systems (needs GL, so
 * the headless balance runner leaves it empty). `PlayScreen.setupEcs()` and `BalanceSim` both call this, so they cannot drift.
 */
fun buildGameplaySystems(deps: GameplayDeps, presentation: (GameplaySystems) -> Unit = {}): GameplaySystems {
    val moveSystem = MoveSystem(deps.settings)
    val boundaryFieldSystem = BoundaryFieldSystem(deps.mapProvider, deps.shimmerImpacts, deps.settings)
    // After the boundary field and before the sweep and relativisticDynamics: it adds to accel / applies forces.
    val gravitySystem = GravitySystem(deps.settings)
    // Accretion-disk damage (ST-175) runs in afterStep, after gravity has absorbed bodies inside the horizon.
    val diskDamageSystem = DiskDamageSystem(deps.settings)
    // A lock-on shot adds its engine acceleration in beforeStep like the ship; RelativisticDynamics integrates it last.
    val lockOnSteeringSystem = LockOnSteeringSystem(deps.settings)
    // Rocket motors burn in beforeStep too (ST-181), right after the lock-on shots.
    val rocketMotorSystem = RocketMotorSystem(deps.settings, deps.clock)
    val projectileSweepSystem = ProjectileSweepSystem(deps.world, deps.settings)
    // Must be the last step listener: it integrates the accelerations the others added.
    val relativisticDynamics = RelativisticDynamics(deps.settings)
    // Must come after relativisticDynamics: it records the post-step state.
    val lightHistory = LightHistorySystem()
    val contactSystem = ContactSystem(deps.world, deps.settings, deps.hullSounds)
    val systems = GameplaySystems(lightHistory)
    with(deps.engine) {
        addSystem(TimeWarpSystem(deps.clock, deps.settings))
        addSystem(EndLevelSystem(deps.onLevelCleared))
        addSystem(
            PlayerInputSystem(
                deps.gameEventManager, deps.camera, deps.controlSettings, deps.observerState, deps.settings,
                lightHistory, deps.clock,
            )
        )
        // Enemy pilots write MoveComponent/WeaponComponent like the player's input does, so before MoveSystem and WeaponSystem.
        addSystem(EnemyAiSystem(lightHistory, deps.clock, deps.settings))
        addSystem(moveSystem)
        addSystem(
            WeaponSystem(deps.weaponProjectileProvider, deps.clock, deps.hullSounds, deps.lockOnProvider, deps.settings, deps.rocketProvider)
        )
        addSystem(contactSystem)
        addSystem(boundaryFieldSystem)
        addSystem(gravitySystem)
        addSystem(diskDamageSystem)
        addSystem(lockOnSteeringSystem)
        addSystem(rocketMotorSystem)
        addSystem(projectileSweepSystem)
        addSystem(relativisticDynamics)
        addSystem(lightHistory)
        addSystem(
            PhysicsSystem(
                deps.world, this,
                listOf(
                    moveSystem, boundaryFieldSystem, gravitySystem, diskDamageSystem, lockOnSteeringSystem, rocketMotorSystem,
                    projectileSweepSystem,
                    relativisticDynamics, lightHistory, contactSystem,
                ),
                deps.clock,
            )
        )
        addSystem(ObserverSystem(deps.observerState, deps.settings, deps.clock))
        presentation(systems)
        addSystem(LifetimeSystem(deps.clock))
        addSystem(DeathSystem(this, deps.asteroidProvider, deps.random, deps.computerSounds, deps.clock, deps.invulnerablePlayer, deps.invulnerableHealth, deps.onPlayerDeath))
        addSystem(RemoveSystem(deps.world, deps.mapProvider))
    }
    return systems
}
