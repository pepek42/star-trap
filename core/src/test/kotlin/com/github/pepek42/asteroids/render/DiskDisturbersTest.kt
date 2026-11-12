package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.BH_RS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiskDisturbersTest {
    private val reach = diskScatterReach(0.5f, BH_RS)

    private fun strength(d: DiskDisturbers, slot: Int) = d.uniforms[slot * DISK_DISTURBER_FLOATS + 3]

    @Test
    fun strengthFallsFromOneToZeroOverTheRecoveryTime() {
        assertEquals(1f, diskScatterStrength(0f))
        assertEquals(0.25f, diskScatterStrength(DISK_SCATTER_RECOVERY / 2f), 1e-6f)
        assertEquals(0f, diskScatterStrength(DISK_SCATTER_RECOVERY))
        assertEquals(0f, diskScatterStrength(10f))
    }

    @Test
    fun pushMovesParticlesToTheEdgeOfTheCircleAtFullStrengthAndNothingOutside() {
        val p = Vector2(1f, 0f)
        diskScatterPush(p, 0f, 0f, reach, 1f)
        assertEquals(reach, p.x, 1e-5f)
        assertEquals(0f, p.y, 1e-5f)
        val far = Vector2(reach + 0.1f, 0f)
        diskScatterPush(far, 0f, 0f, reach, 1f)
        assertEquals(reach + 0.1f, far.x)
        val half = Vector2(0f, 1f)
        diskScatterPush(half, 0f, 0f, reach, 0.5f)
        assertEquals(1f + (reach - 1f) * 0.5f, half.y, 1e-5f)
        val none = Vector2(0.2f, 0.2f)
        diskScatterPush(none, 0f, 0f, reach, 0f)
        assertEquals(0.2f, none.x)
    }

    @Test
    fun particleAtTheCentreGetsAFiniteDeterministicPush() {
        val p = Vector2(3f, 3f)
        diskScatterPush(p, 3f, 3f, reach, 1f)
        assertEquals(3f + reach, p.x, 1e-5f)
        assertEquals(3f, p.y, 1e-5f)
    }

    @Test
    fun overlapChecksTheAnnulusWithTheReach() {
        assertTrue(diskOverlaps((DISK_INNER + DISK_OUTER) / 2f, 0f, BH_RS, reach))
        assertTrue(diskOverlaps(DISK_OUTER + reach * 0.5f, 0f, BH_RS, reach))
        assertTrue(!diskOverlaps(DISK_OUTER + reach * 2f, 0f, BH_RS, reach))
        assertTrue(!diskOverlaps(DISK_INNER - reach * 2f, 0f, BH_RS, reach))
    }

    @Test
    fun theLiveSlotFollowsTheBodyAndFadesAfterItLeaves() {
        val d = DiskDisturbers()
        d.begin(10f)
        d.touch(5f, 6f, reach)
        d.end()
        assertEquals(5f, d.uniforms[0])
        assertEquals(6f, d.uniforms[1])
        assertEquals(reach, d.uniforms[2])
        assertEquals(1f, strength(d, 0))
        d.begin(10.5f)
        d.end()
        assertEquals(diskScatterStrength(0.5f), strength(d, 0), 1e-6f)
        d.begin(10f + DISK_SCATTER_RECOVERY)
        d.end()
        assertEquals(0f, strength(d, 0))
    }

    @Test
    fun aMovingBodyLeavesATrailOfFadingSlots() {
        val d = DiskDisturbers()
        var t = 0f
        val dt = 1f / 60f
        while (t < 1.2f) {
            d.begin(t)
            d.touch(t * 8f, 0f, reach)
            d.end()
            t += dt
        }
        var live = 0
        for (i in 1 until DISK_DISTURBERS) if (strength(d, i) > 0f) live++
        assertEquals(DISK_DISTURBERS - 1, live)
        // The trail is behind the body: every trail slot is at a smaller x than the live slot.
        for (i in 1 until DISK_DISTURBERS) assertTrue(d.uniforms[i * DISK_DISTURBER_FLOATS] < d.uniforms[0])
        // Older slots are weaker than newer ones, and none exceeds the live slot.
        for (i in 1 until DISK_DISTURBERS) assertTrue(strength(d, i) <= strength(d, 0) + 1e-6f)
    }

    @Test
    fun timeGoingBackwardsClearsTheSlots() {
        val d = DiskDisturbers()
        d.begin(50f)
        d.touch(0f, 0f, reach)
        d.end()
        d.begin(1f)
        d.end()
        for (i in 0 until DISK_DISTURBERS) assertEquals(0f, strength(d, i))
    }

    @Test
    fun scatterAllAppliesEverySlot() {
        val d = DiskDisturbers()
        d.begin(0f)
        d.touch(0f, 0f, reach)
        d.end()
        val p = Vector2(0.1f, 0f)
        diskScatterAll(p, d.uniforms)
        assertEquals(reach, p.x, 1e-5f)
    }
}
