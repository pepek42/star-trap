package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ObjectMap
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.component.gravitySourceCmp
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.component.transformCmp

/** Radius of the black shadow as a multiple of `r_s`: the photon capture cross-section `3 sqrt(3) / 2 r_s`. */
const val BH_SHADOW_PER_RS = 2.598f

/** Photon ring radius as a multiple of `r_s`: the photon sphere. */
const val BH_PHOTON_RING_PER_RS = 1.5f

/** ISCO danger ring radius as a multiple of `r_s`: the innermost stable circular orbit. */
const val BH_ISCO_PER_RS = 3f

/** Dashes in the ISCO ring. */
const val BH_ISCO_DASHES = 32

/** Default of the "Einstein ring" assist: a faint dashed ring at the lens's Einstein radius (ST-151). */
const val EINSTEIN_RING_ENABLED = true

/** Dashes in the Einstein ring (an odd-looking count keeps it visually apart from the ISCO ring). */
const val BH_EINSTEIN_DASHES = 48

/** Radius of the Einstein ring assist for a hole of Schwarzschild radius [rs]: `R_E = LENS_EINSTEIN_RADIUS_RS * rs`, as the lens pass uses. */
fun einsteinRingRadius(rs: Float): Float = LENS_EINSTEIN_RADIUS_RS * rs

/** Points of a circle drawn through the pilot-view boost. */
const val BH_RING_POINTS = 64

private const val PHOTON_RING_ALPHA = 0.6f
private const val ISCO_ALPHA = 0.4f
/** Lowered from 0.9 in ST-175: over the dimmer disk the ring would dominate. */
private const val EINSTEIN_RING_ALPHA = 0.6f
/** Spacing of the three lines of the Einstein ring, as a fraction of `R_E`. */
private const val EINSTEIN_RING_STEP_PER_RE = 0.012f
private const val EINSTEIN_RING_R = 0.5f
private const val EINSTEIN_RING_G = 0.8f
private const val EINSTEIN_RING_B = 1f

/** True when a gravity source of [kind] gets a shadow, photon ring and ISCO ring: black holes only, planets are drawn by their sprite. */
fun drawsHoleRings(kind: GravitySourceKind): Boolean = kind == GravitySourceKind.BLACK_HOLE

/**
 * Black hole shadow, photon ring and dashed ISCO ring (ST-085, research E16) with a ShapeRenderer, plus the particle accretion
 * disk (ST-087, research E17). Call outside a SpriteBatch begin/end, after the backdrop and before the sprites. The world view
 * draws at the interpolated transform. In pilot view the hole moves, so it is drawn at its retarded position, every circle point
 * boosted like a sprite corner, and every disk particle gets its own light delay, boost and Doppler factor (ST-089).
 */
class BlackHoleRenderer : Disposable {
    private val shapes = ShapeRenderer()
    private val centre = Vector2()
    private val pt = Vector2()
    private val prevPt = Vector2()
    private val bodyPos = Vector2()
    private val holePos = Vector2()
    private val diskShader = AccretionDiskShader()
    private val disks = ObjectMap<Entity, AccretionDisk>()
    private var diskSeed = 0L
    private val disturbers = DiskDisturbers()

