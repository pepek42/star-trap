package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.moveMapper
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.gdxArrayOf
import ktx.math.vec2
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

private const val DELTA_TIME = 0.5f

class MoveSystemTest {

    private val moveComponent = MoveComponent()
    private val bodyMock = mock<Body>()
    private val entityMock = mock<Entity> {
        on { get(bodyMapper) } doReturn BodyComponent().apply { body = bodyMock }
        on { get(moveMapper) } doReturn moveComponent
    }
    private val engineMock = mock<Engine> {
        on { getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get()) } doReturn ImmutableArray(
            gdxArrayOf(entityMock)
        )
    }

    private val moveSystem = MoveSystem()

    @ParameterizedTest
    @CsvSource(
        "1,4.9999995,8.6602545",
        "0.5,2.4999998,4.3301272",
    )
    fun thrustersShouldWork(thrusters: Float, expectedForceX: Float, expectedForceY: Float) {
        // given
        moveComponent.thrusters = thrusters
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.update(DELTA_TIME)

        // then
        verify(bodyMock).applyForceToCenter(vec2(expectedForceX, expectedForceY), true)
    }

    @Test
    fun thrustersShouldNotWorkIfNoInput() {
        // given
        moveComponent.thrusters = 0f
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.update(DELTA_TIME)

        // then
        verify(bodyMock, never()).applyForceToCenter(any<Vector2>(), any<Boolean>())
    }

    // TODO rotation tests
}
