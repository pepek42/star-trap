package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.FIRST_LEVEL
import com.github.pepek42.asteroids.START_LEVELS
import com.github.pepek42.asteroids.TITLE_NAME
import com.github.pepek42.asteroids.TITLE_SUBTITLE
import com.github.pepek42.asteroids.ui.MENU_ACCENT_COLOR
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.InputBindings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.save.RunSave
import com.github.pepek42.asteroids.save.applyRunSave
import com.github.pepek42.asteroids.save.clearRunSave
import com.github.pepek42.asteroids.save.continueOpensShop
import com.github.pepek42.asteroids.save.readRunSave
import com.github.pepek42.asteroids.save.restoreRunProgress
import com.github.pepek42.asteroids.scenario.Scenario
import com.github.pepek42.asteroids.scenario.Scenarios
import com.github.pepek42.asteroids.scenario.applyScenarioPhysics
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
import com.github.pepek42.asteroids.settings.SettingCategory
import com.github.pepek42.asteroids.settings.SettingSpec
import com.github.pepek42.asteroids.settings.loadSettings
import com.github.pepek42.asteroids.settings.resetSettings
import com.github.pepek42.asteroids.settings.saveSettings
import com.github.pepek42.asteroids.ui.MENU_BUTTON_HEIGHT
import com.github.pepek42.asteroids.ui.MENU_BUTTON_WIDTH
import com.github.pepek42.asteroids.ui.MenuStyle
import com.github.pepek42.asteroids.ui.controlsRows
import com.github.pepek42.asteroids.ui.formatSettingValue
import com.github.pepek42.asteroids.ui.hintTooltip
import com.github.pepek42.asteroids.ui.hintTooltipManager
import com.github.pepek42.asteroids.ui.panelContent
import com.github.pepek42.asteroids.ui.styleOnOffCheckBox
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger

private const val MENU_PAD = 24f
private const val COLUMN_SPACE = 20f
private const val ROW_SPACE = 8f
private const val BUTTON_SPACE = 12f
private const val SECTION_SPACE = 22f
/** Room for the vertical scroll bar next to a pane's content. */
private const val SCROLLBAR_ALLOWANCE = 28f
private const val SLIDER_WIDTH = 160f
/** Letter gap (px) of the "REDSHIFT" line under the title. */
private const val SUBTITLE_LETTER_GAP = 8f

/** Red end of the subtitle's Doppler gradient. */
private val REDSHIFT_END_COLOR = Color(1f, 0.22f, 0.16f, 1f)
/** Width (px) of wrapped description text on the New game and Scenarios pages. */
private const val TEXT_WIDTH = 440f
/** Width (px) of the scenario list buttons. */
private const val SCENARIO_BUTTON_WIDTH = 240f
/** Vertical room (px) the Settings and Scenarios pages keep for their title and button rows outside the scroll panes. */
private const val PAGE_CHROME_HEIGHT = 230f
/** Vertical room (px) the tab bar takes in the Settings panel (button height plus its gap). */
private const val TAB_BAR_HEIGHT = MENU_BUTTON_HEIGHT + ROW_SPACE
private val WARNING_COLOR = Color(1f, 0.55f, 0.4f, 1f)

/** Main menu pages. */
enum class MenuPage { MAIN, NEW_GAME, SCENARIOS, SETTINGS }

/**
 * Main menu: a title page with New game / Scenarios / Settings / Exit, each opening its own page.
 * New game picks the start level and the run's physics (fixed for the run); Settings holds the global settings,
 * which apply to every run at once.
 */
class MainMenuScreen(private val game: Game) : KtxScreen {
    private val i18n = game.get<I18NBundle>()
    private val stage = game.get<Stage>()
    private val skin = game.get<Skin>()
    private val gameState = game.get<GameState>()
    private val store = game.get<KeyValueStore>()
    private val menuSettings = game.get<MenuSettings>()
    private val specs = menuSettings.specs
    private val controls = game.get<ControlSettings>()
    private val debug = game.get<DebugSettings>()
    private val relativity = game.get<RelativitySettings>()
    private val style = MenuStyle(skin)

