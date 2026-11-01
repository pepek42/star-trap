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

1. Should black holes move, and may they leave the map? *Recommendation: they're static or slowly drifting, always inside the arena, and the boundary field also acts on them.*
2. What should the HUD time readout show: player proper time, world time, or both? *Recommendation: both, with the ratio γ shown prominently.*
3. Should the effect intensity sliders be exposed to players, or developer-only for now? *Recommendation: developer constants now, with an options menu in M7.*

## 12. Ideas parking lot

- Time-dilation bullet time: going fast gives you "more" time to aim, as a natural slow-mo.
- A "light weapon" fired at exactly c: you can't outrun it, but you can dodge it if you see it coming late.
- A shield that works by Lorentz-boosting incoming projectiles (flavour).
