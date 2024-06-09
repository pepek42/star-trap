package com.github.pepek42.asteroids.faction

import com.badlogic.gdx.graphics.Color
import kotlin.experimental.or

const val playerEntityBit = (1 shl 1).toShort()
const val neutralEntityBit = (1 shl 2).toShort()
const val enemyEntityBit = (1 shl 3).toShort()

enum class FactionEnum(
    val entityMaskBit: Short,
    val categoryBits: Short,
    val color: Color
) {
    PLAYER(playerEntityBit, neutralEntityBit or enemyEntityBit, Color.BLUE),

    /**
     * Like asteroids
     */
    NEUTRAL(neutralEntityBit, playerEntityBit or neutralEntityBit or enemyEntityBit, Color.WHITE),

    ENEMY(enemyEntityBit, playerEntityBit or neutralEntityBit, Color.RED),
    ;

}
