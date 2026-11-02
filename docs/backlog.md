# Backlog

Task cards for agent-driven development. `/auto-dev` picks the first `ready` card in milestone order whose dependencies are `done`. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-102**. /auto-dev follows `## Implementation order` when present; otherwise milestone order.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

## Implementation order

Owner 2026-11-02. /auto-dev works down this list and takes the first `ready` card whose `depends` are all `done`. `needs-user` cards are skipped by /auto-dev (ask the owner, batched); nothing below is blocked by them, because every card is planned around the recommended answer.

M3 Relativity MVP:
1. ST-066 Braking boost
2. ST-067 Shots leave the arena; ripple where they cross
3. ST-039 Photon lasers: muzzle aberration
3a. ST-100 Readable backdrop: calmer nebula, grid, centre pointer
4. ST-068 Pilot clock: time-warp functions, GameClock, settings
5. ST-069 Pilot clock wiring
6. ST-040 Ship clocks under the pilot clock
7. ST-070 LightHistory ring buffer and retarded-time solver
8. ST-071 LightHistorySystem
9. ST-072 PilotFrame transforms
10. ST-073 PhotonAim
11. ST-044 Length contraction of moving sprites (needed by ST-074)
12. ST-074 Pilot view: retarded, boosted sprites
13. ST-076 Pilot-frame aiming
14. ST-078 Lead markers
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
22. ST-042 HUD: β, γ, ship/world time, warp; speed zoom
23. ST-060 Debug vector overlay
24. ST-061 Twin-stick control scheme
25. ST-064 RelativitySettings follow-ups
26. ST-065 Debug panel polish
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
50. ST-062 Auto-aim + auto-shoot scheme
51. ST-091 Targeting-trance scheme
52. ST-063 Owner decision: default control scheme (**needs-user**)

---

## M0 Tooling & stack

### ST-001 Migrate build to the new gdx-liftoff Kotlin template stack
- status: done (2026-10-26, 1810f4f)
- result: Gradle 9.8.1, Kotlin DSL, version catalog, `build-logic` convention plugin, JDK 25 toolchain.

### ST-002 Set up agents, skills and design/tech docs
- status: done (2026-11-01, de6161b)
- result: `.claude/agents/*`, `.claude/skills/{auto-dev,plan-backlog}`, `docs/` structure.

---

## M1 Fix phase

### ST-003 Fix angle wrapping in player rotation
- status: done (2026-11-01, bf13c42)
- result: Added `math/Angles.kt` (wrapAngleRad, shortestAngleDiffRad) with tests; PlayerInputSystem now rotates the short way.
- size: S
- agent: junior-dev
- depends: —
- files: create `core/src/main/kotlin/com/github/pepek42/asteroids/math/Angles.kt`, edit `system/PlayerInputSystem.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/math/AnglesTest.kt`
- goal: The ship sometimes rotates the long way round. In `handleAngle`, `(angleDiff - PI) % PI2 + PI` uses Kotlin `%`, which keeps the sign of the dividend, and `body.angle` is unbounded. Replace it with a correct shortest-angle function.
- spec:
  - In `Angles.kt` (package `com.github.pepek42.asteroids.math`): `fun wrapAngleRad(angle: Float): Float = angle - MathUtils.PI2 * floor((angle + MathUtils.PI) / MathUtils.PI2)`, returning a value in [−π, π). Use `kotlin.math.floor`.
  - `fun shortestAngleDiffRad(from: Float, to: Float): Float = wrapAngleRad(to - from)`.
  - In `PlayerInputSystem`: `getTargetAngleRad` returns `aimVector.angleRad()` (remove `+ MathUtils.PI`). `handleAngle` sets `moveCmp.rotationRequired = shortestAngleDiffRad(body.angle, targetAngleRad)` and drops the `+ PI` on `currentAngleRads`. Keep the debug logging and adjust its variable names.
- acceptance:
  - [x] Parameterized test: `wrapAngleRad` for 0, π/2, π (→ −π), −π, 3π/2 (→ −π/2), 7π (→ −π), −7.5π (→ π/2), and 100.0; results stay in [−π, π) within 1e-5
  - [x] Test: `shortestAngleDiffRad(from = 10π + 0.1, to = −0.1)` ≈ −0.2
  - [x] `./gradlew core:test` passes

### ST-004 Torque-based rotation controller with tunable feel
- status: done (2026-11-01, f40ff03)
- result: PD controller in `ShipRotationController` (kp=80 instead of ≈60 to meet the 0.6 s target, kd=2√kp, ±200 rad/s², ω ≤ 2π) applied as an angular impulse by `PhysicsSystem` before every substep; `MoveComponent.targetAngleRad` replaces `rotationRequired`.
- size: M
- agent: kotlin-dev
- depends: ST-003
- files: `system/MoveSystem.kt`, `component/MoveComponent.kt` (or a new `ShipHandlingComponent`), `system/MoveSystemTest.kt`
- goal: `MoveSystem.rotate` sets `angularVelocity` directly and caps it at π/2 rad/s, which feels sluggish. Replace this with a PD controller that applies torque (`body.applyTorque`) from `rotationRequired` and the current angular velocity: `torque = I · (kp·err − kd·ω)`, clamped to `maxTorque`, and ω clamped to `maxAngularVelocity`. Use `body.inertia` so the controller is independent of mass. Starting values: maxAngularVelocity 2π rad/s, kp ≈ 60, kd ≈ 2·√kp. Tune so it's critically damped. Keep apply-once-per-step semantics (`moveForcesApplied`). Remember Box2D's own per-step rotation cap of 0.5π rad/step.
- acceptance:
  - [x] Test with a real Box2D `World` and the player-sized box body: a ship at angle 0 that targets π/2 reaches it within 0.6 s of simulated steps, with overshoot < 5°
  - [x] Test: a target of π − 0.01 turns counter-clockwise, and π + 0.01 (wrapped) turns clockwise
  - [x] Constants are named in a companion object, and the old commented-out `applyTorque` block is deleted
  - [x] `./gradlew core:test` passes
- playtest: The ship follows the cursor snappily with no wobble, including when the cursor crosses behind the ship.

### ST-005 Screenshot harness for automated visual checks
- status: done (2026-11-01, f84b805)
- result: `debug/ScreenshotHarness.kt` with `LaunchOptions` parsing; `./gradlew lwjgl3:run --args="--screenshot=/abs/path.png --frames=120"` writes the PNG and exits (verified, also exits if the write fails).
- size: M
- agent: kotlin-dev
- depends: —
- files: `lwjgl3/.../Lwjgl3Launcher.kt`, `Game.kt`, new `core/.../debug/ScreenshotHarness.kt`
- goal: Let agents "see" the game. Add launch arguments: `--screenshot=<path.png>`, `--frames=<N>` (default 180), `--scene=play|menu` (default play). With `--screenshot`, the game skips the main menu when the scene is `play`, runs N frames with no player input (optionally with `--thrust` held), saves the framebuffer with `Pixmap.createFromFrameBuffer` + `PixmapIO.writePNG`, and calls `Gdx.app.exit()`. It must be usable as `./gradlew lwjgl3:run --args="--screenshot=/tmp/st.png --frames=120"`. Note that the run task's working dir is `assets/`, so paths must be absolute. Pass the parsed options into `Game` through a constructor data class with defaults, so the TeaVM launcher still compiles.
- acceptance:
  - [x] An argument-parsing function with unit tests (valid, missing value, unknown flag ignored)
  - [x] Running the command above produces a non-empty PNG and the process exits by itself (run it once to verify; a display is available)
  - [x] A "Visual checks" section is added to `docs/tech/dev-workflow.md` explaining usage
  - [x] `./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin` passes

