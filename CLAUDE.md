# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Star Trap: Redshift (in-game name since 2026-11-07; the repo, Gradle projects, packages and the `star-trap-settings` preferences keep the old names, and `TITLE` in `Config.kt` is the one source of the displayed name): a proof-of-concept physics-based 2D space shooter (Asteroids-like, aiming toward an action roguelike) built with libGDX + Kotlin + KTX, using Ashley ECS and Box2D. Controls: default auto-aim (the mouse steers the hull, the guns pick targets; LMB also fires), RMB thrusters, mouse wheel zoom, Esc quits to the menu; `--controls=mouse|twin|auto` picks the scheme. The Kotlin package is `com.github.pepek42.asteroids` (not `startrap`).

**The web build (TeaVM) is the primary release target.** Desktop (`lwjgl3`) is the fast dev and test loop, but a feature isn't shipped until it works in the browser. See "Web first" below.

## Vision: a very realistic sim, made playable by ship computers

The main goal (owner 2026-11-04) is a **very, very realistic relativistic space sim** with **player-assist tools** layered on top. Every feature belongs to one of two layers, and cards should say which:

1. **Sim (ground truth).** Physics as real as we can compute: relativistic dynamics, finite light speed, and later gravity. Ideally the **pilot view** shows only what the pilot's eyes could physically see: light-delayed (retarded) positions, aberration, Doppler, searchlight, length contraction. That makes it hard to play, and that's intended.
2. **Assist (the ship's computer).** In-fiction instruments that help the player read and beat the sim, e.g. the tactical overlay (true current positions as wireframes over the delayed sprites), intercept and lead markers, target pointers, the reference grid, the time warp, and auto-aim. Assists **never change the physics**. They only show information the computer could plausibly compute, or act through the same controls the player has. Each assist is a setting or a toggle, so the player can choose between the raw sim and computer-aided play.

Rules that follow from this:
- Prefer the physically correct behaviour. A simplification (FAKE/APPROX) needs a reason and a row in `docs/design/physics-lies.md`.
- Readability problems are solved by **adding or improving an assist**, not by making the sim less real.
- Wording: "displayed sprite" = what light shows the pilot; "true state" = Box2D/ECS state in the arena frame; "computer" = an assist that draws or uses the true state (or a prediction of it).

Docs index: `docs/README.md`. Owner's raw roadmap: `docs/design/todo.md`. Game design: `docs/design/game-design.md`. Physics research: `docs/research/`. Task cards: `docs/backlog.md` (open cards only; finished cards are closed, i.e. deleted, and live on in git history); query them with `scripts/backlog.py next|show|list` instead of reading the file. Detailed architecture and known issues: `docs/tech/architecture.md`. Library gotchas: `docs/tech/ktx-cheatsheet.md`.

Development is agent-driven (see `docs/tech/dev-workflow.md`). Subagents live in `.claude/agents/` (planner, kotlin-dev, junior-dev, verifier, reviewer, physics-researcher). `/auto-dev` runs the backlog loop and `/plan-backlog` adds cards. `scratch_pad/` is gitignored reference material (the gdx template used for the stack migration) and is not part of the build.

## Commands

```bash
./gradlew lwjgl3:run          # run desktop game (working dir = assets/)
./gradlew test                # all unit tests (CI runs: ./gradlew clean test)
./gradlew core:test --tests "com.github.pepek42.asteroids.system.MoveSystemTest"   # single test class
./gradlew core:test --tests "*MoveSystemTest.thrustersShouldWork*"                 # single test method
./gradlew lwjgl3:jar          # runnable fat jar -> lwjgl3/build/lib
./gradlew tool:packTextures  # repack raw_assets/*.png -> assets/textures/textures.atlas/.png
./gradlew tool:balanceSim    # headless balance sweep (ST-101) -> tool/build/balance/{runs.csv,summary.md}; -PbalanceArgs="--seeds=8 --levels=1,2" narrows it; ~2 min, see docs/tech/balance.md
./gradlew tool:balanceSmoke  # tagged "balance" smoke test (ST-130): few seeds, c = 20, levels 1 and 5; not part of `test`
./gradlew teavm:generateJavaScript   # web bundle -> teavm/build/dist/js/webapp (~25 s)
./gradlew teavm:javaScriptDevServer  # web dev server at http://localhost:8080 (URL query = launch flags, e.g. ?seed=42&play)
scripts/web-check.sh --play          # build web + headless Firefox smoke test of a level; prints RESULT and a screenshot path
```

- There is no linter. Formatting follows `.editorconfig`: 4-space indent for Kotlin, 2 for Gradle scripts.
- After adding or changing a sprite in `raw_assets/`, run `tool:packTextures` and commit the regenerated atlas. Code looks sprites up by region name in `textures/textures.atlas`.
- Tests use JUnit (Jupiter) + mockito-kotlin. The test classpath includes `assets/` and desktop natives, so tests can touch real Box2D/libGDX classes.

## Web first

`teavm:compileKotlin` passing does **not** mean the web build works: TeaVM finds problems only in `generateJavaScript` or at runtime in the browser. `scripts/web-check.sh --play` builds the bundle and runs a level in headless Firefox (details: `docs/tech/dev-workflow.md` "Web check"); /auto-dev runs it after web-risk cards and every few cards. Web-risk areas:
- **Reflection.** TeaVM keeps reflection metadata only for classes registered in `teavm/build.gradle.kts` (`reflection(...)`, glob patterns, a package needs `.**`). Ashley's `PooledEngine` creates components reflectively, so a component outside `com.github.pepek42.asteroids.component` needs registering. Components must have exactly one public no-arg constructor (no constructor parameters, no `data class`): gdx-teavm picks `getConstructors()[0]`. `ComponentConstructorTest` enforces this.
- **JVM-only APIs.** No threads, `java.io.File`, `System.getenv`, `String.format` with locale tricks, `java.util.concurrent` beyond the basics, or JNI libraries other than gdx/box2d/freetype. Use libGDX abstractions (`Gdx.files`, `Preferences`, `TimeUtils`).
- **GLSL.** ES 1.00 / WebGL 1 only (`#ifdef GL_ES precision mediump float; #endif`); no `glLineWidth` > 1 or desktop-only GL calls.
- **Performance.** JS is slower than the JVM: no per-frame allocation, and watch Box2D body counts.
- **Launch options.** The web launcher maps the URL query to the same flags (`?seed=42&play` = `--seed=42 --play`, see `queryToLaunchArgs`), so a new flag works on web automatically; `screenshot` is desktop-only.

## Architecture

**Bootstrap & DI.** `Game` (a `KtxGame`) loads assets synchronously through `AssetManager`, then registers singletons in a `ktx.inject.Context`: camera, `ExtendViewport` (at least 48×27 world units; a non-16:9 window shows more world instead of letterboxing), `SpriteBatch`, atlas, skin, i18n bundle, `GameEventManager`, `MapProvider`, `GameState`, and the screens. It also registers one seeded `RandomXS128`; gameplay randomness must use it. `Game` takes a `LaunchOptions`, parsed from the command line by `Lwjgl3Launcher`. Other code fetches singletons with `game.get<T>()`. Screens: `MainMenuScreen` (pages Main / New game / Scenarios / Settings; run-only physics settings, `RUN_SETTING_KEYS`, are picked on New game, the rest are global) → `PlayScreen` → `LevelClearedScreen` (`game.finishLevel()`; shop placeholder, later the save point). `GameState` holds the level and an optional `Scenario` (`scenario/`: a hand-made level with its own arena size, spawns, physics overrides and briefing); menus share `ui/MenuStyle`. The brand font (Audiowide, OFL, `assets/fonts/`) is rasterised by FreeType at startup (`ui/BrandFonts.kt`) into the skin; ship the TTF unmodified (Reserved Font Name) and list third-party assets in `CREDITS.md`.

**ECS (Ashley `PooledEngine`).** `PlayScreen` owns the per-level `PooledEngine` and Box2D `World` and registers the systems in `setupEcs()` through `buildGameplaySystems` (`ecs/GameplaySystems.kt`, shared with the headless balance runner; PlayScreen adds the GL systems via its `presentation` callback). **System registration order is the execution order and matters.** The order is time warp (pilot clock) → end level → input → enemy AI → move → weapons → contact → boundary field → projectile sweep → relativistic dynamics → light history → physics step → observer → camera → render → debug/HUD/minimap → lifetime → death → remove. `show()` advances the level and spawns the player and asteroids, with the asteroid count growing with level².

**Component convention.** Each component file defines the component class (usually `Pool.Poolable`), a `xxxMapper = mapperFor<...>()`, and `Entity.xxxCmp` / `Entity.xxxCmpOptional` extension properties via `propertyFor`. Follow this pattern for new components. Every entity should have a `BaseInfoComponent` (id, faction, `EntityType`).

**Entity creation.** Providers in `provider/` (`PlayerEntityProvider`, `AsteroidProvider`, `WeaponProjectileProvider`) build entities with the `engine.entity { with<...> {} }` DSL and create the Box2D body with `world.body {}`. A body's `userData` is set to its `Entity`, and `ContactSystem` relies on that cast.

**Physics loop.** `PhysicsSystem` steps the world at a fixed `PHYSICS_UPDATE_INTERVAL = 1/PHYSICS_TICK_RATE` (60 Hz) using an accumulator. `TimeWarpSystem` caps catch-up at `MAX_LAG_STEPS` (derived, 7) steps at warp 1, rising with warp up to `MAX_STEPS_PER_FRAME` (derived, 12). It consumes `GameClock.worldDt` from `TimeWarpSystem` (registered first): the arena runs on the pilot's clock, fast-forwarded by the player's γ (capped at `timeWarpMax`). The world has `autoClearForces = false`, and `clearForces` runs after every step. It also lerps `TransformComponent.interpolatedPosition/RotationDeg` between the previous and current body state. Rendering must use these interpolated values, not the raw body position. `PhysicsSystem` takes `PhysicsStepListener`s (`beforeStep`/`afterStep`) called around every step; `MoveSystem` is one and applies thrust in `beforeStep`, so forces act once per step. Rotation is a PD controller (`ShipRotationController`) that `PhysicsSystem` applies as an angular impulse before every physics substep, driven by `MoveComponent.targetAngleRad`. Massive bodies (player, asteroids) carry a `RelativisticComponent`: step listeners add proper accelerations to its `accel` in `beforeStep`, and `RelativisticDynamics` (the last listener) integrates the proper velocity `u = γv` and writes `v = u/γ` into Box2D; `afterStep` reads contact-changed velocities back into `u`. Engines are a proper acceleration (`ENGINE_PROPER_ACCEL`), so ships approach c but never reach it. `RELATIVITY_ENABLED` switches this off.

**Gravity, pilot view and death.** Gravity sources (`GravitySourceComponent`: black holes and planets) pull each other and the player through `GravitySystem`, a step listener placed after `BoundaryFieldSystem`. Black holes kill by horizon, not by contact. The pilot view (V, default Outside) draws light-delayed, boosted sprites through `LightHistorySystem`. A player at 0 health ends the run through `DeathSystem` → `Game.endRun()`. Details, and what is still planned (lit sprites, shadows, lensing), are in `docs/tech/architecture.md`.

**Collision filtering.** `FactionEnum` (PLAYER/NEUTRAL/ENEMY) holds the Box2D category/mask bits, which providers assign to fixture filters.

**Entity removal.** Never call `engine.removeEntity` directly for entities that have bodies. Add a `RemoveComponent` instead. `RemoveSystem` (last in the order) destroys the Box2D body, nulls `userData`, and removes the entity. `BodyComponent.reset()` asserts that `userData` is null. `LifetimeSystem` adds `RemoveComponent` when a `LifetimeComponent` runs out. `RemoveSystem` is also a safety net: bodies that leave the map bounds plus a 20% margin get tagged, but the player is clamped back inside and never removed. `BoundaryFieldSystem` keeps bodies inside the arena instead: it is a `PhysicsStepListener` that applies the mass-independent repulsive field from `physics/BoundaryField.kt` before each step (force on dynamic bodies, projectiles are ignored: they leave the arena, leaving one ripple where they cross the wall) and a hard backstop after each step (clamp inside, reflect outward velocity). It fills `BodyComponent.boundingRadius` lazily.

**Input & events.** `GameEventManager` is the libGDX `InputProcessor`, multiplexed with the UI `Stage`. It dispatches keys and mouse buttons through the `InputBindings` table (`input/InputBindings.kt`; add new keys there, never as literals) into `PlayerInputListener` callbacks (aim, thrusters, fire, zoom, block, pilot view) and the debug panel listeners, and broadcasts `MapEventListener.onNewMap`. Systems subscribe as listeners rather than polling `Gdx.input`. `ignorePlayerInputs()`/`enablePlayerInputs()` gate input between screens.

**Settings.** Player settings are the `SettingSpec` list in `settings/MenuSettings.kt` (the menu, persistence and "Reset defaults" all iterate it). They persist in libGDX `Preferences` (`PREFERENCES_NAME`, `Config.kt`) via `KeyValueStore`, are loaded in `Game.create()`, use an in-memory store for `--screenshot` runs, and command-line flags win over saved values.

**Debug.** The "Debug mode" menu setting (`DebugSettings.debugMode`, default off, last in `menuSettings`) sets the libGDX log level, enables `LoggingUtils`, makes `DebugSystem` (the Box2D debug renderer, always registered) draw, and allows the debug panel (`DebugSettings.panelAllowed = debugMode || forcePanel`; `--debug-panel` sets `forcePanel`). For per-frame logging, wrap calls in `defaultLoggingUtils.tryLogging { ... }`, which throttles output to once every 30 s. `PlayScreen.render` ticks the throttle. The screenshot harness (`--screenshot=<path>`, `--seed=<long>`, etc.) is described in `docs/tech/dev-workflow.md`.

**Units.** World units are meters-ish. `UNIT_SCALE = 18` px per unit (`Config.kt`). The map is 200×200 units (`MapProvider`, field skin 10 u). `physics/PhysicsConstants.kt` holds `SPEED_OF_LIGHT`, the default for the runtime `RelativitySettings.c`. Box2D clamps movement to 2 units per step, so the top safe speed is `2 u × tick rate` (about 90 u/s at 60 Hz with the 0.75 safety factor, `C_MAX_BOX2D_SAFE`), and c must stay under it. Default c = 20.

**Constants are the single source of truth.** The game must keep working when a base constant changes: the tick rate (`PHYSICS_UPDATE_INTERVAL`), c (`SPEED_OF_LIGHT` / `RelativitySettings.c`), the map size or `UNIT_SCALE`. Rules:
- Never hardcode a number that follows from a base constant (e.g. `45`, `1/45f`, `60f` for c, `67` for the Box2D speed cap). Derive it in code (e.g. `C_MAX = 0.75f * 2f * TICK_RATE`), and write the derivation in the KDoc.
- Values that should scale with c (engine acceleration, projectile speeds, speed-dependent zoom, debug slider ranges) are expressed as a fraction or multiple of c, and code reads the runtime `settings.c`, not `SPEED_OF_LIGHT`.
- Per-step code takes `dt` from the physics step, never a literal.
- Tests reference the same constants (`PHYSICS_UPDATE_INTERVAL`, `settings.c`) instead of copying their values, and a test that pins a tuned number says which constant it assumes.
- Reviewer and verifier treat a new hardcoded derived value as a `major` issue.

**i18n.** UI strings live in `assets/i18n/messages.properties`; English only (owner 2026-11-03).
