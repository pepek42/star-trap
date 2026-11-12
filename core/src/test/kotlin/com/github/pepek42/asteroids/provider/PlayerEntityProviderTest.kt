package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.shipHandlingCmp
import com.github.pepek42.asteroids.event.GameEventManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PlayerEntityProviderTest {

    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val texture = mock<Texture> {
        on { width } doReturn 24
        on { height } doReturn 24
    }
    private val playerSprite = Sprite(texture)
    private val textureAtlas = mock<TextureAtlas> {
        on { createSprite("spaceship/disc_green") } doReturn playerSprite
    }

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun playerAttachesRelativisticComponentAtRestWithCapturedRestMass() {
        val map = MapProvider(mock<GameEventManager>(), 200f, 200f)
        PlayerEntityProvider(engine, world, map, textureAtlas).spawnPlayerEntity()

        val player = engine.entities.first { it.getComponent(PlayerComponent::class.java) != null }
        val rel = player.getComponent(RelativisticComponent::class.java)
        assertNotNull(rel)
        assertEquals(playerRestMass(), rel.restMass, 1e-4f)
        assertEquals(player.bodyCmp.body.mass, rel.restMass, 1e-4f)
        assertEquals(0f, rel.u.len(), 1e-6f)
    }

    @Test
    fun playerSpawnsWithStartHealthAndBaseEngine() {
        val map = MapProvider(mock<GameEventManager>(), 200f, 200f)
        PlayerEntityProvider(engine, world, map, textureAtlas).spawnPlayerEntity()

        val player = engine.entities.first { it.getComponent(PlayerComponent::class.java) != null }
        assertEquals(1f, player.shipHandlingCmp.engineFactor)
        assertEquals(PLAYER_START_HEALTH, player.healthCmp.health)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
