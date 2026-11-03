# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Star Trap: a proof-of-concept physics-based 2D space shooter (Asteroids-like, aiming toward an action roguelike) built with libGDX + Kotlin + KTX, using Ashley ECS and Box2D. Controls: LMB fire, RMB thrusters, mouse wheel zoom. The Kotlin package is `com.github.pepek42.asteroids` (not `startrap`).

Docs index: `docs/README.md`. Owner's raw roadmap: `docs/design/todo.md`. Game design: `docs/design/game-design.md`. Physics research: `docs/research/`. Task cards: `docs/backlog.md` (open) and `docs/backlog-done.md` (archive); query them with `scripts/backlog.py next|show|list` instead of reading them. Detailed architecture and known issues: `docs/tech/architecture.md`. Library gotchas: `docs/tech/ktx-cheatsheet.md`.

Development is agent-driven (see `docs/tech/dev-workflow.md`). Subagents live in `.claude/agents/` (planner, kotlin-dev, junior-dev, verifier, reviewer, physics-researcher). `/auto-dev` runs the backlog loop and `/plan-backlog` adds cards. `scratch_pad/` is gitignored reference material (the gdx template used for the stack migration) and is not part of the build.

## Commands

Gradle 9.8.1, Kotlin DSL, version catalog `gradle/libs.versions.toml`, convention plugin `build-logic/src/main/kotlin/kotlin-jvm.gradle.kts`. JVM toolchain 25, auto-downloaded via foojay.

```bash
./gradlew lwjgl3:run          # run desktop game (working dir = assets/)
./gradlew test                # all unit tests (CI runs: ./gradlew clean test)
./gradlew core:test --tests "com.github.pepek42.asteroids.system.MoveSystemTest"   # single test class
./gradlew core:test --tests "*MoveSystemTest.thrustersShouldWork*"                 # single test method
./gradlew lwjgl3:jar          # runnable fat jar -> lwjgl3/build/lib
./gradlew tool:packTextures  # repack raw_assets/*.png -> assets/textures/textures.atlas/.png
./gradlew teavm:run           # experimental web build at http://localhost:8080
```

- There is no linter. Formatting follows `.editorconfig`: 4-space indent for Kotlin, 2 for Gradle scripts.
- After adding or changing a sprite in `raw_assets/`, run `tool:packTextures` and commit the regenerated atlas. Code looks sprites up by region name in `textures/textures.atlas`.
- Tests use JUnit (Jupiter) + mockito-kotlin. The test classpath includes `assets/` and desktop natives, so tests can touch real Box2D/libGDX classes.

## Modules

- `core`: all game logic.
- `lwjgl3`: desktop launcher (`Lwjgl3Launcher.kt`).
- `teavm`: experimental browser launcher.
- `tool`: texture packer (`packTextures`).

## Architecture

**Bootstrap & DI.** `Game` (a `KtxGame`) loads assets synchronously through `AssetManager`, then registers singletons in a `ktx.inject.Context`: camera, `FitViewport` (48×27 world units), `SpriteBatch`, atlas, skin, i18n bundle, `GameEventManager`, `MapProvider`, `GameState`, and the screens. It also registers one seeded `RandomXS128`; gameplay randomness must use it. `Game` takes a `LaunchOptions`, parsed from the command line by `Lwjgl3Launcher`. Other code fetches singletons with `game.get<T>()`. Screens: `MainMenuScreen` → `PlayScreen` → `LevelClearedScreen` (`game.finishLevel()`).

**ECS (Ashley `PooledEngine`).** `PlayScreen` owns the per-level `PooledEngine` and Box2D `World` and registers all systems in `setupEcs()`. **System registration order is the execution order and matters.** The order is input → move → weapons → contact → boundary field → projectile sweep → physics step → camera → render → debug/HUD/minimap → lifetime → death → remove. `show()` advances the level and spawns the player and asteroids, with the asteroid count growing with level².

**Component convention.** Each component file defines the component class (usually `Pool.Poolable`), a `xxxMapper = mapperFor<...>()`, and `Entity.xxxCmp` / `Entity.xxxCmpOptional` extension properties via `propertyFor`. Follow this pattern for new components. Every entity should have a `BaseInfoComponent` (id, faction, `EntityType`).

**Entity creation.** Providers in `provider/` (`PlayerEntityProvider`, `AsteroidProvider`, `WeaponProjectileProvider`) build entities with the `engine.entity { with<...> {} }` DSL and create the Box2D body with `world.body {}`. A body's `userData` is set to its `Entity`, and `ContactSystem` relies on that cast.

