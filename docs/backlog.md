# Backlog

Open task cards for agent-driven development. Finished cards are closed with `scripts/backlog.py close` (deleted; lasting knowledge goes into the docs, the card text stays in git history). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-146**.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

## Implementation order

Owner 2026-11-02. /auto-dev works down this list and takes the first `ready` card whose `depends` are all `done`. `needs-user` cards are skipped by /auto-dev (ask the owner, batched); nothing below is blocked by them, because every card is planned around the recommended answer.

Web build (owner 2026-11-04: web is the primary target):
0. ST-145 Exception in Game.dispose: sound asset already unloaded

M3 Relativity MVP:

M4 Relativistic view:

Audio (owner 2026-11-05):

M5 Gravity & black holes (gravity → black holes/planets → disk → lighting → lensing):
45. ST-097 Lit sprites: accretion disks light ships, asteroids and planets
46. ST-098 Shadows from planets and large asteroids
47. ST-099 A distant star as a directional light
48. ST-088 Gravitational lensing of the backdrop

Parked:
51. ST-091 Targeting-trance scheme
Pilot-perspective polish (owner 2026-11-03: parked while the outside view is the default; revive when the pilot perspective mode is worked on):
52. ST-078 Lead markers
53. ST-075 Pilot view: Terrell rotation
54. ST-077 Pilot view: starfield, field band and shimmer
55. ST-079 Ghosts of removed entities
56. ST-080 Searchlight k = 2 in pilot view
57. ST-089 Accretion disk in pilot view

---

## M3 Relativity MVP

- note: Source is `docs/research/relativity-effects.md` §2, §4 Tier 1 (E1–E7) and §7 items 7–15. **Differences from `docs/design/game-design.md` §6:** the research promotes relativistic velocity addition for projectiles (GDD "Maybe") to MVP because Galilean addition breaks the c cap. It also moves length contraction (GDD "Next") into MVP in world view, and adds γ-mass collisions, speed zoom and a mild searchlight (k = 1) to MVP. The HUD shows ship time τ and world time t, which answers GDD §11 Q2 with "both". The GDD parking-lot idea "fast = bullet time" is physically backwards (research E2), so it's not planned. The GDD isn't edited here. ST-049 reconciles it once the owner has answered the questions below. **Feature flag:** `RELATIVITY_ENABLED` (ST-037). When false, providers don't attach `RelativisticComponent` and the Newtonian M2 path runs unchanged. (Superseded by ST-056: the flag becomes a runtime setting, and providers always attach the component.) **Owner questions** ST-030 … ST-034 are `needs-user`. Every other card is planned around their recommended answers and isn't blocked by them.
- note (2026-11-01, owner playtest after M2 + ST-037): "Feels a bit chaotic, maybe the arena should be bigger" → ST-030 answered (c), and ST-046 now also calms a0 and the asteroid spawn. "Relativity debug options on the side" → ST-056 (runtime `RelativitySettings`), ST-057 (plumbing), ST-059 (panel), ST-060 (vector overlay). Controls are undecided → ST-058/ST-061/ST-062 make schemes (a)/(b)/(c) switchable at runtime, and ST-063 asks for the default. γ: no gameplay cap (ST-032), safety bound 50 (ST-055). **From ST-056 on**, runtime code reads c, a0, the γ cap, field params and relativity/field on/off from the DI singleton `RelativitySettings`, not the consts (the consts are its defaults). Cards written earlier (ST-038 … ST-048) that say `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE` or `RELATIVITY_ENABLED` mean the corresponding setting once ST-056 is done.

- note (2026-11-02, owner interview, game-design.md §11): **The game is perceived from the pilot's frame.** The game clock is the pilot's proper time (research E21): the arena advances `γ·dt_real` per frame, capped at about 10× (declared FAKE past the cap, shown on the HUD), and the player's guns and turning run on real time (ST-068, ST-069, ST-040). **Pilot view is the main rendering** and is pulled forward from M4 into this milestone (E8 light delay + E9 aberration: ST-070 … ST-078, default on). The minimap stays true arena positions ("tactical computer"). Aim help is a lead marker (ST-078). Also decided: braking boost (ST-066), shots leave the arena instead of bouncing (ST-067, reworks ST-039), ramming yes (ST-033), photon laser default (ST-034). The alternative control modes (auto-aim + auto-shoot, targeting trance) are **parked** at the end of the backlog.

