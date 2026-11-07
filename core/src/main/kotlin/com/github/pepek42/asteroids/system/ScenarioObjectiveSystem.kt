package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.scenario.ORBIT_MAX_RADII
import com.github.pepek42.asteroids.scenario.Objective
import com.github.pepek42.asteroids.scenario.ObjectiveState
import com.github.pepek42.asteroids.scenario.ObjectiveTracker
import com.github.pepek42.asteroids.scenario.OrbitAccumulator
import com.github.pepek42.asteroids.scenario.TURN_THRESHOLD_RAD
import ktx.ashley.allOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The in-fiction instructor (ST-169, assist): walks a scenario's [Objective]s in order, ticking each when its predicate
 * holds and calling [onChange] so the briefing panel redraws. Reads the true state ([ObserverState], transforms,
 * gravity sources) and input events (it is a [PlayerInputListener]); it never writes to the physics.
 * Idle until [start] is called, so a scenario without objectives (and a normal run) is untouched.
 * Registered after [ObserverSystem] (it reads the observer's beta and gamma).
 */
class ScenarioObjectiveSystem(
    private val observer: ObserverState,
    private val onChange: (ObjectiveTracker?) -> Unit,
) : EntitySystem(), PlayerInputListener {
    private val state = ObjectiveState()
    private val orbit = OrbitAccumulator()
    private var tracker: ObjectiveTracker? = null
    private var startAngleRad = 0f
    private var haveStartAngle = false

    private var player: ImmutableArray<Entity>? = null
    private var asteroids: ImmutableArray<Entity>? = null
    private var sources: ImmutableArray<Entity>? = null
    private var engine: Engine? = null
    private val asteroidFamily: Family = allOf(AsteroidComponent::class).get()
    private val asteroidListener = object : EntityListener {
        override fun entityAdded(entity: Entity) = Unit
        override fun entityRemoved(entity: Entity) {
            if (tracker != null) state.rocksDestroyed++
        }
    }

    /** Begins a scenario: [objectives] empty means nothing to show. */
    fun start(objectives: List<Objective>) {
        state.reset()
        orbit.reset()
        haveStartAngle = false
        tracker = if (objectives.isEmpty()) null else ObjectiveTracker(objectives)
        onChange(tracker)
    }

    fun stop() {
        tracker = null
        state.reset()
    }

    override fun addedToEngine(engine: Engine) {
        this.engine = engine
        player = engine.getEntitiesFor(allOf(PlayerComponent::class, TransformComponent::class).get())
        sources = engine.getEntitiesFor(allOf(GravitySourceComponent::class, TransformComponent::class).get())
        asteroids = engine.getEntitiesFor(asteroidFamily)
        engine.addEntityListener(asteroidFamily, asteroidListener)
    }

    override fun removedFromEngine(engine: Engine) {
        engine.removeEntityListener(asteroidListener)
        this.engine = null
        player = null
        sources = null
        asteroids = null
    }

    override fun update(deltaTime: Float) {
        val t = tracker ?: return
        if (t.finished) return
        state.beta = observer.betaRaw.len()
        state.gamma = observer.gammaRaw
        val p = player
        if (p != null && p.size() > 0) readWorld(p[0])
        state.rocksLeft = asteroids?.size() ?: 0
        if (t.update(state)) {
            haveStartAngle = false // "turn" is measured from the heading when its step starts
            onChange(t)
        }
    }

    private fun readWorld(playerEntity: Entity) {
        val pos = playerEntity.transformCmp.interpolatedPosition
        val heading = playerEntity.transformCmp.interpolatedRotationDeg * MathUtils.degreesToRadians
        if (!haveStartAngle) {
            startAngleRad = heading
            haveStartAngle = true
        } else if (abs(shortestAngleDiffRad(startAngleRad, heading)) >= TURN_THRESHOLD_RAD) {
            state.turned = true
        }
        var bestDistRadii = Float.MAX_VALUE
        var bestDx = 0f
        var bestDy = 0f
        val s = sources
        if (s != null) {
            for (i in 0 until s.size()) {
                val e = s[i]
                val src = e.gravitySourceCmp
                if (src.kind != GravitySourceKind.PLANET || src.radius <= 0f) continue
                val c = e.transformCmp.interpolatedPosition
                val dx = pos.x - c.x
                val dy = pos.y - c.y
                val radii = sqrt(dx * dx + dy * dy) / src.radius
                if (radii < bestDistRadii) {
                    bestDistRadii = radii
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        state.planetDistanceRadii = bestDistRadii
        orbit.update(atan2(bestDy, bestDx), bestDistRadii <= ORBIT_MAX_RADII)
        state.orbits = orbit.turns
    }

    override fun movement(thrusters: Float) {
        if (thrusters > 0f) state.thrusted = true
    }

    override fun moveAxis(x: Float, y: Float) {
        if (x != 0f || y != 0f) state.thrusted = true
    }

    override fun fire(fire: Boolean) {
        if (fire) state.fired = true
    }

    override fun togglePilotView() {
        state.pilotViewToggled = true
    }

    override fun toggleTacticalOverlay() {
        state.tacticalToggled = true
    }
}
