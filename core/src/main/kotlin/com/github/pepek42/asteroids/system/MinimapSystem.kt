package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.Viewport
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.transformCmp
import ktx.ashley.allOf
import ktx.graphics.use

class MinimapSystem(
    private val batch: SpriteBatch,
    private val minimapViewport: Viewport,
    private val shape: ShapeRenderer = ShapeRenderer(),
) : IteratingSystem(allOf(BaseInfoComponent::class, TransformComponent::class).get()) {

    override fun update(deltaTime: Float) {
        minimapViewport.apply()
        batch.use(minimapViewport.camera) {
            super.update(deltaTime)
        }
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val baseInfo = entity.baseInfoCmp
        val entityType = baseInfo.entityType
        val faction = baseInfo.faction

        val size: Float = when (entityType) {
            EntityType.SHIP -> 3f
            EntityType.ASTEROID -> 2f
            EntityType.PROJECTILE -> 1.25f
            else -> 0f
        }

        val transformComp = entity.transformCmp
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.color = faction.color
        shape.circle(
            transformComp.interpolatedPosition.x,
            transformComp.interpolatedPosition.y,
            size,
        )
        shape.end()
    }
}