### ST-145 Exception in Game.dispose: sound asset already unloaded
- status: ready
- size: S
- agent: junior-dev
- layer: infra
- depends: —
- files: `core/src/main/kotlin/com/github/pepek42/asteroids/Game.kt` (`dispose`), `core/src/main/kotlin/com/github/pepek42/asteroids/audio/SoundBank.kt`
- refs: ST-144 report (2026-11-06)
- goal: Every desktop exit (e.g. a `--screenshot` run) prints `GdxRuntimeException: Asset not loaded: sounds/engine_loop.wav` from `SoundBank.get` (SoundBank.kt:22), called during `Game.dispose` (Game.kt ~171). Something asks the SoundBank for a sound after the `AssetManager` was disposed or unloaded (likely a screen's `dispose`/`hide` stopping the engine loop after `ctx.disposeSafely()`, or the AssetManager disposed before the screens). Fix the order or make the stop path not look the sound up again (keep the `Sound` reference, or guard with `assetManager.isLoaded`).
- acceptance:
  - [ ] `./gradlew lwjgl3:run --args="--screenshot=<scratch>/x.png --frames=60 --seed=42"` exits without a stack trace
  - [ ] tests pass
- playtest: start a level, quit the game; no error in the console.

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

### ST-088 Gravitational lensing of the backdrop
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-085
- files: create `render/LensingPass.kt`, `assets/shaders/lensing.vert`, `assets/shaders/lensing.frag`; edit `system/RenderSystem.kt`, `provider/BackgroundProvider.kt`; tests
- goal: Stars behind a black hole bend into an Einstein ring around the shadow (research E18, thin lens, exact for the top-down camera). Only the backdrop is lensed, which is honest (Q12 (a)): sprites sit in the lens plane and aren't distorted, so hitboxes stay readable.
- spec:
  - `const val LENS_EINSTEIN_RADIUS_RS = 4f` (`R_E = 4·r_s`), `LENS_QUAD_HALF = 3f` (in units of `R_E`), `LENS_FADE_START = 0.7f` (fraction of the quad half-size).
  - Pure: `fun lensSourceOffset(rhoX, rhoY, rE, quadHalf, out: Vector2): Boolean`. Returns false inside the shadow (`|ρ| < 2.598·r_s`, drawn black). Otherwise `out = ρ·(1 − w·R_E²/|ρ|²)` with the fade weight `w = 1 − smoothstep(LENS_FADE_START·quadHalf, quadHalf, max(|ρx|, |ρy|))`, so the deflection is zero at the quad edge and there's no seam (FAKE taper, outside the Einstein ring).
  - `LensingPass`: render the nebula and the starfield into a `FrameBuffer` the size of the backbuffer (recreated on resize), then blit it to the screen. For each black hole, draw a quad of half-size `LENS_QUAD_HALF·R_E` around it with `lensing.frag` sampling the FBO at the hole's position plus the lensed offset (the GLSL mirrors `lensSourceOffset`). The black-hole shadow and rings (ST-085) and the disk (ST-087) draw on top. In pilot view the lens is centred on the hole's apparent position (FAKE: the aberrated backdrop is lensed as if unaberrated).
  - A setting `lensingEnabled = true` (last param, debug checkbox). When off, render the backdrop directly as before.
  - Holes spawn at least 60 u apart (ST-085) and a quad's half-size is 24 u, but they drift together under gravity before merging. When two quads overlap, each samples the unlensed FBO, so the second lens doesn't see the first one's image (declared FAKE; it lasts only until the merge). Centre each quad on the hole's interpolated position (world view) or apparent position (pilot view).
- acceptance:
  - [ ] Tests: `lensSourceOffset` returns false at `|ρ| = 5` (shadow 5.196 at r_s 2); at `|ρ| = R_E` it maps to (0, 0) (the Einstein ring images the point behind the hole); at the quad edge `out == ρ`; it is continuous along a radial line (no jump > 0.1 u between samples 0.01 u apart outside the shadow)
  - [ ] Screenshot via the harness (seed 42) viewed by the agent: an Einstein ring of stars around the shadow
  - [ ] `./gradlew core:test` passes
- playtest: Fly past a hole: stars smear into arcs and a ring around it. You can spot holes from the backdrop.

### ST-089 Accretion disk in pilot view, with Doppler beaming
- status: parked
- note (owner 2026-11-03): parked. The outside (world) view is the default; this card only matters for the optional pilot perspective. On ST-078 the owner said: "lets go with realism but I am afraid this might be hard to process for humans."
- size: S
- agent: kotlin-dev
- depends: ST-074, ST-080, ST-087
- files: `render/AccretionDisk.kt`, `assets/shaders/accretion_disk.vert`; tests
- goal: In pilot view each disk particle gets its own Doppler factor toward the pilot, so the side swinging toward you blazes blue-white: the famous lopsided disk (research E17), physically from in-plane motion.
- spec:
  - Mirror the vertex shader math in a pure Kotlin `fun diskParticleApparent(r, phi, gm, c, ox, oy, betaX, betaY, cx, cy, out: Vector2): Float` (returns D), and port it to GLSL by hand: the particle's arena position `p = center + r·(cos φ, sin φ)` and velocity `v = Ω·r·(−sin φ, cos φ)`. The retarded position comes from the constant-velocity closed form (`T` from research E8 with `r₀ = p − o`), `p_ret = p − v·T` (approximation: tangent line over `T`). Then the apparent position `o + apparentOffset(p_ret − o, β)` and `D = dopplerFactor(β_obs, v/c, n̂)` with `n̂` from `p_ret` to the observer.
  - The hole moves (ST-085): `center` is the hole's retarded position from its `LightHistory` (as in ST-085's pilot-view rings), and each particle is then retarded by the tangent-line formula relative to that centre (declared approximation: the centre and the particles use separate retardations; the error is about `v_hole·T`, under 1 u for holes slower than 2 u/s at 30 u).
  - Colour temperature `a_temp·D`, brightness `g⁴·beamBrightness(D, pilotView = true)`. Uniforms `u_pilot`, `u_observer`, `u_beta`, `u_c`. With `u_pilot = 0` the output equals ST-087.
