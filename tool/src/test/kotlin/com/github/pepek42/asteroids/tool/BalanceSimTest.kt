package com.github.pepek42.asteroids.tool

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BalanceSimTest {
    private fun result(bot: String, outcome: Outcome, seconds: Float, damage: Float = 0f, kills: Int = 0, level: Int = 1) =
        LevelResult(
            LevelSetup(SPEED_OF_LIGHT, ENGINE_ACCEL_PER_C, level, 1L, 120f), bot, outcome, seconds, damage, kills,
            gammaMean = 1.5f, gammaP95 = 2f, gammaMax = 3f, warpCappedFraction = 0.1f, stepsCappedFraction = 0f,
            stepsPerFrameMean = 1.5f,
        )

    @Test
    fun percentileUsesNearestRank() {
        val values = floatArrayOf(5f, 1f, 3f, 2f, 4f, 99f)
        assertEquals(3f, percentile(values, 5, 0.5f))
        assertEquals(5f, percentile(values, 5, 0.95f))
        assertEquals(0f, percentile(values, 0, 0.5f))
    }

    @Test
    fun aggregateComputesFractionsAndRatesOverTotalMinutes() {
        val rows = aggregate(
            listOf(
                result("cautious", Outcome.CLEARED, 30f, damage = 10f, kills = 6),
                result("cautious", Outcome.DIED, 30f, damage = 100f, kills = 2),
                result("cautious", Outcome.TIMEOUT, 60f, kills = 4),
                result("idle", Outcome.TIMEOUT, 120f),
            )
        )
        assertEquals(2, rows.size)
        val row = rows.first { it.bot == "cautious" }
        assertEquals(3, row.runs)
        assertEquals(1f / 3f, row.cleared, 1e-6f)
        assertEquals(1f / 3f, row.died, 1e-6f)
        assertEquals(30f, row.timeToDeath)
        assertEquals(30f, row.timeToClear)
        // 110 damage over 2 minutes, 12 kills over 2 minutes.
        assertEquals(55f, row.damagePerMin, 1e-3f)
        assertEquals(6f, row.killsPerMin, 1e-3f)
        assertTrue(rows.first { it.bot == "idle" }.timeToDeath.isNaN())
    }

    @Test
    fun summaryHasATablePerBot() {
        val md = summaryMarkdown(aggregate(listOf(result("cautious", Outcome.CLEARED, 10f))), "# H")
        assertTrue(md.contains("### cautious"))
        assertTrue(md.contains("| 20 | 0.41 | 1 | 1 | 100 |"))
    }

    @Test
    fun parseArgsOverridesDefaultsAndMapsDefaultAccel() {
        val config = parseSweepArgs(listOf("--seeds=3", "--levels=2,4", "--c=15,20", "--accel=0.3,default", "--bots=idle"))
        assertEquals(3, config.seeds)
        assertEquals(listOf(2, 4), config.levels)
        assertEquals(listOf(15f, 20f), config.cs)
        assertEquals(listOf(0.3f, ENGINE_ACCEL_PER_C), config.accelsPerC)
        assertEquals(listOf("idle"), config.bots)
    }

    @Test
    fun defaultSweepFollowsTheBaseConstants() {
        val config = SweepConfig()
        assertTrue(SPEED_OF_LIGHT in config.cs)
        assertTrue(ENGINE_ACCEL_PER_C in config.accelsPerC)
    }

    @Test
    fun idleRunsOncePerAccelSetting() {
        val config = SweepConfig(seeds = 2, levels = listOf(1, 2))
        assertEquals(config.cs.size * 2 * 2, planSetups(config, "idle").size)
        assertEquals(config.cs.size * config.accelsPerC.size * 2 * 2, planSetups(config, "cautious").size)
        assertTrue(planSetups(config, "idle").all { it.engineAccelPerC == ENGINE_ACCEL_PER_C })
    }

    @Test
    fun idleLevelTimesOutAtTheConfiguredLengthAndIsDeterministic() {
        val setup = LevelSetup(SPEED_OF_LIGHT, ENGINE_ACCEL_PER_C, 1, 7L, maxSeconds = 2f)
        val a = runLevel(setup, IdleBot())
        val b = runLevel(setup, IdleBot())
        assertEquals(Outcome.TIMEOUT, a.outcome)
        assertEquals(2f, a.seconds, 0.02f)
        assertEquals(1f, a.gammaMean, 1e-6f)
        assertEquals(a.kills, b.kills)
        assertEquals(a.stepsPerFrameMean, b.stepsPerFrameMean)
    }

    @Test
    fun cautiousBotMovesTheShipAndStaysUnderItsSpeedLimit() {
        val setup = LevelSetup(SPEED_OF_LIGHT, ENGINE_ACCEL_PER_C, 1, 3L, maxSeconds = 20f)
        val r = runLevel(setup, CautiousBot())
        assertTrue(r.gammaMax > 1.01f, "bot never thrusted")
        assertTrue(r.gammaMax < 1.5f, "bot broke its beta limit, gamma ${r.gammaMax}")
    }

    @Test
    fun levelTwoRunWithOneEnemyCountsItsKill() {
        val setup = LevelSetup(SPEED_OF_LIGHT, ENGINE_ACCEL_PER_C, 2, 5L, maxSeconds = 60f, enemies = 1)
        val r = runLevel(setup, AggressiveBot())
        assertEquals(1, r.enemiesSpawned)
        assertTrue(r.enemiesKilled in 0..1)
        assertEquals(r.enemiesKilled == 1, !r.enemyClearSeconds.isNaN())
    }

    @Test
    fun enemyColumnsAggregateOverRunsWithEnemiesOnly() {
        fun withEnemy(clear: Float, dmg: Float) = LevelResult(
            LevelSetup(SPEED_OF_LIGHT, ENGINE_ACCEL_PER_C, 3, 1L, 120f), "cautious", Outcome.CLEARED, 50f, dmg, 0,
            1.5f, 2f, 3f, 0f, 0f, 1.5f, enemiesSpawned = 1, enemiesKilled = if (clear.isNaN()) 0 else 1,
            damageFromEnemies = dmg, enemyClearSeconds = clear,
        )
        val row = aggregate(listOf(withEnemy(30f, 10f), withEnemy(Float.NaN, 30f))).single()
        assertEquals(2, row.enemyRuns)
        assertEquals(0.5f, row.enemyClear)
        assertEquals(20f, row.damageFromEnemies, 1e-4f)
        assertEquals(30f, row.timeToKillEnemies)
        assertTrue(summaryMarkdown(listOf(row), "# H").contains("Enemies (cautious)"))
        assertTrue(aggregate(listOf(result("cautious", Outcome.CLEARED, 10f))).single().enemyRuns == 0)
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
