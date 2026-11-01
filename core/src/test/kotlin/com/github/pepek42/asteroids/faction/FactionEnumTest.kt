package com.github.pepek42.asteroids.faction

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactionEnumTest {

    @Test
    fun playerDoesNotCollideWithPlayer() {
        assertFalse(FactionEnum.PLAYER.collidesWith(FactionEnum.PLAYER))
    }

    @Test
    fun playerCollidesWithNeutral() {
        assertTrue(FactionEnum.PLAYER.collidesWith(FactionEnum.NEUTRAL))
    }

    @Test
    fun playerCollidesWithEnemy() {
        assertTrue(FactionEnum.PLAYER.collidesWith(FactionEnum.ENEMY))
    }

    @Test
    fun enemyDoesNotCollideWithEnemy() {
        assertFalse(FactionEnum.ENEMY.collidesWith(FactionEnum.ENEMY))
    }

    @Test
    fun neutralCollidesWithNeutral() {
        assertTrue(FactionEnum.NEUTRAL.collidesWith(FactionEnum.NEUTRAL))
    }
}
