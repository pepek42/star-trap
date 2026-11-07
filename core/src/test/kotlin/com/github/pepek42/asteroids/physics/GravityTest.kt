package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Assumes the default c ([SPEED_OF_LIGHT]) and `BH_RS`; gm comes from `gmFromRs(BH_RS, c)`. Geometry (orbit shape,
 * deflection, capture radius) does not depend on c, only on `r_s`. Photon and fall tests step with the game step
 * [PHYSICS_UPDATE_INTERVAL].
 */
class GravityTest {
    private val c = SPEED_OF_LIGHT
    private val rs = BH_RS
    private val gm = gmFromRs(rs, c)
    private val soft = 0.1f

    /** Photon step for the capture tests: a tenth of the game step (0.033 u per step at default c), for accuracy near the photon sphere. */
    private val FINE_DT = PHYSICS_UPDATE_INTERVAL / 10f

    @Test
    fun gmAndRsAreInverse() {
        assertEquals(rs * c * c / 2f, gm, 1e-3f)
        assertEquals(rs, rsFromGm(gm, c), 1e-5f)
        assertEquals(0.5f, rsFromGm(gmFromRs(0.5f, 33f), 33f), 1e-5f)
    }

    @Test
    fun gravTimeFactorMatchesTheStaticClockRate() {
        assertEquals(0.7071f, gravTimeFactor(4f, 2f), 1e-4f)
        // Inside 1.01 r_s the radius is clamped, so the factor stays above zero.
        val clamped = sqrt(1f - 1f / GRAV_TIME_MIN_R_PER_RS)
        assertEquals(sqrt(1f - 1f / 1.01f), clamped, 1e-6f)
        assertEquals(clamped, gravTimeFactor(1f, 2f), 1e-6f)
        assertEquals(clamped, gravTimeFactor(0f, 2f), 1e-6f)
        assertEquals(1f, gravTimeFactor(1000f, 2f), 2e-3f)
        assertEquals(1f, gravTimeFactor(3f, 0f), 0f)
    }

