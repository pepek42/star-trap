# Backlog archive

Finished cards, moved here from `docs/backlog.md` by `scripts/backlog.py archive`. Kept for history; agents don't need to read this file. Look up one card with `scripts/backlog.py show ST-NNN`.

## M0 Tooling & stack

### ST-001 Migrate build to the new gdx-liftoff Kotlin template stack
- status: done (2026-10-26, 1810f4f)
- result: Gradle 9.8.1, Kotlin DSL, version catalog, `build-logic` convention plugin, JDK 25 toolchain.

### ST-002 Set up agents, skills and design/tech docs
- status: done (2026-11-01, de6161b)
- result: `.claude/agents/*`, `.claude/skills/{auto-dev,plan-backlog}`, `docs/` structure.

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

## M2 Arena boundary field

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
- status: done (2026-11-02, 786a41f)
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
- status: done (2026-11-02, 2207091)
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
  - [x] Test (`MapProvider(mock(), 100f, 100f)`, real `World`, kinematic sensor body with `ProjectileComponent`, `BaseInfoComponent` and `TransformComponent`): starting at (95, 50) with velocity (60, 0), after 10 steps of `beforeStep` + `world.step` + `afterStep`, x > 100 and the velocity is still (60, 0) exactly. `impacts.count == 1`, with the impact at x = 100 and y = 50 ± 1e-3. Ten more steps add no impact
  - [x] Test: a kinematic body **without** `ProjectileComponent` in the skin still gets the inward velocity kick (existing behaviour)
  - [x] Test: with `fieldEnabled = false` a projectile also passes the wall untouched and still emits its one ripple
  - [x] `./gradlew core:test` passes
- playtest: Shoot at the edge: the bolt passes through with one ripple and disappears off-screen. Asteroids and the ship still bounce softly.
- result: BoundaryFieldSystem skips projectiles (no kick/backstop) and emits one exit ripple; bouncesLeft/PHOTON_MAX_BOUNCES removed; 164 tests pass.

### ST-060 Debug vector overlay: v, u and acceleration arrows
- status: done (2026-11-02, 9237374)
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
  - [x] Test: `arrowSegments` for (0, 0) + (4, 0)·0.25 gives a shaft (0, 0)→(1, 0) and heads ending at `1 − 0.4·cos25°, ±0.4·sin25°`
  - [x] Test: after one step with thrust, `lastAccel` is non-zero, and after a step without thrust it's zero
  - [x] `./gradlew core:test` passes
- result: F1 "show vectors" draws green v, cyan u and orange applied-accel arrows plus a c ring around the player; scales are c-relative (ring radius 5 u, full engine accel = 3 u).
- playtest: At speed the green `v` arrow stops at the c ring while the cyan `u` keeps growing. Thrusting sideways at speed shows a short orange arrow (the 1/γ² turning cost). In the field skin the orange arrow points inward.

### ST-039 Photon lasers: velocity aberration at the muzzle
- status: done (2026-11-02, 49f9823)
- size: S
- agent: kotlin-dev
- depends: ST-024, ST-035, ST-056, ST-067
- files: create `component/PhotonComponent.kt`; edit `provider/WeaponProjectileProvider.kt`; tests in `provider/WeaponProjectileProviderTest.kt`
- goal: Lasers fly at exactly c in a direction that aberrates with the shooter's velocity (research E3). Today projectiles ignore the ship's velocity, so at speed the shots don't sweep forward as they physically should. This follows ST-034 (a). **Reworked 2026-11-02:** the field-mirror and bounce part is dropped, because shots now leave the arena (ST-067). `PhotonComponent.dir` stays because gravity bends photons later (ST-083).
- spec:
  - `PhotonComponent { val dir = Vector2() }` (unit vector, Poolable, reset to zero, mapper + `photonCmp` / `photonCmpOptional`, per the component convention).
  - `WeaponProjectileProvider`: the shooter-frame direction is `n̂' = (cos aim, sin aim)` with `aim = weaponCmp.aimAngleRad ?: body.angle` (`aimAngleRad` and the hull angle are treated as shooter-frame angles; ST-076 later converts the hull fallback properly). `v_ship = relativisticCmpOptional?.vSet ?: body.linearVelocity`, `β = v_ship / settings.c`. `n̂ = aberratePhoton(β, n̂')`. If `!settings.relativityEnabled`, `n̂ = n̂'`. Body velocity = `settings.c·n̂`. The body angle **and** `TransformComponent.prevRotationDeg` = `atan2(n̂)`, the latter in **degrees** (today it is set to radians, a bug). Add `PhotonComponent(dir = n̂)`. The muzzle offset keeps using `aim`. The lifetime from ST-024 stays.
- acceptance:
  - [x] Test: a ship at rest facing +x fires a laser with velocity (60, 0) ± 1e-3 and `prevRotationDeg == 0`
  - [x] Test: a ship moving at β = 0.9 along +x, facing +y, fires a laser at a W angle of 25.84° ± 0.1° with |v| = 60 ± 1e-3, and `prevRotationDeg ≈ 25.84`
  - [x] Test: same ship with `relativityEnabled = false` fires straight along +y
  - [x] `./gradlew core:test` passes
- playtest: Lasers fired sideways at speed visibly sweep forward.
- result: Lasers spawn at c along aberratePhoton(β_ship, aim) with a PhotonComponent dir and sprite rotation in degrees; muzzle-offset follow-up noted on ST-076.

### ST-068 Pilot clock: time-warp functions, GameClock and settings
- status: done (2026-11-02, e5b3861)
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
  - [x] Tests: `timeWarpFactor(3f, true, 10f) == 3f`; `(25f, true, 10f) == 10f`; `(0.5f, true, 10f) == 1f`; `(3f, false, 10f) == 1f`; `(NaN, true, 10f) == 1f`. `isWarpCapped(25f, true, 10f)` is true, `(10f, true, 10f)` and `(25f, false, 10f)` are false. `worldDelta(1/60f, 10f, 10/45f) ≈ 1/6`, `worldDelta(0.1f, 10f, 10/45f) == 10/45f`
  - [x] Test: `GameClock.reset()` restores all defaults; `RelativitySettings(timeWarpMax = 50f).timeWarpMax == 12f`, `0f` clamps to 1
  - [x] `./gradlew core:test` passes
- result: `physics/TimeWarp.kt` (warp factor, cap check, world delta, `GameClock`) plus the time-warp constants and `RelativitySettings` fields, all unit-tested and not wired yet.

### ST-069 Pilot clock wiring: the arena advances γ·dt per frame, up to 10 steps
- status: done (2026-11-02, 09d23e9)
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
  - [x] Test (`TimeWarpSystemTest`, real `World` with `PhysicsSystem` + `RelativisticDynamics` + `TimeWarpSystem`): a player whose `u` gives γ = 4, with no thrust, after one `engine.update(1/45f)` has run exactly 4 physics steps (`clock.worldTime == 4/45 ± 1e-5`, position advanced by `4·v/45 ± 1e-3`)
  - [x] Test: γ = 20 with `timeWarpMax = 10` → `warp == 10`, `warpCapped`; `engine.update(0.1f)` → `worldDt == 10/45`, `stepsCapped`, and at most 10 steps ran
  - [x] Test: `timeWarpEnabled = false` or `relativityEnabled = false` → `warp == 1` at any γ
  - [x] Test: a WORLD lifetime of 2 s on an entity expires after 0.5 s of real time at warp 4 (±1 frame)
  - [x] Test: `formatReadouts(..., clock)` with warp 4 and `stepsCapped = true` contains `warp = ×4.0 (capped)`
  - [x] `./gradlew core:test` passes
- result: `TimeWarpSystem` drives a per-level `GameClock`. Physics (up to 10 steps per frame), WORLD lifetimes and world time now run on warp·real dt, and the debug panel has a warp toggle, a cap slider and a readout.
- playtest: At speed the asteroids and the world visibly speed up while the ship handles as usual. Past γ = 10 the debug panel shows "(capped)". With "time warp" off the game plays as before.

### ST-040 Ship clocks under the pilot clock: player on real time, others on proper time
- status: done (2026-11-02, 8440066)
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
  - [x] Test: the player's weapon fires the same number of shots over 3 s of real time at warp 4 as at warp 1 (±1 shot)
  - [x] Test: a non-player weapon entity with `gUsed = 2` fires half as often per second of world time as at `gUsed = 1` (±1 shot)
  - [x] Test: a non-player PROPER lifetime of 1.2 at `gUsed = 3.2` lasts 3.84 s ± 1 step of world time
  - [x] Test: a player 90° turn with the clock at warp 4 takes 4× ± 15 % as much world time as at warp 1 (so the same real time), with overshoot still < 5°
  - [x] Test: an asteroid spinning at 2 rad/s whose `u` is set to γ = 2 spins at 1 ± 0.02 rad/s after one step
  - [x] `./gradlew core:test` passes
