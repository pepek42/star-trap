# Backlog

Open task cards for agent-driven development. Finished cards are moved to `docs/backlog-done.md` (`scripts/backlog.py archive`). **Don't read this whole file to find work:** `scripts/backlog.py next` prints the next card (first `ready` card in `## Implementation order` whose `depends` are done), `scripts/backlog.py show ST-NNN` prints one card, `scripts/backlog.py list` gives one line per card. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-112**.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

## Implementation order

Owner 2026-11-02. /auto-dev works down this list and takes the first `ready` card whose `depends` are all `done`. `needs-user` cards are skipped by /auto-dev (ask the owner, batched); nothing below is blocked by them, because every card is planned around the recommended answer.

M3 Relativity MVP:
13. ST-076 Pilot-frame aiming
14. ST-078 Lead markers
14a. ST-062 Auto-aim + auto-shoot scheme (owner 2026-11-02: the default, un-parked)
14b. ST-063 Make auto-aim the default control scheme
15. ST-075 Pilot view: Terrell rotation
16. ST-043 Doppler colour and searchlight on the starfield
17. ST-077 Pilot view: starfield, field band and shimmer
18. ST-045 Doppler tint on sprites
19. ST-038 Boundary field acts on proper velocity
19a. ST-023 Boundary field: ~5 % loss per bounce, restitution 0.95
20. ST-047 Relativistic mass in Box2D collisions
21. ST-048 Impact damage from relativistic kinetic energy
21a. ST-084 Player death ends the run (moved up from M5, needed by ST-101)
21b. ST-101 Balance pass: headless sim games, pick c, tune physics
24. ST-061 Twin-stick control scheme
25. ST-064 RelativitySettings follow-ups
29. ST-049 Bring docs in line after M3

M4 Relativistic view:
30. ST-079 Ghosts of removed entities
31. ST-080 Searchlight k = 2 in pilot view
32. ST-081 Doppler-scaled photon damage

M5 Gravity & black holes (gravity → black holes/planets → disk → lighting → lensing):
34. ST-082 Gravity math (pure)
35. ST-083 GravitySystem: n-body sources, bodies and photons
37. ST-085 Black holes: spawn, horizon, merge, shadow, rings
38. ST-093 Planets: solid gravity sources
39. ST-094 Level decoration: drifting debris
40. ST-086 Gravitational time dilation
41. ST-087 Accretion disk
42. ST-089 Accretion disk in pilot view
43. ST-095 Lighting math (pure)
44. ST-096 Normal maps from alpha in the texture packer
45. ST-097 Lit sprites: accretion disks light ships, asteroids and planets
46. ST-098 Shadows from planets and large asteroids
47. ST-099 A distant star as a directional light
48. ST-088 Gravitational lensing of the backdrop
49. ST-090 Bring docs in line after M4/M5

Parked:
51. ST-091 Targeting-trance scheme

---

## M2 Arena boundary field

- note: Physics source is `docs/research/relativity-effects.md` §1, §3 and §7 (items 1–6). **Step-input rule, decided in ST-014:** every per-step physics input goes through `PhysicsStepListener.beforeStep(dt)`. That covers thrust force, field force or velocity kick, and (still inline in `PhysicsSystem`) the rotation impulse. `world.clearForces()` runs after **every** `world.step`. This replaces the old "thrust applied once per frame, persists across substeps, cleared after the loop" scheme. Per step the result is the same: each step still integrates the thrust exactly once. The shimmer uses `ARENA_BOUNDARY_COLOR` (orange, `ui/Colors.kt`) rather than the research doc's cyan, so it matches the minimap frame.

### ST-023 Boundary field: ~5 % loss per bounce, backstop restitution 0.95
- status: ready
- size: S
- agent: junior-dev
- depends: ST-038
- files: `physics/BoundaryField.kt`, `core/src/test/.../BoundaryFieldSystemTest.kt` (or the existing field test); `docs/design/game-design.md` §11; `docs/design/physics-lies.md` row 12
- goal: Owner (2026-11-02): "field should reflect 100% of speed. We can try 95% also, to slow down game a little bit." The field already gets stronger the closer you get (acceleration ∝ q², up to `FIELD_ACCEL_MAX = 3000` at the wall), so no new mechanism is needed, only tuning. Target: about 5 % of the normal speed is lost per bounce.
- spec:
  - `FIELD_BACKSTOP_RESTITUTION = 0.95f`.
  - Lower `FIELD_DAMPING` from 1 to a value giving 5 % ± 2 % normal-speed loss per bounce. Start at `0.3f` and adjust in steps of 0.05 until the test below passes. Put the measured loss in the KDoc.
  - Add a decisions line to game-design.md §11, and update the value in physics-lies.md row 12.
- acceptance:
  - [ ] Test (real `World`, a ship body, default params): entering the field at 30 u/s (0.5 c) straight at a wall, it leaves the field with a normal speed 93–97 % of the entry speed
  - [ ] `./gradlew core:test` passes
- playtest: Bounces feel nearly elastic. Ping-ponging slowly loses energy.

---

## M3 Relativity MVP

- note: Source is `docs/research/relativity-effects.md` §2, §4 Tier 1 (E1–E7) and §7 items 7–15. **Differences from `docs/design/game-design.md` §6:** the research promotes relativistic velocity addition for projectiles (GDD "Maybe") to MVP because Galilean addition breaks the c cap. It also moves length contraction (GDD "Next") into MVP in world view, and adds γ-mass collisions, speed zoom and a mild searchlight (k = 1) to MVP. The HUD shows ship time τ and world time t, which answers GDD §11 Q2 with "both". The GDD parking-lot idea "fast = bullet time" is physically backwards (research E2), so it's not planned. The GDD isn't edited here. ST-049 reconciles it once the owner has answered the questions below. **Feature flag:** `RELATIVITY_ENABLED` (ST-037). When false, providers don't attach `RelativisticComponent` and the Newtonian M2 path runs unchanged. (Superseded by ST-056: the flag becomes a runtime setting, and providers always attach the component.) **Owner questions** ST-030 … ST-034 are `needs-user`. Every other card is planned around their recommended answers and isn't blocked by them.
- note (2026-11-01, owner playtest after M2 + ST-037): "Feels a bit chaotic, maybe the arena should be bigger" → ST-030 answered (c), and ST-046 now also calms a0 and the asteroid spawn. "Relativity debug options on the side" → ST-056 (runtime `RelativitySettings`), ST-057 (plumbing), ST-059 (panel), ST-060 (vector overlay). Controls are undecided → ST-058/ST-061/ST-062 make schemes (a)/(b)/(c) switchable at runtime, and ST-063 asks for the default. γ: no gameplay cap (ST-032), safety bound 50 (ST-055). **From ST-056 on**, runtime code reads c, a0, the γ cap, field params and relativity/field on/off from the DI singleton `RelativitySettings`, not the consts (the consts are its defaults). Cards written earlier (ST-038 … ST-048) that say `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE` or `RELATIVITY_ENABLED` mean the corresponding setting once ST-056 is done.

- note (2026-11-02, owner interview, game-design.md §11): **The game is perceived from the pilot's frame.** The game clock is the pilot's proper time (research E21): the arena advances `γ·dt_real` per frame, capped at about 10× (declared FAKE past the cap, shown on the HUD), and the player's guns and turning run on real time (ST-068, ST-069, ST-040). **Pilot view is the main rendering** and is pulled forward from M4 into this milestone (E8 light delay + E9 aberration: ST-070 … ST-078, default on). The minimap stays true arena positions ("tactical computer"). Aim help is a lead marker (ST-078). Also decided: braking boost (ST-066), shots leave the arena instead of bouncing (ST-067, reworks ST-039), ramming yes (ST-033), photon laser default (ST-034). The alternative control modes (auto-aim + auto-shoot, targeting trance) are **parked** at the end of the backlog.

### ST-061 Twin-stick control scheme (WASD thrust direction + mouse turret)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-058
- files: create `input/TwinStickStrategy.kt`; edit `event/PlayerInputListener.kt`, `event/GameEventManager.kt`, `system/PlayerInputSystem.kt`; tests
- goal: Scheme (b) for the owner to try. WASD gives the desired thrust direction in the world frame. The hull turns toward it through the existing PD rotation controller (`MoveComponent.targetAngleRad`), and the engine fires as the hull lines up. The mouse aims an independent turret (`WeaponComponent.aimAngleRad`), and LMB fires. Gamepad support (left/right stick through gdx-controllers) is **out of scope**. Ask the planner for a card if the owner picks (b) in ST-063.
- spec:
  - `PlayerInputListener`: add `fun moveAxis(x: Float, y: Float) = Unit`. In `GameEventManager`, track W/A/S/D (and the arrow keys) as booleans in `keyDown` and a new `keyUp` override. Respect `ignoreInput`, and return true only for these keys. On every change, dispatch `moveAxis(x = right − left, y = up − down)`, normalised when the length is > 1. Clear the axis in `ignorePlayerInputs()` so a held key doesn't stick across screens.
  - `PlayerInputSystem` copies it into `PlayerInputState.moveAxis`.
  - `TwinStickStrategy` (it keeps `lastTarget`, set to the ship angle in `onActivate`): if `|moveAxis| > 0.1`: `target = atan2(axis)`, `lastTarget = target`, `Δ = shortestAngleDiffRad(shipAngle, target)`, `move.thrusters = |axis|·max(0, cos Δ)^TWIN_STICK_ALIGN_EXPONENT` with `TWIN_STICK_ALIGN_EXPONENT = 2f`. Otherwise `thrusters = 0` and `targetAngleRad = lastTarget` (the ship holds its heading instead of drifting in spin). RMB is ignored. Turret: `weapon.aimAngleRad = atan2(aimWorld − shipPos)`, `doAttack = fireHeld`.
  - Register it in `PlayerInputSystem.strategies[TWIN_STICK]`, replacing the fallback.
  - Note for relativity: the turret aim is the shooter-frame direction n̂'. Once ST-039 lands, a laser aimed at the cursor at high speed visibly sweeps forward (aberration). That's intended and part of what the owner should feel.