    private val tooltips = hintTooltipManager()
    private val syncs = ArrayList<() -> Unit>()
    private var syncing = false
    private val runEndedLabel = Label("", Label.LabelStyle(style.body.font, WARNING_COLOR))
    private var startLevel = FIRST_LEVEL
    private val continueButton = TextButton("", style.primaryButton)
    private val overwriteWarning = style.paragraph(i18n["new_game_overwrite_warning"], TEXT_WIDTH, Label.LabelStyle(style.body.font, WARNING_COLOR))
    private val startButton = button("menu_start", primary = true) { startRun() }
    /** The save found when the menu opened, or null. */
    private var runSave: RunSave? = null
    /** Set by the first Start click while a save exists; the second click overwrites it. */
    private var overwriteConfirmed = false
    private var selectedScenario: Scenario = Scenarios.ALL.first()

    /** The page [show] opens; reset to [MenuPage.MAIN] after use. Set before switching to this screen. */
    var nextPage: MenuPage = MenuPage.MAIN

    /** The open Settings tab. The screen is a singleton, so it is kept for the session (not persisted). */
    var settingsTab: SettingCategory = SettingCategory.entries.first()

    /** Scroll panes take the stage height minus the page's title and button rows. */
    private val scrollMaxHeight = object : Value() {
        override fun get(context: Actor?): Float = stage.height - PAGE_CHROME_HEIGHT
    }

    private val root = Table().apply {
        setFillParent(true)
        touchable = Touchable.enabled
        pad(MENU_PAD)
    }
    private val pages: Map<MenuPage, Actor> = mapOf(
        MenuPage.MAIN to buildMainPage(),
        MenuPage.NEW_GAME to buildNewGamePage(),
        MenuPage.SCENARIOS to buildScenariosPage(),
        MenuPage.SETTINGS to buildSettingsPage(),
    )

    private fun open(page: MenuPage) {
        root.clearChildren()
        root.add(pages.getValue(page))
        syncAll()
    }

    /**
     * The subtitle as spaced letters whose colour slides from the menu's accent blue to red: the word drawn
     * as its own Doppler shift.
     */
    private fun redshiftLogo(text: String): Actor {
        val row = Table()
        val letterStyle = style.logoSub
        val last = (text.length - 1).coerceAtLeast(1)
        val letters = text.mapIndexed { i, ch ->
            Label(ch.toString(), letterStyle).apply {
                setAlignment(Align.center)
                color = Color(MENU_ACCENT_COLOR).lerp(REDSHIFT_END_COLOR, i.toFloat() / last)
            }
        }
        // Equal cells, so narrow letters (I) don't break the rhythm of a proportional font.
        val cell = letters.maxOf { it.prefWidth }
        letters.forEach { row.add(it).width(cell).padLeft(SUBTITLE_LETTER_GAP / 2).padRight(SUBTITLE_LETTER_GAP / 2) }
        return row
    }

    // ---- Main page -------------------------------------------------------------------------------------------

    private fun buildMainPage(): Actor {
        val t = Table()
        t.add(Label(TITLE_NAME.uppercase(), style.logo)).row()
        t.add(redshiftLogo(TITLE_SUBTITLE.uppercase())).padTop(4f).row()
        t.add(style.label(i18n["menu_subtitle"], style.muted)).padTop(10f).padBottom(SECTION_SPACE * 2).row()
        t.defaults().width(MENU_BUTTON_WIDTH).height(MENU_BUTTON_HEIGHT).space(BUTTON_SPACE)
        continueButton.onChange { continueRun() }
        t.add(continueButton).row()
        syncs.add {
            val save = runSave
            continueButton.isVisible = save != null
            if (save != null) continueButton.setText(i18n.format("menu_continue", save.nextLevel))
        }
        val newGameButton = button("menu_new_game", primary = true) { overwriteConfirmed = false; open(MenuPage.NEW_GAME) }
        // Continue is the primary action while a save exists.
        syncs.add { newGameButton.style = if (runSave != null) style.button else style.primaryButton }
        t.add(newGameButton).row()
        t.add(button("menu_scenarios") { open(MenuPage.SCENARIOS) }).row()
        t.add(button("menu_settings") { open(MenuPage.SETTINGS) }).row()
        if (Gdx.app.type != Application.ApplicationType.WebGL) {
            t.add(button("menu_exit") {
                logger.info { "Exiting application" }
                Gdx.app.exit()
            }).row()
        }
        t.add(runEndedLabel).width(Value.prefWidth).height(Value.prefHeight).padTop(SECTION_SPACE).row()
        return t
    }

