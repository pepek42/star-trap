package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MapProviderTest {

    @Test
    fun defaultMapIs200x200() {
        // when
        val map = MapProvider(mock<GameEventManager>())

        // then
        assertEquals(200f, map.mapWidth)
        assertEquals(200f, map.mapHeight)
    }

    @Test
    fun explicitSizeIsUsed() {
        // when
        val map = MapProvider(mock<GameEventManager>(), 100f, 100f)

        // then
        assertEquals(100f, map.mapWidth)
        assertEquals(100f, map.mapHeight)
    }
}
