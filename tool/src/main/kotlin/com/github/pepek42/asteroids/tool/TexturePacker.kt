package com.github.pepek42.asteroids.tool

import com.badlogic.gdx.tools.texturepacker.TexturePacker
import java.io.File

private const val RAW_DIR = "raw_assets"
private const val NORMALS_DIR = "tool/build/normals"
private const val PACK_INPUT_DIR = "tool/build/packinput"

/**
 * Packs `raw_assets` into the game atlas. Packer settings are read from `raw_assets/pack.json`.
 * Lit sprites also get a generated `<name>_n` normal map (ST-096, see NormalMaps.kt).
 */
fun main() {
    val raw = File(RAW_DIR)
    val normals = File(NORMALS_DIR).apply { deleteRecursively() }
    val input = File(PACK_INPUT_DIR).apply { deleteRecursively() }
    raw.copyRecursively(input, overwrite = true)
    raw.walkTopDown().filter { it.isFile }.forEach { f ->
        val rel = f.relativeTo(raw).invariantSeparatorsPath
        if (hasNormalMap(rel)) {
            val dst = File(normals, rel.removeSuffix(".png") + "_n.png")
            writeNormalPng(f, dst)
            dst.copyTo(File(input, dst.relativeTo(normals).path), overwrite = true)
        }
    }
    TexturePacker.process(PACK_INPUT_DIR, "assets/textures", "textures")
}
