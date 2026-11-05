package com.github.pepek42.asteroids.tool

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.physics.box2d.Box2D
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.physics.ENGINE_ACCEL_PER_C
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import java.io.File
import kotlin.system.exitProcess

/**
 * Balance sweep (ST-101): plays headless levels with scripted pilots over a grid of c, engine acceleration and levels,
 * writes `runs.csv` and `summary.md`. Run with `./gradlew tool:balanceSim -PbalanceArgs="--seeds=8 --levels=1,2"`.
 *
 * Options (all optional, `--name=value`):
 * - `--c=15,20,30` speeds of light to sweep (the default sweep is [SPEED_OF_LIGHT] and 0.75x and 1.5x of it)
 * - `--accel=0.25,default,0.7` engine proper acceleration as a fraction of c; `default` is [ENGINE_ACCEL_PER_C]
 * - `--levels=1,2,3,4,5`, `--bots=cautious,aggressive,idle`, `--seeds=20`, `--seed-base=1`
 * - `--max-seconds=120` pilot seconds per level, `--scheme=auto|mouse` (the control scheme the bots use)
 * - `--out=tool/build/balance` output directory
 */
data class SweepConfig(
    val cs: List<Float> = listOf(SPEED_OF_LIGHT * 0.75f, SPEED_OF_LIGHT, SPEED_OF_LIGHT * 1.5f),
    val accelsPerC: List<Float> = listOf(0.25f, ENGINE_ACCEL_PER_C, 0.7f),
    val levels: List<Int> = listOf(1, 2, 3, 4, 5),
    val bots: List<String> = listOf("cautious", "aggressive", "idle"),
    val seeds: Int = 20,
    val seedBase: Long = 1L,
    val maxSeconds: Float = 120f,
    val scheme: ControlScheme = ControlScheme.AUTO_AIM,
    val out: String = "tool/build/balance",
)

fun parseSweepArgs(args: List<String>): SweepConfig {
    var config = SweepConfig()
    for (arg in args) {
        val parts = arg.removePrefix("--").split("=", limit = 2)
        val key = parts[0]
        val value = parts.getOrElse(1) { "" }
        config = when (key) {
            "c" -> config.copy(cs = value.split(",").map { it.toFloat() })
            "accel" -> config.copy(accelsPerC = value.split(",").map { if (it == "default") ENGINE_ACCEL_PER_C else it.toFloat() })
            "levels" -> config.copy(levels = value.split(",").map { it.toInt() })
            "bots" -> config.copy(bots = value.split(","))
            "seeds" -> config.copy(seeds = value.toInt())
            "seed-base" -> config.copy(seedBase = value.toLong())
            "max-seconds" -> config.copy(maxSeconds = value.toFloat())
            "scheme" -> config.copy(scheme = requireNotNull(ControlScheme.fromFlag(value)) { "Unknown scheme '$value'" })
            "out" -> config.copy(out = value)
            else -> error("Unknown option --$key")
        }
    }
    return config
}

/** Idle never thrusts, so its acceleration is irrelevant: it runs once, at the setting closest to the default. */
fun accelsFor(bot: String, accels: List<Float>): List<Float> =
    if (bot == "idle") listOf(accels.minBy { kotlin.math.abs(it - ENGINE_ACCEL_PER_C) }) else accels

fun planSetups(config: SweepConfig, bot: String): List<LevelSetup> = buildList {
    for (c in config.cs) for (accel in accelsFor(bot, config.accelsPerC)) for (level in config.levels) {
        for (i in 0 until config.seeds) {
            add(LevelSetup(c, accel, level, config.seedBase + i, config.maxSeconds, config.scheme))
        }
    }
}

fun runSweep(config: SweepConfig): List<LevelResult> {
    val plan = config.bots.flatMap { bot -> planSetups(config, bot).map { bot to it } }
    val results = ArrayList<LevelResult>(plan.size)
    val started = System.nanoTime()
    plan.forEachIndexed { i, entry ->
        results += runLevel(entry.second, botByName(entry.first))
        if ((i + 1) % 25 == 0 || i + 1 == plan.size) {
            val elapsed = (System.nanoTime() - started) / 1e9
            println("  ${i + 1}/${plan.size} runs, ${"%.0f".format(elapsed)} s elapsed")
        }
    }
    return results
}

fun writeReports(config: SweepConfig, results: List<LevelResult>, elapsedSeconds: Double) {
    val dir = File(config.out).also { it.mkdirs() }
    File(dir, "runs.csv").writeText(RUNS_CSV_HEADER + "\n" + results.joinToString("\n") { it.csvRow() } + "\n")
    val header = "# Balance sweep\n\n" +
        "c = ${config.cs}, a0/c = ${config.accelsPerC}, levels = ${config.levels}, bots = ${config.bots}, " +
        "${config.seeds} seeds (base ${config.seedBase}), max ${config.maxSeconds} s per level, scheme ${config.scheme}. " +
        "${results.size} runs in ${"%.0f".format(elapsedSeconds)} s."
    File(dir, "summary.md").writeText(summaryMarkdown(aggregate(results), header) + "\n")
    println("Wrote ${File(dir, "runs.csv").path} and ${File(dir, "summary.md").path}")
}

fun main(args: Array<String>) {
    val config = parseSweepArgs(args.toList())
    Box2D.init()
    // HeadlessApplication runs create() on its own thread and gives the game code the Gdx.app it expects (logging).
    HeadlessApplication(object : ApplicationAdapter() {
        override fun create() {
            var status = 0
            try {
                Gdx.app.logLevel = Application.LOG_NONE
                val started = System.nanoTime()
                val results = runSweep(config)
                writeReports(config, results, (System.nanoTime() - started) / 1e9)
            } catch (e: Throwable) {
                e.printStackTrace()
                status = 1
            }
            exitProcess(status)
        }
    }, HeadlessApplicationConfiguration())
}
