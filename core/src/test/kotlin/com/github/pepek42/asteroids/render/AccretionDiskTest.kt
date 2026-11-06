package com.github.pepek42.asteroids.render

import com.github.pepek42.asteroids.physics.BH_RS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccretionDiskTest {
    private val rIn = DISK_INNER

    @Test
    fun temperaturePeaksAtFortyNineThirtySixthsOfInnerRadius() {
        val peakR = DISK_PEAK_X * rIn
        val peak = diskTemperature(peakR, rIn, DISK_T_PEAK)
        assertEquals(DISK_T_PEAK, peak, DISK_T_PEAK * 0.01f)
        for (f in listOf(1.05f, 1.2f, 1.3f, 1.45f, 1.7f, 2.5f, 4f)) {
            assertTrue(diskTemperature(f * rIn, rIn, DISK_T_PEAK) <= peak + 1e-3f, "x = $f")
        }
    }

    @Test
    fun temperatureIsZeroAtAndInsideInnerRadius() {
        assertEquals(0f, diskTemperature(rIn, rIn, DISK_T_PEAK), 1e-3f)
        assertEquals(0f, diskTemperature(rIn * 0.5f, rIn, DISK_T_PEAK))
    }

    @Test
    fun redshiftAtIscoIsOneOverSqrtTwo() {
        assertEquals(0.7071f, diskRedshift(3f * BH_RS, BH_RS), 1e-3f)
        assertEquals(1f, diskRedshift(1e6f, BH_RS), 1e-3f)
    }

    @Test
    fun omegaIsKeplerian() {
        assertEquals(1.443f, diskOmega(12f, 3600f), 1e-3f)
    }

    @Test
    fun meshBuilderFillsAllVerticesWithRadiiInsideTheDisk() {
        val v = buildDiskVertices(42L, BH_RS)
        assertEquals(DISK_PARTICLES * 4 * DISK_VERTEX_FLOATS, v.size)
        for (i in 0 until DISK_PARTICLES * 4) {
            val r = v[i * DISK_VERTEX_FLOATS + 2]
            assertTrue(r >= DISK_INNER - 1e-3f && r <= DISK_OUTER + 1e-3f, "radius $r")
            assertEquals(DISK_PARTICLE_SIZE, v[i * DISK_VERTEX_FLOATS + 4])
            assertTrue(v[i * DISK_VERTEX_FLOATS + 5] >= 0f)
        }
    }

    @Test
    fun meshScalesWithSchwarzschildRadius() {
        val v = buildDiskVertices(7L, 2f * BH_RS, count = 200)
        for (i in 0 until 200 * 4) {
            val r = v[i * DISK_VERTEX_FLOATS + 2]
            assertTrue(r >= 2f * DISK_INNER - 1e-3f && r <= 2f * DISK_OUTER + 1e-3f, "radius $r")
        }
    }

    @Test
    fun radiiAreDenserInside() {
        val v = buildDiskVertices(1L, BH_RS)
        val mid = kotlin.math.sqrt(DISK_INNER * DISK_OUTER)
        var inner = 0
        for (i in 0 until DISK_PARTICLES) if (v[i * 4 * DISK_VERTEX_FLOATS + 2] < mid) inner++
        // density ~ 1/r is uniform in ln r, so the geometric mean splits the particles in half.
        assertEquals(DISK_PARTICLES / 2f, inner.toFloat(), DISK_PARTICLES * 0.05f)
    }

    @Test
    fun indicesFormTwoTrianglesPerQuadAndFitShort() {
        val idx = buildDiskIndices()
        assertEquals(DISK_PARTICLES * 6, idx.size)
        assertTrue(DISK_PARTICLES * 4 <= Short.MAX_VALUE)
        assertEquals(4, idx[6].toInt())
        assertEquals(6, idx[8].toInt())
        assertEquals(((DISK_PARTICLES - 1) * 4), idx.last().toInt())
    }
}
