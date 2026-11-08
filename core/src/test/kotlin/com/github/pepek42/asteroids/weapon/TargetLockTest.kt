package com.github.pepek42.asteroids.weapon

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/** Pure tests of the slot-2 targeting lock (ST-165) and the slow lock-on ammo regeneration. */
class TargetLockTest {
    private val dt = 1f / 60f
    private val a = Entity()
    private val b = Entity()

    /** Feeds [candidate] for [seconds]; returns how many calls reported "just locked". */
    private fun WeaponLoadoutComponent.hold(candidate: Entity?, id: Int, seconds: Float, kept: Boolean = false): Int {
        var locks = 0
        repeat((seconds / dt).toInt()) { if (advanceLock(candidate, id, kept || lockState == TargetLockState.LOCKED, dt)) locks++ }
        return locks
    }

    @Test
    fun steadyAimLocksAfterTheLockTimeOnce() {
        val loadout = WeaponLoadoutComponent()
        loadout.hold(a, 1, TARGET_LOCK_TIME_S * 0.5f)
        assertEquals(TargetLockState.LOCKING, loadout.lockState)
        assertNull(loadout.lockedTarget(), "fire must be refused while LOCKING")
        val locks = loadout.hold(a, 1, TARGET_LOCK_TIME_S)
        assertEquals(1, locks)
        assertEquals(TargetLockState.LOCKED, loadout.lockState)
        assertSame(a, loadout.lockedTarget())
    }

    @Test
    fun candidateChangeRestartsLocking() {
        val loadout = WeaponLoadoutComponent()
        loadout.hold(a, 1, TARGET_LOCK_TIME_S * 0.8f)
        loadout.advanceLock(b, 2, false, dt)
        assertEquals(TargetLockState.LOCKING, loadout.lockState)
        assertSame(b, loadout.lockTarget)
        assertEquals(0f, loadout.lockProgress)
        // A pooled entity reused with a new id is a new candidate too.
        loadout.hold(b, 2, TARGET_LOCK_TIME_S * 0.8f)
        loadout.advanceLock(b, 3, false, dt)
        assertEquals(0f, loadout.lockProgress)
    }

    @Test
    fun lockDropsWhenOutOfTheKeepConeAndNoCandidate() {
        val loadout = WeaponLoadoutComponent()
        loadout.hold(a, 1, TARGET_LOCK_TIME_S * 1.2f)
        assertEquals(TargetLockState.LOCKED, loadout.lockState)
        // Still kept even though another target is nearer the aim: no flicker, no switch.
        loadout.advanceLock(b, 2, true, dt)
        assertSame(a, loadout.lockedTarget())
        loadout.advanceLock(null, -1, false, dt)
        assertEquals(TargetLockState.NONE, loadout.lockState)
        assertNull(loadout.lockedTarget())
    }

    @Test
    fun noCandidateNeverLocks() {
        val loadout = WeaponLoadoutComponent()
        assertEquals(0, loadout.hold(null, -1, 3 * TARGET_LOCK_TIME_S))
        assertNull(loadout.lockedTarget())
    }

    @Test
    fun offAxisChecksConeAndRange() {
        val range = TARGET_LOCK_RANGE_LIGHT_SECONDS * 20f
        val inside = TARGET_LOCK_CONE_RAD * 0.5f
        val off = lockOffAxis(10f * cos(inside), 10f * sin(inside), 1f, 0f, range)
        assertEquals(inside, off, 1e-4f)
        assertTrue(lockOffAxis(0f, 10f, 1f, 0f, range) > TARGET_LOCK_KEEP_CONE_RAD)
        assertTrue(lockOffAxis(range * 1.01f, 0f, 1f, 0f, range).isNaN())
        assertTrue(lockOffAxis(0f, 0f, 1f, 0f, range).isNaN())
        assertEquals(MathUtils.PI, lockOffAxis(-5f, 0f, 1f, 0f, range), 1e-4f)
    }

    @Test
    fun reticleClosesFromStartFactorToTheRing() {
        assertEquals(TARGET_LOCK_RETICLE_START_FACTOR * 2f, lockReticleRadius(2f, 0f), 1e-5f)
        assertEquals(2f, lockReticleRadius(2f, 1f), 1e-5f)
        assertFalse(lockReticleRadius(2f, 0.5f) <= 2f)
    }

    @Test
    fun ammoRegeneratesSlowlyUpToTheMagazine() {
        val loadout = WeaponLoadoutComponent()
        loadout.lockOnAmmo = 0
        loadout.regenLockOnAmmo(LOCK_ON_AMMO_REGEN_SECONDS * 0.9f)
        assertEquals(0, loadout.lockOnAmmo)
        loadout.regenLockOnAmmo(LOCK_ON_AMMO_REGEN_SECONDS * 0.2f)
        assertEquals(1, loadout.lockOnAmmo)
        loadout.regenLockOnAmmo(LOCK_ON_AMMO_REGEN_SECONDS * 100f)
        assertEquals(LOCK_ON_AMMO_PER_LEVEL, loadout.lockOnAmmo)
        assertEquals(0f, loadout.lockOnRegen)
    }

    @Test
    fun resetClearsLockAndRefillsAmmo() {
        val loadout = WeaponLoadoutComponent()
        loadout.hold(a, 1, TARGET_LOCK_TIME_S * 1.2f)
        loadout.lockOnAmmo = 0
        loadout.reset()
        assertEquals(TargetLockState.NONE, loadout.lockState)
        assertNull(loadout.lockTarget)
        assertEquals(LOCK_ON_AMMO_PER_LEVEL, loadout.lockOnAmmo)
    }
}
