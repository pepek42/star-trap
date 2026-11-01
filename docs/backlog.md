# Backlog

Task cards for agent-driven development. `/auto-dev` picks the first `ready` card in milestone order whose dependencies are `done`. The card format and routing rules are in `.claude/agents/planner.md`. **Never renumber cards.** The next free ID is **ST-018**.

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
- status: done (2026-11-01, 8455fa4)
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
- status: ready
- size: S
- agent: kotlin-dev
- depends: —
- files: `screen/PlayScreen.kt`, new helper (e.g. `ecs/LevelCleanup.kt`), a test
- goal: `PlayScreen.hide()` only removes player entities. Asteroids, projectiles and their Box2D bodies leak into the next level (there's a TODO). Add `fun clearLevel(engine: Engine, world: World)`: for every entity with a `BodyComponent`, destroy the body and null `userData` (the same thing `RemoveSystem` does), then `engine.removeAllEntities()`. Call it from `hide()`.
- acceptance:
  - [ ] Test with a real `World` + `PooledEngine`: after spawning 3 bodies/entities and calling `clearLevel`, `world.bodyCount == 0` and `engine.entities.size() == 0`, and no assertion error comes from `BodyComponent.reset()`
  - [ ] `./gradlew core:test` passes

### ST-007 Fix faction collision bit naming and the projectile filter
- status: ready
- size: S
- agent: junior-dev
- depends: —
- files: `faction/FactionEnum.kt`, `provider/PlayerEntityProvider.kt`, `provider/AsteroidProvider.kt`, `provider/WeaponProjectileProvider.kt`, new `core/src/test/kotlin/com/github/pepek42/asteroids/faction/FactionEnumTest.kt`
- goal: Field names are inverted. `entityMaskBit` (the faction's own bit) is assigned to `maskBits`, and `categoryBits` (other factions' bits) to `categoryBits`. `WeaponProjectileProvider` sets `filter.categoryBits` twice and never sets `maskBits`.
- spec: Rename `entityMaskBit` → `ownCategoryBit` and `categoryBits` → `collidesWithMask`. Keep the constructor values unchanged. In **all three** providers, set `filter.categoryBits = faction.ownCategoryBit` and `filter.maskBits = faction.collidesWithMask`. (Swapping category and mask on every fixture leaves Box2D's symmetric collision rule unchanged.) Add `fun FactionEnum.collidesWith(other: FactionEnum): Boolean = (collidesWithMask.toInt() and other.ownCategoryBit.toInt()) != 0 && (other.collidesWithMask.toInt() and ownCategoryBit.toInt()) != 0`.
- acceptance:
  - [ ] Test: PLAYER×PLAYER false, PLAYER×NEUTRAL true, PLAYER×ENEMY true, ENEMY×ENEMY false, NEUTRAL×NEUTRAL true
  - [ ] Existing `WeaponProjectileProviderTest` still passes
  - [ ] `./gradlew core:test` passes
- playtest: The player's lasers hit asteroids and don't hit the player ship.

### ST-008 Small asteroids use their own texture
- status: ready
- size: S
- agent: junior-dev
- depends: —
- files: `environment/asteroid.kt`
- goal: `AsteroidSize.SMALL` uses `"asteroid_big"`. Change it to `"asteroid_small"` (the region exists in `textures.atlas`).
- acceptance:
  - [ ] The change is made, and `./gradlew core:test` passes

### ST-009 CI on JDK 25 and on all branches/PRs
- status: ready
- size: S
- agent: junior-dev
- depends: —
- files: `.github/workflows/workflows.yaml`
- goal: CI installs JDK 17, but the toolchain is 25. Use `actions/checkout@v4` and `actions/setup-java@v4` with `java-version: "25"`, `distribution: temurin`, add `gradle/actions/setup-gradle@v4` before the test step, and trigger on `push` (all branches) and `pull_request`.
- acceptance:
  - [ ] The YAML is valid (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/workflows.yaml`)

### ST-010 Fix asteroid split velocity mutation
- status: ready
- size: S
- agent: junior-dev
- depends: —
- files: `system/DeathSystem.kt`
- goal: In `spawnSmallerAsteroids`, `currentVelocity` is `body.linearVelocity`, a vector Box2D reuses. `rotateDeg` mutates it in place, so the second child's velocity is rotated by both angles. Use copies: `currentVelocity.cpy().rotateDeg(-r1)` and `currentVelocity.cpy().rotateDeg(+r2)`. Do the same for `position` (take `body.position.cpy()` once).
- acceptance:
  - [ ] The fix is made, and `./gradlew core:test` passes

### ST-011 Readable minimap
- status: ready
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
  - [ ] Pure function `minimapBounds(screenW, screenH): Rectangle` with a unit test for 800×600 and 1920×1080
  - [ ] Screenshot via the ST-005 harness shows the framed minimap, and the agent has looked at it
  - [ ] `./gradlew core:test` passes
- playtest: The minimap is readable at a glance. You can tell asteroids from yourself, and the view rectangle tracks zoom.

### ST-012 Readable, camera-relative starfield (Doppler-ready)
- status: ready
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
  - [ ] Unit tests: `blackbodyRgb(kelvin)` is monotone in blue/red ratio, and the same seed gives the same star set
  - [ ] Screenshot via the ST-005 harness with `--thrust` shows parallax-readable stars, and the agent has looked at it
  - [ ] No per-frame allocations in the draw loop
  - [ ] `./gradlew core:test` passes
- playtest: The background reads as space with depth, and the ship's motion is felt through parallax without distracting from asteroids.

### ST-013 Bring docs in line after M1
- status: ready
- size: S
- agent: junior-dev
- depends: ST-003, ST-004, ST-006, ST-007, ST-011, ST-012
- files: `docs/tech/architecture.md`, `CLAUDE.md`
- goal: Update the "Known issues" list (remove the fixed items, reference their card IDs), the system table and the component list, so they match the code after M1.
- acceptance:
  - [ ] Every statement in the updated sections can be traced to the current code

### ST-016 Per-ship rotation tuning via ShipHandlingComponent
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-004
- files: create `component/ShipHandlingComponent.kt`, edit `system/ShipRotationController.kt`, `system/PhysicsSystem.kt`, `provider/PlayerEntityProvider.kt`, tests
- goal: Follow-up from ST-004. Move the rotation gains (kp, kd, max angular acceleration, max angular velocity) from `ShipRotationController` constants into a `ShipHandlingComponent` (follow the component convention), with the current values as defaults, so enemies and ship variants can feel different. `PhysicsSystem` reads the component when present and falls back to the defaults otherwise.
- acceptance:
  - [ ] The player entity gets a `ShipHandlingComponent` with the defaults
  - [ ] Test: an entity with a lower max angular velocity never exceeds it during a 180° turn
  - [ ] `./gradlew core:test` passes
- playtest: The player ship feels the same as after ST-004. Also check the feel with the real `disc_green` sprite size, since the tests used a 2×2 box.

### ST-017 Deterministic screenshots with --seed
- status: ready
- size: S
- agent: kotlin-dev
- depends: ST-005
- files: `debug/ScreenshotHarness.kt` (LaunchOptions), `Game.kt`, providers/systems that use randomness, `LaunchOptionsTest.kt`, `docs/tech/dev-workflow.md`
- goal: Follow-up from ST-005. Add `--seed=<long>` so two screenshot runs with the same args give the same image. Route all gameplay randomness (asteroid spawn, starfield, splits) through one seeded `RandomXS128` registered in the DI context, instead of `MathUtils.random` and `Random()` calls.
- acceptance:
  - [ ] Parsing test for `--seed`
  - [ ] Running the screenshot command twice with `--seed=42 --frames=60` gives identical PNGs (run once to verify)
  - [ ] `./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin` passes

---

## M2 Arena boundary field

### ST-014 Plan M2 (boundary field) from research
- status: ready
- size: S
- agent: planner
- depends: —
- files: `docs/backlog.md`
- goal: Once `docs/research/relativity-effects.md` exists, turn its "Arena boundary force field" section into M2 cards. These are a pure force-law function plus tests, a `BoundaryFieldSystem` placed before `PhysicsSystem`, removal of the `WrapSystem` and `WrapComponent` usage, projectile lifetime replacing out-of-bounds removal, and a field shimmer visual.

---

## M3+ Relativity, gravity, enemies, modes

### ST-015 Plan M3 (relativity MVP) from research
- status: ready
- size: S
- agent: planner
- depends: ST-014
- files: `docs/backlog.md`
- goal: Turn the MVP tier of `docs/research/relativity-effects.md` into cards. Expected scope: the choice of `c` and the physics-rate change, a pure `Relativity.kt` math module with tests, a relativistic momentum integrator on top of Box2D, proper time per entity, a β/γ HUD, and Doppler on the starfield. Questions from the research doc that are really the owner's go into `needs-user` cards, planned around the recommended answer. Later milestones (M4–M7) are planned when M3 is about half done.
