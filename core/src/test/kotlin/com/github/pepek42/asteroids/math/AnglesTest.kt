package com.github.pepek42.asteroids.math

import com.badlogic.gdx.math.MathUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.stream.Stream

private const val TOLERANCE = 1e-5f

class AnglesTest {

    companion object {
        private val PI = MathUtils.PI

        @JvmStatic
        fun wrapCases(): Stream<Arguments> = Stream.of(
            Arguments.of(0f, 0f),
            Arguments.of(PI / 2, PI / 2),
            Arguments.of(PI, -PI),
            Arguments.of(-PI, -PI),
            Arguments.of(3 * PI / 2, -PI / 2),
            Arguments.of(7 * PI, -PI),
            Arguments.of(-7.5f * PI, PI / 2),
            Arguments.of(100f, 100f),
        )
    }

    @ParameterizedTest
    @MethodSource("wrapCases")
    fun wrapAngleRadShouldReturnEquivalentAngleInRange(angle: Float, expected: Float) {
        val result = wrapAngleRad(angle)

        assertTrue(result >= -PI - TOLERANCE && result < PI + TOLERANCE, "result $result out of range")
        // compare modulo 2*PI so that the +PI / -PI boundary is accepted
        val diff = result - expected
        val turns = (diff / MathUtils.PI2).roundToInt()
        assertTrue(abs(diff - turns * MathUtils.PI2) < TOLERANCE, "result $result, expected $expected")
    }

    @Test
    fun shortestAngleDiffShouldCrossZeroAcrossLargeAngles() {
        val result = shortestAngleDiffRad(from = 10 * PI + 0.1f, to = -0.1f)

        assertEquals(-0.2f, result, 1e-4f)
    }
}
