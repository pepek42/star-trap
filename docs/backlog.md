# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `agents/planner.md`. **Never renumber cards.** The next free ID is **ST-203**.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

## Implementation order

Owner 2026-11-02. /auto-dev works down this list and takes the first `ready` card whose `depends` are all `done`. `needs-user` cards are skipped by /auto-dev (ask the owner, batched); nothing below is blocked by them, because every card is planned around the recommended answer.

Web build (owner 2026-11-04: web is the primary target):

M3 Relativity MVP:

M4 Relativistic view:

Audio (owner 2026-11-05):

M5 Gravity & black holes (gravity → black holes/planets → disk → lighting → lensing):

M6 Enemies (planner 2026-11-07: honest light-delayed AI, research E13):
49j. ST-173 Enemy difficulty tuning (owner decision)

Menus & runs (owner 2026-11-07: New game / Scenarios / Settings menu done; save, shop and prebuilds come later):

M7 Ship it (owner 2026-11-12: wrap up and ship to master; roguelike cut to a weapon unlock; physics and graphics first; UI waits for owner decisions):
63b1. ST-202 Pilot-view ghosts: lit, and none for the player
63c. ST-080 Searchlight k = 2 in pilot view, with a brightness floor
63d. ST-194 Exact searchlight exponents and 10 % floors (ST-185 Q3)
63e. ST-195 Gravitational red- and blueshift in colours
63f. ST-196 Laser damage: wider clamp and gravitational photon energy (ST-185 Q5)
63g. ST-197 Pilot view: light-delayed rocket plume
63h. ST-198 Photons fly until they leave the arena
63i. ST-199 Pilot clock exact past the warp cap
63j. ST-200 A hole that eats a planet grows
64. ST-186 Lock-on shot: booster, sustainer and side-thruster turning
65. ST-187 Lock-on shot: visible booster, sustainer and RCS plumes
65a. ST-192 Rocket fire control: Newtonian branch and gravity-aware planning
66. ST-189 Rebalance after the weapon changes
67. ST-188 Simplify the UI (owner decisions)
67a. ST-193 Weapon unlock polish: new-weapon highlight, testable HUD slot text
68. ST-190 Release prep: merge AI-slop to master

---

## M4 Relativistic view

The pilot view itself (light delay E8, aberration E9, pilot-frame aiming, lead markers) was pulled forward into M3 by the 2026-11-02 interview: ST-070 to ST-078. M4 holds the follow-ups.
- result: `RelativitySettings.pilotHullTransform` (pilotView && relativityEnabled && pilotAberration) now gates both the drawn player hull in `PilotView.project()` and the `PlayerInputSystem` cursor mapping.

### ST-202 Pilot-view ghosts: lit, and none for the player
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `system/LightHistorySystem.kt` (`GhostInfo`), `render/PilotView.kt` (`projectGhost`), `system/RenderSystem.kt` (`drawGhosts`), `docs/design/physics-lies.md` row 55; tests
- refs: commit ST-079, physics-lies row 55
- layer: sim
- goal: Follow-ups from ST-079. (1) Ghosts are drawn unlit. Copy the normal map / light state the live sprite uses into `GhostInfo` so a ghost is lit like the live entity was, then trim row 55. (2) Don't create a ghost for the player's own entity (a death ghost could show after a respawn).
- acceptance:
  - [ ] Test: a ghost keeps the lighting data of its entity
  - [ ] Test: removing the player creates no ghost
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports PASS
- playtest: Pilot view with light delay: a far asteroid you destroy stays visible, lit and shadowed like before, until its light arrives.

### ST-080 Searchlight k = 2 in pilot view, with a brightness floor
- status: ready
- note (owner 2026-11-12): unparked into M7. The spec dates from 2026-11-03: check every file and symbol it names against the current code (pilot view, `PilotViewProjector`, `LightHistorySystem`) before starting, and adapt it.
- note (owner 2026-11-12): no outlines. The target rings and the tactical overlay already mark dim targets at their true position. The 25 % floor may be lowered by the ST-185 audit (its Q3).
- note (ST-185, 2026-11-13): ST-194 follows this card: floors 0.1, clamps [0.1, 10], exponent 3 for sprites and 2 for stars and disk particles. Build `beamBrightness` so it can take those.
- size: S
- agent: kotlin-dev
- depends: ST-043, ST-045, ST-074
- files: `render/RelativityVisuals.kt`, `render/Starfield.kt`, `system/RenderSystem.kt`; tests
- goal: Full relativistic beaming in the pilot view (research E10, k = 2): things ahead blaze and things behind dim. Q14 is planned around the recommendation (b), so behind you nothing goes below 25 %.
- spec:
  - `const val BRIGHTNESS_EXPONENT_PILOT = 2f`. Starfield and sprite tint use `B = clamp(D^k, B_MIN, B_MAX)` with `k = BRIGHTNESS_EXPONENT_PILOT` in pilot view and `BRIGHTNESS_EXPONENT_MVP` in world view. Pull it into `fun beamBrightness(d: Float, pilotView: Boolean): Float`.
