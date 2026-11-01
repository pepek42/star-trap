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
| 3 | `MoveSystem` | Step listener, `beforeStep` once per physics step. Bodies with a `RelativisticComponent`: adds `thrustAccelWorld(ENGINE_PROPER_ACCEL·thr, …)` to `RelativisticComponent.accel` (reverse thrust is `REVERSE_THRUST_FRACTION` as strong). Other bodies: Newtonian `applyForceToCenter` |
| 4 | `WeaponSystem` | Cooldown, spawns projectiles via `WeaponProjectileProvider` |
| 5 | `ContactSystem` | Box2D `ContactListener`: `beginContact` calls top-level `applyProjectileHit(engine, projectile, target)`, which applies damage to `HealthComponent`, sets `ProjectileComponent.spent` and adds `RemoveComponent`. Projectiles are consumed on first hit and no longer pierce; a spent projectile ignores further hits |
| 6 | `BoundaryFieldSystem` | Physics step listener, passed to `PhysicsSystem`. `beforeStep`: mass-independent repulsive boundary field (`physics/BoundaryField.kt`) as force on dynamic bodies, velocity change on kinematic projectiles. `afterStep`: hard backstop (clamp inside, reflect outward velocity), emits shimmer impacts into `ShimmerImpacts` and advances its clock. Lazily fills `BodyComponent.boundingRadius` |
| 7 | `ProjectileSweepSystem` | Registered before `PhysicsSystem` and passed as its third step listener (order: thrust, field, sweep). `afterStep`: for each unspent projectile, `world.rayCast` from `TransformComponent.prevPosition` (start-of-tick position) to the current body position, so fast sensor projectiles can't tunnel through small targets. Ignores own body, sensors and non-colliding factions (`collidesWith`), keeps the nearest hit and calls `applyProjectileHit`. Valid while `PHYSICS_SUBSTEPS = 1`. Known limitation: a projectile reflected by the boundary backstop gets a chord ray from its old position to the post-reflection position (low risk) |
| 8 | `RelativisticDynamics` | Registered right before `PhysicsSystem` and passed as its LAST step listener (order: thrust, field, sweep, relativistic). `beforeStep`: resets non-finite `u`/`accel` to zero, `u += accel·dt`, `clampU` to `gammaMax`, writes `v = u/γ` into the body, remembers `vSet` and `gUsed`. `afterStep`: if the body velocity differs from `vSet` by more than `COLLISION_EPS` (contact, backstop), clamps it to `BETA_MAX_MASSIVE·c` and sets `u = uFromV(v)`; then `tau += dt/gUsed` and clears `accel`. Family `RelativisticComponent` + `BodyComponent` |
| 9 | `PhysicsSystem` | Accumulator stepping (max 5 steps/frame). Per step: listeners' `beforeStep`, `ShipRotationController` angular impulse for entities with `targetAngleRad`, `world.step`, listeners' `afterStep`, `clearForces`. Transform interpolation runs once per frame |
| 10 | `CameraSystem` | Follows player (clamped to map plus `FIELD_SKIN / 2` margin), mouse wheel zoom 0.5–2 |
| 11 | `RenderSystem` | Nebula background + `Starfield` (camera-relative parallax), then sprites (interpolated transforms), then the boundary field band via `render/FieldShimmerRenderer` (`assets/shaders/field_shimmer.*`: honeycomb brightening with depth plus ripples expanding at c from `ShimmerImpacts`, additive blend; `u_time` is `ShimmerImpacts.time`; falls back to a pulsing tint if the shader fails to compile). `RenderSystem` is `Disposable` and disposes the renderer |
| 12 | `DebugSystem` | (only when `IS_DEBUG`) Box2D debug renderer |
| 13 | `HudSystem` | scene2d HUD |
| 14 | `MinimapSystem` | Bottom-right minimap (`ui/MinimapViewport`): arena background, entity dots, camera view rectangle, arena border |
| 15 | `LifetimeSystem` | `LifetimeComponent.remaining -= dt`; at 0 adds `RemoveComponent`; fades sprite alpha over the last `LIFETIME_FADE_SECONDS`. Uses world time for both clock types until ST-040 |
| 16 | `DeathSystem` | health ≤ 0 → asteroid split, add `RemoveComponent` |
| 17 | `RemoveSystem` | Out-of-bounds safety net: bodies beyond the map plus a 20 % margin get `RemoveComponent`, except the player, which is clamped to 1 unit inside the map with velocity zeroed. Destroys bodies, removes entities |

## Components (`core/.../component/`)

