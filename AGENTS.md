# AGENTS.md

Instructions for AI coding agents (Claude Code, opencode, and others) working in this repository. Tool-specific files only point here: `CLAUDE.md` imports this file, and opencode reads it directly.

## Project

Star Trap: Redshift is a proof-of-concept, physics-based 2D space shooter (Asteroids-like, heading toward an action roguelike). It is built with libGDX + Kotlin + KTX, Ashley ECS and Box2D. "Redshift" has been the in-game name since 2026-11-07. The repo, Gradle projects, packages and the `star-trap-settings` preferences keep the old names, and `TITLE` in `Config.kt` is the only source of the displayed name. The Kotlin package is `com.github.pepek42.asteroids` (not `startrap`).

Controls: by default auto-aim (the mouse steers the hull and the guns pick targets; LMB also fires), RMB fires the thrusters, the mouse wheel zooms, and Esc quits to the menu. `--controls=twin|auto` picks the scheme.

**The web build (TeaVM) is the primary release target.** Desktop (`lwjgl3`) is the fast dev and test loop, but a feature isn't shipped until it works in the browser (see "Web first").

## Vision: a realistic sim, made playable by ship computers

The goal (owner, 2026-11-04) is a **very realistic relativistic space sim** with **player-assist tools** on top. Every feature belongs to one of two layers, and cards say which:

