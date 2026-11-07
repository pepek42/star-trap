# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-163**.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

## Implementation order

Owner 2026-11-02. /auto-dev works down this list and takes the first `ready` card whose `depends` are all `done`. `needs-user` cards are skipped by /auto-dev (ask the owner, batched); nothing below is blocked by them, because every card is planned around the recommended answer.

Web build (owner 2026-11-04: web is the primary target):

M3 Relativity MVP:

M4 Relativistic view:

Audio (owner 2026-11-05):

M5 Gravity & black holes (gravity → black holes/planets → disk → lighting → lensing):
48b. ST-151 Make the Einstein ring readable (owner decision)
48d. ST-162 Assist: tidal stress readout

M6 Enemies (planner 2026-11-07: honest light-delayed AI, research E13):
49a. ST-154 Enemy pilot math: pursue to a standoff range (pure)
49b. ST-155 Enemy ships in the arena (inert targets first)
49c. ST-156 Enemy AI: light-delayed pursuit and honest fire control
49d. ST-157 Enemy lasers use the enemy sprite; computer sound for destroyed ships
49e. ST-158 Assist: incoming-fire warning on the tactical overlay
49f. ST-159 Balance runner with enemies
49g. ST-160 Enemy pressure per level (owner decision)
49h. ST-161 Bring docs in line after M6

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

---

## M5 Gravity & black holes

Owner decision 2026-11-02: black holes are **1–2 per level**. Gravity bends ships and shots, the horizon kills, and a bright accretion disk makes them readable. Research E14–E18 is detailed enough to plan directly. Q12 (lensing) is planned around the recommendation (a): only the backdrop is lensed. Tidal stretching (E19) is left for polish.

- note (2026-11-02, owner follow-up, game-design §11 Q1 answered): black holes, planets and decoration **start static** (placed at level start, zero velocity) but are **dynamic and feel gravity**: n-body among the sources, plus the player's pull. **Decision (planner):** sources are Box2D dynamic bodies on the **Newtonian path** (no `RelativisticComponent`): they are heavy and stay far below 0.1 c, so γ ≈ 1 and they need neither `RelativisticDynamics`, nor the γ-mass of ST-047, nor the ship clocks of ST-040. Black holes get a sensor body (no contacts, ignored by the projectile sweep, which already skips sensors); planets get a solid body. Because they have bodies, `BoundaryFieldSystem` (which acts on every `BodyComponent`) keeps them in the arena with no extra code, and `LightHistorySystem` records them for the pilot view. With the physical pull, two holes 60 u apart would collide in about 6 s, so source–source gravity is scaled by `sourceCoupling = 0.1` (declared FAKE, about 19 s), pending ST-092. Holes that touch merge. Counts stay low for performance: at most 2 holes, 2 planets and 6 debris pieces per level.
- note (2026-11-02): **Lighting** (ST-095 … ST-099) follows the accretion disk: the disk is the emitter (the hole itself emits nothing), plus one distant star per level. Lit sprites use normal maps generated from sprite alpha (or an analytic sphere normal for planets), and planets and large asteroids cast shadows. In pilot view the lighting is computed in the arena frame at each object's retarded time; the exact-vs-FAKE list is on ST-097.

---