- acceptance:
  - [ ] Test: hull at 0, axis (0, 1): `targetAngleRad = π/2`, thrusters = 0 (cos 90° = 0). Hull at π/2, same axis: thrusters = 1. Hull at π/4: thrusters = 0.5 ± 1e-4
  - [ ] Test: no axis after having had one: thrusters = 0 and `targetAngleRad` equals the last target. The turret angle follows the mouse whatever the hull angle
  - [ ] Test: `GameEventManager`: W down + D down dispatches (0.7071, 0.7071) ± 1e-4. W up leaves (1, 0). `ignorePlayerInputs()` dispatches (0, 0)
  - [ ] `./gradlew core:test` passes
- playtest: With `--controls=twin`, strafe past an asteroid while shooting it. Near c, try to turn around: the hull swings fast but the velocity changes slowly (γ² turning cost), which is the momentum-management skill.

### ST-038 Boundary field acts on proper velocity
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-025, ST-037
- files: `system/BoundaryFieldSystem.kt`, tests
- goal: In M2 the field applies a Box2D force. Relativistic bodies must instead get `du/dt = a_field` (research §3 "Relativistic behaviour", exact: a charge in a static potential), and the backstop must reflect `u`.
- spec:
  - `beforeStep`: if the entity has a `RelativisticComponent`, compute `boundaryAcceleration` with velocity = `u` (damping acts on `u`) and **add** it to `relCmp.accel` instead of applying a force. Others keep the M2 path.
  - After ST-056: params come from `settings.fieldParams`, and with `!settings.fieldEnabled` the field acceleration is skipped but the backstop below still runs. The test passes its params through `RelativitySettings(fieldParams = …)` and a `MapProvider(mock(), 100f, 100f)`.
  - Backstop for relativistic bodies: reflect `u_n → −FIELD_BACKSTOP_RESTITUTION·u_n`, then set `v = velocityFromU(u)`, `body.setLinearVelocity(v)`, and `relCmp.vSet = v`, so `RelativisticDynamics.afterStep` (which runs later) doesn't treat it as a collision.
- acceptance:
  - [ ] Test (explicit `BoundaryFieldParams(skin = 8f, damping = 0f)`): a relativistic body heading straight at a wall with β = 0.9 turns around at a depth of 6.7 ± 0.4 u (research §3 table) and never reaches the wall
  - [ ] Test: a body at γ = 5 toward the wall triggers the backstop, ends inside the map with |u| ≤ 0.9·|u_before| and moving inward
  - [ ] `./gradlew core:test` passes
- playtest: Bouncing off the edge at high speed feels like a strong rubber wall. Very fast hits snap back with a backstop ripple.

### ST-043 Doppler colour and mild searchlight on the starfield
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-041
- files: create `render/BlackbodyLut.kt`, `render/RelativityVisuals.kt` (create or extend); edit `render/Starfield.kt`, `provider/BackgroundProvider.kt`, `system/RenderSystem.kt`; tests
- goal: The sky ahead turns blue-white and bright, and behind turns red and dim (research E4, plus E10 with k = 1). It's done on the CPU over the existing `StarData` with a LUT, with no shader, which is TeaVM-safe and cheap for 1500 stars. The research's GPU-shader path is deferred.
- spec:
  - `BlackbodyLut`: 256 entries, log-spaced 1000–60000 K, holding r, g, b from `blackbodyRgb`, built once. `fun rgbInto(kelvin: Float, out: FloatArray)` interpolates linearly and doesn't allocate.
  - `RelativityVisuals.kt` constants: `DOPPLER_COLOR_STRENGTH = 1f`, `DOPPLER_D_MIN = 0.25f`, `DOPPLER_D_MAX = 4f`, `BRIGHTNESS_EXPONENT_MVP = 1f`, `B_MIN = 0.25f`, `B_MAX = 3f`. `fun effectiveDoppler(d: Float) = exp(DOPPLER_COLOR_STRENGTH·ln d).coerceIn(DOPPLER_D_MIN, DOPPLER_D_MAX)`. `fun starDoppler(betaX, betaY, gamma, sx, sy) = gamma·(1 + betaX·sx + betaY·sy)`, where ŝ is the unit vector from the player to the star's on-screen position. This is the static-source case of `dopplerFactor`.
  - `Starfield.render(batch, camera, zoom, logicalWidthPx, observer: ObserverState)`: if `observer.betaVisual.len() < 1e-4`, use the cached `packedColor` (fast path). Otherwise, per drawn star: ŝ = normalize((cx + dx, cy + dy) − `observer.position`), i.e. from the player to where the star is drawn; `D = effectiveDoppler(starDoppler(betaVisual, gammaVisual, ŝ))`; `rgb = lut(kelvin·D)`; `B = D^BRIGHTNESS_EXPONENT_MVP` clamped to `[B_MIN, B_MAX]`; colour = `rgb·brightness·min(B, 1)` packed with `Color.toFloatBits`; size × `sqrt(max(B, 1))`. No allocation.
  - `BackgroundProvider.renderBg` and `RenderSystem` pass the `ObserverState` through.
- note (2026-11-02): pilot view (ST-074, ST-077) moves where stars are drawn, but `starDoppler` keeps using the arena-frame `ŝ` from the player to the star's unaberrated offset. That is the correct source direction for `D`, so this card needs no pilot-view branch.
- acceptance:
  - [ ] Tests: `starDoppler` at β = 0.9 (+x) gives 4.36 for ŝ = (1, 0), 0.229 for (−1, 0), and 2.294 for (0, 1). `effectiveDoppler` clamps to [0.25, 4]. `BlackbodyLut.rgbInto` matches `blackbodyRgb` within 1/255 at 2000, 6500 and 20000 K
  - [ ] Screenshot via the harness with `--seed=42 --thrust --frames=150` shows a blue-shifted side and a red-shifted side, and the agent has looked at it
  - [ ] No per-frame allocation in `Starfield.render`
  - [ ] `./gradlew core:test` passes
- playtest: At speed you can tell which way you're flying from the sky colour alone. Stars behind dim but don't vanish.

### ST-045 Doppler tint on sprites
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-043, ST-044
- files: `system/RenderSystem.kt`, `render/RelativityVisuals.kt`; tests
- goal: Approaching objects look blue and bright, and receding ones look red and dim, which gives a free threat readout (research E4 sprites [FAKE white-point shift] plus E10 k = 1).
- spec:
  - `fun spriteTint(d: Float, lut: BlackbodyLut, out: FloatArray)`: `out = lut(6500·d) / lut(6500)` per channel, with each channel clamped to [0, 1]. `T_REF = 6500f`.
  - `RenderSystem`, for every non-player entity: `n̂ = normalize(observer.position − entity pos)` (source → observer). `β_s = entity velocity / c` (from `u` if relativistic). `D = effectiveDoppler(dopplerFactor(observer.betaVisual, β_s, n̂))`. `B = D` clamped to `[B_MIN, B_MAX]`. Write `Color.toFloatBits(tint.r, tint.g, tint.b, min(B, 1)·spriteAlpha)` into the vertex colour slots (indices 2, 7, 12, 17) of the ST-044 scratch vertices. Always use the vertex path for tinted sprites. If `D > 1`, push the vertex copy into a reusable additive queue with alpha `clamp(D − 1, 0, 1)`. After all sprites, switch once to `GL_SRC_ALPHA, GL_ONE`, draw the queue, and restore the blend. The player sprite isn't tinted.
- note (2026-11-02): in pilot view (ST-074), compute `n̂` from the entity's **retarded** position to the observer and `β_s` from the retarded velocity (`RetardedState`), not the current ones. Write the tint into the same vertex array ST-074 builds.
- acceptance:
  - [ ] Tests: `spriteTint(1f)` ≈ (1, 1, 1) ± 0.01; `spriteTint(2f)` has blue ≥ red; `spriteTint(0.5f)` has red > blue
  - [ ] No per-frame allocation (preallocated queue, max 256 sprites; beyond that, skip the additive pass)
  - [ ] `./gradlew core:test` passes
