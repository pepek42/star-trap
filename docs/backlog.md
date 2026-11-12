# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `agents/planner.md`. **Never renumber cards.** The next free ID is **ST-192**.

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
60. ST-182 Auto-aim for dumb rockets
61. ST-183 Weapon unlock progression
62. ST-185 Physics fidelity audit before release
63. ST-075 Pilot view: per-corner retarded time (Terrell rotation)
63a. ST-077 Pilot view: starfield, field band and shimmer through the boost
63b. ST-079 Pilot view: ghosts of removed entities until their light runs out
63c. ST-080 Searchlight k = 2 in pilot view, with a brightness floor
64. ST-186 Lock-on shot: booster, sustainer and side-thruster turning
65. ST-187 Lock-on shot: visible booster, sustainer and RCS plumes
66. ST-189 Rebalance after the weapon changes
67. ST-188 Simplify the UI (owner decisions)
68. ST-190 Release prep: merge AI-slop to master

---

## M3 Relativity MVP

- note: Source is `docs/research/relativity-effects.md` §2, §4 Tier 1 (E1–E7) and §7 items 7–15. **Differences from `docs/design/game-design.md` §6:** the research promotes relativistic velocity addition for projectiles (GDD "Maybe") to MVP because Galilean addition breaks the c cap. It also moves length contraction (GDD "Next") into MVP in world view, and adds γ-mass collisions, speed zoom and a mild searchlight (k = 1) to MVP. The HUD shows ship time τ and world time t, which answers GDD §11 Q2 with "both". The GDD parking-lot idea "fast = bullet time" is physically backwards (research E2), so it's not planned. The GDD isn't edited here. ST-049 reconciles it once the owner has answered the questions below. **Feature flag:** `RELATIVITY_ENABLED` (ST-037). When false, providers don't attach `RelativisticComponent` and the Newtonian M2 path runs unchanged. (Superseded by ST-056: the flag becomes a runtime setting, and providers always attach the component.) **Owner questions** ST-030 … ST-034 are `needs-user`. Every other card is planned around their recommended answers and isn't blocked by them.
- note (2026-11-01, owner playtest after M2 + ST-037): "Feels a bit chaotic, maybe the arena should be bigger" → ST-030 answered (c), and ST-046 now also calms a0 and the asteroid spawn. "Relativity debug options on the side" → ST-056 (runtime `RelativitySettings`), ST-057 (plumbing), ST-059 (panel), ST-060 (vector overlay). Controls are undecided → ST-058/ST-061/ST-062 make schemes (a)/(b)/(c) switchable at runtime, and ST-063 asks for the default. γ: no gameplay cap (ST-032), safety bound 50 (ST-055). **From ST-056 on**, runtime code reads c, a0, the γ cap, field params and relativity/field on/off from the DI singleton `RelativitySettings`, not the consts (the consts are its defaults). Cards written earlier (ST-038 … ST-048) that say `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE` or `RELATIVITY_ENABLED` mean the corresponding setting once ST-056 is done.

- note (2026-11-02, owner interview, game-design.md §11): **The game is perceived from the pilot's frame.** The game clock is the pilot's proper time (research E21): the arena advances `γ·dt_real` per frame, capped at about 10× (declared FAKE past the cap, shown on the HUD), and the player's guns and turning run on real time (ST-068, ST-069, ST-040). **Pilot view is the main rendering** and is pulled forward from M4 into this milestone (E8 light delay + E9 aberration: ST-070 … ST-078, default on). The minimap stays true arena positions ("tactical computer"). Aim help is a lead marker (ST-078). Also decided: braking boost (ST-066), shots leave the arena instead of bouncing (ST-067, reworks ST-039), ramming yes (ST-033), photon laser default (ST-034). The alternative control modes (auto-aim + auto-shoot, targeting trance) are **parked** at the end of the backlog.

