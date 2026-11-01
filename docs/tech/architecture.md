# Star Trap: Technical Architecture

Status: describes the code as of 2026-11-01 (branch `AI-slop`, after M1). Keep this file current when the architecture changes. Agents read it before coding.

## Stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4 (JVM toolchain 25, auto-provisioned via foojay) |
| Framework | libGDX 1.14.2, KTX 1.14.2-rc2 (`app`, `ashley`, `box2d`, `inject`, `scene2d`, `math`, `log`, `graphics`, `i18n`, `preferences`) |
| ECS | Ashley 1.7.4 (`PooledEngine`) |
| Physics | Box2D (gdx-box2d), fixed step 1/45 s |
| Build | Gradle 9.8.1, Kotlin DSL, version catalog `gradle/libs.versions.toml`, convention plugin `build-logic/src/main/kotlin/kotlin-jvm.gradle.kts` |
| Tests | JUnit 6 (Jupiter) + mockito-kotlin. Test classpath has `assets/` and desktop natives |
| Modules | `core` (all logic), `lwjgl3` (desktop launcher, construo packaging), `teavm` (experimental web), `tool` (texture packer) |

## Runtime structure

```mermaid
flowchart LR
  Game["Game (KtxGame)<br/>AssetManager + ktx.inject Context"] --> MainMenuScreen
  MainMenuScreen --> PlayScreen
  PlayScreen -->|"EndLevelSystem → game.finishLevel()"| LevelClearedScreen
  LevelClearedScreen --> PlayScreen
  PlayScreen --- Engine["PooledEngine (per PlayScreen)"]
  PlayScreen --- World["Box2D World (autoClearForces=false)"]
```

Singletons registered in `Game.create()` and fetched with `game.get<T>()`: `RandomXS128` (seeded from `LaunchOptions.seed`, or the clock), `GameState`, `OrthographicCamera`, `GameEventManager`, `SpriteBatch`, `TextureAtlas` (textures), `Skin`, `I18NBundle`, `Viewport` (`FitViewport` 48×27 world units), `Stage`, `AssetManager`, `MapProvider`, `BackgroundProvider`, and the screens. `Game` takes a `LaunchOptions`, which `Lwjgl3Launcher` parses from the command line.

## ECS system order

Systems are registered in `PlayScreen.setupEcs()`. **Registration order is execution order.**

| # | System | Responsibility |
|---|---|---|
| 1 | `EndLevelSystem` | No asteroids left → `game.finishLevel()` |
| 2 | `PlayerInputSystem` | Input listener → `MoveComponent.thrusters`, `targetAngleRad` (aim angle, set every frame for the player), `WeaponComponent.doAttack` |
| 3 | `MoveSystem` | Step listener: applies thrust in `beforeStep` (once per physics step) |
| 4 | `WeaponSystem` | Cooldown, spawns projectiles via `WeaponProjectileProvider` |
| 5 | `ContactSystem` | Box2D `ContactListener`: projectile damage into `HealthComponent` |
| 5b | `BoundaryFieldSystem` | Step listener (registered just before `PhysicsSystem`). `beforeStep`: mass-independent repulsive boundary field (`physics/BoundaryField.kt`) as force on dynamic bodies, velocity change on kinematic ones. `afterStep`: hard backstop (clamp inside, reflect outward velocity). Lazily fills `BodyComponent.boundingRadius` |
| 6 | `PhysicsSystem` | Accumulator stepping (max 5 steps/frame). Before each substep applies the `ShipRotationController` angular impulse for entities with `targetAngleRad`. Then `clearForces` and interpolation of transforms |
| 7 | `CameraSystem` | Follows player (clamped to map), mouse wheel zoom 0.5–2 |
| 9 | `RenderSystem` | Nebula background + `Starfield` (camera-relative parallax), then sprites, using interpolated transforms |
| 10 | `DebugSystem` | (only when `IS_DEBUG`) Box2D debug renderer |
| 11 | `HudSystem` | scene2d HUD |
| 12 | `MinimapSystem` | Bottom-right minimap (`ui/MinimapViewport`): arena background, entity dots, camera view rectangle, arena border |
| 13 | `LifetimeSystem` | `LifetimeComponent.remaining -= dt`; at 0 adds `RemoveComponent`; fades sprite alpha over the last `LIFETIME_FADE_SECONDS` |
| 14 | `DeathSystem` | health ≤ 0 → asteroid split, add `RemoveComponent` |
| 15 | `RemoveSystem` | Out-of-bounds safety net (20 % map margin: player is clamped back inside, others get `RemoveComponent`), destroys bodies, removes entities |

