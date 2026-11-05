package com.github.pepek42.asteroids.tool

import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** The bot's `tactical computer` view: true state (not light-delayed) of the player and every asteroid. */
class BotView(capacity: Int = 256) {
    var px = 0f
    var py = 0f
    var vx = 0f
    var vy = 0f

    /** Hull angle (rad, world). */
    var hullAngle = 0f
    var c = 1f
    var asteroidCount = 0
    var ax = FloatArray(capacity)
    var ay = FloatArray(capacity)

    val speed: Float get() = hypot(vx, vy)
    val beta: Float get() = speed / c

    fun clearAsteroids() {
        asteroidCount = 0
    }

    fun addAsteroid(x: Float, y: Float) {
        if (asteroidCount == ax.size) {
            ax = ax.copyOf(ax.size * 2)
            ay = ay.copyOf(ay.size * 2)
        }
        ax[asteroidCount] = x
        ay[asteroidCount] = y
        asteroidCount++
    }

    /** Index of the asteroid nearest to the ship, or -1 when there is none. */
    fun nearest(): Int {
        var best = -1
        var bestD2 = Float.MAX_VALUE
        for (i in 0 until asteroidCount) {
            val dx = ax[i] - px
            val dy = ay[i] - py
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                best = i
            }
        }
        return best
    }

    fun distanceTo(i: Int): Float = hypot(ax[i] - px, ay[i] - py)

    fun bearingTo(i: Int): Float = atan2(ay[i] - py, ax[i] - px)
}

/** What a bot asks of the ship, in the player's own terms: thrust axis, a hull aim direction and the fire button. */
class BotCommand {
    /** -1 (reverse thrusters) .. 1 (main engine). */
    var thrust = 0f

    /** World direction (rad) the hull should face; null keeps the previous aim. */
    var aimRad: Float? = null
    var fire = false

    fun reset() {
        thrust = 0f
        aimRad = null
        fire = false
    }
}

/** A scripted pilot. [decide] is pure: it reads [BotView] and fills [BotCommand]. */
interface BalanceBot {
    val name: String
    fun decide(view: BotView, out: BotCommand)
}

/** Tunables of the bots, in units that follow from the base constants where they can. */
object BotTuning {
    /** Cautious pilot keeps β at or below this. */
    const val CAUTIOUS_BETA_MAX = 0.5f

    /** Expert pilot: the cautious logic with a looser speed cap, to bracket human skill from above. */
    const val EXPERT_BETA_MAX = 0.8f

    /** Nearest asteroid closer than this (u, centre to centre) is a threat the cautious pilot steers away from. */
    const val THREAT_RADIUS = 15f

    /** Cautious pilot fires when the nearest asteroid is inside this half-angle (rad) of the hull: a 30 degree cone. */
    val FIRE_CONE_HALF_RAD: Float = (15.0 * PI / 180.0).toFloat()

    /** Cautious pilot closes in on a target until it is this near (u), then lets it come. */
    const val HUNT_STANDOFF = 22f

    /** Fire range (u): the auto-aim range, so both firing paths agree. */
    const val FIRE_RANGE = com.github.pepek42.asteroids.input.AUTO_AIM_RANGE

    /** Hull must be within this of the wanted heading before the main engine burns. */
    val ALIGNED_RAD: Float = (30.0 * PI / 180.0).toFloat()

    /** Below this β the cautious pilot does not bother braking. */
    const val BRAKE_DEADBAND_BETA = 0.02f
}

/**
 * Keeps β at or below [BotTuning.CAUTIOUS_BETA_MAX], steers away from the nearest threat inside
 * [BotTuning.THREAT_RADIUS], otherwise hunts the nearest asteroid; fires at it inside a 30 degree cone.
 *
 * Braking uses the braking boost: the hull turns retrograde and the main engine burns against the motion (3x at k = 2).
 * While the hull still points roughly along the velocity it uses the reverse thrusters instead.
 */
class CautiousBot(
    private val betaMax: Float = BotTuning.CAUTIOUS_BETA_MAX,
    override val name: String = "cautious",
) : BalanceBot {

    override fun decide(view: BotView, out: BotCommand) {
        out.reset()
        val n = view.nearest()
        val beta = view.beta
        if (n < 0) {
            // Nothing to shoot: just stop.
            brake(view, out)
            return
        }
        val dist = view.distanceTo(n)
        val bearing = view.bearingTo(n)
        out.fire = dist <= BotTuning.FIRE_RANGE &&
            abs(shortestAngleDiffRad(view.hullAngle, bearing)) <= BotTuning.FIRE_CONE_HALF_RAD
        when {
            dist < BotTuning.THREAT_RADIUS -> {
                // Steer away: face away from the threat and burn, unless that would break the speed limit.
                val away = bearing + PI.toFloat()
                out.aimRad = away
                val aligned = abs(shortestAngleDiffRad(view.hullAngle, away)) <= BotTuning.ALIGNED_RAD
                if (beta > betaMax) brake(view, out) else out.thrust = if (aligned) 1f else 0f
            }
            beta > betaMax -> brake(view, out)
            else -> {
                out.aimRad = bearing
                val aligned = abs(shortestAngleDiffRad(view.hullAngle, bearing)) <= BotTuning.ALIGNED_RAD
                // Do not accelerate past the limit within one frame of it either: leave some headroom.
                out.thrust = if (dist > BotTuning.HUNT_STANDOFF && aligned && beta < betaMax) 1f else 0f
            }
        }
    }

    /** Burn against the motion. No-op under the dead band. */
    private fun brake(view: BotView, out: BotCommand) {
        if (view.beta < BotTuning.BRAKE_DEADBAND_BETA) return
        val heading = atan2(view.vy, view.vx)
        val retro = heading + PI.toFloat()
        out.aimRad = retro
        out.thrust = when {
            abs(shortestAngleDiffRad(view.hullAngle, retro)) <= BotTuning.ALIGNED_RAD -> 1f
            abs(shortestAngleDiffRad(view.hullAngle, heading)) <= BotTuning.ALIGNED_RAD * 2f -> -1f
            else -> 0f
        }
    }
}

/** Full thrust toward the nearest asteroid, firing all the time. No speed limit. */
class AggressiveBot : BalanceBot {
    override val name = "aggressive"

    override fun decide(view: BotView, out: BotCommand) {
        out.reset()
        out.fire = true
        val n = view.nearest()
        if (n >= 0) out.aimRad = view.bearingTo(n)
        out.thrust = 1f
    }
}

/** Does nothing: no thrust, no fire, no aim. */
class IdleBot : BalanceBot {
    override val name = "idle"

    override fun decide(view: BotView, out: BotCommand) {
        out.reset()
    }
}

fun botByName(name: String): BalanceBot = when (name.lowercase()) {
    "cautious" -> CautiousBot()
    "expert" -> CautiousBot(BotTuning.EXPERT_BETA_MAX, "expert")
    "aggressive" -> AggressiveBot()
    "idle" -> IdleBot()
    else -> error("Unknown bot '$name' (cautious, expert, aggressive, idle)")
}
