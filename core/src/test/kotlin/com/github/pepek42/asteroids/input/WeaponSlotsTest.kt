package com.github.pepek42.asteroids.input

import com.badlogic.gdx.Input.Keys
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import com.github.pepek42.asteroids.component.select
import com.github.pepek42.asteroids.component.slot
import com.github.pepek42.asteroids.component.tickInactive
import com.github.pepek42.asteroids.weapon.WeaponType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeaponSlotsTest {
    @Test
    fun keysMapToSlots() {
        val t = InputBindings.DEFAULT
        assertEquals(0, t.keyAction(Keys.NUM_1)!!.weaponSlot())
        assertEquals(1, t.keyAction(Keys.NUM_2)!!.weaponSlot())
        assertEquals(2, t.keyAction(Keys.NUM_3)!!.weaponSlot())
        assertEquals(-1, InputAction.FIRE.weaponSlot())
    }

    @Test
    fun selectingSlotsActivatesLockOnAndThenRockets() {
        val loadout = WeaponLoadoutComponent()
        val weapon = WeaponComponent()
        loadout.select(1, weapon)
        assertEquals(WeaponType.LOCK_ON, weapon.weaponType)
        loadout.select(2, weapon)
        assertEquals(WeaponType.ROCKET, weapon.weaponType)
        assertEquals(2, loadout.activeSlot)
    }

    @Test
    fun eachSlotKeepsItsCooldown() {
        val loadout = WeaponLoadoutComponent()
        val weapon = WeaponComponent().apply { cooldown = 0.3f }
        loadout.select(1, weapon)
        assertEquals(0f, weapon.cooldown)
        loadout.tickInactive(0.1f)
        loadout.select(0, weapon)
        assertEquals(WeaponType.LASER, weapon.weaponType)
        assertEquals(0.2f, weapon.cooldown, 1e-6f)
    }
}

class WeaponLoadoutDefaultsTest {
    @Test
    fun defaultSlotsAreLaserLockOnRockets() {
        val l = WeaponLoadoutComponent()
        assertEquals(WeaponType.LASER, l.slot(0))
        assertEquals(WeaponType.LOCK_ON, l.slot(1))
        assertEquals(WeaponType.ROCKET, l.slot(2))
    }
}
