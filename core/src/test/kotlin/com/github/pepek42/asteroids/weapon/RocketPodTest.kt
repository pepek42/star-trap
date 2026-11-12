package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pod logic of ST-181 on the loadout, no world needed. Assumes the 60 Hz tick as the frame step. */
class RocketPodTest {
    private val dt = 1f / 60f

    /** Holds the trigger for [seconds] and returns the time of every launch. */
    private fun holdFire(loadout: WeaponLoadoutComponent, seconds: Float): List<Float> {
        val launches = ArrayList<Float>()
        var t = 0f
        while (t < seconds) {
            loadout.tickRocketPod(dt)
            if (loadout.canFireRocket()) {
                loadout.consumeRocket()
                launches.add(t)
            }
            t += dt
        }
        return launches
    }

    @Test
    fun holdingFireLaunchesFiveRocketsASalvoIntervalApartThenReloadsAndLaunchesFiveMore() {
        val launches = holdFire(WeaponLoadoutComponent(), 2f * ROCKET_RELOAD_SECONDS + 0.5f)
        assertEquals(2 * ROCKET_POD_SIZE, launches.size, "two pods in the window: $launches")

        for (i in 1 until ROCKET_POD_SIZE) {
            val gap = launches[i] - launches[i - 1]
            assertTrue(gap >= ROCKET_SALVO_INTERVAL - 1e-4f && gap <= ROCKET_SALVO_INTERVAL + dt + 1e-4f, "salvo gap $gap")
        }
        // Nothing between the 5th rocket and the end of the reload, then the next pod starts.
        val reloadGap = launches[ROCKET_POD_SIZE] - launches[ROCKET_POD_SIZE - 1]
        assertTrue(reloadGap >= ROCKET_RELOAD_SECONDS - 1e-4f && reloadGap <= ROCKET_RELOAD_SECONDS + dt + 1e-4f, "reload gap $reloadGap")
        for (i in ROCKET_POD_SIZE + 1 until 2 * ROCKET_POD_SIZE) {
            val gap = launches[i] - launches[i - 1]
            assertTrue(gap >= ROCKET_SALVO_INTERVAL - 1e-4f && gap <= ROCKET_SALVO_INTERVAL + dt + 1e-4f, "second salvo gap $gap")
        }
    }

    @Test
    fun theFirstRocketLeavesImmediatelyOnAFreshPod() {
        val loadout = WeaponLoadoutComponent()
        assertEquals(ROCKET_POD_SIZE, loadout.rocketsInPod)
        assertTrue(loadout.canFireRocket())
    }

    @Test
    fun theReloadRunsWhileAnotherSlotIsSelected() {
        val loadout = WeaponLoadoutComponent()
        repeat(ROCKET_POD_SIZE) {
            loadout.consumeRocket()
            loadout.tickRocketPod(ROCKET_SALVO_INTERVAL)
        }
        assertEquals(0, loadout.rocketsInPod)
        loadout.tickRocketPod(ROCKET_RELOAD_SECONDS + dt)
        assertEquals(ROCKET_POD_SIZE, loadout.rocketsInPod)
    }

    @Test
    fun resetRefillsThePod() {
        val loadout = WeaponLoadoutComponent()
        repeat(ROCKET_POD_SIZE) { loadout.consumeRocket() }
        loadout.reset()
        assertEquals(ROCKET_POD_SIZE, loadout.rocketsInPod)
        assertEquals(0f, loadout.rocketReloadLeft)
        assertEquals(0f, loadout.rocketSalvoCooldown)
    }
}