### ST-075 Pilot view: per-corner retarded time (Terrell rotation)
- status: ready
- note (owner 2026-11-12): unparked into M7. The spec dates from 2026-11-03: check every file and symbol it names against the current code (pilot view, `PilotViewProjector`, `LightHistorySystem`) before starting, and adapt it.
- size: S
- agent: kotlin-dev
- depends: ST-074
- files: `system/RenderSystem.kt` (or the pilot-view helper file from ST-074); tests
- goal: Light from the near and far corners of an object leaves at different times, which makes fast objects look rotated and skewed (Terrell–Penrose, research E8/E9). ST-074 uses the centre's retarded time for all 4 corners. This refines each corner with one Newton step, cheap and exact enough at our sizes.
- spec:
  - With the centre solution `(t_c, x_c, v_c)` and corner offset `q` (rotated and contracted at `t_c`), look back `s` from `t_c`: `p(s) = x_c + q − v_c·s` and `h(s) = |p(s) − o| − c·(now − t_c + s)`. Then `h(0) = |x_c + q − o| − c·(now − t_c)` and `h'(s) = −r̂·v_c − c`, so one Newton step gives `s = h(0) / (c + r̂·v_c)` with `r̂ = normalize(x_c + q − o)`. The corner position is `p(s)`, then boost as in ST-074. Ignore rotation during `s` (FAKE, tiny).
  - Pure helper `fun cornerRetarded(xc, yc, qx, qy, vcx, vcy, ox, oy, lookback: Float, c: Float, out: Vector2)`, where `lookback = now − t_c`.
- acceptance:
  - [ ] Test: for a static object, `cornerRetarded` returns `x_c + q` exactly
  - [ ] Test: for an object moving at (40, 0), 30 u away perpendicular to the motion, with q = (±2, 0), the result satisfies the corner light-cone equation `|p − o| = c·(lookback + s)` within 1e-2 u (one Newton step)
  - [ ] `./gradlew core:test` passes
- playtest: Fast asteroids crossing close by look slightly skewed. No flicker or corner jitter.

### ST-077 Pilot view: starfield, field band and shimmer through the boost
- status: ready
- note (owner 2026-11-12): unparked into M7. The spec dates from 2026-11-03: check every file and symbol it names against the current code (pilot view, `PilotViewProjector`, `LightHistorySystem`) before starting, and adapt it.
- size: M
- agent: kotlin-dev
- depends: ST-043, ST-074
- files: `render/Starfield.kt`, `render/FieldShimmerRenderer.kt`, `assets/shaders/field_shimmer.vert`, `system/RenderSystem.kt`; tests
- goal: Bring the backdrop and the arena edge into the pilot frame, so the whole view is consistent: the sky crowds forward (E9 aberration on the celestial sphere), and the arena band bends.
- spec:
  - Starfield in pilot view: treat the stars as a shell at radius `ρ` around the player. For each star within the view half-diagonal of the player, take its drawn offset `d`, compute the apparent direction `normalize(apparentOffset(d, β))`, and draw it at `o + that·|d|` (radius kept, angle aberrated). ST-043's Doppler `D` keeps using the arena-frame `ŝ = d̂` (that is the correct source direction for `D = γ(1 + β·ŝ)`). Pull the per-star mapping into a pure `fun aberrateStarOffset(dx, dy, betaX, betaY, out)`.
  - Field band and shimmer: the arena is static, so its retarded position equals its true one; only the boost applies. In `FieldShimmerRenderer.drawStrips`, subdivide each of the 4 strips into `PILOT_BAND_SEGMENTS = 64` quads along its length, and map every vertex with `o + apparentOffset(p − o, β)` when pilot view is on. Pass the **arena-frame** position in the texcoords (`u, v` = W x, y), and in `field_shimmer.vert` set `v_world = a_texCoord0` (the fragment shader never samples `v_texCoords`, so the field, the ripples and the band edges are still computed in the arena frame). In world view keep the texcoords equal to the positions, so the output is unchanged.
  - `FieldShimmerRenderer.render` gets `β`, `o` and the pilot flag.
- acceptance:
  - [ ] Test: `aberrateStarOffset` keeps `|d|`, is the identity at β = 0, and maps (0, 10) at β = (0.9, 0) to the angle 25.84°
  - [ ] Test: the strip builder (extract it into a pure function filling a `FloatArray`) emits 64 quads per side, with texcoords equal to the arena-frame positions and vertex positions equal to the boosted ones
  - [ ] Screenshot (harness, `--seed=42 --thrust --frames=150`) viewed by the agent: stars crowd toward the direction of motion and the band bends near the edges
  - [ ] `./gradlew core:test` passes
- playtest: Near c the sky shrinks into a bright forward cone and the arena wall bulges. Shimmer ripples still appear where shots cross the band.

---

## M4 Relativistic view

