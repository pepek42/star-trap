package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IncomingFireTest {
    private val out = Vector2()

    @Test
    fun headOnPhotonArrivesAtDistanceOverClosingSpeed() {
        val c = 20f
        val v = 5f
        val d = 30f
        // Photon at x = d flying -x at c, ship at origin flying +x at v.
        val t = closestApproach(d, 0f, -c, 0f, 0f, 0f, v, 0f, out)
        assertEquals(d / (c + v), t, 1e-5f)
        assertEquals(0f, out.len(), 1e-4f)
    }

    @Test
    fun photonMovingAwayGivesZeroTimeAndCurrentOffset() {
        val t = closestApproach(10f, 0f, 20f, 0f, 0f, 0f, 0f, 0f, out)
        assertEquals(0f, t)
        assertEquals(10f, out.x, 1e-5f)
    }

    @Test
    fun zeroRelativeVelocityGivesZeroTime() {
        assertEquals(0f, closestApproach(3f, 4f, 1f, 1f, 0f, 0f, 1f, 1f, out))
        assertEquals(5f, out.len(), 1e-5f)
    }

    @Test
    fun offsetPassMissVectorIsPerpendicular() {
        val t = closestApproach(20f, 3f, -20f, 0f, 0f, 0f, 0f, 0f, out)
        assertEquals(1f, t, 1e-5f)
        assertEquals(0f, out.x, 1e-4f)
        assertEquals(3f, out.y, 1e-4f)
    }

    @Test
    fun threatAlphaFallsWithTime() {
        assertEquals(THREAT_ALPHA_MAX, threatAlpha(0f))
        assertEquals(THREAT_ALPHA_MIN, threatAlpha(THREAT_HORIZON_LIGHT_SECONDS), 1e-6f)
    }
}
