package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.scenario.applyScenarioPhysics
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.ui.MENU_BUTTON_HEIGHT
import com.github.pepek42.asteroids.ui.MENU_BUTTON_WIDTH
import com.github.pepek42.asteroids.ui.MenuStyle
import com.github.pepek42.asteroids.ui.panelContent
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger

private const val PANEL_WIDTH = 520f
private const val SECTION_SPACE = 18f
private const val BUTTON_SPACE = 16f
/** Fade-in time (s) of the panel. */
private const val FADE_IN_S = 0.35f

/**
 * Between levels: a centred panel with the result, a placeholder for the shop, and a primary "Next level" button
 * (or "Retry" / "Scenarios" after a scenario). The run is saved here once saving exists.
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
    private val shopTitle = Label(i18n["shop_title"], style.heading)
    private val shopText = style.paragraph(i18n["shop_placeholder"], PANEL_WIDTH, style.muted)
    private val secondaryButton = TextButton("", style.button)
    private val primaryButton = TextButton("", style.primaryButton)
    private var onPrimary: () -> Unit = {}
    private var onSecondary: () -> Unit = {}

    private val root = Table().apply {
        setFillParent(true)
        touchable = Touchable.enabled
    }

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
    }

    /** Fills the panel; the shop section only between levels of a run. */
    private fun layoutPanel(withShop: Boolean) {
        val t = panel.panelContent
        t.clearChildren()
        t.defaults().left().width(PANEL_WIDTH)
        t.add(title).row()
        t.add(subtitle).padTop(4f).row()
        if (withShop) {
            t.add(Image(style.rule)).height(1f).padTop(SECTION_SPACE).padBottom(SECTION_SPACE).row()
            t.add(shopTitle).row()
            t.add(shopText).padTop(4f).row()
        }
        t.add(buttons).padTop(SECTION_SPACE * 2).row()
    }

    override fun show() {
        val scenario = gameState.scenario
        if (scenario == null) {
            title.setText(i18n.format("level_cleared_message", gameState.level))
            subtitle.setText(i18n.format("level_cleared_next", gameState.level + 1))
            layoutPanel(withShop = true)
            primaryButton.setText(i18n.format("next_level_button", gameState.level + 1))
            secondaryButton.setText(i18n["menu_quit_run"])
            onPrimary = { game.setScreen<PlayScreen>() }
            onSecondary = { toMenu(MenuPage.MAIN) }
        } else {
            title.setText(i18n["scenario_cleared_message"])
            subtitle.setText(i18n[scenario.titleKey])
            layoutPanel(withShop = false)
            primaryButton.setText(i18n["scenario_retry"])
            secondaryButton.setText(i18n["menu_scenarios"])
            onPrimary = {
                // The scenario's physics overrides are still in force; reapply in case anything changed them.
                applyScenarioPhysics(scenario.physics, game.get<RelativitySettings>())
                gameState.startScenario(scenario)
                game.setScreen<PlayScreen>()
            }
            onSecondary = { toMenu(MenuPage.SCENARIOS) }
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
