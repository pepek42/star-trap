package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyMapper
import ktx.ashley.get
import ktx.math.vec2
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.stream.Stream

class WeaponProjectileProviderTest {

    private val bodyMock = mock<Body>()
    private val textureAtlasMock = mock<TextureAtlas>()
    private val engineMock = mock<Engine> {
        on { createEntity() } doReturn Entity()
        on { createComponent(BodyComponent::class.java) } doReturn BodyComponent()
    }
    private val worldMock = mock<World> {

    }
    private val weaponCmp = WeaponComponent()
    private val entityMock = mock<Entity> {
        on { get(bodyMapper) } doReturn BodyComponent().apply { body = bodyMock }
    }
    private val weaponProjectileProvider = WeaponProjectileProvider(textureAtlasMock, engineMock, worldMock)

    // TODO
    @ParameterizedTest
    @MethodSource
    fun shouldSpawnProjectile(position: Vector2) {
        // given
        `when`(bodyMock.position).thenReturn(position)

        // when
//        weaponProjectileProvider.spawnProjectile(weaponCmp, entityMock)

        // then
    }

    companion object {
        @JvmStatic
        fun shouldSpawnProjectile(): Stream<Arguments> = Stream.of(
            Arguments.of(vec2(0f, 0f))
        )
    }
}
