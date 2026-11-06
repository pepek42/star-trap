# Sound generator (ST-134)

All SFX are synthesized by code and committed as `assets/sounds/*.wav` (like the texture atlas).
Format: 16-bit mono PCM, 22 050 Hz, peak -1 dBFS, 5 ms fade in/out (loops have none), each file well under 200 KB.

Regenerate: `./gradlew tool:generateSounds` (deterministic: every recipe has a fixed `java.util.Random` seed, so re-running gives identical bytes).
Test: `./gradlew tool:test` (determinism, WAV header, size, peak, loop seam).

Code: `tool/src/main/kotlin/com/github/pepek42/asteroids/tool/sound/`
- `Dsp.kt`: oscillators (sine/saw/square, sweepable), white/brown noise, ADSR and exponential envelopes, biquad low/band-pass with sweepable cutoff, one-pole, tanh saturation, mix/place, `loopable` cross-fade, `normalize`, `toWav`.
- `SoundRecipes.kt`: one function per sound, named after the file; the `all` map lists them. To add a sound, write a recipe and add it to `all`.
- `SoundGenerator.kt`: the `main` that writes the files.

## Layers
- Hull layer (heard through the hull, dull, low, noisy): `engine_loop` (brown-noise rumble + 46/92 Hz hum, 2 s loop with 0.25 s cross-fade), `laser_fire` (low thump + quiet falling whine), `hull_hit` (noise burst through inharmonic band-pass resonances 310..2240 Hz), `hull_scrape` (wobbling band-passed noise, saturated), `alarm_proximity` (880/1175 Hz beeps), `alarm_hull_damage` (low square, 330/247 Hz alternating).
- Computer layer (`synth_` prefix, clean synthesized): `synth_asteroid_break_small/medium/large` (low-passed noise burst + pitch-dropping boom; bigger = longer, lower, slower decay), `synth_ui_lock` (two sine notes 1320 -> 1980 Hz).

## Tuning
Edit the constants in the recipe (frequencies, decay `tau`, filter cutoffs, mix gains) and rerun. Seeds are the `Random(100x)` values; change a seed for a different noise texture.
