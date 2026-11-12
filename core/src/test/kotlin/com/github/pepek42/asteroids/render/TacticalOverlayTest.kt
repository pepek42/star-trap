package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.EdgeShape
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.github.pepek42.asteroids.physics.RelativitySettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt

class TacticalOverlayTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initBox2D() {
            Box2D.init()
        }
    }

    private val out = FloatArray(2 * TACTICAL_OUTLINE_MAX_VERTS)

    @Test
    fun worldPerPixelScalesWithZoom() {
        assertEquals(0.05f, worldPerPixel(48f, 1f, 960), 1e-6f)
        assertEquals(0.1f, worldPerPixel(48f, 2f, 960), 1e-6f)
        assertEquals(0f, worldPerPixel(48f, 1f, 0))
    }

    @Test
    fun circleOutline() {
        val s = CircleShape()
        s.radius = 1f
        s.position = Vector2(1f, 0f)
        val n = outlineVertices(s, 10f, 5f, (PI / 2).toFloat(), 0f, out)
        assertEquals(TACTICAL_CIRCLE_SEGMENTS, n)
        for (i in 0 until n) assertEquals(1f, hypot(out[2 * i] - 10f, out[2 * i + 1] - 6f), 1e-4f)
        s.dispose()
    }

    @Test
    fun polygonOutlineAndMinRadius() {
        val s = PolygonShape()
        s.setAsBox(1f, 1f)
        val r2 = sqrt(2f)
        val n = outlineVertices(s, 0f, 0f, (PI / 4).toFloat(), 0f, out)
        assertEquals(4, n)
        var found = false
        for (i in 0 until n) {
            assertEquals(r2, hypot(out[2 * i], out[2 * i + 1]), 1e-4f)
            if (kotlin.math.abs(out[2 * i]) < 1e-4f && kotlin.math.abs(out[2 * i + 1] - r2) < 1e-4f) found = true
        }
        assertTrue(found)
        val m = outlineVertices(s, 0f, 0f, 0f, 5f, out)
        assertEquals(TACTICAL_CIRCLE_SEGMENTS, m)
        for (i in 0 until m) assertEquals(5f, hypot(out[2 * i], out[2 * i + 1]), 1e-4f)
        s.dispose()
    }

    @Test
    fun edgeShapeHasNoOutline() {
        val s = EdgeShape()
        s.set(0f, 0f, 1f, 0f)
        assertEquals(0, outlineVertices(s, 0f, 0f, 0f, 0f, out))
        s.dispose()
    }

    @Test
    fun visibilityTruthTable() {
        for (mode in TacticalOverlayMode.entries) for (pv in listOf(false, true)) for (inv in listOf(false, true)) {
            assertFalse(tacticalOverlayVisible(mode, pv, TACTICAL_TIER_NONE, inv))
        }
        val t = TACTICAL_TIER_WIREFRAME
        assertFalse(tacticalOverlayVisible(TacticalOverlayMode.OFF, false, t, false))
        assertFalse(tacticalOverlayVisible(TacticalOverlayMode.OFF, true, t, false))
        assertTrue(tacticalOverlayVisible(TacticalOverlayMode.OFF, false, t, true))
        assertFalse(tacticalOverlayVisible(TacticalOverlayMode.PILOT, false, t, false))
        assertTrue(tacticalOverlayVisible(TacticalOverlayMode.PILOT, true, t, false))
        assertTrue(tacticalOverlayVisible(TacticalOverlayMode.PILOT, false, t, true))
        assertFalse(tacticalOverlayVisible(TacticalOverlayMode.PILOT, true, t, true))
        assertTrue(tacticalOverlayVisible(TacticalOverlayMode.ALWAYS, false, TACTICAL_TIER_INTERCEPT, false))
        assertFalse(tacticalOverlayVisible(TacticalOverlayMode.ALWAYS, false, t, true))
        assertEquals(TacticalOverlayMode.PILOT, TacticalOverlayMode.fromFlag("PILOT"))
    }

    @Test
    fun arrowScaleIsCRelative() {
        val c = RelativitySettings().c
        assertEquals(TACTICAL_ARROW_LENGTH_AT_C, tacticalArrowScale(c) * c, 1e-4f)
    }

    @Test
    fun leadRangeIsTwoLightSeconds() {
        assertEquals(2f * 30f, tacticalLeadRange(30f), 1e-5f)
    }

    @Test
    fun insertNearestKeepsSortedBoundedPrefix() {
        val keys = FloatArray(3)
        val ids = IntArray(3)
        var n = 0
        listOf(5f, 1f, 4f, 3f, 9f, 2f).forEachIndexed { id, k -> n = insertNearest(keys, ids, n, k, id) }
        assertEquals(3, n)
        assertEquals(listOf(1f, 2f, 3f), keys.toList())
        assertEquals(listOf(1, 5, 3), ids.toList())
    }

    @Test
    fun lockOnChevronTipIsAheadAndWingsAreSymmetric() {
        val out = FloatArray(6)
        for (angle in listOf(0f, (PI / 2).toFloat())) {
            lockOnChevron(3f, -2f, angle, 2f, out)
            val hx = kotlin.math.cos(angle)
            val hy = kotlin.math.sin(angle)
            assertEquals(3f + 2f * hx, out[0], 1e-5f)
            assertEquals(-2f + 2f * hy, out[1], 1e-5f)
            // Wings mirror about the heading: equal along-heading component, opposite across-heading component.
            val la = (out[2] - 3f) * hx + (out[3] + 2f) * hy
            val ra = (out[4] - 3f) * hx + (out[5] + 2f) * hy
            val lc = -(out[2] - 3f) * hy + (out[3] + 2f) * hx
            val rc = -(out[4] - 3f) * hy + (out[5] + 2f) * hx
            assertEquals(la, ra, 1e-5f)
            assertEquals(lc, -rc, 1e-5f)
            assertTrue(la < 0f)
        }
    }
}