- result: Player cooldown and turning run on real time (turn PD slowed by 1/warp), other ships on worldDt/γ, and free spin scales as ω₀/γ.
- playtest: Near c the world rushes past (warp) but the fire rate and turning feel the same as at rest. Asteroids spin slower when they move fast.

### ST-042 HUD shows β, γ, ship and world time and the time warp; speed zoom
- status: done (2026-11-02, 3501b8a)
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
  - [x] Tests: `speedZoom(1f, 0f) == 1f`; `speedZoom(1f, 0.9f) ≈ 1.486`; `speedZoom(2f, 0.9f) == 2f`. `warpLabelKey(1f, false) == null`, `(4f, false) == "hud_warp"`, `(10f, true) == "hud_warp_capped"`, `(1f, true) == "hud_warp_capped"`
  - [x] The i18n keys exist in both properties files
  - [x] `./gradlew core:test` passes
- result: HUD shows a rapidity bar, beta, gamma, ship/world time and the warp factor (ASCII names, the font has no Greek); the camera zooms out with beta.
- playtest: The β bar fills fast at first, then slowly near c. World time races ahead of ship time at speed, and the warp label shows the factor, marked capped above 10×. The view widens smoothly as you speed up.

### ST-070 LightHistory ring buffer and retarded-time solver (pure)
- status: done (2026-11-02, 0dae081)
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
  - [x] Test: a static object at distance 30, sampled every 1/45 s for 2 s, with c = 60 → `retarded` gives `t = now − 0.5 ± 1e-4` and the recorded position
  - [x] Test: constant velocity (20, 0) from (30, 0), observer at origin → `t` matches the E8 closed form within 1e-4, and `|x(t) − o| = c·(now − t)` within 1e-3
  - [x] Test: circular motion (radius 20 around (40, 0), ω = 1) → the light-cone equation holds within 1e-2 u
  - [x] Test: a photon moving at exactly c (radially away) still returns a solution that satisfies the light-cone equation within 1e-2
  - [x] Test: an entity with 0.1 s of history at distance 30 (not eternal, not wrapped) → false; the same with `eternal = true` → true, position extrapolated backward
  - [x] Test: after more than `capacity` records the buffer has `wrapped`, and lookups still work
  - [x] `./gradlew core:test` passes
- result: `LightHistory` stores 6-float samples in a ring buffer whose capacity (~1730) is derived from the arena diagonal, C_MIN and tick rate. `retarded()` solves the light cone in closed form per segment, with photon bisection and extrapolation backward/forward. 12 tests.

### ST-065 Debug panel polish (ST-059 follow-ups)
- status: done (2026-11-02, a60b608)
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
  - [x] A screenshot with `--debug-panel` shows every widget unobstructed
  - [x] `./gradlew core:test` passes
- result: Panel scrolls (wheel only while hovered), minimap moves left of it, checkboxes tinted green/grey, refresh paced on real time, `flipPanelVisible` tested.
- playtest: With F1 open, wheel over the panel scrolls it and elsewhere zooms; ticked checkboxes look green; the minimap sits left of the panel.

### ST-100 Readable backdrop: calmer nebula, reference grid, arena-centre pointer
- status: done (2026-11-02, 9dc4f26)
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
  - [x] Test: `edgePointer` with a target to the right, off-screen, gives angle 0 and `out.x` = right inset edge; a diagonal target lands on a corner region; an on-screen target is flagged (e.g. returns NaN)
  - [x] `./gradlew core:test` passes
- result: Star layers dimmed to {0.25, 0.4, 1}, a 20 u ShapeRenderer grid is drawn between the backdrop and the sprites, and a HUD edge arrow with a distance label points to the arena centre. Both have debug-panel toggles.
- playtest: The background is calmer. Gliding over the grid shows your speed. The arrow always leads you back to the centre.

### ST-102 Physics catch-up cap: 10 steps only under time warp
- status: done (2026-11-02, da5d6a8)
- size: S
- agent: junior-dev
- depends: ST-069
- files: `system/PhysicsSystem.kt`, `system/TimeWarpSystem.kt`, `system/TimeWarpSystemTest.kt`
- goal: ST-069 review follow-up. The catch-up cap went from 5 to 10 steps for every frame, so at warp 1 a lag spike now runs 10 steps, which risks a spiral of death on slow machines. Keep 10 steps for warp, and go back to 5 for plain lag.
- spec:
  - `PhysicsSystem` companion: add `const val MAX_LAG_STEPS = 5`. In `TimeWarpSystem.update`: `maxWorld = min(MAX_STEPS_PER_FRAME·INTERVAL, max(1f, clock.warp)·MAX_LAG_STEPS·INTERVAL)`. Warp 1 caps at 5 steps, and warp ≥ 2 caps at 10. Set `stepsCapped` against this `maxWorld`.
  - In `TimeWarpSystemTest.warpIsOneWhenDisabled`: also assert that `worldTime` advanced by exactly 1/45 ± 1e-5 after `update(1/45f)`.
- result: TimeWarpSystem caps catch-up at max(1,warp)·5 steps (≤10), so plain lag runs at most 5 steps.
- acceptance:
  - [x] Test: warp 1, `engine.update(0.5f)` → at most 5 steps (`worldTime ≤ 5/45 + 1e-5`); warp 4, `engine.update(0.5f)` → 10 steps
  - [x] `./gradlew core:test` passes

### ST-103 Edge pointers to off-screen asteroids and enemies (replaces the centre pointer)
- status: done (2026-11-02, 267a7a4)
- size: M
- agent: kotlin-dev
- depends: ST-100
- files: `system/HudSystem.kt`, `ui/Hud.kt`, `render/EdgePointer.kt` (reuse), `render/ReferenceGridRenderer.kt` (toggle const), `debug/DebugSettings.kt`, `ui/DebugPanel.kt`, create a pure selection helper (e.g. `render/TargetPointers.kt`) and its test, `ui/DebugPanelTest.kt`
- goal: Owner (2026-11-02): "Pointer to map middle makes little sense. Make pointer to enemies/neutrals." The grid axes already mark the centre, so the arrows should lead the player to things to shoot.
- spec:
  - Remove the arena-centre arrow and its distance label. Rename the toggle to `TARGET_POINTERS_ENABLED` / `DebugSettings.targetPointersEnabled` (default true, restored by `resetDebugDefaults`, with a checkbox labelled "target pointers").
  - Targets: entities with `BaseInfoComponent.faction` NEUTRAL or ENEMY and `type != PROJECTILE`, not tagged with `RemoveComponent`. Use their interpolated transform position.
  - Pure helper `selectPointerTargets(camX, camY, halfW, halfH, candidates, maxCount, out)`: keep only the off-screen candidates, sort them by distance from the camera centre, and keep the nearest `MAX_TARGET_POINTERS = 6`. Don't allocate per frame (reuse arrays and pools).
  - For each selected target, `edgePointer(...)` gives the arrow's position and angle. Draw a small filled arrow in the same ShapeRenderer pass as today. Colour: neutral = light grey, enemy = red. Alpha fades with distance, from 0.9 at the screen edge to 0.3 at `POINTER_FADE_DIST = 120f` u and beyond.
  - Show a distance label (u, measured from the player) on the **nearest** arrow only. Reuse the existing label and its placement code (the letterbox-correct projection from ST-100), and change the i18n key name if needed in both properties files.
  - Hide everything while the HUD is hidden (keep the ST-100 behaviour).
- acceptance:
  - [x] Test: with 10 off-screen and 2 on-screen candidates, the helper returns the 6 nearest off-screen ones, ordered nearest first; on-screen ones are never returned; with no candidates it returns 0
  - [x] Test: `resetDebugDefaults` restores `targetPointersEnabled`
  - [x] `./gradlew core:test` passes
- result: The centre arrow is replaced by up to 6 edge arrows (grey for neutral, red for enemy, fading with distance) to the nearest off-screen targets, with a distance label on the nearest one. The toggle is `targetPointersEnabled`.
- playtest: Arrows at the screen edge point at the nearest asteroids out of view. The nearest arrow shows its distance, and far ones are fainter. There is no arrow to the map centre any more.

