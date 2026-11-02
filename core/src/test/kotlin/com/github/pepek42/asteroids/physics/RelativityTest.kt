package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RelativityTest {

    private val c = SPEED_OF_LIGHT
    private val cases = 10_000

    private fun randomDir(rnd: Random): Vector2 {
        val a = rnd.nextDouble() * 2 * PI
        return Vector2(cos(a).toFloat(), sin(a).toFloat())
    }

    @Test
    fun constantsShouldMatchSpec() {
        assertEquals(50f, GAMMA_MAX_MASSIVE)
        assertEquals(0.99980f, BETA_MAX_MASSIVE, 1e-5f)
        assertEquals(50f, GAMMA_MAX_PROJECTILE)
    }

    @Test
    fun hugeProperVelocityShouldStayBelowC() {
        val rnd = Random(1)
        val out = Vector2()
        repeat(cases) {
            val dir = randomDir(rnd)
            val mag = (Math.pow(10.0, rnd.nextDouble() * 30.0)).toFloat() // up to 1e30
            velocityFromU(dir.x * mag, dir.y * mag, c, out)
            assertTrue(out.len() <= c, "speed ${out.len()} for |u|=$mag")
            assertTrue(!out.x.isNaN() && !out.y.isNaN())
        }
        velocityFromU(Float.MAX_VALUE, Float.MAX_VALUE, c, out)
        assertTrue(out.len() <= c)
    }

    @Test
    fun velocityFromUShouldBeStrictlyBelowCForModerateU() {
        val rnd = Random(2)
        val out = Vector2()
        repeat(cases) {
            val dir = randomDir(rnd)
            val mag = (rnd.nextDouble() * 100 * c).toFloat()
            velocityFromU(dir.x * mag, dir.y * mag, c, out)
            assertTrue(out.len() < c)
        }
    }

    @Test
    fun gammaShouldBeAtLeastOne() {
        val rnd = Random(3)
        repeat(cases) {
            val ux = ((rnd.nextDouble() - 0.5) * 1e4).toFloat()
            val uy = ((rnd.nextDouble() - 0.5) * 1e4).toFloat()
            assertTrue(gammaFromU(ux, uy, c) >= 1f)
            val vx = ((rnd.nextDouble() - 0.5) * 4 * c).toFloat()
            val vy = ((rnd.nextDouble() - 0.5) * 4 * c).toFloat()
            val g = gammaFromV(vx, vy, c)
            assertTrue(g >= 1f && g <= GAMMA_MAX_MASSIVE * 1.0001f, "gamma $g")
        }
        assertEquals(1f, gammaFromU(0f, 0f, c))
    }

    @Test
    fun roundTripUToVToUShouldHold() {
        val rnd = Random(4)
        val v = Vector2()
        val back = Vector2()
        repeat(cases) {
            val dir = randomDir(rnd)
            val gamma = 1.0 + rnd.nextDouble() * (10.0 - 1.0)
            val mag = (c * sqrt(gamma * gamma - 1.0)).toFloat()
            val ux = dir.x * mag
            val uy = dir.y * mag
            velocityFromU(ux, uy, c, v)
            uFromV(v.x, v.y, c, BETA_MAX_MASSIVE, back)
            val err = Vector2(back.x - ux, back.y - uy).len() / mag
            assertTrue(err < 1e-4f, "relative error $err at gamma $gamma")
        }
    }

    @Test
    fun roundTripUToVToUShouldHoldUpToGammaMax() {
        val rnd = Random(9)
        val v = Vector2()
        val back = Vector2()
        repeat(cases) {
            val dir = randomDir(rnd)
            val gamma = 10.0 + rnd.nextDouble() * (50.0 - 10.0)
            val mag = (c * sqrt(gamma * gamma - 1.0)).toFloat()
            val ux = dir.x * mag
            val uy = dir.y * mag
            velocityFromU(ux, uy, c, v)
            uFromV(v.x, v.y, c, BETA_MAX_MASSIVE, back)
            val err = Vector2(back.x - ux, back.y - uy).len() / mag
            assertTrue(err < 1e-3f, "relative error $err at gamma $gamma")
        }
    }

    @Test
    fun uFromVShouldClampToBetaMax() {
        val out = Vector2()
        uFromV(10 * c, 0f, c, BETA_MAX_MASSIVE, out)
        val expected = GAMMA_MAX_MASSIVE * BETA_MAX_MASSIVE * c
        assertEquals(expected, out.x, 1e-3f * expected)
        assertEquals(0f, out.y, 1e-6f)
    }

    @Test
    fun clampSpeedShouldScaleOnlyWhenNeeded() {
        val v = Vector2(3f, 4f)
        clampSpeed(v, 10f)
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
        clampSpeed(v, 2.5f)
        assertEquals(2.5f, v.len(), 1e-5f)
        assertEquals(0.6f, v.x / 2.5f, 1e-5f)
    }

    @Test
    fun clampUShouldLimitGamma() {
        val rnd = Random(5)
        repeat(cases) {
            val dir = randomDir(rnd)
            val mag = (Math.pow(10.0, rnd.nextDouble() * 6.0)).toFloat()
            val u = Vector2(dir.x * mag, dir.y * mag)
            clampU(u, GAMMA_MAX_MASSIVE, c)
            assertTrue(gammaFromU(u.x, u.y, c) <= GAMMA_MAX_MASSIVE * 1.0001f)
        }
        val small = Vector2(1f, 2f)
        clampU(small, GAMMA_MAX_MASSIVE, c)
        assertEquals(1f, small.x)
        assertEquals(2f, small.y)
        val big = Vector2(1000f * c, 0f)
        clampU(big, GAMMA_MAX_PROJECTILE, c)
        assertEquals(c * sqrt(GAMMA_MAX_PROJECTILE * GAMMA_MAX_PROJECTILE - 1f), big.x, 1e-2f)
    }

    @Test
    fun rapidityShouldBeAdditiveForCollinearBoosts() {
        val u1 = 0.5f * c
        val u2 = 1.3f * c
        val out = Vector2()
        // Composite proper velocity of two collinear boosts: sinh(η1 + η2) * c
        val eta = rapidity(u1, 0f, c) + rapidity(u2, 0f, c)
        val v = Vector2(0f, 0f)
        velocityFromU(u1, 0f, c, v)
        val w = Vector2()
        velocityFromU(u2, 0f, c, w)
        addVelocity(v, w, c, out)
        val g = gammaFromV(out.x, out.y, c, 0.9999f)
        val uComposite = g * out.x
        assertEquals(rapidity(uComposite, 0f, c), eta, 1e-3f)
        assertEquals(0f, rapidity(0f, 0f, c))
    }

    @Test
    fun addVelocityShouldNeverReachC() {
        val rnd = Random(6)
        val out = Vector2()
        repeat(cases) {
            val vd = randomDir(rnd)
            val wd = randomDir(rnd)
            val vm = rnd.nextDouble() * 0.99 * c
            val wm = rnd.nextDouble() * 0.99 * c
            val v = Vector2(vd.x * vm.toFloat(), vd.y * vm.toFloat())
            val w = Vector2(wd.x * wm.toFloat(), wd.y * wm.toFloat())
            addVelocity(v, w, c, out)
            assertTrue(out.len() < c, "|out|=${out.len()} v=$v w=$w")
        }
    }

    @Test
    fun addVelocityCollinearHalfCPlusHalfCShouldBeEightTenths() {
        val out = Vector2()
        addVelocity(Vector2(0.5f * c, 0f), Vector2(0.5f * c, 0f), c, out)
        assertEquals(0.8f * c, out.x, 1e-5f * c)
        assertEquals(0f, out.y, 1e-4f)
    }

    @Test
    fun addVelocityShouldFallBackToWhenShooterIsStatic() {
        val out = Vector2()
        addVelocity(Vector2(0f, 0f), Vector2(12f, -7f), c, out)
        assertEquals(12f, out.x)
        assertEquals(-7f, out.y)
        // aliasing out with w is allowed
        val w = Vector2(5f, 6f)
        addVelocity(Vector2(1e-7f, 0f), w, c, w)
        assertEquals(5f, w.x)
        assertEquals(6f, w.y)
    }

    @Test
    fun addVelocityShouldMatchGalileanAtLowSpeed() {
        val out = Vector2()
        addVelocity(Vector2(1f, 2f), Vector2(3f, -1f), 1e6f, out)
        assertEquals(4f, out.x, 1e-3f)
        assertEquals(1f, out.y, 1e-3f)
    }

    @Test
    fun aberratePhotonShouldReturnUnitVector() {
        val rnd = Random(7)
        val out = Vector2()
        repeat(cases) {
            val bd = randomDir(rnd)
            val bm = rnd.nextDouble() * 0.999
            val n = randomDir(rnd)
            aberratePhoton(bd.x * bm.toFloat(), bd.y * bm.toFloat(), n.x, n.y, out)
            assertEquals(1f, out.len(), 1e-5f)
        }
    }

    @Test
    fun aberratePhotonShouldBeIdentityAtRest() {
        val out = Vector2()
        aberratePhoton(0f, 0f, 0.6f, 0.8f, out)
        assertEquals(0.6f, out.x, 1e-6f)
        assertEquals(0.8f, out.y, 1e-6f)
    }

    @Test
    fun aberratePhotonSidewaysAtPointNineShouldGive25Point84Degrees() {
        val out = Vector2()
        aberratePhoton(0.9f, 0f, 0f, 1f, out)
        val deg = Math.toDegrees(atan2(out.y.toDouble(), out.x.toDouble()))
        assertEquals(25.84, deg, 0.05)
    }

    @Test
    fun aberratePhotonShouldRespectBetaDirection() {
        val out = Vector2()
        // Same scenario rotated 90 degrees: ship along +y, shot along -x.
        aberratePhoton(0f, 0.9f, -1f, 0f, out)
        val deg = Math.toDegrees(atan2(out.x.toDouble(), out.y.toDouble()))
        assertEquals(-25.84, deg, 0.05)
    }

    @Test
    fun dopplerFactorShouldMatchResearchTable() {
        val obs = Vector2(0.9f, 0f)
        val src = Vector2(0f, 0f)
        assertEquals(4.36f, dopplerFactor(obs, src, -1f, 0f), 0.01f)
        assertEquals(0.229f, dopplerFactor(obs, src, 1f, 0f), 0.001f)
        assertEquals(2.294f, dopplerFactor(obs, src, 0f, 1f), 0.001f)
    }

    @Test
    fun dopplerFactorShouldBeOneWhenBothAtRest() {
        assertEquals(1f, dopplerFactor(Vector2(), Vector2(), 1f, 0f), 1e-6f)
    }

    @Test
    fun thrustAccelWorldShouldScaleWithGamma() {
        val out = Vector2()
        // Along v at gamma 2: a0.
        thrustAccelWorld(10f, 1f, 0f, 50f, 0f, 2f, out)
        assertEquals(10f, out.len(), 1e-4f)
        assertEquals(10f, out.x, 1e-4f)
        // Perpendicular to v at gamma 2: a0 / 2.
        thrustAccelWorld(10f, 0f, 1f, 50f, 0f, 2f, out)
        assertEquals(5f, out.len(), 1e-4f)
        assertEquals(5f, out.y, 1e-4f)
    }

    @Test
    fun thrustAccelWorldShouldUseEngineDirectionAtRest() {
        val out = Vector2()
        thrustAccelWorld(10f, 0.6f, 0.8f, 0f, 0f, 1f, out)
        assertEquals(6f, out.x, 1e-5f)
        assertEquals(8f, out.y, 1e-5f)
    }

    @Test
    fun gammaRelShouldMatchKnownValues() {
        val v = Vector2(0.3f * c, 0.2f * c)
        assertEquals(1f, gammaRel(v, v, c), 1e-5f)
        assertEquals(1.6667f, gammaRel(Vector2(0.5f * c, 0f), Vector2(-0.5f * c, 0f), c), 1e-4f)
    }

    @Test
    fun gammaRelShouldAgreeWithAddedVelocity() {
        val rnd = Random(8)
        val out = Vector2()
        repeat(1000) {
            val vd = randomDir(rnd)
            val wd = randomDir(rnd)
            val v = Vector2(vd.x * 0.4f * c, vd.y * 0.4f * c)
            val w = Vector2(wd.x * 0.4f * c, wd.y * 0.4f * c)
            // gamma of w in the frame of v equals gammaRel(v, composed velocity)
            addVelocity(v, w, c, out)
            val expected = gammaFromV(w.x, w.y, c)
            assertEquals(expected, gammaRel(v, out, c), 1e-3f)
        }
    }

    @Test
    fun nonFiniteInputShouldGiveZeroVectorOrGammaOne() {
        val out = Vector2(1f, 1f)
        assertEquals(1f, gammaFromU(Float.NaN, 0f, c))
        assertEquals(1f, gammaFromU(Float.POSITIVE_INFINITY, 0f, c))
        assertEquals(1f, gammaFromV(Float.NaN, 0f, c))
        assertEquals(1f, gammaRel(Vector2(Float.NaN, 0f), Vector2(), c))
        assertEquals(1f, dopplerFactor(Vector2(Float.NaN, 0f), Vector2(), 1f, 0f))

        assertEquals(Vector2.Zero, velocityFromU(Float.NaN, 1f, c, out))
        out.set(1f, 1f)
        assertEquals(Vector2.Zero, uFromV(Float.POSITIVE_INFINITY, 0f, c, BETA_MAX_MASSIVE, out))
        assertEquals(Vector2.Zero, clampSpeed(Vector2(Float.NaN, 1f), 5f))
        assertEquals(Vector2.Zero, clampU(Vector2(1f, Float.NaN), 10f, c))
        out.set(1f, 1f)
        assertEquals(Vector2.Zero, addVelocity(Vector2(Float.NaN, 0f), Vector2(1f, 0f), c, out))
        out.set(1f, 1f)
        assertEquals(Vector2.Zero, addVelocity(Vector2(1f, 0f), Vector2(Float.NaN, 0f), c, out))
        out.set(1f, 1f)
        assertEquals(Vector2.Zero, aberratePhoton(Float.NaN, 0f, 1f, 0f, out))
        out.set(1f, 1f)
        assertEquals(Vector2.Zero, thrustAccelWorld(1f, Float.NaN, 0f, 1f, 0f, 1f, out))
    }

    @Test
    fun brakeBoostFactorFollowsAngle() {
        val k = 2f
        assertEquals(1f, brakeBoostFactor(1f, 0f, 1f, 0f, k), 1e-6f)
        assertEquals(3f, brakeBoostFactor(-1f, 0f, 1f, 0f, k), 1e-6f)
        assertEquals(1f, brakeBoostFactor(0f, 1f, 1f, 0f, k), 1e-6f)
        val cos120 = -0.5f
        val sin120 = sqrt(3f) / 2f
        assertEquals(2f, brakeBoostFactor(cos120, sin120, 1f, 0f, k), 1e-5f)
        assertEquals(1f, brakeBoostFactor(-1f, 0f, 0f, 0f, k), 1e-6f)
        assertEquals(1f, brakeBoostFactor(-1f, 0f, 1f, 0f, 0f), 1e-6f)
    }
}