### ST-151 Make the Einstein ring readable (owner decision)
- status: needs-user
- size: S
- agent: kotlin-dev
- layer: assist (option a) or sim backdrop (option b)
- depends: ST-150
- files: `render/LensingPass.kt` or `render/BlackHoleRenderer.kt`, `provider/BackgroundProvider.kt`; tests
- refs: ST-150 report (2026-11-07); `docs/design/physics-lies.md` row 19
- goal: The lensing is physically correct but hard to see. The backdrop has only sparse 1-3 px stars and a faint nebula, so near a hole it shows a few stretched star streaks, not a ring (a gain cap of 16 instead of 4 made no visible difference). Magnifying the uniform clear grey gave a bright halo, but that was fake light and was removed.
- question: How should the Einstein ring become visible? (a) **Recommended:** a ship-computer assist that draws a faint dashed ring at R_E, as a toggle like the photon-ring and ISCO lines. It doesn't change the sim and fits the Vision rule "fix readability with assists". (b) A denser or brighter star layer and nebula near holes. That is more "real light", but it is a backdrop FAKE and costs fill rate on web. (c) Leave it as is: the streaks are the honest picture.
- acceptance:
  - [ ] The chosen option is in place; for (a), it is toggleable and default on; for (b), a physics-lies row is added
  - [ ] Pilot-view screenshot `--hole-near=14 --pilot-view` shows a readable ring
  - [ ] `./gradlew core:test` passes, `scripts/web-check.sh --play` PASS
- playtest: You can tell where the Einstein ring is around a hole.

---

## M6 Enemies

Source: `game-design.md` §7 ("Enemy ships ... their clocks run at their own proper time"), §10 M6, research E13 (AI perceives the past). Planner decisions (2026-11-07): enemy ships are ordinary relativistic bodies, so `MoveSystem`, `RelativisticDynamics`, `WeaponSystem` (cooldown on `properDelta`, i.e. their own proper time), gravity, the boundary field and the light history already handle them. They are faction `ENEMY` (`FactionEnum` bits already exist). The AI is **honest**: it only knows the player's retarded state from `LightHistorySystem` (light that has reached the enemy) and leads it with `leadDirection` (`physics/PhotonAim.kt`), so juking at speed really dodges. No gdx-ai (reflection and TeaVM risk for little gain). Enemy count per level is the owner's call (ST-160); the cards are planned around its recommendation (a).

### ST-162 Assist: tidal stress readout
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-153
- layer: assist
- files: `system/HudSystem.kt` or `system/TacticalOverlaySystem.kt`, `assets/i18n/messages.properties`; tests
- refs: ST-153 follow-up (2026-11-07); `RelativisticComponent.tidalRatio`, `physics/Gravity.kt` `tidalAccel`
- goal: The ship's computer shows the player's tidal stress, so they can tell the deadly zone before they skim a hole. Hull damage starts at `tidalRatio` > 1 (ST-153).
- spec:
  - HUD line or small bar "Tidal stress: N %" (`tidalRatio` × 100) shown only when `tidalRatio` > `TIDAL_READOUT_MIN` (0.25); it turns red above 1 (the hull is taking damage). Reads the player's `RelativisticComponent`; no allocation per frame (reuse a StringBuilder or cache the integer).
  - Optional (if cheap): a faint ring drawn by the computer at the radius where the player's ratio would reach 1, from the inverse of `tidalAccel` (`r = cbrt(2 GM ℓ / threshold)`), drawn with the other hole rings (boosted in pilot view). Toggle with the tactical settings if one fits; otherwise skip the ring.
  - Text in `messages.properties`.
- acceptance:
  - [ ] Test: the pure formatting/threshold helper hides the readout below 0.25, shows it above, and flags red above 1; if the ring is done, its radius gives `tidalAccel(r, gm, ℓ) == threshold` within 1e-3
  - [ ] `./gradlew core:test` passes
- playtest: `--hole-near=6`: the tidal readout appears as you near the hole and turns red just before the hull starts losing health.

