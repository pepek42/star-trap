package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.BH_RS
import kotlin.math.sqrt

/** Disturbers the disk shader takes (`u_disturb0..3`): slot 0 follows the body in the disk, the others are its fading trail. */
const val DISK_DISTURBERS = 4

/** Seconds a pushed-aside particle takes to drift back to its orbit (ST-175: "about 1-2 s"). */
const val DISK_SCATTER_RECOVERY = 1.5f

/** Seconds between trail slots: the trail slots together cover the recovery time (`1.5 / 4` = 0.375 s). */
const val DISK_SCATTER_TRAIL_INTERVAL = DISK_SCATTER_RECOVERY / DISK_DISTURBERS

/** The scatter circle of a body reaches this many particle half-sizes past its bounding radius. */
const val DISK_SCATTER_MARGIN_PARTICLES = 3f

/** Floats per slot in [DiskDisturbers.uniforms]: x, y, reach, strength. */
const val DISK_DISTURBER_FLOATS = 4

/** Radius of the scatter circle for a body of bounding radius [bodyRadius] in a disk of a hole with Schwarzschild radius [rs]. */
fun diskScatterReach(bodyRadius: Float, rs: Float): Float = bodyRadius + DISK_SCATTER_MARGIN_PARTICLES * DISK_PARTICLE_SIZE * rs / BH_RS

/** Strength of a disturbance [age] seconds old: 1 at once, falling smoothly to 0 at [DISK_SCATTER_RECOVERY] (`(1 - age / T)^2`). */
fun diskScatterStrength(age: Float): Float {
    if (age >= DISK_SCATTER_RECOVERY) return 0f
    val f = 1f - age.coerceAtLeast(0f) / DISK_SCATTER_RECOVERY
    return f * f
}

/**
 * Pushes the particle at [pos] radially out of the circle of radius [reach] around ([qx], [qy]) by `(reach - d) * strength`
 * (zero outside the circle), in place. The reference `accretion_disk.vert` mirrors by hand (`scatter`).
 */
fun diskScatterPush(pos: Vector2, qx: Float, qy: Float, reach: Float, strength: Float) {
    if (strength <= 0f) return
    val dx = pos.x - qx
    val dy = pos.y - qy
    val d = sqrt(dx * dx + dy * dy)
    if (d >= reach) return
    val push = (reach - d) * strength
    if (d > SCATTER_EPS) pos.add(dx / d * push, dy / d * push) else pos.add(push, 0f)
}

/** Applies every slot of [uniforms] (x, y, reach, strength per slot, see [DiskDisturbers.uniforms]) to [pos], like the vertex shader. */
fun diskScatterAll(pos: Vector2, uniforms: FloatArray) {
    for (i in 0 until DISK_DISTURBERS) {
        val o = i * DISK_DISTURBER_FLOATS
        diskScatterPush(pos, uniforms[o], uniforms[o + 1], uniforms[o + 2], uniforms[o + 3])
    }
}

/** True when a body of [reach] at ([dx], [dy]) from the hole overlaps the annulus (so it would push particles). */
fun diskOverlaps(dx: Float, dy: Float, rs: Float, reach: Float): Boolean {
    val d = sqrt(dx * dx + dy * dy)
    return d + reach >= DISK_INNER_PER_RS * rs && d - reach <= DISK_OUTER_PER_RS * rs
}

private const val SCATTER_EPS = 1e-4f

/**
 * The disturbances that push disk particles aside (ST-175), preallocated and GL-free. Per frame: [begin], [touch] for every body
 * overlapping a disk (the first one wins slot 0, so the caller feeds the player first), then [end], which fills [uniforms].
 *
 * Slot 0 follows the first touched body (its time stamp is refreshed while touched, so it fades once the body leaves). While a
 * body is touched, every [DISK_SCATTER_TRAIL_INTERVAL] seconds the touching bodies also drop a copy of their circle into one of
 * the slots 1 to 3 (round-robin), which fade over [DISK_SCATTER_RECOVERY]: the particles close up behind a moving ship.
 * Times are world time. Visual only: it never touches the physics.
 */
class DiskDisturbers {
    /** `x, y, reach, strength` per slot, [DISK_DISTURBERS] slots; strength 0 = empty. */
    val uniforms = FloatArray(DISK_DISTURBERS * DISK_DISTURBER_FLOATS)

    private val x = FloatArray(DISK_DISTURBERS)
    private val y = FloatArray(DISK_DISTURBERS)
    private val reach = FloatArray(DISK_DISTURBERS)
    private val stamp = FloatArray(DISK_DISTURBERS) { Float.NEGATIVE_INFINITY }
    private var nextTrail = 1
    private var lastEmit = Float.NEGATIVE_INFINITY
    private var now = 0f
    private var emitDue = false
    private var headTaken = false
    private var emitted = false

    /** Starts a frame at world time [time]. A time that went backwards (new level) clears every slot. */
    fun begin(time: Float) {
        if (time < lastEmit || time < stampMax()) clear()
        now = time
        emitDue = time - lastEmit >= DISK_SCATTER_TRAIL_INTERVAL
        headTaken = false
        emitted = false
    }

    /** A body with scatter circle of radius [r] at ([px], [py]) overlaps a disk this frame. */
    fun touch(px: Float, py: Float, r: Float) {
        if (!headTaken) {
            headTaken = true
            write(0, px, py, r)
        }
        if (emitDue) {
            write(nextTrail, px, py, r)
            nextTrail = if (nextTrail + 1 >= DISK_DISTURBERS) 1 else nextTrail + 1
            emitted = true
        }
    }

    /** Ends the frame: fills [uniforms] with the strengths at the current time. */
    fun end() {
        if (emitted) lastEmit = now
        for (i in 0 until DISK_DISTURBERS) {
            val o = i * DISK_DISTURBER_FLOATS
            uniforms[o] = x[i]
            uniforms[o + 1] = y[i]
            uniforms[o + 2] = reach[i]
            uniforms[o + 3] = diskScatterStrength(now - stamp[i])
        }
    }

    private fun write(slot: Int, px: Float, py: Float, r: Float) {
        x[slot] = px
        y[slot] = py
        reach[slot] = r
        stamp[slot] = now
    }

    private fun stampMax(): Float {
        var m = Float.NEGATIVE_INFINITY
        for (i in 0 until DISK_DISTURBERS) if (stamp[i] > m) m = stamp[i]
        return m
    }

    private fun clear() {
        for (i in 0 until DISK_DISTURBERS) stamp[i] = Float.NEGATIVE_INFINITY
        lastEmit = Float.NEGATIVE_INFINITY
        nextTrail = 1
    }
}