The pilot view itself (light delay E8, aberration E9, pilot-frame aiming, lead markers) was pulled forward into M3 by the 2026-11-02 interview: ST-070 to ST-078. M4 holds the follow-ups.
- result: `RelativitySettings.pilotHullTransform` (pilotView && relativityEnabled && pilotAberration) now gates both the drawn player hull in `PilotView.project()` and the `PlayerInputSystem` cursor mapping.

### ST-079 Pilot view: ghosts of removed entities until their light runs out
- status: ready
- note (owner 2026-11-12): unparked into M7. The spec dates from 2026-11-03: check every file and symbol it names against the current code (pilot view, `PilotViewProjector`, `LightHistorySystem`) before starting, and adapt it.
- size: S
- agent: kotlin-dev
- depends: ST-074
- files: `system/LightHistorySystem.kt`, `system/RenderSystem.kt`; tests in `system/LightHistorySystemTest.kt`
- goal: In pilot view a destroyed asteroid must stay visible until the light of its destruction reaches the pilot, or you see things vanish before the hit "happens" (research E8). Its fragments already stay invisible until their light arrives (ST-070, non-eternal histories).
- spec:
  - `LightHistorySystem.entityRemoved`: besides `removedAt`, store a `GhostInfo` (pooled; `region: TextureRegion`, `width`, `height`, `originX`, `originY`, `color` packed float), copied from the entity's `SpriteComponent` sprite, keyed by id. Free it when the history is pruned.
  - Expose `fun forEachGhost(action: (id: Int, history: LightHistory, ghost: GhostInfo, removedAt: Float) -> Unit)` without allocating (an inline function over the `IntMap`).
  - `RenderSystem` pilot path: after the live sprites, for each ghost `retarded(o, now)`; if it returns true and `state.t < removedAt`, draw it exactly like a live entity (same corner and boost code, ST-075 corners if present). Otherwise skip it.
  - In world view, ghosts are never drawn.
- acceptance:
  - [ ] Test: an asteroid 30 u from a resting observer, removed at `t = 1`: at `now = 1.3` the ghost is visible (`t_ret = 0.8 < 1`), at `now = 1.6` it isn't
  - [ ] Test: the ghost's `GhostInfo` is freed when the history is pruned
  - [ ] `./gradlew core:test` passes
- playtest: Shoot a distant asteroid in pilot view: the shot reaches it, and it breaks a moment later, as seen. No asteroid pops out of existence early.

### ST-080 Searchlight k = 2 in pilot view, with a brightness floor
- status: ready
- note (owner 2026-11-12): unparked into M7. The spec dates from 2026-11-03: check every file and symbol it names against the current code (pilot view, `PilotViewProjector`, `LightHistorySystem`) before starting, and adapt it.
- note (owner 2026-11-12): no outlines. The target rings and the tactical overlay already mark dim targets at their true position. The 25 % floor may be lowered by the ST-185 audit (its Q3).
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

### ST-182 Auto-aim for dumb rockets
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-181
- files: create `weapon/RocketIntercept.kt`; edit `input/AutoAimStrategy.kt`, `system/TacticalOverlaySystem.kt` (intercept marker for the active weapon); tests
- refs: `physics/PhotonAim.kt` (`trueLeadDirection`), `weapon/LockOnSteering.kt` (`lockOnLeadDirection`), `architecture.md` row 12c (ST-125 intercept markers)
- layer: assist
- goal: When the player switches to rockets, the auto-aim must aim them. A rocket accelerates from the ship's velocity, so neither the photon lead nor a constant-speed lead hits; the computer solves the intercept for the real rocket flight, using the true target state (computer targeting, physics-lies row 34).
- spec:
  - Pure, allocation-free `fun rocketInterceptDir(shipX, shipY, shipUx, shipUy, tx, ty, tvx, tvy, properAccel, burn, c, maxTime, out: Vector2): Float` returning the hit time (NaN when there is none). The rocket path for an aim direction is integrated with the same proper-velocity update as `RelativisticDynamics` at the physics step `dt` (no literal; pass it in). Solve by fixed-point iteration on the hit time (start from the straight-line time at the burnout speed, aim at the target extrapolated to that time, re-measure the rocket's time to the target's distance), at most `ROCKET_INTERCEPT_ITERATIONS` iterations, stop below a tolerance derived from the rocket size.
  - `AutoAimStrategy`: when the active weapon is `ROCKET`, use `rocketInterceptDir` instead of `trueLeadDirection`, and only set `doAttack` when a solution exists within the rocket lifetime.
  - The ST-125 intercept marker uses the active weapon's solver, so with rockets the ring shows where the rockets will meet the target.