    /** One RK4 step in proper time of the state (x, y, ux, uy); dx/dtau = u. */
    private fun rk4(s: DoubleArray, h: Double) {
        val tmp = Vector2()
        fun deriv(x: Double, y: Double, ux: Double, uy: Double, d: DoubleArray) {
            gravityAccelTau(x.toFloat(), y.toFloat(), ux.toFloat(), uy.toFloat(), gm, c, soft, tmp)
            d[0] = ux; d[1] = uy; d[2] = tmp.x.toDouble(); d[3] = tmp.y.toDouble()
        }
        val k1 = DoubleArray(4); val k2 = DoubleArray(4); val k3 = DoubleArray(4); val k4 = DoubleArray(4)
        deriv(s[0], s[1], s[2], s[3], k1)
        deriv(s[0] + h / 2 * k1[0], s[1] + h / 2 * k1[1], s[2] + h / 2 * k1[2], s[3] + h / 2 * k1[3], k2)
        deriv(s[0] + h / 2 * k2[0], s[1] + h / 2 * k2[1], s[2] + h / 2 * k2[2], s[3] + h / 2 * k2[3], k3)
        deriv(s[0] + h * k3[0], s[1] + h * k3[1], s[2] + h * k3[2], s[3] + h * k3[3], k4)
        for (i in 0..3) s[i] += h / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i])
    }

    @Test
    fun circularOrbitStaysCircular() {
        val r0 = 12f
        val ut = circularOrbitL(r0, rs, gm) / r0
        val s = doubleArrayOf(r0.toDouble(), 0.0, 0.0, ut.toDouble())
        val period = 2 * PI * r0 / ut
        var rMin = Double.MAX_VALUE
        var rMax = 0.0
        val h = 1e-3
        for (i in 0 until (period / h).toInt()) {
            rk4(s, h)
            val r = hypot(s[0], s[1])
            rMin = min(rMin, r); rMax = max(rMax, r)
        }
        assertEquals(r0.toDouble(), rMin, 0.05 * r0)
        assertEquals(r0.toDouble(), rMax, 0.05 * r0)
    }

    @Test
    fun circularOrbitLIsNanInsidePhotonSphere() {
        assertTrue(circularOrbitL(1.4f * rs, rs, gm).isNaN())
        assertTrue(!circularOrbitL(3f * rs, rs, gm).isNaN())
    }

    /** Flies a photon from x = -[startDist] along +x with impact parameter [b]; returns the final direction, or null if captured. */
    private fun flyPhoton(b: Float, startDist: Float, dt: Float): Vector2? {
        var x = -startDist
        var y = b
        val n = Vector2(1f, 0f)
        var steps = 0
        while (steps++ < 2_000_000) {
            bendPhoton(x, y, n.x, n.y, gm, c, dt, n)
            x += c * n.x * dt
            y += c * n.y * dt
            if (hypot(x, y) < rs) return null
            if (hypot(x, y) > startDist * 1.01f) return n
        }
        error("photon did not leave")
    }

    @Test
    fun photonDeflectionMatchesWeakFieldLimit() {
        val b = 40f
        val n = flyPhoton(b, 400f, PHYSICS_UPDATE_INTERVAL)!!
        val deflection = abs(atan2(n.y, n.x))
        val expected = 2f * rs / b
        assertEquals(expected, deflection, 0.15f * expected)
        assertTrue(n.y < 0f, "bent toward the hole (hole at y = 0, photon at y = +b)")
    }

    @Test
    fun photonBelowCriticalImpactParameterIsCaptured() {
        assertEquals(null, flyPhoton(2.3f * rs, 100f, FINE_DT))
    }

    @Test
    fun photonAboveCriticalImpactParameterEscapes() {
        val n = flyPhoton(2.9f * rs, 100f, FINE_DT)
        assertTrue(n != null)
        assertEquals(1f, n!!.len(), 1e-4f)
    }

    @Test
    fun bendPhotonReturnsOutAndKeepsUnitLength() {
        val out = Vector2()
        assertTrue(bendPhoton(5f, 3f, 0f, 1f, gm, c, PHYSICS_UPDATE_INTERVAL, out) === out)
        assertEquals(1f, out.len(), 1e-5f)
        // Radial photon: L_k = 0, no bending.
        bendPhoton(5f, 0f, 1f, 0f, gm, c, PHYSICS_UPDATE_INTERVAL, out)
        assertEquals(1f, out.x, 1e-6f)
        assertEquals(0f, out.y, 1e-6f)
        // On the source: unchanged, no NaN.
        bendPhoton(0f, 0f, 0f, 1f, gm, c, PHYSICS_UPDATE_INTERVAL, out)
        assertEquals(1f, out.y, 0f)
    }

    @Test
    fun farFieldMatchesNewton() {
        val a = gravityAccelTau(100f, 0f, 0f, 0.5f, gm, c, soft, Vector2())
        val n = newtonAccel(100f, 0f, gm, soft, Vector2())
        assertEquals(n.x, a.x, 0.01f * abs(n.x))
        assertEquals(n.y, a.y, 0.01f * abs(n.x))
        assertEquals(-gm / (100f * 100f), n.x, 1e-6f)
    }

    @Test
    fun softeningCapsTheAcceleration() {
        val inside = newtonAccel(0.01f, 0f, gm, soft, Vector2())
        val atSoft = newtonAccel(soft, 0f, gm, soft, Vector2())
        assertEquals(atSoft.x * 0.01f / soft, inside.x, 1e-3f)
    }

    /**
     * Two equal sources, 60 u apart at rest, each pulled by `SOURCE_COUPLING_DEFAULT * newtonAccel` of the other
     * (semi-implicit Euler, game step). Radial free fall with mu = coupling * (gm1 + gm2): the separation r(t) obeys
     * `t = sqrt(r0^3 / 2mu) (sqrt(x(1-x)) + acos(sqrt(x)))`, `x = r / r0`.
     */
    @Test
    fun twoSourcesFallTogetherOnTheAnalyticTime() {
        val r0 = 60f
        val rEnd = 4f
        val dt = PHYSICS_UPDATE_INTERVAL
        val mu = SOURCE_COUPLING_DEFAULT * (gm + gm)
        val x = rEnd / r0
        val expected = sqrt(r0 * r0 * r0 / (2f * mu)) * (sqrt(x * (1f - x)) + acos(sqrt(x)))
        var p1 = 0f; var v1 = 0f
        var p2 = r0; var v2 = 0f
        val a = Vector2()
        var t = 0f
        var steps = 0
        while (p2 - p1 > rEnd && steps++ < 10_000_000) {
            newtonAccel(p1 - p2, 0f, gm, soft, a)
            v1 += SOURCE_COUPLING_DEFAULT * a.x * dt
            newtonAccel(p2 - p1, 0f, gm, soft, a)
            v2 += SOURCE_COUPLING_DEFAULT * a.x * dt
            p1 += v1 * dt
            p2 += v2 * dt
            t += dt
        }
        assertEquals(expected, t, 0.05f * expected)
        assertEquals(0f, v1 + v2, 1e-3f)
    }

    @Test
    fun mergeConservesMomentumAndCentreLiesBetween() {
        val out = FloatArray(5)
        assertTrue(mergeSources(2f, 0f, 0f, 3f, -1f, 6f, 8f, 4f, -1f, 2f, out) === out)
        assertEquals(8f, out[0], 0f)
        assertEquals(2f * 3f + 6f * -1f, out[0] * out[3], 1e-5f)
        assertEquals(2f * -1f + 6f * 2f, out[0] * out[4], 1e-5f)
        assertEquals(6f, out[1], 1e-5f)
        assertEquals(3f, out[2], 1e-5f)
        // On the segment: (x, y) = t * (8, 4), t in [0, 1].
        val t = out[1] / 8f
        assertTrue(t in 0f..1f)
        assertEquals(t * 4f, out[2], 1e-5f)
        // Heavier body pulls the centre toward itself.
        assertTrue(out[1] > 4f)
    }

    @Test
    fun tidalAccelIsTwoGmLOverRCubed() {
        assertEquals(50f, tidalAccel(2f, 200f, 1f), 1e-4f)
        // r is clamped, so the centre gives a large finite value.
        assertTrue(tidalAccel(0f, 200f, 1f).isFinite())
    }
}
