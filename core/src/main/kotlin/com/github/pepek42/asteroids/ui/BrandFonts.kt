package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Skin

/**
 * The brand font: Audiowide by Astigmatic, SIL Open Font License 1.1 (license text next to the TTF in
 * `assets/fonts/`, attribution in the third-party asset list and on the Settings page). "Audiowide" is a Reserved Font Name, so the
 * TTF ships unmodified (no subsetting or renaming); rasterising it at runtime is fine.
 */
const val BRAND_FONT_PATH = "fonts/Audiowide-Regular.ttf"

/** Skin font names added by [addBrandFonts]. */
const val FONT_LOGO = "brand-logo"
const val FONT_LOGO_SUB = "brand-logo-sub"
const val FONT_TITLE = "brand-title"

/** Pixel sizes (the menu stage is a `ScreenViewport`, so 1 px = 1 screen px). */
private const val LOGO_PX = 46
private const val LOGO_SUB_PX = 22
private const val TITLE_PX = 26

/**
 * Rasterises the brand font at the menu sizes and adds it to [skin], which then owns and disposes it.
 * Rendered once at startup, so text stays crisp instead of scaling a small bitmap font.
 */
fun addBrandFonts(skin: Skin) {
    val generator = FreeTypeFontGenerator(Gdx.files.internal(BRAND_FONT_PATH))
    try {
        skin.add(FONT_LOGO, generator.generateFont(brandParams(LOGO_PX)), BitmapFont::class.java)
        skin.add(FONT_LOGO_SUB, generator.generateFont(brandParams(LOGO_SUB_PX)), BitmapFont::class.java)
        skin.add(FONT_TITLE, generator.generateFont(brandParams(TITLE_PX)), BitmapFont::class.java)
    } finally {
        generator.dispose()
    }
}

private fun brandParams(px: Int) = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
    size = px
    minFilter = Texture.TextureFilter.Linear
    magFilter = Texture.TextureFilter.Linear
}