### ST-154 Enemy pilot math: pursue to a standoff range (pure)
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: create `ai/EnemyPilot.kt`, `core/src/test/kotlin/com/github/pepek42/asteroids/ai/EnemyPilotTest.kt`
- refs: `relativity-effects.md` §E13; `math/Angles.kt` (`shortestAngleDiffRad`)
- layer: sim
- goal: The steering law for enemy ships as a pure function, first card of M6: close to a standoff distance from a target estimate, match its velocity, then face it to shoot.
- spec:
  - `class PilotCommand { var headingRad = 0f; var thrust = 0f; var aiming = false }` (plain reusable holder, not a component).
  - `fun pursuitCommand(sx, sy, svx, svy, hullRad, tx, ty, tvx, tvy, standoff, accel, vMax: Float, out: PilotCommand): PilotCommand`:
    - `r = t − s`, `d = |r|`. If `d < 1e-3`: `aiming = true`, `thrust = 0`, `heading = hullRad`.
    - `r̂ = r/d`, `w = sv − tv` (Galilean relative velocity: APPROX, fine for a controller; the physics stays exact).
    - `vDes = sign(d − standoff) · min(vMax, sqrt(2 · accel · ENEMY_BRAKE_MARGIN · |d − standoff|))`, `ENEMY_BRAKE_MARGIN = 0.5f`.
    - `e = r̂·vDes − w`. If `|e| < ENEMY_VEL_DEADBAND · vMax` (0.1): `aiming = true`, `thrust = 0`, `heading = atan2(r̂)` (the caller swaps in the lead angle). Else `aiming = false`, `heading = atan2(e)`, `thrust = 1` if `|shortestAngleDiffRad(hullRad, heading)| < ENEMY_THRUST_CONE_RAD` (20°), else 0.
  - Constants with KDoc derivations, all scaled by c at the call site: `ENEMY_STANDOFF_LIGHT_SECONDS = 1f` (standoff = `c · 1 s`), `ENEMY_VMAX_BETA = 0.5f` (vMax = `0.5 c`), `ENEMY_ENGINE_FRACTION = 0.6f` (accel = `0.6 · settings.engineProperAccel`). The braking law ignores γ (APPROX: γ ≤ 1.155 at β ≤ 0.5).
- acceptance:
  - [ ] Test: target far ahead along the hull, both at rest: `thrust == 1`, heading toward the target
  - [ ] Test: at the standoff distance with matched velocities: `aiming == true`, `thrust == 0`
  - [ ] Test: closing too fast inside the braking distance: heading points away from the target (braking)
  - [ ] Test: hull 90° off the wanted heading: `thrust == 0`
  - [ ] `./gradlew core:test` passes

### ST-155 Enemy ships in the arena (inert targets first)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-152
- files: create `component/EnemyComponent.kt`, `provider/EnemyShipProvider.kt`; edit `component/ShipHandlingComponent.kt`, `system/MoveSystem.kt`, `system/EndLevelSystem.kt`, `ecs/GameplaySystems.kt` (if the family moves), `input/AutoAimStrategy.kt`, `screen/PlayScreen.kt`, `physics/RelativitySettings.kt`, `ui/DebugPanel.kt`; tests
- refs: CLAUDE.md "Entity creation", "Web first" (component constructor rule); `PlayerEntityProvider` as the template
- layer: sim
- goal: Enemy ships exist, can be shot and rammed, and must be destroyed to clear the level. No AI yet (ST-156), so the game stays playable in between.
- spec:
  - `EnemyComponent` (empty marker, like `PlayerComponent`, in the `component` package so TeaVM reflection covers it; one no-arg constructor).
  - `ShipHandlingComponent.engineFactor = 1f` (reset 1). `MoveSystem.addProperAcceleration` multiplies `settings.engineProperAccel` by it (`shipHandlingCmpOptional?.engineFactor ?: 1f`); the Newtonian path likewise.
  - `EnemyShipProvider(engine, world, atlas, settings).spawn(level, random, mapProvider, avoid: FloatArray)`: `enemyCount(level) = min(ENEMY_MAX, max(0, level − 1))`, `ENEMY_MAX = 3` (ST-160 rec. (a)). Sprite `spaceship/attecker_red`; box fixture like the player, `ENEMY_DENSITY = PLAYER_DENSITY`; faction `ENEMY`, `EntityType.SHIP`; `RelativisticComponent` (`captureRestMass`), `MoveComponent`, `ShipHandlingComponent { engineFactor = ENEMY_ENGINE_FRACTION }`, `WeaponComponent` (laser, `shotsPerSecond = 1f`, `projectileDamage = ENEMY_LASER_DAMAGE = 10f`, muzzle at half width), `HealthComponent { health = ENEMY_HEALTH = 100f }`, `EnemyComponent`, `TransformComponent`, `SpriteComponent`. Positions from the seeded random, at least `ENEMY_MIN_SPAWN_DISTANCE = 3 · c · 1 s` from the player spawn and valid per `validSourcePosition` against `avoid` (holes and planets). `allowSleep = false`.
  - `PlayScreen.show()`: spawn **after** `star.roll(...)`, so no existing layout shifts; gated by `settings.enemiesEnabled` (default true, debug checkbox "enemies", applies from the next level like `blackHolesEnabled`).
  - `EndLevelSystem` default family: `oneOf(AsteroidComponent::class, EnemyComponent::class)`.
  - `AutoAimStrategy`: accept targets that are `ASTEROID` or (`SHIP` with faction `ENEMY`).