### ST-104 Debug panel: ship mass, velocity vector, % of c
- status: done (2026-11-02, 011e884)
- size: S
- agent: junior-dev
- depends: —
- files: `ui/DebugPanel.kt` (`formatReadouts`), `system/DebugPanelSystem.kt`, `ui/DebugPanelTest.kt`
- goal: Owner (2026-11-02): "Add debug about ship mass, total velocity, % of light speed."
- spec:
  - `formatReadouts` gets a new last param `mass: Float = Float.NaN` (the player's Box2D `body.mass`, i.e. rest mass). `DebugPanelSystem` passes the player's body mass.
  - Change the `|v|` line to `|v| = 45.0 u/s (75.0 % c)`, with the percentage being `β·100`, 1 dp.
  - Add a line `v = (vx, vy) u/s` (1 dp each), with the components taken from `observer.betaRaw·c`.
  - When `mass` is finite, add a line `m = 12.3   γm = 45.6`, both 1 dp (rest mass and relativistic mass `γ·m`). When it isn't finite, leave the line out.
  - Keep the existing lines and their order otherwise. Insert the new lines right after the `|v|` line.
- acceptance:
  - [x] Test: β = 0.75 with c = 60 gives a line containing `|v| = 45.0 u/s (75.0 % c)`; `mass = 2f` with γ = 1.5 gives `m = 2.0   γm = 3.0`; the default mass has no `m =` line
  - [x] `./gradlew core:test` passes
- result: The debug panel shows `|v|` with a percentage of c, the velocity vector, and the rest and relativistic mass (from the player body).
- playtest: F1 shows the ship's mass, its velocity vector and the speed as a percentage of c.

### ST-105 ST-040 follow-ups: relativity-off spin restore test, null-target spin
- status: done (2026-11-02, 1b1e3ea)
- size: S
- agent: junior-dev
- depends: ST-040
- files: `system/ShipClocksTest.kt`, `system/RelativisticDynamics.kt` (only if the check below fails)
- goal: Minor issues from the ST-040 review.
- spec:
  - Add a test to `ShipClocksTest`: an asteroid-like entity (RelativisticComponent, no MoveComponent) with `gUsed = 2`, `angularVelocity = 1`, relativity disabled; after one step `angularVelocity ≈ 2 ± 0.02` and `gUsed == 1`.
  - Check whether the player's `MoveComponent.targetAngleRad` can be null after spawn. If it can, the player's spin is rescaled as free spin by `RelativisticDynamics.isRotationDriven`. In that case treat any entity with a `MoveComponent` as rotation-driven (no rescale) and add a test; otherwise just report that it can't happen.
- result: Added a relativity-off spin-restore test. The player's targetAngleRad is always set by PlayerInputSystem before physics, so isRotationDriven is unchanged (revisit if a new ControlStrategy can leave it null).
- acceptance:
  - [x] Test: relativity-off restores ω·gUsed and resets gUsed to 1
  - [x] `./gradlew core:test` passes

### ST-106 Derive physics from base constants; 60 Hz tick; c = 20
- status: done (2026-11-02, 61d2be0)
- size: M
- agent: kotlin-dev
- depends: —
- files: `physics/PhysicsConstants.kt`, `system/PhysicsSystem.kt`, `system/TimeWarpSystem.kt`, `physics/RelativitySettings.kt`, `ui/DebugPanel.kt`, `environment/asteroid.kt`, `provider/AsteroidProvider.kt`, `docs/design/physics-lies.md` (row 3), tests that hardcode 45 Hz or c = 60
- goal: Owner (2026-11-02): "Can we increase physics tick rate? … ship really speeds up. Maybe c should be smaller? 1/3?" Also apply the new CLAUDE.md rule "Constants are the single source of truth": changing the tick rate or c must not need edits anywhere else.
- spec:
  - `PhysicsSystem` companion: `const val PHYSICS_TICK_RATE = 60f`, `PHYSICS_UPDATE_INTERVAL = 1f / PHYSICS_TICK_RATE`. `MAX_STEPS_PER_FRAME = ceil(TIME_WARP_MAX * PHYSICS_TICK_RATE / 60f)` (the full default warp fits at 60 fps; 10 at 60 Hz). `MAX_LAG_STEPS = ceil(MAX_LAG_SECONDS * PHYSICS_TICK_RATE)` with `MAX_LAG_SECONDS = 5f / 45f` (same lag budget as ST-102). These are `val`s if Kotlin `const` can't compute them.
  - `PhysicsConstants.kt`: `BOX2D_MAX_TRANSLATION = 2f`; `C_MAX_BOX2D_SAFE = 0.75f * BOX2D_MAX_TRANSLATION * PHYSICS_TICK_RATE` (90 at 60 Hz); `SPEED_OF_LIGHT = 20f`; `C_MIN = 10f`. KDoc states the derivations, no literal `45`.
  - `ENGINE_PROPER_ACCEL = SPEED_OF_LIGHT / ENGINE_TIME_TO_09C_FACTOR` where the factor keeps the current feel: 0.9c reached in ≈ 5 s (`a = c·γβ/5 s`, γβ(0.9) = 2.065, so ≈ 8.26 at c = 20). Update the KDoc numbers. `RelativitySettings` engine clamp range: derive from c defaults or widen so 8.26 is not clamped.
  - `ASTEROID_SPAWN_SPEED_MAX` becomes a fraction of c (`ASTEROID_SPAWN_BETA_MAX = 8f / 60f`), and `AsteroidProvider` multiplies by `settings.c`.
  - `DebugPanel` c slider uses `C_MIN`..`C_MAX_BOX2D_SAFE`, not literals.
  - Grep `core/src/main` for remaining literals derived from 45 Hz or c = 60 (`45`, `1f/45`, `60f` used as c, `67`), and replace them per the CLAUDE.md rule. List any you deliberately keep in the report.
  - Tests: replace hardcoded `1f/45`, `45f`, c = `60f` with `PHYSICS_UPDATE_INTERVAL` / `SPEED_OF_LIGHT` / `settings.c`. Fix tests that pinned tuned numbers by deriving them from the constants, not by re-pinning new literals.
  - Physics-lies row 3: update to "c = 20 u/s, well under the ~90 u/s Box2D cap at 60 Hz".
  - Perf: add a tagged/ignored-by-default benchmark test or a `main` in tests that builds a world with the level-5 asteroid count (25 large asteroids + player), runs 600 steps, and prints mean ms per `world.step`. Run it once and put the number in the report.
- acceptance:
  - [x] Test: changing `PHYSICS_TICK_RATE` is a one-line change. A unit test asserts `C_MAX_BOX2D_SAFE == 0.75f * 2f * PHYSICS_TICK_RATE` and `MAX_STEPS_PER_FRAME * PHYSICS_UPDATE_INTERVAL * 60f >= TIME_WARP_MAX`
  - [x] Test: default engine reaches 0.9c in 5 s ± 0.3 s of proper time
  - [x] `grep -rnE "1f ?/ ?45|\b45f\b" core/src` finds nothing
  - [x] `./gradlew core:test` passes; benchmark number reported
- result: Tick is 60 Hz and c = 20; the Box2D speed cap, step caps, engine accel (0.9c in 5 s arena time) and asteroid speeds derive from the base constants. Benchmark: 0.015 ms per world.step with 51 asteroids (~1 % of a frame at warp 10).
- playtest: The ship tops out at a third of the old speed (c = 20) but takes as long to get relativistic. Time warp and Doppler effects show up at much lower on-screen speeds. Motion looks a bit smoother at 60 Hz.

### ST-107 ST-106 follow-ups: c-relative engine and thresholds, warp step cap, slider literals
- status: done (2026-11-02, bab77ae)
- size: S
- agent: kotlin-dev
- depends: ST-106
- files: `physics/PhysicsConstants.kt`, `physics/RelativitySettings.kt`, `system/PhysicsSystem.kt`, `ui/DebugPanel.kt`, `system/MoveSystem.kt`; tests
- goal: Minor issues from the ST-106 review and the implementer's notes, so changing c or the tick rate at runtime/compile time keeps the feel (CLAUDE.md "Constants are the single source of truth").
- spec:
  - Engine accel follows runtime c: `RelativitySettings` stores the engine as a fraction of c (`engineAccelPerC`, default `ENGINE_PROPER_ACCEL / SPEED_OF_LIGHT`), and exposes `engineProperAccel get() = engineAccelPerC * c`. The debug slider edits the fraction (or a0 in u/s² mapped back). Changing c keeps the 0.9c-in-5 s feel.
  - `COLLISION_EPS` as a fraction of c (`COLLISION_EPS_BETA = 0.05f / 60f`, used as `COLLISION_EPS_BETA * settings.c`).
  - `MAX_STEPS_PER_FRAME` sized from `TIME_WARP_MAX_LIMIT`, not `TIME_WARP_MAX`, so warp 11–12 is not silently step-capped at 60 Hz. Fix the stale "9 steps per frame" KDoc.
  - DebugPanel γ-cap and warp-cap sliders use `GAMMA_MAX_MIN`/`GAMMA_MAX_MASSIVE` and `TIME_WARP_MAX_LIMIT` (make `GAMMA_MAX_MIN` public in PhysicsConstants).
  - Tests: `RelativitySettingsRuntimeTest:85` divides by the c it sets, not a literal `30f`; `PhysicsConstantsTest` uses `BOX2D_MAX_TRANSLATION` instead of `2f`.
- acceptance:
  - [x] Test: setting `settings.c = 2 × default` doubles `engineProperAccel`, and a run still reaches 0.9c in 5 s ± 0.3 s of arena time
  - [x] Test: `MAX_STEPS_PER_FRAME * PHYSICS_UPDATE_INTERVAL * 60f >= TIME_WARP_MAX_LIMIT`
  - [x] `./gradlew core:test` passes
- result: Engine accel is stored as a fraction of c and collision eps as β, the step cap covers warp 12, and debug sliders read constants.
- playtest: Moving the c slider in the debug panel keeps the ship's acceleration feel; warp 12 is reachable.

### ST-108 ST-070 follow-ups: LightHistory zero-dt guard, level-relative sample time
- status: done (2026-11-02, ad7888d)
- size: S
- agent: junior-dev
- depends: ST-070
- files: `physics/LightHistory.kt`; `LightHistoryTest.kt`
- goal: Minor issues from the ST-070 review.
- spec:
  - In `retarded` (the segment solve and the extrapolation paths, around LightHistory.kt:171 and :193): if a segment has `dt <= 0`, use `s = 0` (or take the later sample) instead of dividing by zero. `record` keeps documenting strictly increasing `t`, but the solver must never return NaN.
  - KDoc on `record`: callers pass time relative to level start (Float resolution). ST-071 must follow this. No storage change.
- acceptance:
  - [x] Test: two samples with equal `t` → `retarded` returns finite values (no NaN)
  - [x] `./gradlew core:test` passes
- result: A zero-length segment now returns the later sample instead of NaN, and the record KDoc requires level-relative time.
- playtest: none (no visible change).

### ST-109 UI font: Greek and Polish glyphs; fix '?' in messages_pl
- status: done (2026-11-03, 8ccefb3)
- size: S
- agent: junior-dev
- depends: ST-042
- files: `assets/ui/font.fnt` (+ page png), `assets/i18n/messages_pl.properties`, `ui/Hud.kt`, `ui/DebugPanel.kt`
- goal: ST-042 follow-up. `assets/ui/font.fnt` lacks Greek and Polish glyphs, so the HUD and debug panel write "beta"/"gamma", and `messages_pl.properties` already has literal `?` where Polish letters belong.
- spec:
  - Regenerate the UI bitmap font with Latin Extended-A (Polish), Greek (β γ τ) and `×·` included, at the same size/style.
  - Replace the `?` corruption in `messages_pl.properties` with the right Polish letters (as `\uXXXX` escapes if the file is read as ISO-8859-1).
  - Switch `hud_beta`/`hud_gamma` back to β/γ and drop the ASCII swap in `DebugPanel.update` (see ST-065).
- acceptance:
  - [x] Screenshot with `--debug-panel` in Polish shows β, γ and Polish diacritics correctly
  - [x] `./gradlew core:test` passes
- result: New `font-glyphs.fnt/.png` generated from Liberation Sans by `tool/font/gen_font.py` (Pillow); uiskin.json points to it, Polish strings fixed, HUD/debug panel show β γ τ.

### ST-110 UI font cleanup (ST-109 follow-ups)
- status: done (2026-11-03, 7094ddc)
- size: S
- agent: junior-dev
- depends: ST-109
- refs: ST-109 in `docs/backlog-done.md`, `tool/font/gen_font.py`
- files: `assets/ui/uiskin.atlas` (+ png), `ui/DebugPanel.kt`
- goal: Tidy up after the font swap.
- spec:
  - Remove the now-unused `font` region from `assets/ui/uiskin.atlas` (the skin loads `font-glyphs.png` instead); only if it can be done without disturbing the other regions, otherwise leave it and note it.
  - Rename the debug panel slider label "gamma cap" to "γ cap".
- acceptance:
  - [x] Game UI still renders (screenshot), `./gradlew core:test` passes
- result: Removed the `font` region entry from uiskin.atlas (png pixels left, unused) and renamed the slider label to "γ cap".

### ST-071 LightHistorySystem: record every body each physics step
- status: done (2026-11-03, 8abe41d)
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
  - [x] Test (real `World` with `PhysicsSystem`): after 45 steps a body moving at (10, 0) has 45 samples, the newest at `time = 1 ± 1e-5`, with x advanced 10 ± 1e-3
  - [x] Test: an entity added at step 0 is `eternal`; one added at step 20 isn't
  - [x] Test: after removal the history stays, `removedAt` is set, and it is pruned after the capacity window
  - [x] `./gradlew core:test` passes
- result: `LightHistorySystem` is the last step listener and keeps id-keyed, pooled histories with eternal/removedAt; tests derive from PHYSICS_TICK_RATE (the 45-step criterion was written for the old 45 Hz tick).

### ST-072 PilotFrame: apparent position and hull-angle transforms (pure)
- status: done (2026-11-03, 97154af)
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
  - [x] Test: `apparentOffset((0, 10), β = (0.9, 0))` → (20.65, 10) ± 0.01, i.e. 25.84° ± 0.05° from +x and length 22.94 ± 0.01
  - [x] Test: `apparentOffset` at β = 0 is the identity; a point straight ahead `(10, 0)` at β = 0.9 maps to (43.59, 0) (= D·10 with D = 4.359); for 20 random `r` and β with |β| ≤ 0.95, `|r'| = γ(1 + β·r̂)·|r|` within 1e-3 relative
  - [x] Test: `worldHullToPilot(45°, (0.9, 0)) = 23.55° ± 0.05°`; `pilotHullToWorld(45°, (0.9, 0)) = 66.45° ± 0.05°`; both functions round-trip within 1e-5 rad for 20 random angles and β; a hull parallel or perpendicular to β is unchanged
  - [x] `./gradlew core:test` passes
- result: Added pure PilotFrame.apparentOffset and world/pilot hull-angle transforms, all covered by tests.

### ST-073 PhotonAim: photon intercept and muzzle-direction inverse (pure, from ST-062)
- status: done (2026-11-03, 04062e1)
- size: S
- agent: junior-dev
- depends: ST-035
- files: create `physics/PhotonAim.kt`; create `core/src/test/kotlin/com/github/pepek42/asteroids/physics/PhotonAimTest.kt`
- goal: Factor the fire-control math out of ST-062, so that both the lead marker (ST-078) and auto-aim (ST-062, parked) use one tested implementation.
- spec (no allocation):
  - `fun photonInterceptDir(rx: Float, ry: Float, vtx: Float, vty: Float, c: Float, out: Vector2): Float`. `r` is target minus shooter (now) and `v_t` the target's arena-frame velocity. Solve `|r + v_t·t| = c·t`: `a = v_t² − c²` (< 0), `b = 2·r·v_t`, `k = r²`, `t = (−b − √(b² − 4ak)) / (2a)`. Set `out = (r + v_t·t)/(c·t)` (a unit vector) and return `t`. Return `NaN` (and leave `out` alone) if `t` isn't finite or `t ≤ 0`. The shooter's velocity doesn't enter, because the photon moves at c in the arena frame anyway.
  - `fun muzzleDirForWorldDir(betaX: Float, betaY: Float, nWx: Float, nWy: Float, out: Vector2): Vector2` = `aberratePhoton(−βx, −βy, nWx, nWy, out)`. This is the inverse of the ST-039 muzzle aberration: the shooter-frame direction to fire so that the photon flies along `n̂_W`.
- acceptance:
  - [x] Test: a static target gives `out = r̂` and `t = |r|/c`
  - [x] Test: a target at (30, 0) moving (0, 20) with c = 60: a photon from the origin along `out` for time `t` ends within 1e-3 u of `r + v_t·t`
  - [x] Test: `aberratePhoton(β, muzzleDirForWorldDir(β, n̂)) ≈ n̂` within 1e-5 for β = (0.9, 0), n̂ = (0, 1), and for 10 random cases with |β| ≤ 0.95
  - [x] Test: `r = 0` returns NaN without crashing
  - [x] `./gradlew core:test` passes
- result: Added pure PhotonAim.photonInterceptDir and muzzleDirForWorldDir (inverse aberration), covered by tests.

### ST-044 Length contraction of moving sprites
- status: done (2026-11-03, fa4ac14)
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
  - [x] Tests: `contractOffset` with k = 0 is the identity. A point along v̂ with k = 1 − 1/2.294 is scaled to 0.436. A point perpendicular to v̂ is unchanged. At γ = 50 the computed k gives a scale of exactly 0.25
  - [x] `./gradlew core:test` passes
- playtest: The player ship visibly flattens along its direction of travel near c. Slow asteroids look unchanged.
- result: RenderSystem contracts sprite quads along v̂ via pure contractOffset/contractionK (min scale 25 %), with physics-lies rows 31-32.

### ST-074 Pilot view: sprites drawn at retarded, boosted positions (default on)
- status: done (2026-11-03, 7a0badd)
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
  - [x] Pure test: extract `fun pilotCorner(px, py, ox, oy, betaX, betaY, aberration: Boolean, out: Vector2)`. A corner at o + (0, 10) with β = (0.9, 0) → o + (20.65, 10); with `aberration = false` → o + (0, 10)
  - [x] Test (real engine, headless: drive `RenderSystem`'s position logic through a helper that doesn't need a GL batch): a static asteroid 30 u away appears at its true position when the player is at rest. With the player at β = 0.8 it appears shifted toward +x by the E9 formula within 0.05 u
  - [x] Test: an asteroid spawned 1 step ago at distance 30 (not eternal) is not drawn
  - [~] Screenshot via the harness with `--seed=42 --thrust --frames=150`, viewed by the agent: asteroids bunch up toward the direction of motion (partial: level-1 asteroids off screen; logged apparent positions confirmed the forward shift)
  - [x] `./gradlew core:test` passes
- playtest: Accelerate: the asteroid field visibly crowds forward and stretches ahead; things beside you appear ahead. V switches between pilot view and the true (world) view. Hitting things should still feel fair; note anything that feels like a desync.
- result: `render/PilotView.kt` projects every non-player sprite through light delay + aberration (`PilotViewProjector`), RenderSystem/CameraSystem use it when `pilotView` is on, V toggles.

### ST-111 Pilot view: apparent edge pointers + harness near-asteroid (ST-074 follow-ups)
- status: done (2026-11-03, 9ff8ee3)
- size: S
- agent: kotlin-dev
- depends: ST-074
- files: `system/HudSystem.kt` (edge/target pointers), `render/PilotView.kt`, screenshot harness launch options (`LaunchOptions`, see `docs/tech/dev-workflow.md`); tests
- refs: ST-074 result (`docs/backlog-done.md`), `docs/tech/dev-workflow.md`
- goal: Two loose ends from ST-074. (1) Edge pointers still point at true arena positions while the sprites are drawn at apparent ones, so a pointer can disagree with what the pilot sees. In pilot view, aim them at the apparent position (`PilotViewProjector` centre: retarded + boosted; hide while its light hasn't arrived). This follows the pilot-frame-first rule (owner, 2026-11-02). The minimap keeps true positions. (2) Level 1 spawns all asteroids far away, so the pilot-view effect can't be verified by screenshot. Add a harness option `--near-asteroids=<n>` that places n asteroids in a ring 8–15 u around the player (seeded RNG).
- spec:
  - Add a projector method that gives the apparent centre of an entity (no quad), allocation-free, false if not arrived. HudSystem uses it when `settings.pilotView` is on, else the old true position.
  - `--near-asteroids` parsed in `LaunchOptions`, applied at level spawn, documented in `docs/tech/dev-workflow.md`.
- acceptance:
  - [x] Test: in pilot view with β = 0.8 the pointer direction to an off-screen asteroid matches its apparent centre, not its true position; in world view it is unchanged
  - [x] Test: `--near-asteroids=4` parses and spawns 4 asteroids within 15 u of the player
  - [x] Screenshot `--seed=42 --thrust --frames=150 --near-asteroids=6`, viewed by the agent: asteroids bunch toward the direction of motion
  - [x] `./gradlew core:test` passes
- playtest: At speed, the edge arrows point where the asteroids appear, not where they "really" are.
- result: `PilotViewProjector.apparentCentre` drives HudSystem edge pointers in pilot view; `--near-asteroids=<n>` spawns a seeded 8–15 u ring around the player.

### ST-076 Pilot-frame aiming: the cursor points in the pilot's frame
- status: done (2026-11-03, 96b5b7b)
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
  - [x] Test: `MouseHullStrategy` with `pilotView = true`, `betaShip = (0.9, 0)` and the cursor at 45° from the ship → `targetAngleRad = 66.45° ± 0.05°`; with `pilotView = false` → 45°
  - [x] Test: a ship at β = 0.9 along +x with body angle 66.45° and no `aimAngleRad` fires along the shooter-frame 45°, i.e. a W direction of `aberratePhoton(β, 45°)` ± 0.1°
  - [x] `./gradlew core:test` passes
- playtest: In pilot view at speed, the ship's nose and the shots go where the cursor is.
- result: MouseHullStrategy uses `pilotHullToWorld` in pilot view, the hull fallback fires at `worldHullToPilot(body.angle, β)`, and the muzzle offset now follows the W-frame flight direction n̂ (decision: keeps the shot on its own flight line).

### ST-112 Pilot view: one gate for hull transform and aim (ST-076 follow-ups)
- status: done (2026-11-03, 76ff775)
- size: S
- agent: kotlin-dev
- depends: ST-076
- files: `render/PilotView.kt`, `system/RenderSystem.kt`, `system/PlayerInputSystem.kt`, `physics/RelativitySettings.kt`; tests
- refs: ST-076 result (`docs/backlog-done.md`)
- goal: The drawn hull and the cursor mapping must use the same switch, otherwise the nose and the cursor disagree. Today `RenderSystem`/`PilotView` apply `worldHullToPilot` whenever `pilotView` is on, but the input side gates on `pilotView && relativityEnabled`, and neither looks at `pilotAberration`.
- spec:
  - Add one helper on `RelativitySettings`, e.g. `val pilotHullTransform get() = pilotView && relativityEnabled && pilotAberration`. Use it in the renderer (player hull rotation) and in `PlayerInputSystem` (`input.pilotView`). Sprite projection (delay/aberration of other entities) stays gated as in ST-074.
- acceptance:
  - [x] Test: with relativity off, or with `pilotAberration` off, the player hull is drawn at `body.angle` and `MouseHullStrategy` returns the raw cursor angle
  - [x] Test: with all three on, both use the transform (β = (0.9, 0), 45° → 66.45°)
  - [x] `./gradlew core:test` passes
- playtest: Toggle relativity / aberration in the debug panel at speed: the nose stays on the cursor in every combination.

## M4 Relativistic view

### ST-050 Plan M4 (relativistic view) from research
- status: done (2026-11-02, replaced: planned directly from the 2026-11-02 interview as ST-070–ST-081)
- size: S
- agent: planner
- depends: ST-037
- files: `docs/backlog.md`
- goal: Turn research §4 Tier 2 (E8–E13) and §7 items 16–20 into M4 cards, along with owner questions Q5, Q8, Q13 and Q14.
- outcome: Q5 answered by the owner: pilot view is the default (b). Q8 answered: the minimap shows true positions (a). Q14 is planned around the recommendation (b), a 25 % floor plus outlines (ST-080). Q13 (enemies obey light delay) moves to M6 with the enemies; `LightHistorySystem` (ST-071) already supports it. E11 (apparent animation rate) waits until there are animated sprites.

## M5 Gravity & black holes

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

## M2a Main menu: controls and settings

- note (owner 2026-11-03, top priority): "Main screen should show all control shortcuts. Before starting the game, I want to have options to choose whatever can be changed. For example, choosing aim mode [...]. Start screen can have play and quit as now, but add all controls and settings that could be changed." Design: `docs/design/main-menu.md`. Two single sources of truth: the bindings table (`input/InputBindings.kt`, ST-114) feeds both `GameEventManager` and the controls list; the settings table (`settings/MenuSettings.kt`, ST-115) feeds the menu widgets, persistence and "Reset defaults". Settings are the existing DI singletons (`RelativitySettings`, `ControlSettings`, `DebugSettings`), so nothing new is plumbed into `PlayScreen`. Non-ASCII characters in `.properties` files are written as `\uXXXX` escapes, like the existing entries.

### ST-119 Owner decision: key rebinding on the menu
- status: done (2026-11-03, decision)
- size: S
- agent: kotlin-dev
- depends: ST-116
- files: `input/InputBindings.kt`, `settings/MenuSettings.kt`, `screen/MainMenuScreen.kt`
- refs: `main-menu.md` "Out of scope"
- goal: The menu lists the controls (ST-116). Should the player also be able to change them?
- question: (a) no, the controls list is read-only for now (recommended: the scheme set is still changing with ST-061/ST-062, and rebinding is easy to add later because everything reads one table); (b) rebind keyboard keys only (V, R-Ctrl, later WASD): click a row, press a key, stored in prefs as `bind_<action>`, with conflict detection; (c) full rebinding including mouse buttons.
- acceptance (if b):
  - [ ] `GameEventManager` reads a mutable bindings table loaded from prefs; a test rebinds TOGGLE_PILOT_VIEW to P and P toggles while V doesn't; binding a key already in use swaps the two
  - [ ] `./gradlew core:test` passes

- result: Owner chose (a): read-only controls list; rebinding can be added later through the bindings table.

### ST-113 i18n key parity test
- status: done (2026-11-03, 2b847d7)
- size: S
- agent: junior-dev
- depends: —
- files: create `core/src/test/kotlin/com/github/pepek42/asteroids/I18nKeysTest.kt`
- refs: `main-menu.md` "i18n"
- goal: The menu work adds many UI strings. A test that both message files have the same keys keeps them in sync, and gives later cards a helper to check their own keys.
- spec:
  - Top-level test helper in the same file: `fun i18nKeys(file: String): Set<String>` loads `i18n/$file` from the test classpath (`assets/` is on it) with `java.util.Properties` and returns `stringPropertyNames()`.
  - Test 1: `i18nKeys("messages.properties") == i18nKeys("messages_pl.properties")`. On failure the message lists the keys missing from each file.
  - Test 2: no value in either file is blank.
- acceptance:
  - [x] Both tests exist and pass on the current files
  - [x] `./gradlew core:test` passes
- result: I18nKeysTest checks key parity and non-blank values; exposes i18nKeys() helper for later cards.

### ST-114 Input bindings table: one source for GameEventManager and the menu
- status: done (2026-11-03, 233fda6)
- size: S
- agent: kotlin-dev
- depends: ST-113
- files: create `input/InputBindings.kt`; edit `event/GameEventManager.kt`, `input/ControlScheme.kt`, `assets/i18n/messages.properties`, `assets/i18n/messages_pl.properties`; `GameEventManagerTest.kt`, new `InputBindingsTest.kt`
- refs: `main-menu.md` "Single sources of truth", "Bindings (today)", "Aim modes"
- goal: Today the keys live in `when` branches inside `GameEventManager`, so a controls screen would have to copy them and could drift. Move every binding into one table that the input dispatcher reads and the menu (ST-116) lists. Behaviour is unchanged.
- spec:
  - In `InputBindings.kt`:
    ```kotlin
    enum class InputAction { AIM, FIRE, THRUST, ZOOM, TOGGLE_PILOT_VIEW, BLOCK, DEBUG_PANEL }
    sealed interface InputTrigger {
        data class Key(val code: Int) : InputTrigger
        data class MouseButton(val button: Int) : InputTrigger
        data object MouseMove : InputTrigger
        data object Wheel : InputTrigger
    }
    data class Binding(
        val action: InputAction, val trigger: InputTrigger, val labelKey: String,
        val debugOnly: Boolean = false,
        val schemes: Set<ControlScheme> = ControlScheme.entries.toSet(),  // menu filter only
    )
    object InputBindings { val DEFAULT: List<Binding> }
    ```
    `DEFAULT`, in this order: AIM/MouseMove/`action_aim`, FIRE/MouseButton(LEFT)/`action_fire`, THRUST/MouseButton(RIGHT)/`action_thrust`, ZOOM/Wheel/`action_zoom`, TOGGLE_PILOT_VIEW/Key(V)/`action_pilot_view`, BLOCK/Key(CONTROL_RIGHT)/`action_block`, DEBUG_PANEL/Key(F1)/`action_debug_panel`/debugOnly.
  - Pure lookups on a `List<Binding>`: `fun List<Binding>.keyAction(code: Int): InputAction?`, `fun List<Binding>.buttonAction(button: Int): InputAction?`, `fun List<Binding>.forMenu(scheme: ControlScheme, includeDebug: Boolean): List<Binding>` (keeps table order, filters by `scheme in schemes` and `debugOnly`).
  - Pure `fun triggerLabel(t: InputTrigger, i18n: (String) -> String): String`: `Key` → `Input.Keys.toString(code)` (e.g. "R-Ctrl", "V", "F1"); `MouseButton` LEFT/RIGHT/MIDDLE → `i18n("input_lmb" / "input_rmb" / "input_mmb")`; `MouseMove` → `i18n("input_mouse")`; `Wheel` → `i18n("input_wheel")`.
  - `GameEventManager(private val bindings: List<Binding> = InputBindings.DEFAULT)`. `keyDown`: look up the action; `DEBUG_PANEL` dispatches to the debug listeners **before** the `ignoreInput` check (as F1 does today); then `ignoreInput` returns false; `TOGGLE_PILOT_VIEW` → `togglePilotView`, `BLOCK` → `block`; no action → false. `touchDown/Up`: `FIRE` → `fire(true/false)`, `THRUST` → `thrusters(1f/0f)`. No `Keys.`/`Buttons.` literals stay in `GameEventManager`. Mouse move and wheel keep their callbacks (they're listed in the table for the menu).
  - `ControlScheme` gets a constructor property `val implemented: Boolean`: `MOUSE_HULL` true, `TWIN_STICK` and `AUTO_AIM` false (they fall back to mouse-hull). Add `companion fun selectable(): List<ControlScheme> = entries.filter { it.implemented }`. ST-061 and ST-062 flip their flag.
  - i18n (both files): `action_aim=Aim` / `Celowanie`, `action_fire=Fire` / `Strzał`, `action_thrust=Thrust` / `Ciąg silnika`, `action_zoom=Zoom` / `Przybliżenie`, `action_pilot_view=Toggle pilot view` / `Przełącz widok pilota`, `action_block=Shield (not yet)` / `Tarcza (wkrótce)`, `action_debug_panel=Debug panel` / `Panel debugowania`, `input_lmb=LMB` / `LPM`, `input_rmb=RMB` / `PPM`, `input_mmb=MMB` / `ŚPM`, `input_mouse=Mouse` / `Mysz`, `input_wheel=Mouse wheel` / `Kółko myszy`.
- acceptance:
  - [x] Existing `GameEventManagerTest` cases pass unchanged (F1 works while input is ignored, V toggles pilot view, unbound key returns false)
  - [x] Test: a `GameEventManager` built with a custom table that binds TOGGLE_PILOT_VIEW to `Keys.P` toggles on P and not on V (proves dispatch reads the table)
  - [x] Test: `forMenu(MOUSE_HULL, includeDebug = false)` has 6 entries in table order without F1; `includeDebug = true` adds F1 last; a binding with `schemes = setOf(TWIN_STICK)` is excluded for MOUSE_HULL
  - [x] Test: `triggerLabel(Key(CONTROL_RIGHT)) == "R-Ctrl"`, `triggerLabel(MouseButton(LEFT), i18n)` returns `i18n("input_lmb")`
  - [x] Test (using `i18nKeys` from ST-113): every `labelKey` in `DEFAULT` and every `input_*` key exists in both files
  - [x] Test: `ControlScheme.selectable() == listOf(MOUSE_HULL)`
  - [x] `./gradlew core:test` passes
- result: Bindings live in InputBindings.DEFAULT; GameEventManager reads the table, menu helpers forMenu/triggerLabel added.

### ST-115 Settings table and persistence (libGDX Preferences)
- status: done (2026-11-03, b110314)
- size: M
- agent: kotlin-dev
- depends: ST-114
- files: create `settings/KeyValueStore.kt`, `settings/MenuSettings.kt`; edit `Config.kt`, `Game.kt`, both `assets/i18n/messages*.properties`; new `settings/MenuSettingsTest.kt`
- refs: `main-menu.md` "Settings", "Persistence", "Flow into PlayScreen"
- goal: One list describes every player-facing setting (key, label, range, default, getter/setter onto the existing DI objects). The menu (ST-116), persistence and "Reset defaults" iterate it. Settings survive restarts via libGDX `Preferences`.
- spec:
  - `KeyValueStore` interface: `contains(key)`, `getBoolean/getFloat/getString(key, default)`, `putBoolean/putFloat/putString(key, value)`, `flush()`. `GdxPreferencesStore(prefs: Preferences)` delegates; `MemoryKeyValueStore` (a `HashMap`, `flush` no-op) lives in main code (tests and screenshot runs use it).
  - `sealed class SettingSpec(val key: String, val enabled: () -> Boolean = { true })` with `labelKey = "setting_$key"`, `hintKey = "setting_${key}_hint"`, subclasses:
    - `Toggle(key, default: Boolean, get: () -> Boolean, set: (Boolean) -> Unit, enabled)`
    - `Slider(key, min: Float, max: Float, step: Float, decimals: Int, default: Float, get, set, enabled)`
    - `Choice(key, options: List<String>, optionLabelKeys: List<String>, default: String, get: () -> String, set: (String) -> Unit, enabled)`
  - `class MenuSettings(val specs: List<SettingSpec>)` (a wrapper so DI can bind it) and `fun menuSettings(relativity: RelativitySettings, controls: ControlSettings, debug: DebugSettings): MenuSettings` with, in this order:
    1. `perspective` Choice ↔ `relativity.pilotView`: options `outside` (false) / `pilot` (true), labels `perspective_outside` / `perspective_pilot`, default from `PILOT_VIEW_DEFAULT` (outside). Owner 2026-11-03: "2 game options - one from player perspective and one from outside".
    2. `relativity` Toggle ↔ `relativityEnabled`, default `RELATIVITY_ENABLED`
    3. `speed_of_light` Slider ↔ `c`, `C_MIN`..`C_MAX_BOX2D_SAFE`, step 1, 0 decimals, default `SPEED_OF_LIGHT`, enabled = `{ relativity.relativityEnabled }`
    4. `time_warp` Toggle ↔ `timeWarpEnabled`, default `TIME_WARP_ENABLED`, enabled = `{ relativity.relativityEnabled }`
    5. `target_pointers` Toggle ↔ `debug.targetPointersEnabled`, default `TARGET_POINTERS_ENABLED`
    6. `grid` Toggle ↔ `debug.gridEnabled`, default `GRID_ENABLED`
  - Pure functions: `loadSettings(store, specs)`: for each spec whose key the store contains, `set(store.getX(key, default))` (setters clamp, e.g. c). `saveSettings(store, specs)`: put every `get()`, put `SETTINGS_VERSION_KEY = "settings_version"` = 1, `flush()`. `resetSettings(specs)`: `set(default)` for each.
  - `Config.kt`: `const val PREFERENCES_NAME = "star-trap-settings"`.
  - `Game.create()`: bind `KeyValueStore` as `GdxPreferencesStore(Gdx.app.getPreferences(PREFERENCES_NAME))`, or `MemoryKeyValueStore()` when `options.screenshotPath != null` (harness output must not depend on a developer's saved prefs). Bind `menuSettings(...)`. After the context is built: `loadSettings(...)`, then `options.controls?.let { controls.scheme = it }` so the command line wins over prefs.
  - i18n (both files, labels and hints): `setting_perspective=Perspective` / `Perspektywa`, hint `Outside: objects are drawn where they really are. Pilot: what the pilot actually sees, with delayed light and aberration (realistic, harder). V switches in game.` / `Z zewnątrz: obiekty tam, gdzie naprawdę są. Pilot: to, co naprawdę widzi pilot, z opóźnionym światłem i aberracją (realistycznie, trudniej). V przełącza w grze.`; `perspective_outside=Outside` / `Z zewnątrz`; `perspective_pilot=Pilot (realistic)` / `Pilot (realistyczna)`; `setting_relativity=Relativity` / `Relatywistyka`, hint `Off: classic Newtonian physics.` / `Wyłączona: klasyczna fizyka Newtona.`; `setting_speed_of_light=Speed of light` / `Prędkość światła`, hint `Lower c makes relativistic effects show up sooner.` / `Mniejsze c: efekty relatywistyczne pojawiają się wcześniej.`; `setting_time_warp=Time warp` / `Przyspieszenie czasu`, hint `The arena runs on the pilot's clock and speeds up at high speed.` / `Arena biegnie według zegara pilota i przyspiesza przy dużej prędkości.`; `setting_target_pointers=Target pointers` / `Wskaźniki celów`, hint `Arrows at the screen edge point at off-screen targets.` / `Strzałki na krawędzi ekranu wskazują cele poza ekranem.`; `setting_grid=Reference grid` / `Siatka odniesienia`, hint `A background grid that makes your motion visible.` / `Siatka w tle, dzięki której widać ruch.`
- acceptance:
  - [x] Test: change every setting, `saveSettings` into a `MemoryKeyValueStore`, reset the objects, `loadSettings`: all values come back; `settings_version` is 1
  - [x] Test: a stored `speed_of_light` of 1000 loads as `C_MAX_BOX2D_SAFE`; an empty store changes nothing
  - [x] Test: `resetSettings` restores every default (perspective outside, c = `SPEED_OF_LIGHT`, ...)
  - [x] Test: `speed_of_light` and `time_warp` report `enabled() == false` when relativity is off
  - [x] Test (ST-113 helper): every `labelKey`, `hintKey` and option label key exists in both files
  - [x] `./gradlew core:test` passes
- playtest: Change c in the F1 panel, quit from the menu, restart: c is still changed (it is a menu setting).
- result: MenuSettings (6 specs) with load/save/reset via KeyValueStore; loaded at startup, saving is wired by ST-116.

### ST-116 Main menu rework: settings panel and controls list
- status: done (2026-11-03, 23e46cb)
- size: M
- agent: kotlin-dev
- depends: ST-115
- files: `screen/MainMenuScreen.kt`; create `ui/MainMenuLayout.kt` (pure helpers); both `assets/i18n/messages*.properties`; new `ui/MainMenuLayoutTest.kt`
- refs: `main-menu.md` "Layout", "Persistence"; `ui/DebugPanel.kt` `checkBox`/`slider`/`SelectBox` code as the widget pattern
- goal: The main screen keeps Play and Quit, and adds a settings panel built from `MenuSettings` and a controls list built from `InputBindings`, so the player can see every shortcut and choose options before a run.
- spec:
  - Pure, in `MainMenuLayout.kt`: `fun controlsRows(bindings: List<Binding>, scheme: ControlScheme, includeDebug: Boolean, i18n: (String) -> String): List<Pair<String, String>>` = `bindings.forMenu(scheme, includeDebug).map { triggerLabel(it.trigger, i18n) to i18n(it.labelKey) }`. `fun formatSettingValue(v: Float, decimals: Int): String`.
  - Screen layout: a root `Table` with `setFillParent(true)`; a title label (`TITLE`); then three columns: (1) the existing Play (`menu_single_player`) and Exit (`menu_exit`, still hidden on WebGL) buttons with their current behaviour; (2) `menu_settings` heading, one row per spec (label with a `TextTooltip` of `i18n[hintKey]`, widget: `Toggle` → `CheckBox`, `Slider` → `Slider` + value label, `Choice` → `SelectBox<String>` showing `i18n[optionLabelKeys[i]]` and mapping the index back to `options[i]`), then a `menu_reset_defaults` button; (3) `menu_controls` heading and a two-column table from `controlsRows(InputBindings.DEFAULT, controls.scheme, includeDebug = false, ...)`, plus a `menu_controls_debug` subheading and the debug rows when `DebugSettings.panelAllowed`. Wrap columns 2 and 3 in a `ScrollPane` so a short window still works.
  - Behaviour: on any widget change, `spec.set(...)`, `saveSettings(store, specs)`, refresh every row's disabled state from `spec.enabled()`. Reset: `resetSettings`, save, re-sync widgets. Play: `saveSettings` first, then the existing `gameState.resetGame(); game.setScreen<PlayScreen>()`. `show()` re-syncs every widget from `spec.get()` (V and the F1 panel change the same objects in game). Guard against the sync firing change listeners that save half-synced state (set a `syncing` flag).
  - i18n (both files): `menu_settings=Settings` / `Ustawienia`, `menu_controls=Controls` / `Sterowanie`, `menu_controls_debug=Debug` / `Debugowanie`, `menu_reset_defaults=Reset defaults` / `Przywróć domyślne`.
  - ST-084 later adds a "run ended" label above the buttons; leave room in column 1.
- acceptance:
  - [x] Test: `controlsRows(DEFAULT, MOUSE_HULL, false, i18n)` has 6 rows in table order, with ("R-Ctrl", i18n("action_block")) and (i18n("input_lmb"), i18n("action_fire")); with `includeDebug = true` the last row is ("F1", i18n("action_debug_panel"))
  - [x] Test: `formatSettingValue(20f, 0) == "20"`, `formatSettingValue(0.25f, 2) == "0.25"`
  - [x] Test (ST-113 helper): the four `menu_*` keys exist in both files
  - [x] If a display is available, `./gradlew lwjgl3:run --args="--screenshot=menu.png --scene=menu"` renders the menu without exceptions (state the outcome in the result)
  - [x] `./gradlew core:test` passes
- playtest: Start the game: every shortcut is listed; hover a setting for its hint; untick relativity and the c slider and time warp grey out; toggle pilot view, press Play, it's on; press V in game, finish the level, back at the menu the box matches.
- result: Menu has settings panel (saves on change, syncs on show) and controls list; --scene=menu screenshot rendered cleanly.

### ST-120 Menu widget polish (ST-116 review minors)
- status: done (2026-11-03, d9de323)
- size: S
- agent: junior-dev
- depends: —
- files: `screen/MainMenuScreen.kt`, `ui/DebugPanel.kt`, create `ui/UiColors.kt`
- refs: ST-116 review
- goal: Tidy the two non-trivial review minors from ST-116.
- spec:
  - Move `CHECKED_COLOR` / `UNCHECKED_COLOR` (duplicated in `DebugPanel` and `MainMenuScreen`) into one `internal` pair in `ui/UiColors.kt`; both files use it.
  - Slider value label width: instead of `VALUE_LABEL_CHARS = 4` × space advance, size it from the wider of `formatSettingValue(spec.min, decimals)` and `formatSettingValue(spec.max, decimals)` measured with `GlyphLayout` on the label font.
  - Drop the duplicate alpha set in the row enabled-sync (label alpha set both directly and via `setDisabled`); keep one path.
- acceptance:
  - [x] No colour constant is defined twice
  - [x] Value label width derives from the slider range, no char-count constant
  - [x] `./gradlew core:test lwjgl3:compileKotlin` passes

### ST-117 Menu: language and fullscreen settings
- status: done (2026-11-03, 83e5a1f)
- size: S
- agent: kotlin-dev
- depends: ST-116
- files: `Game.kt`, `settings/MenuSettings.kt`, create `settings/DisplaySettings.kt`, both `assets/i18n/messages*.properties`; `MenuSettingsTest.kt`
- refs: `main-menu.md` "Settings"
- goal: The two remaining things a player expects to change before a run: the UI language (both bundles already exist) and fullscreen.
- spec:
  - Language: `Choice` spec `language`, options `""`, `"en"`, `"pl"`, labels `language_system`, `language_en`, `language_pl`, default `""`, backed by a `var language: String` on `DisplaySettings`. Pure `fun localeFor(code: String): Locale` = default locale when blank, else `Locale.forLanguageTag(code)`. In `Game.create()`, create the `KeyValueStore` **before** loading assets, read `language` from it, and load the bundle with `I18NBundleLoader.I18NBundleParameter(localeFor(code))`. The new language applies on the next start (the hint says so); no live UI rebuild.
  - Fullscreen: `class DisplaySettings(var language: String = "", var fullscreen: Boolean = false)` bound in DI. `Toggle` spec `fullscreen` ↔ `display.fullscreen`, default false; `menuSettings` takes `applyFullscreen: (Boolean) -> Unit = {}` and the setter calls it. `Game` passes `{ if (it) Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode) else Gdx.graphics.setWindowedMode(BASE_WIDTH, BASE_HEIGHT) }` and applies the loaded value once after `loadSettings`. Only include the fullscreen spec when `Gdx.app.type == Desktop` (pass `includeFullscreen: Boolean` into `menuSettings`; tests pass true with a recording lambda).
  - i18n (both files): `setting_language=Language` / `Język`, hint `Applies after restart.` / `Zmiana po ponownym uruchomieniu.`; `language_system=System` / `Systemowy`; `language_en=English` (same in both); `language_pl=Polski` (same in both); `setting_fullscreen=Fullscreen` / `Pełny ekran`, hint `Switch between window and fullscreen.` / `Przełącza okno i pełny ekran.`
- acceptance:
  - [x] Test: `localeFor("pl").language == "pl"`, `localeFor("")` is `Locale.getDefault()`
  - [x] Test: language and fullscreen round-trip through `saveSettings`/`loadSettings`; setting fullscreen calls the lambda with the new value; `includeFullscreen = false` leaves the spec out
  - [x] Test (ST-113 helper): the new keys exist in both files
  - [x] `./gradlew core:test` passes
- playtest: Pick Polski, restart: the menu and HUD are in Polish. Tick fullscreen: the window goes fullscreen at once, and stays so after a restart.
- result: DisplaySettings with language (bundle locale read from the store before asset load) and desktop-only fullscreen; loadSettings applies saved fullscreen through the setter.

### ST-118 Docs: main menu, bindings table and settings persistence
- status: done (2026-11-03, 2a3f768)
- size: S
- agent: junior-dev
- depends: ST-116
- files: `docs/design/game-design.md` §4, `docs/tech/architecture.md`, `CLAUDE.md` ("Input & events" paragraph), `docs/design/main-menu.md`
- refs: `main-menu.md`
- goal: Keep the docs true after ST-114 … ST-116.
- spec:
  - game-design.md §4: add the V (pilot view) and F1 (debug panel) rows to the table, and a line: "The source of truth is `input/InputBindings.kt`; the main menu lists it."
  - architecture.md and CLAUDE.md "Input & events": `GameEventManager` dispatches through the `InputBindings` table (add new keys there, never as literals); player settings are `settings/MenuSettings.kt` specs persisted in libGDX `Preferences` (`PREFERENCES_NAME`), loaded in `Game.create()`, skipped for `--screenshot` runs, command-line flags win.
  - main-menu.md: mark ST-113 … ST-116 as done in the "Cards" line; fix anything the implementation changed (read the code, don't guess).
- acceptance:
  - [x] The four files say the above, and no doc still claims the keys live in `GameEventManager`'s `when`
  - [x] `./gradlew core:test` passes

## Parked (low priority)

### ST-062 Auto-aim + auto-shoot control scheme (the player manages momentum)
- status: done (2026-11-03, 5131420)
- size: M
- agent: kotlin-dev
- depends: ST-058, ST-073, ST-114
- files: create `input/AutoAim.kt`, `input/AutoAimStrategy.kt`; edit `system/PlayerInputSystem.kt`, `input/ControlScheme.kt`, `input/InputBindings.kt`, `screen/PlayScreen.kt`; tests
- goal: **Reworked 2026-11-02** (owner: parked mode (a), Brotato / vampire-survivors style). Movement is mouse-hull as in scheme (a): the mouse steers the hull and RMB thrusts. The guns pick a target in a forward cone and fire on their own. It uses the honest fire control of the lead markers (ST-078: retarded state, extrapolated), so auto-aim never peeks at true positions. The photon math moved to `physics/PhotonAim.kt` (ST-073).
- spec:
  - (Added 2026-11-03, owner parked ST-078) Implement the pure fire-control function `leadDirection(...)` here, in `physics/PhotonAim.kt`, exactly as ST-078's spec defines it (retarded state, extrapolated at constant velocity, never true positions), with ST-078's math tests. The on-screen lead markers stay parked in ST-078.
  - Pure `fun pickTargetIndex(sx, sy, facingRad, coneHalfRad, range, xs: FloatArray, ys: FloatArray, n: Int): Int` in `AutoAim.kt`: the index of the nearest candidate with `dist ≤ range` and `|shortestAngleDiffRad(facing, atan2(dy, dx))| ≤ coneHalfRad`, or −1. No allocation.
  - `AutoAimStrategy(targets, lightHistory, observer, clock, settings)`: the targets are the family `allOf(HealthComponent, BodyComponent, BaseInfoComponent)` minus the player faction. Candidate positions are their retarded positions (`LightHistory.retarded`), filled into reused scratch arrays. Movement as in `MouseHullStrategy`. Fire: `i = pickTargetIndex(…, AUTO_AIM_CONE_HALF_DEG = 30°, AUTO_AIM_RANGE = 40f)`. If `i ≥ 0`, then `leadDirection(…)` (ST-078) gives `n̂'`, `weapon.aimAngleRad = atan2(n̂')` and `doAttack = true`. Otherwise `doAttack = fireHeld` and `aimAngleRad = null`.
  - Register it in `PlayerInputSystem.strategies[AUTO_AIM]`.
  - (Added 2026-11-03, ST-114) Set `ControlScheme.AUTO_AIM.implemented = true` so the main menu offers it. In `InputBindings.DEFAULT`, give AUTO_AIM its own FIRE row with label `action_fire_manual` ("Fire (guns also fire on their own)" / "Strzał (działa strzelają też same)", both files) and restrict the plain FIRE row to the other schemes, so the menu explains the difference.
- acceptance:
  - [x] Tests: `pickTargetIndex` picks the nearest in-cone target, skips a nearer one at 45° off the facing and one beyond range, and returns −1 for none
  - [x] Test (real `World`): a ship at β = 0.8 along +x, facing +y, with an asteroid 20 u away along +y (histories warmed up), fires a photon whose sweep hits it
  - [x] `./gradlew core:test` passes
- playtest: With `--controls=auto`, fly around: the guns hit asteroids ahead even at high speed. Does it feel like the game plays itself?
- result: AUTO_AIM scheme is selectable: `AutoAimStrategy` picks the nearest retarded-position target in a 30° cone within 40 u and fires along `leadDirection` (ST-078 math, now in `PhotonAim.kt`); target highlighting left to parked ST-078.

