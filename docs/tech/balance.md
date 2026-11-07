# Balance pass (ST-101)

Owner (2026-11-02, ST-020): "Pick some c value for now. Run some sim games and try to balance whole physics so that player still will have some chance to play." This page records the evidence and the constants that came out of it. Re-run the sweep after any change to the physics, the weapons or the asteroid constants.

## Result in one paragraph

c = 20 and the default engine (`ENGINE_ACCEL_PER_C` ≈ 0.41, a0 ≈ 8.26 u/s²) stay. They already sit in the one cell of the sweep that meets every target once the asteroid pressure is fixed. The old pressure (51 large asteroids on level 5, 8/60 c spawn speed) made level 5 unwinnable for a careful pilot at **every** c and a0 in the sweep (0 % clears), so the fix is in the asteroid constants and the player's share of ram damage, not in c. Three constants changed:

| Constant | File | Old | New | Why |
|---|---|---|---|---|
| `ASTEROID_COUNT_PER_LEVEL_SQ` | `environment/asteroid.kt` | 2 | 1 | Large asteroids per level 1 to 5: 3, 9, 19, 33, 51 → 2, 5, 10, 17, 26. Each large rock yields 5 shots of work (3 for the rock, 1 per fragment), so level 5 was 255 shots, i.e. 85 s of non-stop perfect fire at 3 shots/s, over the 120 s budget with real hit rates |
| `ASTEROID_SPAWN_BETA_MAX` | `environment/asteroid.kt` | 8/60 | 6/60 | Slower rocks (±2 u/s per axis at c = 20) are easier to hit and chase; costs the aggressive pilot nothing, because its danger comes from its own speed |
| `PLAYER_RAM_DAMAGE_FACTOR` | `physics/PhysicsConstants.kt` | 0.4 | 0.5 | With fewer rocks a flat-out pilot died too rarely (about 18 % on levels 1 to 3, target 20 to 60 %) |

No owner-decided value changed (c = 20, tick rate, γ caps, `ENGINE_ACCEL_PER_C`, braking boost, weapon damage and fire rate are untouched).

## How to run

```bash
./gradlew tool:balanceSim                                   # the full sweep, about 2 minutes, writes tool/build/balance/{runs.csv,summary.md}
./gradlew tool:balanceSim -PbalanceArgs="--seeds=8 --levels=1,2 --bots=cautious --c=20 --accel=default"
```

Smoke test (tagged `balance`, 6 seeds, c = 20, default a0, levels 1 and 5, loose bounds on the T1 and T2 targets; not part of `test`, takes a minute or two):

```bash
./gradlew tool:balanceSmoke
```

Options: `--c=15,20,30`, `--accel=0.25,default,0.7` (fraction of c, `default` = `ENGINE_ACCEL_PER_C`), `--levels=1,2,3,4,5`, `--bots=cautious,expert,aggressive,idle`, `--seeds=20`, `--seed-base=1`, `--max-seconds=120`, `--scheme=auto|mouse`, `--out=<dir>`. The default c list is `SPEED_OF_LIGHT` × {0.75, 1, 1.5}. Seeds are `seed-base + i`, the same for every bot and setting, so the tables are paired. The full sweep is 2100 runs (20 seeds, no reduction needed). Code: `tool/src/main/kotlin/.../tool/` (`BalanceSim.kt` sweep and CLI, `HeadlessLevel.kt` runner, `BalanceBots.kt` pilots, `BalanceReport.kt` tables), tests in `tool/src/test`.

### What is simulated

