package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.debug.TIME_SCALES
import com.github.pepek42.asteroids.event.DebugInputListener
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.BRAKE_BOOST_K
import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.FIELD_DAMPING
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RELATIVITY_ENABLED
import com.github.pepek42.asteroids.render.CENTRE_POINTER_ENABLED
import com.github.pepek42.asteroids.render.GRID_ENABLED
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import kotlin.math.abs

// Developer tooling, not shipped UI: the strings below are English literals on purpose (no i18n keys).
// If the options move into a player options menu (GDD section 11 Q3), they get i18n keys then.

const val DEBUG_PANEL_WIDTH = 260f
private const val PANEL_BACKGROUND_ALPHA = 0.6f
private const val PANEL_PAD = 6f
private const val SLIDER_WIDTH = 150f
private const val TIME_BUTTON_WIDTH = 70f

/** Appends [value] with exactly [decimals] decimals, always with a dot (locale independent). */
fun appendFixed(out: StringBuilder, value: Float, decimals: Int): StringBuilder {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = Math.round(abs(value.toDouble()) * scale)
    if (value < 0f && scaled != 0L) out.append('-')
    out.append(scaled / scale)
    if (decimals > 0) {
        out.append('.')
        val frac = (scaled % scale).toString()
        repeat(decimals - frac.length) { out.append('0') }
        out.append(frac)
    }
    return out
}

/** Writes the readout block into [out] (cleared first). Pure so it can be tested without widgets. */
fun formatReadouts(observer: ObserverState, rel: RelativisticComponent?, c: Float, a0: Float, out: StringBuilder) {
    out.setLength(0)
    val speed = observer.betaRaw.len() * c
    val properSpeed = rel?.u?.len() ?: (speed * observer.gammaRaw)
    out.append("β = "); appendFixed(out, observer.betaRaw.len(), 3).append('\n')
    out.append("γ = "); appendFixed(out, observer.gammaRaw, 2).append('\n')
    out.append("τ = "); appendFixed(out, observer.tau, 1).append(" s\n")
    out.append("world t = "); appendFixed(out, observer.worldTime, 1).append(" s\n")
    out.append("|v| = "); appendFixed(out, speed, 1).append(" u/s\n")
    out.append("|u| = γ|v| = "); appendFixed(out, properSpeed, 1).append(" u/s\n")
    out.append("ship-frame speed = "); appendFixed(out, observer.rapidity * c, 1).append(" u/s\n")
    out.append("c = "); appendFixed(out, c, 0).append("   a0 = "); appendFixed(out, a0, 0)
}

/** Restores the const defaults for the tunables shown in the panel. */
fun resetDebugDefaults(relativity: RelativitySettings, debug: DebugSettings) {
    relativity.relativityEnabled = RELATIVITY_ENABLED
    relativity.fieldEnabled = true
    relativity.c = SPEED_OF_LIGHT
    relativity.engineProperAccel = ENGINE_PROPER_ACCEL
    relativity.brakeBoost = BRAKE_BOOST_K
    relativity.fieldDamping = FIELD_DAMPING
    relativity.gammaMaxMassive = GAMMA_MAX_MASSIVE
    debug.timeScale = 1f
    debug.showVectors = false
    debug.gridEnabled = GRID_ENABLED
    debug.centrePointerEnabled = CENTRE_POINTER_ENABLED
}

