# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `agents/planner.md`. **Never renumber cards.** The next free ID is **ST-191**.

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
58. ST-180 Cut the shop, credits and upgrades
59. ST-181 Dumb-rocket pod weapon
60. ST-182 Auto-aim for dumb rockets
61. ST-183 Weapon unlock progression
62. ST-185 Physics fidelity audit before release
63. ST-184 Pilot view made playable by the targeting computer
64. ST-186 Lock-on shot: booster, sustainer and side-thruster turning
65. ST-187 Lock-on shot: visible booster, sustainer and RCS plumes
66. ST-189 Rebalance after the weapon changes
67. ST-188 Simplify the UI (owner decisions)
68. ST-190 Release prep: merge AI-slop to master

Parked:
51. ST-091 Targeting-trance scheme
Pilot-perspective polish (owner 2026-11-03: parked while the outside view is the default; revive when the pilot perspective mode is worked on):
52. ST-078 Lead markers
53. ST-075 Pilot view: Terrell rotation
54. ST-077 Pilot view: starfield, field band and shimmer
55. ST-079 Ghosts of removed entities
56. ST-080 Searchlight k = 2 in pilot view

---

## M3 Relativity MVP

- note: Source is `docs/research/relativity-effects.md` §2, §4 Tier 1 (E1–E7) and §7 items 7–15. **Differences from `docs/design/game-design.md` §6:** the research promotes relativistic velocity addition for projectiles (GDD "Maybe") to MVP because Galilean addition breaks the c cap. It also moves length contraction (GDD "Next") into MVP in world view, and adds γ-mass collisions, speed zoom and a mild searchlight (k = 1) to MVP. The HUD shows ship time τ and world time t, which answers GDD §11 Q2 with "both". The GDD parking-lot idea "fast = bullet time" is physically backwards (research E2), so it's not planned. The GDD isn't edited here. ST-049 reconciles it once the owner has answered the questions below. **Feature flag:** `RELATIVITY_ENABLED` (ST-037). When false, providers don't attach `RelativisticComponent` and the Newtonian M2 path runs unchanged. (Superseded by ST-056: the flag becomes a runtime setting, and providers always attach the component.) **Owner questions** ST-030 … ST-034 are `needs-user`. Every other card is planned around their recommended answers and isn't blocked by them.
- note (2026-11-01, owner playtest after M2 + ST-037): "Feels a bit chaotic, maybe the arena should be bigger" → ST-030 answered (c), and ST-046 now also calms a0 and the asteroid spawn. "Relativity debug options on the side" → ST-056 (runtime `RelativitySettings`), ST-057 (plumbing), ST-059 (panel), ST-060 (vector overlay). Controls are undecided → ST-058/ST-061/ST-062 make schemes (a)/(b)/(c) switchable at runtime, and ST-063 asks for the default. γ: no gameplay cap (ST-032), safety bound 50 (ST-055). **From ST-056 on**, runtime code reads c, a0, the γ cap, field params and relativity/field on/off from the DI singleton `RelativitySettings`, not the consts (the consts are its defaults). Cards written earlier (ST-038 … ST-048) that say `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE` or `RELATIVITY_ENABLED` mean the corresponding setting once ST-056 is done.

- note (2026-11-02, owner interview, game-design.md §11): **The game is perceived from the pilot's frame.** The game clock is the pilot's proper time (research E21): the arena advances `γ·dt_real` per frame, capped at about 10× (declared FAKE past the cap, shown on the HUD), and the player's guns and turning run on real time (ST-068, ST-069, ST-040). **Pilot view is the main rendering** and is pulled forward from M4 into this milestone (E8 light delay + E9 aberration: ST-070 … ST-078, default on). The minimap stays true arena positions ("tactical computer"). Aim help is a lead marker (ST-078). Also decided: braking boost (ST-066), shots leave the arena instead of bouncing (ST-067, reworks ST-039), ramming yes (ST-033), photon laser default (ST-034). The alternative control modes (auto-aim + auto-shoot, targeting trance) are **parked** at the end of the backlog.