- playtest: Fly at an asteroid fast: it turns blue-white and glows. Fly away: asteroids behind turn red and dim.

### ST-075 Pilot view: per-corner retarded time (Terrell rotation)
- status: ready
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

### ST-076 Pilot-frame aiming: the cursor points in the pilot's frame
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-039, ST-072, ST-074
- files: `input/PlayerInputState.kt`, `input/MouseHullStrategy.kt`, `system/PlayerInputSystem.kt`, `provider/WeaponProjectileProvider.kt`; tests in `input/MouseHullStrategyTest.kt`, `provider/WeaponProjectileProviderTest.kt`
- goal: In pilot view the screen shows the pilot's frame, so the cursor direction from the ship is a pilot-frame (P) direction. The hull and the shots must follow the cursor as the pilot sees it. Shots already treat `aimAngleRad` and the hull as shooter-frame angles (ST-039), so this card makes the hull consistent.
- spec:
  - `PlayerInputState`: add `val betaShip = Vector2()` and `var pilotView = false`. `PlayerInputSystem` fills them every frame from `ObserverState.betaVisual` and `settings.pilotView && settings.relativityEnabled`.
  - `MouseHullStrategy`: `θ = atan2(aim − shipPos)`. If `input.pilotView`, then `targetAngleRad = pilotHullToWorld(θ, betaShip)`, so the hull, drawn at `worldHullToPilot(body.angle)` in pilot view, points at the cursor. Otherwise `θ` as today.
  - `WeaponProjectileProvider` hull fallback (no `aimAngleRad`): `aim = worldHullToPilot(body.angle, β_ship)` when relativity is on (the hull's angle in the ship's rest frame), else `body.angle`. The muzzle offset uses the same `aim`.
  - Follow-up from ST-039: the muzzle offset still uses the unaberrated `aim`, so at high β the shot starts slightly off its flight line. Decide here whether the muzzle should follow `n̂` or the P-frame aim.
- acceptance:
  - [ ] Test: `MouseHullStrategy` with `pilotView = true`, `betaShip = (0.9, 0)` and the cursor at 45° from the ship → `targetAngleRad = 66.45° ± 0.05°`; with `pilotView = false` → 45°
  - [ ] Test: a ship at β = 0.9 along +x with body angle 66.45° and no `aimAngleRad` fires along the shooter-frame 45°, i.e. a W direction of `aberratePhoton(β, 45°)` ± 0.1°
  - [ ] `./gradlew core:test` passes
- playtest: In pilot view at speed, the ship's nose and the shots go where the cursor is.

### ST-077 Pilot view: starfield, field band and shimmer through the boost
- status: ready
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
- status: ready
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