## Components (`core/.../component/`)

`BaseInfoComponent` (id, faction, `EntityType`; every entity has one), `BodyComponent` (Box2D body), `TransformComponent` (prev + interpolated position/rotation), `SpriteComponent`, `MoveComponent` (thrusters −1..1, `targetAngleRad` world angle or null), `WeaponComponent`, `ProjectileComponent` (sealed projectile types, currently `LaserProjectile`), `HealthComponent`, `AsteroidComponent`, `PlayerComponent` (mapper and `playerCmpOptional`), `ShipHandlingComponent` (per-ship PD gains, defaults from `ShipRotationController`), `LifetimeComponent` (remaining/total, `LifetimeClock` WORLD or PROPER, `bouncesLeft`, -1 = unlimited), `RemoveComponent`.

The convention is described in `CLAUDE.md`: a `Poolable` class, `xxxMapper`, and `Entity.xxxCmp`/`xxxCmpOptional`.

## Physics loop details

- `PHYSICS_UPDATE_INTERVAL = 1/45 s`, velocity iterations 6, position iterations 2.
- `autoClearForces = false`. `PhysicsSystem` takes a list of `PhysicsStepListener`s. For every step it runs `beforeStep(dt)` on each, then `applyRotation()`, `world.step`, `afterStep(dt)`, then `world.clearForces()`. `MoveSystem` applies thrust in `beforeStep`, so forces act exactly once per step. `PHYSICS_SUBSTEPS = 1` splits each tick into sub-steps if needed.
- **Rotation (ST-004):** `PlayerInputSystem` writes the world angle to `MoveComponent.targetAngleRad`. `ShipRotationController` is a PD controller (kp 80, kd 2√kp, acceleration clamp ±200 rad/s², angular velocity cap 2π rad/s). `PhysicsSystem` turns its output into an angular impulse before **every** `world.step`, so it stays correct with several substeps per frame. Entities with a null `targetAngleRad` are not rotated. Angle error uses `shortestAngleDiffRad`, so the ship always turns the short way.
- **Box2D speed cap:** `b2_maxTranslation = 2.0` units per step, so the effective max speed is **90 u/s** at 45 Hz. `SPEED_OF_LIGHT = 60` (in `physics/PhysicsConstants.kt`): c = 60 u/s fits under the 90 u/s Box2D cap.
- Units: 1 world unit = 18 px (`UNIT_SCALE`). The map is 100×100 units. The view is 48×27 units at zoom 1.

## Collision filtering

`FactionEnum` (`PLAYER`, `NEUTRAL`, `ENEMY`). `ownCategoryBit` is the faction's own bit and is set as the fixture's `categoryBits`. `collidesWithMask` holds the bits the faction collides with and is set as `maskBits`. `FactionEnum.collidesWith(other)` checks the symmetric rule.

## Known issues and tech debt

These are input for the backlog. Card IDs are given where a card exists.

1. `BaseInfoComponent.reset()` doesn't reset `entityType`. The global id counter is a static mutable. (No card yet.)

Fixed in M1 and removed from this list: ST-006 (level cleanup in `hide()`), ST-007 (faction filter bits), ST-008 (small asteroid texture), ST-009 (CI on JDK 25), ST-011 (minimap rework), ST-012 (starfield).

## Testing strategy

- Pure logic (math, relativity formulas, angle wrapping, force fields) lives in small top-level functions or objects, with no GL context needed, and is unit tested directly.
- Systems are tested with mocked `Body` or a real `World` (natives are on the test classpath). See `MoveSystemTest`, `WeaponProjectileProviderTest`.
- Rendering and shaders can't be unit tested. Cards that touch them carry a `playtest:` line, and a human checks those visually.

## Planned architecture (see `docs/design/game-design.md` and `docs/research/relativity-effects.md`)

- **Relativistic dynamics layer**: our own integration of momentum and proper time per entity, writing velocities into Box2D. Box2D keeps collision detection and response.
- **Observer-frame rendering**: render the scene to an FBO, then run post-process shaders (aberration, Doppler/searchlight, lensing). Per-sprite shaders handle effects that belong to one object (length contraction, Terrell-like distortion).
- **Arena boundary field**: a radial or edge-distance repulsive force applied in a dedicated system before the physics step. Implemented as `BoundaryFieldSystem` (replaced the old wrap teleport).
