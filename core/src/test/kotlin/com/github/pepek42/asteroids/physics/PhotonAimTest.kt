package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PhotonAimTest {

    // Arbitrary test value passed as an explicit argument to the pure functions. This is NOT the game's c.
    private val c = 60f

    @Test
    fun staticTargetAimsAtTargetWithLightTime() {
        val out = Vector2(99f, 99f)
        val t = photonInterceptDir(30f, 40f, 0f, 0f, c, out)
        assertEquals(50f / c, t, 1e-5f)
        assertEquals(0.6f, out.x, 1e-5f)
        assertEquals(0.8f, out.y, 1e-5f)
    }

    @Test
    fun movingTargetIsHitByPhotonAtInterceptTime() {
        val out = Vector2()
        val t = photonInterceptDir(30f, 0f, 0f, 20f, c, out)
        assertTrue(t.isFinite() && t > 0f)
        val px = out.x * c * t
        val py = out.y * c * t
        val tx = 30f
        val ty = 0f + 20f * t
        assertTrue(hypot(px - tx, py - ty) < 1e-3f)
        assertEquals(1f, hypot(out.x, out.y), 1e-5f)
    }

    @Test
    fun zeroRangeReturnsNaN() {
        val out = Vector2(7f, 8f)
        val t = photonInterceptDir(0f, 0f, 3f, 4f, c, out)
        assertTrue(t.isNaN())
        assertEquals(7f, out.x)
        assertEquals(8f, out.y)
    }

    @Test
    fun muzzleDirInvertsAberration() {
        val tmp = Vector2()
        val n = Vector2(0f, 1f)
        val muzzle = muzzleDirForWorldDir(0.9f, 0f, n.x, n.y, tmp)
        val back = aberratePhoton(0.9f, 0f, muzzle.x, muzzle.y, Vector2())
        assertEquals(n.x, back.x, 1e-5f)
        assertEquals(n.y, back.y, 1e-5f)
    }

    @Test
    fun muzzleDirInvertsAberrationForRandomCases() {
        val rnd = Random(42L)
        val tmp = Vector2()
        repeat(10) {
            val speed = rnd.nextDouble() * 0.95
            val ba = rnd.nextDouble() * 2 * PI
            val na = rnd.nextDouble() * 2 * PI
            val bx = (speed * cos(ba)).toFloat()
            val by = (speed * sin(ba)).toFloat()
            val nx = cos(na).toFloat()
            val ny = sin(na).toFloat()
            val muzzle = muzzleDirForWorldDir(bx, by, nx, ny, tmp)
            val back = aberratePhoton(bx, by, muzzle.x, muzzle.y, Vector2())
            assertEquals(nx, back.x, 1e-5f)
            assertEquals(ny, back.y, 1e-5f)
        }
    }
}