`BaseInfoComponent` (id, faction, `EntityType`; every entity has one), `BodyComponent` (Box2D body, `boundingRadius` for the boundary field, -1 until computed), `TransformComponent` (prev + interpolated position/rotation), `SpriteComponent`, `MoveComponent` (thrusters −1..1, `targetAngleRad` world angle or null), `WeaponComponent`, `ProjectileComponent` (sealed projectile types, currently `LaserProjectile`; `spent` flag), `HealthComponent`, `AsteroidComponent`, `PlayerComponent` (mapper and `playerCmpOptional`), `ShipHandlingComponent` (per-ship PD gains, defaults from `ShipRotationController`), `LifetimeComponent` (remaining/total, `LifetimeClock` WORLD or PROPER, `bouncesLeft`, -1 = unlimited), `RemoveComponent`, `RelativisticComponent` (ST-037: proper velocity `u`, per-step `accel` accumulator, proper time `tau`, `gUsed`, `vSet`, `gammaMax`; added to the player and all asteroids when `RELATIVITY_ENABLED`).

The convention is described in `CLAUDE.md`: a `Poolable` class, `xxxMapper`, and `Entity.xxxCmp`/`xxxCmpOptional`.

## Physics loop details

- `PHYSICS_UPDATE_INTERVAL = 1/45 s`, velocity iterations 6, position iterations 2. The accumulator is capped at 5 intervals per frame.
- Once per frame, `preProcess` stores each body's previous position and rotation. After the stepping loop, `interpolateTransforms` lerps `TransformComponent.interpolatedPosition/RotationDeg` with `alpha = accumulator / interval`. Rendering uses only these interpolated values.
- `autoClearForces = false`. `PhysicsSystem` takes a list of `PhysicsStepListener`s (`MoveSystem`, `BoundaryFieldSystem`, `ProjectileSweepSystem`, `RelativisticDynamics`, in that order). For every step it runs `beforeStep(dt)` on each listener, then `applyRotation()`, `world.step`, `afterStep(dt)` on each, then `world.clearForces()`. Thrust is applied in `beforeStep`, so it acts once per step. `PHYSICS_SUBSTEPS = 1` splits each step into sub-steps if needed.
- **RelativitySettings (ST-056):** `physics/RelativitySettings` is a mutable singleton in DI (`Game.create` binds it, `PlayScreen` passes it to `MoveSystem`, `RelativisticDynamics`, `BoundaryFieldSystem`, the providers and `FieldShimmerRenderer`). Runtime code reads `c`, `engineProperAccel`, `gammaMaxMassive`, `fieldParams`/`fieldDamping` and the switches `relativityEnabled` / `fieldEnabled` from it; the consts in `PhysicsConstants.kt` are only defaults. Setters clamp (`c` to `[C_MIN, C_MAX_BOX2D_SAFE]`). Providers always attach `RelativisticComponent`; with `relativityEnabled = false` `MoveSystem` uses Newtonian force and `RelativisticDynamics` keeps `u` synced from the body velocity. With `fieldEnabled = false` only the hard backstop acts. Changing `c` mid-flight keeps `u`; photons in flight keep their speed until they bounce.
- **Relativistic integration (ST-037):** contributors add proper accelerations into `RelativisticComponent.accel` during `beforeStep`, and `RelativisticDynamics` (last) integrates `u` and writes `v = u/γ` into Box2D, so Box2D only does collisions. `afterStep` reads back any contact-changed velocity into `u`. Forces applied directly to a relativistic body (the boundary field until ST-038) change its velocity during the step; `afterStep` picks that up only when the change exceeds `COLLISION_EPS` (0.05 u/s per step, i.e. accelerations above about 2.25 u/s²), smaller pushes are lost. Rotation is untouched: the angular impulse stays Newtonian.
- **Rotation (ST-004):** `PlayerInputSystem` writes the world angle to `MoveComponent.targetAngleRad`. `ShipRotationController` is a PD controller (kp 80, kd 2√kp, acceleration clamp ±200 rad/s², angular velocity cap 2π rad/s). `PhysicsSystem` turns its output into an angular impulse before **every** `world.step`, so it stays correct with several substeps per frame. Entities with a null `targetAngleRad` are not rotated. Angle error uses `shortestAngleDiffRad`, so the ship always turns the short way.
- **Box2D speed cap:** `b2_maxTranslation = 2.0` units per step, so the effective max speed is **90 u/s** at 45 Hz. `SPEED_OF_LIGHT = 60` (in `physics/PhysicsConstants.kt`) sits under that cap.
- Units: 1 world unit = 18 px (`UNIT_SCALE`). The map is 200×200 units (field skin 10 u). The view is 48×27 units at zoom 1.
- **Boundary field (`BoundaryFieldSystem`):** the field acts on bodies before each step, and the hard backstop runs after it. Constants are in `physics/BoundaryField.kt`.

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
- **Arena boundary field**: implemented as `BoundaryFieldSystem` (see the system table). It replaced the old wrap teleport.