    // ---- New game page ---------------------------------------------------------------------------------------

    private fun buildNewGamePage(): Actor {
        val panel = style.panel()
        val t = panel.panelContent
        t.defaults().left()
        t.add(Label(i18n["menu_new_game"], style.title)).colspan(2).padBottom(SECTION_SPACE).row()

        t.add(Label(i18n["new_game_start_level"], style.heading)).colspan(2).padBottom(ROW_SPACE).row()
        val levelButtons = Table()
        val group = ButtonGroup<TextButton>().apply { setMinCheckCount(1); setMaxCheckCount(1) }
        val levelHint = style.paragraph("", TEXT_WIDTH, style.muted)
        for (level in START_LEVELS) {
            val b = TextButton(i18n.format("new_game_level", level), style.toggleButton)
            b.onChange { if (b.isChecked) { startLevel = level; levelHint.setText(i18n["new_game_level_${level}_hint"]) } }
            group.add(b)
            levelButtons.add(b).width(MENU_BUTTON_WIDTH * 0.6f).height(MENU_BUTTON_HEIGHT).padRight(BUTTON_SPACE)
        }
        syncs.add { group.buttons[START_LEVELS.indexOf(startLevel)].isChecked = true; levelHint.setText(i18n["new_game_level_${startLevel}_hint"]) }
        t.add(levelButtons).colspan(2).row()
        t.add(levelHint).width(TEXT_WIDTH).colspan(2).padTop(ROW_SPACE).row()

        t.add(divider()).colspan(2).growX().padTop(SECTION_SPACE).padBottom(SECTION_SPACE).row()
        t.add(Label(i18n["new_game_physics"], style.heading)).colspan(2).row()
        t.add(style.paragraph(i18n["new_game_physics_hint"], TEXT_WIDTH, style.muted)).width(TEXT_WIDTH).colspan(2).padBottom(ROW_SPACE).row()
        addSettingRows(t, menuSettings.runSpecs)

        t.add(buttonRow(
            button("menu_back") { open(MenuPage.MAIN) },
            startButton,
        )).colspan(2).growX().padTop(SECTION_SPACE).row()
        t.add(overwriteWarning).width(TEXT_WIDTH).colspan(2).padTop(ROW_SPACE).row()
        syncs.add {
            overwriteWarning.isVisible = runSave != null
            startButton.setText(i18n[if (runSave != null && overwriteConfirmed) "menu_start_confirm" else "menu_start"])
        }
        return panel
    }

    private fun continueRun() {
        val save = runSave ?: return
        // Run physics come from the save; the player's New-game defaults in the store stay untouched.
        applyRunSave(store, menuSettings.runSpecs)
        gameState.resetGame(save.nextLevel)
        restoreRunProgress(save, gameState)
        // level = nextLevel - 1 is the "between levels" state the cleared screen expects; Next level then plays nextLevel.
        if (continueOpensShop(gameState)) game.setScreen<LevelClearedScreen>() else game.setScreen<PlayScreen>()
    }

    private fun startRun() {
        if (runSave != null && !overwriteConfirmed) {
            overwriteConfirmed = true
            syncAll()
            return
        }
        // A new run replaces the old one: drop the save so Esc afterwards doesn't offer a stale Continue.
        clearRunSave(store)
        saveSettings(store, specs)
        gameState.resetGame(startLevel)
        // A quick start opens the shop first (level = startLevel - 1 is the "between levels" state, as for Continue).
        if (gameState.quickStart) game.setScreen<LevelClearedScreen>() else game.setScreen<PlayScreen>()
    }

    // ---- Scenarios page --------------------------------------------------------------------------------------