- acceptance:
  - [ ] Tests: `beamBrightness(2f, true) == 3f` (clamped from 4); `beamBrightness(0.3f, true) == 0.25f` (floor); `beamBrightness(0.6f, false) == 0.6f`; `beamBrightness(1f, true) == 1f`
  - [ ] `./gradlew core:test` passes
- playtest: At speed the view ahead is bright, and asteroids behind you are dim; the target rings still show where they are.

### ST-173 Enemy difficulty tuning (owner decision)
- status: needs-user
- size: S
- agent: junior-dev
- depends: —
- files: `provider/EnemyShipProvider.kt` (`enemyCount`), enemy weapon constants (`ENEMY_LASER_DAMAGE`, fire rate), `docs/tech/balance.md`
- refs: `docs/tech/balance.md` "Enemy-aware bots and the full sweep (ST-172)"; commit "ST-172"
- layer: sim (balance)
- goal: Follow-ups from ST-172. With enemies, target T2 (cautious bot clears level 5 in >= 50 % of runs) fails at the chosen cell: 45 % (35 % deaths, 62 damage from enemies). Level 3 with one enemy is trivial (95 % clear, 11 damage).
- question: How to even out enemy difficulty? (a) scale enemy laser damage/fire rate with level (weaker at L5 per ship, stronger at L3) — recommended; (b) cut enemy laser damage or fire rate by ~20-25 % everywhere and spawn 2 enemies on L3; (c) cap enemies at 2 until a later level; (d) accept T2 failing (L5 is meant to be hard).
- spec (for the recommended option): derive per-level enemy damage from `ENEMY_LASER_DAMAGE` (KDoc derivation, no literals), re-run `./gradlew tool:balanceSim -PbalanceArgs="--levels=3,5 --bots=cautious,expert"` and record the numbers in `balance.md`.
- acceptance:
  - [ ] Cautious clears L5 in >= 50 % and L3 is no longer 0-damage trivial (record the numbers)
  - [ ] `./gradlew core:test tool:test` passes
- playtest: Level 3's single enemy is a real threat; level 5 is hard but survivable.
- note: the bots only weave, they don't dodge individual lasers (would need projectile positions in `BotView`); enemy damage attribution is still APPROX (ST-159).

---

## M7 Ship it

- note (owner 2026-11-12): "I want soon to wrap this project up and ship it to main. Simplify scope. I want all the physics to be as close as possible." Decisions: the roguelike is cut to a weapon unlock (start with lasers; after level 1 pick dumb rockets or the lock-on shot; after level 2 get the other one; a quick start at level 5 gets all weapons). Mouse control schemes go. The UI gets simplified, but only after the owner's decisions (ST-188). Physics and graphics have priority; the game stays as realistic as possible. Owner correction (2026-11-12): the targeting computer already makes the game easy, so the pilot view can carry much more physics (a confusing visual layer is fine, the computer reads it for you). ST-184 was dropped; the parked pilot-view realism cards ST-075, ST-077, ST-079 and ST-080 (no outlines) are unparked into M7, ST-078 and ST-091 are closed as out of scope. Outside stays the default perspective; Pilot is the realistic option. The lock-on improvements (ST-186, ST-187) come after the rocket and progression work.
- note: every card with questions is `needs-user` until the owner fills the `A:` lines; the specs are written around the recommended answers (marked "Rec").

