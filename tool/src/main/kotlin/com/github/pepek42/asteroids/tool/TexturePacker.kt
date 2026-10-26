package com.github.pepek42.asteroids.tool

import com.badlogic.gdx.tools.texturepacker.TexturePacker

/** Packs `raw_assets` into the game atlas. Packer settings are read from `raw_assets/pack.json`. */
fun main() {
    TexturePacker.process("raw_assets", "assets/textures", "textures")
}
