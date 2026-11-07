package com.github.pepek42.asteroids.shop

import com.badlogic.gdx.math.RandomXS128
import com.github.pepek42.asteroids.GameState

/** Items shown per shop visit. */
const val OFFER_SIZE = 3

/** Rerolls per visit that cost nothing (owner decision 2026-11-07). */
const val FREE_REROLLS = 2

/** Price of the first paid reroll; each further one costs [REROLL_COST_STEP] more. */
const val REROLL_COST = 10
const val REROLL_COST_STEP = 5

/** Draws up to [size] distinct, not yet maxed items with [random] (partial Fisher-Yates, so the seed fixes the offer). */
fun rollOffer(random: RandomXS128, upgrades: RunUpgrades, size: Int = OFFER_SIZE): List<ShopItem> {
    val pool = ShopItem.entries.filter { !upgrades.isMaxed(it) }.toMutableList()
    val n = minOf(size, pool.size)
    for (i in 0 until n) {
        val j = i + random.nextInt(pool.size - i)
        val t = pool[i]; pool[i] = pool[j]; pool[j] = t
    }
    return pool.subList(0, n).toList()
}

/**
 * One shop visit: the current offer, the reroll counter and the purchases against [state]'s credits and upgrades.
 * [onChanged] runs after every purchase (the screen saves the run there).
 */
class ShopSession(
    private val state: GameState,
    private val random: RandomXS128,
    private val onChanged: () -> Unit = {},
) {
    var offer: List<ShopItem> = rollOffer(random, state.upgrades)
        private set
    var rerollsUsed = 0
        private set

    /** Credits the next reroll costs: 0 for the first [FREE_REROLLS], then [REROLL_COST] rising by [REROLL_COST_STEP]. */
    fun rerollCost(): Int =
        if (rerollsUsed < FREE_REROLLS) 0 else REROLL_COST + REROLL_COST_STEP * (rerollsUsed - FREE_REROLLS)

    fun canReroll(): Boolean = state.credits >= rerollCost()

    /** Pays for and draws a new offer; returns false when the credits do not cover it. */
    fun reroll(): Boolean {
        val cost = rerollCost()
        if (state.credits < cost) return false
        state.credits -= cost
        rerollsUsed++
        offer = rollOffer(random, state.upgrades)
        onChanged()
        return true
    }

    fun canBuy(item: ShopItem): Boolean =
        item in offer && !state.upgrades.isMaxed(item) && state.credits >= state.upgrades.price(item)

    /** Buys [item] from the offer (it leaves the offer); returns false when it is not offered or not affordable. */
    fun buy(item: ShopItem): Boolean {
        if (!canBuy(item)) return false
        state.credits -= state.upgrades.price(item)
        state.upgrades.add(item)
        offer = offer - item
        onChanged()
        return true
    }
}
