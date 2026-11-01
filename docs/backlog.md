# Backlog

Task cards for agent-driven development. `/auto-dev` picks the first `ready` card in milestone order whose dependencies are `done`. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-064**.

Statuses: `ready` · `in-progress` · `done (date, sha)` · `blocked` (has a `blocker:` line) · `needs-user` (has a `question:` line).

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
- status: needs-user
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11, `physics/PhysicsConstants.kt` (only if the answer is not (b))
- goal: Record the owner's choice for the speed of light (research §8 Q1). ST-019 already implements the recommendation.
- question: What should c be? (a) 40 u/s: very slow light, sluggish lasers. (b) **60 u/s at 45 Hz (recommended)**: light delay is visible at screen scale, bullets are fast, and Box2D needs no change. (c) 80 u/s with `PHYSICS_SUBSTEPS = 2`: faster, but Box2D costs twice as much CPU.
- acceptance:
  - [ ] The answer is added to game-design.md §11. If (a): set `SPEED_OF_LIGHT = 40f`. If (c): `SPEED_OF_LIGHT = 80f` and `PHYSICS_SUBSTEPS = 2` (ST-021), and the ST-019 test uses `PHYSICS_UPDATE_INTERVAL / PHYSICS_SUBSTEPS`
  - [ ] `./gradlew core:test` passes

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

### ST-023 Owner decision: boundary field damping
- status: needs-user
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11, `physics/BoundaryField.kt` (only if the answer is (a))
- goal: Record the owner's choice (research §8 Q7). ST-022 implements the recommendation.
- question: Should the boundary field lose energy? (a) Lossless (`FIELD_DAMPING = 0`): pinball-like, everything bounces forever. (b) **Mild damping, `k_d = 1` (recommended)**: about 10–20 % of normal speed is lost per bounce for ships and asteroids. Photon lasers always reflect losslessly but use up a bounce.
- acceptance:
  - [ ] The answer is added to game-design.md §11. If (a): set `FIELD_DAMPING = 0f` and drop the damping test's "strictly lower" assertion in favour of "within 2 %"
  - [ ] `./gradlew core:test` passes

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
- status: needs-user
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
- status: needs-user
- size: S
- agent: junior-dev
- depends: —
- files: `docs/design/game-design.md` §11
- goal: Record the owner's choice (research §8 Q9). ST-047/ST-048 implement the recommendation.
- question: Should a near-c ship hit like a freight train? (a) **Yes: Box2D mass = γ·m₀ (capped at 8) and impact damage from relativistic kinetic energy (recommended)**. (b) No: collisions use rest mass and do no damage.
- acceptance:
  - [ ] The answer is added to game-design.md §11. For (b): set `GAMMA_MASS_ENABLED = false` and mark ST-048 done with "not needed" (or revert it if already done)

### ST-034 Owner decision: default weapon
- status: needs-user
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
- status: done (2026-11-01, PENDINGxx)
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
- status: ready
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
  - [ ] `GAMMA_MAX_MASSIVE == 50f`, and the tests above exist
  - [ ] `./gradlew core:test` passes

### ST-056 RelativitySettings: runtime tunables replace consts at the call sites
- status: ready
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
  - [ ] Test: `RelativitySettings(c = 100f).c == 67f`; `gammaMaxMassive = 1f` clamps to 2; `fieldDamping = 2f` updates `fieldParams.damping` and keeps the other params
  - [ ] Test (real `World`): with `settings.c = 30f`, a ship thrusting for 10 s from rest has |v| < 30 and β (against 30) > 0.9
  - [ ] Test: with `relativityEnabled = false`, an entity that has a `RelativisticComponent` gets the Newtonian force (same velocity after N steps as the existing `MoveSystemTest` Newtonian case), and `u` tracks `uFromV(body velocity)`
  - [ ] Test: with `gammaMaxMassive = 5f`, `u = (100c, 0)` is clamped to γ = 5 ± 0.01 after one step
  - [ ] Test: with `fieldEnabled = false`, a body inside the skin gets no field acceleration, and a body past the wall is still reflected by the backstop
  - [ ] `./gradlew core:test` passes
- playtest: With the defaults, the game plays exactly as after ST-046.

