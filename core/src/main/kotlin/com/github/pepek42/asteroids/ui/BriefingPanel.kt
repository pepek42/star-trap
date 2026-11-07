package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
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
 * The game keeps running underneath; the same text is on the Scenarios page before the start.
 */
class BriefingPanel(skin: Skin, hideText: String) {
    private val style = MenuStyle(skin)
    private val title = Label("", style.heading)
    private val text = style.paragraph("", BRIEFING_WIDTH)
    private val root = Table().apply { setFillParent(true) }

    init {
        val panel = style.panel(BRIEFING_PAD)
        val t = panel.panelContent
        t.defaults().left().width(BRIEFING_WIDTH)
        t.add(title).row()
        t.add(text).padTop(6f).row()
        val hide = TextButton(hideText, style.button)
        hide.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = hide()
        })
        t.add(hide).width(hide.prefWidth).right().padTop(10f).row()
        root.add(panel).expand().bottom().left().pad(BRIEFING_EDGE_GAP)
    }

    fun show(stage: Stage, titleText: String, briefing: String) {
        title.setText(titleText)
        text.setText(briefing)
        stage.addActor(root)
    }

    fun hide() {
        root.remove()
    }
}
