# Star Trap: Game Design Document

> Living document. The owner's raw wishes live in [todo.md](todo.md). This file turns them into a coherent design. Physics details are in [../research/relativity-effects.md](../research/relativity-effects.md), and implementation work is in [../backlog.md](../backlog.md).

## 1. Pitch

**Asteroids meets Einstein.** You fly a top-down, physics-driven ship in an arena where **the speed of light is slow**: tens of world units per second instead of 300 000 km/s. Engines are absurdly powerful, so a ship gets close to *c* within seconds. From then on, relativity takes over gameplay and visuals. Mass-energy grows, time runs differently for you than for your enemies, the starfield bunches up ahead and shifts blue, and black holes bend light and trajectories around them.

The long-term aim is an **action roguelike**, in the vein of Nova Drift, plus the realistic-ship-handling charm of ΔV: Rings of Saturn.

## 2. Design pillars

1. **Physics first, readable always.** Every effect comes from real formulas (SR/GR, with tuned constants). If an effect hurts readability, we keep the physics and tune the presentation, never the other way round.
2. **Speed is a resource and a risk.** Going fast makes you harder to hit and gives you more time (time dilation). It also distorts what you see, makes turning expensive and delays the information you get.
3. **One arena, no teleports.** The play space is bounded by a soft force field, not a wrap. Everything, bullets included, bounces back gracefully.
4. **Effects are gameplay.** Doppler shift shows who is approaching. Lensing shows where the black hole is. Light delay means you aim at where the enemy *was*.

## 3. Core loop

```
Spawn in arena → clear threats (asteroids → enemy ships) → level cleared
  → upgrade choice (roguelike pick-1-of-3) → next level: more mass, more hazards, new effect
```

A run ends on death. Meta progression is out of scope for the POC.

## 4. Controls (current, kept)

| Input | Action |
|---|---|
| Mouse position | Aim. The ship rotates toward the cursor using thrusters/torque, not instantly |
| RMB | Main thrusters |
| LMB | Fire |
| Mouse wheel | Zoom (0.5–2×) |
| Right Ctrl | Shield/block (placeholder) |

Known problem: the rotation algorithm is wrong (it takes the long way round and feels sluggish). This is fixed in milestone M1. Target feel: responsive but with inertia. The ship turns at up to about 1 rev/s, with a slight settle and no visible overshoot.

## 5. The arena

- 100×100 world units for now (`MapProvider`), with the camera clamped inside.
- **Boundary force field** (replaces `WrapSystem`). A repulsive field gets stronger the closer an object is to the edge, so ships, asteroids and projectiles curve back like a soft bounce. The field is visible: a faint shimmer that brightens where something pushes against it. Projectiles bend back too, and self-destruct on their lifetime timer instead of at a map-exit check. The force law and how it interacts with relativistic momentum are specified by the research doc.
- Later: the arena shape varies per level (circle, ring for race mode, multi-room).

## 6. Relativity: what the player experiences

The full catalogue, with formulas, costs and verdicts, is in [relativity-effects.md](../research/relativity-effects.md). Design intent per tier:

| Tier | Effects | Why |
|---|---|---|
| MVP | Velocity cap via relativistic momentum (γ), player proper time vs world time, Doppler colour shift of the starfield and objects, a HUD with β and γ | The core fantasy: "I'm near c and I can feel it" |
| Next | Aberration (stars and objects crowd toward the direction of motion), searchlight brightness, length contraction of moving objects, light-travel delay (see retarded positions) | Changes how you aim and read the battlefield |
| Showcase | Black holes: gravity, gravitational time dilation, lensing shader, accretion disk with Doppler beaming, photon sphere as a danger zone | The visual "wow" and the end goal of the todo list |
| Maybe | Terrell rotation, frame dragging, relativistic velocity addition for projectiles fired from moving ships | Depends on cost and readability |

