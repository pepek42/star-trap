# libGDX / KTX / Ashley / Box2D cheat-sheet for Star Trap

These are the idioms used in this codebase, plus gotchas that have bitten us or will. Agents read this before writing code.

## KTX idioms in use

| Need | Use | Example in repo |
|---|---|---|
| Create an entity | `engine.entity { with<C> { ... } }` (ktx-ashley) | `provider/AsteroidProvider.kt` |
| Add an entity inside `engine.add { }` | `engine.add { entity { ... } }` | `provider/PlayerEntityProvider.kt` |
| Component access | `val xMapper = mapperFor<X>()`, `val Entity.xCmp by propertyFor(xMapper)`, `optionalPropertyFor` | `component/*.kt` |
| Family | `allOf(A::class, B::class).get()`, `oneOf(...)` | systems |
| Has / add component | `entity.has(mapper)`, `entity.addComponent<RemoveComponent>(engine)` | `system/DeathSystem.kt` |
| Box2D world | `createWorld()` (ktx-box2d) | `screen/PlayScreen.kt` |
| Body + fixtures | `world.body(BodyDef.BodyType.DynamicBody) { position.set(..); circle(radius) { density = ..; filter.. } }` | providers |
| DI | `Context().register { bindSingleton(X()) }`, `ctx.inject<X>()`, wrapped by `game.get<X>()` | `Game.kt` |
| Batch scope | `batch.use(camera) { ... }` (ktx-graphics) | `system/RenderSystem.kt` |
| Vector math | `vec2(x, y)`, operators `+ - *` from ktx-math | many |
| Logging | `private val logger = logger<Cls>()` in a companion, `logger.debug { "..." }` (lazy) | all systems |
| Throttled per-frame log | `defaultLoggingUtils.tryLogging { logger.debug { } }` | `debug/LoggingUtils.kt` |
| Dispose | `x.disposeSafely()` | `PlayScreen.dispose` |
| Screens | `KtxGame<KtxScreen>`, `addScreen`, `setScreen<T>()` | `Game.kt` |

## Gotchas

- **Box2D vectors are shared.** `body.position` and `body.linearVelocity` return the *same reused* `Vector2` instance every call. Mutating it (`rotateDeg`, `scl`, `add`) corrupts later reads. Always call `.cpy()`, or copy into your own preallocated vector. Similarly, `Vector2.rotateDeg/rotateRad/scl/nor` **mutate the receiver** and return `this`.
- **Box2D speed caps.** `b2_maxTranslation = 2` units per step and `b2_maxRotation = 0.5π` per step. At the 60 Hz tick that's 120 u/s and 30 π rad/s (`PHYSICS_TICK_RATE` × the per-step cap). Anything faster is silently clamped.
- **No body changes during `world.step` callbacks.** Inside `ContactListener` you can't create or destroy bodies, or change fixtures in a way that alters the broadphase. Record the intent in a component and act in a system. (Setting `isSensor` in `DeathSystem` is fine because it runs outside the step.)
- **Kotlin `%` is `rem`.** The result has the sign of the dividend, so `-1 % 5 == -1`. For angles use `math/Angles.kt` (`wrapAngleRad`, once ST-003 lands) or `floorMod`-style formulas.
- **Radians vs degrees.** `body.angle` is in radians. `Sprite.rotation` and `TransformComponent.*RotationDeg` are in degrees. Name variables with a `Rad`/`Deg` suffix.
- **ShapeRenderer** needs `shape.projectionMatrix = camera.combined`. Don't nest it inside `batch.begin/end`, and use one `begin/end` per frame, not per entity.
- **Blending** for transparent shapes: `Gdx.gl.glEnable(GL20.GL_BLEND)` + `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`. Use additive (`GL_SRC_ALPHA, GL_ONE`) for glow. Reset the batch's blend function after you change it.
- **Ashley pooling.** `PooledEngine` reuses components, so `reset()` must restore *every* field. Don't keep references to an entity after it's removed.
- **Removing entities that have bodies** is done only via `RemoveComponent`. `RemoveSystem` destroys the body, nulls `userData` and then removes the entity. `BodyComponent.reset()` asserts that `userData == null`.
- **Allocation in hot loops.** `vec2(...)` in `processEntity` allocates every frame. Preallocate in the system and reuse it for anything that runs per entity per frame.
- **Shaders and TeaVM.** Keep GLSL compatible with GLSL ES 1.00 / WebGL 1 (`#ifdef GL_ES precision mediump float; #endif`, no `#version 330`) unless we drop the TeaVM target. Always check `shader.isCompiled` and log `shader.log`.
- **FrameBuffer** sizes must follow `resize()`. Dispose and recreate them there.
- **Tests:** a real `World` works in unit tests (natives are on the test classpath). Anything that needs `Gdx.gl` (SpriteBatch, ShaderProgram, Texture) does **not**, so keep that code thin and pull the logic out.

## Useful references

- KTX docs: https://libktx.github.io/ (per-module READMEs on GitHub: `libktx/ktx/<module>/README.md`)
- libGDX wiki: https://libgdx.com/wiki/
- Box2D manual (v2.4): https://box2d.org/documentation/
- Ashley wiki: https://github.com/libgdx/ashley/wiki
