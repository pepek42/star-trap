# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `agents/planner.md`. **Never renumber cards.** The next free ID is **ST-179**.

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
49k. ST-176 Lock-on shot lifetime from arena size and burnout speed
49l. ST-177 Targeting computer: true-position markers on lock-on shots

Menus & runs (owner 2026-11-07: New game / Scenarios / Settings menu done; save, shop and prebuilds come later):
57e. ST-170 Scenario briefing pauses the game
57f. ST-171 Settings page with tabs

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

### ST-176 Lock-on shot lifetime from arena size and burnout speed
- status: ready
- size: S
- agent: junior-dev
- depends: ST-164, ST-165
- files: `weapon/LockOnConstants.kt`, `provider/LockOnProvider.kt`, `screen/PlayScreen.kt` (pass `mapProvider`), `docs/tech/architecture.md` ("Homing lock-on shots"); test `weapon/LockOnLifetimeTest.kt`
- refs: AGENTS.md "Constants are the single source of truth"; architecture.md "Homing lock-on shots (ST-164)"
- layer: sim
- goal: Owner 2026-11-08 (game-design §12): lock-on shots die too early at `LOCK_ON_LIFETIME = 4f` (a 4 s life covers ~55 u at v ≈ 0.70c, less than the 60 u lock range plus target motion). Replace the literal with a lifetime derived from c, the engine, the burn and the arena size, computed at launch.
- spec:
  - New pure top-level fun in `LockOnConstants.kt`: `lockOnLifetime(c: Float, engineProperAccel: Float, arenaWidth: Float, arenaHeight: Float): Float`. `w = LOCK_ON_ACCEL_FACTOR · engineProperAccel · LOCK_ON_BURN_SECONDS / c` (burnout proper speed in units of c), `beta = max(w / sqrt(1 + w²), LOCK_ON_MIN_COAST_BETA)`, return `LOCK_ON_BURN_SECONDS + LOCK_ON_RANGE_ARENA_FRACTION · max(arenaWidth, arenaHeight) / (beta · c)`.
  - New consts with KDoc: `LOCK_ON_RANGE_ARENA_FRACTION = 0.5f` (coast covers half the arena's long side: ~7.9 s at the defaults, 200 u arena, c = 20, β ≈ 0.70; c cancels against the default engine, which is a multiple of c), `LOCK_ON_MIN_COAST_BETA = 0.25f` (floor so an engine slider at 0 gives a finite lifetime). Delete `LOCK_ON_LIFETIME`; write the default-value derivation in the KDoc instead.
  - `LockOnProvider` gets `mapProvider: MapProvider? = null` (last ctor param; null falls back to `MAP_WIDTH`/`MAP_HEIGHT`, so `HeadlessLevel` and tests keep compiling) and sets `remaining`/`total` from `lockOnLifetime(settings.c, settings.engineProperAccel, w, h)` (scenarios use smaller arenas). `PlayScreen` passes its `mapProvider`.
  - `LOCK_ON_MAX_IN_FLIGHT` stays 4 (at 1 shot/s and 6 ammo it still caps a salvo); update its KDoc and the old "shorter than the arena crossing" rationale: after burnout the shot only coasts in a straight line (no velocity steering), so a long life cannot chase a target.
- acceptance:
  - [ ] Test: at `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `MAP_WIDTH`, `MAP_HEIGHT` the lifetime equals the formula above recomputed in the test from the constants, and exceeds `LOCK_ON_BURN_SECONDS + TARGET_LOCK_RANGE_LIGHT_SECONDS · c / v_burnout`
  - [ ] Test: doubling c together with the engine (engine ∝ c) halves the coast time with the same arena; a half-size arena halves the coast time; `engineProperAccel = 0` gives a finite value from `LOCK_ON_MIN_COAST_BETA`
  - [ ] No `LOCK_ON_LIFETIME` reference left; architecture.md "Homing lock-on shots" states the new rule
  - [ ] `./gradlew core:test` passes
- playtest: Fire a lock-on shot at a distant enemy: it reaches it instead of fading out on the way; a missed shot coasts on for several seconds and then fades.

### ST-177 Targeting computer: true-position markers on lock-on shots
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-164
- files: `system/TacticalOverlaySystem.kt`, `render/TacticalOverlay.kt` (pure geometry + px consts), `settings/TacticalSettings.kt`, `settings/MenuSettings.kt`, `assets/i18n/messages.properties`, `docs/tech/architecture.md`; tests in `render/TacticalOverlayTest.kt`
- refs: architecture.md "Homing lock-on shots (ST-164)" and the `TacticalOverlaySystem` row; commit c6e9bc9 (ST-165 `drawTargetLock`, the pattern to copy); physics-lies row 46
- layer: assist
- goal: Owner 2026-11-08: in pilot view the lock-on shot sprite is light-delayed, so the player cannot tell where an in-flight shot really is. The targeting computer marks each live lock-on shot at its TRUE interpolated arena position. Draw only, no physics change.
- spec:
  - New toggle `TacticalSettings.showLockOnMarkers` (default `TACTICAL_LOCK_ON_MARKERS_DEFAULT = true`), `SettingSpec.Toggle("tactical_lock_on_markers", ...)` next to `tactical_incoming_fire`, i18n `setting_tactical_lock_on_markers` + `_hint` (say it uses the true state, not light).
  - `drawLockOnMarkers()` is called in `update()` next to `drawTargetLock()`, before the overlay-visible gate (like target rings and the lock, independent of the overlay flip), with its own `shape.begin/end` and blend setup copied from `drawTargetRings`. Family `allOf(LockOnComponent, TransformComponent, BaseInfoComponent).exclude(RemoveComponent)`, fetched in `addedToEngine`.
  - Marker: an open chevron of constant screen size at `transformCmp.interpolatedPosition`, pointing along `interpolatedRotationDeg`, in `faction.color`. Pure `lockOnChevron(x, y, angleRad, sizeWorld, out: FloatArray): FloatArray` in `TacticalOverlay.kt` returns the 3 points (tip, left, right) for 2 lines; `sizeWorld = LOCK_ON_MARKER_PX · wpp` (new const `LOCK_ON_MARKER_PX = 9f`). If the shot's target is alive (same `targetId` and live `body.userData`, the `LockOnSteeringSystem` check), also draw one faint line (alpha `LOCK_ON_MARKER_LINE_ALPHA = 0.35f`) to the target's interpolated position.
  - Allocation-free (scratch `FloatArray`, the existing `color`); `ShapeRenderer` lines only (WebGL, no wide lines).
- acceptance:
  - [ ] Tests: `lockOnChevron` tip lies `sizeWorld` ahead of `(x, y)` along the angle and the wings are symmetric about the heading (angles 0 and π/2)
  - [ ] Test (`TacticalOverlaySystemTest` style): with `showLockOnMarkers = false` nothing extra is drawn; a lock-on shot's velocity and position are unchanged after `update`
  - [ ] Setting shows in the settings menu and persists
  - [ ] `./gradlew core:test` passes; `scripts/web-check.sh --play` passes
- playtest: Pilot view, fire a lock-on shot at a far enemy: a chevron runs ahead of the light-delayed sprite at the shot's real position with a faint line to its target; in the outside view the chevron sits on the sprite.

---

## Menus & runs

Source: owner 2026-11-07 ("After each level there will be save (and buying in shop between levels) ... New game - select level 1, 5th (for quick start later level, with some prebuild once we have shop) ... tutorial like scenarios with some text"). The menu (New game with start level 1/5 and run physics, Scenarios with briefings, global Settings), the restyled `LevelClearedScreen` with a shop placeholder, Esc-to-menu and four scenarios (`scenario/Scenarios.kt`) are done. Web: saves go through libGDX `Preferences` (`KeyValueStore`), never `java.io`.

### ST-170 Scenario briefing pauses the game
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `ui/BriefingPanel.kt`, `screen/PlayScreen.kt`, `physics/GameClock` / `system/TimeWarpSystem.kt`
- layer: meta (UI)
- goal (owner 2026-11-07): while a scenario's briefing is open, the arena is frozen; "Got it" (or Enter) starts it.
- spec: freeze world time (no physics steps, no AI, no weapon cooldowns, no lifetime ticks) but keep rendering the frozen scene and the UI. Player input other than closing the briefing and Esc is ignored while paused. ST-169 objective panels later reuse the same pause for "read this first" steps.
- acceptance:
  - [ ] Tests: world time does not advance while the briefing is shown, and advances after it is hidden
  - [ ] `scripts/web-check.sh --query=scenario=relativity&play` passes (the screenshot shows the briefing and ship time 0)
- playtest: Scenarios → Relativity → Start: nothing moves until Got it.

### ST-171 Settings page with tabs
- status: ready
- size: M
- agent: kotlin-dev
- depends: —
- files: `settings/MenuSettings.kt` (`SettingSpec` gains a category), `screen/MainMenuScreen.kt` (`buildSettingsPage`), `ui/MenuStyle.kt` (tab style), `assets/i18n/messages.properties`; tests in `settings/MenuSettingsTest.kt`, `ui/MainMenuLayoutTest.kt`
- refs: AGENTS.md "Architecture essentials" (settings), "Vision" (assists are toggles); `ui/MenuStyle.toggleButton`
- layer: meta (UI)
- goal (owner 2026-11-07): the Settings page is split into tabs instead of one long scrolling list, with Dev last.
- spec:
  - `SettingSpec` gets a `category: SettingCategory` (enum, in tab order); the tabs are built from the enum, so a new setting only names its category. Run settings (`RUN_SETTING_KEYS`) stay on the New-game page and are in no tab.
  - Tabs and today's settings (global specs only):
    1. **Controls**: `aim_mode`, plus the key/mouse bindings list (moves here from the right column).
    2. **Ship computer** (UI / HUD assists): `tactical_overlay`, `tactical_laser_damage`, `tactical_target_rings`, `tactical_intercept_markers`, `target_pointers`, `grid`.
    3. **Graphics**: `fullscreen` (desktop only), `perspective`. Later quality toggles (shadows, lensing, starfield) go here.
    4. **Audio**: `master_volume`, `hull_sound_volume`, `computer_sounds`, `computer_sound_volume`.
    5. **Dev** (always last): `debug_mode` only.
  - Tab bar: a row of `MenuStyle.toggleButton`s in a `ButtonGroup` at the top of the panel; the selected tab is lit. The page keeps its fixed panel size when switching tabs (size it to the largest tab), so the buttons don't jump.
  - "Reset defaults" resets the **current tab** only (hint text says so). The Audiowide credit line stays at the bottom of the panel.
  - The last open tab is remembered for the session (not persisted).
  - Web: no new reflection, no per-frame allocation (tabs are built once and swapped, like the menu pages).
- acceptance:
  - [ ] Tests: every global spec has a category; `debug_mode` is the only Dev spec and Dev is the last category; no run setting has a tab; reset of one category leaves the others unchanged; all tab i18n keys exist
  - [ ] `./gradlew core:test` passes; `scripts/web-check.sh` passes; screenshots of each tab (`--scene=menu --menu-page=settings`, add a `--settings-tab=` flag) fit at 800×600 without scrolling
- playtest: Settings → click through the tabs: each shows only its own options, Dev is last with Debug mode, Reset defaults on Audio resets only the volumes.

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