- acceptance:
  - [ ] Tests: `enemyCount(1) == 0`, `enemyCount(2) == 1`, `enemyCount(10) == ENEMY_MAX`; spawned enemies keep the minimum distance; `ComponentConstructorTest` covers `EnemyComponent`
  - [ ] Test: the level is not cleared while an enemy lives, even with no asteroids
  - [ ] Test: an enemy at 0 health gets a `RemoveComponent` (generic `DeathSystem` path)
  - [ ] `./gradlew core:test` passes, `scripts/web-check.sh --play` PASS (new component: web risk)
- playtest: Level 2 shows one red ship drifting in place; auto-aim locks onto it; two hits destroy it.

### ST-156 Enemy AI: light-delayed pursuit and honest fire control
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-154, ST-155
- files: create `system/EnemyAiSystem.kt`; edit `ecs/GameplaySystems.kt`, `docs/tech/architecture.md` (system order); tests
- refs: `relativity-effects.md` §E13, §E8; `physics/PhotonAim.kt` `leadDirection`; `physics/LightHistory.kt` `retarded`
- layer: sim
- goal: Enemies hunt the player using only the light that has reached them (E13): they see where the player was, extrapolate at constant velocity and lead their photons. Fast juking dodges for real reasons.
- spec:
  - `EnemyAiSystem(lightHistory, clock, settings)`, an `IteratingSystem` over `EnemyComponent + MoveComponent + WeaponComponent + BodyComponent`, registered in `buildGameplaySystems` right after `PlayerInputSystem` (before `MoveSystem`). Finds the player once per update (`PlayerComponent` family); no player → zero thrust, no fire.
  - Per enemy: `history = lightHistory.historyOf(player.baseInfoCmp.id)`; `history.retarded(ex, ey, clock.worldTime, c, ret)` (reused `RetardedState`). If false (no light yet): `thrusters = 0`, `doAttack = false`.
  - Estimate `x_e = ret.x + ret.vx·(now − ret.t)` (same for y), `pursuitCommand(...)` with the enemy's `vSet` (or body velocity), `body.angle`, `standoff = ENEMY_STANDOFF_LIGHT_SECONDS · c`, `accel = engineProperAccel · engineFactor`, `vMax = ENEMY_VMAX_BETA · c`. Write `move.thrusters = cmd.thrust`, `move.targetAngleRad = cmd.headingRad`.
  - Fire: `leadDirection(ex, ey, βx, βy, ret, now, c, dir)`. If it succeeds and the estimate is within `ENEMY_FIRE_RANGE_LIGHT_SECONDS · c` (2.5): when `cmd.aiming`, set `targetAngleRad = atan2(dir)`; if `|shortestAngleDiffRad(body.angle, atan2(dir))| < ENEMY_FIRE_CONE_RAD` (15°) set `weapon.aimAngleRad = atan2(dir)` and `doAttack = true`, else `doAttack = false`.
  - No allocation per frame. It never reads the player's true body state (assert this in a test by moving the player's body without recording history).
