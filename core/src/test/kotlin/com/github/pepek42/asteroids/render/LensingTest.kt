package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.BH_RS
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LensingTest {
    // r_s = 2 u: the shadow radius is BH_SHADOW_PER_RS * 2 = 5.196 u, R_E = 8 u, the quad half-size 24 u.
    private val rs = 2f * BH_RS
    private val rE = LENS_EINSTEIN_RADIUS_RS * rs
    private val quadHalf = LENS_QUAD_HALF * rE
    private val out = Vector2()

    @Test
    fun insideTheShadowThereIsNoSourceImage() {
        assertFalse(lensSourceOffset(5f, 0f, rE, quadHalf, out))
        assertFalse(lensSourceOffset(0f, 0f, rE, quadHalf, out))
        assertTrue(lensSourceOffset(BH_SHADOW_PER_RS * rs + 0.01f, 0f, rE, quadHalf, out))
    }

    @Test
    fun theEinsteinRingImagesThePointBehindTheHole() {
        assertTrue(lensSourceOffset(rE, 0f, rE, quadHalf, out))
        assertEquals(0f, out.x, 1e-4f)
        assertEquals(0f, out.y, 1e-4f)
        assertTrue(lensSourceOffset(0f, -rE, rE, quadHalf, out))
        assertEquals(0f, out.len(), 1e-4f)
    }

    @Test
    fun insideTheRingTheSourceLiesOnTheOppositeSide() {
        assertTrue(lensSourceOffset(6f, 0f, rE, quadHalf, out))
        assertTrue(out.x < 0f)
        assertTrue(lensSourceOffset(12f, 0f, rE, quadHalf, out))
        assertTrue(out.x > 0f && out.x < 12f)
    }

    @Test
    fun theDeflectionIsZeroAtTheQuadEdge() {
        for (p in listOf(Vector2(quadHalf, 0f), Vector2(quadHalf, 10f), Vector2(-quadHalf, -quadHalf), Vector2(3f, quadHalf))) {
            val x = p.x
            val y = p.y
            assertTrue(lensSourceOffset(x, y, rE, quadHalf, out))
            assertEquals(x, out.x, 1e-3f)
            assertEquals(y, out.y, 1e-3f)
        }
    }

    @Test
    fun thePointIsContinuousAlongRadialLines() {
        val prev = Vector2()
        for (angleDeg in intArrayOf(0, 30, 45, 90, 200)) {
            val dx = Math.cos(Math.toRadians(angleDeg.toDouble())).toFloat()
            val dy = Math.sin(Math.toRadians(angleDeg.toDouble())).toFloat()
            var r = BH_SHADOW_PER_RS * rs + 0.01f
            assertTrue(lensSourceOffset(r * dx, r * dy, rE, quadHalf, prev))
            while (r < 1.5f * quadHalf) {
                r += 0.01f
                assertTrue(lensSourceOffset(r * dx, r * dy, rE, quadHalf, out))
                assertTrue(hypot(out.x - prev.x, out.y - prev.y) < 0.1f) { "jump at r=$r angle=$angleDeg" }
                prev.set(out)
            }
        }
    }

    @Test
    fun beyondTheQuadTheOffsetIsTheIdentity() {
        assertTrue(lensSourceOffset(40f, -30f, rE, quadHalf, out))
        assertEquals(40f, out.x, 1e-3f)
        assertEquals(-30f, out.y, 1e-3f)
    }

    @Test
    fun theGainPeaksAtTheRingAndIsOneFarAway() {
        assertEquals(LENS_GAIN_MAX, lensGain(rE, 0f, rE, quadHalf), 1e-4f)
        assertEquals(1f, lensGain(quadHalf, 0f, rE, quadHalf), 1e-4f)
        assertEquals(1f, lensGain(60f, 40f, rE, quadHalf), 1e-4f)
        for (r in listOf(6f, 7f, 9f, 10f, 14f)) assertTrue(lensGain(r, 0f, rE, quadHalf) in 1f..LENS_GAIN_MAX)
        assertTrue(lensGain(9f, 0f, rE, quadHalf) > lensGain(14f, 0f, rE, quadHalf))
    }

    @Test
    fun onlyBlackHolesGetRings() {
        assertTrue(drawsHoleRings(com.github.pepek42.asteroids.component.GravitySourceKind.BLACK_HOLE))
        assertFalse(drawsHoleRings(com.github.pepek42.asteroids.component.GravitySourceKind.PLANET))
    }

    @Test
    fun theGainLeavesEmptySpaceAtTheClearGrey() {
        assertEquals(SCREEN_CLEAR_GRAY, lensChannel(SCREEN_CLEAR_GRAY, SCREEN_CLEAR_GRAY, LENS_GAIN_MAX), 1e-6f)
        assertEquals(SCREEN_CLEAR_GRAY, lensChannel(0.05f, SCREEN_CLEAR_GRAY, LENS_GAIN_MAX), 1e-6f)
        assertEquals(SCREEN_CLEAR_GRAY + 0.4f, lensChannel(SCREEN_CLEAR_GRAY + 0.1f, SCREEN_CLEAR_GRAY, 4f), 1e-6f)
    }
}