**Physics loop.** `PhysicsSystem` steps the world at a fixed `PHYSICS_UPDATE_INTERVAL = 1/PHYSICS_TICK_RATE` (60 Hz) using an accumulator. `TimeWarpSystem` caps catch-up at `MAX_LAG_STEPS` (derived, 7) steps at warp 1, rising with warp up to `MAX_STEPS_PER_FRAME` (derived, 10). It consumes `GameClock.worldDt` from `TimeWarpSystem` (registered first): the arena runs on the pilot's clock, fast-forwarded by the player's γ (capped at `timeWarpMax`). The world has `autoClearForces = false`, and `clearForces` runs after every step. It also lerps `TransformComponent.interpolatedPosition/RotationDeg` between the previous and current body state. Rendering must use these interpolated values, not the raw body position. `PhysicsSystem` takes `PhysicsStepListener`s (`beforeStep`/`afterStep`) called around every step; `MoveSystem` is one and applies thrust in `beforeStep`, so forces act once per step. Rotation is a PD controller (`ShipRotationController`) that `PhysicsSystem` applies as an angular impulse before every physics substep, driven by `MoveComponent.targetAngleRad`. Massive bodies (player, asteroids) carry a `RelativisticComponent`: step listeners add proper accelerations to its `accel` in `beforeStep`, and `RelativisticDynamics` (the last listener) integrates the proper velocity `u = γv` and writes `v = u/γ` into Box2D; `afterStep` reads contact-changed velocities back into `u`. Engines are a proper acceleration (`ENGINE_PROPER_ACCEL`), so ships approach c but never reach it. `RELATIVITY_ENABLED` switches this off.

**Collision filtering.** `FactionEnum` (PLAYER/NEUTRAL/ENEMY) holds the Box2D category/mask bits, which providers assign to fixture filters.

**Entity removal.** Never call `engine.removeEntity` directly for entities that have bodies. Add a `RemoveComponent` instead. `RemoveSystem` (last in the order) destroys the Box2D body, nulls `userData`, and removes the entity. `BodyComponent.reset()` asserts that `userData` is null. `LifetimeSystem` adds `RemoveComponent` when a `LifetimeComponent` runs out. `RemoveSystem` is also a safety net: bodies that leave the map bounds plus a 20% margin get tagged, but the player is clamped back inside and never removed. `BoundaryFieldSystem` keeps bodies inside the arena instead: it is a `PhysicsStepListener` that applies the mass-independent repulsive field from `physics/BoundaryField.kt` before each step (force on dynamic bodies, projectiles are ignored: they leave the arena, leaving one ripple where they cross the wall) and a hard backstop after each step (clamp inside, reflect outward velocity). It fills `BodyComponent.boundingRadius` lazily.

**Rendering.** `RenderSystem` draws the nebula and starfield, then sprites from interpolated transforms, then the boundary band via `render/FieldShimmerRenderer` (GLSL in `assets/shaders/field_shimmer.*`, impact data from `ShimmerImpacts`).

**Input & events.** `GameEventManager` is the libGDX `InputProcessor`, multiplexed with the UI `Stage`. It translates raw input into `PlayerInputListener` callbacks (aim, thrusters, fire, zoom, block) and broadcasts `MapEventListener.onNewMap`. Systems subscribe as listeners rather than polling `Gdx.input`. `ignorePlayerInputs()`/`enablePlayerInputs()` gate input between screens.

**Debug.** The `IS_DEBUG` constant in `Game.kt` sets the log level and adds `DebugSystem` (the Box2D debug renderer). For per-frame logging, wrap calls in `defaultLoggingUtils.tryLogging { ... }`, which throttles output to once every 30 s. `PlayScreen.render` ticks the throttle. The screenshot harness (`--screenshot=<path>`, `--seed=<long>`, etc.) is described in `docs/tech/dev-workflow.md`.

**Units.** World units are meters-ish. `UNIT_SCALE = 18` px per unit (`Config.kt`). The map is 200×200 units (`MapProvider`, field skin 10 u). `physics/PhysicsConstants.kt` holds `SPEED_OF_LIGHT`, the default for the runtime `RelativitySettings.c`. Box2D clamps movement to 2 units per step, so the top safe speed is `2 u × tick rate` (about 90 u/s at 60 Hz with the 0.75 safety factor, `C_MAX_BOX2D_SAFE`), and c must stay under it. Default c = 20.

**Constants are the single source of truth.** The game must keep working when a base constant changes: the tick rate (`PHYSICS_UPDATE_INTERVAL`), c (`SPEED_OF_LIGHT` / `RelativitySettings.c`), the map size or `UNIT_SCALE`. Rules:
- Never hardcode a number that follows from a base constant (e.g. `45`, `1/45f`, `60f` for c, `67` for the Box2D speed cap). Derive it in code (e.g. `C_MAX = 0.75f * 2f * TICK_RATE`), and write the derivation in the KDoc.
- Values that should scale with c (engine acceleration, projectile speeds, speed-dependent zoom, debug slider ranges) are expressed as a fraction or multiple of c, and code reads the runtime `settings.c`, not `SPEED_OF_LIGHT`.
- Per-step code takes `dt` from the physics step, never a literal.
- Tests reference the same constants (`PHYSICS_UPDATE_INTERVAL`, `settings.c`) instead of copying their values, and a test that pins a tuned number says which constant it assumes.
- Reviewer and verifier treat a new hardcoded derived value as a `major` issue.

**i18n.** UI strings live in `assets/i18n/messages.properties`; English only (owner 2026-11-03).