### ST-057 Debug plumbing: --debug-panel flag, F1 event, UI-first input, time scale
- status: ready
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
  - [ ] Test: `--debug-panel` sets `debugPanel = true`, and the existing `LaunchOptionsTest` cases still pass (update the `LaunchOptions(...)` constructor calls if they're positional)
  - [ ] Test: `GameEventManager.keyDown(Keys.F1)` calls a registered `DebugInputListener` once, also after `ignorePlayerInputs()`. `keyDown(Keys.A)` returns false
  - [ ] `./gradlew core:test` passes
- playtest: Main menu buttons and the level-cleared button still work. In play, LMB fires and RMB thrusts as before.

### ST-058 ControlScheme abstraction; current mouse-hull scheme as the first strategy
- status: ready
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
  - [ ] Test: `MouseHullStrategy` with the ship at (10, 10) and aim at (10, 20) sets `targetAngleRad = π/2`, copies thrust and fire, and leaves `aimAngleRad` null
  - [ ] Test: `ControlScheme.fromFlag("TWIN") == TWIN_STICK`; `--controls=auto` parses; `--controls=foo` keeps null
  - [ ] Test: `WeaponProjectileProvider` with `aimAngleRad = π/2` on a ship facing 0 gives a projectile velocity along +y. With null, the existing `WeaponProjectileProviderTest` expectations hold
  - [ ] `./gradlew core:test` passes
- playtest: The controls feel exactly as before.

### ST-041 ObserverState: smoothed player β, γ, τ, t
- status: ready
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
  - [ ] Tests: `smoothToward` reaches 63 % ± 1 % of a step after one time constant, and is stable for dt ≫ timeConstant (no overshoot)
  - [ ] `./gradlew core:test` passes

### ST-059 Debug side panel: relativity readouts, toggles and sliders
- status: ready
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
  - [ ] Test: `formatReadouts` with β = 0.9 along +x and c = 60 contains `γ = 2.29`, `|u| = 123.9` and `ship-frame speed = 88.3` (rapidity atanh 0.9 = 1.4722, times 60)
  - [ ] Test (no GL): `DebugPanel.toggleDebugPanel()` flips `debugSettings.panelVisible`. Skip this if building the `Skin` needs GL, and say so in the result
  - [ ] `./gradlew core:test` passes
  - [ ] Screenshot via the harness with `--debug-panel --seed=42 --thrust --frames=150` shows the panel on the right with non-zero β, and the agent has looked at it
- playtest: F1 shows and hides the panel. Dragging a slider doesn't fire or thrust. Dropping c to 30 makes the ship hit "near c" much sooner. 0.25× time makes bounces and aberration easy to watch. Relativity off lets the ship go past c, up to the Box2D clamp.

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

### ST-039 Photon lasers: velocity aberration at the muzzle and field mirrors
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-024, ST-025, ST-035
- files: create `component/PhotonComponent.kt`; edit `provider/WeaponProjectileProvider.kt`, `system/BoundaryFieldSystem.kt`; tests
- goal: Lasers fly at exactly c in a direction that aberrates with the shooter's velocity (research E3). Today projectiles ignore the ship's velocity, so a ship near c would outrun its own bullets. The field doesn't push photons. Instead they mirror at mid-skin and use up a bounce (research §3 [FAKE] rule). This follows the ST-034 recommendation.
- spec:
  - `PhotonComponent { val dir = Vector2() }` (unit vector, Poolable, convention).
  - `WeaponProjectileProvider`: `n̂' = (cos aim, sin aim)` with `aim = weaponCmp.aimAngleRad ?: body.angle` (ST-058 adds the turret aim, null = hull facing; aim is in W for M3). `β = v_ship / c`, where `v_ship` is `relativisticCmpOptional?.vSet ?: body.linearVelocity` and `c = settings.c` once ST-056 is done. `n̂ = aberratePhoton(β, n̂')`. Body velocity = `SPEED_OF_LIGHT·n̂`, and the body angle (and the TransformComponent `prevRotationDeg`, in degrees) = `atan2(n̂)`. Add `PhotonComponent(dir = n̂)`. The lifetime from ST-024 stays.
  - `BoundaryFieldSystem.beforeStep`: photon entities skip the kinematic kick. For each wall, with `r = 0`, if `qᵢ ≥ FIELD_PHOTON_MIRROR_Q` and `dir·n̂ᵢ < 0`: if `lifetime.bouncesLeft == 0`, add `RemoveComponent`. Otherwise reflect `dir → dir − 2(dir·n̂ᵢ)n̂ᵢ`, decrement `bouncesLeft`, set the velocity to `c·dir` and the angle to `atan2(dir)`, and if `ShimmerImpacts` exists, `emit(id, …, strength = 1f, force = true)`.
- acceptance:
  - [ ] Test: a ship at rest facing +x fires a laser with velocity (60, 0) ± 1e-3
  - [ ] Test: a ship moving at β = 0.9 along +x, facing +y, fires a laser at a W angle of 25.84° ± 0.1° with |v| = 60 ± 1e-3
  - [ ] Test (`MapProvider(mock(), 100f, 100f)`, skin 8): a photon moving +x reflects between x = 95.5 and 96.5, `bouncesLeft` goes 3 → 2, and the speed stays 60. With `bouncesLeft = 0` it gets `RemoveComponent` instead
  - [ ] `./gradlew core:test` passes
- playtest: Lasers fired sideways at speed visibly sweep forward. Lasers bounce off the edge up to 3 times with a ripple, which allows bank shots.

### ST-062 Auto-aim control scheme (steer + guns pick targets in a cone)
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-039, ST-058
- files: create `input/AutoAim.kt`, `input/AutoAimStrategy.kt`; edit `system/PlayerInputSystem.kt`, `screen/PlayScreen.kt`; tests
- goal: Scheme (c), which is also an accessibility option. Movement is as in (a): the mouse steers the hull and RMB thrusts. The guns fire on their own at the nearest target inside a forward cone. Because photons fly at exactly c in the world frame and their direction aberrates at the muzzle (ST-039), the aim has to lead the target and pre-compensate the aberration, or auto-aim misses at speed. That's why this card depends on ST-039.
- spec:
  - Pure functions in `AutoAim.kt` (no allocation):
    - `fun pickTargetIndex(sx, sy, facingRad, coneHalfRad, range, xs: FloatArray, ys: FloatArray, n: Int): Int` returns the index of the nearest candidate with `dist ≤ range` and `|shortestAngleDiffRad(facing, atan2(dy, dx))| ≤ coneHalfRad`, or −1.
    - `fun photonInterceptDir(rx, ry, vtx, vty, c, out: Vector2): Boolean`: `r` is target minus shooter, `v_t` is the target's world velocity. Solve `|r + v_t·t| = c·t`, i.e. `a = v_t² − c²` (< 0), `b = 2 r·v_t`, `k = r²`, `t = (−b − sqrt(b² − 4ak)) / (2a)`, `out = (r + v_t·t)/(c·t)`. Return false if `t` isn't finite or is ≤ 0. The shooter's velocity doesn't enter, because the photon moves at c in W anyway.
    - `fun muzzleDirForWorldDir(betaX, betaY, nWx, nWy, out)` = `aberratePhoton(−β, n̂_W)`, the inverse of the ST-039 muzzle aberration.
  - `AutoAimStrategy(targets: ImmutableArray<Entity>, relativitySettings)`: the targets are the engine family `allOf(HealthComponent, BodyComponent, BaseInfoComponent)`, minus the player faction (filtered when filling the scratch arrays, which are reused and grow as needed). Movement is the same as `MouseHullStrategy`. Fire: `i = pickTargetIndex(…, AUTO_AIM_CONE_HALF_DEG = 30°, AUTO_AIM_RANGE = 40f)`. If `i ≥ 0`: the target velocity comes from `relativisticCmpOptional?.vSet ?: body.linearVelocity`. `photonInterceptDir` → `muzzleDirForWorldDir(β_ship, …)` → `weapon.aimAngleRad = atan2`, `doAttack = true`. Otherwise `doAttack = fireHeld` (LMB still fires along the hull) and `aimAngleRad = null`.
  - Register it in `PlayerInputSystem.strategies[AUTO_AIM]`. `PlayScreen` passes the family.
- acceptance:
  - [ ] Tests: `pickTargetIndex` picks the nearest in-cone target, skips a nearer one at 45° off the facing and one beyond range, and returns −1 for none
  - [ ] Test: `photonInterceptDir` for a static target gives r̂. For a target at (30, 0) moving (0, 20) with c = 60, a photon from the origin along `out` meets the target within 1e-3 u at the returned time
  - [ ] Test: `aberratePhoton(β, muzzleDirForWorldDir(β, n̂))` ≈ n̂ within 1e-5 for β = (0.9, 0) and n̂ = (0, 1)
  - [ ] Test (real `World`, ST-039 provider): a ship at β = 0.8 along +x, facing +y, with a static asteroid 20 u away along +y fires a photon whose sweep hits it
  - [ ] `./gradlew core:test` passes
- playtest: With `--controls=auto`, fly around: the guns hit asteroids ahead even at high speed. Check whether it feels like the game plays itself, which is the cost of this option.

### ST-063 Owner decision: default control scheme
- status: needs-user
- size: S
- agent: junior-dev
- depends: ST-058, ST-061, ST-062
- files: `docs/design/game-design.md` §4 and §11; `input/ControlSettings.kt` (default)
- goal: Record which scheme is the default once the owner has tried all three through `--controls=` or the debug panel selector. The others stay selectable.
- question: Which control scheme should be the default? (a) Mouse aims and rotates the hull, RMB thrusts forward (today). (b) **Twin-stick (recommended)**: WASD (later the left stick) sets the thrust direction in the world frame, the hull turns toward it, and the mouse (later the right stick) aims an independent turret. Separating aim from thrust lets the player manage momentum near c, which is the core skill, while still shooting. The relativistic aberration of your own shots is also clearly visible, because you aim sideways while moving fast. (c) Auto-aim + steering only. Keep it as an accessibility option rather than the default.
- acceptance:
  - [ ] The answer is in game-design.md §11 (decisions) and §4. `ControlSettings` defaults to the chosen scheme. For (b), ask the planner for a gamepad card (gdx-controllers, sticks → `moveAxis`/aim)

### ST-040 Proper time drives ship clocks
- status: ready
- size: M
- agent: kotlin-dev
- depends: ST-024, ST-037
- files: `component/RelativisticComponent.kt`, `physics/PhysicsConstants.kt`, `system/WeaponSystem.kt`, `system/LifetimeSystem.kt`, `system/PhysicsSystem.kt`, `system/RelativisticDynamics.kt`; tests
- goal: Time dilation becomes gameplay (research E2): the ship's own clock runs at 1/γ. This follows the ST-031 recommendation: guns exact, turning softened.
- note: There's no gameplay γ cap (ST-032), so at large γ the ship clock nearly freezes the guns: at the γ = 50 safety bound the fire rate is 1/50 (option a of ST-031). That's accepted for now. Keep both exponents as `const`s so the ST-031 softening variant (exponent 0.5 or a `CLOCK_RATE_MIN` floor) is a one-line change.
- spec:
  - Constants: `COOLDOWN_DILATION_EXPONENT = 1f`, `ROTATION_DILATION_EXPONENT = 0.5f`.
  - In `RelativisticComponent.kt`: `fun Entity.clockRate(exponent: Float): Float = relativisticCmpOptional?.let { it.gUsed.pow(-exponent) } ?: 1f`.
  - `WeaponSystem`: `cooldown -= deltaTime · entity.clockRate(COOLDOWN_DILATION_EXPONENT)`.
  - `LifetimeSystem`: for the PROPER clock, `remaining -= dt · entity.clockRate(1f)`. WORLD is unchanged.
  - `PhysicsSystem.applyRotation`: `f = entity.clockRate(ROTATION_DILATION_EXPONENT)`. Time-scale the PD controller by passing `kp·f²`, `kd·f`, `maxAngularAcceleration·f²`, `maxAngularVelocity·f` (from `ShipHandlingComponent` or the `ShipRotationController` defaults). This is exactly the controller on a clock slowed by `f`.
  - Asteroid spin `ω₀/γ`: in `RelativisticDynamics.beforeStep`, for entities without a `MoveComponent` or with a null `targetAngleRad`, compute `g` and do `body.angularVelocity *= gUsedPrevious / g` before overwriting `gUsed`. This rescales exactly and survives collisions.
- acceptance:
  - [ ] Test: a weapon on an entity with `gUsed = 2` fires half as often over 3 s of world time as at `gUsed = 1` (±1 shot)
  - [ ] Test: a PROPER lifetime of 1.2 at `gUsed = 3.2` lasts 3.84 s ± 1 frame of world time
  - [ ] Test: a 90° turn at `gUsed = 4` (f = 0.5) takes 2× ± 15 % as long as at γ = 1, with overshoot still < 5°
  - [ ] Test: an asteroid spinning at 2 rad/s whose `u` is set to γ = 2 spins at 1 ± 0.02 rad/s after one step
  - [ ] `./gradlew core:test` passes
- playtest: At high speed the fire rate visibly drops and turning feels heavier but still controllable.

### ST-042 HUD shows β, γ, τ and t; speed zoom
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-041
- files: `ui/Hud.kt`, `system/HudSystem.kt`, `system/CameraSystem.kt`, `assets/i18n/messages.properties`, `assets/i18n/messages_pl.properties`; tests
- goal: The "I'm near c and I can feel it" readout (research E6), plus a camera that zooms out with speed, because at c = 60 half the screen is crossed in 0.4 s.
- spec:
  - HUD: a `ProgressBar(0f, 1f, 0.001f, false, skin, "default-horizontal")` showing `(rapidity / HUD_RAPIDITY_FULL).coerceAtMost(1f)` with `HUD_RAPIDITY_FULL = acosh(10f)` (≈ 2.993, β = 0.995). It keeps moving near c. The γ safety bound is 50 now (ST-055), so normalising by `acosh(GAMMA_MAX_MASSIVE)` would leave the bar a third full at 0.9c. Labels: `hud_beta=β = {0,number,0.000} c`, `hud_gamma=γ = {0,number,0.00}`, `hud_time=Ship time {0,number,0.0} s · World time {1,number,0.0} s · τ/t {2,number,0.00}`. Polish: `Czas statku`, `Czas świata`. Keep the existing level, position and velocity labels. `HudSystem` takes the `ObserverState` and feeds `betaRaw.len()`, `gammaRaw`, `tau`, `worldTime`, and `tau/worldTime` (1 when worldTime = 0).
  - `CameraSystem`: keep a `wheelZoom` field (the old `camera.zoom` logic, clamped 0.5–2). Add top-level `fun speedZoom(wheelZoom: Float, beta: Float, k: Float = CAMERA_ZOOM_PER_BETA2): Float = (wheelZoom·(1 + k·beta²)).coerceIn(0.5f, 2f)` with `CAMERA_ZOOM_PER_BETA2 = 0.6f`. Set `camera.zoom = speedZoom(wheelZoom, observer.betaVisual.len())`. `CameraSystem` takes the `ObserverState`.
- acceptance:
  - [ ] Tests: `speedZoom(1f, 0f) == 1f`; `speedZoom(1f, 0.9f) ≈ 1.486`; `speedZoom(2f, 0.9f) == 2f`
  - [ ] The i18n keys exist in both properties files
  - [ ] `./gradlew core:test` passes
- playtest: The β bar fills fast at first, then slowly near c. τ visibly falls behind t at speed. The view widens smoothly as you speed up.

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
- acceptance:
  - [ ] Tests: `spriteTint(1f)` ≈ (1, 1, 1) ± 0.01; `spriteTint(2f)` has blue ≥ red; `spriteTint(0.5f)` has red > blue
  - [ ] No per-frame allocation (preallocated queue, max 256 sprites; beyond that, skip the additive pass)
  - [ ] `./gradlew core:test` passes
- playtest: Fly at an asteroid fast: it turns blue-white and glows. Fly away: asteroids behind turn red and dim.

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

### ST-049 Bring docs in line after M3
- status: ready
- size: S
- agent: junior-dev
- depends: ST-036, ST-037, ST-038, ST-039, ST-040, ST-041, ST-042, ST-043, ST-044, ST-045, ST-046, ST-047, ST-048, ST-055, ST-056, ST-057, ST-058, ST-059, ST-060, ST-061, ST-062
- files: `docs/tech/architecture.md`, `CLAUDE.md`, `docs/design/game-design.md` (§4 controls, §5 arena size, §6 tier table and §11, only to reflect the answered `needs-user` cards)
- goal: Document the relativistic dynamics layer (step-listener order, `RelativisticComponent`, `RELATIVITY_ENABLED`), `ObserverState`, the projectile sweep and photons, the new constants, the M3 promotions listed in this milestone's note, the runtime `RelativitySettings`/`DebugSettings`/`ControlSettings` singletons, the debug panel (F1, `--debug-panel`), the `ControlScheme` strategies and `--controls=`, and the UI-first input multiplexer order.
- acceptance:
  - [ ] Every statement in the updated sections can be traced to the current code or to an answered owner decision

---

## M4+ Relativistic view, gravity, enemies, modes

### ST-050 Plan M4 (relativistic view) from research
- status: blocked
- blocker: Plan when about half of M3 is done (research §7 items 16–20).
- size: S
- agent: planner
- depends: ST-037
- files: `docs/backlog.md`
- goal: Turn research §4 Tier 2 (E8–E13) and §7 items 16–20 into M4 cards, along with owner questions Q5, Q8, Q13 and Q14.