- acceptance:
  - [ ] Test: static target straight ahead, ship at rest: the direction points at the target
  - [ ] Integration test (real `World`, physics + `RelativisticDynamics`): ship at β = 0.5 along +x, an asteroid 30 u away along +y moving (5, 0): a rocket fired along `rocketInterceptDir` hits it
  - [ ] Test: a target faster than the rocket that runs away returns NaN and no rocket is fired
  - [ ] `./gradlew test` passes
- playtest: With rockets selected, auto-aim salvos hit moving asteroids; the intercept ring sits where the rockets meet the target.

### ST-183 Weapon unlock progression
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-180, ST-181
- files: `GameState.kt` (unlocked weapons), `component/WeaponLoadoutComponent.kt` (slots from the unlocked set), `provider/PlayerEntityProvider.kt`, `screen/LevelClearedScreen.kt` (pick panel), `save/RunSave.kt`, `scenario/Scenario.kt`, `ui/Hud.kt` (slot strip), `tool/` balance runner, `assets/i18n/messages.properties`; tests; `game-design.md` §3
- refs: `architecture.md` "Screens"
- layer: infra
- goal: Replace the shop with a simple unlock (owner 2026-11-12): level 1 = laser only; after level 1 the cleared screen offers dumb rockets or the lock-on shot; after level 2 the player gets the other one; a quick start at level 5 starts with all weapons.
- questions:
* Q1: Slot keys: fixed per weapon (1 = laser, 2 = rockets, 3 = lock-on; a locked slot is empty) (Rec), or in unlock order?
  * A:locked slot is empty
* Q2: After level 2 the second weapon is given automatically, with a "New weapon: X" line on the cleared screen (Rec)?
  * A:Rockets and lock-on is proposed, user can choose either
* Q3: Scenarios: all weapons unlocked (Rec), or each scenario lists its own?
  * A:All
* Q4: Does a run end? (a) endless levels until death, as now (Rec, smallest scope); (b) a final level (e.g. 10) with a victory screen.
  * A:A
* Q5: (from your ST-180 answer "2 options, just one pick per level") With 3 weapons, a pick exists only after level 1 (rockets or lock-on). What do the 2 options offer from level 2 on? (a) after L2 the other weapon is given automatically and later levels have no pick (Rec, matches Q2); (b) a pick on every level from small free run bonuses (e.g. +1 lock-on ammo or +1 rocket per pod vs. +25 % max health), which needs a list of bonuses; (c) something else.
  * A:A
- spec (Rec answers): `GameState.unlockedWeapons` (laser at start; all for a start at `QUICK_START_LEVEL` or above and in scenarios). The player's loadout fills slot 1 laser, slot 2 rockets, slot 3 lock-on when unlocked, else null. `LevelClearedScreen` after level 1 shows two buttons with a one-line description each; "Next level" is enabled only after a pick. After level 2 it unlocks the remaining weapon and says so. `RunSave` stores the unlocked set (bump the version). The HUD shows the three slots (locked ones dim) with the active one highlighted and lock-on ammo / pod count. Balance bots get the weapons a player would have at that level.
- note (owner answers 2026-11-12): Q1 fixed slots, a locked slot is empty; Q2 "Rockets and lock-on is proposed, user can choose either" = the level 1 pick, and Q5 (a) confirms the other weapon is given after level 2 with no later picks; Q3 all weapons in scenarios; Q4 (a) endless.
- acceptance:
  - [ ] Test: a new run has only the laser; picking rockets after L1 gives laser + rockets; after L2 all three
  - [ ] Test: a start at `QUICK_START_LEVEL` and every scenario have all three
  - [ ] Test: the unlocked set survives a save/restore
  - [ ] Test: selecting a locked slot does nothing
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Clear level 1, pick lock-on, play level 2 with keys 1 and 3; clear it and get the rockets.

### ST-185 Physics fidelity audit before release
- status: needs-user
- size: M
- agent: physics-researcher
- depends: —
- files: `docs/design/physics-lies.md`, create `docs/research/release-fidelity-audit.md`, `docs/backlog.md` (follow-up cards)
- refs: `docs/design/physics-lies.md` (all rows), `relativity-effects.md` §4
- layer: sim
- goal: Owner 2026-11-12: "I want all the physics to be as close as possible." Go through every FAKE/APPROX row and decide per row: remove now (cost and risk), keep because the engine forces it (Box2D, float, perf on web), or keep for gameplay (owner decides). Turn the "remove now" rows into small follow-up cards.
- questions:
* Q1: Braking boost (row 7: engines push up to 3× harder against your motion). Remove it for realism (Rec: yes; it makes braking slower, so the balance pass ST-189 rechecks levels)?
  * A:
