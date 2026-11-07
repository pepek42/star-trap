package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.gmFromRs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** ST-089: the pilot-view disk particle model that `accretion_disk.vert` mirrors. Assumes c = [SPEED_OF_LIGHT], `r_s` = [BH_RS]. */
class AccretionDiskPilotTest {
    private val c = SPEED_OF_LIGHT
    private val gm = gmFromRs(BH_RS, c)
    private val r = DISK_INNER // ISCO: the fastest particles
    private val obsX = 100f // observer on the +x axis, the hole at the origin
    private val tau = obsX / c
    private val out = Vector2()
    private val halfPi = (PI / 2).toFloat()

    private fun d(phi: Float, delay: Boolean = false, betaX: Float = 0f): Float =
        diskParticleApparent(r, phi, gm, c, obsX, 0f, betaX, 0f, 0f, 0f, tau, 0f, 0f, delay, aberration = true, out)

    @Test
    fun approachingSideIsBlueshiftedAndRecedingSideRedshifted() {
        // Counter-clockwise orbit: at phi = -90° the particle moves along +x, toward the observer.
        val toward = d(-halfPi)
        val away = d(halfPi)
        assertTrue(toward > 1f, "toward D = $toward")
        assertTrue(away < 1f, "away D = $away")
        // Both move almost exactly along the line of sight: D = 1 / (γ (1 ∓ β cos θ)), so D_toward / D_away = (1 + β cos θ) / (1 - β cos θ).
        val beta = diskOmega(r, gm) * r / c
        val cosT = obsX / hypot(obsX, r)
        assertEquals((1f + beta * cosT) / (1f - beta * cosT), toward / away, 1e-3f)
    }

    @Test
    fun mirroredParticlesHaveTheExpectedDopplerProduct() {
        // Mirrored across the line of sight, the particle at -phi moves opposite to the mirror image of the one at phi, so
        // β·n̂ flips sign: D(phi) D(-phi) = 1 / (γ² (1 - (β·n̂)²)).
        val beta = diskOmega(r, gm) * r / c
        val gamma2 = 1f / (1f - beta * beta)
        for (deg in listOf(20f, 60f, 100f, 150f)) {
            val a = deg * PI.toFloat() / 180f
            val px = r * cos(a)
            val py = r * sin(a)
            val len = hypot(obsX - px, -py)
            val bn = beta * (-sin(a) * (obsX - px) + cos(a) * (-py)) / len
            assertEquals(1f / (gamma2 * (1f - bn * bn)), d(a) * d(-a), 1e-3f, "deg = $deg")
        }
    }

    @Test
    fun delayMovesTheParticleAlongItsOrbitByLessThanVTimesT() {
        val v = diskOmega(r, gm) * r
        for (deg in 0 until 360 step 30) {
            val phi = deg * PI.toFloat() / 180f
            d(phi, delay = false)
            val ux = out.x
            val uy = out.y
            d(phi, delay = true)
            // The extra delay of a particle relative to the centre is at most r / c.
            assertTrue(hypot(out.x - ux, out.y - uy) <= v * r / c + 1e-3f, "deg = $deg")
            // Still on the circle (β_obs = 0: no boost).
            assertEquals(r, hypot(out.x, out.y), 1e-3f)
        }
    }

    @Test
    fun delayedParticleSitsOnThePastLightCone() {
        val omega = diskOmega(r, gm)
        for (deg in 0 until 360 step 45) {
            val phi = deg * PI.toFloat() / 180f
            d(phi, delay = true)
            var dPhi = atan2(out.y, out.x) - phi
            while (dPhi > PI) dPhi -= (2 * PI).toFloat()
            while (dPhi < -PI) dPhi += (2 * PI).toFloat()
            val s = -dPhi / omega
            val dist = hypot(out.x - obsX, out.y)
            assertEquals(c * (tau + s), dist, 0.02f, "deg = $deg")
        }
    }

    @Test
    fun movingObserverBlueshiftsTheWholeDiskAhead() {
        val rest = d(0f)
        val flying = d(0f, betaX = 0.5f) // +x: away from the hole at the origin
        val flyingIn = d(0f, betaX = -0.5f)
        assertTrue(flyingIn > rest && flying < rest, "rest $rest, in $flyingIn, out $flying")
        assertEquals(sqrt(3f), flyingIn / rest, 1e-3f) // γ(1 + β) = sqrt(3) for β = 0.5 head-on
    }
}
