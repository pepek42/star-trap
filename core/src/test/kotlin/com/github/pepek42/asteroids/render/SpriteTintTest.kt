package com.github.pepek42.asteroids.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SpriteTintTest {
    private val rgb = FloatArray(3)

    @Test
    fun restDopplerIsWhite() {
        spriteTint(1f, BlackbodyLut, rgb)
        for (c in rgb) assertEquals(1f, c, 0.01f)
    }

    @Test
    fun blueshiftIsBlueish() {
        spriteTint(2f, BlackbodyLut, rgb)
        assertTrue(rgb[2] >= rgb[0], "blue ${rgb[2]} >= red ${rgb[0]}")
    }

    @Test
    fun redshiftIsReddish() {
        spriteTint(0.5f, BlackbodyLut, rgb)
        assertTrue(rgb[0] > rgb[2], "red ${rgb[0]} > blue ${rgb[2]}")
    }

    @Test
    fun channelsStayInUnitRange() {
        for (d in listOf(0.01f, 0.25f, 0.5f, 1f, 2f, 4f, 100f)) {
            spriteTint(d, BlackbodyLut, rgb)
            for (c in rgb) assertTrue(c in 0f..1f, "d=$d channel $c")
        }
    }

    @Test
    fun brightnessAndGlowFollowDoppler() {
        assertEquals(B_MIN, spriteBrightness(0.01f))
        assertEquals(B_MAX, spriteBrightness(100f))
        assertEquals(1f, spriteBrightness(1f), 1e-6f)
        assertEquals(0f, additiveGlowAlpha(0.5f))
        assertEquals(0.5f, additiveGlowAlpha(1.5f), 1e-6f)
        assertEquals(1f, additiveGlowAlpha(4f))
    }

    @Test
    fun tintWritesAllFourCornerColours() {
        val v = FloatArray(SPRITE_VERTEX_FLOATS)
        val glow = tintSpriteVertices(v, 0.5f, 1f, 1f, 1f, 1f, BlackbodyLut, rgb)
        assertEquals(0f, glow)
        val c = Color()
        for (i in 0 until 4) {
            Color.abgr8888ToColor(c, v[SPRITE_COLOR_OFFSET + i * SPRITE_VERTEX_STRIDE])
            assertEquals(0.5f, c.a, 0.01f, "receding sprite dims through alpha")
            assertTrue(c.r > c.b)
        }
    }

    @Test
    fun blueshiftedSpriteIsFullAlphaWithGlow() {
        val v = FloatArray(SPRITE_VERTEX_FLOATS)
        val glow = tintSpriteVertices(v, 1.5f, 1f, 1f, 1f, 1f, BlackbodyLut, rgb)
        assertEquals(0.5f, glow, 1e-6f)
        val c = Color()
        Color.abgr8888ToColor(c, v[SPRITE_COLOR_OFFSET])
        assertEquals(1f, c.a, 0.01f)
    }

    @Test
    fun queueIsBoundedAndClears() {
        val q = AdditiveSpriteQueue(2)
        val v = FloatArray(SPRITE_VERTEX_FLOATS)
        setSpriteVertexColor(v, 0.5f, 0.5f, 1f, 1f)
        val tex = mock<Texture>()
        assertTrue(q.add(tex, v, 0.4f))
        assertTrue(q.add(tex, v, 0.4f))
        assertFalse(q.add(tex, v, 0.4f))
        assertEquals(2, q.size)
        assertEquals(0.4f, q.alphaAt(1), 0.01f)
        q.clear()
        assertEquals(0, q.size)
    }

    @Test
    fun defaultQueueUsesNamedCapacity() {
        assertEquals(ADDITIVE_QUEUE_MAX_SPRITES, AdditiveSpriteQueue().capacity)
    }
}
