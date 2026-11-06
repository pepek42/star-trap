package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.gmFromRs
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

enum class GravitySourceKind { BLACK_HOLE, PLANET }

/**
 * A body that pulls others (research E14, ST-083). Every source has a Box2D dynamic body and its position and velocity come
 * from it; sources have no `RelativisticComponent` (Newtonian path, they are slow).
 */
class GravitySourceComponent : Component, Pool.Poolable {
    var kind = GravitySourceKind.BLACK_HOLE

    /** Schwarzschild radius of a [GravitySourceKind.BLACK_HOLE]; `gm` is derived from it and c every step. */
    var rs = BH_RS

    /** Gravitational parameter, used as stored for a [GravitySourceKind.PLANET]. Ignored for black holes. */
    var gm = 0f

    /** Distance below which the pull stops growing (the acceleration is evaluated at this radius). */
    var softening = DEFAULT_SOFTENING

    /** Surface radius of a [GravitySourceKind.PLANET]. */
    var radius = 0f

    /** Effective `GM` at light speed [c]: from [rs] for a black hole, the stored [gm] for a planet. */
    fun effectiveGm(c: Float): Float = if (kind == GravitySourceKind.BLACK_HOLE) gmFromRs(rs, c) else gm

    override fun reset() {
        kind = GravitySourceKind.BLACK_HOLE
        rs = BH_RS
        gm = 0f
        softening = DEFAULT_SOFTENING
        radius = 0f
    }

    companion object {
        const val DEFAULT_SOFTENING = 0.5f
    }
}

val gravitySourceMapper = mapperFor<GravitySourceComponent>()
val Entity.gravitySourceCmp by propertyFor(gravitySourceMapper)
val Entity.gravitySourceCmpOptional by optionalPropertyFor(gravitySourceMapper)
