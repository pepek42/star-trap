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
import com.github.pepek42.asteroids.save.clearShopVisit
import com.github.pepek42.asteroids.save.saveAfterLevel
import com.github.pepek42.asteroids.shop.shopSeed
import com.github.pepek42.asteroids.shop.FREE_REROLLS
import com.github.pepek42.asteroids.shop.ShopSession
import com.badlogic.gdx.math.RandomXS128
import com.github.pepek42.asteroids.scenario.applyScenarioPhysics
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
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
 * Between levels: a centred panel with the result, the shop, and a primary "Next level" button
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
    private val shopTitle = Label(i18n["shop_title"], style.heading)
    private val creditsLabel = Label("", style.body)
    private val shopItems = Table()
    private val rerollButton = TextButton("", style.button)
    private var shop: ShopSession? = null
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
        rerollButton.onChange { shop?.reroll()?.let { refreshShop() } }
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
            t.add(creditsLabel).padTop(4f).row()
            t.add(shopItems).padTop(8f).row()
            t.add(rerollButton).height(MENU_BUTTON_HEIGHT).padTop(BUTTON_SPACE).left().row()
        }
        t.add(buttons).padTop(SECTION_SPACE * 2).row()
    }

    private fun saveRun() = saveAfterLevel(game.get<KeyValueStore>(), gameState, game.get<MenuSettings>().runSpecs, shop?.visit())

    /** Rebuilds the credits line, the offered items and the reroll button from the [shop] session. */
    private fun refreshShop() {
        val session = shop ?: return
        creditsLabel.setText(i18n.format("shop_credits", gameState.credits))
        shopItems.clearChildren()
        shopItems.defaults().left().width(PANEL_WIDTH)
        if (session.offer.isEmpty()) shopItems.add(style.paragraph(i18n["shop_empty"], PANEL_WIDTH, style.muted)).row()
        session.offer.forEach { item ->
            val buy = TextButton(i18n.format("shop_buy", gameState.upgrades.price(item)), style.button)
            buy.isDisabled = !session.canBuy(item)
            buy.onChange { if (session.buy(item)) refreshShop() }
            val text = Table().apply {
                add(Label(i18n[item.titleKey], style.heading)).left().row()
                add(Label(i18n[item.descKey], style.muted)).left().row()
                add(Label(i18n.format("shop_item_level", gameState.upgrades.level(item), item.maxLevel), style.muted)).left().row()
            }
            val row = Table()
            row.add(text).left().expandX().fillX()
            row.add(buy).height(MENU_BUTTON_HEIGHT).right()
            shopItems.add(row).padBottom(BUTTON_SPACE / 2).row()
        }
        val cost = session.rerollCost()
        rerollButton.setText(
            if (cost == 0) i18n.format("shop_reroll_free", FREE_REROLLS - session.rerollsUsed)
            else i18n.format("shop_reroll_cost", cost)
        )
        rerollButton.isDisabled = !session.canReroll()
    }

    override fun show() {
        val scenario = gameState.scenario
        if (scenario == null) {
            val resume = gameState.pendingShop
            gameState.pendingShop = null
            shop = ShopSession(gameState, RandomXS128(shopSeed(gameState.runSeed, gameState.level)), resume, ::saveRun)
            saveRun()
            if (gameState.quickStart) {
                title.setText(i18n["quick_start_message"])
                subtitle.setText(i18n.format("quick_start_next", gameState.level + 1))
            } else {
                title.setText(i18n.format("level_cleared_message", gameState.level))
                subtitle.setText(i18n.format("level_cleared_next", gameState.level + 1))
            }
            gameState.quickStart = false
            layoutPanel(withShop = true)
            refreshShop()
            primaryButton.setText(i18n.format("next_level_button", gameState.level + 1))
            secondaryButton.setText(i18n["menu_quit_run"])
            onPrimary = {
                // The visit is over: Continue after quitting mid-level must go to the level, not the shop.
                clearShopVisit(game.get<KeyValueStore>())
                game.setScreen<PlayScreen>()
            }
            onSecondary = { toMenu(MenuPage.MAIN) }
        } else {
            title.setText(i18n["scenario_cleared_message"])
            subtitle.setText(i18n[scenario.titleKey])
            shop = null
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
