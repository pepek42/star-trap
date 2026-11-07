package com.github.pepek42.asteroids.component

import com.github.pepek42.asteroids.faction.FactionEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BaseInfoComponentTest {
    @Test
    fun resetClearsFactionAndEntityTypeAndGivesNewId() {
        val info = BaseInfoComponent()
        info.entityType = EntityType.ASTEROID
        info.faction = FactionEnum.ENEMY
        val oldId = info.id

        info.reset()

        assertEquals(EntityType.UNKNOWN, info.entityType)
        assertEquals(FactionEnum.NEUTRAL, info.faction)
        assertNotEquals(oldId, info.id)
    }
}