    private fun buildScenariosPage(): Actor {
        val panel = style.panel()
        val t = panel.panelContent
        t.defaults().left().top()
        t.add(Label(i18n["menu_scenarios"], style.title)).colspan(2).padBottom(SECTION_SPACE).row()

        val list = Table()
        list.defaults().width(SCENARIO_BUTTON_WIDTH).height(MENU_BUTTON_HEIGHT).space(BUTTON_SPACE)
        val group = ButtonGroup<TextButton>().apply { setMinCheckCount(1); setMaxCheckCount(1) }
        val title = Label("", style.heading)
        val summary = style.paragraph("", TEXT_WIDTH, style.muted)
        val briefing = style.paragraph("", TEXT_WIDTH)
        val details = Table().apply {
            defaults().left().width(TEXT_WIDTH)
            add(title).row()
            add(summary).padBottom(ROW_SPACE).row()
            add(briefing).row()
        }
        fun show(s: Scenario) {
            title.setText(i18n[s.titleKey])
            summary.setText(i18n[s.summaryKey])
            briefing.setText(i18n[s.briefingKey])
        }
        for (s in Scenarios.ALL) {
            val b = TextButton(i18n[s.titleKey], style.toggleButton)
            b.label.setAlignment(Align.left)
            b.onChange { if (b.isChecked) { selectedScenario = s; show(s) } }
            group.add(b)
            list.add(b).row()
        }
        syncs.add { group.buttons[Scenarios.ALL.indexOf(selectedScenario)].isChecked = true; show(selectedScenario) }
        t.add(list).padRight(COLUMN_SPACE)
        t.add(scrolled(details)).width(TEXT_WIDTH + SCROLLBAR_ALLOWANCE).maxHeight(scrollMaxHeight).row()

        t.add(buttonRow(
            button("menu_back") { open(MenuPage.MAIN) },
            button("menu_start", primary = true) { startScenario(selectedScenario) },
        )).colspan(2).growX().padTop(SECTION_SPACE).row()
        return panel
    }

    private fun startScenario(scenario: Scenario) {
        // Save first: the scenario's physics overrides are temporary and must not be persisted.
        saveSettings(store, specs)
        applyScenarioPhysics(scenario.physics, relativity)
        gameState.startScenario(scenario)
        game.setScreen<PlayScreen>()
    }

    // ---- Settings page ---------------------------------------------------------------------------------------

    private fun buildSettingsPage(): Actor {
        val panel = style.panel()
        val t = panel.panelContent
        t.defaults().top().left()
        t.add(Label(i18n["menu_settings"], style.title)).padBottom(ROW_SPACE).row()
        t.add(style.paragraph(i18n["menu_settings_hint"], TEXT_WIDTH * 1.5f, style.muted)).width(TEXT_WIDTH * 1.5f).padBottom(ROW_SPACE).row()

        // Tabs are built once; switching swaps the actor in one cell. The cell is sized to the largest tab so the
        // buttons below don't jump. A ScrollPane cell may be shrunk below its content's width, and the content
        // table then shrinks around its centre and clips labels on the left, so the width is pinned to the content.
        val tabs = SettingCategory.entries.associateWith { tabContent(it) }
        val panes = tabs.mapValues { scrolled(it.value) }
        val paneWidth = tabs.values.maxOf { it.prefWidth } + SCROLLBAR_ALLOWANCE
        val paneHeight = tabs.values.maxOf { it.prefHeight }
        val bar = Table()
        val group = ButtonGroup<TextButton>().apply { setMinCheckCount(1); setMaxCheckCount(1) }
        val tabButtons = SettingCategory.entries.associateWith { TextButton(i18n[it.labelKey], style.tabButton) }
        t.add(bar).left().padBottom(ROW_SPACE).row()
        val body = t.add().width(paneWidth).minWidth(paneWidth).height(object : Value() {
            override fun get(context: Actor?): Float = minOf(paneHeight, stage.height - PAGE_CHROME_HEIGHT - TAB_BAR_HEIGHT)
        }).left().top()
        t.row()
        for (category in SettingCategory.entries) {
            val b = tabButtons.getValue(category)
            b.onChange {
                if (!b.isChecked) return@onChange
                settingsTab = category
                body.setActor(panes.getValue(category))
            }
            group.add(b)
            bar.add(b).height(MENU_BUTTON_HEIGHT).padRight(ROW_SPACE)
        }
        syncs.add { tabButtons.getValue(settingsTab).isChecked = true }

        t.add(buttonRow(
            button("menu_back") { open(MenuPage.MAIN) },
            button("menu_reset_defaults") {
                resetSettings(menuSettings.specsIn(settingsTab))
                saveSettings(store, specs)
                syncAll()
            }.also { it.addListener(hintTooltip(i18n["menu_reset_defaults_hint"], tooltips, skin)) },
        )).growX().padTop(SECTION_SPACE).row()
        t.add(style.label(i18n["credits_fonts"], style.muted)).left().padTop(SECTION_SPACE).row()
        return panel
    }

