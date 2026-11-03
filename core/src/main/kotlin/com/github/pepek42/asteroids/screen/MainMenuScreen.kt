package com.github.pepek42.asteroids.screen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
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
import com.badlogic.gdx.utils.I18NBundle
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.TITLE
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.input.InputBindings
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.MenuSettings
import com.github.pepek42.asteroids.settings.SettingSpec
import com.github.pepek42.asteroids.settings.resetSettings
import com.github.pepek42.asteroids.settings.saveSettings
import com.github.pepek42.asteroids.ui.controlsRows
import com.github.pepek42.asteroids.ui.CHECKED_COLOR
import com.github.pepek42.asteroids.ui.UNCHECKED_COLOR
import com.github.pepek42.asteroids.ui.formatSettingValue
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.log.logger

private const val MENU_PAD = 24f
private const val COLUMN_SPACE = 40f
private const val ROW_SPACE = 8f
private const val BUTTON_SPACE = 16f
private const val SLIDER_WIDTH = 160f

class MainMenuScreen(private val game: Game) : KtxScreen {
    private val i18n = game.get<I18NBundle>()
    private val stage = game.get<Stage>()
    private val skin = game.get<Skin>()
    private val gameState = game.get<GameState>()
    private val store = game.get<KeyValueStore>()
    private val specs = game.get<MenuSettings>().specs
    private val controls = game.get<ControlSettings>()
    private val debug = game.get<DebugSettings>()

    private val syncs = ArrayList<() -> Unit>()
    private var syncing = false
    private val menu: Actor = buildMenu()

    private fun buildMenu(): Actor {
        val root = Table(skin)
        root.setFillParent(true)
        root.touchable = Touchable.enabled
        root.pad(MENU_PAD)
        root.add(Label(TITLE, skin)).padBottom(MENU_PAD).row()
        val columns = Table()
        columns.defaults().top().padLeft(COLUMN_SPACE / 2).padRight(COLUMN_SPACE / 2)
        columns.add(buildButtons())
        columns.add(scrolled(buildSettings()))
        columns.add(scrolled(buildControls()))
        root.add(columns)
        return root
    }

    private fun scrolled(content: Table): ScrollPane = ScrollPane(content, skin).apply {
        setFadeScrollBars(false)
        setScrollingDisabled(true, false)
    }

    private fun buildButtons(): Table {
        val t = Table()
        t.defaults().space(BUTTON_SPACE).fillX()
        // Column 1 has room above for the ST-084 "run ended" label.
        t.add(TextButton(i18n["menu_single_player"], skin).also { b ->
            b.onChange {
                saveSettings(store, specs)
                gameState.resetGame()
                game.setScreen<PlayScreen>()
            }
        }).row()
        if (Gdx.app.type != Application.ApplicationType.WebGL) {
            t.add(TextButton(i18n["menu_exit"], skin).also { b ->
                b.onChange {
                    logger.info { "Exiting application" }
                    Gdx.app.exit()
                }
            }).row()
        }
        return t
    }

    private fun buildSettings(): Table {
        val t = Table()
        t.defaults().left().pad(ROW_SPACE / 2)
        t.add(Label(i18n["menu_settings"], skin)).colspan(2).row()
        val enabledSyncs = ArrayList<() -> Unit>()
        for (spec in specs) {
            val label = Label(i18n[spec.labelKey], skin)
            label.addListener(TextTooltip(i18n[spec.hintKey], skin))
            t.add(label)
            val widget: Actor = when (spec) {
                is SettingSpec.Toggle -> toggleWidget(spec)
                is SettingSpec.Slider -> sliderWidget(spec)
                is SettingSpec.Choice -> choiceWidget(spec)
            }
            t.add(widget).row()
            enabledSyncs.add { setDisabled(widget, !spec.enabled()); label.color.a = if (spec.enabled()) 1f else 0.5f }
        }
        syncs.addAll(enabledSyncs)
        t.add(TextButton(i18n["menu_reset_defaults"], skin).also { b ->
            b.onChange {
                resetSettings(specs)
                saveSettings(store, specs)
                syncAll()
            }
        }).colspan(2).padTop(BUTTON_SPACE).row()
        return t
    }

    private fun toggleWidget(spec: SettingSpec.Toggle): Actor {
        val box = CheckBox("", skin)
        // The skin's checkbox looks the same checked and unchecked, so tint it: green on, grey off.
        val tint = { box.color.set(if (box.isChecked) CHECKED_COLOR else UNCHECKED_COLOR) }
        box.onChange { tint(); userChanged { spec.set(box.isChecked) } }
        syncs.add { box.isChecked = spec.get(); tint() }
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

    private fun buildControls(): Table {
        val t = Table()
        t.defaults().left().pad(ROW_SPACE / 2)
        val tr = { key: String -> i18n[key] }
        t.add(Label(i18n["menu_controls"], skin)).colspan(2).row()
        fun rows(includeDebug: Boolean): List<Pair<String, String>> =
            controlsRows(InputBindings.DEFAULT, controls.scheme, includeDebug, tr)
        for (row in rows(false)) {
            t.add(Label(row.first, skin)).padRight(COLUMN_SPACE / 2)
            t.add(Label(row.second, skin)).row()
        }
        if (debug.panelAllowed) {
            val plain = rows(false).toSet()
            t.add(Label(i18n["menu_controls_debug"], skin)).colspan(2).padTop(BUTTON_SPACE).row()
            for (row in rows(true).filter { it !in plain }) {
                t.add(Label(row.first, skin)).padRight(COLUMN_SPACE / 2)
                t.add(Label(row.second, skin)).row()
            }
        }
        return t
    }

    private fun Actor.onChange(block: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = block()
        })
    }

    override fun show() {
        game.get<GameEventManager>().ignorePlayerInputs()
        syncAll()
        stage.addActor(menu)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "Resizing screen to ${width}x$height" }
        stage.viewport.update(width, height, true)
    }

    override fun hide() {
        menu.remove()
    }

    companion object {
        private val logger = logger<MainMenuScreen>()
    }
}
