package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.dopplerFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class RelativityVisualsDopplerTest {
    private val beta = 0.9f
    private val gamma = (1.0 / sqrt(1.0 - beta * beta)).toFloat()

    @Test
    fun starDopplerMatchesResearchTable() {
        assertEquals(4.36f, starDoppler(beta, 0f, gamma, 1f, 0f), 0.01f)
        assertEquals(0.229f, starDoppler(beta, 0f, gamma, -1f, 0f), 0.001f)
        assertEquals(2.294f, starDoppler(beta, 0f, gamma, 0f, 1f), 0.001f)
    }

    @Test
    fun starDopplerAgreesWithDopplerFactorForStaticSource() {
        // Source at rest; light travels from the star to the observer, so n = -s.
        val d = dopplerFactor(Vector2(beta, 0f), Vector2(), -1f, 0f)
        assertEquals(d, starDoppler(beta, 0f, gamma, 1f, 0f), 0.01f)
    }

    @Test
    fun effectiveDopplerClamps() {
        assertEquals(DOPPLER_D_MAX, effectiveDoppler(10f))
        assertEquals(DOPPLER_D_MIN, effectiveDoppler(0.01f))
        assertEquals(DOPPLER_D_MIN, effectiveDoppler(-1f))
        assertEquals(1.5f, effectiveDoppler(1.5f), 1e-5f)
    }
}
