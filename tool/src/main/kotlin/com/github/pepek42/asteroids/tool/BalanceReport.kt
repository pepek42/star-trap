package com.github.pepek42.asteroids.tool

import java.util.Locale

/** Aggregate of all runs of one bot at one (c, engine accel, level) setting. */
class BalanceRow(
    val bot: String,
    val c: Float,
    val accelPerC: Float,
    val level: Int,
    val runs: Int,
    val cleared: Float,
    val died: Float,
    val timeout: Float,
    /** Mean pilot seconds until death over the runs that died; NaN when none did. */
    val timeToDeath: Float,
    /** Mean pilot seconds until the level was cleared over the runs that cleared it; NaN when none did. */
    val timeToClear: Float,
    val damagePerMin: Float,
    val killsPerMin: Float,
    val gammaMean: Float,
    val gammaP95: Float,
    val warpCapped: Float,
    val stepsPerFrame: Float,
    /** Runs that spawned enemies; the enemy columns below only cover these. */
    val enemyRuns: Int = 0,
    /** Fraction of the enemy runs in which every enemy died. */
    val enemyClear: Float = Float.NaN,
    val damageFromEnemies: Float = Float.NaN,
    /** Mean pilot seconds until the last enemy died, over the runs that killed them all; NaN when none did. */
    val timeToKillEnemies: Float = Float.NaN,
)

private fun List<LevelResult>.fraction(o: Outcome) = count { it.outcome == o }.toFloat() / size

private fun List<LevelResult>.meanOf(f: (LevelResult) -> Float): Float =
    if (isEmpty()) Float.NaN else (sumOf { f(it).toDouble() } / size).toFloat()

/** Groups [results] by (bot, c, accel, level) and averages. Rates are totals over total pilot minutes, not means of rates. */
fun aggregate(results: List<LevelResult>): List<BalanceRow> =
    results.groupBy { listOf(it.bot, it.setup.c, it.setup.engineAccelPerC, it.setup.level) }
        .values.map { group ->
            val first = group.first()
            val minutes = group.sumOf { it.seconds.toDouble() } / 60.0
            val died = group.filter { it.outcome == Outcome.DIED }
            val cleared = group.filter { it.outcome == Outcome.CLEARED }
            val withEnemies = group.filter { it.enemiesSpawned > 0 }
            BalanceRow(
                bot = first.bot,
                c = first.setup.c,
                accelPerC = first.setup.engineAccelPerC,
                level = first.setup.level,
                runs = group.size,
                cleared = group.fraction(Outcome.CLEARED),
                died = group.fraction(Outcome.DIED),
                timeout = group.fraction(Outcome.TIMEOUT),
                timeToDeath = died.meanOf { it.seconds },
                timeToClear = cleared.meanOf { it.seconds },
                damagePerMin = (group.sumOf { it.damageTaken.toDouble() } / minutes).toFloat(),
                killsPerMin = (group.sumOf { it.kills.toDouble() } / minutes).toFloat(),
                gammaMean = group.meanOf { it.gammaMean },
                gammaP95 = group.meanOf { it.gammaP95 },
                warpCapped = group.meanOf { it.warpCappedFraction },
                stepsPerFrame = group.meanOf { it.stepsPerFrameMean },
                enemyRuns = withEnemies.size,
                enemyClear = withEnemies.meanOf { if (it.enemyClearSeconds.isNaN()) 0f else 1f },
                damageFromEnemies = withEnemies.meanOf { it.damageFromEnemies },
                timeToKillEnemies = withEnemies.filter { !it.enemyClearSeconds.isNaN() }.meanOf { it.enemyClearSeconds },
            )
        }
        .sortedWith(compareBy({ it.bot }, { it.c }, { it.accelPerC }, { it.level }))

private fun f(x: Float, digits: Int = 1) = if (x.isNaN()) "-" else String.format(Locale.ROOT, "%.${digits}f", x)

private fun pct(x: Float) = f(x * 100f, 0)

const val RUNS_CSV_HEADER =
    "bot,c,accel_per_c,level,seed,outcome,seconds,damage_taken,kills,gamma_mean,gamma_p95,gamma_max," +
        "warp_capped_fraction,steps_capped_fraction,steps_per_frame,enemies_spawned,enemies_killed,damage_from_enemies,enemy_clear_seconds"

fun LevelResult.csvRow(): String = listOf(
    bot, setup.c, setup.engineAccelPerC, setup.level, setup.seed, outcome, f(seconds, 2), f(damageTaken, 1), kills,
    f(gammaMean, 3), f(gammaP95, 3), f(gammaMax, 3), f(warpCappedFraction, 4), f(stepsCappedFraction, 4), f(stepsPerFrameMean, 3),
    enemiesSpawned, enemiesKilled, f(damageFromEnemies, 1), f(enemyClearSeconds, 2),
).joinToString(",")

/** One markdown table per bot, sorted by c, accel, level. */
fun summaryMarkdown(rows: List<BalanceRow>, header: String): String = buildString {
    appendLine(header)
    for (entry in rows.groupBy { it.bot }) {
        val bot = entry.key
        val botRows = entry.value
        appendLine()
        appendLine("### $bot")
        appendLine()
        appendLine("| c | a0/c | lvl | n | clear % | died % | timeout % | t death s | t clear s | dmg/min | kills/min | γ mean | γ p95 | warp capped % | steps/frame |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (r in botRows) {
            appendLine(
                "| ${f(r.c, 0)} | ${f(r.accelPerC, 2)} | ${r.level} | ${r.runs} | ${pct(r.cleared)} | ${pct(r.died)} | " +
                    "${pct(r.timeout)} | ${f(r.timeToDeath)} | ${f(r.timeToClear)} | ${f(r.damagePerMin)} | ${f(r.killsPerMin)} | " +
                    "${f(r.gammaMean, 2)} | ${f(r.gammaP95, 2)} | ${f(r.warpCapped * 100f)} | ${f(r.stepsPerFrame, 2)} |"
            )
        }
        val enemyRows = botRows.filter { it.enemyRuns > 0 }
        if (enemyRows.isNotEmpty()) {
            appendLine()
            appendLine("Enemies ($bot):")
            appendLine()
            appendLine("| c | a0/c | lvl | enemy runs | all killed % | dmg from enemies (mean) | t kill all s |")
            appendLine("|---|---|---|---|---|---|---|")
            for (r in enemyRows) {
                appendLine(
                    "| ${f(r.c, 0)} | ${f(r.accelPerC, 2)} | ${r.level} | ${r.enemyRuns} | ${pct(r.enemyClear)} | " +
                        "${f(r.damageFromEnemies)} | ${f(r.timeToKillEnemies)} |"
                )
            }
        }
    }
}
