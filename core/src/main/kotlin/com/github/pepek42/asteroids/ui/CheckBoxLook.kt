package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

/** Tint for a checked checkbox (bright green). */
internal val CHECKED_COLOR: Color = Color(0.4f, 1f, 0.4f, 1f)

/** Tint for an unchecked checkbox (dim grey). */
internal val UNCHECKED_COLOR: Color = Color(0.45f, 0.45f, 0.45f, 1f)

/**
 * Makes [box] readable without relying on colour alone: its text switches between [textOn] and [textOff],
 * and the tint of mark and text between bright green and dim grey. The skin's check mark drawable differs per state.
 * Returns a function that re-applies the look (it also runs on every change of [box]).
 */
fun styleOnOffCheckBox(box: CheckBox, textOn: String, textOff: String): () -> Unit {
    val refresh = {
        box.setText(if (box.isChecked) textOn else textOff)
        // A Table does not tint its children with its own colour, so tint the mark and the text directly.
        val tint = if (box.isChecked) CHECKED_COLOR else UNCHECKED_COLOR
        box.image.color.set(tint)
        box.label.color.set(tint)
        Unit
    }
    box.addListener(object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = refresh()
    })
    refresh()
    return refresh
}

/** Seconds the pointer must rest on a widget before its hint shows. */
const val HINT_TOOLTIP_DELAY = 0.3f

/** Hints wrap at this width (screen pixels) so a long hint never becomes a screen-wide line. */
const val HINT_TOOLTIP_MAX_WIDTH = 320f

/** A tooltip manager for hints: quick to appear, no fade, and not shared with other screens. */
fun hintTooltipManager(): TooltipManager = TooltipManager().apply {
    initialTime = HINT_TOOLTIP_DELAY
    subsequentTime = 0f
    resetTime = 0f
    animations = false
}

/** A wrapped hint tooltip for [manager] (see [hintTooltipManager]). */
fun hintTooltip(text: String, manager: TooltipManager, skin: Skin): TextTooltip {
    val tip = TextTooltip(text, manager, skin)
    tip.container.actor.wrap = true
    tip.container.width(HINT_TOOLTIP_MAX_WIDTH)
    return tip
}
