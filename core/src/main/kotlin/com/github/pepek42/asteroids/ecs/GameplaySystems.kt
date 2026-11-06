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
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.BoundaryFieldSystem
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import com.github.pepek42.asteroids.system.LifetimeSystem
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.MoveSystem
import com.github.pepek42.asteroids.system.ObserverSystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PlayerInputSystem
import com.github.pepek42.asteroids.system.ProjectileSweepSystem
import com.github.pepek42.asteroids.system.RelativisticDynamics
import com.github.pepek42.asteroids.system.RemoveSystem
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
)

/** Systems the presentation layer needs a handle on. */
class GameplaySystems(val lightHistory: LightHistorySystem)

/**
 * Registers the gameplay systems on `deps.engine` in the one true execution order (CLAUDE.md): time warp, end level,
 * input, move, weapons, contact, boundary field, projectile sweep, relativistic dynamics, light history, physics, observer,
 * [presentation], lifetime, death, remove. Registration order is execution order.
 *
 * [presentation] runs after the observer system and registers camera, render, debug, HUD and minimap systems (needs GL, so
 * the headless balance runner leaves it empty). `PlayScreen.setupEcs()` and `BalanceSim` both call this, so they cannot drift.
 */
fun buildGameplaySystems(deps: GameplayDeps, presentation: (GameplaySystems) -> Unit = {}): GameplaySystems {
    val moveSystem = MoveSystem(deps.settings)
    val boundaryFieldSystem = BoundaryFieldSystem(deps.mapProvider, deps.shimmerImpacts, deps.settings)
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
        addSystem(moveSystem)
        addSystem(WeaponSystem(deps.weaponProjectileProvider, deps.clock, deps.hullSounds))
        addSystem(contactSystem)
        addSystem(boundaryFieldSystem)
        addSystem(projectileSweepSystem)
        addSystem(relativisticDynamics)
        addSystem(lightHistory)
        addSystem(
            PhysicsSystem(
                deps.world, this,
                listOf(moveSystem, boundaryFieldSystem, projectileSweepSystem, relativisticDynamics, lightHistory, contactSystem),
                deps.clock,
            )
        )
        addSystem(ObserverSystem(deps.observerState, deps.settings, deps.clock))
        presentation(systems)
        addSystem(LifetimeSystem(deps.clock))
        addSystem(DeathSystem(this, deps.asteroidProvider, deps.random, deps.computerSounds, deps.clock, deps.onPlayerDeath))
        addSystem(RemoveSystem(deps.world, deps.mapProvider))
    }
    return systems
}
