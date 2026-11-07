package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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

    @Test
    fun loadMapWithSizeResizesAndBroadcasts() {
        val events = mock<GameEventManager>()
        val map = MapProvider(events)

        map.loadMap(80f, 80f)

        assertEquals(80f, map.mapWidth)
        assertEquals(80f, map.mapHeight)
        assertEquals(40f, map.playerSpawnLocation().x)
        verify(events).newMap(80f, 80f)
    }

    @Test
    fun loadMapRejectsEmptyArena() {
        assertThrows<IllegalArgumentException> { MapProvider(mock<GameEventManager>()).loadMap(0f, 10f) }
    }
}
