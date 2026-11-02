package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/**
 * Marks a projectile as a photon flying at exactly c. [dir] is its unit travel direction in W; it is kept separately
 * from the body velocity because gravity will later bend photons.
 */
class PhotonComponent : Component, Pool.Poolable {
    val dir = Vector2()

    override fun reset() {
        dir.setZero()
    }
}

val photonMapper = mapperFor<PhotonComponent>()
val Entity.photonCmp by propertyFor(photonMapper)
val Entity.photonCmpOptional by optionalPropertyFor(photonMapper)