/** Right-docked relativity debug panel, toggled with F1 (ST-059). */
class DebugPanel(
    private val skin: Skin,
    private val relativitySettings: RelativitySettings,
    private val debugSettings: DebugSettings,
    private val controlSettings: ControlSettings,
    private val onVisibilityChanged: (Boolean) -> Unit = {},
) : DebugInputListener {
    private val root = Table()
    private val container = Table().apply {
        setFillParent(true)
        touchable = Touchable.childrenOnly
        right().top()
    }
    private val readouts = Label("", skin)
    private val text = StringBuilder()
    private val syncs = ArrayList<() -> Unit>()

    val isVisible: Boolean get() = debugSettings.panelVisible

    init {
        root.background = skin.newDrawable("white", Color(0f, 0f, 0f, PANEL_BACKGROUND_ALPHA))
        root.pad(PANEL_PAD)
        root.defaults().left().pad(2f)
        build()
        root.isVisible = debugSettings.panelVisible
        container.add(root).width(DEBUG_PANEL_WIDTH).top().right()
    }

    private fun build() {
        root.add(readouts).colspan(2).left().row()
        readouts.addTooltip("ship-frame speed = rapidity * c. For straight-line thrust from rest it equals a0 * tau, " +
            "what the pilot's own accelerometer would integrate.")

        checkBox("relativity", { relativitySettings.relativityEnabled }) { relativitySettings.relativityEnabled = it }
        checkBox("boundary field", { relativitySettings.fieldEnabled }) { relativitySettings.fieldEnabled = it }
        checkBox("grid", { debugSettings.gridEnabled }) { debugSettings.gridEnabled = it }
        checkBox("centre pointer", { debugSettings.centrePointerEnabled }) { debugSettings.centrePointerEnabled = it }
        checkBox("show vectors", { debugSettings.showVectors }) { debugSettings.showVectors = it }

        val initialScale = debugSettings.timeScale
        val group = ButtonGroup<TextButton>().apply { setMaxCheckCount(1); setMinCheckCount(1) }
        val buttons = Table()
        TIME_SCALES.forEach { scale ->
            val b = TextButton(if (scale == 1f) "1x" else "${scale}x", skin, "toggle")
            b.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    if (b.isChecked) debugSettings.timeScale = scale
                }
            })
            group.add(b)
            buttons.add(b).width(TIME_BUTTON_WIDTH).pad(1f)
            syncs.add { b.isChecked = debugSettings.timeScale == scale }
        }
        // ButtonGroup.add auto-checks the first button (firing its listener), so restore the real state afterwards.
        debugSettings.timeScale = initialScale
        group.buttons.forEachIndexed { i, b -> if (TIME_SCALES[i] == initialScale) b.isChecked = true }
        root.add(Label("time scale", skin)).colspan(2).row()
        root.add(buttons).colspan(2).left().row()

        slider("c", 20f, 67f, 1f, 0, { relativitySettings.c }) { relativitySettings.c = it }
        slider("a0", 5f, 120f, 1f, 0, { relativitySettings.engineProperAccel }) { relativitySettings.engineProperAccel = it }
        slider("brake boost", 0f, 5f, 0.1f, 1, { relativitySettings.brakeBoost }) { relativitySettings.brakeBoost = it }
        slider("field damping", 0f, 5f, 0.1f, 1, { relativitySettings.fieldDamping }) { relativitySettings.fieldDamping = it }
        slider("gamma cap", 2f, 50f, 1f, 0, { relativitySettings.gammaMaxMassive }) { relativitySettings.gammaMaxMassive = it }

        val select = SelectBox<String>(skin)
        select.setItems(*ControlScheme.entries.map { it.flag }.toTypedArray())
        select.selected = controlSettings.scheme.flag
        select.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                ControlScheme.fromFlag(select.selected)?.let { controlSettings.scheme = it }
            }
        })
        syncs.add { select.selected = controlSettings.scheme.flag }
        root.add(Label("controls", skin)).left()
        root.add(select).left().row()

        val reset = TextButton("reset defaults", skin)
        reset.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                resetDebugDefaults(relativitySettings, debugSettings)
                syncs.forEach { it() }
            }
        })
        root.add(reset).colspan(2).left().padTop(6f).row()
    }

    private fun Actor.addTooltip(msg: String) {
        addListener(TextTooltip(msg, skin))
    }

    private fun checkBox(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val box = CheckBox(" $title", skin)
        box.isChecked = get()
        box.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = set(box.isChecked)
        })
        syncs.add { box.isChecked = get() }
        root.add(box).colspan(2).left().row()
    }

    private fun slider(
        title: String, min: Float, max: Float, step: Float, decimals: Int,
        get: () -> Float, set: (Float) -> Unit,
    ) {
        val s = Slider(min, max, step, false, skin, "default-horizontal")
        val valueLabel = Label("", skin)
        fun refreshLabel() {
            val sb = StringBuilder(title).append(": ")
            appendFixed(sb, s.value, decimals)
            valueLabel.setText(sb)
        }
        s.value = get()
        refreshLabel()
        s.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                set(s.value)
                refreshLabel()
            }
        })
        syncs.add { s.value = get(); refreshLabel() }
        root.add(valueLabel).colspan(2).left().row()
        root.add(s).colspan(2).width(SLIDER_WIDTH).left().row()
    }

    fun show(stage: Stage) {
        stage.addActor(container)
    }

    fun hide() {
        container.remove()
    }

    override fun toggleDebugPanel() {
        debugSettings.panelVisible = !debugSettings.panelVisible
        root.isVisible = debugSettings.panelVisible
        onVisibilityChanged(debugSettings.panelVisible)
    }

    /** Refreshes the readout label. Called at 10 Hz by DebugPanelSystem. */
    fun update(observer: ObserverState, rel: RelativisticComponent?) {
        formatReadouts(observer, rel, relativitySettings.c, relativitySettings.engineProperAccel, text)
        // The UI bitmap font has no Greek glyphs, so spell the letters out on screen.
        readouts.setText(text.toString().replace("\u03B2", "beta").replace("\u03B3", "gamma").replace("\u03C4", "tau"))
    }
}
