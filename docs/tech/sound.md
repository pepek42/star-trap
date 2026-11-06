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

## Runtime (ST-135)
- `audio/SoundBank` (DI singleton): `Game.create` queues every `SoundId` WAV on the `AssetManager` (`SoundBank.queueLoading`) and wraps the loaded libGDX `Sound`s. Only presentation code uses it; the headless balance runner never loads audio.
- Settings (`settings/SoundSettings`, menu sliders `master_volume`, `hull_sound_volume`, persisted): hull gain = master x hull.
- `system/HullSoundSystem` (presentation, right after `RenderSystem`): the engine loop (one `Sound.loop` instance, started when the smoothed volume passes `ENGINE_STOP_VOLUME`, stopped below it) follows the player's `MoveComponent.thrusters` through `audio/HullSoundMix` (`engineVolume`, `enginePitch`, smoothed by `ENGINE_SMOOTHING_TIME`). One-shots come from `audio/HullSoundQueue`, a fixed-size queue: `WeaponSystem` pushes `LASER_FIRE` for the player's shots, `ContactSystem.beginContact` pushes `HULL_HIT` for non-projectile contacts with the player (not slow rubs, `isScrape`), volume from `impactVolume(relativeSpeed, c)`. Sustained scrape (ST-138): `ContactSystem` tracks bodies touching the player in the queue (`beginTouch`/`endTouch`, fixed array); `HullSoundSystem` keeps one `hull_scrape` loop while any is touching and the tangential speed (`tangentialSpeed`, normal approximated by the centre-to-centre line) is above `SCRAPE_MIN_C_FRACTION` x c; volume is `scrapeVolume` (0 below, monotonic, capped at 1, full at `SCRAPE_FULL_C_FRACTION` x c). It stops when the last contact ends. `Sound` handles are cached in the system's fields. Both take the queue as an optional parameter (null headless).
- Pitch is never Doppler- or time-warp-shifted. `PlayScreen.hide()`/`dispose()` call `HullSoundSystem.silence()`; `pause()`/`resume()` set `HullSoundSystem.paused` (silent, queue dropped while true). There is no in-game pause overlay yet; `pause()` is focus loss / app pause.
- TODO: alarms (`alarm_proximity`, `alarm_hull_damage`): no proximity or low-hull state exists yet.

## Computer layer (ST-136, assist)
The ship's computer turns what it sees into sound. No sound travels through vacuum (physics-lies row 37). Off = realistic silence.
- Settings: `SoundSettings.computerSounds` (toggle `computer_sounds`, default on, `COMPUTER_SOUNDS_DEFAULT`) and `computerSoundVolume` (slider `computer_sound_volume`, default 0.8); gain = master x computer volume. Both are in `menuSettings`, so they persist and reset with the others.
- Events: `DeathSystem` pushes asteroid break-ups (`ComputerSoundKind` by `AsteroidSize`: SMALL, LARGE) into `audio/ComputerSoundQueue` with arena position, velocity and world time. Fixed size (32, extra events dropped); nothing is queued while the toggle is off. Null in the headless runner. `ASTEROID_BREAK_MEDIUM` and `SYNTH_UI_LOCK` are loaded but not used yet (no medium size; UI lock is for later target-lock events).
- Light-arrival rule: `system/ComputerSoundSystem` (presentation, after `HullSoundSystem`) plays an event when `lightHasArrived`: `dist(pilot now, event) <= c (now - t_event)` in world time, never earlier. Pitch = `computerPitch(dopplerFactor(observer betaRaw, source beta, n))` clamped to `COMPUTER_PITCH_MIN..MAX` (0.5..2); 1 with relativity off. Volume = `computerVolume(d, c)` = `1/(1 + d/d0)`, `d0 = c x COMPUTER_FALLOFF_LIGHT_SECONDS` (1 s of light). Pan = `computerPan` (`dx/dist`, screen x).
- Voice cap: `VoiceLimiter`, `COMPUTER_MAX_VOICES` = 8 at once (a voice is held `COMPUTER_VOICE_SECONDS / pitch` of real time); extra events are dropped. `PlayScreen` pauses/silences it like the hull system and clears the queue on level start. Pending events are dropped on pause/focus loss, so light that arrives while unfocused is never played late. Pitch uses the source velocity at emission (the event time), which is right for retarded emission.

