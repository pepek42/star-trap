package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.component.select
import com.github.pepek42.asteroids.component.slot
import com.github.pepek42.asteroids.component.tickInactive
import com.github.pepek42.asteroids.component.WEAPON_SLOT_COUNT
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.weapon.TargetLockState
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.scene2d.label
import ktx.scene2d.progressBar
import ktx.scene2d.scene2d
import kotlin.math.acosh
import kotlin.math.ceil
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

/** `tidalRatio` below this hides the tidal stress readout (ST-162). */
const val TIDAL_READOUT_MIN = 0.25f

/** Percent shown by the tidal readout, or -1 when it is hidden (`ratio <= TIDAL_READOUT_MIN`). */
fun tidalReadoutPercent(ratio: Float): Int = if (ratio > TIDAL_READOUT_MIN) (ratio * 100f).roundToInt() else -1

/** The readout is red while the hull takes damage (`ratio > 1`, ST-153). */
fun tidalReadoutDamaging(ratio: Float): Boolean = ratio > 1f

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
    private val tidalLabel: Label = scene2d.label("").apply { isVisible = false }
    private val weaponLabels: Array<Label> = Array(WEAPON_SLOT_COUNT) { scene2d.label("").apply { isVisible = false } }
    private val weaponRow = HorizontalGroup().apply { space(8f) }
    private var weaponShownActive = -1
    private var weaponShownAmmo = -1
    private var weaponShownRocketKey = Int.MIN_VALUE
    private var weaponShownUnlocked = -1
    private val lockLabel: Label = scene2d.label("").apply { isVisible = false }
    private var lockShown: TargetLockState? = null
    private var tidalShownPercent = -1
    private var tidalShownRed = false
    private val levelLabel: Label = scene2d.label("")
    private val centerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val pointerLabel: Label = scene2d.label("").apply { isVisible = false }
    private val table: Table = Table()
    private val pointerPos = Vector2()
    private val targetsTitle: Label = scene2d.label("")
    private val asteroidLabels: Array<Label> = Array(AsteroidSize.entries.size) { scene2d.label("") }
    private val enemiesLabel: Label = scene2d.label("").apply { color.set(ENEMY_COUNT_COLOR); isVisible = false }
    private val shownTargets = TargetCounts()
    private var targetsShownOnce = false
    /** True once this level had an enemy ship, so the row stays (at 0) after the last one dies. */
    private var levelHasEnemies = false

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
            addActor(tidalLabel)
            addActor(weaponRow)
            columnAlign(Align.topLeft)
            top().left()
            pad(5f, 5f, 0f, 0f)
        }
        weaponRow.addActor(scene2d.label(i18NBundle.get("hud_weapon")))
        weaponLabels.forEach { weaponRow.addActor(it) }
        weaponRow.addActor(lockLabel)
        targetsTitle.setText(i18NBundle.get("hud_targets_title"))
        val targetsGroup = VerticalGroup().apply {
            addActor(targetsTitle)
            // Biggest asteroids first.
            AsteroidSize.entries.sortedByDescending { it.radius }.forEach { addActor(asteroidLabels[it.ordinal]) }
            addActor(enemiesLabel)
            columnAlign(Align.topRight)
            top().right()
            pad(5f, 0f, 0f, 5f)
        }
        table.add(verticalGroup).top().left()
        table.add()
        table.add(targetsGroup).top().right()
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

    /** Weapon slots line; the active weapon is highlighted (ST-163). The lock-on slot shows its ammo (ST-164), the rocket slot its pod (ST-181) and a locked slot is dim (ST-183). */
    fun updateWeapons(loadout: WeaponLoadoutComponent) {
        updateLock(loadout.lockState)
        val rocketKey = rocketReadoutKey(loadout.rocketsInPod, loadout.rocketReloadLeft)
        val unlockedMask = (if (loadout.type1 != null) 1 else 0) or (if (loadout.type2 != null) 2 else 0)
        if (unlockedMask == weaponShownUnlocked && loadout.activeSlot == weaponShownActive && loadout.lockOnAmmo == weaponShownAmmo && rocketKey == weaponShownRocketKey) return
        weaponShownUnlocked = unlockedMask
        weaponShownActive = loadout.activeSlot
        weaponShownAmmo = loadout.lockOnAmmo
        weaponShownRocketKey = rocketKey
        for (i in 0 until WEAPON_SLOT_COUNT) {
            val type = loadout.slot(i)
            val label = weaponLabels[i]
            label.isVisible = true
            if (type == null) { // locked (ST-183): the slot stays, dimmed and empty
                label.setText(i18NBundle.format("hud_weapon_slot_locked", i + 1))
                label.color.set(Color.DARK_GRAY)
                continue
            }
            val name = i18NBundle.get("weapon_" + type.name.lowercase())
            label.setText(
                if (type == WeaponType.LOCK_ON) {
                    i18NBundle.format("hud_weapon_slot_ammo", name, i + 1, loadout.lockOnAmmo)
                } else if (type == WeaponType.ROCKET) {
                    if (loadout.rocketsInPod > 0) {
                        i18NBundle.format("hud_weapon_slot_ammo", name, i + 1, loadout.rocketsInPod)
                    } else {
                        i18NBundle.format("hud_weapon_slot_reload", name, i + 1, ceil(loadout.rocketReloadLeft).toInt())
                    }
                } else {
                    i18NBundle.format("hud_weapon_slot", name, i + 1)
                }
            )
            label.color.set(if (i == weaponShownActive) WEAPON_ACTIVE_COLOR else Color.GRAY)
        }
        weaponRow.invalidateHierarchy()
    }

    /**
     * Change key of the rocket readout (ST-181): the rocket count while the pod has some, else minus the reload seconds
     * left (rounded up) minus one, so the label is rebuilt when the count or the displayed second changes, not every frame.
     */
    private fun rocketReadoutKey(rockets: Int, reloadLeft: Float): Int =
        if (rockets > 0) rockets else -1 - ceil(reloadLeft).toInt()

    /** "Locking…" / "LOCKED" next to the weapon slots (ST-165); rebuilt only when the state changes. */
    private fun updateLock(state: TargetLockState) {
        if (state == lockShown) return
        lockShown = state
        lockLabel.isVisible = state != TargetLockState.NONE
        when (state) {
            TargetLockState.LOCKING -> {
                lockLabel.setText(i18NBundle.get("hud_lock_locking"))
                lockLabel.color.set(TARGET_LOCKING_COLOR)
            }
            TargetLockState.LOCKED -> {
                lockLabel.setText(i18NBundle.get("hud_lock_locked"))
                lockLabel.color.set(TARGET_LOCKED_COLOR)
            }
            TargetLockState.NONE -> {}
        }
        weaponRow.invalidateHierarchy()
    }

    /** Tidal stress readout (ST-162): hidden at or below [TIDAL_READOUT_MIN]; the text is rebuilt only when the percent changes. */
    fun updateTidal(ratio: Float) {
        val percent = tidalReadoutPercent(ratio)
        tidalLabel.isVisible = percent >= 0
        if (percent < 0) {
            tidalShownPercent = -1
            return
        }
        if (percent != tidalShownPercent) {
            tidalShownPercent = percent
            tidalLabel.setText(i18NBundle.format("hud_tidal", percent))
        }
        val red = tidalReadoutDamaging(ratio)
        if (red != tidalShownRed) {
            tidalShownRed = red
            tidalLabel.color.set(if (red) Color.RED else Color.WHITE)
        }
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

    fun updateLevel(level: Int) = updateTitle(i18NBundle.format("hud_level", level))

    /** Top-left title of a new level ("Level 3", or a scenario's name); resets the per-level target rows. */
    fun updateTitle(text: String) {
        levelLabel.setText(text)
        levelHasEnemies = false
        targetsShownOnce = false
    }

    /** Targets-left counter (top right): one row per asteroid size and one for enemy ships; text rebuilt only on change. */
    fun updateTargets(counts: TargetCounts) {
        if (targetsShownOnce && counts.sameAs(shownTargets)) return
        targetsShownOnce = true
        shownTargets.copyFrom(counts)
        for (size in AsteroidSize.entries) {
            asteroidLabels[size.ordinal].setText(i18NBundle.format(asteroidCountKey(size), counts.asteroids[size.ordinal]))
        }
        if (counts.enemies > 0) levelHasEnemies = true
        enemiesLabel.isVisible = levelHasEnemies
        enemiesLabel.setText(i18NBundle.format("hud_enemies", counts.enemies))
    }

    companion object {
        private const val MAX_DELTA_TIME = 1f / 30
        private const val POINTER_LABEL_OFFSET = 40f
        private val WEAPON_ACTIVE_COLOR = Color(1f, 0.9f, 0.3f, 1f)
        private val ENEMY_COUNT_COLOR = Color(1f, 0.35f, 0.35f, 1f)
    }
}