### ST-047 Relativistic mass in Box2D collisions
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-038
- files: `component/RelativisticComponent.kt`, `system/RelativisticDynamics.kt`, `physics/PhysicsConstants.kt`; tests
- goal: Box2D contact impulses conserve `Σ M v`. With `M = γm₀` they conserve relativistic momentum, so a near-c ship hits like a freight train (research E7, owner's "mass going to infinite"). This follows the ST-033 recommendation.
- spec:
  - Constants: `GAMMA_MASS_ENABLED = true`, `GAMMA_MASS_CAP = 8f`. This is its own Box2D solver-stability cap (mass ratios much above ~10:1 make contacts mushy). It doesn't depend on the γ safety bound `GAMMA_MAX_MASSIVE` (50 since ST-055), so keep "up to 8×". The kinematics stay exact above it: the readback `u = gUsed·vb` uses the true γ.
  - `RelativisticComponent`: add `restMass`, `restInertia` (captured from the body when the component is attached in the providers) and `massGamma = 1f`.
  - `RelativisticDynamics.beforeStep`, if enabled: `g' = min(g, GAMMA_MASS_CAP)`. If `|g' − massGamma| / massGamma > 0.01`: reuse one `MassData` (`mass = restMass·g'`, `I = restInertia·g'`, `center = body.localCenter`), call `body.setMassData(md)`, and set `massGamma = g'`.
  - The `afterStep` collision readback when enabled: `u = gUsed·vb`, then `clampU`. This is momentum-consistent. The old `uFromV` path stays for when the flag is off.
  - Thrust, field and rotation are acceleration-based or scale with inertia, so they're unaffected.
- acceptance:
  - [ ] Test: two equal rest-mass circles, A at β = 0.9 head-on into B at rest. `Σ restMass·u` is conserved within 2 % across the collision, and B's final speed exceeds its speed in the same test with the flag off
  - [ ] Test: γ = 20 is capped to a Box2D mass of 8·m₀
  - [ ] `./gradlew core:test` passes
- playtest: Ramming asteroids at speed sends them flying. A slow nudge feels the same as before.

### ST-048 Impact damage from relativistic kinetic energy
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-047
- files: `system/ContactSystem.kt`, `physics/PhysicsConstants.kt`; tests
- goal: Collisions currently do no damage. Make ramming at relativistic speed a weapon and a risk, from the exact relative Lorentz factor (research E7).
- spec:
  - Constants: `DAMAGE_PER_ENERGY = 1e-3f`, `RAM_GAMMA_THRESHOLD = 0.02f` (≈ 0.2c relative), `PLAYER_RAM_DAMAGE_FACTOR = 0f`. The player damage factor is 0 because there's no player-death flow yet. Raise it once one exists (follow-up).
  - Top-level `fun ramDamage(va, vb, restMassA, restMassB, c): Float`: `γ_rel = gammaRel(va, vb)`. If `γ_rel − 1 < RAM_GAMMA_THRESHOLD`, return 0. Otherwise `μ = mA·mB/(mA + mB)` (if one is 0 or static, μ = the other), `K = (γ_rel − 1)·μ·c²`, and return `K·DAMAGE_PER_ENERGY`.
  - `ContactSystem.beginContact`: if neither entity is a projectile and neither fixture is a sensor, take each velocity from `relativisticCmpOptional?.vSet` (pre-step) or the body velocity, and the rest mass from `restMass` or `body.mass`. Apply `ramDamage` to each body that has a `HealthComponent`, scaled by `PLAYER_RAM_DAMAGE_FACTOR` for the player.
- acceptance:
  - [ ] Tests: 10 u/s vs rest gives 0. Head-on at ±0.5c gives more damage than overtaking at 0.9c vs 0.4c with the same masses. Damage grows with the masses
  - [ ] `./gradlew core:test` passes
- playtest: A near-c ram destroys a large asteroid in one hit, and a slow bump does nothing.

### ST-064 RelativitySettings follow-ups (review minors)
- status: ready
- size: S
- agent: junior-dev
- depends: ST-056
- files: `core/src/test/kotlin/com/github/pepek42/asteroids/system/RelativitySettingsRuntimeTest.kt`, `provider/PlayerEntityProvider.kt`, provider tests, `docs/tech/architecture.md`
- goal: Tidy the gaps the ST-056 review found.
- spec:
  - Relativity-off test: compare against a reference run with a Newtonian-only setup (an entity without `RelativisticComponent`), using tolerance 0.1 instead of 1 u/s against `MAIN_ENGINE_THRUST / mass`.
  - Field-off test: add a control. In the same setup with `fieldEnabled = true`, the body does get accelerated.
  - `PlayerEntityProvider`: comment that `projectileSpeed` is ignored for LASER (it fires at `settings.c`), or drop the assignment if nothing reads it.
  - Tests: `WeaponProjectileProvider` gives a laser speed `settings.c` (e.g. `c = 40` gives |v| = 40). Player and asteroid providers always attach `RelativisticComponent`.
  - (ST-058 review) Test that changing `ControlSettings.scheme` between `PlayerInputSystem` updates calls `onActivate` (thrusters zeroed). Sort imports in `WeaponProjectileProviderTest`.
  - architecture.md: remove the stale "boundary field until ST-038" wording. Note that `FieldShimmerRenderer`, `MinimapSystem` and `CameraSystem` still read the `FIELD_SKIN` const, not `settings.fieldParams.skin`.
- acceptance:
  - [ ] The tests above exist, and `./gradlew core:test` passes

### ST-049 Bring docs in line after M3
- status: ready
- size: S
- agent: junior-dev
- depends: ST-036, ST-037, ST-038, ST-039, ST-040, ST-041, ST-042, ST-043, ST-044, ST-045, ST-046, ST-047, ST-048, ST-055, ST-056, ST-057, ST-058, ST-059, ST-060, ST-061, ST-066, ST-067, ST-068, ST-069, ST-070, ST-071, ST-072, ST-073, ST-074, ST-075, ST-076, ST-077, ST-078
- files: `docs/tech/architecture.md`, `CLAUDE.md`, `docs/design/game-design.md` (§4 controls, §5 arena size, §6 tier table and §11, only to reflect the answered `needs-user` cards)
- goal: Document the relativistic dynamics layer (step-listener order, `RelativisticComponent`, `RELATIVITY_ENABLED`), `ObserverState`, the projectile sweep and photons, the new constants, the M3 promotions listed in this milestone's note, the runtime `RelativitySettings`/`DebugSettings`/`ControlSettings` singletons, the debug panel (F1, `--debug-panel`), the `ControlScheme` strategies and `--controls=`, the UI-first input multiplexer order, and (2026-11-02) the pilot clock (`GameClock`, `TimeWarpSystem`, the 10-step cap), the braking boost, shots leaving the arena, and the pilot view with `LightHistorySystem`, aiming and lead markers.
- acceptance:
  - [ ] Every statement in the updated sections can be traced to the current code or to an answered owner decision

---

### ST-101 Balance pass: headless sim games, pick c and tune the physics
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-048, ST-084
- files: create `tool/src/main/kotlin/.../BalanceSim.kt` (or `core/src/test/.../BalanceSim.kt` behind a JUnit tag), Gradle task `tool:balanceSim`; `physics/PhysicsConstants.kt`, `physics/RelativitySettings.kt`; create `docs/tech/balance.md`; `docs/design/game-design.md` §11
- goal: Owner (2026-11-02, ST-020): "Pick some c value for now. Run some sim games and try to balance whole physics so that player still will have some chance to play." Make the numbers evidence-based.
- spec:
  - Headless runner: build the gameplay systems the way `PlayScreen.setupEcs()` does, but without render, HUD and minimap. Use a real Box2D `World`, the seeded `RandomXS128` and fixed `dt = 1/60`. Factor out a shared `buildGameplaySystems(...)` if needed so it doesn't drift from `PlayScreen`.
  - Bot pilots drive `PlayerInputListener`/`ControlScheme` inputs:
    - `cautious`: keeps β ≤ 0.5 using the braking boost, steers away from the nearest threat inside 15 u, and fires at the nearest asteroid in a 30° cone.
    - `aggressive`: full thrust toward the nearest target, firing constantly.
    - `idle`: does nothing (baseline).
  - Sweep: c ∈ {40, 60, 80}, `engineProperAccel` ∈ {15, 25, 40}, levels 1–5. Run 20 seeds each, max 120 s per level.
  - Metrics: survival rate, time to death, damage taken per minute, kills per minute, mean and p95 γ, fraction of time the time warp is capped, mean physics steps per frame.
  - Targets: `cautious` clears levels 1–3 in ≥ 90 % of runs and level 5 in ≥ 50 %. `aggressive` dies in 20–60 % of level 1–3 runs (speed should be risky but not suicide). The time warp is capped < 5 % of the time.
  - Pick c and a0 (and tune asteroid count/speed and impact damage scale if the targets are missed). Write the tables and choices to `docs/tech/balance.md`, and add a §11 decisions line. If a target needs a non-physical tweak, add a row to `docs/design/physics-lies.md`.
- acceptance:
  - [ ] `./gradlew tool:balanceSim` (or the tagged test) runs the sweep headless in under ~10 min and writes a CSV plus a markdown summary
  - [ ] `docs/tech/balance.md` has the tables, the chosen constants and the reasoning; the constants are updated in code
  - [ ] `./gradlew core:test` passes (the sweep is not part of the default `test` run)
- playtest: Level 1 is survivable for a careful pilot, and going fast is a gamble.

## M4 Relativistic view

The pilot view itself (light delay E8, aberration E9, pilot-frame aiming, lead markers) was pulled forward into M3 by the 2026-11-02 interview: ST-070 to ST-078. M4 holds the follow-ups.

### ST-079 Pilot view: ghosts of removed entities until their light runs out
- status: ready
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
- status: ready
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

### ST-081 Doppler-scaled photon damage
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-039, ST-048
- files: `component/ProjectileComponent.kt`, `provider/WeaponProjectileProvider.kt`, `system/ContactSystem.kt`, `physics/Relativity.kt`, `physics/RelativitySettings.kt`; tests
- goal: A photon's energy as absorbed depends on both ends' motion (research E12, exact): charging enemies take more damage, fleeing ones less, and firing forward while charging hits harder. It makes Doppler colour tactically meaningful.
- spec:
  - `ProjectileComponent`: add `shooterBetaX`, `shooterBetaY` (reset to 0). `WeaponProjectileProvider` sets them to `v_ship / c` at emission.
  - `fun photonEnergyFactor(bsx, bsy, btx, bty, nx, ny): Float = γ_t(1 − β_t·n̂) / (γ_s(1 − β_s·n̂))` in `Relativity.kt`, where `n̂` is the photon's arena direction (`PhotonComponent.dir`), with each β clamped to `BETA_MAX_MASSIVE`.
  - `applyProjectileHit`: if `settings.photonDopplerDamage` and the projectile has a `PhotonComponent`, `damage = laser.damage·photonEnergyFactor(...).coerceIn(PHOTON_DAMAGE_D_MIN = 0.25f, PHOTON_DAMAGE_D_MAX = 4f)`. β_t comes from the target's `relativisticCmpOptional?.vSet ?: body.linearVelocity`, divided by c. `RelativitySettings` gets a last param `photonDopplerDamage: Boolean = true`, with a debug checkbox.
- acceptance:
  - [ ] Tests: shooter at rest, target moving at β = 0.6 against the photon → factor 2.0 ± 1e-3; target fleeing at 0.6 → 0.5; shooter moving at 0.6 along the photon, target at rest → 2.0; everything at rest → 1
  - [ ] Test: with a laser of damage 50, a head-on target at β = 0.6 loses 100 health; with `photonDopplerDamage = false` it loses 50
  - [ ] `./gradlew core:test` passes
- playtest: Head-on passes kill asteroids faster than shooting at ones that run away.

---

## M5 Gravity & black holes

Owner decision 2026-11-02: black holes are **1–2 per level**. Gravity bends ships and shots, the horizon kills, and a bright accretion disk makes them readable. Research E14–E18 is detailed enough to plan directly. Q12 (lensing) is planned around the recommendation (a): only the backdrop is lensed. Tidal stretching (E19) is left for polish.

- note (2026-11-02, owner follow-up, game-design §11 Q1 answered): black holes, planets and decoration **start static** (placed at level start, zero velocity) but are **dynamic and feel gravity**: n-body among the sources, plus the player's pull. **Decision (planner):** sources are Box2D dynamic bodies on the **Newtonian path** (no `RelativisticComponent`): they are heavy and stay far below 0.1 c, so γ ≈ 1 and they need neither `RelativisticDynamics`, nor the γ-mass of ST-047, nor the ship clocks of ST-040. Black holes get a sensor body (no contacts, ignored by the projectile sweep, which already skips sensors); planets get a solid body. Because they have bodies, `BoundaryFieldSystem` (which acts on every `BodyComponent`) keeps them in the arena with no extra code, and `LightHistorySystem` records them for the pilot view. With the physical pull, two holes 60 u apart would collide in about 6 s, so source–source gravity is scaled by `sourceCoupling = 0.1` (declared FAKE, about 19 s), pending ST-092. Holes that touch merge. Counts stay low for performance: at most 2 holes, 2 planets and 6 debris pieces per level.
- note (2026-11-02): **Lighting** (ST-095 … ST-099) follows the accretion disk: the disk is the emitter (the hole itself emits nothing), plus one distant star per level. Lit sprites use normal maps generated from sprite alpha (or an analytic sphere normal for planets), and planets and large asteroids cast shadows. In pilot view the lighting is computed in the arena frame at each object's retarded time; the exact-vs-FAKE list is on ST-097.

### ST-082 Gravity math: massive and photon laws (pure)
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-035
- files: create `physics/Gravity.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/GravityTest.kt`
- goal: One gravity law for black holes that gives exact Schwarzschild orbit shapes for ships, and the matching law that bends photons (research E14). Pure functions first, so the orbits and the photon capture can be proven in tests.
- spec (no allocation; `r⃗` = body minus source):
  - `const val BH_RS = 2f` (Schwarzschild radius in u), `fun gmFromRs(rs: Float, c: Float) = rs·c²/2` (GM = 3600 at c = 60). KDoc the derived radii: photon sphere 1.5 r_s, shadow `b_c = 2.598 r_s`, ISCO 3 r_s. With `ENGINE_PROPER_ACCEL = 25` the engine can't hold a ship still inside about 12 u (6 r_s).
  - `fun gravityAccelTau(rx, ry, ux, uy, gm, c, softening, out: Vector2): Vector2`: `r = max(|r⃗|, softening)`, `L = |rx·uy − ry·ux|` (from proper velocity u), `out = −(gm/r³)·(1 + 3L²/(c²r²))·r⃗`. This is du/dτ. Callers in arena time divide by γ.
  - `fun newtonAccel(rx, ry, gm, softening, out)`: `−gm/r³·r⃗` (for `relativityEnabled = false`).
  - `fun bendPhoton(rx, ry, nx, ny, gm, c, dt, out: Vector2): Vector2`: `k = c·n̂`, `L_k = |r⃗ × k|`, `k += −(3gm/c²)(L_k²/r⁵)·r⃗·dt`, `out = normalize(k)` (renormalising keeps the path shape, research E14).
  - `fun circularOrbitL(r: Float, rs: Float, gm: Float): Float = sqrt(gm·r/(1 − 1.5·rs/r))` (test helper and future spawns; NaN inside 1.5 r_s).
  - Constants for the dynamic sources (owner 2026-11-02): `const val SOURCE_COUPLING_DEFAULT = 0.1f` (scale of source–source pull, declared FAKE, ST-092) and `const val PLAYER_GM = 30f` (the player's pull on sources only; at 10 u it accelerates a hole by 0.3 u/s²). KDoc both.
  - The inverse of `gmFromRs`: `fun rsFromGm(gm: Float, c: Float) = 2·gm/c²` (used by ST-086 for planets).
  - `fun mergeSources(m1, x1, y1, vx1, vy1, m2, x2, y2, vx2, vy2, out: FloatArray)`: `out = [m1 + m2, xcm, ycm, vxcm, vycm]` (mass-weighted centre and velocity: momentum is conserved). For black holes `m ∝ rs`, so callers pass rs as the mass and the merged hole gets `rs = rs1 + rs2`.
- acceptance:
  - [ ] Test: integrate `gravityAccelTau` with RK4 in τ (step 1e-3) for one orbit starting at r = 12 with tangential `|u| = circularOrbitL(12)/12` → r stays within 12 ± 5 %
  - [ ] Test: a photon (step `dt = 1/450`) with impact parameter b = 40, starting 400 u away, is deflected by `2r_s/b = 0.1 rad ± 15 %`
  - [ ] Test: a photon with b = 2.3 r_s ends inside r_s (captured); one with b = 2.9 r_s escapes
  - [ ] Test: far from the hole (r = 100, small u) `gravityAccelTau` matches `newtonAccel` within 1 %
  - [ ] Test: two sources with gm 3600 each, at rest 60 u apart, both pulled by `SOURCE_COUPLING_DEFAULT·newtonAccel` (semi-implicit Euler, dt = 1/45) close to 4 u apart after `19.2 s ± 5 %` (radial free fall `t = (π/2)·sqrt(r₀³/(2μ))`, `μ = 0.1·7200`)
  - [ ] Test: `mergeSources` conserves `m·v` and puts the merged centre on the segment between the two
  - [ ] `./gradlew core:test` passes

### ST-083 GravitySystem: n-body sources, bodies and photons
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-039, ST-082
- files: create `component/GravitySourceComponent.kt`, `system/GravitySystem.kt`; edit `physics/RelativitySettings.kt`, `screen/PlayScreen.kt`, `ui/DebugPanel.kt`; create `system/GravitySystemTest.kt`
- goal: Put gravity into the per-step physics (research E14). Ships and asteroids orbit and slingshot, lasers curve around holes, and the sources themselves move: they start at rest and pull each other, and the player pulls them (owner 2026-11-02).
- spec:
  - `enum class GravitySourceKind { BLACK_HOLE, PLANET }`. `GravitySourceComponent { var kind = BLACK_HOLE; var rs = BH_RS; var gm = 0f; var softening = 0.5f; var radius = 0f }` (Poolable, mapper and `gravitySourceCmp`/`gravitySourceCmpOptional`, per the convention). For `BLACK_HOLE` the effective `gm = gmFromRs(rs, settings.c)` is computed each step (c is a setting); for `PLANET` `gm` is used as stored and `radius` is the surface radius. **Every source has a Box2D dynamic body** (ST-085, ST-093) and its position and velocity come from the body. Sources have **no** `RelativisticComponent` (the Newtonian path, see the M5 note).
  - `GravitySystem(settings) : EntitySystem(), PhysicsStepListener`, families: sources `allOf(GravitySourceComponent, BodyComponent)`, bodies `allOf(BodyComponent, BaseInfoComponent).exclude(GravitySourceComponent)`, player `allOf(PlayerComponent, BodyComponent)`. Register it in the step-listener list after `boundaryFieldSystem` and before `projectileSweepSystem` (so before `relativisticDynamics`). `beforeStep(dt)`, skipped if `!settings.gravityEnabled` or there are no sources:
    - **Sources (n-body).** For each source i: `a = Σ_{j≠i} settings.sourceCoupling·newtonAccel(r⃗_ij, gm_j, softening = max(radius_i + radius_j, 1f))` + `newtonAccel(r⃗_i,player, settings.playerGm, softening = 2f)` (only if a player exists). Apply `body.applyForceToCenter(a·mass)`. O(n²) with n ≤ 4, so negligible. Asteroids, debris and photons don't pull sources.
    - **Relativistic bodies** (`RelativisticComponent`, relativity on): for each source, `rel.accel += gravityAccelTau(r⃗, rel.u, gm, c, softening) / rel.gUsed` for black holes, and `newtonAccel(r⃗, gm, softening = radius)/rel.gUsed` for planets (planets are weak, no strong-field term).
    - **Other dynamic bodies** (or relativity off): `body.applyForceToCenter(newtonAccel·mass)` per source.
    - **Photons** (`PhotonComponent`): `dir = bendPhoton(r⃗, dir, gm, c, dt)` per source (holes and planets; the player doesn't bend light), `body.linearVelocity = c·dir`, and the body angle follows `dir`.
    - Other kinematic projectiles: `v += newtonAccel·dt`.
  - `RelativitySettings`: append `gravityEnabled: Boolean = GRAVITY_ENABLED` (`const val GRAVITY_ENABLED = true`), `sourceCoupling: Float = SOURCE_COUPLING_DEFAULT` (clamped to [0, 1]) and `playerGm: Float = PLAYER_GM` (clamped to [0, 200]), restored by `resetDebugDefaults`. Debug panel: checkbox "gravity", sliders "source coupling" and "player GM".
  - The system does nothing when there are no sources, so all levels play as before until ST-085.
- acceptance:
  - [ ] Test (real `World`, a black-hole source on a dynamic sensor body at the origin, player-like relativistic body): released at rest at r = 30, it falls inward, and after 0.5 s its radial speed matches the integrated `gravityAccelTau/γ` within 5 %
  - [ ] Test: a photon passing at b = 40 is deflected 0.1 rad ± 15 % (the sweep through the real system)
  - [ ] Test: two black-hole sources at rest 60 u apart move toward each other with equal and opposite velocities (within 1 %), and with `sourceCoupling = 0` they stay at rest
  - [ ] Test: a source at rest 10 u from the player gains velocity toward the player of `playerGm/100·t` (± 5 % after 0.5 s); an asteroid next to the source doesn't move the source
  - [ ] Test: `gravityEnabled = false` → no velocity change on any body
  - [ ] `./gradlew core:test` passes

### ST-084 Player death ends the run
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `system/DeathSystem.kt`, `Game.kt`, `screen/PlayScreen.kt`, `screen/MainMenuScreen.kt`, `GameState.kt`, `assets/i18n/messages.properties`, `assets/i18n/messages_pl.properties`; tests
- goal: Black-hole horizons kill (owner decision), and impact damage (ST-048) can kill too, but today a dead player is just tagged for removal, which breaks the level. Make death end the run cleanly.
- spec:
  - `DeathSystem` gets a last constructor param `onPlayerDeath: () -> Unit = {}`. For an entity with `PlayerComponent` and `health ≤ 0`: call `onPlayerDeath()` once (guard with a flag reset in `addedToEngine`) and **don't** add `RemoveComponent`.
  - `Game.endRun()`: `gameState.resetGame()`, set a `gameState.lastRunEnded = true` flag, `setScreen<MainMenuScreen>()`. `PlayScreen` passes `{ game.endRun() }`. The screen switch must not happen in the middle of `engine.update`: post it with `Gdx.app.postRunnable`.
  - `MainMenuScreen.show()`: if `gameState.lastRunEnded`, show a label `run_ended_message` above the buttons and clear the flag. i18n: `run_ended_message=Your ship was lost. Run over.` and `run_ended_message=Statek stracony. Koniec gry.` in both files.
- acceptance:
  - [ ] Test: `DeathSystem` with a player at health 0 calls the callback once over 3 updates and adds no `RemoveComponent`; an asteroid at health 0 is still split and removed as before
  - [ ] Test: `GameState.resetGame()` puts the level back to its start value and `lastRunEnded` is settable
  - [ ] The i18n key exists in both files
  - [ ] `./gradlew core:test` passes
- playtest: Die (debug: set health to 0, or fly into a black hole after ST-085): you land on the main menu with the message, and a new game starts at level 1.

### ST-085 Black holes in the arena: spawn, horizon, merge, shadow and rings
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-072, ST-083, ST-084
- files: create `provider/BlackHoleProvider.kt`, `render/BlackHoleRenderer.kt`; edit `component/BaseInfoComponent.kt`, `provider/AsteroidProvider.kt`, `system/GravitySystem.kt`, `system/RenderSystem.kt`, `system/MinimapSystem.kt`, `physics/RelativitySettings.kt`, `screen/PlayScreen.kt`; tests
- goal: Black holes, 1–2 per level (owner decision 2026-11-02, research E16): a black shadow, a photon ring and a dashed ISCO danger ring. They start at rest and then move under gravity (ST-083); the boundary field keeps them in the arena. Crossing the horizon kills, and holes that touch merge.
- spec:
  - `EntityType.BLACK_HOLE`. `BlackHoleProvider.spawn(level: Int, random: RandomXS128)` creates `count = if (level >= 3) 2 else 1` entities with `BaseInfoComponent(NEUTRAL, BLACK_HOLE)`, `TransformComponent`, `BodyComponent` and `GravitySourceComponent(kind = BLACK_HOLE, rs = BH_RS)`. The body (`world.body {}`, `userData = entity`): `DynamicBody`, `fixedRotation = true`, zero initial velocity, one circle fixture of radius `rs` with `isSensor = true`, `density = 1f`, `filter.categoryBits = FactionEnum.NEUTRAL` category and `filter.maskBits = 0` (no contacts at all; the projectile sweep already skips sensors). Gravity is mass-independent and the boundary field is too, so the Box2D mass doesn't matter. Placement by rejection sampling with the seeded random: at least 30 u from every wall, at least 40 u from the arena centre (the player spawn) and at least 60 u from each other. Return the positions. Pull the rule into a pure `fun validSourcePosition(x, y, wallMargin, others: FloatArray, n: Int, minSep, mapW, mapH): Boolean` (also used by ST-093 and ST-094).
  - `PlayScreen.show()` spawns black holes before asteroids (if `settings.blackHolesEnabled`, a new last setting, default true, debug checkbox). `AsteroidProvider` rejects spawn points within `BH_ASTEROID_CLEARANCE = 8·BH_RS` of any black hole.
  - Horizon, in `GravitySystem.afterStep`: any non-source body with centre distance `< rs` from a hole gets a `RemoveComponent` (photons and asteroids included). The player instead gets `health = 0`, so ST-084 ends the run. Absorbed asteroids count as cleared for `EndLevelSystem`.
  - Merge, in the same pass: two holes with centre distance `< rs₁ + rs₂` merge. The survivor (lower `BaseInfoComponent.id`) takes `mergeSources` (ST-082, with rs as the mass): `rs = rs₁ + rs₂`, the body is moved to the centre of mass (`setTransform`) with the merged velocity, and its fixture is replaced by a circle of the new radius. The other gets a `RemoveComponent`. Planets are handled in ST-093.
  - `BlackHoleRenderer` (`ShapeRenderer`, called by `RenderSystem` after the backdrop and before sprites): a filled black circle of radius `2.598·rs` (the shadow), a thin bright ring at `1.5·rs` (photon ring, white, alpha 0.6) and a dashed ring at `3·rs` (ISCO, 32 dashes, orange, alpha 0.4). World view: at the interpolated transform. Pilot view: the holes move, so take the hole's retarded position from `LightHistorySystem.historyOf(id).retarded(...)` (holes exist from level start, so their history is `eternal`), then draw each circle as a 64-point polyline/fan through `o + apparentOffset(p − o, β)` around that position.
  - Minimap: a black dot with a white outline at each hole's true position.
- acceptance:
  - [ ] Tests: `validSourcePosition` rejects points 20 u from a wall, 30 u from the centre and 50 u from another hole; accepts a valid point. With seed 42, level 3 spawns two holes satisfying all rules, both with zero velocity
  - [ ] Test (real `World`): an asteroid dropped at r = 10 from a hole with no velocity is removed within 3 s; a player in the same spot ends up with `health == 0`
  - [ ] Test: two holes placed 3 u apart merge in one step into one hole with `rs = 4`, at the midpoint, with the summed momentum; the other entity has a `RemoveComponent`
  - [ ] Test: a hole given a velocity toward a wall stays inside the arena for 10 s (boundary field)
  - [ ] Test: no asteroid spawns within 16 u of a hole (seed 42, level 4)
  - [ ] `./gradlew core:test` passes
- playtest: A black disc with rings sits in the arena and slowly drifts. Ships slingshot around it, and lasers bend near it. Flying close tugs the hole toward you. On level 3+, the two holes drift together and merge. Is the ISCO ring readable as "danger"?

### ST-093 Planets: solid gravity sources and obstacles
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-083, ST-085
- files: create `provider/PlanetProvider.kt`, `render/PlanetTextures.kt`; edit `component/BaseInfoComponent.kt`, `system/GravitySystem.kt`, `provider/AsteroidProvider.kt`, `system/MinimapSystem.kt`, `physics/RelativitySettings.kt`, `ui/DebugPanel.kt`, `screen/PlayScreen.kt`, `Game.kt`; tests
- goal: Planets (owner 2026-11-02): smaller gravity sources with a solid surface instead of a horizon. They start at rest, feel the other sources and the player (ST-083), bend paths gently, and are obstacles you can crash into.
- spec:
  - Constants (`provider/PlanetProvider.kt`): `PLANET_RADIUS = 5f`, `PLANET_GM = 300f` (surface gravity 12 u/s², half of `ENGINE_PROPER_ACCEL = 25`, so ships can take off), `PLANET_DENSITY = 20f` (heavy relative to ships and asteroids, so ramming barely moves it), `PLANET_ASTEROID_CLEARANCE = PLANET_RADIUS + 6f`. Count: `planetCount(level) = if (level >= 4) 2 else 1` (pure). With ST-085 that's at most 4 sources per level.
  - `EntityType.PLANET`. `PlanetProvider.spawn(level, random, occupied: FloatArray, n: Int)`: entities with `BaseInfoComponent(NEUTRAL, PLANET)`, `TransformComponent`, `SpriteComponent` (a `PlanetTextures` region, size `2·PLANET_RADIUS`), `BodyComponent` and `GravitySourceComponent(kind = PLANET, gm = PLANET_GM, radius = PLANET_RADIUS, softening = PLANET_RADIUS)`. No `HealthComponent` (indestructible; photons are consumed on hit by the sweep because NEUTRAL collides with every faction) and no `RelativisticComponent`. Body: `DynamicBody`, zero velocity, a solid circle fixture of radius `PLANET_RADIUS`, density `PLANET_DENSITY`, friction 0.3, restitution 0.2, the NEUTRAL faction filter. Placement: `validSourcePosition` (ST-085) with wall margin 25, at least 30 u from the centre and from every hole and planet already placed. Spawn order in `PlayScreen.show()`: holes, planets, then asteroids (`AsteroidProvider` also rejects points within `PLANET_ASTEROID_CLEARANCE` of a planet). Setting `planetsEnabled = true` (last param, debug checkbox).
  - Black hole eats a planet: in the ST-085 `afterStep` pass, a planet whose centre is within `rs + 0.5·PLANET_RADIUS` of a hole gets a `RemoveComponent` (the hole's rs doesn't change: declared omission, planet mass ≪ hole).
  - Crashes need no code: Box2D resolves the contact, and impact damage (ST-048) applies to ships hitting the surface.
  - `PlanetTextures` (DI singleton, created in `Game` after the atlas, disposed in `Game.dispose`): 4 variants built once with the seeded random, each a 96×96 `Pixmap` disc. Pull the colour rule into a pure `fun planetTexel(variant: Int, seed: Long, u: Float, v: Float): Int` (RGBA8888; transparent outside the unit disc; inside, 3–5 horizontal bands from a 1D value-noise in `v` with a palette per variant, plus a darker 1-texel rim). Nearest filtering, matching the pixel-art atlas.
  - Minimap: a filled circle at true scale and position, grey.
- acceptance:
  - [ ] Tests: `planetCount(1) == 1`, `planetCount(4) == 2`; `planetTexel` is transparent at (0.99, 0.99) and opaque at (0.5, 0.5), and is deterministic for the same seed
  - [ ] Test (seed 42, level 4): 2 planets, each at least 30 u from every hole and from the centre, all at rest; no asteroid within 11 u of a planet
  - [ ] Test (real `World`): a ship dropped 15 u above a planet with no velocity falls, collides and comes to rest on the surface (centre distance `PLANET_RADIUS + ship radius ± 0.3` after 5 s); a planet placed 10 u from a hole is removed within 3 s
  - [ ] `./gradlew core:test` passes
- playtest: A banded planet sits in the arena and drifts slowly toward the hole. Orbit it, slingshot around it, try landing gently. Hitting it fast should hurt. Are the generated planet sprites good enough as placeholders?

### ST-094 Level decoration: drifting debris
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-083, ST-085
- files: create `provider/DecorationProvider.kt`; edit `component/BaseInfoComponent.kt`, `physics/RelativitySettings.kt`, `screen/PlayScreen.kt`; tests
- goal: A little light set dressing (owner 2026-11-02): a few small debris pieces that start at rest and then drift under gravity, which makes the pull of holes and planets visible even when nothing else is nearby. Moons on orbits are left out: a moon that starts at rest just falls into its planet.
- spec:
  - `EntityType.DEBRIS`. `DEBRIS_COUNT = 6` per level (pure `fun debrisCount(level: Int) = 6`; keep it a function so it can grow). Each piece: `BaseInfoComponent(NEUTRAL, DEBRIS)`, `TransformComponent`, `SpriteComponent` with the existing atlas region `debris`, size `random.range(0.8f, 1.6f)` u, random rotation, `HealthComponent(10f)` so one or two shots destroy it, a dynamic body with a circle fixture of radius `0.4·size`, density 0.5, zero velocity, the NEUTRAL faction filter. No `RelativisticComponent` and no `AsteroidComponent`: debris isn't counted by `EndLevelSystem` and isn't split by `DeathSystem` (check the death path removes it without spawning children).
  - Placement with `validSourcePosition` (ST-085): wall margin 15, at least 20 u from the centre, at least 12 u from every source, at least 8 u apart. Spawned after asteroids. Setting `decorationEnabled = true` (last param, debug checkbox).
  - Gravity, the boundary field and light history all apply through the existing families (they're ordinary bodies).
- acceptance:
  - [ ] Test (seed 42, level 2): 6 debris entities, all at rest, satisfying the placement rules
  - [ ] Test: a level with only debris left (no asteroids) counts as cleared; a debris piece at health 0 is removed without spawning anything
  - [ ] `./gradlew core:test` passes
- playtest: Debris drifts toward the nearest hole or planet and gets swallowed or settles on the surface. Is 6 pieces the right amount?

### ST-086 Gravitational time dilation in ship clocks and the time warp
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-040, ST-085
- files: `component/RelativisticComponent.kt`, `system/GravitySystem.kt`, `system/RelativisticDynamics.kt`, `system/TimeWarpSystem.kt`; tests
- goal: Clocks deep in a gravity well run slow (research E15: `dτ/dt = √(1 − r_s/r)`, combined as `√(1 − r_s/r)/γ`). Hiding near the hole makes the world race ahead ("Interstellar's planet"), and it feeds into the pilot clock warp.
- spec:
  - `RelativisticComponent.gravFactor = 1f` (reset 1). `GravitySystem.beforeStep` sets it to `∏ √(1 − rs/max(r, 1.01·rs))` over all sources (1 when gravity is off or there are no sources). For planets use `rs = rsFromGm(gm, c)` (0.17 u for the default planet, so about 2 % at its surface). The player's pull isn't a source here. Sources themselves have no `RelativisticComponent` and run on arena time.
  - `RelativisticDynamics.afterStep`: `tau += dt·gravFactor/gUsed`.
  - `properDelta` (ST-040), non-player relativistic entities: `worldDt·gravFactor/gUsed`.
  - `TimeWarpSystem`: the warp uses `γ_eff = gUsed/gravFactor` instead of `gUsed`. Rotation factor `f` for non-players (ST-040): `gravFactor/gUsed`.
  - Pure: `fun gravTimeFactor(r: Float, rs: Float): Float`.
- acceptance:
  - [ ] Tests: `gravTimeFactor(4f, 2f) ≈ 0.7071`; `gravTimeFactor(1f, 2f) ≈ √(1 − 1/1.01)` (clamped); far away (r = 1000) ≈ 1
  - [ ] Test: a body held at rest at r = 2 r_s accumulates `tau ≈ 0.707` per second of world time, and with it as the player the warp is `1.414 ± 0.01`
  - [ ] `./gradlew core:test` passes
- playtest: Skim close to a hole and watch the world time race ahead on the HUD while your guns feel the same.

### ST-087 Accretion disk: GPU particle disk
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-043, ST-085
- files: create `render/AccretionDisk.kt`, `assets/shaders/accretion_disk.vert`, `assets/shaders/accretion_disk.frag`; edit `render/BlackHoleRenderer.kt`, `system/RenderSystem.kt`; tests
- goal: The owner's end-goal visual: "bright matter around it" (research E17). A face-on rotating disk of glowing particles, coloured by temperature and gravitational redshift. It is also a "danger here" signal.
- spec:
  - Constants: `DISK_INNER = 3·BH_RS` (ISCO), `DISK_OUTER = 12·BH_RS`, `DISK_PARTICLES = 3000`, `DISK_T_PEAK = 12000f`, `DISK_PARTICLE_SIZE = 0.25f`.
  - Pure: `fun diskTemperature(r: Float, rIn: Float, tPeak: Float): Float` with `T(r) = T*·x^(−3/4)·(1 − x^(−1/2))^(1/4)`, `x = r/rIn`, and `T*` chosen so the peak, at `x = 49/36`, equals `tPeak`. `fun diskRedshift(r: Float, rs: Float) = sqrt(1 − 1.5·rs/r)` (face-on, includes transverse Doppler). `fun diskOmega(r: Float, gm: Float) = sqrt(gm/r³)`.
  - `AccretionDisk(seed)`: a static `Mesh` built once per black hole, with 4 vertices and 6 indices per particle. Attributes `a_corner` (vec2, ±1), `a_r`, `a_phi0`, `a_size`, `a_temp` (precomputed `T·g`). Radii sampled with density ∝ 1/r (more near the inside); `φ₀` uniform. No CPU updates per frame.
  - Holes move (ST-085), so `u_center` is a per-hole uniform set every frame from the hole's interpolated transform, and a merge (rs changes) rebuilds that hole's mesh with radii scaled by the new rs.
  - Vertex shader: `φ = a_phi0 + sqrt(u_gm/a_r³)·u_time` (`u_time` = `GameClock.worldTime`, so the disk turns at arena speed and visibly fast-forwards with the warp), `pos = u_center + a_r·(cos φ, sin φ) + a_corner·a_size`, colour from a 256×1 blackbody LUT texture built from `BlackbodyLut` (ST-043) at `a_temp`, brightness `g⁴`. Fragment: soft round dot. Additive blending (`GL_SRC_ALPHA, GL_ONE`). GLSL must be TeaVM/WebGL-safe (`#ifdef GL_ES precision mediump float;`).
  - No radial drift in this version (declared omission). World view only; pilot view comes in ST-089 (in pilot view, skip the disk until then).
- acceptance:
  - [ ] Tests: `diskTemperature` peaks at `r = 49/36·rIn` with value `tPeak ± 1 %` and is 0 at `rIn`; `diskRedshift(3·rs, rs) ≈ 0.7071`; `diskOmega(12f, 3600f) ≈ 1.443`
  - [ ] Test: the mesh builder fills `DISK_PARTICLES·4` vertices with all radii in `[DISK_INNER, DISK_OUTER]`
  - [ ] Screenshot via the harness (seed 42, level 1) viewed by the agent: a bright disk around the hole, whiter inside, redder outside
  - [ ] `./gradlew core:test` passes
- playtest: The disk swirls, faster near the hole. It speeds up when the world fast-forwards.

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
- status: ready
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

### ST-095 Lighting math: falloff, wrapped diffuse and light picking (pure)
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-043
- files: create `render/Lighting.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/render/LightingTest.kt`
- goal: First slice of lighting (owner 2026-11-02: "lighting should work as expected"). Pure functions that the lit-sprite shader (ST-097) mirrors, so falloff and direction can be proven in tests.
- spec (no allocation; arena units; lights live in the arena plane at a small height):
  - `class LightSource { var x = 0f; var y = 0f; var z = LIGHT_HEIGHT; var r = 1f; var g = 1f; var b = 1f; var intensity = 1f; var size = 1f; var range = 100f; var directional = false }`. `directional = true` means `(x, y)` is a unit direction toward the light (a light at infinity, ST-099).
  - Constants: `LIGHT_HEIGHT = 2f`, `LIGHT_AMBIENT = 0.35f` (keeps the unlit side readable, like ST-080's brightness floor), `MAX_LIGHTS_PER_SPRITE = 4`. Accretion-disk light: `DISK_LIGHT_INTENSITY = 3f`, `DISK_LIGHT_SIZE = DISK_OUTER` (24 u), `DISK_LIGHT_RANGE = 120f`, colour `BlackbodyLut` at `0.85·DISK_T_PEAK`. The hole itself emits nothing; the disk is approximated as one soft point light at the hole centre (declared FAKE: an annulus as a point light of size `DISK_OUTER`).
  - `fun falloff(d: Float, size: Float, range: Float): Float = (1/(1 + (d/size)²))·(1 − smoothstep(0.75·range, range, d))`: inverse square, softened at the emitter size, faded to 0 at the range so lights can be culled.
  - `fun wrapDiffuse(nDotL: Float, wrap: Float): Float = max(0, (nDotL + wrap)/(1 + wrap))`, with `wrap = clamp(size/d, 0, 1)` for point lights (an extended source seen from close lights more than a hemisphere; inside the disk radius the object is lit from all around), and `wrap = 0` for directional lights.
  - `fun lightDir(px, py, light, out: Vector3): Float`: unit vector from the point to the light (`(lx − px, ly − py, z)` normalised; for directional `(x, y, LIGHT_HEIGHT/10)` normalised) and returns the in-plane distance (∞ for directional).
  - `fun shade(nx, ny, nz, px, py, lights: Array<LightSource>, idx: IntArray, n: Int, ambient: Float, out: FloatArray)`: `out(rgb) = ambient + Σ colour·intensity·falloff·wrapDiffuse(N·L)` (falloff 1 for directional).
  - `fun pickLights(px, py, lights, count, max, outIdx: IntArray): Int`: the indices of up to `max` lights with the largest `intensity·falloff(d)`, skipping those with 0. Insertion into a small sorted array, no allocation.
- acceptance:
  - [ ] Tests: `falloff(0, 4, 100) == 1`, `falloff(4, 4, 100) == 0.5`, `falloff(100, 4, 100) == 0`, monotonic in d
  - [ ] Tests: a normal facing the light gets `ambient + intensity`; facing away with `wrap = 0` gets `ambient` only; with `wrap = 1` the back side still gets half the front side's diffuse
  - [ ] Test: `pickLights` with 6 lights returns the 4 strongest at the point, in descending order, and skips one beyond its range
  - [ ] `./gradlew core:test` passes

### ST-096 Normal maps from sprite alpha in the texture packer
- status: ready
- size: M
- agent: kotlin-dev
- depends: —
- files: `tool/src/main/kotlin/com/github/pepek42/asteroids/tool/TexturePacker.kt`, create `tool/src/main/kotlin/com/github/pepek42/asteroids/tool/NormalMaps.kt`, `tool/build.gradle.kts`, create `tool/src/test/kotlin/com/github/pepek42/asteroids/tool/NormalMapsTest.kt`; regenerated `assets/textures/textures.atlas` and `.png`
- goal: Lit sprites need a per-texel normal so light comes from the right side. Generate a bevelled "pillow" normal map from each sprite's alpha at pack time, so no hand-drawn normal art is needed (the owner has no art pipeline for it).
- spec:
  - Pure `fun normalMapFromAlpha(w: Int, h: Int, alpha: FloatArray, bevelPx: Int = 3, strength: Float = 2f): FloatArray` (returns `w·h·4` floats rgba). Inside = `alpha > 0.5`. Height `hgt = min(dist, bevelPx)/bevelPx`, where `dist` is the chamfer (3-4) distance to the nearest outside texel in texels, from a two-pass distance transform. Smooth `hgt` with a 3×3 box filter. Normal `n = normalize(−dhdx·strength, +dhdy·strength, 1)` with central differences, and `+dhdy` because image rows go down while world y goes up. Encode `rgb = n·0.5 + 0.5`, `a = alpha` (so the packer trims both images identically).
  - `main()`: for every PNG under `raw_assets` whose name starts with `asteroid_`, `debris` or lives in `spaceship/`, write `<name>_n.png` to `tool/build/normals/` (mirroring subdirectories). Copy `raw_assets` (with `pack.json`) and the generated files into `tool/build/packinput/`, then `TexturePacker.process("tool/build/packinput", "assets/textures", "textures")`. Lasers, thrusters, explosions, blast and backgrounds stay without normals (they're unlit or emissive). Read and write PNGs with `javax.imageio` (the tool is JVM-only).
  - Add JUnit test dependencies to `tool/build.gradle.kts` the same way `core` has them, if missing.
  - Run `./gradlew tool:packTextures` and commit the regenerated atlas. Region lookups by name in `core` are unchanged (new regions are only added).
- acceptance:
  - [ ] Test: for a 32×32 filled disc of radius 12, the centre normal is (0, 0, 1) ± 0.05, a texel at the right rim has `nx > 0.5`, at the top rim (row 4) `ny > 0.5`
  - [ ] Test: a fully transparent image gives all-transparent output; a fully opaque 8×8 square has flat normals in its inner 2×2
  - [ ] After packing, `textures.atlas` contains `asteroid_big_n`, `debris_n` and `spaceship/triangle_blue_n` (or the path form the packer uses), with the same width and height as their colour regions
  - [ ] `./gradlew core:test` and `./gradlew tool:test` pass

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

### ST-090 Bring docs in line after M4/M5
- status: ready
- size: S
- agent: junior-dev
- depends: ST-079, ST-080, ST-081, ST-082, ST-083, ST-084, ST-085, ST-086, ST-087, ST-088, ST-089, ST-093, ST-094, ST-095, ST-096, ST-097, ST-098, ST-099
- files: `docs/tech/architecture.md`, `CLAUDE.md`, `docs/design/game-design.md` (§6, §7 entities, §9 visuals, §11 decisions only)
- goal: Document the pilot view render path and its flags (V key), `LightHistorySystem` and ghosts, the step-listener order including `GravitySystem` and `LightHistorySystem`, `GravitySourceComponent` and the n-body source path (Newtonian, sensor bodies for holes, `sourceCoupling`, `playerGm`), black holes (`EntityType.BLACK_HOLE`, horizon kill, merging), planets and debris, the player death flow, the accretion disk, lighting (lit-sprite shader, normal maps from the packer, shadows, star light, the pilot-view exact/FAKE list) and lensing passes, and the new constants and settings.
- acceptance:
  - [ ] Every statement in the updated sections can be traced to the current code or to an answered owner decision

---

## Parked (low priority)

Owner 2026-11-02: control modes for later (game-design §12). Keep them ready but behind all other milestones.

### ST-062 Auto-aim + auto-shoot control scheme (the player manages momentum)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-058, ST-073, ST-078
- files: create `input/AutoAim.kt`, `input/AutoAimStrategy.kt`; edit `system/PlayerInputSystem.kt`, `screen/PlayScreen.kt`; tests
- goal: **Reworked 2026-11-02** (owner: parked mode (a), Brotato / vampire-survivors style). Movement is mouse-hull as in scheme (a): the mouse steers the hull and RMB thrusts. The guns pick a target in a forward cone and fire on their own. It uses the honest fire control of the lead markers (ST-078: retarded state, extrapolated), so auto-aim never peeks at true positions. The photon math moved to `physics/PhotonAim.kt` (ST-073).
- spec:
  - Pure `fun pickTargetIndex(sx, sy, facingRad, coneHalfRad, range, xs: FloatArray, ys: FloatArray, n: Int): Int` in `AutoAim.kt`: the index of the nearest candidate with `dist ≤ range` and `|shortestAngleDiffRad(facing, atan2(dy, dx))| ≤ coneHalfRad`, or −1. No allocation.
  - `AutoAimStrategy(targets, lightHistory, observer, clock, settings)`: the targets are the family `allOf(HealthComponent, BodyComponent, BaseInfoComponent)` minus the player faction. Candidate positions are their retarded positions (`LightHistory.retarded`), filled into reused scratch arrays. Movement as in `MouseHullStrategy`. Fire: `i = pickTargetIndex(…, AUTO_AIM_CONE_HALF_DEG = 30°, AUTO_AIM_RANGE = 40f)`. If `i ≥ 0`, then `leadDirection(…)` (ST-078) gives `n̂'`, `weapon.aimAngleRad = atan2(n̂')` and `doAttack = true`. Otherwise `doAttack = fireHeld` and `aimAngleRad = null`.
  - Register it in `PlayerInputSystem.strategies[AUTO_AIM]`.
- acceptance:
  - [ ] Tests: `pickTargetIndex` picks the nearest in-cone target, skips a nearer one at 45° off the facing and one beyond range, and returns −1 for none
  - [ ] Test (real `World`): a ship at β = 0.8 along +x, facing +y, with an asteroid 20 u away along +y (histories warmed up), fires a photon whose sweep hits it
  - [ ] `./gradlew core:test` passes
- playtest: With `--controls=auto`, fly around: the guns hit asteroids ahead even at high speed. Does it feel like the game plays itself?

### ST-091 Targeting-trance control scheme (hold LMB: slow-mo aim, release: one shot)
- status: ready
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

### ST-063 Owner decision: default control scheme
- status: ready
- size: S
- agent: junior-dev
- depends: ST-058, ST-062
- files: `docs/design/game-design.md` §4 and §11; `Game.kt` (`ControlSettings(options.controls ?: …)` fallback) and `input/ControlSettings.kt` (default)
- goal: Record which scheme is the default once the owner has tried them all through `--controls=` or the debug panel selector. The others stay selectable. Parked with the modes (2026-11-02); ask once ST-061, ST-062 and ST-091 are done.
- answer (owner 2026-11-02): **(c) Auto-aim + auto-shoot is the default.** Mouse-hull, twin-stick and trance stay selectable via `--controls=` and the debug panel.
- acceptance:
  - [ ] The answer is in game-design.md §11 (decisions) and §4. `ControlSettings` and the `Game.kt` fallback default to `ControlScheme.AUTO_AIM`
  - [ ] Test: `ControlSettings().scheme == ControlScheme.AUTO_AIM`
  - [ ] `./gradlew core:test` passes