    /**
     * Draws every entity of [holes] (each with a gravity source and transform). [pilot] is non-null in pilot view. [einsteinRing] adds the dashed Einstein ring assist. [worldTime] is
     * `GameClock.worldTime` (turns the disk) and [c] the light speed (gives the disk `GM`). [bodies] (entities with a body and a
     * transform, e.g. ships and asteroids) push disk particles aside where they overlap the disk (ST-175); the push uses their
     * interpolated positions, in pilot view the retarded ones (where the displayed sprite and the displayed disk are, visual only).
     */
    fun render(
        camera: Camera, holes: ImmutableArray<Entity>, pilot: PilotViewProjector?, worldTime: Float, c: Float,
        einsteinRing: Boolean = false, bodies: ImmutableArray<Entity>? = null,
    ) {
        if (holes.size() == 0) {
            releaseGoneDisks(holes)
            return
        }
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (i in 0 until holes.size()) {
            val e = holes[i]
            if (!drawsHoleRings(e.gravitySourceCmp.kind) || !holeCentre(e, pilot)) continue
            val rs = e.gravitySourceCmp.rs
            shapes.setColor(0f, 0f, 0f, 1f)
            if (pilot == null) {
                shapes.circle(centre.x, centre.y, BH_SHADOW_PER_RS * rs, BH_RING_POINTS)
            } else {
                fan(BH_SHADOW_PER_RS * rs, pilot)
            }
        }
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        for (i in 0 until holes.size()) {
            val e = holes[i]
            if (!drawsHoleRings(e.gravitySourceCmp.kind) || !holeCentre(e, pilot)) continue
            val rs = e.gravitySourceCmp.rs
            shapes.setColor(1f, 1f, 1f, PHOTON_RING_ALPHA)
            ring(BH_PHOTON_RING_PER_RS * rs, pilot, dashes = 0)
            shapes.setColor(1f, 0.55f, 0.1f, ISCO_ALPHA)
            ring(BH_ISCO_PER_RS * rs, pilot, dashes = BH_ISCO_DASHES)
        }
        shapes.end()
        if (diskShader.ready) renderDisks(camera, holes, bodies, pilot, worldTime, c)
        if (einsteinRing) renderEinsteinRings(holes, pilot)
        releaseGoneDisks(holes)
    }

    /** The Einstein ring assist goes on top of the accretion disk, or the disk particles would hide it. */
    private fun renderEinsteinRings(holes: ImmutableArray<Entity>, pilot: PilotViewProjector?) {
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.setColor(EINSTEIN_RING_R, EINSTEIN_RING_G, EINSTEIN_RING_B, EINSTEIN_RING_ALPHA)
        for (i in 0 until holes.size()) {
            val e = holes[i]
            if (!drawsHoleRings(e.gravitySourceCmp.kind) || !holeCentre(e, pilot)) continue
            val rE = einsteinRingRadius(e.gravitySourceCmp.rs)
            // Three concentric lines: GL lines are 1 px (WebGL has no wide lines), too thin over the disk.
            for (k in -1..1) ring(rE + k * EINSTEIN_RING_STEP_PER_RE * rE, pilot, dashes = BH_EINSTEIN_DASHES)
        }
        shapes.end()
    }

    /** Feeds [disturbers] with every ship or asteroid overlapping a hole's disk, the player first so it gets the live slot. */
    private fun collectDisturbers(holes: ImmutableArray<Entity>, bodies: ImmutableArray<Entity>?, pilot: PilotViewProjector?, worldTime: Float) {
        disturbers.begin(worldTime)
        if (bodies != null) {
            for (pass in 0..1) {
                for (b in 0 until bodies.size()) {
                    val body = bodies[b]
                    if (playerMapper.has(body) != (pass == 0) || !diskAffects(body.baseInfoCmp.entityType)) continue
                    val bc = body.bodyCmp
                    if (bc.boundingRadius < 0f) bc.boundingRadius = computeBoundingRadius(bc.body)
                    if (!displayedPosition(body, pilot, bodyPos)) continue
                    for (i in 0 until holes.size()) {
                        val hole = holes[i]
                        val source = hole.gravitySourceCmp
                        if (source.kind != GravitySourceKind.BLACK_HOLE) continue
                        if (!displayedPosition(hole, pilot, holePos)) continue
                        val reach = diskScatterReach(bc.boundingRadius, source.rs)
                        if (!diskOverlaps(bodyPos.x - holePos.x, bodyPos.y - holePos.y, source.rs, reach)) continue
                        disturbers.touch(bodyPos.x, bodyPos.y, reach)
                        break
                    }
                }
            }
        }
        disturbers.end()
    }

    /** Writes the arena position [e] is displayed at: retarded in pilot view (false when its light has not arrived), else interpolated. */
    private fun displayedPosition(e: Entity, pilot: PilotViewProjector?, out: Vector2): Boolean {
        if (pilot != null) return pilot.retardedCentre(e, out)
        out.set(e.transformCmp.interpolatedPosition)
        return true
    }