    /** The content of one Settings tab: its setting rows, and for Controls the key bindings list below them. */
    private fun tabContent(category: SettingCategory): Table = Table().also {
        it.top().left()
        it.defaults().top().left()
        addSettingRows(it, menuSettings.specsIn(category))
        if (category == SettingCategory.CONTROLS) it.add(buildControls()).colspan(2).padTop(ROW_SPACE).row()
    }

    private fun buildControls(): Table {
        val t = Table()
        t.top().left()
        t.defaults().left().pad(ROW_SPACE / 4)
        val tr = { key: String -> i18n[key] }
        fun rows(includeDebug: Boolean): List<Pair<String, String>> =
            controlsRows(InputBindings.DEFAULT, controls.scheme, includeDebug, tr)
        var shownScheme: ControlScheme? = null
        var shownDebug: Boolean? = null
        // Rebuilt from syncAll() so the list follows the "Controls" setting.
        fun fill() {
            if (shownScheme == controls.scheme && shownDebug == debug.panelAllowed) return
            shownScheme = controls.scheme
            shownDebug = debug.panelAllowed
            t.clearChildren()
            t.add(Label(i18n["menu_controls"], style.heading)).colspan(4).padBottom(ROW_SPACE).row()
            // Two side-by-side columns of bindings, so the Controls tab fits the panel without scrolling.
            val all = rows(false) + if (debug.panelAllowed) {
                val plain = rows(false).toSet()
                rows(true).filter { it !in plain }
            } else emptyList()
            val perColumn = (all.size + 1) / 2
            for (i in 0 until perColumn) {
                for (row in listOfNotNull(all[i], all.getOrNull(i + perColumn))) {
                    t.add(Label(row.first, style.muted)).padRight(COLUMN_SPACE / 2)
                    t.add(Label(row.second, style.body)).padRight(COLUMN_SPACE)
                }
                t.row()
            }
        }
        fill()
        syncs.add { fill() }
        return t
    }

    // ---- Setting widgets -------------------------------------------------------------------------------------

    private fun addSettingRows(t: Table, list: List<SettingSpec>) {
        for (spec in list) {
            val label = Label(i18n[spec.labelKey], skin)
            label.addListener(hintTooltip(i18n[spec.hintKey], tooltips, skin))
            t.add(label).left().pad(ROW_SPACE / 2).padRight(COLUMN_SPACE)
            val widget: Actor = when (spec) {
                is SettingSpec.Toggle -> toggleWidget(spec)
                is SettingSpec.Slider -> sliderWidget(spec)
                is SettingSpec.Choice -> choiceWidget(spec)
            }
            widget.addListener(hintTooltip(i18n[spec.hintKey], tooltips, skin))
            t.add(widget).left().pad(ROW_SPACE / 2).row()
            syncs.add { setDisabled(widget, !spec.enabled()); label.color.a = if (spec.enabled()) 1f else 0.5f }
        }
    }

    private fun toggleWidget(spec: SettingSpec.Toggle): Actor {
        val box = CheckBox("", skin)
        val look = styleOnOffCheckBox(box, i18n["setting_on"], i18n["setting_off"])
        box.onChange { userChanged { spec.set(box.isChecked) } }
        syncs.add { box.isChecked = spec.get(); look() }
        return box
    }

