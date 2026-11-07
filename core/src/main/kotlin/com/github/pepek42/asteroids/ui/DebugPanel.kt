package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
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
import com.github.pepek42.asteroids.physics.C_MAX_BOX2D_SAFE
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_MAX
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_MIN
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C
import com.github.pepek42.asteroids.physics.FIELD_DAMPING
import com.github.pepek42.asteroids.physics.GAMMA_MASS_ENABLED
import com.github.pepek42.asteroids.physics.GRAVITY_ENABLED
import com.github.pepek42.asteroids.physics.PLAYER_GM
import com.github.pepek42.asteroids.physics.PLAYER_GM_MAX
import com.github.pepek42.asteroids.physics.SOURCE_COUPLING_DEFAULT
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.PILOT_VIEW_DEFAULT
import com.github.pepek42.asteroids.physics.TIME_WARP_ENABLED
import com.github.pepek42.asteroids.physics.TIME_WARP_MAX
import com.github.pepek42.asteroids.physics.TIME_WARP_MAX_LIMIT
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MIN
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C_MIN
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C_MAX
import com.github.pepek42.asteroids.physics.RELATIVITY_ENABLED
import com.github.pepek42.asteroids.render.TARGET_POINTERS_ENABLED
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

/** Flips [settings].panelVisible and returns the new value. Pure so the toggle can be tested without GL. */
fun flipPanelVisible(settings: DebugSettings): Boolean {
    settings.panelVisible = !settings.panelVisible
    return settings.panelVisible
}

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
fun formatReadouts(observer: ObserverState, rel: RelativisticComponent?, c: Float, a0: Float, out: StringBuilder, clock: GameClock? = null, mass: Float = Float.NaN) {
    out.setLength(0)
    val speed = observer.betaRaw.len() * c
    val properSpeed = rel?.u?.len() ?: (speed * observer.gammaRaw)
    out.append("\u03B2 = "); appendFixed(out, observer.betaRaw.len(), 3).append('\n')
    out.append("\u03B3 = "); appendFixed(out, observer.gammaRaw, 2).append('\n')
    out.append("\u03C4 = "); appendFixed(out, observer.tau, 1).append(" s\n")
    out.append("world t = "); appendFixed(out, observer.worldTime, 1).append(" s\n")
    out.append("|v| = "); appendFixed(out, speed, 1).append(" u/s (")
    appendFixed(out, observer.betaRaw.len() * 100f, 1).append(" % c)\n")
    out.append("v = ("); appendFixed(out, observer.betaRaw.x * c, 1).append(", ")
    appendFixed(out, observer.betaRaw.y * c, 1).append(") u/s\n")
    if (mass.isFinite()) {
        out.append("m = "); appendFixed(out, mass, 1).append("   \u03B3\u00B7m = ")
        appendFixed(out, mass * observer.gammaRaw, 1).append('\n')
    }
    out.append("|u| = \u03B3|v| = "); appendFixed(out, properSpeed, 1).append(" u/s\n")
    out.append("ship-frame speed = "); appendFixed(out, observer.rapidity * c, 1).append(" u/s\n")
    out.append("c = "); appendFixed(out, c, 0).append("   a0 = "); appendFixed(out, a0, 0)
    if (clock != null) {
        out.append("\nwarp = ×"); appendFixed(out, clock.warp, 1)
        if (clock.warpCapped || clock.stepsCapped) out.append(" (capped)")
    }
}