    private fun renderDisks(
        camera: Camera, holes: ImmutableArray<Entity>, bodies: ImmutableArray<Entity>?, pilot: PilotViewProjector?,
        worldTime: Float, c: Float,
    ) {
        collectDisturbers(holes, bodies, pilot, worldTime)
        diskShader.begin(camera, worldTime, disturbers.uniforms)
        for (i in 0 until holes.size()) {
            val e = holes[i]
            val source = e.gravitySourceCmp
            if (source.kind != GravitySourceKind.BLACK_HOLE) continue
            val disk = diskFor(e, source.rs)
            if (pilot == null) {
                centre.set(e.transformCmp.interpolatedPosition)
                diskShader.draw(disk, centre.x, centre.y, source.effectiveGm(c))
            } else {
                if (!pilot.retardedCentre(e, centre)) continue
                diskShader.drawPilot(
                    disk, centre.x, centre.y, source.effectiveGm(c),
                    time = pilot.now - pilot.centreLag, tau = pilot.centreLag,
                    holeVx = pilot.centreVx, holeVy = pilot.centreVy,
                    ox = pilot.ox, oy = pilot.oy, betaX = pilot.betaX, betaY = pilot.betaY, c = c,
                    delay = pilot.delayEnabled && pilot.centreLag > 0f, aberration = pilot.aberrationEnabled,
                )
            }
        }
        diskShader.end()
    }

    /** The disk mesh of [e]; a merge changed `rs`, so the mesh is rebuilt with the same seed (same layout, new scale). */
    private fun diskFor(e: Entity, rs: Float): AccretionDisk {
        val old = disks.get(e)
        if (old != null && old.rs == rs) return old
        val seed = old?.seed ?: diskSeed++
        old?.dispose()
        return AccretionDisk(seed, rs).also { disks.put(e, it) }
    }

    /** Frees the meshes of holes that are no longer in [holes]; checked every frame without allocating. */
    private fun releaseGoneDisks(holes: ImmutableArray<Entity>) {
        val it = disks.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!holes.contains(entry.key, true)) {
                entry.value.dispose()
                it.remove()
            }
        }
    }

    /** Fills [centre] with the hole's arena centre as drawn (before the boost); false when its light has not arrived. */
    private fun holeCentre(e: Entity, pilot: PilotViewProjector?): Boolean {
        if (pilot != null) return pilot.retardedCentre(e, centre)
        centre.set(e.transformCmp.interpolatedPosition)
        return true
    }

    private fun point(angle: Float, radius: Float, pilot: PilotViewProjector?, out: Vector2) {
        val x = centre.x + radius * MathUtils.cos(angle)
        val y = centre.y + radius * MathUtils.sin(angle)
        if (pilot != null) pilot.apparentPoint(x, y, out) else out.set(x, y)
    }

    private fun fan(radius: Float, pilot: PilotViewProjector) {
        pilot.apparentPoint(centre.x, centre.y, prevPt)
        val cx = prevPt.x
        val cy = prevPt.y
        point(0f, radius, pilot, prevPt)
        for (k in 1..BH_RING_POINTS) {
            point(MathUtils.PI2 * k / BH_RING_POINTS, radius, pilot, pt)
            shapes.triangle(cx, cy, prevPt.x, prevPt.y, pt.x, pt.y)
            prevPt.set(pt)
        }
    }

    /** A polyline circle; [dashes] > 0 draws every other of `2 * dashes` arcs instead of a closed line. */
    private fun ring(radius: Float, pilot: PilotViewProjector?, dashes: Int) {
        val segments = if (dashes > 0) dashes * 2 else BH_RING_POINTS
        val sub = if (dashes > 0) DASH_SUBSEGMENTS else 1
        point(0f, radius, pilot, prevPt)
        for (k in 1..segments * sub) {
            point(MathUtils.PI2 * k / (segments * sub), radius, pilot, pt)
            val segment = (k - 1) / sub
            if (dashes == 0 || segment % 2 == 0) shapes.line(prevPt.x, prevPt.y, pt.x, pt.y)
            prevPt.set(pt)
        }
    }

    override fun dispose() {
        shapes.dispose()
        for (d in disks.values()) d.dispose()
        disks.clear()
        diskShader.dispose()
    }

    private companion object {
        /** Line pieces per dash arc so a dash follows the circle. */
        const val DASH_SUBSEGMENTS = 2
    }
}