- acceptance:
  - [ ] Tests: with the observer at rest 100 u from the hole, the particle moving toward the observer has D > 1 and the one moving away has D < 1, with `D_toward·D_away` equal for mirrored particles on either side of the line of sight (within 1e-3); with β_obs = 0 the apparent position differs from the true one by less than `|v|·T`
  - [ ] Screenshot (pilot view, near a hole) viewed by the agent: one side of the disk is brighter and bluer
  - [ ] `./gradlew core:test` passes
- playtest: Orbit a hole in pilot view: the approaching half of the disk glows blue-white and the other half dims red.

### ST-097 Lit sprites: accretion disks light ships, asteroids and planets
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-074, ST-087, ST-093, ST-095, ST-096
- files: create `render/LitSpriteRenderer.kt`, `render/LightSources.kt`, `assets/shaders/lit_sprite.vert`, `assets/shaders/lit_sprite.frag`; edit `system/RenderSystem.kt`, `physics/RelativitySettings.kt`, `ui/DebugPanel.kt`, `screen/PlayScreen.kt`; tests
- goal: The glowing plasma around black holes lights nearby ships, asteroids, debris and planets, with distance falloff and the lit side facing the disk (owner 2026-11-02). Black holes emit nothing themselves. Works in world view and pilot view.
- spec:
  - `LightSources` (per level, reused arrays of `LightSource`, ST-095): rebuilt every frame. One light per black hole with a disk (ST-087) at the hole's position, with the ST-095 disk constants (after a merge, scale `size` and `range` with `rs/BH_RS`). ST-099 adds the star.
  - Shader `lit_sprite` (SpriteBatch-compatible attributes `a_position`, `a_color`, `a_texCoord0`, uniform `u_projTrans`; GLSL TeaVM/WebGL-safe with `#ifdef GL_ES precision mediump float;`). Per-sprite uniforms: `u_colorRegion`, `u_normalRegion` (vec4 u, v, u2, v2; the normal texture on unit 1, `u_normals`), `u_normalMode` (0 flat, 1 normal map, 2 analytic sphere), `u_center` (arena position, vec2), `u_rot` (cos, sin of the arena angle), `u_halfSize` (vec2), `u_ambient`, and up to 4 lights `u_lightPos[4]` (vec3), `u_lightColor[4]` (colour·intensity), `u_lightSize[4]`, `u_lightRange[4]`, `u_lightDir[4]` (float flag), `u_lightCount`. In the fragment: local `q = ((uv − region.xy)/(region.zw − region.xy)·2 − 1)·(1, −1)`; normal from the map (mode 1, the normal UV mapped from the colour UV through the two regions), the sphere `(q.x, q.y, sqrt(max(0, 1 − |q|²)))` (mode 2, planets) or (0, 0, 1); rotate `n.xy` by `u_rot`; the texel's arena position `p = u_center + R(u_rot)·(q·u_halfSize)`. Then the ST-095 `shade` math. Output `albedo·shade·v_color`, so the existing vertex colour (Doppler tint and searchlight, ST-045/ST-080) still applies on top.
  - Pull the per-texel math into a pure Kotlin `fun shadeSpriteTexel(qx, qy, halfW, halfH, cx, cy, rotRad, mode, nTexX, nTexY, nTexZ, lights, idx, n, ambient, out: FloatArray)` that the GLSL mirrors line by line.
  - `RenderSystem`: when `settings.lightingEnabled` (new last setting, default true, debug checkbox "lighting") and there is at least one light, lit entities (ships, asteroids, debris: those whose region has a `<name>_n` partner; planets: mode 2) are drawn with `batch.shader = lit`, per entity: `pickLights`, set uniforms, draw, `batch.flush()`. Lasers, thrusters and explosions keep the default shader. One draw call per lit sprite is accepted because entity counts are kept low (owner 2026-11-02); KDoc a budget of about 150 lit sprites. Cache the `_n` region lookup per region (an `ObjectMap`, no per-frame string building).
  - **Pilot view: exact vs FAKE** (KDoc this list on `LitSpriteRenderer`):
    - Exact (given the pilot view itself): which side of an object is lit is a set of events, so it is the same in every frame. The shader computes it in the arena frame from the sprite's **retarded** position and angle (the same state ST-074 uses for the vertices) and then the sprite is drawn on the boosted vertices. So `u_center` and `u_rot` are always the arena-frame state (current interpolated state in world view, retarded state in pilot view), never the apparent one.
    - FAKE 1: the emitter position. Exactly, the light hitting the object at its retarded time `t_ret` left the disk earlier still, at `t_ret − |p − e|/c`. We use the hole's position extrapolated to `t_ret` (`e = e_now − v_hole·(now − t_ret)`) and skip the extra emitter-to-object delay. Holes move slowly, so the error is about `v_hole·|p − e|/c`, under 1 u for holes slower than 2 u/s at 30 u.
    - FAKE 2: the light received by a moving object isn't Doppler-shifted or aberrated at the object (its colour and brightness are computed as if it were at rest in the arena). The pilot's own Doppler on the result still applies through the vertex colour.
    - FAKE 3: lighting ignores length contraction (ST-044) of the lit sprite, and the disk is one soft point light (ST-095).
  - With `lightingEnabled = false` (or no lights) the render path and output are unchanged.