### ST-075 Pilot view: per-corner retarded time (Terrell rotation)
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
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
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
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

### ST-078 Lead markers: where to aim so a photon hits
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
- size: M
- agent: kotlin-dev
- depends: ST-039, ST-071, ST-073, ST-074, ST-076
- files: create `system/LeadMarkerSystem.kt`; edit `physics/PhotonAim.kt`, `input/ControlSettings.kt`, `ui/DebugPanel.kt`, `screen/PlayScreen.kt`; tests
- goal: Near c, aiming at what you see misses, because you see the past and your own shots aberrate. Mark, for nearby targets, the cursor direction that makes a photon hit. It uses honest fire control: only light that has reached the pilot (the retarded state), extrapolated at constant velocity. It never peeks at true positions.
- spec:
  - Add to `PhotonAim.kt`: `fun leadDirection(sx, sy, betaX, betaY, ret: RetardedState, now: Float, c: Float, out: Vector2): Boolean`. Extrapolate `x_e = ret.x + ret.vx·(now − ret.t)` (same for y); `photonInterceptDir(x_e − s, ret.v, c, nW)`; `out = muzzleDirForWorldDir(β, nW)` (the shooter-frame direction n̂'). Return false when the intercept fails.
  - `LeadMarkerSystem(lightHistory, observer, clock, settings, controlSettings, shapeRenderer, camera)`, registered right after `RenderSystem`. For non-player entities with `HealthComponent` and a history, take the nearest `LEAD_MARKER_MAX = 8` within `LEAD_MARKER_RANGE = 60` u (by retarded distance). For each, `leadDirection`. Screen direction: `n̂'` if pilot view is on or the active scheme aims a turret (`TWIN_STICK`, `AUTO_AIM`); otherwise (world view + mouse-hull) the hull W angle `pilotHullToWorld(n̂', β)`, because the hull fallback fires along `worldHullToPilot(hull)` (ST-076). Draw a ring (radius 0.6 u, line width 1) at `o + dir·dist`, with `dist` = the target's on-screen distance from `o` (its apparent distance in pilot view, its true one in world view), and a thin line from the target's drawn position to the ring. Colour: faction-tinted, alpha 0.7.
  - `ControlSettings.leadMarkers: Boolean = true`. Debug checkbox "lead markers".
- acceptance:
  - [ ] Test: `leadDirection` at β = 0 for a static target returns r̂
  - [ ] Integration test (real `World` with `PhysicsSystem`, `RelativisticDynamics`, `ProjectileSweepSystem` and `LightHistorySystem`): a ship moving at β = 0.8 along +x, an asteroid 20 u away along +y moving (10, 0), histories warmed up for 1 s; set `aimAngleRad = atan2(leadDirection(...))` and fire. The photon's sweep hits the asteroid within 1 s
  - [ ] `./gradlew core:test` passes
- playtest: At speed, aim at the ring instead of the asteroid and the shots hit. Check whether 8 markers clutter the screen.
- note: (2026-11-03) ST-125 is the tactical-computer variant using the true state (`trueLeadDirection`). When this honest variant is unparked, share its drawing and target selection with `TacticalOverlaySystem` instead of a separate `LeadMarkerSystem`.
- note: (2026-11-03, from ST-062) when unparked, also highlight the auto-aim target in the AUTO_AIM scheme; `leadDirection` already exists in `physics/PhotonAim.kt`.

---

## M4 Relativistic view

The pilot view itself (light delay E8, aberration E9, pilot-frame aiming, lead markers) was pulled forward into M3 by the 2026-11-02 interview: ST-070 to ST-078. M4 holds the follow-ups.
- result: `RelativitySettings.pilotHullTransform` (pilotView && relativityEnabled && pilotAberration) now gates both the drawn player hull in `PilotView.project()` and the `PlayerInputSystem` cursor mapping.

### ST-079 Pilot view: ghosts of removed entities until their light runs out
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
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

### ST-080 Searchlight k = 2 in pilot view, with a brightness floor and outlines
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
- note (2026-11-03): the tactical computer overlay (ST-123) already outlines every body at its true position. When unparked, decide whether the dim-entity outlines here are still needed.
- size: S
- agent: kotlin-dev
- depends: ST-043, ST-045, ST-074
- files: `render/RelativityVisuals.kt`, `render/Starfield.kt`, `system/RenderSystem.kt`; tests
- goal: Full relativistic beaming in the pilot view (research E10, k = 2): things ahead blaze and things behind dim. Q14 is planned around the recommendation (b), so behind you nothing goes below 25 %, and dim hostiles and asteroids get an outline.
- spec:
  - `const val BRIGHTNESS_EXPONENT_PILOT = 2f`. Starfield and sprite tint use `B = clamp(D^k, B_MIN, B_MAX)` with `k = BRIGHTNESS_EXPONENT_PILOT` in pilot view and `BRIGHTNESS_EXPONENT_MVP` in world view. Pull it into `fun beamBrightness(d: Float, pilotView: Boolean): Float`.
  - Outlines: for non-player entities with `HealthComponent`, in pilot view, when `B < OUTLINE_B_THRESHOLD = 0.6f`, draw a `ShapeRenderer` circle (line, 24 segments) at the entity's apparent centre with radius `boundingRadius·1.1`, in the faction colour at alpha `0.5·(1 − B/OUTLINE_B_THRESHOLD)`. One `ShapeRenderer.begin/end` per frame, after the sprite batch ends.
- acceptance:
  - [ ] Tests: `beamBrightness(2f, true) == 3f` (clamped from 4); `beamBrightness(0.3f, true) == 0.25f` (floor); `beamBrightness(0.6f, false) == 0.6f`; `beamBrightness(1f, true) == 1f`
  - [ ] `./gradlew core:test` passes
- playtest: At speed the view ahead is bright, and asteroids behind you are dim but outlined. Check whether the outline is too strong.

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

- note (owner 2026-11-12): "I want soon to wrap this project up and ship it to main. Simplify scope. I want all the physics to be as close as possible." Decisions: the roguelike is cut to a weapon unlock (start with lasers; after level 1 pick dumb rockets or the lock-on shot; after level 2 get the other one; a quick start at level 5 gets all weapons). Mouse control schemes go. The UI gets simplified, but only after the owner's decisions (ST-188). Physics and graphics have priority; the game stays as realistic as possible. With the targeting computer the game should be easier, also in pilot view (ST-184). The lock-on improvements (ST-186, ST-187) come after the rocket and progression work.
- note: every card with questions is `needs-user` until the owner fills the `A:` lines; the specs are written around the recommended answers (marked "Rec").

### ST-180 Cut the shop, credits and upgrades
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: —
- files: delete `shop/Shop.kt`, `shop/Upgrades.kt` (keep any constant still used elsewhere by moving it); edit `GameState.kt`, `save/RunSave.kt`, `screen/LevelClearedScreen.kt`, `screen/PlayScreen.kt`, `screen/MainMenuScreen.kt`, `ui/Hud.kt`, `system/HudSystem.kt`, `system/DeathSystem.kt` (credit awards), `provider/PlayerEntityProvider.kt` (start health, engine factor), `component/ShipHandlingComponent.kt`, `assets/i18n/messages.properties`, `tool/` balance runner; tests; docs `game-design.md` §3, §10
- refs: `architecture.md` "Screens", `balance.md` (the runner's shop handling)
- layer: infra
- goal: The roguelike is cut (owner 2026-11-12). Credits, the shop, rerolls and the engine/hull upgrades go away; the cleared screen keeps only the result and "Next level" until ST-183 adds the weapon pick.
- questions:
* Q1: Remove the engine and hull upgrades completely, so every run flies the same ship (Rec), or keep a fixed small bonus per level?
  * A:Remove them. Shop will have 2 options, no money. Just one pick per level.
* Q2: Keep the run save (Continue from the main menu at the next level)? Rec: yes, it then stores the level, the run physics and (ST-183) the unlocked weapons; `RUN_SAVE_VERSION` bumps, so old saves are ignored.
  * A:Yes
* Q3: The quick start at level 5 stays on the New game page (it gives all weapons, ST-183)? Rec: yes.
  * A:Yes
- spec (Rec answers): Remove `GameState.credits`, `runSeed` use for the shop, `pendingShop`, `quickStart` shop logic and `upgrades`; the player starts every level with `PLAYER_START_HEALTH` and engine factor 1. `RunSave` keeps `nextLevel` + run physics; bump `RUN_SAVE_VERSION`. Drop the HUD credits line, the cleared-screen shop section and all `shop_*` / `credits_*` i18n keys. The balance runner stops buying upgrades.
- acceptance:
  - [ ] `grep -ri "credits\|ShopItem\|RunUpgrades" core/src tool/src` finds nothing
  - [ ] Test: a save written with the new version restores the level and run physics; a version-1 save is ignored
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Clear level 1: the cleared screen shows the result and "Next level" only; Continue from the menu resumes at level 2.

### ST-181 Dumb-rocket pod weapon
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: —
- files: `weapon/WeaponType.kt` (+`ROCKET`), create `weapon/RocketConstants.kt`, `component/RocketComponent.kt`, `provider/RocketProvider.kt`; edit `system/WeaponSystem.kt`, `component/WeaponLoadoutComponent.kt` (pod state as scalar fields, web rule), `system/ContactSystem.kt`, `system/RenderSystem.kt` (plume), `ui/Hud.kt` (pod / reload readout), `raw_assets/` sprite + `tool:packTextures`, `docs/design/physics-lies.md`; tests
- refs: `architecture.md` "Physics loop details", `LockOnProvider.kt` / `LockOnSteeringSystem.kt` (copy the relativistic body setup and the engine burn), physics-lies rows 46-48
- layer: sim
- goal: A new unguided weapon: a pod of 5 dumb rockets. A rocket leaves the ship with the ship's velocity, then its motor burns along its fixed launch heading (constant proper acceleration, so it never reaches c) and it coasts after burnout. Holding fire empties the pod quickly; then the pod reloads.
- questions:
* Q1: Which way does a rocket launch? (a) from a gimballed pod along the computer's aim direction, like the laser turret, so auto-aim works without turning the ship (Rec); (b) only along the hull; the auto-aim then fires only when the hull points at the intercept.
  * A:A
* Q2: Pod numbers (Rec, all derived from constants): 5 rockets, 0.15 s between rockets, 4 s reload of the whole pod (ship proper time); no ammo limit beyond the reload. OK or other values?
  * A:Ok
* Q3: Motor (Rec): proper acceleration 2 × `ENGINE_PROPER_ACCEL` for 1.5 s of the rocket's own clock (burnout β ≈ 0.78 at defaults), lifetime = burn + a coast across half the arena (like `lockOnLifetime`). OK?
  * A:Lets start with proposed. Lifetime can be detonate at arena boundry
* Q4: Damage per rocket (Rec): `ENEMY_HEALTH / 2` on a direct hit (two rockets kill a ship; a large asteroid at 150 HP takes three). Add a blast radius (more realistic warhead, splash on nearby bodies with an impulse) or direct hit only (Rec: direct hit for now)?
  * A:Recommended
* Q5: Can the laser shoot rockets down like lock-on shots (1 HP)? Rec: yes, same rule for enemies later.
  * A:Yes
- spec (Rec answers):
  - `RocketConstants.kt`: `ROCKET_POD_SIZE = 5`, `ROCKET_SALVO_INTERVAL`, `ROCKET_RELOAD_SECONDS`, `ROCKET_ACCEL_FACTOR = 2f` (× `settings.engineProperAccel` at runtime), `ROCKET_BURN_SECONDS`, `ROCKET_DAMAGE = ENEMY_HEALTH / 2`, sizes and density like the lock-on shot; `rocketLifetime(c, a0, arenaW, arenaH)` mirrors `lockOnLifetime`. KDoc each derivation.
  - `RocketProvider`: a small dynamic Box2D box with `RelativisticComponent` whose proper velocity `u` is the ship's `u` (the rocket starts at rest relative to the ship), placed just outside the hull along the launch direction, faction bits like the player's projectiles, `RocketComponent(burnLeft)`.
  - Motor: a `PhysicsStepListener` (extend `LockOnSteeringSystem` or a sibling `RocketMotorSystem`) adds `thrustAccelWorld(properAccel, heading, v, γ)` while `burnLeft > 0`, and ticks `burnLeft` by `dt/γ`. The heading never changes.
  - Pod: in the loadout, `rocketsInPod`, `rocketSalvoCooldown`, `rocketReloadLeft` (scalar fields). Fire while `rocketsInPod > 0` and the salvo cooldown is 0; at 0 rockets start the reload; a full reload refills the pod. All on ship proper time, like the laser cooldown (ST-040).
  - Contact: a hit deals `ROCKET_DAMAGE` and removes the rocket (via `RemoveComponent`); pilot view draws the rocket from light history like any massive body.
  - Physics-lies: add a row for the rocket motor (no fuel use or reaction mass, like row 47).
- acceptance:
  - [ ] Test: holding fire launches exactly 5 rockets `ROCKET_SALVO_INTERVAL` apart, then none until `ROCKET_RELOAD_SECONDS` have passed, then 5 more
  - [ ] Integration test (real `World`, `PhysicsSystem`, `RelativisticDynamics`): a rocket launched from a ship moving at β = 0.5 keeps its launch heading, speeds up, stays below c and has the burnout speed from the formula within 2 %
  - [ ] Test: a rocket hit removes `ROCKET_DAMAGE` health and the rocket
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Select the rockets (slot key) and hold fire: five rockets ripple out, accelerate visibly in straight lines and the HUD shows the reload.

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
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: ST-180, ST-181
- files: `GameState.kt` (unlocked weapons), `component/WeaponLoadoutComponent.kt` (slots from the unlocked set), `provider/PlayerEntityProvider.kt`, `screen/LevelClearedScreen.kt` (pick panel), `save/RunSave.kt`, `scenario/Scenario.kt`, `ui/Hud.kt` (slot strip), `tool/` balance runner, `assets/i18n/messages.properties`; tests; `game-design.md` §3
- refs: `architecture.md` "Screens"
- layer: infra
- goal: Replace the shop with a simple unlock (owner 2026-11-12): level 1 = laser only; after level 1 the cleared screen offers dumb rockets or the lock-on shot; after level 2 the player gets the other one; a quick start at level 5 starts with all weapons.
- questions:
* Q1: Slot keys: fixed per weapon (1 = laser, 2 = rockets, 3 = lock-on; a locked slot is empty) (Rec), or in unlock order?
  * A:
* Q2: After level 2 the second weapon is given automatically, with a "New weapon: X" line on the cleared screen (Rec)?
  * A:
* Q3: Scenarios: all weapons unlocked (Rec), or each scenario lists its own?
  * A:
* Q4: Does a run end? (a) endless levels until death, as now (Rec, smallest scope); (b) a final level (e.g. 10) with a victory screen.
  * A:
- spec (Rec answers): `GameState.unlockedWeapons` (laser at start; all for a start at `QUICK_START_LEVEL` or above and in scenarios). The player's loadout fills slot 1 laser, slot 2 rockets, slot 3 lock-on when unlocked, else null. `LevelClearedScreen` after level 1 shows two buttons with a one-line description each; "Next level" is enabled only after a pick. After level 2 it unlocks the remaining weapon and says so. `RunSave` stores the unlocked set (bump the version). The HUD shows the three slots (locked ones dim) with the active one highlighted and lock-on ammo / pod count. Balance bots get the weapons a player would have at that level.
- acceptance:
  - [ ] Test: a new run has only the laser; picking rockets after L1 gives laser + rockets; after L2 all three
  - [ ] Test: a start at `QUICK_START_LEVEL` and every scenario have all three
  - [ ] Test: the unlocked set survives a save/restore
  - [ ] Test: selecting a locked slot does nothing
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Clear level 1, pick lock-on, play level 2 with keys 1 and 3; clear it and get the rockets.

### ST-184 Pilot view made playable by the targeting computer
- status: needs-user
- size: M
- agent: kotlin-dev
- depends: ST-179, ST-182
- files: `system/TacticalOverlaySystem.kt`, `render/TacticalOverlay.kt`, `settings/TacticalSettings.kt`, `settings/MenuSettings.kt`; tests; `main-menu.md` "Perspective"
- refs: `architecture.md` row 12c, `relativity-effects.md` §E8/E9, physics-lies row 34
- layer: assist
- goal: Owner 2026-11-12: "with targeting computer playing the game should be easier, even in pilot view." The pilot view stays honest (light-delayed, aberrated sprites); the ship's computer adds what the pilot needs to fight in it, so the pilot view becomes a real way to play, not a curiosity.
- questions:
* Q1: What makes pilot view hardest today? Rec assumption: you cannot tell which delayed sprite is which true target, you do not see where your own shots will go, and steering the hull by the cursor feels off when the sprites are aberrated.
  * A:
* Q2: Which assists should the card add? Rec: (a) a faint "light-delay tether" from each target's displayed sprite to its true-position ring; (b) the intercept ring for the active weapon (laser, rockets, lock-on) always on in pilot view; (c) a hull-heading line and a velocity-vector marker (where you are drifting) at the ship; (d) the auto-aim's chosen target highlighted. All toggles under the Ship computer tab.
  * A:
* Q3: Unpark the realistic pilot-view polish cards ST-075 (Terrell rotation), ST-077 (aberrated starfield and field band), ST-079 (ghosts of removed entities) and ST-080 (searchlight k = 2, with floor and outlines) after this card? Rec: yes for 075, 077 and 079; 080 without the outlines, since the target rings already mark things.
  * A:
* Q4: Close ST-078 (honest lead markers from light-delayed data) and ST-091 (targeting trance) as out of scope? Rec: yes; the computer intercept rings replace ST-078, and the trance is a control scheme.
  * A:
* Q5: Make Pilot the default perspective at release once this lands? Rec: no, keep Outside default and offer Pilot as "realistic".
  * A:
- spec (Rec answers): add `TacticalSettings.showLightDelayTether` (default on) and `showFlightMarkers` (default on); in pilot view draw (a) a 1 px line, alpha 0.35, from each target's displayed (retarded, aberrated) position to its true ring, only when they are more than the ring radius apart; (b) the intercept ring for the active weapon's solver; (c) the velocity marker at `o + v·FLIGHT_MARKER_SECONDS`, projected through the pilot boost; (d) a thicker ring on the auto-aim target. Pure helpers for the positions, tested. No physics changes.
- acceptance:
  - [ ] Tests for each pure helper (tether endpoints, velocity marker position at β = 0 and β = 0.8)
  - [ ] Screenshot (`--seed=42 --thrust --frames=150 --pilot-view` or the equivalent flags) viewed by the agent: tethers, rings and markers are visible and not cluttered
  - [ ] `./gradlew test` passes and `scripts/web-check.sh --play` reports OK
- playtest: Fly a level in pilot view at high β with auto-aim: you can tell which sprite is which target and where your shots will land.

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
- spec: For each row: tag (keep-engine / keep-gameplay / remove), cost estimate, web performance risk, and the exact formula that replaces the lie. Recommend the 3-6 most visible wins. Add follow-up cards (`kotlin-dev` or `junior-dev`, size S/M) to M7 after ST-184, `needs-user` where the owner's answer above is not enough.
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

---

## Parked (low priority)

Owner 2026-11-02: control modes for later (game-design §12). Keep them ready but behind all other milestones.

### ST-091 Targeting-trance control scheme (hold LMB: slow-mo aim, release: one shot)
- status: parked
- size: M
- agent: kotlin-dev
- depends: ST-058, ST-069, ST-078
- files: `input/ControlScheme.kt`, create `input/TranceStrategy.kt`; edit `physics/TimeWarp.kt` (`GameClock`), `component/MoveComponent.kt`, `system/PhysicsSystem.kt`, `system/PlayerInputSystem.kt`, `system/LeadMarkerSystem.kt`, `screen/PlayScreen.kt`; tests
- goal: Owner's parked mode (b): holding LMB strongly slows time (a declared FAKE "targeting trance"), shows the lead indicators and turns the ship instantly toward the aim. **Releasing LMB fires a single shot**, followed by a cooldown of at least 1 s (owner 2026-11-02). Single shots fit the owner's wish for fewer asteroids and enemies on the map (performance). Continuous fire while held could be revisited if entity counts grow.
- spec:
  - `ControlScheme.TRANCE("trance")`. `GameClock.slowMo = 1f` (reset to 1). `PlayScreen.render` multiplies the delta passed to `engine.update` by `clock.slowMo` (after the debug time scale), so the pilot and the world slow together.
  - `const val TRANCE_TIME_SCALE = 0.2f`, `const val TRANCE_SHOT_COOLDOWN = 1f` (seconds of the pilot's real time = proper time τ, the clock the player's guns run on, ST-040). KDoc both.
  - `TranceStrategy(clock)` with `cooldownLeft = 0f`: movement as in `MouseHullStrategy`. Each `apply`: `cooldownLeft = max(0, cooldownLeft − dt)`. The trance can only start when `cooldownLeft == 0`, so during the cooldown `slowMo == 1` and the strategy's `dt` is unscaled real time. While `fireHeld` (and off cooldown): `clock.slowMo = TRANCE_TIME_SCALE`, `move.instantTurn = true`, no firing. On the frame LMB is released after a trance: `doAttack = true` for that frame only (exactly one shot), `slowMo = 1`, `instantTurn = false`, `cooldownLeft = max(TRANCE_SHOT_COOLDOWN, weapon cooldown)`. Holding LMB during the cooldown does nothing until it ends, then the trance starts. `onActivate` resets `slowMo`, `instantTurn` and `cooldownLeft`.
  - `MoveComponent.instantTurn = false` (reset false). `PhysicsSystem.applyRotation`: if set and `targetAngleRad != null`, `body.setTransform(body.position, target)` and `angularVelocity = 0` instead of the PD impulse (FAKE).
  - `LeadMarkerSystem` draws the markers while the trance is active, even if `controlSettings.leadMarkers` is off.
- acceptance:
  - [ ] Test: with LMB held the clock's `slowMo == 0.2` and no shot is fired over 1 s; on release exactly one shot fires and `slowMo == 1`
  - [ ] Test: pressing and releasing again 0.5 s after a shot fires nothing and doesn't slow time; LMB held from 0.5 s to 1.5 s enters the trance at 1.0 s and the release at 1.5 s fires the second shot
  - [ ] Test: `instantTurn` sets the body angle to the target in one step with zero angular velocity
  - [ ] Test: switching away from the scheme mid-trance resets `slowMo` to 1
  - [ ] `./gradlew core:test` passes
- playtest: With `--controls=trance`, hold LMB: time crawls, the markers appear and the nose snaps to the cursor; release to fire one shot, then wait a second. Does the 1 s cooldown feel right?

