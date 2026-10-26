# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Star Trap: a proof-of-concept physics-based 2D space shooter (Asteroids-like, aiming toward an action roguelike) built with libGDX + Kotlin + KTX, using Ashley ECS and Box2D. Controls: LMB fire, RMB thrusters, mouse wheel zoom. The Kotlin package is `com.github.pepek42.asteroids` (not `startrap`).

Roadmap and planned work live in `docs/design/todo.md`. The current plan is to migrate to a newer gdx Kotlin template (Kotlin DSL, version catalog, `build-logic` convention plugins). A freshly generated reference copy sits in `scratch_pad/star-trap-update/`. It is gitignored and is reference material only, not part of the build.

## Commands

Groovy Gradle build, JDK 17 (`sourceCompatibility`/`jvmTarget` 17).

```bash
./gradlew lwjgl3:run          # run desktop game (working dir = assets/)
./gradlew test                # all unit tests (CI runs: ./gradlew clean test)
./gradlew core:test --tests "com.github.pepek42.asteroids.system.MoveSystemTest"   # single test class
./gradlew core:test --tests "*MoveSystemTest.thrustersShouldWork*"                 # single test method
./gradlew lwjgl3:jar          # runnable fat jar -> lwjgl3/build/lib
./gradlew texturePacker       # repack raw_assets/*.png -> assets/textures/textures.atlas/.png
./gradlew teavm:run           # experimental web build at http://localhost:8080
```

- There is no linter. Formatting follows `.editorconfig`: 4-space indent for Kotlin, 2 for `.gradle`.
- After adding or changing a sprite in `raw_assets/`, run `texturePacker` and commit the regenerated atlas. Code looks sprites up by region name in `textures/textures.atlas`.
- `compileJava` regenerates `assets/assets.txt` (an asset list) as a side effect.
- Tests use JUnit 5 + mockito-kotlin. The test classpath includes `assets/` and desktop natives, so tests can touch real Box2D/libGDX classes.

## Modules

- `core`: all game logic.
- `lwjgl3`: desktop launcher (`Lwjgl3Launcher.kt`).
- `teavm`: experimental browser launcher.

## Architecture

**Bootstrap & DI.** `Game` (a `KtxGame`) loads assets synchronously through `AssetManager`, then registers singletons in a `ktx.inject.Context`: camera, `FitViewport` (48×27 world units), `SpriteBatch`, atlas, skin, i18n bundle, `GameEventManager`, `MapProvider`, `GameState`, and the screens. Other code fetches them with `game.get<T>()`. Screens: `MainMenuScreen` → `PlayScreen` → `LevelClearedScreen` (`game.finishLevel()`).

**ECS (Ashley `PooledEngine`).** `PlayScreen` owns the per-level `PooledEngine` and Box2D `World` and registers all systems in `setupEcs()`. **System registration order is the execution order and matters.** The order is input → move → weapons → contact → physics step → camera → wrap → render → debug/HUD/minimap → death → remove. `show()` advances the level and spawns the player and asteroids, with the asteroid count growing with level².

**Component convention.** Each component file defines the component class (usually `Pool.Poolable`), a `xxxMapper = mapperFor<...>()`, and `Entity.xxxCmp` / `Entity.xxxCmpOptional` extension properties via `propertyFor`. Follow this pattern for new components. Every entity should have a `BaseInfoComponent` (id, faction, `EntityType`).

**Entity creation.** Providers in `provider/` (`PlayerEntityProvider`, `AsteroidProvider`, `WeaponProjectileProvider`) build entities with the `engine.entity { with<...> {} }` DSL and create the Box2D body with `world.body {}`. A body's `userData` is set to its `Entity`, and `ContactSystem` relies on that cast.

**Physics loop.** `PhysicsSystem` steps the world at a fixed `PHYSICS_UPDATE_INTERVAL = 1/45 s` using an accumulator, which caps catch-up at 5 steps. The world has `autoClearForces = false`, and forces are cleared after the stepping loop. It also lerps `TransformComponent.interpolatedPosition/RotationDeg` between the previous and current body state. Rendering must use these interpolated values, not the raw body position. `BodyComponent.moveForcesApplied` makes `MoveSystem` apply thrust/rotation only once per physics step, and `PhysicsSystem.preProcess` resets it.

**Collision filtering.** `FactionEnum` (PLAYER/NEUTRAL/ENEMY) holds the Box2D category/mask bits, which providers assign to fixture filters.

**Entity removal.** Never call `engine.removeEntity` directly for entities that have bodies. Add a `RemoveComponent` instead. `RemoveSystem` (last in the order) destroys the Box2D body, nulls `userData`, and removes the entity. `BodyComponent.reset()` asserts that `userData` is null. `RemoveSystem` also tags non-wrapping bodies that leave the map bounds plus a 5% buffer. Entities with `WrapComponent` teleport to the opposite edge in `WrapSystem`.

**Input & events.** `GameEventManager` is the libGDX `InputProcessor`, multiplexed with the UI `Stage`. It translates raw input into `PlayerInputListener` callbacks (aim, thrusters, fire, zoom, block) and broadcasts `MapEventListener.onNewMap`. Systems subscribe as listeners rather than polling `Gdx.input`. `ignorePlayerInputs()`/`enablePlayerInputs()` gate input between screens.

**Debug.** The `IS_DEBUG` constant in `Game.kt` sets the log level and adds `DebugSystem` (the Box2D debug renderer). For per-frame logging, wrap calls in `defaultLoggingUtils.tryLogging { ... }`, which throttles output to once every 30 s. `PlayScreen.render` ticks the throttle.

**Units.** World units are meters-ish. `UNIT_SCALE = 18` px per unit (`Config.kt`). The map is 100×100 units (`MapProvider`). `physics/PhysicsConstants.kt` holds `SPEED_OF_LIGHT` for the planned relativity mechanics.

**i18n.** UI strings live in `assets/i18n/messages.properties` and `messages_pl.properties`. Keep both files in sync.