- The real gameplay systems in the real order: `ecs/GameplaySystems.kt` `buildGameplaySystems` is shared with `PlayScreen.setupEcs()` (render, HUD and minimap are the `presentation` callback, left empty headless). Real Box2D `World`, seeded `RandomXS128`, a frame is `PHYSICS_UPDATE_INTERVAL` (1/60 s) of pilot time, and the time warp makes the arena run up to 10 steps per frame exactly as in the game.
- The default control scheme (auto-aim, owner 2026-11-03). Bots drive `GameEventManager` like the mouse does: `mouseMoved` to a far point in the wanted direction (hull heading), `thrusters(f)`, left-button down and up. The hull turns with the real PD controller. Under auto-aim the guns fire themselves at targets in the 90° cone (with honest lead from retarded states), so the bot's fire button matters only with `--scheme=mouse`.
- Bots read the true state of asteroids and the ship (they act as the tactical computer, not through light delay). They do not use pilot view.
- Each level is played alone with full health (100). Max 120 pilot seconds, then the run is a timeout.
- Idle never thrusts, so it runs once per c (at the default a0).

### Pilots

- **cautious**: keeps β ≤ 0.5; when faster, turns the hull retrograde and burns against the motion (braking boost 3×, `BRAKE_BOOST_K`), or uses the reverse thrusters while the hull still points along the motion; steers away from the nearest asteroid inside 15 u; otherwise closes on the nearest asteroid until it is 22 u away; fires at the nearest asteroid inside a 30° cone (15° each side) and 40 u.
- **expert**: the cautious logic with β ≤ 0.8 (`BotTuning.EXPERT_BETA_MAX`), a faster, riskier pilot. Together with `--scheme=mouse` (a weaker aimer) it brackets human skill.
- **aggressive**: thrusts at full power toward the nearest asteroid all the time and holds fire. No speed limit.
- **idle**: does nothing (baseline; auto-aim guns still shoot what crosses the hull's cone).

### Metrics

Per run: outcome (cleared, died, timeout), pilot seconds, damage taken, kills (each asteroid destroyed counts, fragments too), mean and p95 γ of the ship (per frame), fraction of frames with the time warp capped (`GameClock.warpCapped`: γ above `timeWarpMax`), fraction of frames with the step cap hit, mean physics steps per frame. Tables aggregate per (bot, c, a0/c, level): rates are totals over total pilot minutes; "t death" is the mean over died runs, "t clear" over cleared runs.

## Targets and the verdict

| # | Target (card) | How it is checked |
|---|---|---|
| T1 | `cautious` clears levels 1 to 3 in ≥ 90 % of runs | each of levels 1, 2, 3 |
| T2 | `cautious` clears level 5 in ≥ 50 % of runs | level 5 |
| T3 | `aggressive` dies in 20 to 60 % of level 1 to 3 runs | pooled over levels 1 to 3 (60 runs). Per level it is 5 / 35 / 50 %: the death rate grows with asteroid density, level 1 is nearly safe on purpose |
| T4 | Time warp capped < 5 % of the time | worst (bot, level) mean in the cell |

### Before the tuning (count 2, speed 8/60, factor 0.4), 20 seeds

| c | a0/c | cautious clear L1 / L2 / L3 | cautious L5 | aggressive died L1-3 pooled | aggressive died L1 / L2 / L3 | worst warp capped | T1 T2 T3 T4 |
|---|---|---|---|---|---|---|---|
| 15 | 0.25 | 100 / 100 / 20 | 0 % | 17 % | 0 / 5 / 45 | 0.0 % | n n n Y |
| 15 | 0.41 | 100 / 100 / 40 | 0 % | 53 % | 15 / 55 / 90 | 0.0 % | n n Y Y |
| 15 | 0.70 | 100 / 100 / 75 | 0 % | 72 % | 20 / 95 / 100 | 0.9 % | n n n Y |
| 20 | 0.25 | 100 / 100 / 85 | 0 % | 8 % | 0 / 5 / 20 | 0.0 % | n n n Y |
| 20 | 0.41 | 100 / 100 / 95 | 0 % | 40 % | 0 / 40 / 80 | 0.0 % | Y n Y Y |
| 20 | 0.70 | 100 / 100 / 100 | 0 % | 82 % | 50 / 95 / 100 | 1.4 % | Y n n Y |
| 30 | 0.25 | 100 / 100 / 100 | 0 % | 7 % | 0 / 0 / 20 | 0.0 % | Y n n Y |
| 30 | 0.41 | 100 / 100 / 100 | 0 % | 32 % | 0 / 15 / 80 | 0.0 % | Y n Y Y |
| 30 | 0.70 | 100 / 100 / 100 | 0 % | 63 % | 20 / 70 / 100 | 0.9 % | Y n n Y |

T2 fails everywhere: cautious clears level 4 in 10 % (c = 20) and level 5 never, even at c = 30, so c is not the lever. The best-guess fixes were asteroid count, spawn speed and the damage scale (the three tunables the card names).

### Tuning steps at c = 20, default a0 (cautious L5 clear / aggressive died L1 / L2 / L3)

| Step | cautious L5 | aggressive died L1-3 | Seeds |
|---|---|---|---|
| Baseline (count 2, 8/60, 0.4) | 0 % | 0 / 40 / 80 (pooled 40 %) | 20 |
| count 1 | 45 % (58 % at 60 seeds) | 0 / 15 / 40 (18 %); 5 / 15 / 48 (23 %) at 60 seeds | 20, 60 |
| count 1, factor 0.6 | (same, cautious takes no damage) | 10 / 30 / 65 (35 %) | 60 |
| count 1, factor 0.5 | 45 % | 0 / 15 / 60 (25 %) | 20 |
| count 1, factor 0.5, speed 11/60 | 33 % | 8 / 23 / 50 (27 %) | 40 |
| count 1, factor 0.5, speed 6/60 (chosen) | 70 % (65 % in the final sweep) | 3 / 30 / 55 (29 %); 5 / 35 / 50 (30 %) in the final sweep | 40, 20 |

Factor 0.5 was picked over 0.4 for margin inside the 20 to 60 % window (0.4 sits at its lower edge); 0.6 would already push level 3 to 65 %.

### After the tuning (count 1, speed 6/60, factor 0.5), 20 seeds

| c | a0/c | cautious clear L1 / L2 / L3 | cautious L5 | aggressive died L1-3 pooled | aggressive died L1 / L2 / L3 | worst warp capped | T1 T2 T3 T4 |
|---|---|---|---|---|---|---|---|
| 15 | 0.25 | 100 / 100 / 100 | 0 % | 12 % | 5 / 25 / 5 | 0.0 % | Y n n Y |
| 15 | 0.41 | 100 / 100 / 100 | 5 % | 33 % | 10 / 15 / 75 | 0.0 % | Y n Y Y |
| 15 | 0.70 | 100 / 100 / 100 | 0 % | 63 % | 15 / 80 / 95 | 3.0 % | Y n n Y |
| 20 | 0.25 | 100 / 100 / 100 | 25 % | 13 % | 5 / 5 / 30 | 0.0 % | Y n n Y |
| **20** | **0.41** | **100 / 100 / 100** | **65 %** | **30 %** | **5 / 35 / 50** | **0.0 %** | **Y Y Y Y** |
| 20 | 0.70 | 100 / 100 / 100 | 85 % | 63 % | 20 / 70 / 100 | 0.9 % | Y Y n Y |
| 30 | 0.25 | 100 / 100 / 100 | 95 % | 2 % | 0 / 0 / 5 | 0.0 % | Y Y n Y |
| 30 | 0.41 | 100 / 100 / 100 | 100 % | 17 % | 0 / 5 / 45 | 0.0 % | Y Y n Y |
| 30 | 0.70 | 100 / 100 / 100 | 100 % | 52 % | 5 / 50 / 100 | 1.0 % | Y Y Y Y |

Reading the matrix:

- **c = 15** is too slow for the careful pilot: β ≤ 0.5 is only 7.5 u/s, so hunting takes too long (level 5 at most 5 %), while the aggressive pilot's death rate is about the same as at c = 20.
- **c = 30** (with β capped at 0.5 the careful pilot flies 15 u/s) clears everything, and the aggressive pilot survives too easily at the default a0 (17 %). Only the strong engine (0.7) brings its death rate up. It is a valid alternative, but then every distance-in-seconds feels shorter and the speed-dependent tuning would shift.
- **a0/c = 0.25** (sluggish) is too safe for the aggressive pilot (2 to 13 %) and slows the careful one on level 5. **0.7** makes flat-out flight a coin flip even on level 2 (70 % death), which violates "risky but not suicide".
- **c = 20, a0/c = 0.41** is the only cell besides the extreme (30, 0.7) that passes all four, and it is the owner's pick (c = 20, `ENGINE_ACCEL_PER_C` from the 5 s-to-0.9c rule). Kept.

### Chosen setting in detail (c = 20, a0/c = 0.41, 20 seeds, final sweep)

| Bot | Lvl | clear % | died % | timeout % | t death s | t clear s | dmg/min | kills/min | γ mean | γ p95 | warp capped % | steps/frame |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cautious | 1 | 100 | 0 | 0 | - | 16.0 | 0.0 | 22.5 | 1.12 | 1.16 | 0.0 | 1.12 |
| cautious | 2 | 100 | 0 | 0 | - | 39.2 | 0.0 | 23.0 | 1.14 | 1.16 | 0.0 | 1.14 |
| cautious | 3 | 100 | 0 | 0 | - | 59.4 | 0.0 | 30.3 | 1.13 | 1.16 | 0.0 | 1.13 |
| cautious | 4 | 100 | 0 | 0 | - | 83.2 | 0.0 | 36.8 | 1.13 | 1.16 | 0.0 | 1.13 |
| cautious | 5 | 65 | 0 | 35 | - | 111.0 | 0.0 | 40.6 | 1.12 | 1.16 | 0.0 | 1.12 |
| aggressive | 1 | 95 | 5 | 0 | 7.2 | 24.0 | 33.5 | 15.3 | 1.83 | 3.37 | 0.0 | 1.83 |
| aggressive | 2 | 65 | 35 | 0 | 31.1 | 48.5 | 90.2 | 18.0 | 2.02 | 3.51 | 0.0 | 2.02 |
| aggressive | 3 | 40 | 50 | 10 | 27.3 | 57.2 | 131.0 | 29.0 | 1.96 | 3.45 | 0.0 | 1.96 |
| aggressive | 4 | 30 | 60 | 10 | 27.9 | 97.1 | 124.4 | 37.3 | 1.97 | 3.41 | 0.0 | 1.97 |
| aggressive | 5 | 5 | 95 | 0 | 26.8 | 104.5 | 274.1 | 58.9 | 1.76 | 2.91 | 0.0 | 1.76 |
| idle | 1 | 5 | 0 | 95 | - | 106.8 | 0.0 | 0.9 | 1.00 | 1.00 | 0.0 | 1.00 |
| idle | 2 | 0 | 0 | 100 | - | - | 0.0 | 2.9 | 1.00 | 1.00 | 0.0 | 1.00 |
| idle | 3 | 0 | 0 | 100 | - | - | 0.0 | 3.9 | 1.00 | 1.00 | 0.0 | 1.00 |
| idle | 4 | 0 | 0 | 100 | - | - | 0.0 | 6.3 | 1.00 | 1.00 | 0.0 | 1.00 |
| idle | 5 | 0 | 0 | 100 | - | - | 0.0 | 10.4 | 1.00 | 1.00 | 0.0 | 1.00 |

The full tables for every cell (c × a0 × level × bot) are produced by the task into `tool/build/balance/summary.md` and `runs.csv` (not committed: a few hundred KB of generated output).

## Findings worth keeping

- **Speed is the only thing that hurts.** Idle and cautious take almost no damage at any setting: asteroid spawn speeds (≤ 0.14c) stay under the ram damage threshold (γrel − 1 < 0.02, about 0.2c relative), so a rock only hurts when the ship brings the speed. That is the design intent ("going fast is a gamble"). It also means asteroid speed is a weak lever on danger and a strong lever on how hard the rocks are to hit.
- **Throughput limits the high levels, not survival.** Kill rate of the cautious pilot is 22 to 41 per minute; level 5 needs 26 large rocks = 78 kills (130 shots). At 3 shots/s, 120 s is a hard ceiling of 360 shots, so fire rate and hit rate cap the level length. If weapons are upgraded later (shop), revisit the counts.
- **Time warp is rarely capped.** With the default engine the aggressive pilot reaches γ p95 ≈ 3.4, well under the 10× cap (`TIME_WARP_MAX`), so the FAKE time-warp cap (`TIME_WARP_MAX`, see `physics-lies.md`) practically never triggers. Worst cell in the sweep: 3.0 % (c = 15, a0/c = 0.7, level 3). Mean physics steps per frame stay at about 1.1 for a careful pilot and about 2 for a fast one (cap 10 per frame, `MAX_STEPS_PER_FRAME`), so web performance headroom is fine at these asteroid counts.
- **Level 5 margin is thin.** The T2 target (cautious clears level 5 in ≥ 50 %) passes with only ~15 points of margin. Re-run the sweep (at least `--levels=5 --bots=cautious`, or `tool:balanceSmoke`) whenever weapon fire rate or upgrades, or the 120 s level cap (`--max-seconds`), change.
- **Single-threaded sweep.** `BaseInfoComponent.globalCounter` is a plain `@Volatile` int, not atomic, and the runs share it; a parallel sweep would need an atomic counter. It stays single-threaded (about 2 min is fine) and the game code stays free of `java.util.concurrent`, which keeps it web-safe.
- **Caveats.** The bots are scripted and tuned once; they model a decent but not expert human. Numbers will shift if the bots change. The sweep uses true positions for decisions (an assist), and the auto-aim control scheme; a mouse-hull sweep (`--scheme=mouse`) is possible but the cautious pilot's non-leading shots will clear much less. 20 seeds give about ±10 % on a rate, so the 50 % level-5 target is met with margin of about 15 points only (65 % at 20 seeds, 70 % at 40 seeds).
- **No physics lie added.** The changed constants are gameplay tunables (asteroid count and speed) and the damage scale already registered as `physics-lies.md` row 35 (now 0.5).

## Enemies in the sweep (ST-159)

`runLevel` spawns the level's enemies (`EnemyShipProvider.spawn`, `enemyCount(level)`: none before level 3, then 1, 2, 3) after the asteroids, with no holes or planets to avoid. `LevelSetup.enemies` overrides the count (any other value puts that many on a ring around the player; tests use it for a level-2 enemy). Enemy ships are plain targets for the bots (same list as the rocks, nearest first). `summary.md` has an "Enemies" table per bot, for levels that spawn them.

Columns: **all killed %** is the share of runs where every enemy died; **dmg from enemies** is the mean over those runs; **t kill all** is the mean pilot time of the last enemy kill over the runs that killed them all. `runs.csv` gains `enemies_spawned, enemies_killed, damage_from_enemies, enemy_clear_seconds`.

Attribution is APPROX: `damageFromEnemies` is the player's health drop in frames where a spent projectile of faction ENEMY was removed, so a ram in the same frame counts too. A dead or removed laser that missed is not counted (only `spent` ones, i.e. hits).

### First numbers (8 seeds, c = 20, default a0, levels 2, 3, 5)

Level 2 has no enemies. Enemy table (all killed % / mean dmg from enemies / t kill all s):

| Bot | Lvl 3 | Lvl 5 |
|---|---|---|
| cautious | 100 / 0.0 / 11.8 | 75 / 53.1 / 15.6 |
| expert | 100 / 7.6 / 13.2 | 63 / 56.5 / 16.9 |
| aggressive | 88 / 0.0 / 23.4 | 38 / 0.0 / 27.2 |
| idle | 38 / 111.7 / 7.6 | 0 / 163.0 / - |

Level outcomes (clear / died %): cautious L3 100/0, L5 75/25; expert L3 100/0, L5 50/38; aggressive L3 63/38, L5 0/100; idle L3 0/63, L5 0/100 (idle takes mean 1289 damage per minute on level 5, mostly rocks).
