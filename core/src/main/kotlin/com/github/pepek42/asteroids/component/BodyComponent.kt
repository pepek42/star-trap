package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

class BodyComponent : Component, Pool.Poolable {
    lateinit var body: Body

    /** Max extent of the fixtures from the body origin; -1 until computed by BoundaryFieldSystem. */
    var boundingRadius = -1f

    override fun reset() {
        boundingRadius = -1f
        assert(body.userData == null) { "Body userData should be null from remove component" }
    }
}

fun computeBoundingRadius(body: Body): Float {
    var result = 0f
    val v = Vector2()
    for (fixture in body.fixtureList) {
        val shape = fixture.shape
        val r = when (shape) {
            is CircleShape -> shape.position.len() + shape.radius
            is PolygonShape -> {
                var m = 0f
                for (i in 0 until shape.vertexCount) {
                    shape.getVertex(i, v)
                    m = maxOf(m, v.len())
                }
                m
            }
            else -> 0f
        }
        result = maxOf(result, r)
    }
    return result
}

val bodyMapper = mapperFor<BodyComponent>()
val Entity.bodyCmp by propertyFor(bodyMapper)
val Entity.bodyCmpOptional by optionalPropertyFor(bodyMapper)