* Q2: Fuel / reaction mass (row 6): add a rocket equation with fuel for the ship? Rec: no for release (big gameplay change); keep the row.
  * A:
* Q3: Length-contraction floor at 25 % (row 31) and the clamped starfield Doppler/brightness (row 33): move toward the true values with the floors only as a safety (Rec: floor 10 %, brightness D³ clamped wider)?
  * A:
* Q4: Source-source gravity at 0.1 (row 14): try physical coupling with stable initial orbits (the parking-lot idea) for release? Rec: research only, card only if cheap.
  * A:
* Q5: Laser damage clamp [0.25, 4] (row 36): widen or remove? Rec: widen to [0.1, 10].
  * A:
- spec: For each row: tag (keep-engine / keep-gameplay / remove), cost estimate, web performance risk, and the exact formula that replaces the lie. Recommend the 3-6 most visible wins. Add follow-up cards (`kotlin-dev` or `junior-dev`, size S/M) to M7 after ST-080, `needs-user` where the owner's answer above is not enough.
- acceptance:
  - [ ] `release-fidelity-audit.md` has a table covering every physics-lies row
  - [ ] Follow-up cards are in the backlog and in the implementation order
  - [ ] No code changes in this card
- playtest: —

### ST-186 Lock-on shot: booster, sustainer and side-thruster turning
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: ST-183
- files: `weapon/LockOnConstants.kt`, `weapon/LockOnSteering.kt`, `system/LockOnSteeringSystem.kt`, `component/LockOnComponent.kt`, `provider/LockOnProvider.kt`, `docs/design/physics-lies.md` rows 46-47; tests
- refs: physics-lies rows 46-48, `architecture.md` "Physics loop details", `ShipRotationController.kt` (PD turning), commit ST-164
- layer: sim
- goal: Owner 2026-11-12: lock-ons need to be more dynamic and fly like the real thing. After launch a booster gives a big speed boost for a short time; then the regular (sustainer) engine takes over. The shot turns with side thrusters (a torque, finite angular acceleration), not by setting its angle, and the main engines only push along the hull, so the path curves because the hull turns.
- questions:
* Q1: Booster (Rec): proper acceleration 8 × `ENGINE_PROPER_ACCEL` for 0.3 s of the shot's clock, along the launch heading, no steering during the boost. OK?
  * A:
* Q2: Sustainer (Rec): the current 3 × engine accel for 2 s, then coast; the sustainer throttles down when the hull is more than 30° off the guidance direction (so it does not push the wrong way). Or should it burn until it hits (longer chase)?
  * A:
* Q3: Guidance (Rec): proportional navigation (N = 3) on the target's true state, replacing the current "lead point" solver; it is what real missiles use and curves more naturally. OK?
  * A:
* Q4: Side thrusters (Rec): angular acceleration limit 20 rad/s², max turn rate kept at `LOCK_ON_TURN_RATE`, turning by a PD controller through Box2D torque. OK?
  * A:
- spec (Rec answers): `LockOnComponent` gets a `phase` (BOOST, SUSTAIN, COAST) and the burn timers (proper time, ticked by `dt/γ`). Thrust is always along the body angle (`thrustAccelWorld` with the hull heading, never the guidance direction). Turning: the guidance gives a desired heading; a PD controller applies `body.applyTorque` limited by `LOCK_ON_RCS_ANGULAR_ACCEL · I`; no `setTransform`. Expose the current RCS command (−1..1) and phase on the component for ST-187. Update `lockOnLifetime` for the new burn and physics-lies rows 46-47.
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
- spec: An additive plume quad behind the body, length and alpha from the phase (BOOST large, SUSTAIN medium, COAST none); two RCS puff quads at the nose sides, alpha from the RCS command of ST-186 (left/right by its sign). Plumes use the entity's displayed transform (retarded in pilot view) and the same Doppler tint as the body. No allocation per frame; pure helper `plumeGeometry(phase, rcs, out)` tested.
- acceptance:
  - [ ] Test: `plumeGeometry` gives the BOOST > SUSTAIN > COAST (zero) lengths and the RCS puff on the correct side for a positive and a negative command
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

