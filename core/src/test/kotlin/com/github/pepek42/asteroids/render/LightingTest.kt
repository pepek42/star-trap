package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightingTest {
    private val eps = 1e-5f

    @Test
    fun falloffEndpoints() {
        assertEquals(1f, falloff(0f, 4f, 100f), eps)
        assertEquals(0.5f, falloff(4f, 4f, 100f), eps)
        assertEquals(0f, falloff(100f, 4f, 100f), eps)
        assertEquals(0f, falloff(150f, 4f, 100f), eps)
    }

    @Test
    fun falloffIsMonotonic() {
        var prev = falloff(0f, 4f, 100f)
        var d = 0.5f
        while (d <= 110f) {
            val f = falloff(d, 4f, 100f)
            assertTrue(f <= prev, "falloff rose at d=$d")
            prev = f
            d += 0.5f
        }
    }

    private fun directional(dx: Float, dy: Float, intensity: Float = 1f) = LightSource().apply {
        x = dx; y = dy; directional = true; this.intensity = intensity
    }

    @Test
    fun normalFacingTheLightGetsAmbientPlusIntensity() {
        val light = directional(1f, 0f, intensity = 2f)
        val dir = com.badlogic.gdx.math.Vector3()
        lightDir(0f, 0f, light, dir)
        val out = FloatArray(3)
        shade(dir.x, dir.y, dir.z, 0f, 0f, arrayOf(light), intArrayOf(0), 1, LIGHT_AMBIENT, out)
        assertArrayEquals(floatArrayOf(LIGHT_AMBIENT + 2f, LIGHT_AMBIENT + 2f, LIGHT_AMBIENT + 2f), out, eps)
    }

    @Test
    fun normalFacingAwayWithoutWrapGetsAmbientOnly() {
        val light = directional(1f, 0f)
        val dir = com.badlogic.gdx.math.Vector3()
        lightDir(0f, 0f, light, dir)
        val out = FloatArray(3)
        shade(-dir.x, -dir.y, -dir.z, 0f, 0f, arrayOf(light), intArrayOf(0), 1, LIGHT_AMBIENT, out)
        assertArrayEquals(floatArrayOf(LIGHT_AMBIENT, LIGHT_AMBIENT, LIGHT_AMBIENT), out, eps)
    }

    @Test
    fun fullWrapStillLightsTheTerminatorAtHalf() {
        // wrap = 1 (inside an extended source): a normal perpendicular to the light gets half the front's diffuse,
        // and even the far side is not cut off before N·L = -1.
        assertEquals(0.5f * wrapDiffuse(1f, 1f), wrapDiffuse(0f, 1f), eps)
        assertEquals(0f, wrapDiffuse(0f, 0f), eps)
        assertTrue(wrapDiffuse(-0.5f, 1f) > 0f)
        val inside = LightSource().apply { size = 24f }
        assertEquals(1f, lightWrap(inside, 10f), eps)
        assertEquals(0.5f, lightWrap(inside, 48f), eps)
    }

    @Test
    fun pickLightsReturnsStrongestInOrderAndSkipsOutOfRange() {
        val lights = arrayOf(
            LightSource().apply { x = 10f; intensity = 1f },   // 1
            LightSource().apply { x = 5f; intensity = 1f },    // 0: nearest
            LightSource().apply { x = 30f; intensity = 1f },   // weak
            LightSource().apply { x = 150f; intensity = 50f }, // beyond range -> skipped
            LightSource().apply { x = 20f; intensity = 1f },   // 2
            LightSource().apply { x = 10f; intensity = 0.5f }, // 3
        )
        val idx = IntArray(MAX_LIGHTS_PER_SPRITE)
        val n = pickLights(0f, 0f, lights, lights.size, MAX_LIGHTS_PER_SPRITE, idx)
        assertEquals(4, n)
        assertArrayEquals(intArrayOf(1, 0, 5, 4), idx)
        for (i in 1 until n) {
            assertTrue(lightStrengthAt(0f, 0f, lights[idx[i - 1]]) >= lightStrengthAt(0f, 0f, lights[idx[i]]))
        }
    }

    @Test
    fun diskLightUsesBlackbodyColour() {
        val light = LightSource()
        configureDiskLight(light, FloatArray(3))
        assertEquals(DISK_LIGHT_INTENSITY, light.intensity, eps)
        assertEquals(DISK_OUTER, light.size, eps)
        assertTrue(light.r > 0f && light.g > 0f && light.b > 0f)
    }
}
