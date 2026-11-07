package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Intensity of the distant star's directional light (ST-099); the disk lights add on top. */
const val STAR_LIGHT_INTENSITY = 1.25f

/**
 * Elevation of the star above the arena plane (rad, 40 degrees). A star in the plane would give flat +z normals (ships, the
 * asteroid interior) N·L = 0; at 40 degrees `LIGHT_AMBIENT + STAR_LIGHT_INTENSITY·sin(40°)` is about 1.15 on a flat sprite,
 * so lit sprites read as before on their lit side. FAKE, physics-lies.md row 42.
 */
const val STAR_ELEVATION = 0.6981317f

/** Range of the star's blackbody temperature (K), drawn uniformly per level. */
const val STAR_KELVIN_MIN = 4000f
const val STAR_KELVIN_MAX = 9000f

/**
 * The distant star of one level (ST-099): a direction in the arena plane (unit vector toward the star) and a blackbody
 * temperature. Static and infinitely far, so the direction is the same everywhere in the arena frame. [roll] draws the
 * values from the seeded random, so a level always gets the same star.
 */
class StarLight {
    var angleRad = 0f
        private set
    var kelvin = STAR_KELVIN_MIN
        private set
    var dirX = 1f
        private set
    var dirY = 0f
        private set
    var active = false
        private set

    /** Draws angle then temperature from [random] and makes the star active. */
    fun roll(random: RandomXS128) {
        set(random.nextFloat() * MathUtils.PI2, STAR_KELVIN_MIN + random.nextFloat() * (STAR_KELVIN_MAX - STAR_KELVIN_MIN))
    }

    fun set(angleRad: Float, kelvin: Float) {
        this.angleRad = angleRad
        this.kelvin = kelvin
        dirX = cos(angleRad)
        dirY = sin(angleRad)
        active = true
    }

    /** Fills [light] as the directional star light, colour from [BlackbodyLut]. */
    fun configure(light: LightSource, rgbScratch: FloatArray) {
        BlackbodyLut.rgbInto(kelvin, rgbScratch)
        light.r = rgbScratch[0]
        light.g = rgbScratch[1]
        light.b = rgbScratch[2]
        light.intensity = STAR_LIGHT_INTENSITY
        light.directional = true
        light.x = dirX
        light.y = dirY
        light.z = tan(STAR_ELEVATION)
    }
}
