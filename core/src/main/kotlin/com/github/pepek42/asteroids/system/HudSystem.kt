package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.render.pointerAlpha
import com.github.pepek42.asteroids.render.selectPointerTargets
import com.github.pepek42.asteroids.render.ReferenceGridRenderer
import com.github.pepek42.asteroids.render.edgePointer
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.weaponLoadoutCmpOptional
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.ui.Hud
import com.github.pepek42.asteroids.ui.TargetCounts
import com.github.pepek42.asteroids.ui.countLevelTargets
import com.github.pepek42.asteroids.ecs.levelTargetFamily
import ktx.ashley.allOf

class HudSystem(
    private val hud: Hud,
    private val camera: OrthographicCamera,
    private val viewport: Viewport,
    private val debugSettings: DebugSettings,
    private val pointerRenderer: ReferenceGridRenderer,
    private val observer: ObserverState,
    private val clock: GameClock,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class, BodyComponent::class).get()) {

    /** Only level targets get arrows (owner 2026-11-07): every asteroid and enemy ship, nothing else. */
    private val targetFamily = levelTargetFamily()
    private var candidates = FloatArray(64)
    private var candidateEntities = arrayOfNulls<Entity>(32)
    private var selection = IntArray(32)
    private val pointerPos = Vector2()
    private val screenPos = Vector3()
    private val playerPos = Vector2()
    private var hasPlayer = false
    private val targetCounts = TargetCounts()

    override fun update(deltaTime: Float) {
        hasPlayer = false
        super.update(deltaTime)
        countLevelTargets(engine.getEntitiesFor(targetFamily), targetCounts)
        hud.updateTargets(targetCounts)
        updateTargetPointers()
        hud.updateAndRender(deltaTime)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        val transformCmp = entity.transformCmp
        hasPlayer = true
        playerPos.set(transformCmp.interpolatedPosition)
        hud.updatePosition(transformCmp.interpolatedPosition)
        hud.updateVelocity(body.linearVelocity)
        hud.updateRelativity(
            observer.betaRaw.len(), observer.gammaRaw, observer.rapidity, observer.tau, observer.worldTime,
            clock.warp, clock.warpCapped || clock.stepsCapped,
        )
        val loadout = entity.weaponLoadoutCmpOptional
        if (loadout != null) hud.updateWeapons(loadout)
        hud.updateTidal(entity.relativisticCmpOptional?.tidalRatio ?: 0f)
    }

    private fun updateTargetPointers() {
        if (!debugSettings.targetPointersEnabled || !hasPlayer || !hud.isShown) {
            hud.hidePointerDistance()
            return
        }
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        val entities = engine.getEntitiesFor(targetFamily)
        // The arrows are a ship-computer assist (owner 2026-11-07): they always point at the TRUE current position,
        // also in pilot view and before the object's light has arrived. Only the sprites are light-delayed.
        var count = 0
        for (i in 0 until entities.size()) {
            val e = entities[i]
            if (2 * count + 1 >= candidates.size) candidates = candidates.copyOf(candidates.size * 2)
            if (count >= candidateEntities.size) candidateEntities = candidateEntities.copyOf(candidates.size / 2)
            val truePos = e.transformCmp.interpolatedPosition
            candidates[2 * count] = truePos.x
            candidates[2 * count + 1] = truePos.y
            candidateEntities[count] = e
            count++
        }
        // Every off-screen target gets an arrow; the buffer only grows (no per-frame allocation once warmed up).
        if (selection.size < count) selection = IntArray(candidateEntities.size)
        val selected = selectPointerTargets(
            camera.position.x, camera.position.y, halfW, halfH, candidates, count, count, selection,
        )
        if (selected == 0) {
            hud.hidePointerDistance()
            return
        }
        val margin = POINTER_MARGIN_FRACTION * halfH
        var labelled = false
        for (k in 0 until selected) {
            val idx = selection[k]
            val tx = candidates[2 * idx]
            val ty = candidates[2 * idx + 1]
            val angle = edgePointer(camera.position.x, camera.position.y, halfW, halfH, tx, ty, margin, pointerPos)
            if (angle.isNaN()) continue
            val dist = playerPos.dst(tx, ty)
            val enemy = candidateEntities[idx]?.baseInfoCmp?.faction == FactionEnum.ENEMY
            val alpha = pointerAlpha(dist)
            if (enemy) {
                pointerRenderer.renderArrow(camera, pointerPos, angle, POINTER_SIZE_FRACTION * halfH, 1f, 0.15f, 0.15f, alpha)
            } else {
                pointerRenderer.renderArrow(camera, pointerPos, angle, POINTER_SIZE_FRACTION * halfH, 0.8f, 0.8f, 0.8f, alpha)
            }
            if (!labelled) {
                labelled = true
                // Project into letterboxed game-viewport pixels (y-down), which Hud maps into stage coordinates.
                screenPos.set(pointerPos.x, pointerPos.y, 0f)
                camera.project(screenPos, viewport.screenX.toFloat(), viewport.screenY.toFloat(),
                    viewport.screenWidth.toFloat(), viewport.screenHeight.toFloat())
                // Viewport pixels are back-buffer pixels; input/stage coordinates are logical (HiDPI differs).
                val scale = Gdx.graphics.width.toFloat() / Gdx.graphics.backBufferWidth
                hud.showPointerDistance(screenPos.x * scale, Gdx.graphics.height - screenPos.y * scale, dist)
            }
        }
        if (!labelled) hud.hidePointerDistance()
        java.util.Arrays.fill(candidateEntities, 0, count, null)
    }

    companion object {
        private const val POINTER_MARGIN_FRACTION = 0.1f
        private const val POINTER_SIZE_FRACTION = 0.04f
    }
}
