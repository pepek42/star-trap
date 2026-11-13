package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.save.saveAfterLevel
import com.github.pepek42.asteroids.scenario.applyScenarioPhysics
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.ui.MENU_BUTTON_HEIGHT
import com.github.pepek42.asteroids.ui.MENU_BUTTON_WIDTH
import com.github.pepek42.asteroids.ui.MenuStyle
import com.github.pepek42.asteroids.ui.panelContent
import com.github.pepek42.asteroids.weapon.FIRST_PICK_LEVEL
import com.github.pepek42.asteroids.weapon.FIRST_PICK_OPTIONS
import com.github.pepek42.asteroids.weapon.WeaponType
import com.github.pepek42.asteroids.weapon.weaponSlotOf
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger

private const val PANEL_WIDTH = 520f
private const val SECTION_SPACE = 18f
private const val BUTTON_SPACE = 16f
/** Fade-in time (s) of the panel. */
private const val FADE_IN_S = 0.35f

/**
 * Between levels: a centred panel with the result and a primary "Next level" button
 * (or "Retry" / "Scenarios" after a scenario). The run is saved here (normal runs only).
 */
class LevelClearedScreen(
    private val game: Game,
) : KtxScreen {
    private val i18n = game.get<I18NBundle>()
    private val gameState: GameState = game.get<GameState>()
    private val stage = game.get<Stage>()
    private val style = MenuStyle(game.get<Skin>())
    private val gameEventManager = game.get<GameEventManager>()

    private val title = Label("", style.title)
    private val subtitle = style.paragraph("", PANEL_WIDTH, style.muted)
    private val secondaryButton = TextButton("", style.button)
    private val primaryButton = TextButton("", style.primaryButton)
    private var onPrimary: () -> Unit = {}
    private var onSecondary: () -> Unit = {}

    private val root = Table().apply {
        setFillParent(true)
        touchable = Touchable.enabled
    }

    /** Weapon pick (ST-183): a button and a one-line description per option, shown after level 1 until one is taken. */
    private val pickTitle = Label("", style.title)
    private val pickNote = style.paragraph("", PANEL_WIDTH, style.muted)
    private val weaponNote = style.paragraph("", PANEL_WIDTH, style.muted)
    private val pickButtons = FIRST_PICK_OPTIONS.associateWith { TextButton("", style.button) }
    private val pickRows = Table()

    private val panel = style.panel()
    private val buttons = Table().apply {
        add(secondaryButton).height(MENU_BUTTON_HEIGHT).left()
        add().expandX()
        add(primaryButton).height(MENU_BUTTON_HEIGHT).minWidth(MENU_BUTTON_WIDTH).right()
    }

    init {
        root.add(panel)
        primaryButton.onChange { onPrimary() }
        secondaryButton.onChange { onSecondary() }
        for (entry in pickButtons.entries) entry.value.onChange { pick(entry.key) }
    }

    private fun weaponName(weapon: WeaponType) = i18n["weapon_" + weapon.name.lowercase()]

    private fun pick(weapon: WeaponType) {
        if (!gameState.pickWeapon(weapon)) return
        saveRun()
        showWeaponState()
    }

    /** Pick rows while a pick is pending (Next level locked), else the unlocked-weapon note. */
    private fun showWeaponState(newWeapon: WeaponType? = null) {
        val pending = gameState.scenario == null && gameState.weaponPickPending
        pickRows.isVisible = pending
        pickTitle.isVisible = pending
        pickNote.isVisible = pending
        primaryButton.isDisabled = pending
        primaryButton.touchable = if (pending) Touchable.disabled else Touchable.enabled
        val shown = newWeapon ?: lastPicked
        weaponNote.isVisible = shown != null
        if (shown != null) {
            val key = if (newWeapon != null) "weapon_new_unlocked" else "weapon_picked"
            weaponNote.setText(i18n.format(key, weaponName(shown), weaponSlotOf(shown) + 1))
        }
        panel.invalidateHierarchy()
    }

    /** The weapon taken from the pick on this screen, for the note. */
    private val lastPicked: WeaponType?
        get() = FIRST_PICK_OPTIONS.firstOrNull {
            it in gameState.unlockedWeapons && gameState.scenario == null && gameState.level == FIRST_PICK_LEVEL
        }

    /** Fills the panel: title, subtitle and the buttons. */
    private fun layoutPanel() {
        val t = panel.panelContent
        t.clearChildren()
        t.defaults().left().width(PANEL_WIDTH)
        t.add(title).row()
        t.add(subtitle).padTop(4f).row()
        pickRows.clearChildren()
        pickTitle.setText(i18n["weapon_pick_title"])
        pickNote.setText(i18n["weapon_pick_needed"])
        for (entry in pickButtons.entries) {
            val weapon = entry.key
            val button = entry.value
            val key = "weapon_pick_" + weapon.name.lowercase()
            button.setText(i18n[key])
            pickRows.add(button).width(MENU_BUTTON_WIDTH).height(MENU_BUTTON_HEIGHT).left().top()
            pickRows.add(style.paragraph(i18n[key + "_desc"], PANEL_WIDTH - MENU_BUTTON_WIDTH - BUTTON_SPACE, style.muted))
                .padLeft(BUTTON_SPACE).left().row()
            pickRows.getCell(button).padBottom(BUTTON_SPACE)
        }
        t.add(pickTitle).padTop(SECTION_SPACE).row()
        t.add(pickRows).padTop(4f).row()
        t.add(pickNote).padTop(4f).row()
        t.add(weaponNote).padTop(SECTION_SPACE).row()
        t.add(buttons).padTop(SECTION_SPACE * 2).row()
    }

    private fun saveRun() = saveAfterLevel(game.get<KeyValueStore>(), gameState, game.get<MenuSettings>().runSpecs)

    override fun show() {
        val scenario = gameState.scenario
        if (scenario == null) {
            val granted = gameState.grantRemainingWeapon()
            // With a pick pending the save waits for the pick, so Continue never resumes a run with no choice made.
            if (!gameState.weaponPickPending) saveRun()
            title.setText(i18n.format("level_cleared_message", gameState.level))
            subtitle.setText(i18n.format("level_cleared_next", gameState.level + 1))
            layoutPanel()
            primaryButton.setText(i18n.format("next_level_button", gameState.level + 1))
            secondaryButton.setText(i18n["menu_quit_run"])
            onPrimary = { game.setScreen<PlayScreen>() }
            onSecondary = { toMenu(MenuPage.MAIN) }
            showWeaponState(granted)
        } else {
            title.setText(i18n["scenario_cleared_message"])
            subtitle.setText(i18n[scenario.titleKey])
            layoutPanel()
            primaryButton.setText(i18n["scenario_retry"])
            secondaryButton.setText(i18n["menu_scenarios"])
            onPrimary = {
                // The scenario's physics overrides are still in force; reapply in case anything changed them.
                applyScenarioPhysics(scenario.physics, game.get<RelativitySettings>())
                gameState.startScenario(scenario)
                game.setScreen<PlayScreen>()
            }
            onSecondary = { toMenu(MenuPage.SCENARIOS) }
            showWeaponState()
        }
        gameEventManager.ignorePlayerInputs()
        root.clearActions()
        root.color.a = 0f
        root.addAction(Actions.fadeIn(FADE_IN_S))
        stage.addActor(root)
    }

    private fun toMenu(page: MenuPage) {
        game.get<MainMenuScreen>().nextPage = page
        gameState.resetGame()
        game.setScreen<MainMenuScreen>()
    }

    private fun Actor.onChange(block: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = block()
        })
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resizing screen to ${width}x$height" }
        stage.viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        stage.run {
            viewport.apply()
            act()
            draw()
        }
    }

    override fun hide() {
        root.remove()
    }

    companion object {
        private val logger = logger<LevelClearedScreen>()
    }
}
