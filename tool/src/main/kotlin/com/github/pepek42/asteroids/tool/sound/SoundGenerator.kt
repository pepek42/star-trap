package com.github.pepek42.asteroids.tool.sound

import java.io.File

/** Writes every recipe to assets/sounds/<name>.wav (ST-134). Run via `./gradlew tool:generateSounds`. */
fun main() {
    val dir = File("assets/sounds").apply { mkdirs() }
    for (e in SoundRecipes.all.entries) {
        val name = e.key
        val bytes = Dsp.toWav(e.value())
        File(dir, "$name.wav").writeBytes(bytes)
        println("$name.wav: ${bytes.size} bytes")
    }
}
