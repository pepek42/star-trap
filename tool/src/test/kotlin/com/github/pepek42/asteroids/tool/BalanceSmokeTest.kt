package com.github.pepek42.asteroids.tool

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Balance smoke run (ST-130): a few seeds at c = 20 and the default a0, loosely asserting the ST-101 targets
 * (see docs/tech/balance.md). Tagged "balance", so it is not in `test`; run `./gradlew tool:balanceSmoke`.
 * Few seeds means wide error bars: the bounds are far looser than the targets and only catch a broken balance.
 */
@Tag("balance")
class BalanceSmokeTest {
    private val c = 20f
    private val seeds = 6

    private fun clearRate(bot: () -> BalanceBot, level: Int): Float {
        val results = (1L..seeds).map { runLevel(LevelSetup(c, ENGINE_ACCEL_PER_C, level, it, 120f), bot()) }
        return results.count { it.outcome == Outcome.CLEARED }.toFloat() / seeds
    }

    @Test
    fun cautiousClearsLevelOne() {
        val rate = clearRate({ botByName("cautious") }, 1)
        assertTrue(rate >= 0.8f, "cautious level 1 clear rate $rate (target >= 0.9)")
    }

    @Test
    fun cautiousClearsLevelFiveOften() {
        val rate = clearRate({ botByName("cautious") }, 5)
        assertTrue(rate >= 0.3f, "cautious level 5 clear rate $rate (target >= 0.5)")
    }

    @Test
    fun expertClearsLevelOne() {
        val rate = clearRate({ botByName("expert") }, 1)
        assertTrue(rate >= 0.5f, "expert level 1 clear rate $rate")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