    private fun sliderWidget(spec: SettingSpec.Slider): Actor {
        val s = Slider(spec.min, spec.max, spec.step, false, skin, "default-horizontal")
        val valueLabel = Label("", skin)
        // Widest of the two range ends, measured in the label font, so the readout never reflows while dragging.
        val font = valueLabel.style.font
        val valueWidth = listOf(spec.min, spec.max).maxOf { GlyphLayout(font, formatSettingValue(it, spec.decimals)).width }
        val refresh = { valueLabel.setText(formatSettingValue(s.value, spec.decimals)) }
        s.onChange {
            if (!syncing) refresh()
            userChanged { spec.set(s.value) }
        }
        syncs.add { s.value = spec.get(); refresh() }
        return Table().also {
            it.add(s).width(SLIDER_WIDTH)
            it.add(valueLabel).padLeft(ROW_SPACE).width(valueWidth)
            it.userObject = s
        }
    }

    private fun choiceWidget(spec: SettingSpec.Choice): Actor {
        val select = SelectBox<String>(skin)
        select.setItems(*spec.optionLabelKeys.map { i18n[it] }.toTypedArray())
        select.onChange {
            userChanged { spec.options.getOrNull(select.selectedIndex)?.let(spec.set) }
        }
        syncs.add { select.selectedIndex = spec.options.indexOf(spec.get()).coerceAtLeast(0) }
        return select
    }

    private fun setDisabled(widget: Actor, disabled: Boolean) {
        when (widget) {
            is CheckBox -> widget.isDisabled = disabled
            is SelectBox<*> -> widget.isDisabled = disabled
            is Table -> (widget.userObject as? Slider)?.isDisabled = disabled
        }
        widget.touchable = if (disabled) Touchable.disabled else Touchable.enabled
        widget.color.a = if (disabled) 0.5f else 1f
    }

    /** A widget changed by the user: apply, persist, refresh disabled states. Ignored while syncing from specs. */
    private fun userChanged(apply: () -> Unit) {
        if (syncing) return
        apply()
        saveSettings(store, specs)
        syncAll()
    }

    private fun syncAll() {
        val wasSyncing = syncing
        syncing = true
        try {
            syncs.forEach { it() }
        } finally {
            syncing = wasSyncing
        }
    }

    // ---- Small builders --------------------------------------------------------------------------------------

    private fun button(key: String, primary: Boolean = false, onClick: () -> Unit): TextButton =
        TextButton(i18n[key], if (primary) style.primaryButton else style.button).also { it.onChange(onClick) }

    /** [left] on the left edge, [right] on the right edge. */
    private fun buttonRow(left: Actor, right: Actor): Table = Table().apply {
        add(left).height(MENU_BUTTON_HEIGHT).left()
        add().expandX()
        add(right).height(MENU_BUTTON_HEIGHT).minWidth(MENU_BUTTON_WIDTH * 0.6f).right()
    }

    private fun divider(): Image = Image(style.rule).apply { height = 1f }

    private fun scrolled(content: Table): ScrollPane = ScrollPane(content, skin).apply {
        setFadeScrollBars(false)
        setScrollingDisabled(true, false)
    }

    private fun Actor.onChange(block: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = block()
        })
    }

    override fun show() {
        game.get<GameEventManager>().ignorePlayerInputs()
        // A scenario may have overridden the run physics; restore the player's saved New-game choices.
        resetSettings(menuSettings.runSpecs)
        loadSettings(store, menuSettings.runSpecs)
        runSave = readRunSave(store)
        overwriteConfirmed = false
        runEndedLabel.setText(if (gameState.lastRunEnded) i18n["run_ended_message"] else "")
        gameState.lastRunEnded = false
        stage.addActor(root)
        open(nextPage)
        nextPage = MenuPage.MAIN
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resizing screen to ${width}x$height" }
        stage.viewport.update(width, height, true)
        root.invalidateHierarchy()
    }

    override fun hide() {
        root.remove()
    }

    companion object {
        private val logger = logger<MainMenuScreen>()
    }
}