Tunables: the speed of light `c` (it has to fit Box2D's ~90 u/s speed cap at 45 Hz, see the architecture doc), engine power, and per-effect intensity sliders. The intensity sliders exist for accessibility and nausea.

## 7. Entities

| Entity | Status | Notes |
|---|---|---|
| Player ship (`disc_green`) | exists | Thrust, laser, health 100 |
| Asteroids | exist | Split LARGE → SMALL. Count grows with level² |
| Laser projectiles | exist | Should travel at a fraction of c, or at c for a "light" weapon |
| Enemy ships | planned | Sprites exist: attacker/tic-tac/triangle/moon-lander in red. Simple steering AI via gdx-ai. Their clocks run at their own proper time |
| Planets | planned | Static or orbiting gravity wells. Mild gravitational time dilation |
| Black holes | planned | Bright accretion disk, lensing. Open question: do they move or leave the map? |
| Pickups/upgrades | planned | Roguelike choices between levels |

## 8. Game modes

- **Battle** (primary): wave/level based, as now.
- **Race** (exploratory): a track defined by gates or a ring arena. Relativity matters a lot here: a near-c racer experiences less time, so "lap time" could be measured in proper time. That way going faster rewards you twice.

## 9. Visual direction

- Dark space, high-contrast sprites (lunar-battle pack), additive glow for engines, lasers and accretion matter.
- **Starfield**: currently unreadable. It will be redone as camera-relative parallax with distinct star points that carry per-star colour temperature. This is a prerequisite for Doppler/aberration, since those effects need individual stars to shift.
- **Minimap**: currently unreadable. It will become a framed, semi-transparent corner panel with faction-coloured dots, the camera view rectangle and the arena boundary.
- Relativistic effects are done through an FBO post-process plus per-sprite shaders (see the architecture doc).

## 10. Milestones

| ID | Milestone | Exit criteria |
|---|---|---|
| M0 | Tooling & stack | New template stack builds, agents and docs in place ✅ |
| M1 | Fix phase | Rotation fixed, minimap and starfield readable, no level leaks, screenshot harness for automated visual checks |
| M2 | Arena field | Wrap teleport replaced by a boundary force field (projectiles too) |
| M3 | Relativity MVP | Relativistic dynamics, proper time, Doppler, a β/γ HUD |
| M4 | Relativistic view | Aberration, searchlight, light delay, length contraction |
| M5 | Gravity & black holes | Gravity wells, black hole with lensing and accretion disk |
| M6 | Enemies | AI ships with their own proper time |
| M7 | Roguelike & modes | Upgrades between levels. Race-mode prototype |

## 11. Open questions (owner)

The planner and auto-dev put new questions here or on `needs-user` backlog cards.

1. ~~Should black holes move, and may they leave the map?~~ *Answered 2026-11-02: they start static and then move under gravity, and the boundary field keeps them inside the arena (see Decisions).*
2. What should the HUD time readout show: player proper time, world time, or both? *Recommendation: both, with the ratio γ shown prominently.*
3. Should the effect intensity sliders be exposed to players, or developer-only for now? *Recommendation: developer constants now, with an options menu in M7.*

### Decisions

- 2026-11-01 (ST-030): The arena is **200×200** with a 10 u field skin from M3 on (owner: "feels a bit chaotic, maybe the arena should be bigger"). ST-046 also lowers the engine proper acceleration to 25 u/s² and rescales the asteroid spawn.
- 2026-11-01 (ST-032): There's **no gameplay γ cap**: "it should be capped by relativity effects themselves". Only a numerical safety bound `GAMMA_MAX_MASSIVE = 50` remains, and it's tunable in the debug panel. In practice the arena and the boundary field limit γ.
- 2026-11-01: The control scheme is **open**. Mouse-hull (today), twin-stick and auto-aim become switchable at runtime (`--controls=`, debug panel) so the owner can try them. The default is decided in ST-063 (recommendation: twin-stick).

- 2026-11-02 (interview): **The game is perceived from the pilot's frame.** Owner: "In relativity there is no world clock... I want the game to be perceived from player time perspective." The game clock is the pilot's proper time τ (research E21): the arena advances γ·dt_real per frame, and guns and turning run normally in τ. Fast-forward is capped at about 10× (declared FAKE past the cap, shown on the HUD). The sim stays in the arena frame. The main screen becomes the pilot view (E8/E9, pulled forward from M4). The minimap stays a "tactical computer" with true arena positions. Aim help is a lead marker. This replaces ST-031.
- 2026-11-02 (ST-033): Ramming: **yes**. Box2D mass = γ·m₀ (capped at 8), and impact damage comes from relativistic kinetic energy.
- 2026-11-02 (ST-034): The default weapon is the **photon laser at c** with muzzle aberration. The slug comes later as an upgrade.
- 2026-11-02: **Braking boost.** Engine proper accel = a0·(1 + k·max(0, −cos θ)), with k = 2 (3× when burning straight against your motion).
- 2026-11-02: **Shots leave the arena.** The boundary field ignores projectiles (no mirrors or bounces). A ripple shows where they cross.
- 2026-11-02: The next big areas are relativistic visuals and gravity/black holes. Black holes are **fixed, 1–2 per level**: gravity bends ships and shots, the horizon kills, and a bright accretion disk makes them readable.
- 2026-11-02 (follow-up, Q1): Black holes, planets and decoration **start static but are dynamic**: they feel gravity from each other (n-body) and from the player, and the boundary field keeps them in the arena; counts stay low for performance. **Lighting:** the accretion disk (not the hole) and a distant star light nearby sprites with falloff and direction, and planets and large asteroids cast shadows; in pilot view, emitter light delay is skipped as a declared approximation.
- 2026-11-02 (ST-092): Source–source gravity is scaled to **0.1** (FAKE). Every deliberate physics departure is listed in [physics-lies.md](physics-lies.md).
- 2026-11-02 (ST-020): c = 60 u/s for now. A headless balance pass (ST-101) picks the final c and a0 so that a careful pilot can survive.
- 2026-11-02 (ST-023): The boundary field is nearly elastic: about 5 % normal-speed loss per bounce, backstop restitution 0.95. The field already stiffens with depth.
- 2026-11-02: The backdrop should be calmer, with orientation aids (reference grid, pointer to the arena centre), ST-100. Code-generated planet sprites are fine as placeholders.

## 12. Ideas parking lot

- Gravitationally balanced level setup: start black holes and planets with initial velocities (bound orbits, enough spacing) so the system is stable at physical coupling 1, replacing the 0.1 FAKE (physics-lies.md row 14).

- Control modes in settings (later): (a) auto-aim + auto-shoot (Brotato / vampire-survivors style, where the player mostly manages momentum) and (b) slow-mo aim: holding LMB strongly slows time (a declared FAKE "targeting trance"), with a lead indicator and an instant turn toward the aim.

- Time-dilation bullet time: going fast gives you "more" time to aim, as a natural slow-mo.
- A "light weapon" fired at exactly c: you can't outrun it, but you can dodge it if you see it coming late.
- A shield that works by Lorentz-boosting incoming projectiles (flavour).
