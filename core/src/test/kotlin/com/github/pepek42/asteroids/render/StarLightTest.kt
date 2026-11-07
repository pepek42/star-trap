package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.RandomXS128
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StarLightTest {
    private fun rolls(seed: Long, levels: Int): List<Pair<Float, Float>> {
        val random = RandomXS128(seed)
        val star = StarLight()
        return List(levels) { star.roll(random); star.angleRad to star.kelvin }
    }

    @Test
    fun sameSeedGivesSameStarPerLevel() {
        assertEquals(rolls(42L, 3), rolls(42L, 3))
    }

    @Test
    fun differentLevelsGiveDifferentStars() {
        val r = rolls(42L, 3)
        assertNotEquals(r[0], r[1])
        assertNotEquals(r[1], r[2])
    }

    @Test
    fun rollStaysInRange() {
        val random = RandomXS128(7L)
        val star = StarLight()
        repeat(200) {
            star.roll(random)
            assertTrue(star.kelvin in STAR_KELVIN_MIN..STAR_KELVIN_MAX)
            assertEquals(1f, star.dirX * star.dirX + star.dirY * star.dirY, 1e-5f)
        }
    }

    @Test
    fun starLightIsBrighterFacingTheStarAndIndependentOfPosition() {
        val star = StarLight().apply { set(0.7f, 6000f) }
        val light = LightSource()
        star.configure(light, FloatArray(3))
        val lights = arrayOf(light)
        val toStar = Math.atan2(star.dirY.toDouble(), star.dirX.toDouble()).toFloat()

        fun shade(angleOffset: Float, cx: Float, cy: Float): Float {
            // Texel at the sphere rim on the side of [angleOffset] from the star direction; rotRad 0, sphere mode.
            val out = FloatArray(3)
            val qx = kotlin.math.cos(toStar + angleOffset) * 0.9f
            val qy = kotlin.math.sin(toStar + angleOffset) * 0.9f
            shadeSpriteTexel(qx, qy, 1f, 1f, cx, cy, 0f, NORMAL_MODE_SPHERE, 0f, 0f, 1f, lights, intArrayOf(0), 1, LIGHT_AMBIENT, out)
            return out[1]
        }
        assertTrue(shade(0f, 0f, 0f) > shade(Math.PI.toFloat(), 0f, 0f))
        assertEquals(shade(0f, 0f, 0f), shade(0f, 90f, -60f), 1e-5f)
        assertEquals(shade(Math.PI.toFloat(), 0f, 0f), shade(Math.PI.toFloat(), -40f, 70f), 1e-5f)
    }

    @Test
    fun aFlatSpriteIsLitAboutAsBrightAsWithoutAStar() {
        val star = StarLight().apply { set(1f, 6000f) }
        val light = LightSource()
        star.configure(light, FloatArray(3))
        val out = FloatArray(3)
        shadeSpriteTexel(0f, 0f, 1f, 1f, 0f, 0f, 0f, NORMAL_MODE_FLAT, 0f, 0f, 1f, arrayOf(light), intArrayOf(0), 1, LIGHT_AMBIENT, out)
        // Flat +z normal: ambient + intensity * sin(elevation) (colour of the blackbody scales each channel, so check the green one).
        val expected = LIGHT_AMBIENT + light.g * STAR_LIGHT_INTENSITY * kotlin.math.sin(STAR_ELEVATION)
        assertEquals(expected, out[1], 1e-4f)
        assertTrue(out[1] > 0.9f)
    }
}

