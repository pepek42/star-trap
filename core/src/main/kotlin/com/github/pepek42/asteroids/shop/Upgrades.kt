package com.github.pepek42.asteroids.shop

import com.github.pepek42.asteroids.FIRST_LEVEL
import com.github.pepek42.asteroids.environment.ASTEROID_FRAGMENTS
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.environment.asteroidCount
import com.github.pepek42.asteroids.provider.enemyCount
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH

/** Credits for destroying a large asteroid (a small one gives [CREDITS_SMALL_ASTEROID]; its two fragments come on top). */
const val CREDITS_LARGE_ASTEROID = 5

/** Credits for destroying a small asteroid (or fragment). */
const val CREDITS_SMALL_ASTEROID = 2

/** Credits for destroying an enemy ship. */
const val CREDITS_ENEMY_SHIP = 20

/** Credits a destroyed asteroid of [size] is worth. */
fun asteroidCredits(size: AsteroidSize): Int = when (size) {
    AsteroidSize.LARGE -> CREDITS_LARGE_ASTEROID
    AsteroidSize.SMALL -> CREDITS_SMALL_ASTEROID
}

/** Credits one large asteroid yields when shot down completely: itself plus all its small fragments. */
const val CREDITS_PER_LARGE_ROCK_CHAIN = CREDITS_LARGE_ASTEROID + ASTEROID_FRAGMENTS * CREDITS_SMALL_ASTEROID

/**
 * Credits a player would typically have earned by clearing levels 1 to [levels] (ST-168): per level,
 * `asteroidCount(level)` large asteroids, each worth [CREDITS_PER_LARGE_ROCK_CHAIN] with its fragments, plus
 * `enemyCount(level)` enemy ships at [CREDITS_ENEMY_SHIP]. Derived from the spawn functions, so it follows balance changes.
 */
fun typicalCreditsThroughLevel(levels: Int): Int =
    (FIRST_LEVEL..levels).sumOf { asteroidCount(it) * CREDITS_PER_LARGE_ROCK_CHAIN + enemyCount(it) * CREDITS_ENEMY_SHIP }

/** What an upgrade improves. Add a `WEAPON` effect here later (lock-on shots, ST-164). */
enum class UpgradeEffect { ENGINE, HULL }

/**
 * The shop's item table. Each item can be bought up to [maxLevel] times; the price of the next purchase is
 * `basePrice * (1 + level)`. [bonus] is per level: a fraction of the base engine proper acceleration for
 * [UpgradeEffect.ENGINE] (a multiplier on `settings.engineProperAccel`, see `ShipHandlingComponent.engineFactor`), hit
 * points for [UpgradeEffect.HULL]. [titleKey] and [descKey] are i18n keys.
 */
enum class ShopItem(
    val effect: UpgradeEffect,
    val bonus: Float,
    val basePrice: Int,
    val maxLevel: Int,
    val titleKey: String,
    val descKey: String,
) {
    ENGINE_TUNING(UpgradeEffect.ENGINE, 0.10f, 15, 3, "shop_item_engine_tuning", "shop_desc_engine_tuning"),
    AFTERBURNER(UpgradeEffect.ENGINE, 0.20f, 40, 3, "shop_item_afterburner", "shop_desc_afterburner"),
    HULL_PLATING(UpgradeEffect.HULL, 25f, 15, 3, "shop_item_hull_plating", "shop_desc_hull_plating"),
    ABLATIVE_ARMOUR(UpgradeEffect.HULL, 60f, 40, 3, "shop_item_ablative_armour", "shop_desc_ablative_armour"),
}

/** Purchased upgrade levels of the current run (saved with it, see `save/RunSave.kt`). */
class RunUpgrades {
    private val levels = IntArray(ShopItem.entries.size)

    fun level(item: ShopItem): Int = levels[item.ordinal]

    fun setLevel(item: ShopItem, level: Int) {
        levels[item.ordinal] = level.coerceIn(0, item.maxLevel)
    }

    fun isMaxed(item: ShopItem): Boolean = level(item) >= item.maxLevel

    /** Price of the next level of [item]. */
    fun price(item: ShopItem): Int = item.basePrice * (1 + level(item))

    fun add(item: ShopItem) = setLevel(item, level(item) + 1)

    fun clear() = levels.fill(0)

    private fun total(effect: UpgradeEffect): Float =
        ShopItem.entries.filter { it.effect == effect }.sumOf { (it.bonus * level(it)).toDouble() }.toFloat()

    /** Multiplier on the engine proper acceleration: 1 with no upgrades. */
    fun engineFactor(): Float = 1f + total(UpgradeEffect.ENGINE)

    /** Player health at the start of a level. */
    fun startHealth(): Float = PLAYER_START_HEALTH + total(UpgradeEffect.HULL)
}