### ST-006 Clear all entities and bodies between levels
- status: done (2026-11-01, 1f103d8)
- result: `ecs/LevelCleanup.kt` `clearLevel` destroys every body and removes all entities; `PlayScreen.hide()` calls it.
- size: S
- agent: kotlin-dev
- depends: —
- files: `screen/PlayScreen.kt`, new helper (e.g. `ecs/LevelCleanup.kt`), a test
- goal: `PlayScreen.hide()` only removes player entities. Asteroids, projectiles and their Box2D bodies leak into the next level (there's a TODO). Add `fun clearLevel(engine: Engine, world: World)`: for every entity with a `BodyComponent`, destroy the body and null `userData` (the same thing `RemoveSystem` does), then `engine.removeAllEntities()`. Call it from `hide()`.
- acceptance:
  - [x] Test with a real `World` + `PooledEngine`: after spawning 3 bodies/entities and calling `clearLevel`, `world.bodyCount == 0` and `engine.entities.size() == 0`, and no assertion error comes from `BodyComponent.reset()`
  - [x] `./gradlew core:test` passes

### ST-007 Fix faction collision bit naming and the projectile filter
- status: done (2026-11-01, 74bcd75)
- result: `FactionEnum` fields renamed to `ownCategoryBit`/`collidesWithMask` with `collidesWith()`; all providers (including projectiles, which now set a mask) use category=own, mask=collidesWith.
- size: S
- agent: junior-dev
- depends: —
- files: `faction/FactionEnum.kt`, `provider/PlayerEntityProvider.kt`, `provider/AsteroidProvider.kt`, `provider/WeaponProjectileProvider.kt`, new `core/src/test/kotlin/com/github/pepek42/asteroids/faction/FactionEnumTest.kt`
- goal: Field names are inverted. `entityMaskBit` (the faction's own bit) is assigned to `maskBits`, and `categoryBits` (other factions' bits) to `categoryBits`. `WeaponProjectileProvider` sets `filter.categoryBits` twice and never sets `maskBits`.
- spec: Rename `entityMaskBit` → `ownCategoryBit` and `categoryBits` → `collidesWithMask`. Keep the constructor values unchanged. In **all three** providers, set `filter.categoryBits = faction.ownCategoryBit` and `filter.maskBits = faction.collidesWithMask`. (Swapping category and mask on every fixture leaves Box2D's symmetric collision rule unchanged.) Add `fun FactionEnum.collidesWith(other: FactionEnum): Boolean = (collidesWithMask.toInt() and other.ownCategoryBit.toInt()) != 0 && (other.collidesWithMask.toInt() and ownCategoryBit.toInt()) != 0`.
- acceptance:
  - [x] Test: PLAYER×PLAYER false, PLAYER×NEUTRAL true, PLAYER×ENEMY true, ENEMY×ENEMY false, NEUTRAL×NEUTRAL true
  - [x] Existing `WeaponProjectileProviderTest` still passes
  - [x] `./gradlew core:test` passes
- playtest: The player's lasers hit asteroids and don't hit the player ship.

### ST-008 Small asteroids use their own texture
- status: done (2026-11-01, 7ce5a63)
- result: `AsteroidSize.SMALL` uses `asteroid_small`.
- size: S
- agent: junior-dev
- depends: —
- files: `environment/asteroid.kt`
- goal: `AsteroidSize.SMALL` uses `"asteroid_big"`. Change it to `"asteroid_small"` (the region exists in `textures.atlas`).
- acceptance:
  - [x] The change is made, and `./gradlew core:test` passes

### ST-009 CI on JDK 25 and on all branches/PRs
- status: done (2026-11-01, 44719a8)
- result: Workflow runs on all pushes and PRs with JDK 25 and setup-gradle@v4; YAML validated (not yet run on GitHub, nothing pushed).
- size: S
- agent: junior-dev
- depends: —
- files: `.github/workflows/workflows.yaml`
- goal: CI installs JDK 17, but the toolchain is 25. Use `actions/checkout@v4` and `actions/setup-java@v4` with `java-version: "25"`, `distribution: temurin`, add `gradle/actions/setup-gradle@v4` before the test step, and trigger on `push` (all branches) and `pull_request`.
- acceptance:
  - [x] The YAML is valid (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/workflows.yaml`)

### ST-010 Fix asteroid split velocity mutation
- status: done (2026-11-01, 6704cb8)
- result: `DeathSystem.spawnSmallerAsteroids` copies position/velocity so each child gets its own rotation.
- size: S
- agent: junior-dev
- depends: —
- files: `system/DeathSystem.kt`
- goal: In `spawnSmallerAsteroids`, `currentVelocity` is `body.linearVelocity`, a vector Box2D reuses. `rotateDeg` mutates it in place, so the second child's velocity is rotated by both angles. Use copies: `currentVelocity.cpy().rotateDeg(-r1)` and `currentVelocity.cpy().rotateDeg(+r2)`. Do the same for `position` (take `body.position.cpy()` once).
- acceptance:
  - [x] The fix is made, and `./gradlew core:test` passes

### ST-011 Readable minimap
- status: done (2026-11-01, 44ccd24)
- result: Minimap anchored to the window's bottom-right (logical px, HiDPI-safe), one ShapeRenderer pass, `ARENA_BOUNDARY_COLOR` in `ui/Colors.kt`, view rectangle tracks zoom; screenshot checked.
- size: M
- agent: kotlin-dev
- depends: ST-005
- files: `system/MinimapSystem.kt`, `ui/MinimapViewport.kt`, `screen/PlayScreen.kt`
- goal: The minimap is unreadable. `MinimapSystem` calls `shape.begin/end` per entity, inside a `SpriteBatch.use` block, never sets `shape.projectionMatrix`, and has no frame. Rework it:
  - Square panel in the bottom-right corner, 22% of the screen height, with a 12 px margin. Use a viewport that shows exactly the map (`mapWidth × mapHeight`) through `setScreenBounds`.
  - Semi-transparent dark background (alpha 0.55, blending enabled) plus a 1-px border in the arena-boundary colour.
  - One `shape.begin/end` pass per frame with `shape.projectionMatrix = minimapCamera.combined`. Don't use `SpriteBatch.use`.
  - Dots sized in screen-independent world units (ship 2.0, asteroid ∝ radius, projectile 0.8), in faction colours. The player is drawn last and brighter.
  - A rectangle showing the main camera's visible area (it accounts for zoom).
  - Dispose the `ShapeRenderer` (the system implements `Disposable`).
- acceptance:
  - [x] Pure function `minimapBounds(screenW, screenH): Rectangle` with a unit test for 800×600 and 1920×1080
  - [x] Screenshot via the ST-005 harness shows the framed minimap, and the agent has looked at it
  - [x] `./gradlew core:test` passes
- playtest: The minimap is readable at a glance. You can tell asteroids from yourself, and the view rectangle tracks zoom.

### ST-012 Readable, camera-relative starfield (Doppler-ready)
- status: done (2026-11-01, e00f364)
- result: `render/Starfield.kt`: 1500 seeded stars in `StarData` arrays (x, y, layer, brightness, kelvin, packedColor), 3 parallax layers, additive dots sized in logical px, dim `background_1` nebula; brightness constants (layer 0.55/0.8/1.0, sizes 1.5/2.2/3.2 px) are the knobs for playtest tuning.
- size: M
- agent: kotlin-dev
- depends: ST-005
- files: `provider/BackgroundProvider.kt`, new `render/Starfield.kt`, `system/RenderSystem.kt`
- goal: The current background is three timer-scrolled full-map textures, low contrast, with no motion parallax. Replace it with a procedural star layer that later relativity shaders can shift per star:
  - Generate about 1500 stars once per map, seeded. Each star has a position, a depth layer (3 layers with parallax factors 0.15 / 0.4 / 0.75 relative to camera movement), brightness, and colour temperature (2500–12000 K, mapped to RGB via a small blackbody lookup function).
  - Draw stars as tiny additive-blended quads (a 1–3 px soft dot made with `Pixmap` at startup), with wrap-around tiling so the field is infinite.
  - Keep `background_1` as a very dim nebula base (tint alpha ≈ 0.35) and drop `background_2/3` scrolling.
  - Keep the data (`StarData` arrays) in a structure a future shader or system can read (positions + temperature), because M3/M4 will Doppler-shift and aberrate them.
- acceptance:
  - [x] Unit tests: `blackbodyRgb(kelvin)` is monotone in blue/red ratio, and the same seed gives the same star set
  - [x] Screenshot via the ST-005 harness with `--thrust` shows parallax-readable stars, and the agent has looked at it
  - [x] No per-frame allocations in the draw loop
  - [x] `./gradlew core:test` passes
- playtest: The background reads as space with depth, and the ship's motion is felt through parallax without distracting from asteroids.

### ST-013 Bring docs in line after M1
- status: done (2026-11-01, b586f78)
- result: architecture.md and CLAUDE.md updated for M1; known issues cut to wrap teleport (M2) and the static id counter.
- size: S
- agent: junior-dev
- depends: ST-003, ST-004, ST-006, ST-007, ST-011, ST-012
- files: `docs/tech/architecture.md`, `CLAUDE.md`
- goal: Update the "Known issues" list (remove the fixed items, reference their card IDs), the system table and the component list, so they match the code after M1.
- acceptance:
  - [x] Every statement in the updated sections can be traced to the current code

### ST-016 Per-ship rotation tuning via ShipHandlingComponent
- status: done (2026-11-01, 3833b76)
- result: `ShipHandlingComponent` (kp, kd, max angular accel/velocity) read per substep by `PhysicsSystem`; defaults stay in `ShipRotationController`.
- size: S
- agent: kotlin-dev
- depends: ST-004
- files: create `component/ShipHandlingComponent.kt`, edit `system/ShipRotationController.kt`, `system/PhysicsSystem.kt`, `provider/PlayerEntityProvider.kt`, tests
- goal: Follow-up from ST-004. Move the rotation gains (kp, kd, max angular acceleration, max angular velocity) from `ShipRotationController` constants into a `ShipHandlingComponent` (follow the component convention), with the current values as defaults, so enemies and ship variants can feel different. `PhysicsSystem` reads the component when present and falls back to the defaults otherwise.
- acceptance:
  - [x] The player entity gets a `ShipHandlingComponent` with the defaults
  - [x] Test: an entity with a lower max angular velocity never exceeds it during a 180° turn
  - [x] `./gradlew core:test` passes
- playtest: The player ship feels the same as after ST-004. Also check the feel with the real `disc_green` sprite size, since the tests used a 2×2 box.

### ST-017 Deterministic screenshots with --seed
- status: done (2026-11-01, 991c9e4)
- result: `--seed` feeds one `RandomXS128` in DI (AsteroidProvider, DeathSystem); screenshot runs use a fixed 1/60 s delta; two `--seed=42` runs gave byte-identical PNGs.
- size: S
- agent: kotlin-dev
- depends: ST-005
- files: `debug/ScreenshotHarness.kt` (LaunchOptions), `Game.kt`, providers/systems that use randomness, `LaunchOptionsTest.kt`, `docs/tech/dev-workflow.md`
- goal: Follow-up from ST-005. Add `--seed=<long>` so two screenshot runs with the same args give the same image. Route all gameplay randomness (asteroid spawn, starfield, splits) through one seeded `RandomXS128` registered in the DI context, instead of `MathUtils.random` and `Random()` calls.
- acceptance:
  - [x] Parsing test for `--seed`
  - [x] Running the screenshot command twice with `--seed=42 --frames=60` gives identical PNGs (run once to verify)
  - [x] `./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin` passes

### ST-018 PlayerComponent mapper and property
- status: done (2026-11-01, be183ef)
- result: `PlayerComponent` follows the component convention; MinimapSystem uses `playerCmpOptional`.
- size: S
- agent: junior-dev
- depends: —
- files: `component/PlayerComponent.kt`, `system/MinimapSystem.kt`
- goal: Follow-up from ST-011. `PlayerComponent` has no `playerMapper` or `Entity.playerCmp`/`playerCmpOptional` properties, unlike the other components. Add them following the component convention in CLAUDE.md. Then switch `MinimapSystem`'s player detection (currently SHIP type + PLAYER faction) to `entity.playerCmpOptional != null`.
- acceptance:
  - [x] The mapper and properties exist, and MinimapSystem uses them
  - [x] `./gradlew core:test` passes

---

## M2 Arena boundary field

- note: Physics source is `docs/research/relativity-effects.md` §1, §3 and §7 (items 1–6). **Step-input rule, decided in ST-014:** every per-step physics input goes through `PhysicsStepListener.beforeStep(dt)`. That covers thrust force, field force or velocity kick, and (still inline in `PhysicsSystem`) the rotation impulse. `world.clearForces()` runs after **every** `world.step`. This replaces the old "thrust applied once per frame, persists across substeps, cleared after the loop" scheme. Per step the result is the same: each step still integrates the thrust exactly once. The shimmer uses `ARENA_BOUNDARY_COLOR` (orange, `ui/Colors.kt`) rather than the research doc's cyan, so it matches the minimap frame.

### ST-014 Plan M2 (boundary field) from research
- status: done (2026-11-01)
- result: Created ST-019 … ST-029 (ST-020 and ST-023 are `needs-user`). `SPEED_OF_LIGHT = 60` goes into M2 (ST-019) so the field is tuned against the final c.
- size: S
- agent: planner
- depends: —
- files: `docs/backlog.md`
- goal: Once `docs/research/relativity-effects.md` exists, turn its "Arena boundary force field" section into M2 cards. These are a pure force-law function plus tests, a `BoundaryFieldSystem` placed before `PhysicsSystem`, removal of the `WrapSystem` and `WrapComponent` usage, projectile lifetime replacing out-of-bounds removal, and a field shimmer visual.

### ST-019 Set SPEED_OF_LIGHT to 60 u/s
- status: done (2026-11-01, 5add849)
- result: c = 60 u/s with a test guarding the Box2D cap; docs updated.
- size: S
- agent: junior-dev
- depends: —
- files: `core/src/main/kotlin/com/github/pepek42/asteroids/physics/PhysicsConstants.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/PhysicsConstantsTest.kt`, `CLAUDE.md` (Units paragraph), `docs/tech/architecture.md` ("Box2D speed cap" bullet)
- goal: `SPEED_OF_LIGHT = 250` is above Box2D's 2 u/step cap (90 u/s at 45 Hz), so lasers are silently clamped. Research §1 recommends c = 60 (1.33 u/step, 67 % of the cap). This is planned around the recommended answer to ST-020.
- spec: Set `const val SPEED_OF_LIGHT = 60f`. Replace the KDoc with: "World units per second. Must stay ≤ 0.75 × 2 u/step × 45 Hz ≈ 67 so Box2D's b2_maxTranslation never clamps (research §1)." Lasers keep `projectileSpeed = SPEED_OF_LIGHT` in `PlayerEntityProvider`, so they now fly at 60 u/s. In `CLAUDE.md` and `architecture.md`, replace the sentences saying 250 exceeds the cap with "c = 60 u/s fits under the 90 u/s Box2D cap".
- acceptance:
  - [x] Test: `SPEED_OF_LIGHT * PhysicsSystem.PHYSICS_UPDATE_INTERVAL <= 0.75f * 2f`
  - [x] `./gradlew core:test` passes
- playtest: Lasers look a bit slower than before (60 vs the clamped 90 u/s) and are still easy to aim.

### ST-020 Owner decision: value of c
- status: done (2026-11-02)
- result: Owner: "Pick some c value for now. Run some sim games and try to balance whole physics so that player still will have some chance to play." c = 60 u/s stays the default. The balance pass is ST-101.

### ST-021 Per-step physics listener hook; thrust applied per step
- status: done (2026-11-01, a9015c0)
- result: PhysicsStepListener hook added; thrust is applied in beforeStep and moveForcesApplied is gone.
- size: S
- agent: kotlin-dev
- depends: —
- files: create `physics/PhysicsStepListener.kt`, edit `system/PhysicsSystem.kt`, `system/MoveSystem.kt`, `component/BodyComponent.kt`, `screen/PlayScreen.kt`, `system/MoveSystemTest.kt`
- goal: The boundary field (and later the relativistic integrator) must act once per physics step. Today thrust is applied once per frame in `MoveSystem.update` and persists through the substeps until `clearForces` after the loop. Replace this with a per-step hook (research §3 "Box2D interaction", points 1–2). Per-step behaviour stays the same.
- spec:
  - `interface PhysicsStepListener { fun beforeStep(dt: Float); fun afterStep(dt: Float) }` in package `com.github.pepek42.asteroids.physics`.
  - `PhysicsSystem(world, engine, private val stepListeners: List<PhysicsStepListener> = emptyList())`. Add `const val PHYSICS_SUBSTEPS = 1` to its companion. The loop becomes:
    ```
    while (accumulator >= PHYSICS_UPDATE_INTERVAL) {
        preProcess()                                   // prev transforms only, once per tick
        val subDt = PHYSICS_UPDATE_INTERVAL / PHYSICS_SUBSTEPS
        repeat(PHYSICS_SUBSTEPS) {
            stepListeners.forEach { it.beforeStep(subDt) }
            applyRotation()                            // unchanged angular impulse
            world.step(subDt, 6, 2)
            stepListeners.forEach { it.afterStep(subDt) }
            world.clearForces()
        }
        accumulator -= PHYSICS_UPDATE_INTERVAL
    }
    ```
    Delete the `runSimulation` flag and the post-loop `clearForces`.
  - `MoveSystem` becomes `EntitySystem(), PhysicsStepListener`. It caches the `allOf(MoveComponent, BodyComponent)` family in `addedToEngine`. `update` does nothing. `beforeStep` applies the existing `applyMainThrusters` to every entity (same force, `applyForceToCenter`). `afterStep` does nothing.
  - Delete `BodyComponent.moveForcesApplied` and its reset in `PhysicsSystem.preProcess`.
  - `PlayScreen.setupEcs`: keep `val moveSystem = MoveSystem()`, add it at the same position, and construct `PhysicsSystem(world, engine, listOf(moveSystem))`.
- acceptance:
  - [x] `MoveSystemTest` thrust tests call `moveSystem.beforeStep(dt)` instead of `update`. The "once per step" test asserts that two `beforeStep` calls apply the force twice and that `update` alone applies nothing
  - [x] New test with a real `World`: a 1-unit box with thrusters = 1 driven through `PhysicsSystem` for 45 steps reaches `|v| = MAIN_ENGINE_THRUST / mass × 1 s` within 1 %, both with 1 step per frame and with 3 steps per frame (frame time `3 × PHYSICS_UPDATE_INTERVAL + 1e-4`)
  - [x] The existing rotation tests still pass unchanged
  - [x] `./gradlew core:test` passes
- playtest: Thrust feels exactly as before.

### ST-022 Boundary field force law (pure functions)
- status: done (2026-11-01, e0fc27a)
- result: BoundaryField.kt implements research §3 force law; 11 tests incl. Euler bounce simulation.
- size: S
- agent: kotlin-dev
- depends: —
- files: create `core/src/main/kotlin/com/github/pepek42/asteroids/physics/BoundaryField.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/BoundaryFieldTest.kt`
- goal: The force law from research §3 as GL-free functions with tests. It's an acceleration field, independent of mass.
- spec:
  - Top-level constants: `FIELD_SKIN = 8f`, `FIELD_EXPONENT = 2f`, `FIELD_ACCEL_MAX = 3000f`, `FIELD_DAMPING = 1f` (per ST-023 recommendation), `FIELD_BACKSTOP_RESTITUTION = 0.9f`, `FIELD_PHOTON_MIRROR_Q = 0.5f`, `FIELD_USE_BODY_RADIUS = true`.
  - `data class BoundaryFieldParams(skin, exponent, accelMax, damping, useBodyRadius)` with those defaults.
  - Walls of `[0, W] × [0, H]`: left `d = x − r`, n̂ = (1, 0); right `d = W − x − r`, n̂ = (−1, 0); bottom `d = y − r`, n̂ = (0, 1); top `d = H − y − r`, n̂ = (0, −1). `r` = body radius, or 0 when `useBodyRadius` is false.
  - `qᵢ = clamp((s − dᵢ)/s, 0, 1)`.
  - `fun boundaryAcceleration(x, y, vx, vy, rBody, mapW, mapH, params, out: Vector2): Vector2`:
    `a = Σᵢ A·qᵢⁿ·n̂ᵢ − Σᵢ k_d·qᵢ·min(0, v·n̂ᵢ)·n̂ᵢ`. Writes into `out` and returns it, with no allocation. `v` is the Newtonian velocity in M2 and the proper velocity `u` in M3 (ST-038).
  - `fun boundaryDepthQ(x, y, rBody, mapW, mapH, params): Float` = max qᵢ (for shimmer and photon mirrors).
  - `fun boundaryPotential(q, params) = A·s·q^(n+1)/(n+1)` (per unit mass).
  - `fun newtonianTurnAroundDepth(vNormal, params): Float` = `s·q*` where `q* = ((n+1)·v²/(2·A·s))^(1/(n+1))`, capped at `s` (≥ s means the backstop catches it).
- acceptance:
  - [x] All tests build an explicit `BoundaryFieldParams(skin = 8f, exponent = 2f, accelMax = 3000f, …)` instead of relying on defaults, so ST-046 can change `FIELD_SKIN` without breaking them
  - [x] Test: zero acceleration at (50, 50) and at (s + r + 0.01, 50) on a 100×100 map
  - [x] Test: symmetry. `a` at (x, 50) equals −`a` at (100 − x, 50) for x in 0.5..7.5
  - [x] Test: |a| is monotonically non-decreasing as x goes from 8 to 0 (zero velocity)
  - [x] Test: corner. At (2, 2), `a.x == a.y > 0`, and each equals the single-wall value at (2, 50)
  - [x] Test: body radius. With r = 2.25, x = 9 gives a > 0, and with `useBodyRadius = false` it gives 0
  - [x] Test: `newtonianTurnAroundDepth` gives 1.47 ± 0.02 for 10 u/s, 3.06 ± 0.02 for 30 u/s, and 4.87 ± 0.03 for 60 u/s (card originally said 7.02, an arithmetic slip)
  - [x] Test: a semi-implicit Euler simulation (dt = 1/45, damping 0, point body entering from x = 20 toward x = 0) reaches a maximum depth within `v·dt` of `newtonianTurnAroundDepth` for v = 10, 30, 60, and leaves with speed within 2 % of the entry speed
  - [x] Test: with damping 1, the exit speed is strictly lower than the entry speed and above 50 % of it for v = 30
  - [x] `./gradlew core:test` passes

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

### ST-024 Projectile lifetime; out-of-bounds removal becomes a safety net
- status: done (2026-11-01, c96e32f)
- result: LifetimeComponent/LifetimeSystem added; lasers fade after 2 s; player is clamped, never removed.
- size: S
- agent: kotlin-dev
- depends: —
- files: create `component/LifetimeComponent.kt`, `system/LifetimeSystem.kt`, `weapon/ProjectileConstants.kt`; edit `provider/WeaponProjectileProvider.kt`, `system/RemoveSystem.kt`, `screen/PlayScreen.kt`; tests
- goal: Once the field bends projectiles back (ST-025), they never leave the map, so they need a clock (research §3 "Projectiles: lifetime"). The out-of-bounds check stays only as a safety net, and the player is never removed.
- spec:
  - `enum class LifetimeClock { WORLD, PROPER }`. `LifetimeComponent(var remaining: Float = 0f, var total: Float = 0f, var clock: LifetimeClock = WORLD, var bouncesLeft: Int = -1)` is `Poolable` and follows the component convention (`lifetimeMapper`, `lifetimeCmp`, `lifetimeCmpOptional`). `bouncesLeft = -1` means unlimited. Only the photon mirror decrements it (ST-039). `LifetimeSystem` only handles time.
  - `ProjectileConstants.kt`: `PHOTON_WORLD_LIFETIME = 2f`, `PHOTON_MAX_BOUNCES = 3`, `SLUG_PROPER_LIFETIME = 1.2f`, `LIFETIME_FADE_SECONDS = 0.2f`.
  - `LifetimeSystem : IteratingSystem(allOf(LifetimeComponent))`, registered just before `DeathSystem`. It does `remaining -= dt`. PROPER is treated like WORLD until ST-040. When `remaining <= 0` and there's no `RemoveComponent`, it adds one. If the entity has a `SpriteComponent` and `remaining < LIFETIME_FADE_SECONDS`, it calls `sprite.setAlpha(remaining / LIFETIME_FADE_SECONDS)`.
  - `WeaponProjectileProvider`: lasers get `LifetimeComponent(remaining = total = PHOTON_WORLD_LIFETIME, clock = WORLD, bouncesLeft = PHOTON_MAX_BOUNCES)`.
  - `RemoveSystem`: rename `REMOVE_BUFFER_MULTIPLAYER` to `REMOVE_BUFFER_FRACTION = 0.20f`. When out of bounds: if `entity.playerCmpOptional != null`, `setTransform` the body to the position clamped into `[1, W−1] × [1, H−1]`, zero its linear velocity, and log a warning (throttled via `defaultLoggingUtils.tryLogging`). Never remove the player. Otherwise add `RemoveComponent` and log a throttled warning. Keep the `wrapMapper` exemption for now (ST-025 deletes it).
- acceptance:
  - [x] Test (`PooledEngine` + `LifetimeSystem`): lifetime 0.5. After `update(0.3)` there's no `RemoveComponent`. After another `update(0.3)` there is
  - [x] Test: a player entity with a real body at (130, 50) on a 100×100 map is moved inside and not removed. A non-player body entity at (130, 50) gets `RemoveComponent`. A body at (110, 50) is left alone (inside the 20 % margin)
  - [x] `./gradlew core:test` passes
- playtest: Lasers fade out after about 2 s instead of vanishing at the map edge.

### ST-025 BoundaryFieldSystem replaces the wrap teleport
- status: done (2026-11-01, df90e41)
- result: Edge field + backstop replace wrap; reviewer approved, minors in ST-052.
- size: M
- agent: kotlin-dev
- depends: ST-021, ST-022, ST-024
- files: create `system/BoundaryFieldSystem.kt`; edit `component/BodyComponent.kt`, `screen/PlayScreen.kt`, `system/RemoveSystem.kt`, `provider/PlayerEntityProvider.kt`, `provider/AsteroidProvider.kt`, `provider/WeaponProjectileProvider.kt`, `weapon/WeaponType.kt`; delete `system/WrapSystem.kt`, `component/WrapComponent.kt`; tests
- goal: The wrap teleport is confusing (owner's todo). Replace it with the mass-independent repulsive field from ST-022, applied per step through the ST-021 hook, plus a hard backstop. Projectiles bend back too.
- spec:
  - `BodyComponent`: add `var boundingRadius = -1f` (reset to −1). Add a helper `fun computeBoundingRadius(body: Body): Float` = max over fixtures of (`CircleShape`: `position.len() + radius`; `PolygonShape`: max vertex length). `BoundaryFieldSystem` fills it lazily the first time it sees the body.
  - `class BoundaryFieldSystem(private val mapProvider: MapProvider, private val params: BoundaryFieldParams = BoundaryFieldParams()) : EntitySystem(), PhysicsStepListener`, with family `allOf(BodyComponent, TransformComponent)`. `update` does nothing. Register it in `setupEcs` directly before `PhysicsSystem` and pass it as the second listener: `PhysicsSystem(world, engine, listOf(moveSystem, boundaryFieldSystem))`.
  - `beforeStep(dt)`: for each body, skip `StaticBody`. Compute `a = boundaryAcceleration(pos, vel, boundingRadius, mapW, mapH, params, scratch)`. If it's zero, skip. For a `DynamicBody`: `applyForceToCenter(a.x·mass, a.y·mass, true)`. For a `KinematicBody` (projectiles): `setLinearVelocity(v + a·dt)`. Use preallocated scratch vectors, with no per-step allocation.
  - `afterStep(dt)`, the backstop: for each wall with `dᵢ <= 0` (the same `dᵢ`, radius included, as the force law), `setTransform` so that `dᵢ = 0.01`. If the normal velocity is outward (`v·n̂ᵢ < 0`), set `v_n → −FIELD_BACKSTOP_RESTITUTION·v_n`. Log at debug level via `tryLogging`.
  - Delete `WrapSystem`, `WrapComponent`, `wrapMapper`, every `with<WrapComponent>()`, the `wrapMapper` check in `RemoveSystem`, and `WeaponType.trapped`, so it becomes `enum class WeaponType { LASER }`.
  - `AsteroidProvider.spawnAsteroids`: the spawn ring radius `radius * 0.85f` becomes `min(center.x, center.y) − FIELD_SKIN − AsteroidSize.LARGE.radius − 1f`, so asteroids don't spawn inside the skin.
- acceptance:
  - [x] Test (real `World`, `PhysicsSystem` with this listener, 100×100 map): a dynamic circle (r = 1) at (85, 50) moving +x at 30 u/s never has `x + r > 100` during 3 s, and ends with `vx < 0`
  - [x] Test: mass independence. Circles of density 1 and 100, same radius and speed, reach the same maximum x within 0.05
  - [x] Test: a kinematic sensor body (r = 0.1) at 60 u/s toward the right wall turns back before x = 100
  - [x] Test: backstop. A body at x = 100.5 moving +x is inside after one step and moving −x
  - [x] `grep -r "WrapComponent\|WrapSystem\|trapped" core/src` returns nothing
  - [x] `./gradlew core:test` passes
- playtest: Flying into the edge curves you back smoothly with no teleport. Asteroids drift off the edges. Lasers fired at the wall curve back and can hit asteroids on the rebound.

### ST-026 Camera may show the field skin; minimap shows the skin
- status: done (2026-11-01, 45fb6fc)
- result: clampCameraAxis with margin plus minimap skin rectangle; 4 tests.
- size: S
- agent: junior-dev
- depends: ST-025
- files: `system/CameraSystem.kt`, `system/MinimapSystem.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/system/CameraClampTest.kt`
- goal: The camera is clamped exactly to the map, so the boundary field is barely visible. Let the view extend `FIELD_SKIN / 2` past the edge, and draw the skin on the minimap (research §3).
- spec:
  - In `CameraSystem.kt` add a top-level `fun clampCameraAxis(target: Float, halfView: Float, mapSize: Float, margin: Float): Float`. Let `lo = halfView − margin` and `hi = mapSize − halfView + margin`. If `lo > hi`, return `mapSize / 2`. Otherwise return `target.coerceIn(lo, hi)`. `handleCameraPosition` uses it for x and y with `margin = CAMERA_ARENA_MARGIN`, where `private const val CAMERA_ARENA_MARGIN = FIELD_SKIN / 2`.
  - `MinimapSystem.update`: after the arena border, in the same `Line` pass, draw a second rectangle inset by `FIELD_SKIN` on each side, in a private copy of `ARENA_BOUNDARY_COLOR` with alpha 0.35 (`ARENA_BOUNDARY_COLOR.cpy().apply { a = 0.35f }` stored in a `private val`). Don't mutate the shared colour.
- acceptance:
  - [x] Tests: `clampCameraAxis(0f, 24f, 100f, 4f) == 20f`; `clampCameraAxis(100f, 24f, 100f, 4f) == 80f`; `clampCameraAxis(50f, 24f, 100f, 4f) == 50f`; `clampCameraAxis(0f, 60f, 100f, 4f) == 50f`
  - [x] `./gradlew core:test` passes
- playtest: Near the edge you can see a strip of space beyond the arena. The minimap shows an inner rectangle where the field starts.

### ST-027 Shimmer impact buffer and emission from the field
- status: done (2026-11-01, 2da47e8)
- result: ShimmerImpacts buffer (16 slots, throttled, decay eviction) fed from field and backstop; 7 tests.
- size: S
- agent: kotlin-dev
- depends: ST-025
- files: create `render/ShimmerImpacts.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/render/ShimmerImpactsTest.kt`; edit `system/BoundaryFieldSystem.kt`, `screen/PlayScreen.kt`
- goal: This is the CPU half of the shimmer (research §3 "Shimmer visual"). It's a fixed-size buffer of recent wall impacts that the shader (ST-028) uploads as `u_impacts[16]`.
- spec:
  - `class ShimmerImpacts(val capacity: Int = SHIMMER_MAX_IMPACTS)` with constants `SHIMMER_MAX_IMPACTS = 16`, `SHIMMER_DECAY = 0.6f`, `SHIMMER_IMPACT_LIFETIME = 1.5f`, `SHIMMER_REFRESH_SECONDS = 0.25f`. Its state is `var time = 0f` (advanced by `BoundaryFieldSystem.afterStep(dt)`), `val data = FloatArray(capacity * 4)` holding (x, y, startTime, strength), and `var count`. It also keeps an `IntFloatMap` of last emit time per entity id.
  - `fun emit(entityId: Int, x: Float, y: Float, strength: Float, force: Boolean = false)`: skip if `!force` and this id emitted less than `SHIMMER_REFRESH_SECONDS` ago. If an empty slot exists, use it. Otherwise replace the slot with the smallest `strength·exp(−age/SHIMMER_DECAY)`.
  - `fun prune()`: drops entries older than `SHIMMER_IMPACT_LIFETIME` and compacts the array.
  - `BoundaryFieldSystem` takes a `ShimmerImpacts` (created in `PlayScreen`, which later also gives it to the renderer). In `afterStep`, for each body with `q = boundaryDepthQ(...) > 0.1`, it emits at the centre projected onto the nearest wall, with strength `q^FIELD_EXPONENT` and id `baseInfoCmp.id`. Backstop hits emit with `strength = 1.5f, force = true`. It calls `prune()` once per `afterStep`.
- acceptance:
  - [x] Tests: the same id twice within 0.25 s stores one impact, and after 0.3 s stores two. `force = true` bypasses the throttle. 20 emits into capacity 16 keep 16 and evict the weakest decayed one. `prune` removes entries older than 1.5 s
  - [x] No allocation in `emit` or `prune` (arrays are preallocated)
  - [x] `./gradlew core:test` passes

### ST-028 Field shimmer shader
- status: done (2026-11-01, fbf4f82)
- result: Shimmer shader renders edge band and ripples; screenshot checked; reviewer approved, polish in ST-053.
- size: M
- agent: kotlin-dev
- depends: ST-027
- files: create `assets/shaders/field_shimmer.vert`, `assets/shaders/field_shimmer.frag`, `render/FieldShimmerRenderer.kt`; edit `system/RenderSystem.kt`, `screen/PlayScreen.kt`
- goal: Make the field visible: a faint honeycomb that brightens with depth, plus ripples from impacts that expand at c (research §3 "Shimmer visual"). It uses `ARENA_BOUNDARY_COLOR`.
- spec:
  - GLSL ES 1.00 (TeaVM-safe): `#ifdef GL_ES precision mediump float; #endif`. Attributes and uniforms are the `SpriteBatch` ones (`a_position`, `a_color`, `a_texCoord0`, `u_projTrans`). The vertex shader passes `v_world = a_position.xy`.
  - Fragment uniforms: `u_mapSize` (vec2), `u_skin`, `u_time`, `u_baseColor` (vec3 = `ARENA_BOUNDARY_COLOR` rgb), `u_baseAlpha` (`SHIMMER_BASE_ALPHA = 0.08`), `u_c` (= `SPEED_OF_LIGHT`), `u_impacts[16]` (vec4), and `u_impactCount` (int; loop `for (int i = 0; i < 16; i++) { if (i >= u_impactCount) break; … }`).
  - Fragment math: `d` = distance from `v_world` to the nearest wall (negative outside the map, clamped to 0). `q = clamp((u_skin − d)/u_skin, 0, 1)`. `hex` = standard two-offset-grid hexagon edge mask (cell size 1.2 u) in 0..1, modulated by `0.6 + 0.4·sin(u_time·2 + v_world.x·0.3)`. `base = u_baseAlpha·q²·hex`. For each impact `(x, y, t0, s)`: `ρ = distance(v_world, xy)`, `age = u_time − t0`, `ring = s·exp(−((ρ − u_c·age)/0.6)²)·exp(−age/0.6)`, `spot = s·exp(−ρ²/1.44)·max(0, 1 − age/0.2)`. `I = base + Σ(ring + spot)·q_soft`, with `q_soft = max(q, 0.15)` so ripples are visible across the skin. Colour is `mix(u_baseColor, vec3(1.0), clamp(Σs·(ring+spot), 0, 1))`, alpha `clamp(I, 0, 1)`.
  - `FieldShimmerRenderer(impacts: ShimmerImpacts, mapProvider: MapProvider) : Disposable`. It loads the shader via `Gdx.files.internal("shaders/…")` with `ShaderProgram.pedantic = false`, and makes a 1×1 white `Texture` from a `Pixmap`. `render(batch)`: set `batch.shader`, the additive blend (`GL_SRC_ALPHA, GL_ONE`), and the uniforms (`setUniform4fv("u_impacts", impacts.data, 0, 64)`). Draw 4 edge strips covering the band from `−FIELD_SKIN/2` outside to `FIELD_SKIN` inside each wall. Then restore the default shader and blend. If `!program.isCompiled`, log the error once and fall back to drawing the same strips with the default shader, tinted `ARENA_BOUNDARY_COLOR` at alpha `0.06 + 0.04·sin(2·time)`.
  - `RenderSystem` takes the renderer (constructor param) and calls `render(batch)` after `super.update(deltaTime)` inside the existing `batch.use` block. `PlayScreen` creates it and disposes it via the existing `Disposable` sweep (make `RenderSystem` `Disposable` and forward).
- acceptance:
  - [x] `./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin` passes
  - [x] A screenshot via the ST-005 harness (`--seed=42 --frames=240 --thrust`) shows the shimmer band near an edge, and the agent has looked at it. If the ship doesn't reach an edge in 240 frames, add a temporary `--frames` value that does, and note it on the card
  - [x] No per-frame allocation in `render`
- playtest: The edge reads as a soft energy wall. Bumping into it sends a ripple along the wall at visibly finite speed. The band is not distracting in the middle of the arena.

### ST-029 Bring docs in line after M2
- status: done (2026-11-01, 5e1bce1)
- result: architecture.md system table renumbered and synced with M2 code; CLAUDE.md gained a Rendering paragraph.
- size: S
- agent: junior-dev
- depends: ST-019, ST-021, ST-024, ST-025, ST-026, ST-028
- files: `docs/tech/architecture.md`, `CLAUDE.md`
- goal: Update the system table (WrapSystem gone, BoundaryFieldSystem and LifetimeSystem added), the component list (LifetimeComponent added, WrapComponent removed, `BodyComponent.boundingRadius`), the "Physics loop details" (the per-step listener hook, clearForces per step, no `moveForcesApplied`), RemoveSystem as a safety net, c = 60, and remove known issue 1 (wrap teleport, fixed by ST-025).
- acceptance:
  - [x] Every statement in the updated sections can be traced to the current code

---

### ST-051 WeaponProjectileProviderTest asserts laser lifetime
- status: done (2026-11-01, 35f9134)
- result: Laser provider test asserts LifetimeComponent values.
- size: S
- agent: junior-dev
- depends: ST-024
- files: `core/src/test/kotlin/com/github/pepek42/asteroids/provider/WeaponProjectileProviderTest.kt`
- goal: The test is a TODO stub (follow-up from ST-024).
- spec: Build a laser via `WeaponProjectileProvider` with a `PooledEngine` and real `World`; assert it has `LifetimeComponent` with `remaining == total == PHOTON_WORLD_LIFETIME`, `clock == WORLD`, `bouncesLeft == PHOTON_MAX_BOUNCES`.
- acceptance:
  - [x] Test exists and passes; `./gradlew core:test` passes

### ST-052 BoundaryFieldSystem test gaps and tidy-up
- status: done (2026-11-01, 642af96)
- result: Corner and top/bottom wall tests added; imports tidied.
- size: S
- agent: junior-dev
- depends: ST-025
- files: `core/src/test/kotlin/com/github/pepek42/asteroids/system/BoundaryFieldSystemTest.kt`, `system/BoundaryFieldSystem.kt`, `screen/PlayScreen.kt`
- goal: Review minors from ST-025.
- spec: Add a corner backstop test (body at (100.5, 100.5) moving (+10, +10) ends inside with both velocity components negative) and a top/bottom wall turn-back test. In `fieldIsMassIndependent`, keep the `Body` reference instead of looking it up via `getBodies`. Replace the fully qualified `com.badlogic.gdx.utils.Array()` with an import or `lateinit`; sort imports in `BoundaryFieldSystem.kt` and `PlayScreen.kt`.
- acceptance:
  - [x] New tests pass; `./gradlew core:test` passes

### ST-053 Shimmer polish: ripple clipping, precision, emission threshold
- status: done (2026-11-01, 70efe24)
- result: Ripples fade past the skin, highp+pulse phase uniform, weak impacts on inward motion; screenshot checked.
- size: S
- agent: kotlin-dev
- depends: ST-028
- files: `render/FieldShimmerRenderer.kt`, `assets/shaders/field_shimmer.frag`, `system/BoundaryFieldSystem.kt`
- goal: Follow-ups from ST-028 review and screenshots.
- spec: (1) Ripples are cut off by a hard straight edge at the inner border of the strips (`FIELD_SKIN` inside). Extend the strips to `2·FIELD_SKIN` inside and fade ripple contribution smoothly to 0 between `FIELD_SKIN` and `2·FIELD_SKIN` depth; base honeycomb unchanged. (2) In the frag shader use `highp` when `GL_FRAGMENT_PRECISION_HIGH` is defined; pass `u_time` from `impacts.time` but wrap the pulse phase CPU-side (`time % (PI)`) as a separate `u_pulsePhase` uniform. (3) In normal play the ship rarely reaches `q > 0.1` (it turns back early); emit a weak impact (strength `q²`, throttled as now) for any body with `q > 0.05` and normal velocity into the wall, so bumping the edge visibly ripples.
- acceptance:
  - [x] Screenshot with a forced impact shows no straight cut at the inner band edge (agent looks at it; remove the forcing hack after)
  - [x] `./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin` passes
- playtest: Bumping into the edge at moderate speed produces a visible ripple.

## M3 Relativity MVP

- note: Source is `docs/research/relativity-effects.md` §2, §4 Tier 1 (E1–E7) and §7 items 7–15. **Differences from `docs/design/game-design.md` §6:** the research promotes relativistic velocity addition for projectiles (GDD "Maybe") to MVP because Galilean addition breaks the c cap. It also moves length contraction (GDD "Next") into MVP in world view, and adds γ-mass collisions, speed zoom and a mild searchlight (k = 1) to MVP. The HUD shows ship time τ and world time t, which answers GDD §11 Q2 with "both". The GDD parking-lot idea "fast = bullet time" is physically backwards (research E2), so it's not planned. The GDD isn't edited here. ST-049 reconciles it once the owner has answered the questions below. **Feature flag:** `RELATIVITY_ENABLED` (ST-037). When false, providers don't attach `RelativisticComponent` and the Newtonian M2 path runs unchanged. (Superseded by ST-056: the flag becomes a runtime setting, and providers always attach the component.) **Owner questions** ST-030 … ST-034 are `needs-user`. Every other card is planned around their recommended answers and isn't blocked by them.
- note (2026-11-01, owner playtest after M2 + ST-037): "Feels a bit chaotic, maybe the arena should be bigger" → ST-030 answered (c), and ST-046 now also calms a0 and the asteroid spawn. "Relativity debug options on the side" → ST-056 (runtime `RelativitySettings`), ST-057 (plumbing), ST-059 (panel), ST-060 (vector overlay). Controls are undecided → ST-058/ST-061/ST-062 make schemes (a)/(b)/(c) switchable at runtime, and ST-063 asks for the default. γ: no gameplay cap (ST-032), safety bound 50 (ST-055). **From ST-056 on**, runtime code reads c, a0, the γ cap, field params and relativity/field on/off from the DI singleton `RelativitySettings`, not the consts (the consts are its defaults). Cards written earlier (ST-038 … ST-048) that say `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE` or `RELATIVITY_ENABLED` mean the corresponding setting once ST-056 is done.

- note (2026-11-02, owner interview, game-design.md §11): **The game is perceived from the pilot's frame.** The game clock is the pilot's proper time (research E21): the arena advances `γ·dt_real` per frame, capped at about 10× (declared FAKE past the cap, shown on the HUD), and the player's guns and turning run on real time (ST-068, ST-069, ST-040). **Pilot view is the main rendering** and is pulled forward from M4 into this milestone (E8 light delay + E9 aberration: ST-070 … ST-078, default on). The minimap stays true arena positions ("tactical computer"). Aim help is a lead marker (ST-078). Also decided: braking boost (ST-066), shots leave the arena instead of bouncing (ST-067, reworks ST-039), ramming yes (ST-033), photon laser default (ST-034). The alternative control modes (auto-aim + auto-shoot, targeting trance) are **parked** at the end of the backlog.

### ST-015 Plan M3 (relativity MVP) from research
- status: done (2026-11-01)
- result: Created ST-030 … ST-049 for M3 (ST-030 … ST-034 are `needs-user`) and the M4 planning placeholder ST-050. The choice of c moved to M2 (ST-019/ST-020).
- size: S
- agent: planner
- depends: ST-014
- files: `docs/backlog.md`
- goal: Turn the MVP tier of `docs/research/relativity-effects.md` into cards. Expected scope: the choice of `c` and the physics-rate change, a pure `Relativity.kt` math module with tests, a relativistic momentum integrator on top of Box2D, proper time per entity, a β/γ HUD, and Doppler on the starfield. Questions from the research doc that are really the owner's go into `needs-user` cards, planned around the recommended answer. Later milestones (M4–M7) are planned when M3 is about half done.

### ST-030 Owner decision: arena size
- status: done (2026-11-01)
- result: Owner chose (c) 200×200 with `FIELD_SKIN = 10` ("feels a bit chaotic, maybe arena should be bigger"). Recorded in game-design.md §11. ST-046 implements it and also lowers a0 and rescales the asteroid spawn.
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11; values in ST-046
- goal: Record the owner's choice (research §8 Q2). At c = 60 with 60 u/s² proper acceleration, reaching 0.9c takes about 77 u of straight flight, so 100×100 feels cramped near c. ST-046 implements the recommendation.
- question: How big should the arena be from M3 on? (a) Keep 100×100. (b) 150×150. (c) **200×200 with `FIELD_SKIN = 10` (recommended)**. The asteroid count formula stays the same, so asteroids are sparser.
- answer: (c).
- acceptance:
  - [x] The answer is added to game-design.md §11. If not (c): change ST-046's values (or set it to done with "not needed" for (a))

### ST-031 Owner decision: what time dilation does to gameplay
- status: done (2026-11-02)
- result: Replaced: the game clock is the pilot's proper time (owner interview 2026-11-02, research E21, game-design.md §11). The arena advances γ·dt_real per frame (capped ~10×, FAKE past the cap). Guns and turning run on real time, so they are not dilated from the player's view. None of options (a)–(c) applies. Implemented by ST-068, ST-069 and the rewritten ST-040.
- answer: replaced, game clock = pilot proper time.
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11; constants in `physics/PhysicsConstants.kt` (ST-040)
- goal: Record the owner's choice (research §8 Q3 and Q4, merged). In every option, world time is the game clock (a pilot-time clock, research E21, is parked as a possible race-mode variant). ST-040 implements the recommendation.
- question: How should the ship's own clock (proper time τ, which runs at 1/γ) affect play? (a) **Guns exact (cooldown runs on τ), turning softened (`γ^−0.5`) (recommended)**: speed costs fire rate but stays controllable. (b) Both exact (`1/γ`): purist, and turning at γ = 5 is 5× slower. (c) Neither: τ is only shown on the HUD. **Note (2026-11-01):** there's no gameplay γ cap any more (ST-032), so (a) and (b) get extreme at high γ: fire rate is 1/γ, 1/10 at γ = 10 and nearly frozen at the γ = 50 safety bound. In practice a 200 u arena with a0 = 25 gives γ ≈ 2–3 per straight run (`γ = 1 + d·a0/c²`), and only sustained loops that keep momentum through field bounces go higher. The recommendation stays (a). If high-γ guns feel dead, a softening variant (a′) uses `COOLDOWN_DILATION_EXPONENT = 0.5` or a floor `CLOCK_RATE_MIN = 0.2` on the gun clock.
- acceptance:
  - [ ] The answer is added to game-design.md §11. Constants: (a) `COOLDOWN_DILATION_EXPONENT = 1f`, `ROTATION_DILATION_EXPONENT = 0.5f`; (b) both `1f`; (c) both `0f`

### ST-032 Owner decision: hard γ cap
- status: done (2026-11-01)
- result: Owner: "it should be capped by relativity effects themselves", so there's **no gameplay cap**. A numerical safety bound `GAMMA_MAX_MASSIVE = 50` stays far out (u is stored, so precision is fine) and is exposed in the debug panel. In practice the arena and the field limit γ, since crossing 200 u at about c takes about 3.3 s. Implemented by ST-055. Recorded in game-design.md §11.
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11; `physics/PhysicsConstants.kt`
- goal: Record the owner's choice (research §8 Q6). ST-035/ST-037 implement the recommendation.
- question: Should γ be capped? (a) **Hard cap γ = 10, β ≤ 0.995 (recommended)**: numerically safe and readable. (b) No cap: risky, because float precision and Box2D clamping become an issue above γ ≈ 30. (c) A soft cap where engine efficiency drops with γ: this needs a new card, and is only worth it if (a) feels like an invisible wall.
- answer: (b) no gameplay cap, with the safety bound at 50 instead of 30 (ST-055).
- acceptance:
  - [x] The answer is added to game-design.md §11. For (b), set `GAMMA_MAX_MASSIVE = 30f` (still a safety bound). For (c), ask the planner for a card

### ST-033 Owner decision: ramming at relativistic mass
- status: done (2026-11-02)
- result: Owner chose (a): Box2D mass = γ·m₀ (capped at 8), impact damage from relativistic kinetic energy. Recorded in game-design.md §11. ST-047/ST-048 implement it as planned.
- answer: (a).
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11
- goal: Record the owner's choice (research §8 Q9). ST-047/ST-048 implement the recommendation.
- question: Should a near-c ship hit like a freight train? (a) **Yes: Box2D mass = γ·m₀ (capped at 8) and impact damage from relativistic kinetic energy (recommended)**. (b) No: collisions use rest mass and do no damage.
- acceptance:
  - [ ] The answer is added to game-design.md §11. For (b): set `GAMMA_MASS_ENABLED = false` and mark ST-048 done with "not needed" (or revert it if already done)

### ST-034 Owner decision: default weapon
- status: done (2026-11-02)
- result: Owner chose (a): the photon laser at exactly c with muzzle aberration is the default; the slug at 0.7c comes later as a roguelike upgrade (plan it with M7). Recorded in game-design.md §11. ST-039 implements the laser (field mirrors dropped by the same interview, see ST-067).
- answer: (a).
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11
- goal: Record the owner's choice (research §8 Q10). ST-039 implements the recommendation. The slug weapon isn't planned in M3.
- question: What should the default weapon be? (a) **A photon laser at exactly c, whose direction aberrates with your speed (recommended)**, with a massive slug at 0.7c (which inherits your speed and lives on proper time) as the first roguelike upgrade in M7. (b) The slug as the default. (c) Both from the start.
- acceptance:
  - [ ] The answer is added to game-design.md §11. For (b) or (c), ask the planner for a slug card in M3

### ST-035 Relativity math module (pure functions)
- status: done (2026-11-01, 190ad9f)
- result: Relativity.kt with γ, proper velocity, velocity addition, aberration, Doppler, thrust and γ_rel; 24 tests.
- size: S
- agent: kotlin-dev
- depends: ST-019
- files: create `core/src/main/kotlin/com/github/pepek42/asteroids/physics/Relativity.kt`, edit `physics/PhysicsConstants.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/RelativityTest.kt`
- goal: All special relativity formulas used in M3, in one GL-free, unit-tested file (research §2 "Numerically safe γ", E3, E4, E7). Compute in `Double` internally and use `Float`/`Vector2` at the API. Output goes into caller-provided `Vector2`s, with no allocation.
- spec:
  - Constants in `PhysicsConstants.kt`: `GAMMA_MAX_MASSIVE = 10f`, `GAMMA_MAX_PROJECTILE = 20f`, `val BETA_MAX_MASSIVE = sqrt(1 − 1/GAMMA_MAX_MASSIVE²)` (≈ 0.99499).
  - `gammaFromU(ux, uy, c = SPEED_OF_LIGHT) = sqrt(1 + (ux²+uy²)/c²)`
  - `velocityFromU(ux, uy, c, out)` = u / γ(u)
  - `gammaFromV(vx, vy, c, betaMax = BETA_MAX_MASSIVE) = 1 / sqrt(1 − min(v²/c², betaMax²))`
  - `clampSpeed(v: Vector2, maxSpeed)` in place. `uFromV(vx, vy, c, betaMax, out)` = γ(clamped v)·(clamped v)
  - `clampU(u: Vector2, gammaMax, c)` in place: `u *= min(1, c·sqrt(γmax² − 1)/|u|)`
  - `rapidity(ux, uy, c) = asinh(|u|/c)`
  - `addVelocity(v: Vector2, w: Vector2, c, out)`: the shooter moves at `v` in W, and the muzzle velocity `w` is in the shooter frame. `out = (w_∥ + v + w_⊥/γ_v) / (1 + v·w/c²)`, with ∥ along v̂. If |v| < 1e-6, `out = w`.
  - `aberratePhoton(betaX, betaY, nx, ny, out)`: n̂' is in the shooter frame. `n_∥ = (n'_∥ + β)/(1 + β n'_∥)`, `n_⊥ = n'_⊥/(γ(1 + β n'_∥))`, then renormalise.
  - `dopplerFactor(betaObs: Vector2, betaSrc: Vector2, nx, ny)`, where n̂ is the propagation direction from source to observer: `D = γ_o(1 − β_o·n̂) / (γ_s(1 − β_s·n̂))`.
  - `thrustAccelWorld(a0, nx, ny, vx, vy, gamma, out)`: with n̂' the engine direction, `a = a0·[(n̂'·v̂)v̂ + (n̂' − (n̂'·v̂)v̂)/γ]`. If |v| < 1e-6, `a = a0·n̂'`.
  - `gammaRel(va: Vector2, vb: Vector2, c) = γ_a·γ_b·(1 − va·vb/c²)`
  - Non-finite input: return the zero vector or γ = 1. NaN is guarded at the call sites (ST-037).
- acceptance:
  - [x] Tests (seeded random, 10 000 cases where relevant): |velocityFromU(huge u)| < c; γ ≥ 1; the round trip `u → v → uFromV` holds within 1e-4 relative error for γ ≤ 10; `addVelocity` never gives |out| ≥ c for |v|, |w| < c; `aberratePhoton` output is a unit vector
  - [x] Test: collinear 0.5c ⊕ 0.5c = 0.8c (±1e-5·c)
  - [x] Test: a ship at β = 0.9 along +x firing a photon at +y (shooter frame) gives a W angle of 25.84° ± 0.05°
  - [x] Test: `dopplerFactor` with the observer at β = 0.9 (+x) and a static source: n̂ = (−1, 0) (source ahead) gives 4.36 ± 0.01, n̂ = (1, 0) (behind) gives 0.229 ± 0.001, n̂ = (0, 1) gives γ = 2.294 ± 0.001
  - [x] Test: `thrustAccelWorld` at γ = 2: along v gives magnitude a0, perpendicular to v gives a0/2
  - [x] Test: `gammaRel` of equal velocities = 1; head-on ±0.5c = 1.6667 ± 1e-4
  - [x] `./gradlew core:test` passes

### ST-036 Swept raycast for projectile hits; projectiles are consumed on hit
- status: done (2026-11-01, d0da175)
- result: ProjectileSweepSystem raycasts each step; projectiles hit once and are consumed; reviewer fixes applied to tests.
- size: M
- agent: kotlin-dev
- depends: ST-021
- files: create `system/ProjectileSweepSystem.kt`; edit `component/ProjectileComponent.kt`, `system/ContactSystem.kt`, `screen/PlayScreen.kt`; tests
- goal: At c = 60, a projectile moves 1.33 u per step, and sensor bodies don't use continuous collision detection, so small targets get skipped (research §1 "Tunneling"). Add a per-step ray test. Also make each projectile deal damage once and then disappear. Today a laser pierces and can hit several bodies. That interacts badly with bouncing lasers (ST-025) and is the intended change.
- spec:
  - `ProjectileComponent` becomes `Pool.Poolable` with `var spent = false` (reset to false), and gets `projectileCmpOptional`.
  - Extract `fun applyProjectileHit(engine: Engine, projectile: Entity, target: Entity)` (top-level, in `ContactSystem.kt`): if spent, return. Apply damage as today if the target has health. Set `spent = true` and add `RemoveComponent` if not already present. `ContactSystem.beginContact` uses it (it needs the engine, so use `EntitySystem.engine` once added).
  - `ProjectileSweepSystem(world) : EntitySystem(), PhysicsStepListener`, with family `allOf(ProjectileComponent, BodyComponent, TransformComponent)`. Register it before `PhysicsSystem` and add it to the listener list. `afterStep`: for each unspent projectile, `world.rayCast(callback, transform.prevPosition, body.position)` (`prevPosition` is the start-of-tick position set in `preProcess`; valid while `PHYSICS_SUBSTEPS = 1`). The callback ignores the projectile's own body, sensor fixtures, and targets whose `baseInfoCmp.faction` doesn't satisfy `projectileFaction.collidesWith(targetFaction)`. It keeps the smallest fraction. On a hit, call `applyProjectileHit`. Reuse one callback instance and preallocated vectors.
- acceptance:
  - [x] Test (real `World`): a kinematic sensor projectile (r = 0.1) at 89 u/s whose consecutive step positions straddle a static circle target (r = 0.3) without overlapping it damages the target exactly once and gets `RemoveComponent`
  - [x] Test: a projectile that overlaps the target in `beginContact` and is also swept in the same step damages it only once
  - [x] Test: a PLAYER projectile ignores a PLAYER-faction target in the sweep
  - [x] `./gradlew core:test` passes
- playtest: Lasers reliably hit small asteroids and disappear on impact.

### ST-037 RelativisticComponent and RelativisticDynamics integrator
- status: done (2026-11-01, 914d53b)
- result: Proper-velocity integrator with relativistic thrust, γ≤10 cap, NaN guard and collision readback; reviewer approved, minors in ST-054.
- size: M
- agent: kotlin-dev
- depends: ST-025, ST-035, ST-036
- files: create `component/RelativisticComponent.kt`, `system/RelativisticDynamics.kt`; edit `physics/PhysicsConstants.kt`, `system/MoveSystem.kt`, `screen/PlayScreen.kt`, `provider/PlayerEntityProvider.kt`, `provider/AsteroidProvider.kt`; tests
- goal: This is the core of M3 (research §2, E1). Each massive body integrates its proper velocity `u = γv` once per step and writes `v = u/γ` into Box2D. Box2D only handles collisions. Engines become a proper acceleration, so the ship approaches c but never reaches it, and turning at speed gets γ² harder.
- spec:
  - Constants: `RELATIVITY_ENABLED = true`, `ENGINE_PROPER_ACCEL = 60f`, `REVERSE_THRUST_FRACTION = 0.5f`, `COLLISION_EPS = 0.05f`.
  - `RelativisticComponent` (Poolable, convention): `u: Vector2`, `accel: Vector2` (per-step accumulator), `tau: Float`, `gUsed: Float = 1f`, `vSet: Vector2`, `gammaMax: Float = GAMMA_MAX_MASSIVE`. Reset everything to zero or defaults.
  - Step-listener order in `PlayScreen`: `listOf(moveSystem, boundaryFieldSystem, projectileSweepSystem, relativisticDynamics)`. Contributors add into `accel` in `beforeStep`, and `RelativisticDynamics` runs last and integrates. `afterStep` runs in the same order.
  - `MoveSystem.beforeStep`: if the entity has a `RelativisticComponent`, don't apply a force. Instead, with `thr = thrusters if > 0 else thrusters·REVERSE_THRUST_FRACTION`, `v = velocityFromU(u)` and `γ = gammaFromU(u)`, add `thrustAccelWorld(ENGINE_PROPER_ACCEL·thr, cos(angle), sin(angle), v, γ)` to `accel`. Entities without the component keep the Newtonian force.
  - `RelativisticDynamics : EntitySystem(), PhysicsStepListener` (family `allOf(RelativisticComponent, BodyComponent)`, registered before `PhysicsSystem`):
    - `beforeStep(dt)`: if `u` or `accel` is non-finite, log once via `tryLogging` and zero both. `u += accel·dt`; `clampU(u, gammaMax)`; `g = gammaFromU(u)`; `v = u/g`; `body.setLinearVelocity(v)`; `vSet = v`; `gUsed = g`.
    - `afterStep(dt)`: copy `body.linearVelocity` into a scratch vector (Box2D reuses it). If `dst(vb, vSet) > COLLISION_EPS` (a contact changed it): `clampSpeed(vb, BETA_MAX_MASSIVE·c)`, `u = uFromV(vb)`. Then `tau += dt / gUsed`, `accel.setZero()`.
  - Providers, when `RELATIVITY_ENABLED`: the player and all asteroids (including split children through `spawnAsteroid`) get a `RelativisticComponent` with `u = uFromV(initial velocity)`. The player body gets `allowSleep = false`. Projectiles don't get one (photons are ST-039).
- acceptance:
  - [x] Test (real `World`, `PhysicsSystem` + `MoveSystem` + `RelativisticDynamics`, no walls): a ship from rest with thrusters = 1 and fixed facing, after 94 steps (`t = 94/45` s), has β = `(a0t/c)/sqrt(1+(a0t/c)²)` ± 0.005 and τ = `(c/a0)·asinh(a0t/c)` ± 0.02
  - [x] Test: 20 s of thrust gives β < `BETA_MAX_MASSIVE`, γ ≤ 10 + 1e-3, and no NaN
  - [x] Test: at β = 0.9 along +x, thrust along +y for one step changes the velocity direction by `a0·dt/(γ²v)` rad ± 10 %
  - [x] Test: setting `u = NaN` leads, after one step, to `u = 0` and a finite body velocity
  - [x] Test: two relativistic circles collide head-on at 10 u/s each, and afterwards `u` equals `γ(v_body)·v_body` within 1e-3 for both
  - [x] With `RELATIVITY_ENABLED = false` the existing `MoveSystemTest` Newtonian tests pass unchanged
  - [x] `./gradlew core:test` passes
- playtest: The ship accelerates hard at first, then creeps toward c. Turning at high speed is much heavier. Asteroids behave as before at their low speeds.

### ST-054 RelativisticDynamics review minors
- status: done (2026-11-01, 6e19bc6)
- result: Architecture table renumbered 1-17, readback beta cap derived from per-body gammaMax, EPS comment, sorted imports, pool reset test.
- size: S
- agent: junior-dev
- depends: ST-037
- files: `system/RelativisticDynamics.kt`, `system/MoveSystem.kt`, `docs/tech/architecture.md`, tests
- goal: Minor issues from the ST-037 review.
- spec: (1) Renumber the architecture.md system table cleanly (no "7a"; RelativisticDynamics = 8, PhysicsSystem = 9, shift the rest) and fix any references. (2) In `afterStep` readback, derive the beta cap from `rel.gammaMax` (`sqrt(1 − 1/gammaMax²)`) instead of `BETA_MAX_MASSIVE`. (3) Add a code comment at the `COLLISION_EPS` readback explaining that velocity changes under the threshold are dropped. (4) Sort imports in `MoveSystem.kt`. (5) Tests: a pooled `RelativisticComponent` is fully reset. (The provider-skip test is dropped: ST-056 makes providers always attach the component, and relativity on/off becomes a runtime setting.)
- acceptance:
  - [x] `./gradlew core:test` passes

### ST-046 Enlarge the arena to 200×200 and calm the pacing
- status: done (2026-11-01, 96782ba)
- result: Arena 200×200 with 10 u skin, ENGINE_PROPER_ACCEL 25, asteroids 1+2·level² in a disc ≥30 u from centre at ±8 u/s; tests pin old sizes explicitly.
- size: S
- agent: junior-dev
- depends: ST-025, ST-037
- files: `provider/MapProvider.kt`, `physics/BoundaryField.kt`, `physics/PhysicsConstants.kt`, `environment/asteroid.kt`, `provider/AsteroidProvider.kt`; tests that build a `MapProvider` or a `BoundaryFieldSystem` (`BoundaryFieldSystemTest`, `RelativisticDynamicsTest`, `LifetimeAndRemoveSystemTest`)
- goal: The owner chose 200×200 (ST-030 (c)) because the game "feels a bit chaotic". Make the arena 4× bigger, make the engine calmer, and scale the asteroids so the arena isn't empty but is less crowded than now. `ENGINE_PROPER_ACCEL` becomes a runtime tunable in ST-056/ST-059. This card only changes its default.
- spec:
  - `MapProvider`: `MAP_WIDTH = 200f`, `MAP_HEIGHT = 200f`. Add constructor params `width: Float = MAP_WIDTH, height: Float = MAP_HEIGHT` and use them in `init`, so tests can ask for a 100×100 map.
  - `BoundaryField.kt`: `FIELD_SKIN = 10f`.
  - `PhysicsConstants.kt`: `ENGINE_PROPER_ACCEL = 25f`. Update its KDoc: with c = 60, 0.9c (γβ = 2.065) takes `t = (c/a)·γβ ≈ 5.0 s` and `d = (c²/a)(γ − 1) ≈ 186 u` of straight flight (it was ≈ 2.1 s and 78 u at 60).
  - `environment/asteroid.kt`: add `ASTEROID_COUNT_PER_LEVEL_SQ = 2`, `ASTEROID_SPAWN_SPEED_MAX = 8f` (u/s per axis, was a literal 10), `ASTEROID_SPAWN_MIN_RADIUS = 30f` (distance from the map centre, where the player spawns).
  - `AsteroidProvider.spawnAsteroids`: `maxAsteroids = 1 + ASTEROID_COUNT_PER_LEVEL_SQ·level²` (level 1/2/3 → 3/9/19, was 2/5/10). That is about half the old density on 4× the area. Rename the existing `radius` to `maxRadius` (same formula). For each asteroid, draw from `random` in this order: angle (existing formula), `r = ASTEROID_SPAWN_MIN_RADIUS + random.nextFloat()·(maxRadius − ASTEROID_SPAWN_MIN_RADIUS)`, vx, vy (each `random.nextFloat()·2·ASTEROID_SPAWN_SPEED_MAX − ASTEROID_SPAWN_SPEED_MAX`), spin (existing). Position = centre + `(r, 0).rotateRad(angle)`. Asteroids then fill a disc around the player instead of a ring at the edge.
  - Tests: every test that depends on map size or skin must pass an explicit `MapProvider(mock(), 100f, 100f)` and `BoundaryFieldParams(skin = 8f)`. Keep their expected numbers. Tests use `ENGINE_PROPER_ACCEL` symbolically, so their maths follows the new value. Only fix tests that break.
- acceptance:
  - [x] Test: `MapProvider(mock())` is 200×200, and `MapProvider(mock(), 100f, 100f)` is 100×100
  - [x] Test (real `World`, seeded `RandomXS128`): `spawnAsteroids(3, 200×200 map)` creates 19 asteroids. All are between 30 u and `maxRadius` from the centre, and every velocity component is within ±8
  - [x] `./gradlew core:test` passes
  - [x] Screenshot via the harness (`--seed=42 --frames=120`) shows the minimap with the larger arena and asteroids spread through it, and the agent has looked at it
- playtest: Less frantic than at 100×100 with a = 60. A straight run from the centre almost reaches 0.9c before the field. Level 1 should feel sparse but not empty, with 3 asteroids within reach. If it's too empty, raise `ASTEROID_COUNT_PER_LEVEL_SQ` to 3. If it's still too hectic, lower `ASTEROID_SPAWN_SPEED_MAX`. Try a0 = 15/25/40 in the debug panel (ST-059) to pick the feel.

### ST-055 Raise the γ safety cap to 50 (no gameplay cap)
- status: done (2026-11-01, bbeb967)
- result: GAMMA_MAX_MASSIVE and GAMMA_MAX_PROJECTILE are 50 (safety bound only); tests cover the γ 10–50 round trip and a one-step clamp from u = 100c.
- size: S
- agent: junior-dev
- depends: ST-037
- files: `physics/PhysicsConstants.kt`, `core/src/test/kotlin/com/github/pepek42/asteroids/physics/RelativityTest.kt`, `core/src/test/kotlin/com/github/pepek42/asteroids/system/RelativisticDynamicsTest.kt`
- goal: The owner decided (ST-032) that γ is limited only by relativity itself, through the engine, the arena and the field, so there's no gameplay cap. Keep a numerical safety bound far out. Precision is fine there because the integrator stores proper velocity `u`. At c = 60 and a0 = 25, 50 is only reachable after about 2 minutes of uninterrupted thrust.
- spec:
  - `GAMMA_MAX_MASSIVE = 50f`. KDoc: "Numerical safety bound, not a gameplay cap (owner decision ST-032). Runtime value lives in `RelativitySettings.gammaMaxMassive` (ST-056)." `BETA_MAX_MASSIVE` then ≈ 0.99980 (it's derived, so no code change).
  - `GAMMA_MAX_PROJECTILE = 50f`, so slugs are never capped below ships.
  - `RelativityTest`: the constants test expects `50f` and `BETA_MAX_MASSIVE = 0.99980 ± 1e-5`. In the `u → v → uFromV` round trip, change the γ range from `GAMMA_MAX_MASSIVE` to a literal `10.0` (gameplay range, keep 1e-4). Add a second loop over γ ∈ [10, 50] with 1e-3 relative tolerance. For the `uFromV(10 * c, …)` assertion, use a relative tolerance of `1e-3·expected` instead of `1e-2f`.
  - `RelativisticDynamicsTest`, the 20 s thrust test: keep `β ≤ BETA_MAX_MASSIVE + 1e-5` and `γ ≤ GAMMA_MAX_MASSIVE·(1 + 1e-4)`, plus no NaN. Fix the comment (the cap isn't reached in 20 s any more). Add a test: set `rel.u = (100·c, 0)`, run one step, and expect γ = 50 ± 0.05, |body velocity| < c, and finite values.
  - Leave `GAMMA_MASS_CAP` (ST-047) alone. It's a separate Box2D solver-stability cap.
- acceptance:
  - [x] `GAMMA_MAX_MASSIVE == 50f`, and the tests above exist
  - [x] `./gradlew core:test` passes

### ST-056 RelativitySettings: runtime tunables replace consts at the call sites
- status: done (2026-11-01, 5b37f88)
- result: RelativitySettings singleton in DI with clamping setters; every runtime const read goes through it, with runtime relativity and field switches (backstop stays). Reviewer approved; minors went to ST-064.
- size: M
- agent: kotlin-dev
- depends: ST-046, ST-054, ST-055
- files: create `physics/RelativitySettings.kt`; edit `Game.kt`, `screen/PlayScreen.kt`, `system/MoveSystem.kt`, `system/RelativisticDynamics.kt`, `system/BoundaryFieldSystem.kt`, `provider/PlayerEntityProvider.kt`, `provider/AsteroidProvider.kt`, `provider/WeaponProjectileProvider.kt`, `render/FieldShimmerRenderer.kt`, `physics/PhysicsConstants.kt` (KDoc only); tests
- goal: The debug panel (ST-059) has to change c, a0, field damping, the γ cap and relativity/field on/off while the game runs. `RELATIVITY_ENABLED` and friends are `const val`s. Add one mutable settings object in DI that runtime code reads. The consts stay and become its defaults. Nothing changes in play until someone moves a slider.
- spec:
  - `class RelativitySettings(relativityEnabled: Boolean = RELATIVITY_ENABLED, c: Float = SPEED_OF_LIGHT, engineProperAccel: Float = ENGINE_PROPER_ACCEL, var fieldEnabled: Boolean = true, var fieldParams: BoundaryFieldParams = BoundaryFieldParams(), gammaMaxMassive: Float = GAMMA_MAX_MASSIVE)`. Every property is a `var`, and the setters clamp: `c` to `[C_MIN = 20f, C_MAX_BOX2D_SAFE = 67f]`, `engineProperAccel` to `[5f, 120f]`, `gammaMaxMassive` to `[2f, GAMMA_MAX_MASSIVE]`. Add `val betaMaxMassive get() = sqrt(1 − 1/gammaMaxMassive²)`, plus `var fieldDamping` (get from `fieldParams.damping`, set with `fieldParams = fieldParams.copy(damping = it.coerceIn(0f, 5f))`, which allocates only on change). Put `C_MIN` and `C_MAX_BOX2D_SAFE` in `PhysicsConstants.kt`, and add a `PhysicsConstantsTest` assertion `C_MAX_BOX2D_SAFE·PHYSICS_UPDATE_INTERVAL ≤ 0.75·2`.
  - DI: `Game.create` does `bindSingleton(RelativitySettings())`. `PlayScreen` injects it and passes it to every consumer below. Each consumer takes `settings: RelativitySettings = RelativitySettings()` as its **last** constructor param, so existing test call sites still compile. **Exception:** `BoundaryFieldSystem` drops its `params` param, reads `settings.fieldParams` each step, and the tests that passed params now pass `RelativitySettings(fieldParams = …)`.
  - Replace runtime reads (grep `SPEED_OF_LIGHT`, `ENGINE_PROPER_ACCEL`, `BETA_MAX_MASSIVE`, `RELATIVITY_ENABLED` in `core/src/main`): `MoveSystem` uses `settings.c` and `settings.engineProperAccel`. `RelativisticDynamics` uses `settings.c` and clamps with `min(rel.gammaMax, settings.gammaMaxMassive)`. The readback beta cap is derived from that same effective γ max (this supersedes ST-054 item 2). The providers' `uFromV` uses `settings.c` and `settings.betaMaxMassive`. `WeaponProjectileProvider` gives a LASER the speed `settings.c` at the moment it's fired (ignoring `weaponCmp.projectileSpeed` for lasers). `FieldShimmerRenderer` sets `u_c` from `settings.c`. Default params on the pure `Relativity.kt` functions stay.
  - **Runtime relativity off.** Providers now **always** attach `RelativisticComponent` (to the player and asteroids), and `settings.relativityEnabled` picks the path each step: `MoveSystem` uses the Newtonian force path when it's false, even if the component exists. `BoundaryFieldSystem` uses the M2 force path when it's false. `RelativisticDynamics.beforeStep` does nothing when it's false except `accel.setZero()`. `afterStep` when it's false: copy the body velocity, `clampSpeed(v, betaMax·c)`, `u = uFromV(v)`, `gUsed = 1`, `tau += dt`. This keeps `u` in sync so switching back on is seamless. Update the `RELATIVITY_ENABLED` KDoc to say "default for `RelativitySettings.relativityEnabled`".
  - **Field off.** `BoundaryFieldSystem` skips the field acceleration (both paths) when `!settings.fieldEnabled`, but it **keeps the backstop**. The arena becomes a hard-walled box, and bodies never leave it, so `RemoveSystem` never deletes the player.
  - Changing `c` mid-flight keeps `u`, so v = u/γ(u, c) changes on the next step. Photons already in flight keep their speed until they bounce. That's acceptable for a debug knob, and the KDoc should say so.
- acceptance:
  - [x] Test: `RelativitySettings(c = 100f).c == 67f`; `gammaMaxMassive = 1f` clamps to 2; `fieldDamping = 2f` updates `fieldParams.damping` and keeps the other params
  - [x] Test (real `World`): with `settings.c = 30f`, a ship thrusting for 10 s from rest has |v| < 30 and β (against 30) > 0.9
  - [x] Test: with `relativityEnabled = false`, an entity that has a `RelativisticComponent` gets the Newtonian force (same velocity after N steps as the existing `MoveSystemTest` Newtonian case), and `u` tracks `uFromV(body velocity)`
  - [x] Test: with `gammaMaxMassive = 5f`, `u = (100c, 0)` is clamped to γ = 5 ± 0.01 after one step
  - [x] Test: with `fieldEnabled = false`, a body inside the skin gets no field acceleration, and a body past the wall is still reflected by the backstop
  - [x] `./gradlew core:test` passes
- playtest: With the defaults, the game plays exactly as after ST-046.

### ST-057 Debug plumbing: --debug-panel flag, F1 event, UI-first input, time scale
- status: done (2026-11-01, 3ade280)
- result: Added the --debug-panel flag, DebugSettings with a time scale, and an F1 DebugInputListener that fires even while input is ignored; the Stage now comes first in the input multiplexer.
- size: S
- agent: junior-dev
- depends: —
- files: `debug/ScreenshotHarness.kt` (`LaunchOptions`), create `debug/DebugSettings.kt`, create `event/DebugInputListener.kt`, edit `event/GameEventManager.kt`, `Game.kt`, `screen/PlayScreen.kt`; `core/src/test/kotlin/com/github/pepek42/asteroids/debug/LaunchOptionsTest.kt`, create `core/src/test/kotlin/com/github/pepek42/asteroids/event/GameEventManagerTest.kt`
- goal: Groundwork for the debug side panel (ST-059) that has no UI yet. It adds a launch flag, an F1 toggle event, and a time-scale knob, and it fixes input order. Today `GameEventManager` comes first in the `InputMultiplexer` and returns `true` from every `touchDown`/`keyDown`, so Stage widgets in play would never get clicks.
- spec:
  - `LaunchOptions`: add `val debugPanel: Boolean = false`, parsed from `--debug-panel` (no value). Extend the KDoc of `parseLaunchOptions`.
  - `class DebugSettings(var panelAllowed: Boolean, var panelVisible: Boolean, var timeScale: Float = 1f, var showVectors: Boolean = false)`. `Game.create` binds `DebugSettings(panelAllowed = IS_DEBUG || options.debugPanel, panelVisible = options.debugPanel)`. Add `val TIME_SCALES = floatArrayOf(0.25f, 0.5f, 1f)`.
  - `fun interface DebugInputListener { fun toggleDebugPanel() }`. `GameEventManager` gets `addDebugListener`/`removeDebugListener` (a `GdxArray` like the others). In `keyDown`, `Keys.F1` dispatches to the debug listeners **before** the `ignoreInput` check. Make `keyDown` return `true` only for keys it handles (`CONTROL_RIGHT`, `F1`) and `false` otherwise.
  - `Game.create`: the multiplexer order becomes `InputMultiplexer(stage, gameEventManager)`. Stage returns `true` only when a widget handled the event, so clicks on empty space still reach the game. Add a comment saying why.
  - `PlayScreen.render`: `engine.update(baseDelta · debugSettings.timeScale)`, where `baseDelta` is the existing screenshot-or-real delta.
- acceptance:
  - [x] Test: `--debug-panel` sets `debugPanel = true`, and the existing `LaunchOptionsTest` cases still pass (update the `LaunchOptions(...)` constructor calls if they're positional)
  - [x] Test: `GameEventManager.keyDown(Keys.F1)` calls a registered `DebugInputListener` once, also after `ignorePlayerInputs()`. `keyDown(Keys.A)` returns false
  - [x] `./gradlew core:test` passes
- playtest: Main menu buttons and the level-cleared button still work. In play, LMB fires and RMB thrusts as before.

### ST-058 ControlScheme abstraction; current mouse-hull scheme as the first strategy
- status: done (2026-11-01, 3616c29)
- result: Added ControlScheme, ControlSettings and --controls=, plus a ControlStrategy interface with MouseHullStrategy porting today's controls; WeaponComponent.aimAngleRad allows turret aiming. Reviewer approved; minor issues went to ST-064.
- size: M
- agent: kotlin-dev
- depends: ST-057
- files: create `input/ControlScheme.kt`, `input/ControlSettings.kt`, `input/PlayerInputState.kt`, `input/ControlStrategy.kt`, `input/MouseHullStrategy.kt`; edit `system/PlayerInputSystem.kt`, `component/WeaponComponent.kt`, `provider/WeaponProjectileProvider.kt`, `debug/ScreenshotHarness.kt` (`LaunchOptions`), `Game.kt`, `screen/PlayScreen.kt`; tests
- goal: The owner wants to try three control schemes at runtime before choosing one (ST-063). Pull the input → `MoveComponent`/`WeaponComponent` mapping out of `PlayerInputSystem` into swappable strategies, and port today's behaviour (scheme (a)) with no change in feel. The input plumbing stays the same: `GameEventManager` is the `InputProcessor` and dispatches `PlayerInputListener` callbacks. `PlayerInputSystem` subscribes and never polls `Gdx.input`.
- spec:
  - `enum class ControlScheme(val flag: String) { MOUSE_HULL("mouse"), TWIN_STICK("twin"), AUTO_AIM("auto"); companion fun fromFlag(s: String?): ControlScheme? }` (case-insensitive). `class ControlSettings(var scheme: ControlScheme = ControlScheme.MOUSE_HULL)` is bound in `Game.create` from `options.controls ?: MOUSE_HULL`. `LaunchOptions` gets `val controls: ControlScheme? = null`, parsed from `--controls=mouse|twin|auto`. Invalid values are ignored.
  - `class PlayerInputState { var thrustHeld = 0f; var fireHeld = false; val aimWorld = Vector2(); val moveAxis = Vector2() }`. `PlayerInputSystem` fills it from the existing callbacks. It unprojects the screen aim point into `aimWorld` once per frame (the existing `camera.unproject`, but into a reused `Vector3` to stop allocating). `moveAxis` stays zero until ST-061.
  - `interface ControlStrategy { fun apply(input: PlayerInputState, shipPos: Vector2, shipAngleRad: Float, move: MoveComponent, weapon: WeaponComponent?, dt: Float); fun onActivate(move: MoveComponent, weapon: WeaponComponent?, shipAngleRad: Float) {} }`. These are pure apart from writing the components, so they can be tested without GL.
  - `MouseHullStrategy`: `move.thrusters = input.thrustHeld`, `move.targetAngleRad = atan2(aimWorld − shipPos)`, `weapon?.doAttack = fireHeld`, `weapon?.aimAngleRad = null`. This is exactly today's behaviour.
  - `PlayerInputSystem(gameEventManager, camera, controlSettings = ControlSettings())` holds `strategies: Map<ControlScheme, ControlStrategy>`. Until ST-061/ST-062 land, TWIN_STICK and AUTO_AIM map to `MouseHullStrategy`, and that fallback is logged once. When `controlSettings.scheme` differs from last frame, call `onActivate` (which zeroes thrusters and sets `aimAngleRad = null`).
  - `WeaponComponent`: add `var aimAngleRad: Float? = null` (world frame; null means fire along the hull), reset to null. `WeaponProjectileProvider`: `val aim = weaponCmp.aimAngleRad ?: body.angle`. Muzzle = `body.position + weaponPosition.rotateRad(body.angle) + (2·projectileRadius, 0).rotateRad(aim)`. The projectile velocity and angle use `aim`. With null, this is identical to today.
- acceptance:
  - [x] Test: `MouseHullStrategy` with the ship at (10, 10) and aim at (10, 20) sets `targetAngleRad = π/2`, copies thrust and fire, and leaves `aimAngleRad` null
  - [x] Test: `ControlScheme.fromFlag("TWIN") == TWIN_STICK`; `--controls=auto` parses; `--controls=foo` keeps null
  - [x] Test: `WeaponProjectileProvider` with `aimAngleRad = π/2` on a ship facing 0 gives a projectile velocity along +y. With null, the existing `WeaponProjectileProviderTest` expectations hold
  - [x] `./gradlew core:test` passes
- playtest: The controls feel exactly as before.

### ST-041 ObserverState: smoothed player β, γ, τ, t
- status: done (2026-11-01, b1fb0d6)
- result: ObserverState plus ObserverSystem, which runs right after PhysicsSystem and tracks the player's raw and smoothed β/γ, rapidity, τ and world time; smoothToward is tested.
- size: S
- agent: kotlin-dev
- depends: ST-037
- files: create `physics/ObserverState.kt`, `system/ObserverSystem.kt`; edit `screen/PlayScreen.kt`; tests
- goal: The HUD, camera zoom and Doppler visuals all need the player's kinematics. Visuals need a low-pass-filtered β so field bounces don't flash the screen (research E4 `VISUAL_BETA_SMOOTHING`).
- spec:
  - `class ObserverState { val position = Vector2(); val betaRaw = Vector2(); var gammaRaw = 1f; val betaVisual = Vector2(); var gammaVisual = 1f; var tau = 0f; var worldTime = 0f; var rapidity = 0f; fun reset() }`.
  - `fun smoothToward(current: Float, target: Float, dt: Float, timeConstant: Float) = current + (target − current)·(1 − exp(−dt/timeConstant))`, top-level and tested. `VISUAL_BETA_SMOOTHING = 0.15f`.
  - `ObserverSystem(state) : IteratingSystem(allOf(PlayerComponent, BodyComponent, TransformComponent))`, registered right after `PhysicsSystem`. `worldTime += dt`. `position = interpolatedPosition`. `betaRaw = v/c`, where `v` comes from `velocityFromU(u)` if a `RelativisticComponent` exists, otherwise the body velocity. `gammaRaw` and `rapidity` come from `u` (or `gammaFromV`). `tau` comes from the component (or `worldTime`). `betaVisual` smooths x and y separately, and `gammaVisual = gammaFromV(betaVisual·c)`.
  - `PlayScreen` owns one `ObserverState`, passes it to the systems that need it (ST-042/043/045), and calls `reset()` in `show()`.
- acceptance:
  - [x] Tests: `smoothToward` reaches 63 % ± 1 % of a step after one time constant, and is stable for dt ≫ timeConstant (no overshoot)
  - [x] `./gradlew core:test` passes

### ST-059 Debug side panel: relativity readouts, toggles and sliders
- status: done (2026-11-01, 411a63f)
- result: F1 debug side panel with live readouts, relativity and field toggles, time scale, c/a0/damping/γ-cap sliders and a control picker. The toggle test was skipped because the Skin needs GL, the reviewer approved, and polish is in ST-065.
- size: M
- agent: kotlin-dev
- depends: ST-041, ST-056, ST-057, ST-058
- files: create `ui/DebugPanel.kt`, `system/DebugPanelSystem.kt`; edit `screen/PlayScreen.kt`; tests
- goal: The owner asked for relativity debug options on the side of the screen, so the feel can be tuned in play instead of by editing consts. The panel is a scene2d.ui table on the existing `Stage` with the existing `Skin`, docked on the right and toggled with F1. It exists only when `DebugSettings.panelAllowed` is true (`IS_DEBUG` or `--debug-panel`).
- spec:
  - **Labels aren't i18n (decision):** this is developer tooling, not shipped UI, so the strings are English literals in `DebugPanel.kt`, with a comment saying so. `messages*.properties` don't change. If panel options ever move into a player options menu (GDD §11 Q3, M7), they get i18n keys then.
  - `class DebugPanel(skin, relativitySettings, debugSettings, controlSettings) : DebugInputListener`. Its root is a `Table` with `background = skin.newDrawable("white", Color(0f, 0f, 0f, 0.6f))`, fixed width 260 px, filling the right edge of a full-screen container table (`setFillParent(true)`, `right().top()`). `toggleDebugPanel()` flips `debugSettings.panelVisible` and the root's `isVisible`. `show(stage)`/`hide()` add or remove the container. `PlayScreen.show` adds it only if `panelAllowed` and registers it with `gameEventManager.addDebugListener`. `hide` removes both. While the panel is visible, `PlayScreen.show` sets the system cursor to Arrow instead of Crosshair, and the toggle swaps it.
  - **Readouts** (`Label`s, refreshed at 10 Hz from `DebugPanelSystem` through `panel.update(observer, playerRel)`, reusing one `StringBuilder`): `β` (`betaRaw.len()`, 3 dp), `γ` (2 dp), `τ` and `world t` (s, 1 dp), `|v|` (u/s), `|u| = γ|v|` (proper velocity, i.e. world units per ship second), and `ship-frame speed = rapidity·c`. For straight-line thrust from rest, the last one equals `a0·τ`, the speed the pilot's own accelerometer would integrate, and the label's tooltip says so. Also show the current `c` and `a0`.
  - **Toggles** (`CheckBox`): relativity on/off → `relativitySettings.relativityEnabled`; boundary field on/off → `fieldEnabled`; show vectors → `debugSettings.showVectors` (drawn by ST-060).
  - **Time scale:** three `TextButton`s, 0.25×, 0.5× and 1×, in a `ButtonGroup` (exactly one checked) → `debugSettings.timeScale`.
  - **Sliders** (`Slider(min, max, step, false, skin, "default-horizontal")` + value label): `c` 20–67 step 1; `a0` 5–120 step 1; `field damping` 0–5 step 0.1; `γ safety cap` 2–50 step 1. Each slider writes the setting in a `ChangeListener` and is initialised from it. Add a "reset defaults" `TextButton` that restores the const defaults.
  - **Control scheme:** `SelectBox<String>` with `ControlScheme.entries.map { it.flag }` → `controlSettings.scheme`.
  - `DebugPanelSystem(panel, observerState) : EntitySystem()`, family `allOf(PlayerComponent, RelativisticComponent)`. Register it just before `HudSystem` (which acts and draws the stage). It runs only when the panel is visible.
  - Keep the logic you can test separate from the widgets: put `fun formatReadouts(observer: ObserverState, rel: RelativisticComponent?, c: Float, a0: Float, out: StringBuilder)` at top level.
- acceptance:
  - [x] Test: `formatReadouts` with β = 0.9 along +x and c = 60 contains `γ = 2.29`, `|u| = 123.9` and `ship-frame speed = 88.3` (rapidity atanh 0.9 = 1.4722, times 60)
  - [x] Test (no GL): `DebugPanel.toggleDebugPanel()` flips `debugSettings.panelVisible`. Skip this if building the `Skin` needs GL, and say so in the result
  - [x] `./gradlew core:test` passes
  - [x] Screenshot via the harness with `--debug-panel --seed=42 --thrust --frames=150` shows the panel on the right with non-zero β, and the agent has looked at it
- playtest: F1 shows and hides the panel. Dragging a slider doesn't fire or thrust. Dropping c to 30 makes the ship hit "near c" much sooner. 0.25× time makes bounces and aberration easy to watch. Relativity off lets the ship go past c, up to the Box2D clamp.

### ST-066 Braking boost: engines push harder against the motion
- status: done (2026-11-02, 328e688)
- size: S
- agent: junior-dev
- depends: ST-056, ST-059
- files: `physics/Relativity.kt`, `physics/PhysicsConstants.kt`, `physics/RelativitySettings.kt`, `system/MoveSystem.kt`, `ui/DebugPanel.kt`; tests in `physics/RelativityTest.kt`, `physics/RelativitySettingsTest.kt`, `system/MoveSystemTest.kt`, `ui/DebugPanelTest.kt`
- goal: Owner decision 2026-11-02 (top priority). Shedding speed near c takes too long, so the engine's proper acceleration grows when it burns against the current motion: `a = a0·(1 + k·max(0, −cos θ))`, with θ the angle between the thrust direction and the velocity and k = 2 (3× when burning straight against the motion). Burning along or across the motion is unchanged.
- spec:
  - `PhysicsConstants.kt`: `const val BRAKE_BOOST_K = 2f` with KDoc citing the owner decision.
  - `Relativity.kt`: `fun brakeBoostFactor(dirX: Float, dirY: Float, vx: Float, vy: Float, k: Float): Float`. `dir` is the unit thrust direction. `speed = sqrt(vx² + vy²)`. If `speed < 1e-6f` return `1f`. Otherwise `cos = (dirX·vx + dirY·vy) / speed` and return `1f + k·max(0f, −cos)`.
  - `RelativitySettings`: append a last constructor param `brakeBoost: Float = BRAKE_BOOST_K` and a `var brakeBoost` whose setter clamps to `[0f, 5f]` (same pattern as `engineProperAccel`).
  - `MoveSystem.addProperAcceleration`: after computing `thr`, `velocity` and `gamma`, let `s = sign(thr)` (reverse thrust pushes backwards, so the thrust direction is `s·(cos angle, sin angle)`). `boost = brakeBoostFactor(s·cos(angle), s·sin(angle), velocity.x, velocity.y, settings.brakeBoost)`. Pass `settings.engineProperAccel * thr * boost` as the first argument of `thrustAccelWorld` (it was `settings.engineProperAccel * thr`). The Newtonian path (`applyMainThrusters`, relativity off) is unchanged.
  - `DebugPanel.build`: add `slider("brake boost", 0f, 5f, 0.1f, 1, { relativitySettings.brakeBoost }) { relativitySettings.brakeBoost = it }` right after the `a0` slider. `resetDebugDefaults` sets `relativity.brakeBoost = BRAKE_BOOST_K`.
- acceptance:
  - [x] Tests for `brakeBoostFactor` with k = 2: thrust along v gives 1; against v gives 3; perpendicular gives 1; at 120° from v gives 2 ± 1e-5; v = 0 gives 1; k = 0 always gives 1
  - [x] Test: `RelativitySettings(brakeBoost = 9f).brakeBoost == 5f`, `brakeBoost = -1f` clamps to 0
  - [x] Test (`MoveSystemTest`, call `beforeStep` directly and read `rel.accel` before `RelativisticDynamics` clears it): a ship with `u` for β = 0.5 along +x, facing −x, thrusters = 1, gets `accel.x = −3·a0 ± 1e-3` and `accel.y ≈ 0`. With `settings.brakeBoost = 0f` it gets `−a0`. Facing +x with thrusters = −1 (reverse), it gets `−3·a0·REVERSE_THRUST_FRACTION`
  - [x] Test: `resetDebugDefaults` restores `brakeBoost` to `BRAKE_BOOST_K`
  - [x] `./gradlew core:test` passes
- playtest: Near c, flip around and burn: speed drops about three times faster than it built up. Sideways burns still feel heavy (γ² turning cost). Try k = 0/2/4 on the debug slider.
- result: Braking boost factor applied to relativistic engine thrust (3× straight against motion), tunable via debug slider; 161 tests pass.

### ST-067 Shots leave the arena; a ripple marks where they cross the field
- status: ready
- size: S
- agent: junior-dev
- depends: ST-025, ST-027, ST-036
- files: `system/BoundaryFieldSystem.kt`, `component/ProjectileComponent.kt`, `component/LifetimeComponent.kt`, `weapon/ProjectileConstants.kt`, `provider/WeaponProjectileProvider.kt`, `system/RemoveSystem.kt`, `CLAUDE.md` (Architecture, entity removal paragraph), `docs/tech/architecture.md`; tests in `system/BoundaryFieldSystemTest.kt`, `provider/WeaponProjectileProviderTest.kt`
- goal: Owner decision 2026-11-02 (top priority). The boundary field ignores projectiles entirely: no kinematic velocity kick, no backstop, no bounces. Shots fly out of the arena and are removed by their lifetime or by `RemoveSystem`'s out-of-bounds safety net. The field shows one shimmer ripple where each shot crosses the wall.
- spec:
  - `ProjectileComponent`: add `var exitRippleEmitted = false`, reset to false in `reset()`.
  - `BoundaryFieldSystem.beforeStep`: skip any entity that has a `ProjectileComponent` (`projectileMapper.has(entity)`), before the field acceleration. Kinematic bodies that are not projectiles keep today's velocity-kick path.
  - `BoundaryFieldSystem.afterStep`: for projectile entities, skip `emitFieldImpact` and `enforceBackstop` and call a new `emitProjectileExit(id, entity, body)` instead: if `!exitRippleEmitted` and the body position is outside the arena rectangle (`x < 0 || x > w || y < 0 || y > h`), call `impacts.emit(id, x.coerceIn(0f, w), y.coerceIn(0f, h), PROJECTILE_EXIT_RIPPLE_STRENGTH, force = true)` and set `exitRippleEmitted = true`. Add `private const val PROJECTILE_EXIT_RIPPLE_STRENGTH = 1f` to the companion. This also runs when `settings.fieldEnabled` is false (only the field acceleration is gated by that flag).
  - Remove photon bounces, which nothing uses any more: delete `LifetimeComponent.bouncesLeft` (field, KDoc and reset line), `PHOTON_MAX_BOUNCES` in `ProjectileConstants.kt`, the `bouncesLeft = …` line in `WeaponProjectileProvider` and the matching assertion in `WeaponProjectileProviderTest`.
  - `RemoveSystem`: shots leaving the arena is now normal, so for entities whose `baseInfoCmp.entityType == EntityType.PROJECTILE` log the out-of-bounds removal at `debug` instead of the `"WARN: …"` info line. Behaviour is unchanged (they still get `RemoveComponent`).
  - Docs: in `CLAUDE.md` and `docs/tech/architecture.md`, replace "velocity change on kinematic projectiles" with "projectiles are ignored: they leave the arena, leaving one ripple where they cross the wall". Don't touch game-design.md (ST-049 reconciles it).
- acceptance:
  - [ ] Test (`MapProvider(mock(), 100f, 100f)`, real `World`, kinematic sensor body with `ProjectileComponent`, `BaseInfoComponent` and `TransformComponent`): starting at (95, 50) with velocity (60, 0), after 10 steps of `beforeStep` + `world.step` + `afterStep`, x > 100 and the velocity is still (60, 0) exactly. `impacts.count == 1`, with the impact at x = 100 and y = 50 ± 1e-3. Ten more steps add no impact
  - [ ] Test: a kinematic body **without** `ProjectileComponent` in the skin still gets the inward velocity kick (existing behaviour)
  - [ ] Test: with `fieldEnabled = false` a projectile also passes the wall untouched and still emits its one ripple
  - [ ] `./gradlew core:test` passes
- playtest: Shoot at the edge: the bolt passes through with one ripple and disappears off-screen. Asteroids and the ship still bounce softly.

### ST-060 Debug vector overlay: v, u and acceleration arrows
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-056, ST-057
- files: create `system/DebugVectorSystem.kt`; edit `component/RelativisticComponent.kt`, `system/RelativisticDynamics.kt`, `screen/PlayScreen.kt`; tests
- goal: Make the difference between coordinate velocity `v` (capped at c) and proper velocity `u = γv` (unbounded) visible, together with the acceleration actually applied, which is thrust plus field. Enabled by the panel's "show vectors" toggle.
- spec:
  - `RelativisticComponent`: add `val lastAccel = Vector2()` (reset to zero). In `RelativisticDynamics.afterStep`, `lastAccel.set(accel)` right before `accel.setZero()`. When relativity is off it's zero.
  - `DebugVectorSystem(camera, relativitySettings, debugSettings) : IteratingSystem(allOf(RelativisticComponent, BodyComponent, TransformComponent))`, registered right after `RenderSystem`. It owns a `ShapeRenderer` and is `Disposable`. In `update`, it returns early unless `debugSettings.showVectors`. Otherwise it calls `begin(ShapeType.Line)` with `camera.combined` once and `end()` once. For each entity, from the origin `transform.interpolatedPosition`, it draws: `v` (green) with length `v·VEC_SCALE_VELOCITY`; `u` (cyan) with `u·VEC_SCALE_VELOCITY`; `lastAccel` (orange) with `a·VEC_SCALE_ACCEL`; and a thin grey circle of radius `c·VEC_SCALE_VELOCITY` around the player only (the "c" ring, which `v` can never leave but `u` can). Constants: `VEC_SCALE_VELOCITY = 0.25f` (s), `VEC_SCALE_ACCEL = 0.1f` (s²). Arrowheads are two 0.4 u lines at ±25°.
  - Put the geometry in a pure function: `fun arrowSegments(ox, oy, dx, dy, scale, headLen, headAngleRad, out: FloatArray)` fills 12 floats (shaft + 2 head lines). Draw from that.
- acceptance:
  - [ ] Test: `arrowSegments` for (0, 0) + (4, 0)·0.25 gives a shaft (0, 0)→(1, 0) and heads ending at `1 − 0.4·cos25°, ±0.4·sin25°`
  - [ ] Test: after one step with thrust, `lastAccel` is non-zero, and after a step without thrust it's zero
  - [ ] `./gradlew core:test` passes
- playtest: At speed the green `v` arrow stops at the c ring while the cyan `u` keeps growing. Thrusting sideways at speed shows a short orange arrow (the 1/γ² turning cost). In the field skin the orange arrow points inward.

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

### ST-039 Photon lasers: velocity aberration at the muzzle
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-024, ST-035, ST-056, ST-067
- files: create `component/PhotonComponent.kt`; edit `provider/WeaponProjectileProvider.kt`; tests in `provider/WeaponProjectileProviderTest.kt`
- goal: Lasers fly at exactly c in a direction that aberrates with the shooter's velocity (research E3). Today projectiles ignore the ship's velocity, so at speed the shots don't sweep forward as they physically should. This follows ST-034 (a). **Reworked 2026-11-02:** the field-mirror and bounce part is dropped, because shots now leave the arena (ST-067). `PhotonComponent.dir` stays because gravity bends photons later (ST-083).
- spec:
  - `PhotonComponent { val dir = Vector2() }` (unit vector, Poolable, reset to zero, mapper + `photonCmp` / `photonCmpOptional`, per the component convention).
  - `WeaponProjectileProvider`: the shooter-frame direction is `n̂' = (cos aim, sin aim)` with `aim = weaponCmp.aimAngleRad ?: body.angle` (`aimAngleRad` and the hull angle are treated as shooter-frame angles; ST-076 later converts the hull fallback properly). `v_ship = relativisticCmpOptional?.vSet ?: body.linearVelocity`, `β = v_ship / settings.c`. `n̂ = aberratePhoton(β, n̂')`. If `!settings.relativityEnabled`, `n̂ = n̂'`. Body velocity = `settings.c·n̂`. The body angle **and** `TransformComponent.prevRotationDeg` = `atan2(n̂)`, the latter in **degrees** (today it is set to radians, a bug). Add `PhotonComponent(dir = n̂)`. The muzzle offset keeps using `aim`. The lifetime from ST-024 stays.
- acceptance:
  - [ ] Test: a ship at rest facing +x fires a laser with velocity (60, 0) ± 1e-3 and `prevRotationDeg == 0`
  - [ ] Test: a ship moving at β = 0.9 along +x, facing +y, fires a laser at a W angle of 25.84° ± 0.1° with |v| = 60 ± 1e-3, and `prevRotationDeg ≈ 25.84`
  - [ ] Test: same ship with `relativityEnabled = false` fires straight along +y
  - [ ] `./gradlew core:test` passes
- playtest: Lasers fired sideways at speed visibly sweep forward.

### ST-068 Pilot clock: time-warp functions, GameClock and settings
- status: ready
- size: S
- agent: junior-dev
- depends: ST-056
- files: create `physics/TimeWarp.kt`; edit `physics/PhysicsConstants.kt`, `physics/RelativitySettings.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/TimeWarpTest.kt`, edit `physics/RelativitySettingsTest.kt`
- goal: The pure first slice of the pilot-time game clock (owner decision 2026-11-02, research E21). Per rendered frame the pilot lives `dt_real`, and the arena must advance `γ·dt_real`. The fast-forward is capped at about 10×. Past the cap the world stops speeding up, which is a declared FAKE. No system uses this yet (ST-069 wires it).
- spec:
  - `PhysicsConstants.kt`: `const val TIME_WARP_ENABLED = true`, `const val TIME_WARP_MAX = 10f`, `const val TIME_WARP_MAX_LIMIT = 12f` (upper clamp for the setting; at 60 fps a 12× warp needs 9 physics steps per frame, under the 10-step cap of ST-069). KDoc: "Game clock = pilot proper time (owner decision 2026-11-02, research E21). Past TIME_WARP_MAX the world advances slower than the pilot's γ demands: declared FAKE."
  - `TimeWarp.kt`:
    - `fun timeWarpFactor(gamma: Float, enabled: Boolean, max: Float): Float` = `1f` if `!enabled` or `!gamma.isFinite()`, else `gamma.coerceIn(1f, max)`.
    - `fun isWarpCapped(gamma: Float, enabled: Boolean, max: Float): Boolean` = `enabled && gamma.isFinite() && gamma > max + 1e-4f`.
    - `fun worldDelta(realDt: Float, warp: Float, maxWorldDt: Float): Float` = `min(realDt·warp, maxWorldDt)`.
    - `class GameClock { var realDt = 0f; var warp = 1f; var worldDt = 0f; var warpCapped = false; var stepsCapped = false; var worldTime = 0f; var interpolationAlpha = 0f; fun reset() }` (reset sets every field back to these defaults). KDoc per field: `realDt` is the pilot's frame time (after the debug time scale); `worldDt` the arena time to simulate this frame; `stepsCapped` true when the physics step cap cut `worldDt`; `worldTime` sums the simulated physics steps; `interpolationAlpha` is `PhysicsSystem`'s render lerp factor.
  - `RelativitySettings`: append two last constructor params, `timeWarpEnabled: Boolean = TIME_WARP_ENABLED` (plain `var`) and `timeWarpMax: Float = TIME_WARP_MAX` (`var`, setter clamps to `[1f, TIME_WARP_MAX_LIMIT]`).
- acceptance:
  - [ ] Tests: `timeWarpFactor(3f, true, 10f) == 3f`; `(25f, true, 10f) == 10f`; `(0.5f, true, 10f) == 1f`; `(3f, false, 10f) == 1f`; `(NaN, true, 10f) == 1f`. `isWarpCapped(25f, true, 10f)` is true, `(10f, true, 10f)` and `(25f, false, 10f)` are false. `worldDelta(1/60f, 10f, 10/45f) ≈ 1/6`, `worldDelta(0.1f, 10f, 10/45f) == 10/45f`
  - [ ] Test: `GameClock.reset()` restores all defaults; `RelativitySettings(timeWarpMax = 50f).timeWarpMax == 12f`, `0f` clamps to 1
  - [ ] `./gradlew core:test` passes

### ST-069 Pilot clock wiring: the arena advances γ·dt per frame, up to 10 steps
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-041, ST-059, ST-068
- files: create `system/TimeWarpSystem.kt`; edit `system/PhysicsSystem.kt`, `system/ObserverSystem.kt`, `system/LifetimeSystem.kt`, `screen/PlayScreen.kt`, `ui/DebugPanel.kt`, `system/DebugPanelSystem.kt`; tests (create `system/TimeWarpSystemTest.kt`, extend `system/LifetimeAndRemoveSystemTest.kt`, `ui/DebugPanelTest.kt`)
- goal: Make the arena run on the pilot's clock (owner decision 2026-11-02, research E21). `engine.update` keeps receiving real (pilot) time, so cameras, UI and the player's own systems are unchanged. Only the physics accumulator, world lifetimes and the world-time readout consume `worldDt = warp·realDt`. Box2D's fixed step stays 1/45 s; fast-forward means more steps per frame, at most 10.
- spec:
  - `PhysicsSystem` companion: `const val MAX_STEPS_PER_FRAME = 10` and `maxTimeToProcess = MAX_STEPS_PER_FRAME·PHYSICS_UPDATE_INTERVAL` (raised from 5 steps). Constructor gets a last param `clock: GameClock? = null`. In `update`: `val dt = clock?.worldDt ?: deltaTime`, then the existing `accumulator += min(maxTimeToProcess, dt)` loop. Inside each step add `clock?.worldTime += PHYSICS_UPDATE_INTERVAL`. After the loop, `clock?.interpolationAlpha = alpha` (the value used by `interpolateTransforms`). With no clock, behaviour equals today's apart from the higher catch-up cap.
  - `TimeWarpSystem(clock: GameClock, settings: RelativitySettings) : EntitySystem()`, with the family `allOf(PlayerComponent, RelativisticComponent)`. Register it **first** in `PlayScreen.setupEcs` (before `EndLevelSystem`). `update(deltaTime)`: `clock.realDt = deltaTime`; `gamma = player's rel.gUsed` (the γ of the last physics step; `1f` if there is no player); `enabled = settings.timeWarpEnabled && settings.relativityEnabled`; `clock.warp = timeWarpFactor(gamma, enabled, settings.timeWarpMax)`; `clock.warpCapped = isWarpCapped(gamma, enabled, settings.timeWarpMax)`; `val maxWorld = PhysicsSystem.MAX_STEPS_PER_FRAME·PHYSICS_UPDATE_INTERVAL`; `clock.worldDt = worldDelta(deltaTime, clock.warp, maxWorld)`; `clock.stepsCapped = deltaTime·clock.warp > maxWorld + 1e-6f`.
  - `ObserverSystem(state, settings, clock: GameClock? = null)`: `state.worldTime = clock?.worldTime ?: (state.worldTime + deltaTime)`. The fallback `tau = worldTime` path (no `RelativisticComponent`) is unchanged.
  - `LifetimeSystem(clock: GameClock? = null)`: the WORLD clock subtracts `clock?.worldDt ?: deltaTime`. PROPER stays as it is until ST-040. The fade logic is unchanged.
  - `PlayScreen`: own one `GameClock`, `reset()` it in `show()` next to `observerState.reset()`, and pass it to `TimeWarpSystem`, `PhysicsSystem`, `ObserverSystem`, `LifetimeSystem` and `DebugPanelSystem`. `ShimmerImpacts.time` already advances per physics step, so ripples keep running at c in arena time.
  - Debug panel: a checkbox "time warp" → `settings.timeWarpEnabled`, a slider "warp cap" 1–12 step 1 → `settings.timeWarpMax`, both restored by `resetDebugDefaults`. `formatReadouts` gets a last param `clock: GameClock? = null`; when non-null it appends a line `warp = ×N.N` (1 dp) followed by ` (capped)` if `warpCapped || stepsCapped`.
  - The debug time scale (ST-057) still multiplies the real delta before all of this, so 0.25× slows the pilot and the warped world together.
- acceptance:
  - [ ] Test (`TimeWarpSystemTest`, real `World` with `PhysicsSystem` + `RelativisticDynamics` + `TimeWarpSystem`): a player whose `u` gives γ = 4, with no thrust, after one `engine.update(1/45f)` has run exactly 4 physics steps (`clock.worldTime == 4/45 ± 1e-5`, position advanced by `4·v/45 ± 1e-3`)
  - [ ] Test: γ = 20 with `timeWarpMax = 10` → `warp == 10`, `warpCapped`; `engine.update(0.1f)` → `worldDt == 10/45`, `stepsCapped`, and at most 10 steps ran
  - [ ] Test: `timeWarpEnabled = false` or `relativityEnabled = false` → `warp == 1` at any γ
  - [ ] Test: a WORLD lifetime of 2 s on an entity expires after 0.5 s of real time at warp 4 (±1 frame)
  - [ ] Test: `formatReadouts(..., clock)` with warp 4 and `stepsCapped = true` contains `warp = ×4.0 (capped)`
  - [ ] `./gradlew core:test` passes
- playtest: At speed the asteroids and the world visibly speed up while the ship handles as usual. Past γ = 10 the debug panel shows "(capped)". With "time warp" off the game plays as before.

### ST-040 Ship clocks under the pilot clock: player on real time, others on proper time
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-024, ST-037, ST-069
- files: `component/RelativisticComponent.kt`, `system/WeaponSystem.kt`, `system/LifetimeSystem.kt`, `system/PhysicsSystem.kt`, `system/RelativisticDynamics.kt`, `screen/PlayScreen.kt`; tests
- goal: **Rewritten 2026-11-02** for the pilot-time game clock (ST-031 replaced). The player's gun cooldown and turning run on τ, which is real time, so from the player's view they are never dilated. Everything else runs on its own proper time `dτ = dt_world/γ` (research E2), which matters for enemies (M6) and asteroid spin. Physics stays in the arena frame, so the player's PD rotation controller must be slowed in world time by the warp to look normal in real time.
- spec:
  - In `RelativisticComponent.kt`: `fun Entity.properDelta(clock: GameClock?, frameDt: Float): Float`. With `realDt = clock?.realDt ?: frameDt` and `worldDt = clock?.worldDt ?: frameDt`: the player (`playerCmpOptional != null`) returns `realDt` (exact while the warp is uncapped, FAKE past the cap: the pilot's clock never slows); an entity with a `RelativisticComponent` returns `worldDt / rel.gUsed`; others return `worldDt`.
  - `WeaponSystem(provider, clock: GameClock? = null)`: `cooldown -= entity.properDelta(clock, deltaTime)`.
  - `LifetimeSystem`: the PROPER clock subtracts `entity.properDelta(clock, deltaTime)`. WORLD stays as in ST-069.
  - `PhysicsSystem.applyRotation` (runs every world-time substep): time factor `f` = `1f / clock.warp` for the player (the turn takes the same real time at any warp), `1f / rel.gUsed` for other entities with a `RelativisticComponent`, `1f` otherwise or with no clock. Pass `kp·f²`, `kd·f`, `maxAngularAcceleration·f²`, `maxAngularVelocity·f` (from `ShipHandlingComponent` or the `ShipRotationController` defaults). This is exactly the PD controller on a clock slowed by `f`.
  - Asteroid spin `ω₀/γ`: in `RelativisticDynamics.beforeStep`, for entities without a `MoveComponent` or with a null `targetAngleRad`, compute `g` and do `body.angularVelocity *= gUsedPrevious / g` before overwriting `gUsed`. This rescales exactly and survives collisions.
  - `PlayScreen` passes the `GameClock` to `WeaponSystem` and `LifetimeSystem`.
  - No `COOLDOWN_DILATION_EXPONENT` / `ROTATION_DILATION_EXPONENT` constants: the old ST-031 options are gone.
- acceptance:
  - [ ] Test: the player's weapon fires the same number of shots over 3 s of real time at warp 4 as at warp 1 (±1 shot)
  - [ ] Test: a non-player weapon entity with `gUsed = 2` fires half as often per second of world time as at `gUsed = 1` (±1 shot)
  - [ ] Test: a non-player PROPER lifetime of 1.2 at `gUsed = 3.2` lasts 3.84 s ± 1 step of world time
  - [ ] Test: a player 90° turn with the clock at warp 4 takes 4× ± 15 % as much world time as at warp 1 (so the same real time), with overshoot still < 5°
  - [ ] Test: an asteroid spinning at 2 rad/s whose `u` is set to γ = 2 spins at 1 ± 0.02 rad/s after one step
  - [ ] `./gradlew core:test` passes
- playtest: Near c the world rushes past (warp) but the fire rate and turning feel the same as at rest. Asteroids spin slower when they move fast.

### ST-042 HUD shows β, γ, ship and world time and the time warp; speed zoom
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-041, ST-069
- files: `ui/Hud.kt`, `system/HudSystem.kt`, `system/CameraSystem.kt`, `screen/PlayScreen.kt`, `assets/i18n/messages.properties`, `assets/i18n/messages_pl.properties`; tests
- goal: The "I'm near c and I can feel it" readout (research E6), plus a camera that zooms out with speed, because at c = 60 half the screen is crossed in 0.4 s. **Updated 2026-11-02:** with the pilot clock, ship time τ tracks real time and the world fast-forwards, so the HUD shows the fast-forward factor and whether it is capped (owner decision).
- spec:
  - HUD: a `ProgressBar(0f, 1f, 0.001f, false, skin, "default-horizontal")` showing `(rapidity / HUD_RAPIDITY_FULL).coerceAtMost(1f)` with `HUD_RAPIDITY_FULL = acosh(10f)` (≈ 2.993, β = 0.995). It keeps moving near c. Labels: `hud_beta=β = {0,number,0.000} c`, `hud_gamma=γ = {0,number,0.00}`, `hud_time=Ship time {0,number,0.0} s · World time {1,number,0.0} s`, `hud_warp=Time warp ×{0,number,0.0}`, `hud_warp_capped=Time warp ×{0,number,0.0} (capped)`. Polish: `Czas statku`, `Czas świata`, `Przyspieszenie czasu ×{0,number,0.0}`, `… (limit)`. Keep the existing level, position and velocity labels. `HudSystem` takes the `ObserverState` and the `GameClock` and feeds `betaRaw.len()`, `gammaRaw`, `tau`, `worldTime` and `clock.warp`, using `hud_warp_capped` when `warpCapped || stepsCapped`. Hide the warp label while `warp < 1.05` and nothing is capped, so it doesn't clutter low speeds.
  - `CameraSystem`: keep a `wheelZoom` field (the old `camera.zoom` logic, clamped 0.5–2). Add top-level `fun speedZoom(wheelZoom: Float, beta: Float, k: Float = CAMERA_ZOOM_PER_BETA2): Float = (wheelZoom·(1 + k·beta²)).coerceIn(0.5f, 2f)` with `CAMERA_ZOOM_PER_BETA2 = 0.6f`. Set `camera.zoom = speedZoom(wheelZoom, observer.betaVisual.len())`. `CameraSystem` takes the `ObserverState`.
  - Put the label choice in a pure top-level `fun warpLabelKey(warp: Float, capped: Boolean): String?` (null = hidden).
- acceptance:
  - [ ] Tests: `speedZoom(1f, 0f) == 1f`; `speedZoom(1f, 0.9f) ≈ 1.486`; `speedZoom(2f, 0.9f) == 2f`. `warpLabelKey(1f, false) == null`, `(4f, false) == "hud_warp"`, `(10f, true) == "hud_warp_capped"`, `(1f, true) == "hud_warp_capped"`
  - [ ] The i18n keys exist in both properties files
  - [ ] `./gradlew core:test` passes
- playtest: The β bar fills fast at first, then slowly near c. World time races ahead of ship time at speed, and the warp label shows the factor, marked capped above 10×. The view widens smoothly as you speed up.

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

### ST-044 Length contraction of moving sprites
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-035
- files: `system/RenderSystem.kt`, `render/RelativityVisuals.kt` (create or extend); tests
- goal: In world view, moving objects are flattened along their motion to `L₀/γ` (research E5, exact for the top-down camera). It's done on the CPU with 4-corner quads, so no shader is needed and batching survives. Hitboxes stay uncontracted for now (research E20, later).
- spec:
  - Constant `CONTRACTION_STRENGTH = 1f`. Add `fun contractOffset(ox, oy, vhx, vhy, k, out: Vector2)`: `o' = o − k·(o·v̂)·v̂`.
  - Readability clamp: there's no gameplay γ cap (ST-032), so a γ = 50 ship would become an invisible sliver. Add `CONTRACTION_MIN_SCALE = 0.25f` and compute `k = min(CONTRACTION_STRENGTH·(1 − 1/γ), 1 − CONTRACTION_MIN_SCALE)`, so a sprite never drops below 25 % of its rest length along v̂.
  - `RenderSystem.processEntity`: after setting the sprite rotation and position, if the entity has a body with speed > 1e-3: `γ = relativisticCmpOptional?.gUsed ?: gammaFromV(v)` and `k = CONTRACTION_STRENGTH·(1 − 1/γ)`. Copy `sprite.vertices` (20 floats) into a reusable `FloatArray(20)`. For corners at offsets 0, 5, 10, 15, transform `(x − cx, y − cy)` with `contractOffset` around the interpolated centre. Then `batch.draw(sprite.texture, scratch, 0, 20)`. Otherwise use `sprite.draw(batch)` as today.
- note (2026-11-02): this is the world-view path. Pilot view (ST-074) reuses `contractOffset` on the retarded corners before the boost, so keep it a pure top-level function.
- acceptance:
  - [ ] Tests: `contractOffset` with k = 0 is the identity. A point along v̂ with k = 1 − 1/2.294 is scaled to 0.436. A point perpendicular to v̂ is unchanged. At γ = 50 the computed k gives a scale of exactly 0.25
  - [ ] `./gradlew core:test` passes
- playtest: The player ship visibly flattens along its direction of travel near c. Slow asteroids look unchanged.

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

### ST-070 LightHistory ring buffer and retarded-time solver (pure)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-035
- files: create `physics/LightHistory.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/LightHistoryTest.kt`
- goal: The first slice of the pilot view (owner decision 2026-11-02: pilot view becomes the main rendering, research E8). The pilot sees every object where it was when the light now arriving left it. This card is the pure data structure and solver, with no ECS.
- spec:
  - `const val LIGHT_HISTORY_CAPACITY = 659`: `ceil((arenaDiagonal / C_MIN + 0.5 s) / PHYSICS_UPDATE_INTERVAL)` with the 200×200 arena (diagonal 282.8 u, C_MIN = 20). KDoc the derivation. A sample is 6 floats, so about 16 KB per entity.
  - `class LightHistory(capacity: Int = LIGHT_HISTORY_CAPACITY)`: parallel `FloatArray`s `t, x, y, angle, vx, vy`, plus `head`, `size`, `wrapped: Boolean` (true once a sample has been overwritten) and `eternal: Boolean` (set by the caller: the entity existed at level start, so it is treated as having moved uniformly forever before its first sample). `fun record(t, x, y, angle, vx, vy)` appends and overwrites the oldest when full. `fun clear()`. Samples must have strictly increasing `t`.
  - `class RetardedState { var t; var x; var y; var angle; var vx; var vy }` (reusable output).
  - `fun LightHistory.retarded(ox: Float, oy: Float, now: Float, c: Float, out: RetardedState): Boolean`. It finds the emission time `t_e` with `|x(t_e) − o| = c·(now − t_e)`. Define `h(t_e) = |x(t_e) − o| − c·(now − t_e)`. It is increasing in `t_e`, because |v| < c.
    1. If `size == 0`, return false. If `h(newest) < 0`, the light from the newest sample hasn't arrived yet, which only happens when `now` is ahead of the buffer. Treat it as the newest segment and extrapolate forward with the step-3 formula (allowed for τ₁ < 0).
    2. Bisect over sample indices for the last sample `i` with `h(t_i) ≤ 0`. Then within the segment `[i, i+1]`, use constant velocity `v = (x_{i+1} − x_i)/(t_{i+1} − t_i)`.
    3. Closed form, looking back `s ≥ 0` from sample `i+1`: with `r₁ = x_{i+1} − o` and `τ₁ = now − t_{i+1}`, solve `|r₁ − v·s| = c·(τ₁ + s)`. That is `(c² − v²)s² + 2(r₁·v + c²τ₁)s + (c²τ₁² − r₁²) = 0`, so `s = [−(r₁·v + c²τ₁) + √((r₁·v + c²τ₁)² − (c² − v²)(c²τ₁² − r₁²))] / (c² − v²)`. With `τ₁ = 0` this is research E8's `T = [−(r₀·v) + √((r₀·v)² + (c² − v²)|r₀|²)]/(c² − v²)`. If `c² − v² < 1e-3·c²` (photons, which fly at c), bisect `s` over the segment for 24 iterations instead.
    4. Output `t = t_{i+1} − s` and the lerped `x, y, vx, vy, angle` (shortest-arc for the angle).
    5. If the root is older than the oldest sample: when `wrapped || eternal`, extrapolate backward from the oldest sample with its velocity (the step-3 formula with sample `oldest` as `i+1`), return true. Otherwise the object is younger than its light travel time and isn't visible yet, so return false.
  - No allocation in `retarded` or `record`.
- acceptance:
  - [ ] Test: a static object at distance 30, sampled every 1/45 s for 2 s, with c = 60 → `retarded` gives `t = now − 0.5 ± 1e-4` and the recorded position
  - [ ] Test: constant velocity (20, 0) from (30, 0), observer at origin → `t` matches the E8 closed form within 1e-4, and `|x(t) − o| = c·(now − t)` within 1e-3
  - [ ] Test: circular motion (radius 20 around (40, 0), ω = 1) → the light-cone equation holds within 1e-2 u
  - [ ] Test: a photon moving at exactly c (radially away) still returns a solution that satisfies the light-cone equation within 1e-2
  - [ ] Test: an entity with 0.1 s of history at distance 30 (not eternal, not wrapped) → false; the same with `eternal = true` → true, position extrapolated backward
  - [ ] Test: after more than `capacity` records the buffer has `wrapped`, and lookups still work
  - [ ] `./gradlew core:test` passes

### ST-071 LightHistorySystem: record every body each physics step
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-069, ST-070
- files: create `system/LightHistorySystem.kt`; edit `screen/PlayScreen.kt`; create `system/LightHistorySystemTest.kt`
- goal: Fill a `LightHistory` per entity in arena time, so that the pilot-view renderer (ST-074) and later the AI (E13, M6) can ask "where did the light now reaching point o leave this entity?".
- spec:
  - `LightHistorySystem : EntitySystem(), PhysicsStepListener, EntityListener`, with the family `allOf(BodyComponent, BaseInfoComponent, TransformComponent)`. It owns `time: Float` (starts at 0 and gains `dt` in every `afterStep`, so it equals `GameClock.worldTime`) and an `IntMap<LightHistory>` keyed by `BaseInfoComponent.id` (ids are unique per run; Ashley pools `Entity` objects, so don't key by `Entity`). It keeps a free list of `LightHistory` objects for reuse.
  - Register it as the **last** step listener, after `relativisticDynamics`, so it records the post-step state. `afterStep`: for every family entity record `(time, body.x, body.y, body.angle, v.x, v.y)`. An entity first seen at `time ≤ PHYSICS_UPDATE_INTERVAL` gets `eternal = true`.
  - `entityRemoved`: keep the history and store `removedAt = time` (an `IntFloatMap`). Prune entries in `afterStep` once `time − removedAt > LIGHT_HISTORY_CAPACITY·PHYSICS_UPDATE_INTERVAL` and return them to the free list. ST-079 draws these ghosts.
  - Query API: `fun historyOf(id: Int): LightHistory?`, `fun removedAt(id: Int): Float` (NaN while alive), `val now: Float`.
  - `PlayScreen` adds it to the engine and the listener list and calls `clear()` (all histories back to the free list, `time = 0`) in `show()`.
- acceptance:
  - [ ] Test (real `World` with `PhysicsSystem`): after 45 steps a body moving at (10, 0) has 45 samples, the newest at `time = 1 ± 1e-5`, with x advanced 10 ± 1e-3
  - [ ] Test: an entity added at step 0 is `eternal`; one added at step 20 isn't
  - [ ] Test: after removal the history stays, `removedAt` is set, and it is pruned after the capacity window
  - [ ] `./gradlew core:test` passes

### ST-072 PilotFrame: apparent position and hull-angle transforms (pure)
- status: ready
- size: S
- agent: junior-dev
- depends: ST-035
- files: create `physics/PilotFrame.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/PilotFrameTest.kt`
- goal: Pure functions for the boost into the pilot frame (research E9) and for the ship's hull angle between the arena frame and the ship's rest frame. Used by the pilot-view renderer (ST-074) and pilot-frame aiming (ST-076).
- spec (all allocation-free; `β = (betaX, betaY)`; if `|β| < 1e-6` every function is the identity):
  - `fun apparentOffset(rx: Float, ry: Float, betaX: Float, betaY: Float, out: Vector2): Vector2`. Here `r` is the arena-frame offset from the observer to the source's **retarded** position. With `b̂ = β/|β|`, `r∥ = r·b̂` and `r⊥ = r − r∥·b̂`: `r'∥ = γ·(r∥ + |β|·|r|)`, `r'⊥ = r⊥`, and `out = r'∥·b̂ + r'⊥`. Then `|r'| = D·|r|` with `D = γ(1 + β·r̂)`. KDoc: "Lorentz boost of the light-reception event into the pilot frame (research E9). Objects slide toward the direction of motion."
  - `fun worldHullToPilot(angleW: Float, betaX: Float, betaY: Float): Float`: take the unit vector at `angleW`, multiply its component along `b̂` by `γ`, return `atan2`. A rod at rest in the ship is contracted in the arena frame, so `tan θ_W = γ·tan θ_P` (angles measured from `b̂`).
  - `fun pilotHullToWorld(angleP: Float, betaX: Float, betaY: Float): Float`: same, but divide the component along `b̂` by `γ`.
  - `γ = 1/√(1 − β²)` with `β²` clamped to `BETA_MAX_MASSIVE²`.
- acceptance:
  - [ ] Test: `apparentOffset((0, 10), β = (0.9, 0))` → (20.65, 10) ± 0.01, i.e. 25.84° ± 0.05° from +x and length 22.94 ± 0.01
  - [ ] Test: `apparentOffset` at β = 0 is the identity; a point straight ahead `(10, 0)` at β = 0.9 maps to (43.59, 0) (= D·10 with D = 4.359); for 20 random `r` and β with |β| ≤ 0.95, `|r'| = γ(1 + β·r̂)·|r|` within 1e-3 relative
  - [ ] Test: `worldHullToPilot(45°, (0.9, 0)) = 23.55° ± 0.05°`; `pilotHullToWorld(45°, (0.9, 0)) = 66.45° ± 0.05°`; both functions round-trip within 1e-5 rad for 20 random angles and β; a hull parallel or perpendicular to β is unchanged
  - [ ] `./gradlew core:test` passes

### ST-073 PhotonAim: photon intercept and muzzle-direction inverse (pure, from ST-062)
- status: ready
- size: S
- agent: junior-dev
- depends: ST-035
- files: create `physics/PhotonAim.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/PhotonAimTest.kt`
- goal: Factor the fire-control math out of ST-062, so that both the lead marker (ST-078) and auto-aim (ST-062, parked) use one tested implementation.
- spec (no allocation):
  - `fun photonInterceptDir(rx: Float, ry: Float, vtx: Float, vty: Float, c: Float, out: Vector2): Float`. `r` is target minus shooter (now) and `v_t` the target's arena-frame velocity. Solve `|r + v_t·t| = c·t`: `a = v_t² − c²` (< 0), `b = 2·r·v_t`, `k = r²`, `t = (−b − √(b² − 4ak)) / (2a)`. Set `out = (r + v_t·t)/(c·t)` (a unit vector) and return `t`. Return `NaN` (and leave `out` alone) if `t` isn't finite or `t ≤ 0`. The shooter's velocity doesn't enter, because the photon moves at c in the arena frame anyway.
  - `fun muzzleDirForWorldDir(betaX: Float, betaY: Float, nWx: Float, nWy: Float, out: Vector2): Vector2` = `aberratePhoton(−βx, −βy, nWx, nWy, out)`. This is the inverse of the ST-039 muzzle aberration: the shooter-frame direction to fire so that the photon flies along `n̂_W`.
- acceptance:
  - [ ] Test: a static target gives `out = r̂` and `t = |r|/c`
  - [ ] Test: a target at (30, 0) moving (0, 20) with c = 60: a photon from the origin along `out` for time `t` ends within 1e-3 u of `r + v_t·t`
  - [ ] Test: `aberratePhoton(β, muzzleDirForWorldDir(β, n̂)) ≈ n̂` within 1e-5 for β = (0.9, 0), n̂ = (0, 1), and for 10 random cases with |β| ≤ 0.95
  - [ ] Test: `r = 0` returns NaN without crashing
  - [ ] `./gradlew core:test` passes

### ST-074 Pilot view: sprites drawn at retarded, boosted positions (default on)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-044, ST-059, ST-071, ST-072
- files: `physics/RelativitySettings.kt`, `physics/PhysicsConstants.kt`, `system/RenderSystem.kt`, `system/CameraSystem.kt`, `event/PlayerInputListener.kt`, `event/GameEventManager.kt`, `ui/DebugPanel.kt`, `screen/PlayScreen.kt`; tests
- goal: Make what the pilot actually sees the main rendering (owner decision 2026-11-02, research E8 + E9). Every other object is drawn where its light left it (light delay) and then boosted into the pilot's frame (aberration), so the world slides forward and stretches ahead at speed. Physics, collisions and the minimap stay in true arena positions. A flag keeps the old world view.
- spec:
  - Settings (append last, with defaults): `pilotView: Boolean = PILOT_VIEW_DEFAULT` (`const val PILOT_VIEW_DEFAULT = true`), `pilotAberration = true` and `pilotDelay = true` (debug switches for the two halves). All are plain `var`s, restored by `resetDebugDefaults`. Debug panel: three checkboxes "pilot view", "aberration" and "light delay".
  - The V key toggles `pilotView`: `PlayerInputListener.togglePilotView()` (default no-op). `GameEventManager.keyDown(Keys.V)` calls it on the listeners. `CameraSystem` implements it and flips `settings.pilotView`.
  - `CameraSystem` in pilot view: centre the camera exactly on the player's interpolated position with **no** arena clamp (the clamp is an arena-frame idea, and the boosted view has no fixed edges).
  - `RenderSystem` gets `LightHistorySystem`, `ObserverState`, `GameClock` and `RelativitySettings`. In pilot view, with `o` = the player's interpolated position, `β = observer.betaVisual` (the same smoothed β that ST-076 aiming uses) and `now = clock.worldTime − (1 − clock.interpolationAlpha)·PHYSICS_UPDATE_INTERVAL` (the interpolated arena time):
    - The player is drawn at `o`, unboosted, with rotation `worldHullToPilot(body.angle, β)` (in its own frame the ship is at rest and uncontracted).
    - Every other sprite entity: `historyOf(id).retarded(o, now, c, state)` (if `!pilotDelay`, use the current interpolated transform). If it returns false, skip the entity (its light hasn't arrived). Build the 4 sprite corners at the retarded position and angle, with the ST-044 contraction for the retarded velocity, in a reusable `FloatArray(20)`. Map each corner `p` to `o + apparentOffset(p − o, β)` (skip the boost if `!pilotAberration`), then `batch.draw(texture, verts, 0, 20)`.
    - Keep the world-view path unchanged when `pilotView` is false.
  - The minimap and the HUD keep using true positions.
  - No per-frame allocation (a reusable `RetardedState`, `Vector2` and vertex array).
- acceptance:
  - [ ] Pure test: extract `fun pilotCorner(px, py, ox, oy, betaX, betaY, aberration: Boolean, out: Vector2)`. A corner at o + (0, 10) with β = (0.9, 0) → o + (20.65, 10); with `aberration = false` → o + (0, 10)
  - [ ] Test (real engine, headless: drive `RenderSystem`'s position logic through a helper that doesn't need a GL batch): a static asteroid 30 u away appears at its true position when the player is at rest. With the player at β = 0.8 it appears shifted toward +x by the E9 formula within 0.05 u
  - [ ] Test: an asteroid spawned 1 step ago at distance 30 (not eternal) is not drawn
  - [ ] Screenshot via the harness with `--seed=42 --thrust --frames=150`, viewed by the agent: asteroids bunch up toward the direction of motion
  - [ ] `./gradlew core:test` passes
- playtest: Accelerate: the asteroid field visibly crowds forward and stretches ahead; things beside you appear ahead. V switches between pilot view and the true (world) view. Hitting things should still feel fair; note anything that feels like a desync.

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

### ST-065 Debug panel polish (ST-059 follow-ups)
- status: ready
- size: S
- agent: junior-dev
- depends: ST-059
- files: `ui/DebugPanel.kt`, `system/MinimapSystem.kt`
- goal: Clean up the issues the ST-059 screenshot showed.
- spec:
  - The minimap (bottom-right) covers the controls selectbox and the reset button. While the panel is visible, move the minimap left of the panel, or move it to the bottom-left.
  - Checkbox checked and unchecked states look the same in this skin. Tint the checked state or use a clearer style.
  - The UI font has no Greek glyphs, so `DebugPanel.update` swaps in ASCII names. Drop that swap if a Greek-capable font is added.
  - (ST-059 review, approved, minors) Swap the Greek letters for ASCII inside the reused StringBuilder instead of chaining `String.replace` calls. Pace the 10 Hz refresh in `DebugPanelSystem` on real time (`Gdx.graphics.rawDeltaTime`), not the scaled delta. Call `select.hideList()` in `hide()` and `toggleDebugPanel()`. Make the toggle-flip logic testable without GL and add the `panelVisible` test.
- acceptance:
  - [ ] A screenshot with `--debug-panel` shows every widget unobstructed
  - [ ] `./gradlew core:test` passes

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

### ST-100 Readable backdrop: calmer nebula, reference grid, arena-centre pointer
- status: ready
- size: M
- agent: kotlin-dev
- depends: —
- files: `system/RenderSystem.kt` (nebula/starfield), create `render/ReferenceGridRenderer.kt`, `system/HudSystem.kt` (or the HUD stage), tests for the pure parts
- goal: Owner (2026-11-02): "Current background is too much. Player needs to have some way to orient themselves." Make motion and position readable at any speed.
- spec:
  - Tone the nebula down: alpha/brightness constant `NEBULA_ALPHA = 0.35f` (from today's effective 1). Keep at most one sparse starfield layer at full brightness, and dim the rest.
  - `ReferenceGridRenderer`: faint lines in arena space every `GRID_STEP = 20f` u (alpha 0.07), with the two axes through the arena centre at alpha 0.18. Use `ShapeRenderer` lines in a single batch, culled to the camera. Draw it after the backdrop and before the sprites. Moving past the grid shows your speed and direction even when nothing else is nearby.
  - HUD pointer: when the arena centre is off-screen, draw a small arrow at the screen edge pointing to it, with the distance in u. Put a pure helper `edgePointer(camX, camY, halfW, halfH, tx, ty, margin, out: Vector2): Float` (returns the angle, places `out` on the inset screen rectangle) in `render/EdgePointer.kt`.
  - Toggles `GRID_ENABLED`, `CENTRE_POINTER_ENABLED` in settings and the debug panel.
  - Pilot view note for ST-077: the grid is a set of static arena points, so it goes through the same static-point transform and bends visibly at high β. This is a nice readout, not a bug.
- acceptance:
  - [ ] Test: `edgePointer` with a target to the right, off-screen, gives angle 0 and `out.x` = right inset edge; a diagonal target lands on a corner region; an on-screen target is flagged (e.g. returns NaN)
  - [ ] `./gradlew core:test` passes
- playtest: The background is calmer. Gliding over the grid shows your speed. The arrow always leads you back to the centre.

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

### ST-050 Plan M4 (relativistic view) from research
- status: done (2026-11-02, replaced: planned directly from the 2026-11-02 interview as ST-070–ST-081)
- size: S
- agent: planner
- depends: ST-037
- files: `docs/backlog.md`
- goal: Turn research §4 Tier 2 (E8–E13) and §7 items 16–20 into M4 cards, along with owner questions Q5, Q8, Q13 and Q14.
- outcome: Q5 answered by the owner: pilot view is the default (b). Q8 answered: the minimap shows true positions (a). Q14 is planned around the recommendation (b), a 25 % floor plus outlines (ST-080). Q13 (enemies obey light delay) moves to M6 with the enemies; `LightHistorySystem` (ST-071) already supports it. E11 (apparent animation rate) waits until there are animated sprites.

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

### ST-092 Owner decision: how strongly gravity sources pull each other
- status: done (2026-11-02)
- result: Owner: (b) 0.1, "lets go with simpler solution now and note all physics lies in doc". The preferred future fix is to give the whole system initial velocities so it's gravitationally balanced (physics-lies.md row 14, game-design §12). `SOURCE_COUPLING_DEFAULT = 0.1f` is already in ST-082.
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11; `physics/Gravity.kt` (`SOURCE_COUPLING_DEFAULT`, only if the answer is not (b))
- goal: Record the owner's choice. ST-083 implements the recommendation as a debug slider, so this can be tried in-game before answering.
- question: Black holes and planets pull each other (owner 2026-11-02). With physically consistent strength (black hole GM = 3600 u³/s²), two holes placed 60 u apart fall together and merge in about 6 s, so most levels would start with a merger. How strong should source–source gravity be? (a) Physical (`sourceCoupling = 1`): fast mergers, chaotic. (b) **Scaled down to 0.1 (recommended)**: a declared FAKE; two holes meet in about 19 s, planets drift visibly over a level, and a merger is an event rather than the opening. (c) Off between sources (`0`): sources only feel the player's pull and stay almost static.
- acceptance:
  - [ ] The answer is in game-design.md §11 Decisions. If (a) or (c): set `SOURCE_COUPLING_DEFAULT` to 1f or 0f
  - [ ] `./gradlew core:test` passes

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
- status: needs-user
- size: S
- agent: junior-dev
- depends: ST-058, ST-061, ST-062, ST-091
- files: `docs/design/game-design.md` §4 and §11; `input/ControlSettings.kt` (default)
- goal: Record which scheme is the default once the owner has tried them all through `--controls=` or the debug panel selector. The others stay selectable. Parked with the modes (2026-11-02); ask once ST-061, ST-062 and ST-091 are done.
- question: Which control scheme should be the default? (a) Mouse aims and rotates the hull, RMB thrusts forward (today). (b) **Twin-stick (recommended)**: WASD (later the left stick) sets the thrust direction in the arena frame, the hull turns toward it, and the mouse (later the right stick) aims an independent turret. Separating aim from thrust lets the player manage momentum near c, which is the core skill, while still shooting. (c) Auto-aim + auto-shoot (ST-062): you only steer and manage momentum. Better as an accessibility option. (d) Targeting trance (ST-091): hold LMB for slow-mo aim with an instant turn, release for one shot, then a 1 s cooldown. It's a FAKE time stop, so it's better as an option than as the default.
- acceptance:
  - [ ] The answer is in game-design.md §11 (decisions) and §4. `ControlSettings` defaults to the chosen scheme. For (b), ask the planner for a gamepad card (gdx-controllers, sticks → `moveAxis`/aim)
