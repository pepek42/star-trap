package com.github.pepek42.asteroids.shop

import com.badlogic.gdx.math.RandomXS128
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.save.readRunSave
import com.github.pepek42.asteroids.save.restoreRunProgress
import com.github.pepek42.asteroids.save.saveAfterLevel
import com.github.pepek42.asteroids.settings.MemoryKeyValueStore
import com.github.pepek42.asteroids.environment.AsteroidSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShopTest {
    private fun state(credits: Int) = GameState().apply { resetGame(); nextLevel(); this.credits = credits }

    @Test
    fun offerIsReproducibleFromTheSeedAndDistinct() {
        val a = rollOffer(RandomXS128(42), RunUpgrades())
        val b = rollOffer(RandomXS128(42), RunUpgrades())
        assertEquals(a, b)
        assertEquals(OFFER_SIZE, a.size)
        assertEquals(a.size, a.toSet().size)
    }

    @Test
    fun maxedItemsAreNotOffered() {
        val up = RunUpgrades()
        repeat(ShopItem.ENGINE_TUNING.maxLevel) { up.add(ShopItem.ENGINE_TUNING) }
        repeat(20) { seed -> assertFalse(ShopItem.ENGINE_TUNING in rollOffer(RandomXS128(seed.toLong()), up, 10)) }
    }

    @Test
    fun buyingDeductsCreditsAddsUpgradeAndSaves() {
        val s = state(100)
        val store = MemoryKeyValueStore()
        var saves = 0
        val shop = ShopSession(s, RandomXS128(1)) { saves++; saveAfterLevel(store, s, emptyList()) }
        val item = shop.offer.first()
        val price = s.upgrades.price(item)
        assertTrue(shop.buy(item))
        assertEquals(100 - price, s.credits)
        assertEquals(1, s.upgrades.level(item))
        assertEquals(1, saves)
        assertFalse(item in shop.offer)

        val saved = readRunSave(store)!!
        assertEquals(100 - price, saved.credits)
        val restored = GameState()
        restoreRunProgress(saved, restored)
        assertEquals(1, restored.upgrades.level(item))
        assertEquals(100 - price, restored.credits)
    }

    @Test
    fun cannotBuyWithoutCredits() {
        val s = state(0)
        val shop = ShopSession(s, RandomXS128(1))
        assertFalse(shop.buy(shop.offer.first()))
        assertEquals(0, s.credits)
    }

    @Test
    fun firstTwoRerollsAreFreeThirdCosts() {
        val s = state(100)
        val shop = ShopSession(s, RandomXS128(5))
        assertTrue(shop.reroll()); assertEquals(100, s.credits)
        assertTrue(shop.reroll()); assertEquals(100, s.credits)
        assertEquals(REROLL_COST, shop.rerollCost())
        assertTrue(shop.reroll()); assertEquals(100 - REROLL_COST, s.credits)
        assertEquals(REROLL_COST + REROLL_COST_STEP, shop.rerollCost())
    }

    @Test
    fun paidRerollFailsWhenBroke() {
        val s = state(0)
        val shop = ShopSession(s, RandomXS128(5))
        assertTrue(shop.reroll()); assertTrue(shop.reroll())
        assertFalse(shop.reroll())
    }

    @Test
    fun creditsOnlyInNormalRunsAndResetWithTheRun() {
        val s = state(0)
        s.addCredits(asteroidCredits(AsteroidSize.LARGE))
        assertEquals(CREDITS_LARGE_ASTEROID, s.credits)
        s.upgrades.add(ShopItem.HULL_PLATING)
        s.resetGame()
        assertEquals(0, s.credits)
        assertEquals(0, s.upgrades.level(ShopItem.HULL_PLATING))
    }

    @Test
    fun engineFactorScalesWithLevels() {
        val up = RunUpgrades()
        assertEquals(1f, up.engineFactor())
        up.add(ShopItem.ENGINE_TUNING); up.add(ShopItem.AFTERBURNER)
        assertEquals(1f + ShopItem.ENGINE_TUNING.bonus + ShopItem.AFTERBURNER.bonus, up.engineFactor(), 1e-6f)
    }

    @Test
    fun offerAndRerollsSurviveASaveAndRestore() {
        val s = state(100)
        val store = MemoryKeyValueStore()
        val shop = ShopSession(s, RandomXS128(3))
        shop.reroll(); shop.reroll()
        val bought = shop.offer.first()
        shop.buy(bought)
        assertTrue(saveAfterLevel(store, s, emptyList(), shop.visit()))

        val restored = GameState()
        restoreRunProgress(readRunSave(store)!!, restored)
        assertEquals(shop.visit(), restored.pendingShop)
        val resumed = ShopSession(restored, RandomXS128(99), restored.pendingShop)
        assertEquals(shop.offer, resumed.offer)
        assertEquals(2, resumed.rerollsUsed)
    }

    @Test
    fun clearedShopVisitIsNotRestored() {
        val s = state(10)
        val store = MemoryKeyValueStore()
        saveAfterLevel(store, s, emptyList(), ShopSession(s, RandomXS128(3)).visit())
        com.github.pepek42.asteroids.save.clearShopVisit(store)
        assertEquals(null, readRunSave(store)!!.shop)
    }

    @Test
    fun rerollsDoNotShiftTheLevelRng() {
        fun layoutDraw(rerolls: Int): Long {
            val level = RandomXS128(5)
            val s = state(1000)
            val shop = ShopSession(s, RandomXS128(shopSeed(5, s.level)))
            repeat(rerolls) { shop.reroll() }
            return level.nextLong()
        }
        assertEquals(layoutDraw(0), layoutDraw(3))
        assertEquals(shopSeed(5, 2), shopSeed(5, 2))
        assertTrue(shopSeed(5, 2) != shopSeed(5, 3))
    }

    @Test
    fun creditsHudTextShowsGameStateCredits() {
        val s = state(42)
        assertEquals("C42", com.github.pepek42.asteroids.ui.creditsHudText(s.credits) { "C$it" })
        assertEquals(null, com.github.pepek42.asteroids.ui.creditsHudText(null) { "C$it" })
    }

    @Test
    fun continueOpensShopOnlyWithPendingVisit() {
        val s = state(10)
        val store = MemoryKeyValueStore()
        saveAfterLevel(store, s, emptyList(), ShopSession(s, RandomXS128(3)).visit())
        val a = GameState()
        val save = readRunSave(store)!!
        a.resetGame(save.nextLevel); restoreRunProgress(save, a)
        assertTrue(com.github.pepek42.asteroids.save.continueOpensShop(a))
        assertEquals(save.nextLevel - 1, a.level)

        saveAfterLevel(store, s, emptyList(), null)
        val b = GameState()
        val save2 = readRunSave(store)!!
        b.resetGame(save2.nextLevel); restoreRunProgress(save2, b)
        assertFalse(com.github.pepek42.asteroids.save.continueOpensShop(b))
    }
}
