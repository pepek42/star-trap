package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

/** Width (px) of the in-game briefing text. */
private const val BRIEFING_WIDTH = 400f

/** Inner padding (px), tighter than menu panels. */
private const val BRIEFING_PAD = 16f

/** Gap (px) between the panel and the bottom-left corner of the screen. */
private const val BRIEFING_EDGE_GAP = 12f

/**
 * A scenario's briefing, shown in the bottom-left corner (clear of the ship at the centre and the minimap) while the scenario runs, until the player hides it.
 * While the text is open the arena is frozen ([onDismiss] fires when it closes; `PlayScreen` pauses and resumes world time, ST-170).
 * The same text is on the Scenarios page before the start.
 */
class BriefingPanel(skin: Skin, hideText: String, private val onDismiss: () -> Unit = {}) {
    private val style = MenuStyle(skin)
    private val title = Label("", style.heading)
    private val text = style.paragraph("", BRIEFING_WIDTH)
    private val objectives = style.paragraph("", BRIEFING_WIDTH)
    private val root = Table().apply { setFillParent(true) }
    private val column = Table()
    private val briefingPanel = style.panel(BRIEFING_PAD)
    private val objectivesPanel = style.panel(BRIEFING_PAD)
    private val objectivesCell: Cell<*>
    private val briefingCell: Cell<*>
    private var hasObjectives = false

    /** True while the briefing text is on screen. */
    var open = false
        private set

    init {
        val t = briefingPanel.panelContent
        t.defaults().left().width(BRIEFING_WIDTH)
        t.add(title).row()
        t.add(text).padTop(6f).row()
        val hide = TextButton(hideText, style.button)
        hide.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = hide()
        })
        t.add(hide).width(hide.prefWidth).right().padTop(10f).row()
        val o = objectivesPanel.panelContent
        o.defaults().left().width(BRIEFING_WIDTH)
        o.add(objectives)
        objectivesCell = column.add().left().padBottom(BRIEFING_EDGE_GAP / 2)
        column.row()
        briefingCell = column.add().left()
        root.add(column).expand().bottom().left().pad(BRIEFING_EDGE_GAP)
    }

    fun show(stage: Stage, titleText: String, briefing: String) {
        title.setText(titleText)
        text.setText(briefing)
        briefingCell.setActor(briefingPanel)
        open = true
        refreshObjectivesCell()
        column.invalidateHierarchy()
        stage.addActor(root)
    }

    /** Shows the tutorial checklist above the briefing; empty text hides it (ST-169). It stays after [hide]. */
    fun setObjectives(checklist: String) {
        objectives.setText(checklist)
        hasObjectives = checklist.isNotEmpty()
        refreshObjectivesCell()
        column.invalidateHierarchy()
    }

    private fun refreshObjectivesCell() {
        objectivesCell.setActor(if (hasObjectives) objectivesPanel else null)
    }

    /** "Got it": hides the briefing text but keeps the checklist on screen. */
    fun hide() {
        val wasOpen = open
        open = false
        briefingCell.setActor(null)
        column.invalidateHierarchy()
        if (wasOpen) onDismiss()
    }

    /** Removes everything (leaving the level). */
    fun close() {
        open = false
        root.remove()
    }
}
