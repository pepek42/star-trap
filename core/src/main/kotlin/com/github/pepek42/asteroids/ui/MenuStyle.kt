package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable

/** Accent of menu titles and the primary button: the player-marker blue, so the menus match the HUD. */
val MENU_ACCENT_COLOR: Color = Color(PLAYER_MARKER_COLOR)

/** Secondary text (hints, subtitles, placeholders). */
val MENU_MUTED_COLOR: Color = Color(0.6f, 0.65f, 0.72f, 1f)

private val PANEL_COLOR = Color(0.03f, 0.04f, 0.07f, 0.94f)
private val PANEL_EDGE_COLOR = Color(0.2f, 0.35f, 0.5f, 1f)
private val BUTTON_UP = Color(0.15f, 0.2f, 0.28f, 1f)
private val BUTTON_OVER = Color(0.23f, 0.3f, 0.41f, 1f)
private val BUTTON_DOWN = Color(0.1f, 0.13f, 0.18f, 1f)
private val PRIMARY_UP = Color(0.1f, 0.42f, 0.66f, 1f)
private val PRIMARY_OVER = Color(0.16f, 0.53f, 0.8f, 1f)
private val PRIMARY_DOWN = Color(0.07f, 0.3f, 0.48f, 1f)
private val DISABLED_TEXT = Color(0.4f, 0.42f, 0.46f, 1f)

/** Width (px) of the panel's accent edge. */
private const val PANEL_EDGE_PX = 2f

/** Inner padding (px) of menu buttons. */
private const val BUTTON_PAD_X = 24f
private const val BUTTON_PAD_Y = 10f

/** Inner padding (px) of a panel. */
const val MENU_PANEL_PAD = 28f

/** Width (px) of the main menu's stacked buttons. */
const val MENU_BUTTON_WIDTH = 280f

/** Height (px) of large menu buttons. */
const val MENU_BUTTON_HEIGHT = 46f

/**
 * Styles shared by the menu screens, built from the skin's "white" region and fonts, so no new assets are needed.
 * The larger "window" font is used for titles and buttons, the default font for body text.
 */
class MenuStyle(private val skin: Skin) {
    private fun fill(color: Color): Drawable = skin.newDrawable("white", color)

    /** Brand font when loaded (see [addBrandFonts]), else the skin's bitmap font, so a bare test skin still works. */
    private fun brand(name: String) = skin.optional(name, BitmapFont::class.java) ?: skin.getFont("window")

    val title = Label.LabelStyle(brand(FONT_TITLE), MENU_ACCENT_COLOR)
    val logo = Label.LabelStyle(brand(FONT_LOGO), MENU_ACCENT_COLOR)
    val logoSub = Label.LabelStyle(brand(FONT_LOGO_SUB), Color.WHITE)
    val heading = Label.LabelStyle(skin.getFont("window"), Color.WHITE)
    val body = Label.LabelStyle(skin.getFont("font"), Color.WHITE)
    val muted = Label.LabelStyle(skin.getFont("font"), MENU_MUTED_COLOR)

    /** A thin accent rule, used as a section divider. */
    val rule: Drawable = fill(PANEL_EDGE_COLOR)

    val button = buttonStyle(BUTTON_UP, BUTTON_OVER, BUTTON_DOWN)
    val primaryButton = buttonStyle(PRIMARY_UP, PRIMARY_OVER, PRIMARY_DOWN)

    /** A button that stays lit while checked (options in a ButtonGroup). */
    val toggleButton = buttonStyle(BUTTON_UP, BUTTON_OVER, BUTTON_DOWN).apply {
        checked = padded(fill(PRIMARY_UP))
        checkedOver = padded(fill(PRIMARY_OVER))
    }

    private fun buttonStyle(up: Color, over: Color, down: Color) = TextButton.TextButtonStyle().apply {
        this.up = padded(fill(up))
        this.over = padded(fill(over))
        this.down = padded(fill(down))
        this.disabled = padded(fill(BUTTON_DOWN))
        font = skin.getFont("window")
        fontColor = Color.WHITE
        disabledFontColor = DISABLED_TEXT
    }

    private fun padded(d: Drawable): Drawable = d.apply {
        leftWidth = BUTTON_PAD_X
        rightWidth = BUTTON_PAD_X
        topHeight = BUTTON_PAD_Y
        bottomHeight = BUTTON_PAD_Y
    }

    /** A dark panel table with an accent edge and inner padding [pad]. */
    fun panel(pad: Float = MENU_PANEL_PAD): Table {
        val inner = Table()
        inner.background = fill(PANEL_COLOR)
        inner.pad(pad)
        val edge = Table()
        edge.background = fill(PANEL_EDGE_COLOR)
        edge.pad(PANEL_EDGE_PX)
        edge.add(inner).grow()
        edge.userObject = inner
        return edge
    }

    fun label(text: String, style: Label.LabelStyle = body) = Label(text, style)

    /** A label that wraps at [width] px. */
    fun paragraph(text: String, width: Float, style: Label.LabelStyle = body) =
        Label(text, style).apply { wrap = true; setWidth(width) }
}

/** The content table of a [MenuStyle.panel]. */
val Table.panelContent: Table get() = userObject as Table
