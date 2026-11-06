package com.github.pepek42.asteroids.tool

import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.math.min
import kotlin.math.sqrt

private const val CHAMFER_ORTHO = 3
private const val CHAMFER_DIAG = 4
private const val ALPHA_INSIDE = 0.5f
private const val FAR = 1_000_000

/**
 * Bevelled "pillow" normal map from a sprite's alpha (ST-096). [alpha] is `w*h` floats in 0..1, row 0 on top.
 * Returns `w*h*4` floats (rgba): rgb = normal * 0.5 + 0.5, a = alpha. The image border counts as outside.
 */
fun normalMapFromAlpha(w: Int, h: Int, alpha: FloatArray, bevelPx: Int = 3, strength: Float = 4f): FloatArray {
    val dist = chamferDistance(w, h, alpha)
    val raw = FloatArray(w * h) { min(dist[it].toFloat() / CHAMFER_ORTHO, bevelPx.toFloat()) / bevelPx }
    val height = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var sum = 0f
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) {
                sum += raw[yy * w + xx]
                n++
            }
        }
        height[y * w + x] = sum / n
    }
    fun hAt(x: Int, y: Int) = height[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
    val out = FloatArray(w * h * 4)
    for (y in 0 until h) for (x in 0 until w) {
        val i = (y * w + x) * 4
        val a = alpha[y * w + x]
        if (a > 0f) {
            val dhdx = (hAt(x + 1, y) - hAt(x - 1, y)) * 0.5f
            val dhdy = (hAt(x, y + 1) - hAt(x, y - 1)) * 0.5f
            val nx = -dhdx * strength
            val ny = dhdy * strength
            val len = sqrt(nx * nx + ny * ny + 1f)
            out[i] = nx / len * 0.5f + 0.5f
            out[i + 1] = ny / len * 0.5f + 0.5f
            out[i + 2] = 1f / len * 0.5f + 0.5f
        }
        out[i + 3] = a
    }
    return out
}

/** Two-pass 3-4 chamfer distance to the nearest outside texel (0 outside; beyond the image counts as far). */
private fun chamferDistance(w: Int, h: Int, alpha: FloatArray): IntArray {
    val d = IntArray(w * h) { if (alpha[it] > ALPHA_INSIDE) FAR else 0 }
    fun at(x: Int, y: Int) = if (x in 0 until w && y in 0 until h) d[y * w + x] else FAR
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        if (d[i] == 0) continue
        d[i] = minOf(
            d[i],
            at(x - 1, y) + CHAMFER_ORTHO, at(x, y - 1) + CHAMFER_ORTHO,
            at(x - 1, y - 1) + CHAMFER_DIAG, at(x + 1, y - 1) + CHAMFER_DIAG,
        )
    }
    for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
        val i = y * w + x
        if (d[i] == 0) continue
        d[i] = minOf(
            d[i],
            at(x + 1, y) + CHAMFER_ORTHO, at(x, y + 1) + CHAMFER_ORTHO,
            at(x + 1, y + 1) + CHAMFER_DIAG, at(x - 1, y + 1) + CHAMFER_DIAG,
        )
    }
    return d
}

/** Sprites that get a normal map: asteroids, debris and ships. Lasers, thrusters, explosions, backgrounds do not. */
fun hasNormalMap(relativePath: String): Boolean {
    val name = relativePath.substringAfterLast('/')
    return name.endsWith(".png") &&
        (name.startsWith("asteroid_") || name.startsWith("debris") || relativePath.startsWith("spaceship/"))
}

fun writeNormalPng(src: File, dst: File) {
    val img = ImageIO.read(src)
    val w = img.width
    val h = img.height
    val alpha = FloatArray(w * h) { ((img.getRGB(it % w, it / w) ushr 24) and 0xFF) / 255f }
    val rgba = normalMapFromAlpha(w, h, alpha)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    fun c(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    for (y in 0 until h) for (x in 0 until w) {
        val i = (y * w + x) * 4
        out.setRGB(x, y, (c(rgba[i + 3]) shl 24) or (c(rgba[i]) shl 16) or (c(rgba[i + 1]) shl 8) or c(rgba[i + 2]))
    }
    dst.parentFile.mkdirs()
    ImageIO.write(out, "png", dst)
}