1. **Sim (ground truth).** Physics as real as we can compute: relativistic dynamics, finite light speed, gravity. The **pilot view** ideally shows only what the pilot's eyes could see: light-delayed (retarded) positions, aberration, Doppler, searchlight effect, length contraction. That makes it hard to play, on purpose.
2. **Assist (the ship's computer).** In-fiction instruments that help the player read and beat the sim: the tactical overlay, intercept and lead markers, target pointers, the reference grid, time warp and auto-aim. Assists **never change the physics**. They only show what the computer could plausibly compute, or they act through the player's own controls. Each one is a setting or toggle.

Rules:
- Prefer the physically correct behaviour. A simplification (FAKE/APPROX) needs a reason and a row in `docs/design/physics-lies.md`.
- Fix readability problems by **adding or improving an assist**, not by making the sim less real.
- Wording: "displayed sprite" = what light shows the pilot; "true state" = the Box2D/ECS state in the arena frame; "computer" = an assist that draws or uses the true state (or a prediction of it).

## Where things are

- Docs index: `docs/README.md`. Owner's roadmap: `docs/design/todo.md`. Game design: `docs/design/game-design.md`. Physics research: `docs/research/` (read only the sections a card names).
- **Architecture, system order, invariants and known issues: `docs/tech/architecture.md`.** Read only the sections that cover the code you touch. Library gotchas: `docs/tech/ktx-cheatsheet.md`.
- Task cards: `docs/backlog.md` (open cards only; closed cards are deleted and live on in git history). Query it with `scripts/backlog.py next|show ST-NNN|list|close`; don't read the whole file.
- Agent workflow: `docs/tech/dev-workflow.md`. Agent prompts: `agents/*.md` (the source; `scripts/sync-agents.py` generates `.claude/agents/` and `.opencode/agent/` from it, so edit `agents/` only and rerun the script). Skills: `.claude/skills/` (`auto-dev` = backlog loop, `plan-backlog` = add cards); opencode reads them too.
- `scratch_pad/` is gitignored reference material, not part of the build.

## Commands

```bash
./gradlew lwjgl3:run          # run desktop game (working dir = assets/)
./gradlew test                # all unit tests (CI: ./gradlew clean test)
./gradlew core:test --tests "com.github.pepek42.asteroids.system.MoveSystemTest"   # one test class
./gradlew core:test --tests "*MoveSystemTest.thrustersShouldWork*"                 # one test method
./gradlew lwjgl3:jar          # runnable fat jar -> lwjgl3/build/lib
./gradlew tool:packTextures   # repack raw_assets/*.png -> assets/textures/textures.atlas/.png
./gradlew tool:balanceSim     # headless balance sweep (~2 min), see docs/tech/balance.md; -PbalanceArgs="--seeds=8 --levels=1,2"
./gradlew tool:balanceSmoke   # tagged "balance" smoke test; not part of `test`
./gradlew teavm:generateJavaScript   # web bundle -> teavm/build/dist/js/webapp (~25 s)
./gradlew teavm:javaScriptDevServer  # http://localhost:8080 (URL query = launch flags, e.g. ?seed=42&play)
scripts/web-check.sh --play          # build web + headless Firefox smoke test; prints RESULT and a screenshot path
```

- There is no linter. Formatting follows `.editorconfig`: 4-space indent for Kotlin, 2 for Gradle scripts.
- After changing a sprite in `raw_assets/`, run `tool:packTextures` and commit the regenerated atlas. Code finds sprites by region name in `textures/textures.atlas`.
- Tests use JUnit (Jupiter) + mockito-kotlin. The test classpath includes `assets/` and desktop natives, so tests can use real Box2D/libGDX classes.
- Create and edit files with the editor tools, not shell redirection. Never run a command that waits on stdin with nothing attached (a bare `cat > file`, `python3 -` without a heredoc).

## Git

- All agent work happens on the **`AI-slop`** branch. Never commit to `master` or `stack-update`, and never push.
- One backlog card per commit: `ST-NNN: <card title>`, a blank line, then a one-line summary.

## Web first

`teavm:compileKotlin` passing does **not** mean the web build works: TeaVM finds problems only in `generateJavaScript` or at runtime. `scripts/web-check.sh --play` builds the bundle and runs a level in headless Firefox (see `docs/tech/dev-workflow.md` "Web check"). Web-risk areas:
- **Reflection.** TeaVM keeps reflection metadata only for classes registered in `teavm/build.gradle.kts` (`reflection(...)`, globs; a package needs `.**`). Ashley's `PooledEngine` creates components reflectively, so a component outside `com.github.pepek42.asteroids.component` must be registered. Components have exactly one public no-arg constructor (no constructor parameters, no `data class`), because gdx-teavm picks `getConstructors()[0]`. `ComponentConstructorTest` enforces this.
- **JVM-only APIs.** No threads, `java.io.File`, `System.getenv`, locale tricks in `String.format`, `java.util.concurrent` beyond the basics, or JNI libraries other than gdx/box2d/freetype. Use the libGDX abstractions (`Gdx.files`, `Preferences`, `TimeUtils`).
- **GLSL.** ES 1.00 / WebGL 1 only (`#ifdef GL_ES precision mediump float; #endif`). No `glLineWidth` > 1 or desktop-only GL calls.
- **Performance.** JS is slower than the JVM: no per-frame allocation, and watch Box2D body counts.
- **Launch options.** The web launcher maps the URL query to the same flags (`?seed=42&play` = `--seed=42 --play`, `queryToLaunchArgs`), so a new flag works on web automatically. `screenshot` is desktop-only.

## Architecture essentials

Full detail is in `docs/tech/architecture.md`. These are the invariants every change must respect:

- **DI.** `Game` (a `KtxGame`) loads assets, then registers singletons in a `ktx.inject.Context`. Fetch them with `game.get<T>()`. Gameplay randomness uses the one seeded `RandomXS128` from the context. Screens: `MainMenuScreen` → `PlayScreen` → `LevelClearedScreen` (result and run save). Menus share `ui/MenuStyle`.
- **ECS order.** Systems are registered by `buildGameplaySystems` (`ecs/GameplaySystems.kt`, shared with the headless balance runner). **Registration order is execution order:** time warp → end level → input → enemy AI → move → weapons → contact → boundary field → projectile sweep → relativistic dynamics → light history → physics step → observer → scenario objectives → camera → render → debug/HUD/minimap → lifetime → death → remove.
- **Components.** Each component file defines the class (usually `Pool.Poolable` with a complete `reset()`), `xxxMapper = mapperFor<...>()`, and `Entity.xxxCmp` / `Entity.xxxCmpOptional` via `propertyFor`. Every entity has a `BaseInfoComponent` (id, faction, `EntityType`).
- **Entity creation.** Providers in `provider/` use `engine.entity { with<...> {} }` and `world.body {}`. A body's `userData` is its `Entity` (`ContactSystem` relies on that cast). Collision bits come from `FactionEnum`.
- **Removal.** Never call `engine.removeEntity` for an entity with a body. Add a `RemoveComponent`; `RemoveSystem` (last) destroys the body, nulls `userData` and removes the entity.
- **Physics step.** `PhysicsSystem` steps at `PHYSICS_UPDATE_INTERVAL` on `GameClock.worldDt` (pilot clock, warped by γ). Forces and accelerations are applied in a `PhysicsStepListener.beforeStep`, so they act once per step. Massive bodies carry a `RelativisticComponent`: listeners add proper acceleration to `accel`, and `RelativisticDynamics` integrates `u = γv` and writes `v` into Box2D. `autoClearForces = false`.
- **Rendering** reads `TransformComponent.interpolatedPosition/RotationDeg`, never the raw body position.
- **Input.** Keys and mouse buttons live in the `InputBindings` table (`input/InputBindings.kt`), never as literals. Systems subscribe as listeners (`PlayerInputListener`, …) rather than polling `Gdx.input`.
- **Settings.** Player settings are the `SettingSpec` list in `settings/MenuSettings.kt`, persisted via `KeyValueStore`. Command-line flags win over saved values.
- **Debug logging.** Wrap per-frame logging in `defaultLoggingUtils.tryLogging { ... }` (throttled).
- **Assets.** The brand font (Audiowide, OFL) ships unmodified in `assets/fonts/`. List third-party assets in `CREDITS.md`.
- **i18n.** UI strings go in `assets/i18n/messages.properties` (English only).

## Constants are the single source of truth

The game must keep working when a base constant changes: the tick rate (`PHYSICS_TICK_RATE`), c (`SPEED_OF_LIGHT` / runtime `RelativitySettings.c`), the map size or `UNIT_SCALE` (18 px per world unit). Box2D moves a body at most 2 units per step, so the safe top speed is `C_MAX_BOX2D_SAFE = 0.75 · 2 · tick rate` (90 u/s at 60 Hz), and c (default 20) stays under it.
- Never hardcode a number that follows from a base constant (e.g. `45`, `1/60f`, `20f` for c, `90` for the speed cap). Derive it in code and write the derivation in the KDoc.
- Values that should scale with c (engine acceleration, projectile speeds, speed-dependent zoom, slider ranges) are a fraction or multiple of c, and code reads the runtime `settings.c`.
- Per-step code takes `dt` from the physics step, never a literal.
- Tests use the same constants instead of copying their values, and a test that pins a tuned number says which constant it assumes.
- A new hardcoded derived value is a `major` review issue.
