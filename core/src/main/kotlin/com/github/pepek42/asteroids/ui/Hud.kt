package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import ktx.scene2d.label
import ktx.scene2d.progressBar
import ktx.scene2d.scene2d
import kotlin.math.acosh
import kotlin.math.roundToInt

/** Rapidity at which the HUD bar is full: beta = tanh(acosh(10)) = 0.995. */
val HUD_RAPIDITY_FULL = acosh(10f)

/** Warp below this (and not capped) hides the warp label. */
const val HUD_WARP_VISIBLE_THRESHOLD = 1.05f

/** Bundle key of the time warp label, or null when it should be hidden. */
fun warpLabelKey(warp: Float, capped: Boolean): String? = when {
    capped -> "hud_warp_capped"
    warp >= HUD_WARP_VISIBLE_THRESHOLD -> "hud_warp"
    else -> null
}

/** Fill of the rapidity bar in 0..1. */
fun rapidityBarValue(rapidity: Float): Float = (rapidity / HUD_RAPIDITY_FULL).coerceIn(0f, 1f)

class Hud(
    game: Game,
) {
    private val stage: Stage = game.get()
    private val i18NBundle: I18NBundle = game.get()
    private val coordinatesLabel: Label = scene2d.label("")
    private val speedLabel: Label = scene2d.label("")
    private val betaLabel: Label = scene2d.label("")
    private val gammaLabel: Label = scene2d.label("")
    private val timeLabel: Label = scene2d.label("")
    private val warpLabel: Label = scene2d.label("").apply { isVisible = false }
    private val rapidityBar: ProgressBar = scene2d.progressBar(0f, 1f, 0.001f, false, "default-horizontal")
    private val levelLabel: Label = scene2d.label("")
    private val centerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val pointerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val table: Table = Table()
    private val pointerPos = Vector2()

    /** False after toggleHud(false). */
    var isShown: Boolean = false
        private set

    init {
        table.setFillParent(true)
        table.defaults().pad(3f).expand().fill()
        val verticalGroup = VerticalGroup().apply {
            addActor(levelLabel)
            addActor(coordinatesLabel)
            addActor(speedLabel)
            addActor(betaLabel)
            addActor(rapidityBar)
            addActor(gammaLabel)
            addActor(timeLabel)
            addActor(warpLabel)
            columnAlign(Align.topLeft)
            top().left()
            pad(5f, 5f, 0f, 0f)
        }
        table.add(verticalGroup).top().left()
        table.add()
        table.add()
        table.row()
        table.add()
        table.add(centerLabel).align(Align.center)
        table.add()
        table.row()
        table.add()
        table.add()
        table.add()
    }

    fun toggleHud(show: Boolean) {
        isShown = show
        if (show) {
            stage.addActor(table)
            stage.addActor(pointerLabel)
        } else {
            table.remove()
            pointerLabel.remove()
        }
    }

    fun updateAndRender(deltaTime: Float) {
        stage.run {
            viewport.apply()
            act(deltaTime.coerceAtMost(MAX_DELTA_TIME))
            draw()
        }
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun updatePosition(interpolatedPosition: Vector2) {
        coordinatesLabel.setText(
            i18NBundle.format(
                "hud_coordinates",
                interpolatedPosition.x.roundToInt(),
                interpolatedPosition.y.roundToInt()
            )
        )
    }

    fun updateVelocity(linearVelocity: Vector2) {
        speedLabel.setText(
            i18NBundle.format(
                "hud_velocity",
                linearVelocity.x.roundToInt(),
                linearVelocity.y.roundToInt()
            )
        )
    }

    fun updateRelativity(beta: Float, gamma: Float, rapidity: Float, shipTime: Float, worldTime: Float, warp: Float, capped: Boolean) {
        betaLabel.setText(i18NBundle.format("hud_beta", beta))
        gammaLabel.setText(i18NBundle.format("hud_gamma", gamma))
        timeLabel.setText(i18NBundle.format("hud_time", shipTime, worldTime))
        rapidityBar.value = rapidityBarValue(rapidity)
        val key = warpLabelKey(warp, capped)
        warpLabel.isVisible = key != null
        if (key != null) warpLabel.setText(i18NBundle.format(key, warp))
    }

    /** Distance label next to the nearest target arrow. [screenX], [screenY] are window pixels with y pointing down. */
    fun showPointerDistance(screenX: Float, screenY: Float, distance: Float) {
        pointerLabel.setText(i18NBundle.format("hud_target_distance", distance.roundToInt()))
        pointerLabel.pack()
        val p = stage.screenToStageCoordinates(pointerPos.set(screenX, screenY))
        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        // Centre the label on the arrow, then keep it inside the stage; nudge towards the screen centre.
        val x = (p.x - pointerLabel.width / 2f).coerceIn(0f, w - pointerLabel.width)
        val towardsCentreY = if (p.y > h / 2f) -POINTER_LABEL_OFFSET else POINTER_LABEL_OFFSET
        val y = (p.y - pointerLabel.height / 2f + towardsCentreY).coerceIn(0f, h - pointerLabel.height)
        pointerLabel.setPosition(x, y)
        pointerLabel.isVisible = true
    }

    fun hidePointerDistance() {
        pointerLabel.isVisible = false
    }

    fun updateLevel(level: Int) {
        levelLabel.setText(i18NBundle.format("hud_level", level))
    }

    companion object {
        private const val MAX_DELTA_TIME = 1f / 30
        private const val POINTER_LABEL_OFFSET = 40f
    }
}