- acceptance:
  - [ ] Test: with no recorded player history the enemy neither thrusts nor fires
  - [ ] Integration test (real `World`, gameplay systems from `buildGameplaySystems` with no presentation): a resting player 30 u away is approached and shot; the player's health drops within 10 s
  - [ ] Test: the enemy aims at the retarded estimate, not the true position (teleport the player body after the history is recorded; the enemy's lead does not change that frame)
  - [ ] `./gradlew core:test` passes, `scripts/web-check.sh --play` PASS
- playtest: Level 2: the red ship closes in, hovers about 20 u away and fires. Flying fast and turning makes its shots miss behind you.

### ST-157 Enemy lasers use the enemy sprite; computer sound for destroyed ships
- status: ready
- size: S
- agent: junior-dev
- depends: ST-155
- files: `provider/WeaponProjectileProvider.kt`, `system/DeathSystem.kt`; tests in `provider/WeaponProjectileProviderTest.kt`, `system/DeathSystemTest.kt`
- refs: —
- layer: sim (sprite), assist (sound)
- goal: Tell enemy fire from your own at a glance, and let the ship's computer announce a kill like it does for asteroids.
- spec:
  - `WeaponProjectileProvider.spawnProjectile`, `SpriteComponent` block: replace `textureAtlas.createSprite("player_laser", 1)` with `if (faction == FactionEnum.ENEMY) textureAtlas.createSprite("enemy_laser") else textureAtlas.createSprite("player_laser", 1)` (`faction` is already a local there). Keep the size code unchanged.
  - `DeathSystem.processEntity`: before adding `RemoveComponent`, if `entity.has(enemyMapper)` push `ComputerSoundKind.ASTEROID_BREAK_LARGE` exactly like `pushBreakUp` does (position, velocity, `clock?.worldTime ?: 0f`). Add a KDoc note that a dedicated ship sound can replace it later.
- acceptance:
  - [ ] Test: a projectile spawned by an `ENEMY` shooter asks the atlas for region `enemy_laser` (mock the atlas)
  - [ ] Test: an enemy at 0 health pushes one computer sound event
  - [ ] `./gradlew core:test` passes

### ST-158 Assist: incoming-fire warning on the tactical overlay
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-156
- files: `system/TacticalOverlaySystem.kt`, `render/TacticalOverlay.kt`, `settings/TacticalSettings.kt`, `settings/MenuSettings.kt`, `assets/i18n/messages.properties`, `docs/design/physics-lies.md`; tests
- refs: physics-lies row 34; CLAUDE.md "Vision"
- layer: assist
- goal: A photon flying at you arrives together with its own light, so the pilot can never see enemy fire coming. The ship's computer (true state, like the rest of the overlay) marks enemy photons that will pass close to the player.
- spec:
  - Pure, in `TacticalOverlay.kt`: `fun closestApproach(px, py, pvx, pvy, sx, sy, svx, svy: Float, out: Vector2): Float` returns the time `t* = max(0, −(Δr·Δv)/|Δv|²)` of the closest approach of a photon (p, v) to the ship (s, v_s) under constant velocities and writes the miss vector `Δr + Δv·t*` into `out` (`Δr = p − s`, `Δv = pv − sv`; `|Δv| = 0` gives `t* = 0`).
  - In the overlay pass: for each projectile with faction `ENEMY` (skip removed), if `t* < THREAT_HORIZON_LIGHT_SECONDS` (1.5 s) and `|miss| < THREAT_MISS_RADIUS` (2 · player bounding radius), draw a red line from its true position to the impact point `p + pv·t*` and a small cross there, alpha falling with `t*`. Off-screen threats get a red `EdgePointer` like target pointers.
  - `TacticalSettings.showIncomingFire = true`, a menu `SettingSpec` "Incoming fire warning" (English `messages.properties` key).
  - physics-lies row: the warning uses the true photon state, information light has not delivered (FAKE by design, toggleable; like row 34).
- acceptance:
  - [ ] Tests: `closestApproach` for a head-on photon gives the arrival time `d/(c + v)` and zero miss; for a photon moving away it gives `t* = 0`
  - [ ] `./gradlew core:test` passes
- playtest: Enemy shots that will hit you show a red line and an impact cross ahead of time; ones that miss widely show nothing.

### ST-159 Balance runner with enemies
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-156
- files: `tool/src/main/kotlin/com/github/pepek42/asteroids/tool/HeadlessLevel.kt`, `tool/.../BalanceReport.kt`, `tool/.../BalanceBots.kt` (bot targets), `docs/tech/balance.md`; tests in `tool/src/test`
- refs: `docs/tech/balance.md`
- layer: infra
- goal: The headless sweep plays levels with enemies (the AI is already inside `buildGameplaySystems`), so enemy damage and count can be tuned with data, like ST-101 did for asteroids.
- spec:
  - `runLevel` spawns enemies with `EnemyShipProvider` after the asteroids (the headless atlas sprite stub works). The bots' target list includes enemy ships. `LevelResult` gains `enemiesKilled` (enemy entities removed, counted in the existing entity-removed listener) and `damageFromEnemies`: the player's per-frame health drop in frames where the listener saw a removed `spent` projectile of faction `ENEMY` (attribution APPROX: a ram in the same frame counts too; note it in `balance.md`).
  - `summary.md` gets an enemy section per level: clear rate, mean damage from enemies, mean time to kill all enemies.
  - Run `./gradlew tool:balanceSim -PbalanceArgs="--seeds=8 --levels=2,3,5"` and paste the summary numbers into the card report and `balance.md`. Do not retune constants here; propose changes in the report.
- acceptance:
  - [ ] `BalanceSimTest` covers a level-2 run that spawns one enemy and records `enemiesKilled`
  - [ ] `./gradlew core:test tool:test` passes
- playtest: —

### ST-160 Enemy pressure per level (owner decision)
- status: needs-user
- size: S
- agent: kotlin-dev
- depends: ST-159
- files: `provider/EnemyShipProvider.kt`
- refs: ST-159 balance numbers
- layer: sim
- goal: Decide how many enemy ships appear and when, after the balance numbers exist.
- question: How should enemies enter the levels? (a) **Recommended:** from level 2, `level − 1` ships, at most 3, all present from the start alongside the asteroids, and the level clears only when they are all dead. It ramps gently and keeps body counts low for web. (b) One ship from level 1, growing with the level. (c) Enemies come as a separate wave after the asteroids are cleared.
- acceptance:
  - [ ] `enemyCount` and the spawn timing match the chosen option; `game-design.md` "Decisions" records it
  - [ ] `./gradlew core:test` passes

### ST-161 Bring docs in line after M6
- status: ready
- size: S
- agent: junior-dev
- depends: ST-156, ST-157, ST-158
- files: `CLAUDE.md` (system order sentence), `docs/tech/architecture.md` ("ECS system order", components list), `docs/design/game-design.md` (§7 Enemy ships row, §10 M6, §4 if controls changed)
- refs: the closed cards' reports (`scripts/backlog.py show ST-154` … `ST-158`)
- layer: infra
- goal: Docs describe enemies as built: the `EnemyAiSystem` position in the order, `EnemyComponent`, `ShipHandlingComponent.engineFactor`, enemy count rule, honest light-delayed AI, the incoming-fire assist.
- acceptance:
  - [ ] CLAUDE.md's order list includes "enemy AI" after input; architecture's system table has a row for `EnemyAiSystem`
  - [ ] game-design §7 shows enemy ships as "exists" with a one-line summary
  - [ ] `./gradlew core:test` passes

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