- acceptance:
  - [ ] Test: an asteroid (mode 2) at (10, 0) with a disk light at the origin: the texel at `q = (−1, 0)` (facing the hole) is brighter than at `q = (1, 0)`, which gets about the ambient plus the wrap term
  - [ ] Test: a normal-map texel with tangent normal (1, 0, 0) on a sprite rotated by 90° gets the light from +y, not from +x
  - [ ] Test: pilot-view emitter extrapolation: a hole moving at (2, 0) with the object's `t_ret` 0.5 s ago gives an emitter 1 u behind the current hole position
  - [ ] Screenshot via the harness (seed 42, level 1, world view and pilot view) viewed by the agent: asteroids near the hole are lit on the side facing the disk
  - [ ] `./gradlew core:test` passes
- playtest: Fly near a black hole: your ship and the asteroids pick up a warm glow on the side facing the disk, and it fades with distance. Toggle "lighting" in the debug panel to compare. Check the frame rate on a busy level.

### ST-098 Shadows from planets and large asteroids
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-097
- files: `render/Lighting.kt`, `render/LitSpriteRenderer.kt`, `assets/shaders/lit_sprite.frag`; tests
- goal: Planets and large asteroids cast shadows away from the light (owner 2026-11-02). Space itself has nothing to light, so a shadow only shows where it falls on another sprite. That is physically right and means no shadow geometry is needed: an occlusion test in the lit-sprite shader is enough.
- spec:
  - Pure `fun circleShadowFactor(px, py, lx, ly, lightSize: Float, directional: Boolean, cx, cy, cr): Float` (1 = lit, 0 = fully shadowed). With `d̂` from the point toward the light and `t = (c − p)·d̂`: if `t ≤ 0` or (point light) `t ≥ |l − p|`, return 1 (the occluder isn't between). `dPerp` = distance from `c` to the ray. The penumbra half-width at the occluder is `w = lightSize·t/|l − p|` for point lights (similar triangles) and `w = STAR_ANGULAR_SIZE·t` for directional ones (`const val STAR_ANGULAR_SIZE = 0.01f` in `Lighting.kt`, used by ST-099), at least 0.05 u. Return `smoothstep(cr − w, cr + w, dPerp)`.
  - Occluders: planets and asteroids with `boundingRadius ≥ OCCLUDER_MIN_RADIUS = 1.5f`, as circles in the arena frame (current state in world view, retarded state in pilot view, like ST-097). Per lit sprite, the CPU keeps up to `MAX_OCCLUDERS = 6` occluders whose circle comes within `cr + spriteRadius` of a segment from the sprite to one of its picked lights, excluding the sprite itself. Uniforms `u_occ[6]` (vec3 x, y, r), `u_occCount`. In the shader each light's contribution is multiplied by the product of `circleShadowFactor` over the occluders.
  - Self-shadowing on the far side already comes from N·L. Small asteroids, ships and debris don't cast (performance; KDoc it).
  - Setting `shadowsEnabled = true` (last param, debug checkbox).
- acceptance:
  - [ ] Tests: an occluder exactly between point and light → < 0.05; offset sideways by `cr + 2w` → > 0.95; an occluder behind the point or beyond the light → 1; continuous along a sideways sweep (no jump > 0.1 between samples 0.01 u apart)
  - [ ] Test: the CPU selection skips the sprite's own circle and keeps at most 6
  - [ ] Screenshot (seed 42, a debug setup with a ship behind a planet relative to a hole) viewed by the agent: the ship is darker than one beside the planet
  - [ ] `./gradlew core:test` passes
- playtest: Hide behind a planet from a black hole's glow: your ship goes dark. Are disk shadows (very soft, because the disk is large) readable?

### ST-099 A distant star as a directional light
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-097, ST-098
- files: `render/LightSources.kt`, `render/Lighting.kt`, `screen/PlayScreen.kt`, `physics/RelativitySettings.kt`, `ui/DebugPanel.kt`; tests
- goal: "Maybe some stars as light sources" (owner 2026-11-02). Planned as one distant sun per level: a directional light, so levels without a black hole near you still have a lit side and shading, and each level gets its own mood. In-arena stars (gravity, heat and light together) would be a later, bigger feature.
- spec:
  - Per level, from the seeded random in `PlayScreen.show()`: `angle = random.range(0, 2π)`, `temperature = random.range(4000f, 9000f)`. A `LightSource` with `directional = true`, `(x, y) = (cos, sin)`, colour from `BlackbodyLut`, `intensity = STAR_LIGHT_INTENSITY = 0.6f`. Shadows from the star use `STAR_ANGULAR_SIZE` (ST-098). It's always in the list, before the disk lights.
  - No visible star sprite (it's far beyond the arena, in the plane). Draw a faint arrow on the minimap edge pointing toward it, in the star's colour.
  - Pilot view: the star is static and infinitely far, so the illumination direction in the arena frame is exact (by the ST-097 events argument), and only FAKE 2 (no Doppler at the object) applies.
  - Setting `starLightEnabled = true` (last param, debug checkbox).
- acceptance:
  - [ ] Test: seed 42 gives the same star direction and temperature on every run of a level; a different level gives a different one
  - [ ] Test: with only the star light, a sphere-mode texel facing the star direction is brighter than one facing away, and the result doesn't depend on distance
  - [ ] `./gradlew core:test` passes
- playtest: Each level has a sun direction: everything is lit from one side. Does it read well with the disk glow on top? Does the owner want visible in-arena stars instead (later card)?

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