/** Restores the const defaults for the tunables shown in the panel. */
fun resetDebugDefaults(relativity: RelativitySettings, debug: DebugSettings) {
    relativity.relativityEnabled = RELATIVITY_ENABLED
    relativity.fieldEnabled = true
    relativity.c = SPEED_OF_LIGHT
    relativity.engineAccelPerC = ENGINE_ACCEL_PER_C
    relativity.brakeBoost = BRAKE_BOOST_K
    relativity.fieldDamping = FIELD_DAMPING
    relativity.gammaMaxMassive = GAMMA_MAX_MASSIVE
    relativity.timeWarpEnabled = TIME_WARP_ENABLED
    relativity.timeWarpMax = TIME_WARP_MAX
    relativity.pilotView = PILOT_VIEW_DEFAULT
    relativity.pilotAberration = true
    relativity.pilotDelay = true
    relativity.gammaMassEnabled = GAMMA_MASS_ENABLED
    relativity.photonDopplerDamage = true
    relativity.gravityEnabled = GRAVITY_ENABLED
    relativity.sourceCoupling = SOURCE_COUPLING_DEFAULT
    relativity.playerGm = PLAYER_GM
    relativity.blackHolesEnabled = true
    relativity.enemiesEnabled = true
    relativity.planetsEnabled = true
    relativity.decorationEnabled = true
    relativity.lightingEnabled = true
    relativity.shadowsEnabled = true
    relativity.starLightEnabled = true
    relativity.lensingEnabled = true
    relativity.tidalEnabled = true
    debug.timeScale = 1f
    debug.showVectors = false
    debug.gridEnabled = GRID_ENABLED
    debug.targetPointersEnabled = TARGET_POINTERS_ENABLED
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
    // Scrolls vertically when the widgets are taller than the window; the stage height bounds it.
    private val scroll = ScrollPane(root, skin).apply {
        setFadeScrollBars(false)
        setScrollingDisabled(true, false)
        setScrollbarsVisible(true)
    }
    private val container = Table().apply {
        setFillParent(true)
        touchable = Touchable.childrenOnly
        right().top()
    }
    private var stage: Stage? = null
    private val readouts = Label("", skin)
    private val text = StringBuilder()
    private val syncs = ArrayList<() -> Unit>()
    private lateinit var controlsSelect: SelectBox<String>

    val isVisible: Boolean get() = debugSettings.panelVisible

    init {
        root.background = skin.newDrawable("white", Color(0f, 0f, 0f, PANEL_BACKGROUND_ALPHA))
        root.pad(PANEL_PAD)
        root.defaults().left().pad(2f)
        build()
        scroll.isVisible = debugSettings.panelVisible
        scroll.addListener(object : InputListener() {
            // Wheel scrolls the panel only while the pointer is over it, so zoom keeps working elsewhere.
            override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (pointer == -1) stage?.scrollFocus = scroll
            }

            override fun exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (pointer == -1 && stage?.scrollFocus == scroll) stage?.scrollFocus = null
            }
        })
        container.add(scroll).width(DEBUG_PANEL_WIDTH).growY().top().right()
    }

    /** Panel width in window pixels, from the stage viewport (stage units may not be 1:1 with pixels). */
    fun widthInScreenPx(): Float {
        val vp = stage?.viewport ?: return DEBUG_PANEL_WIDTH
        return DEBUG_PANEL_WIDTH * vp.screenWidth / vp.worldWidth
    }

    private fun build() {
        root.add(readouts).colspan(2).left().row()
        readouts.addTooltip("ship-frame speed = rapidity * c. For straight-line thrust from rest it equals a0 * tau, " +
            "what the pilot's own accelerometer would integrate.")

        checkBox("relativity", { relativitySettings.relativityEnabled }) { relativitySettings.relativityEnabled = it }
        checkBox("γ-mass", { relativitySettings.gammaMassEnabled }) { relativitySettings.gammaMassEnabled = it }
        checkBox("photon Doppler dmg", { relativitySettings.photonDopplerDamage }) { relativitySettings.photonDopplerDamage = it }
        checkBox("boundary field", { relativitySettings.fieldEnabled }) { relativitySettings.fieldEnabled = it }
        checkBox("time warp", { relativitySettings.timeWarpEnabled }) { relativitySettings.timeWarpEnabled = it }
        checkBox("pilot view", { relativitySettings.pilotView }) { relativitySettings.pilotView = it }
        checkBox("aberration", { relativitySettings.pilotAberration }) { relativitySettings.pilotAberration = it }
        checkBox("light delay", { relativitySettings.pilotDelay }) { relativitySettings.pilotDelay = it }
        checkBox("gravity", { relativitySettings.gravityEnabled }) { relativitySettings.gravityEnabled = it }
        checkBox("black holes (next level)", { relativitySettings.blackHolesEnabled }) { relativitySettings.blackHolesEnabled = it }
        checkBox("enemies (next level)", { relativitySettings.enemiesEnabled }) { relativitySettings.enemiesEnabled = it }
        checkBox("planets (next level)", { relativitySettings.planetsEnabled }) { relativitySettings.planetsEnabled = it }
        checkBox("debris (next level)", { relativitySettings.decorationEnabled }) { relativitySettings.decorationEnabled = it }
        checkBox("lighting", { relativitySettings.lightingEnabled }) { relativitySettings.lightingEnabled = it }
        checkBox("shadows", { relativitySettings.shadowsEnabled }) { relativitySettings.shadowsEnabled = it }
        checkBox("star light", { relativitySettings.starLightEnabled }) { relativitySettings.starLightEnabled = it }
        checkBox("lensing", { relativitySettings.lensingEnabled }) { relativitySettings.lensingEnabled = it }
        checkBox("tidal", { relativitySettings.tidalEnabled }) { relativitySettings.tidalEnabled = it }
        checkBox("grid", { debugSettings.gridEnabled }) { debugSettings.gridEnabled = it }
        checkBox("target pointers", { debugSettings.targetPointersEnabled }) { debugSettings.targetPointersEnabled = it }
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

        slider("c", C_MIN, C_MAX_BOX2D_SAFE, 1f, 0, { relativitySettings.c }) { relativitySettings.c = it }
        slider("a0 / c", ENGINE_ACCEL_PER_C_MIN, ENGINE_ACCEL_PER_C_MAX, 0.01f, 2, { relativitySettings.engineAccelPerC }) { relativitySettings.engineAccelPerC = it }
        slider("brake boost", 0f, 5f, 0.1f, 1, { relativitySettings.brakeBoost }) { relativitySettings.brakeBoost = it }
        slider("field damping", 0f, 5f, 0.1f, 1, { relativitySettings.fieldDamping }) { relativitySettings.fieldDamping = it }
        slider("warp cap", 1f, TIME_WARP_MAX_LIMIT, 1f, 0, { relativitySettings.timeWarpMax }) { relativitySettings.timeWarpMax = it }
        slider("source coupling", 0f, 1f, 0.01f, 2, { relativitySettings.sourceCoupling }) { relativitySettings.sourceCoupling = it }
        slider("player GM", 0f, PLAYER_GM_MAX, 1f, 0, { relativitySettings.playerGm }) { relativitySettings.playerGm = it }
        slider("γ cap", GAMMA_MAX_MIN, GAMMA_MAX_MASSIVE, 1f, 0, { relativitySettings.gammaMaxMassive }) { relativitySettings.gammaMaxMassive = it }

        val select = SelectBox<String>(skin)
        controlsSelect = select
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
        val look = styleOnOffCheckBox(box, " $title: ON", " $title: off")
        box.isChecked = get()
        look()
        box.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                set(box.isChecked)
            }
        })
        syncs.add { box.isChecked = get(); look() }
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
        this.stage = stage
        stage.addActor(container)
    }

    fun hide() {
        controlsSelect.hideList()
        container.remove()
        stage = null
    }

    override fun toggleDebugPanel() {
        val visible = flipPanelVisible(debugSettings)
        if (!visible) controlsSelect.hideList()
        scroll.isVisible = visible
        onVisibilityChanged(visible)
    }

    /** Refreshes the readout label. Called at 10 Hz by DebugPanelSystem. */
    fun update(observer: ObserverState, rel: RelativisticComponent?, clock: GameClock? = null, mass: Float = Float.NaN) {
        formatReadouts(observer, rel, relativitySettings.c, relativitySettings.engineProperAccel, text, clock, mass)
        readouts.setText(text)
    }
}
