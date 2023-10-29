package com.github.pepek42.asteroids.faction

import kotlin.experimental.or

const val playerEntityBit = (1 shl 1).toShort()
const val neutralEntityBit = (1 shl 2).toShort()
const val enemyEntityBit = (1 shl 3).toShort()

enum class FactionEnum {
    PLAYER,

    /**
     * Like asteroids
     */
    NEUTRAL,

    ENEMY,
    ;

    val entityMaskBit: Short
        get() = when (this) {
            ENEMY -> enemyEntityBit
            PLAYER -> playerEntityBit
            else -> neutralEntityBit
        }

    val categoryBits: Short
        get() = when (this) {
            ENEMY -> playerEntityBit or neutralEntityBit
            PLAYER -> neutralEntityBit or enemyEntityBit
            NEUTRAL -> playerEntityBit or neutralEntityBit or enemyEntityBit
        }
}