### ST-192 Rocket fire control: Newtonian branch and gravity-aware planning
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-182
- files: `weapon/RocketIntercept.kt`, `input/AutoAimStrategy.kt`, `system/TacticalOverlaySystem.kt`, tests
- refs: `weapon/RocketIntercept.kt` KDoc (ST-182), `physics/RelativitySettings.kt` `relativityEnabled`, `system/GravitySystem.kt`
- layer: assist
- goal: Follow-up from ST-182. The rocket probe models the relativistic motor only: with `relativityEnabled` off the rocket integrates Newtonian, and near a planet or black hole `GravitySystem` adds accel the probe does not see, so the aim and the intercept ring point at a straight-line meeting point the rocket never reaches.
- spec:
  - Pick the probe's per-step velocity update with the same `relativityEnabled` switch `RelativisticDynamics` uses (pass the flag in; no new setting).
  - Fold the gravity accel (`Gravity.kt` pull, `GravitySystem` settings) at the probe position into the probe. If evaluating the field per probe step is too costly, use the assist fallback: no ring and no fire while the plan runs within a `GRAVITY_PLAN_...` radius of a source, named in the KDoc.
  - Tests for both: relativity off hits the β = 0.5 moving-target case; near a planet the solved plan follows the bent path (or the fallback holds fire and hides the ring).
- acceptance:
  - [ ] Test: with `relativityEnabled = false`, a rocket fired along `rocketInterceptDir` hits a moving asteroid
  - [ ] Test: near a gravity source the aim (or the fallback) never sends the rocket where it cannot meet the target
  - [ ] `./gradlew test` passes
- playtest: Rockets still hit near a planet or hole, and the intercept ring lines up with the real meeting point.

### ST-194 Exact searchlight exponents and 10 % floors (ST-185 Q3)
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-080
- files: `render/RelativityVisuals.kt`, `render/Starfield.kt`, `render/AccretionDisk.kt`, `system/RenderSystem.kt`, `docs/design/physics-lies.md` rows 21, 26, 31, 33; tests
- refs: `release-fidelity-audit.md` §2 "Brightness exponents", `relativity-effects.md` E10 (2D column)
- layer: sim
- goal: Owner ST-185 Q3: move the visuals toward the true values and keep the floors only as a safety net. In 2D, surface brightness goes as D³ and a point source's flux as D², and length contraction is L₀/γ.
- spec:
  - `CONTRACTION_MIN_SCALE` 0.25 → 0.1 (both views). `DOPPLER_D_MIN/MAX` → 0.1 / 10. `B_MIN/B_MAX` → 0.1 / 10.
  - Extend ST-080's `beamBrightness` into `beamBrightness(d, pilotView, point: Boolean)`. Pilot view: k = `BRIGHTNESS_EXPONENT_SURFACE` = 3 for sprites, `BRIGHTNESS_EXPONENT_POINT` = 2 for stars and disk particles (the aberration mapping already crowds points by another factor D). Outside view: `BRIGHTNESS_EXPONENT_MVP` = 1 (row 52). Write the derivation in the KDoc.
  - Sprites: alpha `min(B, 1)`, and the additive glow alpha `clamp((B − 1)·DOPPLER_GLOW_STRENGTH, 0, 1)` from B instead of D (identical in Outside view, where B = D).
  - Disk pilot path: `u_beam` gets the point exponent and the new clamps.
  - Update rows 21, 26, 31 and 33 (new values; the floors stay as FAKE safety clamps).
