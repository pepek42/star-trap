package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.IntArray
import com.badlogic.gdx.utils.IntFloatMap
import com.badlogic.gdx.utils.IntMap
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.physics.LIGHT_HISTORY_CAPACITY
import com.github.pepek42.asteroids.physics.LightHistory
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import ktx.ashley.allOf

/**
 * Records every body's post-step state into a [LightHistory] in arena time, so the renderer and AI can ask where the
 * light now reaching a point left an entity (see [historyOf] with `retarded`).
 *
 * It must be the LAST [PhysicsStepListener]: [afterStep] then sees the final state of the step. [time] starts at 0 on
 * [clear] and gains `dt` per step, so it equals `GameClock.worldTime`. Histories are keyed by
 * [BaseInfoComponent.id] (unique per run; Ashley pools `Entity` objects). Removed entities keep their history for
 * [LIGHT_HISTORY_CAPACITY] samples so ghosts of the last light can still be drawn, then it returns to the free list.
 */
class LightHistorySystem : EntitySystem(), PhysicsStepListener, EntityListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(Array())
    private val histories = IntMap<LightHistory>()
    private val removedAtMap = IntFloatMap()
    private val freeList = Array<LightHistory>()
    private val pruneScratch = IntArray()

    /** Arena time (s) of the newest recorded step. */
    var time = 0f
        private set
    val now: Float get() = time

    private val pruneAfter = LIGHT_HISTORY_CAPACITY * PHYSICS_UPDATE_INTERVAL

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        val family = allOf(BodyComponent::class, BaseInfoComponent::class, TransformComponent::class).get()
        entities = engine.getEntitiesFor(family)
        engine.addEntityListener(family, this)
    }

    override fun removedFromEngine(engine: Engine) {
        engine.removeEntityListener(this)
        super.removedFromEngine(engine)
    }

    override fun update(deltaTime: Float) {
        // Recording runs per physics step in afterStep.
    }

    override fun beforeStep(dt: Float) = Unit

    override fun afterStep(dt: Float) {
        time += dt
        for (i in 0 until entities.size()) {
            val entity = entities[i]
            val id = entity.baseInfoCmp.id
            var history = histories[id]
            if (history == null) {
                history = if (freeList.isEmpty) LightHistory() else freeList.pop().also { it.clear() }
                // Existed at level start (first sample within one step): treated as moving uniformly before it.
                history.eternal = time <= PHYSICS_UPDATE_INTERVAL + dt * EPS_FRACTION
                histories.put(id, history)
            }
            val body = entity.bodyCmp.body
            val v = body.linearVelocity
            history.record(time, body.position.x, body.position.y, body.angle, v.x, v.y)
        }
        prune()
    }

    override fun entityAdded(entity: Entity) = Unit

    override fun entityRemoved(entity: Entity) {
        val id = entity.baseInfoCmp.id
        if (histories.containsKey(id)) removedAtMap.put(id, time)
    }

    private fun prune() {
        if (removedAtMap.size == 0) return
        pruneScratch.clear()
        val it = removedAtMap.entries()
        while (it.hasNext) {
            val e = it.next()
            if (time - e.value > pruneAfter) pruneScratch.add(e.key)
        }
        for (i in 0 until pruneScratch.size) {
            val id = pruneScratch[i]
            removedAtMap.remove(id, 0f)
            histories.remove(id)?.let { freeList.add(it) }
        }
    }

    fun historyOf(id: Int): LightHistory? = histories[id]

    /** Arena time the entity was removed, or NaN while it is alive (or unknown). */
    fun removedAt(id: Int): Float = removedAtMap.get(id, Float.NaN)

    /** Returns all histories to the free list and restarts [time] at 0. Call when a level starts. */
    fun clear() {
        for (h in histories.values()) freeList.add(h)
        histories.clear()
        removedAtMap.clear()
        time = 0f
    }

    private companion object {
        /** Float tolerance on the "first step" test, as a fraction of one step. */
        const val EPS_FRACTION = 1e-3f
    }
}
