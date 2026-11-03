package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.weaponCmpOptional
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.input.ControlStrategy
import com.github.pepek42.asteroids.input.MouseHullStrategy
import com.github.pepek42.asteroids.input.PlayerInputState
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import ktx.ashley.allOf
import ktx.log.logger

/** Collects input into [PlayerInputState] and delegates the mapping to the active [ControlStrategy]. */
class PlayerInputSystem(
    private val gameEventManager: GameEventManager,
    private val camera: Camera,
    private val controlSettings: ControlSettings = ControlSettings(),
    private val observer: ObserverState = ObserverState(),
    private val settings: RelativitySettings = RelativitySettings(),
) : IteratingSystem(allOf(PlayerComponent::class, MoveComponent::class, BodyComponent::class).get()),
    PlayerInputListener {
    private val input = PlayerInputState()
    private val screenAimPoint = Vector2()
    private val unprojected = Vector3()
    private var lastScheme: ControlScheme? = null

    private val mouseHull = MouseHullStrategy()

    // TWIN_STICK and AUTO_AIM fall back to MouseHullStrategy until ST-061/ST-062.
    private val strategies: Map<ControlScheme, ControlStrategy> = mapOf(
        ControlScheme.MOUSE_HULL to mouseHull,
        ControlScheme.TWIN_STICK to mouseHull,
        ControlScheme.AUTO_AIM to mouseHull,
    )

    override fun addedToEngine(engine: Engine?) {
        super.addedToEngine(engine)
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine?) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun update(deltaTime: Float) {
        unprojected.set(screenAimPoint.x, screenAimPoint.y, 0f)
        camera.unproject(unprojected)
        input.aimWorld.set(unprojected.x, unprojected.y)
        input.betaShip.set(observer.betaVisual)
        input.pilotView = settings.pilotView && settings.relativityEnabled
        super.update(deltaTime)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        val move = entity.moveCmp
        val weapon = entity.weaponCmpOptional
        val scheme = controlSettings.scheme
        val strategy = strategies.getValue(scheme)
        if (scheme != lastScheme) {
            if (strategy === mouseHull && scheme != ControlScheme.MOUSE_HULL) {
                logger.info { "Control scheme $scheme is not implemented yet, falling back to mouse-hull" }
            }
            strategy.onActivate(move, weapon, body.angle)
            lastScheme = scheme
        }
        strategy.apply(input, body.position, body.angle, move, weapon, deltaTime)
        defaultLoggingUtils.tryLogging { logger.debug { "scheme $scheme aimWorld ${input.aimWorld}" } }
    }

    override fun movement(thrusters: Float) {
        input.thrustHeld = thrusters
    }

    override fun screenAimPoint(screenX: Int, screenY: Int) {
        screenAimPoint.set(screenX.toFloat(), screenY.toFloat())
    }

    override fun fire(fire: Boolean) {
        input.fireHeld = fire
    }

    override fun block() {
        // TODO
        logger.debug { "Using shields!" }
    }

    companion object {
        private val logger = logger<PlayerInputSystem>()
    }
}