- acceptance:
  - [ ] Tests: `contractionK(20f)` gives scale 0.1 (floor) and `contractionK(5f)` gives exactly 1/5; `beamBrightness(2f, true, false) == 8f`, `beamBrightness(2f, true, true) == 4f`, `beamBrightness(0.3f, true, false) == 0.1f`, `beamBrightness(0.6f, false, false) == 0.6f`
  - [ ] ST-080's tests are updated to the new clamps
  - [ ] `./gradlew core:test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Pilot view at 0.9 c: the sky and the asteroids behind go nearly black, things ahead glow; fast ships flatten to slivers (γ up to 10).

### ST-195 Gravitational red- and blueshift in colours
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-194
- files: `physics/ObserverState.kt`, `system/ObserverSystem.kt`, `system/RenderSystem.kt` (`tintAndQueue`), `render/Starfield.kt`, `render/PilotView.kt`, `render/AccretionDisk.kt` (`diskParticleApparent`), `assets/shaders/accretion_disk.vert`, `docs/design/physics-lies.md` rows 26, 53; tests
- refs: `release-fidelity-audit.md` §1 rows 26 and 53, `relativity-effects.md` E15 and E17, `GravitySystem` `gravFactor`
- layer: sim
- goal: Light climbing out of a well loses energy and light falling in gains it. Hover near a hole and the sky turns blue (×3.3 at 1.1 r_s); things deep in a well look red. Today no colour sees gravity (new lie row 53).
- spec:
  - `ObserverState.gravFactor` = the player's `RelativisticComponent.gravFactor` (1 without one), set in `ObserverSystem` (not smoothed).
  - Every Doppler factor gets `D_tot = D·g_e/g_o`: sprites use `g_e` = the entity's current `gravFactor` (APPROX in pilot view: the current value, not the retarded one; note it in row 53), and stars use `g_e = 1` (at infinity). Do it in one pure helper `gravDoppler(d, ge, go)`.
  - Disk, pilot path (shader and `diskParticleApparent` mirror): the baked `a_temp` already holds the face-on `g = √(1 − 1.5 r_s/r)`, which includes the transverse Doppler. So drop the source γ: `d = γ_o(1 − β_o·n̂) / (g_o·(1 − β_src·n̂))` (new uniform `u_gObs`). Brightness = `clamp(D_disk², …)` with `D_disk = g·d`, replacing `g⁴·D^k` (g⁴ is the face-on 3D value for the top-down camera). Keep the world path as is (exact face-on).
  - Rewrite row 53 as APPROX: `g_e` is current rather than retarded, other holes don't redshift the disk, Galilean hole velocity (row 26).
- acceptance:
  - [ ] Test: `gravDoppler` with `g_o` = 0.3, a static star and a pilot at rest gives D = 1/0.3
  - [ ] Test: `diskParticleApparent` for a hole at rest and an observer at rest far away along the orbit's tangent gives `D = √(1 − 1.5 r_s/r)/(1 ∓ β_orb)` within 1e-4
  - [ ] `./gradlew core:test` passes and `scripts/web-check.sh --play` reports OK (shader change)
- playtest: Hover just outside a hole's ISCO: the starfield shifts blue and brightens; an asteroid falling in reddens.

### ST-196 Laser damage: wider clamp and gravitational photon energy (ST-185 Q5)
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `physics/PhysicsConstants.kt`, `component/ProjectileComponent.kt`, `provider/WeaponProjectileProvider.kt`, `system/ContactSystem.kt` (`photonDamageFactor`), `render/TacticalOverlay.kt` (`displayedPhotonFactor`), `system/TacticalOverlaySystem.kt`, `docs/design/physics-lies.md` row 36; tests
- refs: `relativity-effects.md` E12, E15
- layer: sim
- goal: Owner ST-185 Q5: widen the photon damage clamp to [0.1, 10]. Also add the missing gravitational term: a photon's energy at the target is `E·D_{s→t}·g_s/g_t`, so a shot from high up into a hole's well hits harder.
- spec: `PHOTON_DAMAGE_D_MIN/MAX` → 0.1 / 10. `ProjectileComponent.shooterGravFactor` (reset to 1) is set at emission from the shooter's `RelativisticComponent.gravFactor`. `photonDamageFactor` multiplies by `shooterGravFactor / target.gravFactor` (1 when the target has none) before the clamp. `displayedPhotonFactor` gets `gs, gt` parameters with the same formula, so the overlay label matches the hit. Update row 36.
- acceptance:
  - [ ] Test: a head-on hit (shooter at rest, target at 0.9 c toward it) gives 4.36 (no longer 4); with `g_s = 1, g_t = 0.5` a shot from rest at a target at rest gives 2
  - [ ] Test: the overlay factor equals the applied factor for the same inputs
  - [ ] `./gradlew core:test` passes
- playtest: Charging an enemy head-on, the overlay shows ×4+ and the hit lands that hard.

### ST-197 Pilot view: light-delayed rocket plume
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `component/RocketComponent.kt`, `system/RocketMotorSystem.kt`, `system/RenderSystem.kt` (`applyRocketPlume`), `render/PilotView.kt` (`sourceLag`), `docs/design/physics-lies.md` row 49; tests
- refs: `architecture.md` row 6d (`RocketMotorSystem`), physics-lies row 49
- layer: sim
- goal: At c = 20 a rocket 40 u away is seen 2 s late, longer than its 1.5 s burn, yet its plume follows the true `burnLeft`. In the pilot view the plume should show while the rocket's light shows it burning.
- spec: `RocketComponent.burnEndTime` (arena time, `GameClock`/`ObserverState.worldTime`, set when `burnLeft` reaches 0; `+∞` while burning; reset in `reset()`) and `launchTime`. A pure `plumeVisible(emitTime, launchTime, burnEndTime) = emitTime >= launchTime && emitTime < burnEndTime`. Pilot view: `emitTime = now − pilotView.sourceLag`; Outside view: `emitTime = now` (unchanged behaviour). Drop the plume clause from row 49. ST-187 reuses `plumeVisible` for the lock-on booster and sustainer plumes.
- acceptance:
  - [ ] Test: `plumeVisible` at the edges (launch, burnout, and while burning with `burnEndTime = ∞`)
  - [ ] Test: a rocket that burned out at t = 1 and is seen with a 0.8 s lag at now = 1.5 still shows the plume; at now = 1.9 it doesn't
  - [ ] `./gradlew core:test` passes
- playtest: In pilot view a distant rocket keeps its flame after launch until its light says it burned out.

### ST-198 Photons fly until they leave the arena
- status: needs-user
- size: S
- agent: kotlin-dev
- depends: —
- files: `weapon/ProjectileConstants.kt`, `provider/WeaponProjectileProvider.kt`, `docs/design/physics-lies.md` row 9; tests
- refs: physics-lies rows 9 and 13, `architecture.md` (projectile sweep, off-arena removal ST-067)
- layer: sim
- goal: A laser bolt disappears 2 s (40 u) after leaving the muzzle, in the middle of empty space. Photons don't decay: one should fly until it crosses the arena edge, where it is already removed.
- questions:
* Q1: This gives the laser (and the enemies' lasers) the whole arena as range. OK, with ST-189 rebalancing the enemy fire range? Rec: yes.
  * A:
- spec (Rec): `PHOTON_WORLD_LIFETIME` becomes a safety net derived from the map: `(diagonal of the arena + margin) / c`, computed at spawn from `MapProvider` and the runtime c (KDoc derivation; no literal). The fade only plays at the arena edge, or drop it. Measure the live photon count in a level-5 run (debug log) and run `scripts/web-check.sh --play`. Update row 9 (only the off-arena removal and the safety lifetime remain).
- acceptance:
  - [ ] Test: a photon fired across an empty arena from the centre is removed at the edge, not before
  - [ ] `./gradlew core:test` passes and `scripts/web-check.sh --play` reports OK (body count)
- playtest: Shots cross the whole arena; no frame drops on the web build in a busy level.

### ST-199 Pilot clock exact past the warp cap
- status: needs-user
- question: Past the warp cap (mostly near holes) the player's own fire rate and turning would slow in real time, like every other body. It's more exact, but it changes the feel. Do it? Rec: yes.
- size: S
- agent: kotlin-dev
- depends: —
- files: `component/RelativisticComponent.kt` (`properDelta`, `rotationTimeFactor`), `docs/design/physics-lies.md` rows 27, 28; tests
- refs: physics-lies rows 1, 27, 28; `architecture.md` "Physics loop details"
- layer: sim
- goal: Below the warp cap the pilot's clock and real time agree. Past the cap (γ/g > `timeWarpMax`, mostly near holes) the arena can't keep pace, but the player's guns and turning still run on real time. Make them run on the pilot's proper time like every other body, so the pilot-to-arena clock ratio is exact everywhere and the only lie left is row 1 (the playback slows down).
- spec: for the player `properDelta = worldDt·gravFactor/gUsed` and `rotationTimeFactor = gravFactor/gUsed`, the same as other relativistic bodies. Below the cap this equals the current values (worldDt = warp·realDt, warp = γ/g); keep the player branch only for when the player has no `RelativisticComponent`. Delete rows 27 and 28 (mark them retired), and extend row 1: "past the cap the whole game, the pilot included, plays slower than real time".
- acceptance:
  - [ ] Test: below the cap the player's `properDelta` equals `realDt` (within float error) and the turn time is unchanged
  - [ ] Test: at γ = 20 with `timeWarpMax` = 10 the player's cooldown advances `realDt/2` per frame
  - [ ] `./gradlew core:test` passes
- playtest: Hovering at the edge of a hole, your own fire rate and turning slow down visibly as the arena hits the warp cap.

### ST-200 A hole that eats a planet grows
- status: ready
- size: S
- agent: junior-dev
- depends: —
- files: `system/GravitySystem.kt` (`eatPlanets`), `docs/design/physics-lies.md` row 19b; tests
- refs: `physics/Gravity.kt` `mergeSources`, physics-lies row 19b
- layer: sim
- goal: The planet's mass is about 13 % of the hole's (gm 25.8 against 200 at the defaults), so swallowing it should grow the hole and pass on its momentum.
- spec: in `eatPlanets`, before adding the planet's `RemoveComponent`, call `mergeSources` with masses `rs_hole` and `2·gm_planet/c²` (runtime `settings.c`; a hole's `gm = rs·c²/2`), then set the hole's `rs` to the merged mass and its body velocity to the merged velocity. Keep the hole's position (the planet is within `rs + 0.5 R`). The disk and renderer already follow a changed `rs` (black-hole merges, ST-085). Retire row 19b.
- acceptance:
  - [ ] Test: a resting hole (rs 1) eats a planet (gm 25.8, v = (2, 0)) at c = 20: rs becomes 1.129 and the hole's velocity is (0.229, 0) (momentum conserved)
  - [ ] `./gradlew core:test` passes
- playtest: —

### ST-186 Lock-on shot: booster, sustainer and side-thruster turning
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-183
- files: `weapon/LockOnConstants.kt`, `weapon/LockOnSteering.kt`, `system/LockOnSteeringSystem.kt`, `component/LockOnComponent.kt`, `provider/LockOnProvider.kt`, `docs/design/physics-lies.md` rows 46-47; tests
- refs: physics-lies rows 46-48, `architecture.md` "Physics loop details", `ShipRotationController.kt` (PD turning), commit ST-164
- layer: sim
- goal: Owner 2026-11-12: lock-ons need to be more dynamic and fly like the real thing. After launch a booster gives a big speed boost for a short time; then the regular (sustainer) engine takes over. The shot turns with side thrusters (a torque, finite angular acceleration), not by setting its angle, and the main engines only push along the hull, so the path curves because the hull turns.
- questions:
* Q1: Booster (Rec): proper acceleration 8 × `ENGINE_PROPER_ACCEL` for 0.3 s of the shot's clock, along the launch heading, no steering during the boost. OK?
  * A:OK
* Q2: Sustainer (Rec): the current 3 × engine accel for 2 s, then coast; the sustainer throttles down when the hull is more than 30° off the guidance direction (so it does not push the wrong way). Or should it burn until it hits (longer chase)?
  * A:Sustainer should have infinite fuel for booster and manoeuvre boosters. TBD exact values during rebalance
* Q3: Guidance (Rec): proportional navigation (N = 3) on the target's true state, replacing the current "lead point" solver; it is what real missiles use and curves more naturally. OK?
  * A:Looks ok
* Q4: Side thrusters (Rec): angular acceleration limit 20 rad/s², max turn rate kept at `LOCK_ON_TURN_RATE`, turning by a PD controller through Box2D torque. OK?
  * A:OK
- note (owner answers 2026-11-13): Q1, Q3, Q4 Rec. Q2: the booster is the finite 0.3 s kick; the sustainer and the RCS have unlimited fuel, so there is no COAST phase: the sustainer burns until the shot hits or its lifetime ends (still throttled down when the hull is > 30° off the guidance direction). Exact values are tuned in ST-189. End of flight: keep a lifetime cap derived from the arena (boost + powered flight across `LOCK_ON_RANGE_ARENA_FRACTION` of the arena), then the shot self-destructs; if the target dies it flies straight on until the cap.
- spec: `LockOnComponent` gets a `phase` (BOOST, SUSTAIN) and the boost timer (proper time, ticked by `dt/γ`). Thrust is always along the body angle (`thrustAccelWorld` with the hull heading, never the guidance direction), sustainer throttle `max(0, cos Δ)`-style falloff to zero past 30°. Turning: proportional navigation (N = 3) on the target's true state gives a desired heading; a PD controller applies `body.applyTorque` limited by `LOCK_ON_RCS_ANGULAR_ACCEL · I`, turn rate capped at `LOCK_ON_TURN_RATE`; no `setTransform`. Expose the current RCS command (−1..1), the phase and the sustainer throttle on the component for ST-187. Rewrite `lockOnLifetime` for a powered (constant proper acceleration) flight instead of burn + coast, with the derivation in the KDoc. Update physics-lies rows 46-47 (unlimited sustainer fuel joins row 6's reason).
- acceptance:
  - [ ] Test: the angle changes only through angular velocity (no `setTransform` after launch), and |dω/dt| never exceeds the RCS limit
  - [ ] Test: the speed after the boost matches the formula within 2 %; during the boost the heading does not change
  - [ ] Integration test: a shot launched 90° off a target at 40 u hits it within its lifetime
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: A lock-on leaps off the rail, then visibly swings its nose around and curves onto the target.

### ST-187 Lock-on shot: visible booster, sustainer and RCS plumes
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-186, ST-181
- files: `system/RenderSystem.kt` (or a new `render/ExhaustRenderer.kt` shared with the rockets and the ship), `raw_assets/` plume sprites + `tool:packTextures`; tests
- refs: `render/RelativityVisuals.kt` (Doppler tint), `render/PilotView.kt`, physics-lies row 20
- layer: sim
- goal: "It should be all visible" (owner 2026-11-12): a big, bright booster plume, a smaller sustainer plume and short side puffs when the RCS fires, drawn for lock-on shots (and the main plume for dumb rockets), so the player can read what each missile is doing. Plumes are light like any sprite: light-delayed and aberrated in pilot view, Doppler-tinted.
- note (2026-11-13): ST-186 has no COAST phase (unlimited sustainer); the sustainer plume scales with the sustainer throttle instead.
- note (ST-185, 2026-11-13): "light-delayed" includes the plume state: in pilot view pick the phase with ST-197's `plumeVisible` at the emission time `now − sourceLag` (store the boost end time like `burnEndTime`). Throttle and RCS puffs from the current state are APPROX; add them to the lock-on row in `physics-lies.md`.
- spec: An additive plume quad behind the body, length and alpha from the phase (BOOST large, SUSTAIN medium × sustainer throttle, so it shrinks to nothing when the hull is far off course; dumb rockets: the main plume until burnout); two RCS puff quads at the nose sides, alpha from the RCS command of ST-186 (left/right by its sign). Plumes use the entity's displayed transform (retarded in pilot view) and the same Doppler tint as the body. No allocation per frame; pure helper `plumeGeometry(phase, rcs, out)` tested.
- acceptance:
  - [ ] Test: `plumeGeometry` gives BOOST > SUSTAIN (full throttle) > SUSTAIN (zero throttle) = 0 lengths and the RCS puff on the correct side for a positive and a negative command
  - [ ] Screenshot (harness) viewed by the agent: a just-launched lock-on shows the booster flame, a turning one shows side puffs
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: You can tell at a glance which missiles are boosting, cruising or coasting, and which way they are turning.

### ST-188 Simplify the UI (owner decisions)
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: ST-179, ST-180, ST-183
- files: `screen/MainMenuScreen.kt`, `ui/MainMenuLayout.kt`, `settings/MenuSettings.kt`, `ui/Hud.kt`, `screen/LevelClearedScreen.kt`, `ui/DebugPanel.kt`, `assets/i18n/messages.properties`; `main-menu.md`
- refs: `main-menu.md`
- layer: infra
- goal: Owner 2026-11-12: simplify the UI before release, blocked on the owner's decisions. This card collects them; the spec is written once they are answered (it may split into S cards).
- questions:
* Q1: Main menu: which entries stay? Rec: Continue, New game (level 1 / quick start level 5), Scenarios, Settings, Controls, Exit (desktop only).
  * A:
* Q2: Settings: which stay for players? Rec keep: Perspective, Speed of light c, Time warp, Ship-computer toggles (target rings, intercept rings, tethers, overlay mode), Sound volume, Fullscreen. Move to the debug panel only: Relativity off (Newtonian), engine slider, field and gravity tuning, grid.
  * A:
* Q3: Keep the scenarios (Flight school, Combat, Relativity, Gravity) in the release? Rec: yes, they teach the physics.
  * A:
* Q4: HUD: what stays? Rec: health, β and γ, ship time τ and arena time t, weapon slots with ammo/reload, targets left, the warp cap warning. Remove anything else (credits are gone with ST-180).
  * A:
* Q5: Debug panel (F1) in the release build: hidden unless `--debug-panel` (Rec), or removed?
  * A:
* Q6: Anything else on screen that bothers you (minimap, edge arrows, briefings)?
  * A:
- spec: written after the answers.
- acceptance:
  - [ ] Owner answers recorded; spec and acceptance filled in (or split into cards)
- playtest: —

### ST-193 Weapon unlock polish: new-weapon highlight, testable HUD slot text
- status: ready
- size: S
- agent: junior-dev
- depends: ST-188
- files: `screen/LevelClearedScreen.kt`, `ui/Hud.kt` (`updateWeapons`), tests
- refs: `game-design.md` §3 (weapon unlock), commit ST-183
- layer: infra
- goal: Follow-ups from ST-183. (1) The "New weapon: X (slot N)" line on the cleared screen gets a highlight (accent colour or a short pulse; a jingle only if an audio cue already exists). (2) Move the HUD's lock-on ammo / rocket pod / locked-slot text formatting out of `Hud.updateWeapons` into a small pure function and unit-test it.
- acceptance:
  - [ ] Test: the pure slot-text function covers locked, laser, lock-on ammo and rocket pod states
  - [ ] `./gradlew test` passes
- playtest: Clear level 2 and check that the new-weapon line stands out.

### ST-189 Rebalance after the weapon changes
- status: needs-user
- size: S
- agent: kotlin-dev
- depends: ST-183, ST-185
- files: `tool/` balance runner and bots, `provider/EnemyShipProvider.kt`, `docs/tech/balance.md`
- refs: `balance.md` (ST-101, ST-172 sections), ST-173
- layer: sim (balance)
- goal: The shop (upgrades) is gone, rockets and the unlock exist, and ST-185 may remove the braking boost. Re-run the headless sweep so levels 1-5 stay winnable without upgrades, and settle enemy difficulty (ST-173).
- questions:
* Q1: Fold ST-173 (enemy difficulty, needs-user) into this card and use its recommended option (a), enemy damage scaled per level? Rec: yes, and close ST-173.
  * A:
* Q2: Targets stay as before: a cautious bot clears levels 1-3 in ≥ 90 % and level 5 in ≥ 50 % of runs? Rec: yes.
  * A:
- spec (Rec answers): bots use the weapons a player has at that level (rockets picked after L1 in half the seeds, lock-on in the other half). Tune only derived spawn/enemy constants (no literals), re-run `./gradlew tool:balanceSim`, record the table in `balance.md`.
- note (owner 2026-11-13): also review (1) the braking boost strength `BRAKE_BOOST_K` (ST-185 Q1: it stays, but its strength is up for review) and (2) the ST-186 lock-on values: booster accel/duration, sustainer accel, RCS angular accel, lifetime cap.
- note (ST-185, 2026-11-13): the audit cards change the laser: ST-196 widens the damage clamp to [0.1, 10], and ST-198 (if accepted) gives photons the whole arena as range. Re-check the enemy laser threat with both in.
- acceptance:
  - [ ] The targets in Q2 hold; numbers recorded in `balance.md`
  - [ ] `./gradlew test tool:test` passes
- playtest: A fresh run to level 5 feels hard but fair with no shop.

### ST-190 Release prep: merge AI-slop to master
- status: needs-user
- size: S
- agent: junior-dev
- depends: ST-188, ST-189
- files: `README.md`, `docs/design/game-design.md` (§1 pitch, §3 loop, §4 controls, §10 milestones), `docs/README.md`, `CREDITS.md`, `AGENTS.md`, `docs/backlog.md`
- refs: `dev-workflow.md` "Web check"
- layer: infra
- goal: Get the branch ready for the owner to merge to `master`: docs describe the shipped game, dead code and stale docs are gone, the web build and tests pass. Agents never commit to or push `master`; the owner merges.
- questions:
* Q1: Version label for the release (e.g. `1.0`, `0.9-redshift`)? Where should it show (main menu corner, Rec)?
  * A:
* Q2: Merge style: the owner merges `AI-slop` into `master` as is (Rec), or squash per milestone?
  * A:
* Q3: Is the web bundle deployed somewhere as part of release (e.g. GitHub Pages), or only built?
  * A:
- spec: Update the docs listed above to the shipped scope (no roguelike, auto-aim only, weapon unlock). Check `CREDITS.md` lists every asset. Remove unused code found by a search for unreferenced classes and i18n keys. Run `./gradlew clean test` and `scripts/web-check.sh --play`; record the results in the commit body.
- acceptance:
  - [ ] `./gradlew clean test` passes and `scripts/web-check.sh --play` reports OK
  - [ ] GDD §1/§3/§4/§10 match the shipped game
  - [ ] The owner's merge command is written in the final message (not run)
- playtest: Full run from the main menu to level 3 on the web build.

