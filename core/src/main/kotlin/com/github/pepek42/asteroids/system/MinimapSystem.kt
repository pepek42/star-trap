package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.asteroidMapper
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.ui.ARENA_BOUNDARY_COLOR
import com.github.pepek42.asteroids.ui.MinimapViewport
import com.github.pepek42.asteroids.ui.PLAYER_MARKER_COLOR
import ktx.ashley.allOf

private const val SHIP_DOT_RADIUS = 2.0f
private const val PROJECTILE_DOT_RADIUS = 0.8f
private const val ASTEROID_RADIUS_FACTOR = 1.0f
private const val ASTEROID_MIN_DOT_RADIUS = 0.6f
private const val HOLE_DOT_RADIUS = 1.6f
private const val HOLE_OUTLINE = 0.5f
private const val BACKGROUND_ALPHA = 0.55f
private const val BORDER_INSET_PX = 0.5f

private const val DEBRIS_DOT_RADIUS = 0.5f

/** Planets are drawn as grey discs at their true radius. */
private val PLANET_MINIMAP_COLOR = Color(0.55f, 0.55f, 0.55f, 1f)
/** Debris is a small dim grey dot (ST-139). */
private val DEBRIS_MINIMAP_COLOR = Color(0.3f, 0.3f, 0.3f, 1f)
private val BACKGROUND_COLOR = Color(0.02f, 0.02f, 0.06f, BACKGROUND_ALPHA)
private val VIEW_RECT_COLOR = Color(1f, 1f, 1f, 0.8f)
private val FIELD_SKIN_COLOR = ARENA_BOUNDARY_COLOR.cpy().apply { a = 0.35f }

/**
 * Minimap anchored to the bottom-right corner of the window (not the letterboxed game viewport),
 * so it keeps a constant size relative to the screen.
 */
class MinimapSystem(
    private val mainViewport: Viewport,
    private val minimapViewport: MinimapViewport,
    private val mainCamera: OrthographicCamera,
    private val mapProvider: MapProvider,
    private val debugSettings: DebugSettings,
    private val settings: RelativitySettings,
    private val panelWidthPx: () -> Float,
    private val shape: ShapeRenderer = ShapeRenderer(),
) : IteratingSystem(allOf(BaseInfoComponent::class, TransformComponent::class).get()),
    Disposable {

    private var playerEntity: Entity? = null

    init {
        shape.setAutoShapeType(true)
    }

    override fun update(deltaTime: Float) {
        val mapW = mapProvider.mapWidth
        val mapH = mapProvider.mapHeight
        // Slide left of the debug panel while it is open so the panel's controls stay visible.
        val inset = if (debugSettings.panelVisible) panelWidthPx() else 0f
        minimapViewport.resize(Gdx.graphics.width, Gdx.graphics.height, mapW, mapH, inset)
        minimapViewport.apply()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        // Separate alpha func keeps the framebuffer alpha opaque (matters for screenshots)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE)
        shape.projectionMatrix = minimapViewport.camera.combined
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.color = BACKGROUND_COLOR
        shape.rect(0f, 0f, mapW, mapH)

        playerEntity = null
        super.update(deltaTime)
        playerEntity?.let { drawDot(it, PLAYER_MARKER_COLOR, SHIP_DOT_RADIUS) }

        shape.set(ShapeRenderer.ShapeType.Line)
        shape.color = VIEW_RECT_COLOR
        val viewW = mainCamera.viewportWidth * mainCamera.zoom
        val viewH = mainCamera.viewportHeight * mainCamera.zoom
        shape.rect(mainCamera.position.x - viewW / 2, mainCamera.position.y - viewH / 2, viewW, viewH)
        shape.color = ARENA_BOUNDARY_COLOR
        val insetX = mapW / minimapViewport.screenWidth * BORDER_INSET_PX
        val insetY = mapH / minimapViewport.screenHeight * BORDER_INSET_PX
        shape.rect(insetX, insetY, mapW - 2 * insetX, mapH - 2 * insetY)
        shape.color = FIELD_SKIN_COLOR
        val skin = settings.fieldParams.skin
        shape.rect(skin, skin, mapW - 2 * skin, mapH - 2 * skin)
        shape.end()

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        mainViewport.apply()
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val baseInfo = entity.baseInfoCmp
        val radius = when (baseInfo.entityType) {
            EntityType.SHIP -> SHIP_DOT_RADIUS
            EntityType.ASTEROID -> asteroidMapper.get(entity)
                ?.let { (it.asteroidSize.radius * ASTEROID_RADIUS_FACTOR).coerceAtLeast(ASTEROID_MIN_DOT_RADIUS) }
                ?: ASTEROID_MIN_DOT_RADIUS
            EntityType.PROJECTILE -> PROJECTILE_DOT_RADIUS
            EntityType.BLACK_HOLE -> {
                drawHole(entity)
                return
            }
            EntityType.PLANET -> {
                drawDot(entity, PLANET_MINIMAP_COLOR, entity.gravitySourceCmp.radius)
                return
            }
            EntityType.DEBRIS -> {
                drawDot(entity, DEBRIS_MINIMAP_COLOR, DEBRIS_DOT_RADIUS)
                return
            }
            EntityType.UNKNOWN -> return
        }
        if (entity.playerCmpOptional != null) {
            playerEntity = entity
            return
        }
        drawDot(entity, baseInfo.faction.color, radius)
    }

    /** Black dot with a white outline at the true position; the outline is a second, larger white disc drawn first. */
    private fun drawHole(entity: Entity) {
        val pos = entity.transformCmp.interpolatedPosition
        shape.color = Color.WHITE
        shape.circle(pos.x, pos.y, HOLE_DOT_RADIUS + HOLE_OUTLINE)
        shape.color = Color.BLACK
        shape.circle(pos.x, pos.y, HOLE_DOT_RADIUS)
    }

    private fun drawDot(entity: Entity, color: Color, radius: Float) {
        shape.color = color
        val pos = entity.transformCmp.interpolatedPosition
        shape.circle(pos.x, pos.y, radius)
    }

    override fun dispose() {
        shape.dispose()
    }
}
