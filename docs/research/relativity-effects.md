# Relativity effects for Star Trap

> Research doc by `physics-researcher`. Status: v1, 2026-11-01. This is the single source for the physics behind M2 (arena field), M3 (relativity MVP), M4 (relativistic view) and M5 (gravity and black holes). The planner turns it into cards (ST-014, ST-015). It contains no game code. The snippets are pseudocode to pin down formulas and ordering.
>
> Honesty labels used throughout:
> - **[EXACT]** means the formula is the real special or general relativity result, under the observer convention stated.
> - **[APPROX]** means it's a physically motivated approximation, with the error stated.
> - **[FAKE]** means it's fake but evocative: a deliberate artistic choice that is not what any observer would see.

## TL;DR

| Topic | Recommendation |
|---|---|
| Speed of light | `SPEED_OF_LIGHT = 60` u/s at the current 45 Hz. That is 1.33 u per step, 67 % of Box2D's 2 u/step cap. Keep `PHYSICS_SUBSTEPS` as a knob for later. Drop the current 250. |
| Dynamics | Integrate **proper velocity** `u = γv` ourselves, once per physics step, and write `v = u/γ` into Box2D. Box2D keeps collisions only. γ is always computed from `u`, never from `v`, so it can't overflow or go NaN. |
| Arena | The force field replaces the wrap. It is a mass-independent "electrostatic" wall that pushes on `u`, with a hard mirror backstop and a shimmer shader. Projectiles die on a **lifetime** clock (proper time for slugs, world time plus a bounce count for photons). |
| MVP (M3) | Relativistic momentum (velocity cap), per-entity proper time, relativistic velocity addition for projectiles, Doppler colour (starfield and sprites), length contraction in the world view, a β/γ/τ HUD, γ-mass collisions, speed zoom. |
| Next (M4) | "Pilot view": light-travel delay from a history buffer plus a Lorentz boost into the player frame. Together these give aberration, apparent stretching, Terrell-like distortion, the searchlight effect, and apparent animation rate. Also Doppler-scaled photon damage and AI that sees the player's past. |
| Showcase (M5) | One gravity law, Schwarzschild-geodesic exact in orbit *shape*, for planets and black holes. Plus photon bending, gravitational time dilation, a black hole with horizon, photon-sphere and ISCO rings, a particle accretion disk, and a thin-lens backdrop lensing pass. |
| Skip | Kerr frame dragging, Thomas–Wigner rotation, an exact relativistic collision solver, a co-moving camera snapshot. |

---

## 0. Conventions, frames and observers

### Symbols

| Symbol | Meaning |
|---|---|
| `c` | Speed of light in world units per second (u/s). Proposed value 60. |
| `t` | World (arena) coordinate time. This is Box2D's clock and the wall clock. |
| `τ` | Proper time of an entity (its own clock). |
| `v` | Velocity in the world frame W (u/s). `β = v/c` (vector), `β = |β|` |
| `γ` | Lorentz factor `1/√(1−β²)`. In code, always `√(1 + |u|²/c²)` |
| `u` | Proper velocity `γv = dx/dτ`. Unbounded, so it is our integration state |
| `η` | Rapidity `asinh(|u|/c) = atanh(β)`. Additive for collinear boosts |
| `m₀` | Rest mass (what Box2D's density gives today) |
| `p` | Momentum `m₀u = γm₀v`. Energy `E = γm₀c²`, kinetic `K = (γ−1)m₀c²` |
| `n̂` | Unit propagation direction of a light ray (source → observer) |
| `D` | Doppler factor `f_observed / f_emitted`. `D > 1` is a blueshift |
| `r_s` | Schwarzschild radius, `GM = r_s c²/2` |
| `∥, ⊥` | Components parallel and perpendicular to the relevant velocity |

### Who is observing? (read this first)

The game world is a 2D plane, and **light travels in the plane**. The screen is a top-down picture. We use three observers and must always say which one an effect belongs to.

1. **W, the arena frame.** Box2D integrates everything in W, with coordinate time `t`. The level and asteroid spawns live here.
2. **P, the pilot frame.** This is the player ship's *instantaneous* rest frame (a momentarily co-moving inertial frame). The pilot's eye sits in the plane, so it sees only in-plane light: a 1D ring of directions, each with an arrival time.
3. **C_W, the world camera.** This is the current renderer. Physically it's a distant observer far above the plane, at rest in W. Light from every point of the plane travels the same distance (straight up) to it, so it sees a **W-simultaneous snapshot**, which is exactly the Box2D state. The camera *pans* to follow the player, but we define it as a family of W-rest observers, not a moving observer. This makes it causal and exact.

From these we get two rendering modes:

- **World view (default, M3).** This is what C_W sees: Box2D positions. Two effects are exact here. Moving objects are **length-contracted** (a flat in-plane object seen face-on from far away shows its W-simultaneous shape, so there is no Terrell rotation for this camera). The camera also sees transverse Doppler (`1/γ`), which is negligible for gameplay. On top of that, we tint colours with the **pilot's** Doppler factor. This is a declared hybrid: positions are from C_W, colours are from P's eye. The Doppler factor itself is exact for the ray arriving at the pilot, but the object is drawn at its W position, not its apparent position.
- **Pilot view (M4).** This is what the pilot's in-plane eye records, drawn as a map around the ship. Every object is drawn at the **spatial coordinates, in P, of the event whose light reaches the pilot now**. The apparent distance is `c ×` (light-travel time measured in P). This is the same convention OpenRelativity uses for 3D vertices (Sherin et al. 2016, eqs. 6–8). It's [EXACT] as a map of emission events, and it's the only consistent way to draw "what the pilot sees" on a 2D screen. Velocity Raptor faces the same problem: it's a bird's-eye game, "so the distance needs to be drawn right on the screen" (TestTubeGames FAQ, via search snippet).

A third option, a distant camera **co-moving with the player**, would see a P-simultaneous snapshot. That needs the *future* W state of objects ahead of the player (`Δt = β·Δx/c`, up to about 0.75 s at β = 0.9 over 50 u). **Skip it.**

---

## 1. Choice of `c` and the Box2D speed cap

### The constraint

Box2D clamps each body's translation per step to `b2_maxTranslation = 2.0` length units. This is a **compile-time constant** in the native library (`b2Settings.h`/`b2_common.h`, "not meant to be adjusted"), so we can't change it from Kotlin without rebuilding the gdx-box2d natives. At 45 Hz that gives `v_max = 2 × 45 = 90` u/s. The clamp also **rescales the body's velocity**, which destroys momentum. So we must design so it never fires in normal play.

General rule, with `f` the step rate, `n` the substeps per step and `η` a safety margin for collision transients:

```
c ≤ η · b2_maxTranslation · f · n        with η ≈ 0.75
45 Hz, n=1:  c ≤ 67 u/s      45 Hz, n=2 (or 90 Hz): c ≤ 135 u/s
```

### Candidates

Here "Screen-edge delay" is the light delay from the edge of a 48-unit-wide view at zoom 1 (24 u).

| `c` (u/s) | u per step at 45 Hz | Headroom | Screen-edge delay | Crossing a 100 u arena at c | Verdict |
|---|---|---|---|---|---|
| 40 | 0.89 | 2.25× | 0.60 s | 2.5 s | Works. Very "slow light", but projectiles at c feel sluggish |
| **60** | **1.33** | **1.5×** | **0.40 s** | **1.7 s** | **Recommended**. Clearly visible delay, fast bullets, no Box2D change |
| 80 | 1.78 | 1.1× | 0.30 s | 1.25 s | Collision recoil gets clamped. Needs `n = 2` or 60 Hz |
| 250 (current) | 5.6 | 0.36× | 0.10 s | 0.4 s | Needs ≥ 170 Hz. Light-delay effects become invisible at screen scale. Reject |

### Recommendation

- `SPEED_OF_LIGHT = 60f`. Keep `PHYSICS_UPDATE_INTERVAL = 1/45`.
- Add `PHYSICS_SUBSTEPS = 1`. With `n > 1`, `PhysicsSystem` calls the per-step hooks and `world.step(dt/n)` `n` times per tick. This is the one knob if the owner later wants `c > 67`. Cost: Box2D CPU scales linearly with `n`, which is trivial at our body counts on desktop and worth checking on TeaVM.
- Every massive body is capped at `β_max = √(1 − 1/γ_max²)` (0.995 for γ_max = 10). Projectiles are slugs below c or photons at exactly c, which is 1.33 u/step. The Box2D clamp then only fires for transient Newtonian collision outcomes, such as a light body hit by a heavy near-c body, which can momentarily come out near 2c. We re-clamp those ourselves in `afterStep` (§2).
- **Don't** rescale Box2D units against world units (`BOX2D_UNITS_PER_WORLD_UNIT`). It works in principle, since the cap applies in Box2D units, but it touches every provider, the camera and the debug renderer, for no benefit over `c = 60`.
- **Tunneling.** At c, a projectile moves 1.33 u per step. A 1.33 u ship hit off-centre can be skipped, because sensors don't use CCD and `bullet = true` has no effect on kinematic sensor bodies. Recommend a **swept ray test** per projectile per step (`world.rayCast(prev, next)`), which is cheap. It's part of the projectile card in M3.
- Set all projectile speeds as **fractions of c**, not absolute numbers. The current `projectileSpeed = SPEED_OF_LIGHT = 250` is silently clamped to 90 today.

### Scale consequences, and why engine thrust should be a proper acceleration

Today's ship has mass `m₀ = 1.33² × 20 ≈ 35.6`. Its thrust is 300, so it accelerates at about 8.4 u/s². That's far too weak to reach relativistic speeds. Instead, specify engines by their **proper acceleration** `a₀` (mass-independent, which also makes upgrades easy to balance).

For constant proper acceleration from rest (hyperbolic motion) [EXACT]:

```
u(t) = a₀ t          β(t) = (a₀t/c) / √(1 + (a₀t/c)²)
x(t) = (c²/a₀)(γ − 1)          τ(t) = (c/a₀) asinh(a₀t/c)
```

With `a₀ = 60 u/s²` (that is, "1 c per second"):

| Target | World time | Proper time | Distance |
|---|---|---|---|
| β = 0.71 (γ = 1.41) | 1.0 s | 0.88 s | 25 u |
| β = 0.90 (γ = 2.29) | 2.1 s | 1.47 s | 77 u |
| γ = 5 (β = 0.98) | 4.9 s | 2.3 s | 240 u |

So a 100 × 100 arena only reaches about 0.85c in a straight line from the centre. You can climb higher by bouncing off the field and orbiting, but the arena feels cramped near c. See open question Q2 (recommend 200 × 200 from M3 on).

---

## 2. Relativistic momentum on top of Box2D

### Physics [EXACT for free flight and field forces]

```
State per body:   u (proper velocity, vector), m₀, τ
γ(u)   = √(1 + |u|²/c²)               ≥ 1, finite for any finite u
v      = u / γ(u)                      |v| < c for any finite u
p      = m₀ u
dp/dt  = F_W              ⇒   du/dt = F_W / m₀ = a_W
dτ/dt  = 1/γ
```

**Thrust is defined in the ship's frame.** The engine produces a proper force `f' = m₀ a₀ n̂'` in P. The world-frame force comes from the force transformation for a body momentarily at rest in P: `F_∥ = f'_∥` and `F_⊥ = f'_⊥ / γ`. So:

```
a_thrust,W = a₀ [ (n̂'·v̂) v̂  +  (n̂' − (n̂'·v̂) v̂) / γ ]
```

Consequences the player feels:
- Acceleration along the direction of travel falls as `dv/dt = a₀/γ³` (the "longitudinal mass" `γ³m₀`). You approach c ever more slowly and never reach it.
- Turning the velocity vector costs `dφ/dt = a₀ / (γ² v)`. **Near c, turning is γ² times harder.** This is the core "speed is a risk" mechanic.
- The pilot's clock runs at `1/γ`. See E2 for what that drives.

### Integration pipeline (per physics step, inside `PhysicsSystem`'s accumulator loop)

```
beforeStep(dt):                         // per relativistic body
    a = 0
    a += thrust (above), boundary field (§3), gravity (E14)
    u += a·dt                           // semi-implicit Euler, fine at 45 Hz
    if |u| > U_MAX: u *= U_MAX/|u|      // U_MAX = c·√(γ_max² − 1)
    g = √(1 + |u|²/c²)
    v = u / g
    body.setLinearVelocity(v);  vSet = v;  gUsed = g
    if GAMMA_MASS: body.setMassData(m₀·min(g, GAMMA_MASS_CAP), I₀·min(g, …))   // E7

world.step(dt / n, 6, 2)                // n = PHYSICS_SUBSTEPS (loop)

afterStep(dt):
    vb = copy(body.linearVelocity)      // Box2D returns a shared Vector2, so copy it!
    if |vb − vSet| > COLLISION_EPS:     // a contact changed the velocity
        vb = clampSpeed(vb, β_max·c)
        u = (GAMMA_MASS ? gUsed : gammaFromV(vb)) · vb   // see E7
    τ += dt / gUsed  (× gravitational factor, E15)
    record history sample (M4)
```

Notes for the implementer:
- Linear forces no longer go through Box2D. There's no `applyForceToCenter` for thrust, the field or gravity, and the world has zero gravity. This removes the `moveForcesApplied` / `clearForces` dance described in the architecture doc. Keep calling `world.clearForces()` after each step, as a safety measure.
- **Kinematic projectiles.** Box2D ignores forces on kinematic bodies, so `setLinearVelocity` is the only way to move them anyway. The same integrator works.
- **Photons** (light-speed projectiles) don't carry `u`, since it would be infinite. They store a unit direction `n̂` and move at exactly `c`. Fields and gravity only rotate `n̂` (§3, E14).
- Angular motion stays in Box2D. Rotation isn't relativistic in our model, except that the pilot's clock slows it (E2).
- Set `allowSleep = false` on all relativistic bodies. Asteroids already do this.
- Interpolation in `PhysicsSystem.interpolateTransforms` is unaffected.

### Numerically safe γ (pure functions, unit tested)

Put them in a top-level `physics/Relativity.kt` with no GL dependency. Compute in `Double` internally and store `Float` in components.

```
gammaFromU(ux, uy, c)       = sqrt(1 + (ux²+uy²)/c²)               // no cancellation, never < 1
velocityFromU(u, c)         = u / gammaFromU(u)                      // |v| < c always
gammaFromV(vx, vy, c, βmax) = 1 / sqrt(1 − min((vx²+vy²)/c², βmax²))  // denominator ≥ 1/γmax
uFromV(v, c, βmax)          = gammaFromV(clampSpeed(v)) · clampSpeed(v)
clampU(u, γmax, c)          = u · min(1, c·sqrt(γmax²−1)/|u|)
rapidity(u, c)              = asinh(|u|/c)
```

Rules:
1. **Never derive γ from `v` on the hot path.** `1 − β²` at β ≈ 0.999 loses most float digits. γ from `u` has no cancellation.
2. `gammaFromV` only runs after Box2D changes a velocity, and it always clamps `β ≤ β_max` first.
3. Guard against NaN. If any input is NaN or infinite, log once via `defaultLoggingUtils.tryLogging`, reset `u = 0`, and continue. Unit tests should check: random huge `u` gives `|v| < c`; `γ ≥ 1`; the round trip `u → v → u` holds within 1e-4 relative error for `γ ≤ γ_max`; velocity addition never exceeds c; photon addition gives exactly `|v| = c`.
4. `γ_max` is enforced by clamping `u` (`GAMMA_MAX_MASSIVE = 10`, `β_max = 0.99499`). Projectile slugs get `GAMMA_MAX_PROJECTILE = 20`.

---

## 3. Arena boundary force field

**Replaces** `WrapSystem`/`WrapComponent` (M2). Arena: `[0, W] × [0, H]`, currently 100 × 100.

### Force law

For each wall `i` with inward normal `n̂ᵢ`, let `dᵢ` be the distance from the body's centre to the wall, minus the body radius `r_b` when `FIELD_USE_BODY_RADIUS` is set. Skin width is `s`.

```
qᵢ = clamp((s − dᵢ)/s, 0, 1)            // 0 at the inner edge of the skin, 1 at the wall
a_field = Σᵢ A · qᵢⁿ · n̂ᵢ               // acceleration, not force: same for every mass
        − Σᵢ k_d · qᵢ · min(0, u·n̂ᵢ) · n̂ᵢ    // optional damping, only while moving outward
```

At corners two walls add up, giving a diagonal push, which is fine.

- **It's an acceleration field, not a force field.** Asteroids, ships and bullets of any mass bounce at the same depth for the same speed. (Physics analogue: a static electric field acting on bodies with a fixed charge-to-mass ratio.)
- **Relativistic behaviour [EXACT].** Applied as `du/dt = a_field`, this is exactly a charged particle in a static electric potential. The potential per unit rest mass is `Φ(q) = A s q^(n+1)/(n+1)`. Momentum along the wall is conserved, so a body is turned around at the depth where `c²(γ₀ − γ_turn) = Φ(q)`, with `γ_turn = √(1 + u_∥²/c²)`. In the Newtonian limit this is `v_n²/2 = Φ(q)`. A finite potential **can't stop arbitrarily energetic bodies**, which is why there's a backstop.
- **Hard backstop.** If after a step `dᵢ ≤ 0` (the centre is at or past the wall), `setTransform` the body to 0.01 u inside and reflect the normal component: `u_n → −e · u_n` with `e = FIELD_BACKSTOP_RESTITUTION`. Fire a strong shimmer impact and log it at debug level, throttled. This should be rare. If it isn't, raise `A`.
- **Photons** pass through electric fields, so the field doesn't bend them. Rule [FAKE]: photons reflect specularly (`n̂ → n̂ − 2(n̂·n̂ᵢ)n̂ᵢ`) at the mirror depth `q = FIELD_PHOTON_MIRROR_Q`, spend one bounce, and trigger a small shimmer impact.

### Tuning (starting values, with `c = 60`)

| Constant | Start | Reasoning |
|---|---|---|
| `FIELD_SKIN` `s` | 8 u | Visible on a 48 u screen. 16 % of a 100 u arena is lost to the skin on each axis. In a 200 u arena it can be 10 |
| `FIELD_EXPONENT` `n` | 2 | Gentle onset for slow bodies, steep near the wall |
| `FIELD_ACCEL_MAX` `A` | 3000 u/s² | A head-on body at β = 0.95 (γ = 3.2) turns exactly at the wall: `A = 3·c²(γ−1)/s ≈ 2970` |
| `FIELD_DAMPING` `k_d` | 1.0 s⁻¹ | Removes about 10–20 % of normal speed per bounce, so the arena doesn't feel like a pinball machine. 0 means lossless |
| `FIELD_BACKSTOP_RESTITUTION` `e` | 0.9 | |
| `FIELD_PHOTON_MIRROR_Q` | 0.5 | Photons visibly reflect mid-skin |
| `FIELD_USE_BODY_RADIUS` | true | Large asteroids don't half-poke out of the arena |

Resulting turn-around depths with `A = 3000`, `s = 8`, `n = 2`:

| Body | Required Φ (u²/s²) | Turn-around depth |
|---|---|---|
| Asteroid at 10 u/s | 50 | q = 0.18, about 1.5 u |
| Slug at β = 0.7 | 1440 | q = 0.57, about 4.5 u |
| Ship at β = 0.9 | 4660 | q = 0.84, about 6.7 u |
| Ship at γ > 3.2 | | Backstop |

Per-step safety: the largest kick is `A·dt = 67` u/s of `u`, only at `q = 1`. Slow bodies only see `a ≈ A q² ≈ 100` u/s², about 2 u/s per step, so it's smooth.

### Box2D interaction (M2, before relativity exists)

1. Add a **per-step hook** to `PhysicsSystem`: `interface PhysicsStepListener { fun beforeStep(dt: Float); fun afterStep(dt: Float) }`. The loop becomes: `beforeStep` → `world.step` → `afterStep` → `world.clearForces()` **on every step**. This replaces "apply once per frame, clear after the loop".
2. Move the thrust application out of `MoveSystem.update` into a step listener, or have `MoveSystem` only write intents that a listener applies. Delete the `moveForcesApplied` flag.
3. `BoundaryFieldSystem` implements the listener and is registered with `PhysicsSystem`. Put it in the system list before `PhysicsSystem` for clarity, even though it runs from the hook. In M2 (Newtonian), for each body:
   - dynamic body: `applyForceToCenter(m·a_field)`, or equivalently `applyLinearImpulse(m·a·dt)`;
   - kinematic body (projectiles): `setLinearVelocity(v + a·dt)`, because Box2D ignores forces on kinematic bodies.
   In M3 it just adds `a_field` to the relativistic accumulator (§2).
4. `afterStep`: run the backstop check.
5. Remove `WrapSystem`, `WrapComponent` and its uses in the providers, and `WeaponType.trapped`. The field never applies torque.
6. Pure function `boundaryAcceleration(x, y, rBody, mapW, mapH, params): Vector2` (output into a pre-allocated vector) with unit tests: zero in the interior; symmetric; monotonic in depth; sums at corners; turn-around depth matches the table above within one step.

### Projectiles: lifetime instead of out-of-bounds removal

- New `LifetimeComponent { remaining: Float, clock: WORLD | PROPER, bouncesLeft: Int }` and a `LifetimeSystem` (per frame, before `DeathSystem`). It subtracts `dt` (WORLD) or `dt/γ` (PROPER), and adds `RemoveComponent` when the time or bounces run out. Add a short fade so the projectile doesn't just vanish.
- Starting values: lasers (photons) `2.0 s` WORLD and 3 bounces (that's 120 u of travel at `c = 60`). Slugs (M3) `1.2 s` PROPER. A slug at β = 0.95 then lives 3.8 s of world time and flies 220 u. That's the muon-lifetime effect, physically [EXACT] and good gameplay: faster rounds reach further. In M2, before relativity, every lifetime is WORLD.
- `RemoveSystem` out-of-bounds tagging stays only as a **safety net**, with the margin raised from 5 % to 20 %. Projectiles and asteroids are removed with a warning log. The **player is never removed**: it's `setTransform`ed back inside.
- `CameraSystem` clamp: allow the view to extend `s/2` past the arena edge so the shimmer is visible. The minimap draws the arena rectangle and the skin.

### Shimmer visual

- Geometry: one quad covering the arena plus a margin, or four edge strips, drawn after sprites with additive blending.
- Fragment shader (GLSL ES 1.00, TeaVM-safe):
  - `d` is the distance to the nearest wall and `q = clamp((s−d)/s)`.
  - Base: `BASE_ALPHA · q² · (0.6 + 0.4·hexNoise(worldPos, time))`, a faint cyan honeycomb.
  - Impacts: `uniform vec4 u_impacts[16]` holds `(x, y, startTime, strength)`. For each impact, with `ρ` the distance to the impact point and `age = t − t₀`:
    - expanding ring: `strength·exp(−((ρ − c·age)/w)²)·exp(−age/τ_r)`. **The ripple runs at c**, a free lesson in causality;
    - hot spot: `strength·exp(−ρ²/σ²)·max(0, 1 − age/0.2)`.
  - Colour cyan → white with strength.
- CPU side: `BoundaryFieldSystem` emits impacts for bodies with `q > 0.1`, with strength `qⁿ`. A body that stays in the field refreshes its impact at most every 0.25 s. Keep the 16 most recent or strongest. Backstop hits and photon reflections are strong impacts.
- In pilot view (M4), the strips are subdivided into about 64 segments per edge and their vertices go through the static-point transform (E9). The walls then visibly bend and stretch.
- Fallback if shaders slip: a `ShapeRenderer` rectangle with alpha pulses (S).

**Cost:** CPU S (one loop over bodies per step), GPU S (one quad, 16-iteration loop on band pixels). Shader complexity M. **Gameplay:** high. It removes the confusing teleport, allows bank shots with bouncing bullets, and visibly shows you where the wall is. **Verdict: M2.**

---

## 4. Effect catalogue

Each entry covers: **Physics** · **What the player sees** · **Implementation** · **Cost** · **Gameplay** · **Verdict**.

### Tier 1: MVP (milestone M3)

#### E1. Relativistic momentum: the speed of light is a wall you can approach but never touch

- **Physics:** §2. `p = γm₀v`, thrust transformed with `F_∥ = f'_∥`, `F_⊥ = f'_⊥/γ`. [EXACT] in free flight.
- **Player sees:** strong acceleration at first, then a "soft wall" as β → 1. Turning gets heavy at speed (γ²). Speed readouts creep toward 0.99c but never reach 1.
- **Implementation:** `RelativisticComponent { u, m₀, τ, gUsed, vSet }` plus a step listener `RelativisticDynamics` (§2). Engines: `MoveComponent` gets a commanded proper acceleration. The `MAIN_ENGINE_THRUST` force becomes `ENGINE_PROPER_ACCEL`. Touches `PhysicsSystem` (hooks), `MoveSystem` (intent only), and all providers (attach the component, set `u` from the initial velocity).
- **Cost:** CPU trivial (a few flops per body per step). Complexity M.
- **Gameplay:** core. Reaching speed takes commitment, and changing course at speed is the risk.
- **Verdict: MVP.** `ENGINE_PROPER_ACCEL = 60`, `REVERSE_THRUST_FRACTION = 0.5`, `GAMMA_MAX_MASSIVE = 10`.

#### E2. Proper time and time dilation (per-entity clocks)

- **Physics:** `dτ/dt = 1/γ` [EXACT]; times a gravitational factor in M5 (E15). A clock that moves through the arena and returns ages less: the twin effect, exact in W.
- **Player sees:** the HUD shows world time and ship time drifting apart. Everything the ship does by its own clock slows as seen from the arena: weapon cooldown, shield regen, and (tunably) turning speed. Enemies flying fast fire less often. Asteroids spinning on their own clock spin `ω₀/γ` slower when fast [EXACT].
- **Implementation:** `τ` lives in `RelativisticComponent`. Introduce `dtProper = dt/γ` and use it in `WeaponSystem` (cooldown), the future shield and AI think timers, and `LifetimeSystem`. Rotation: `MoveSystem` sets `angularVelocity = ω_cmd / γ^ROTATION_DILATION_EXPONENT`. Asteroids store a proper spin `ω₀` and get `ω₀/γ`.
- **Cost:** trivial, S.
- **Gameplay:** a real trade-off: speed protects you (harder to hit, see E9) but slows your own fire rate. Race mode can score lap time in proper time, which rewards speed twice.
- **Physically honest note on "bullet time":** going fast does **not** slow the world down for the pilot. The world ages γ× *faster* per pilot second. The parking-lot idea of "fast = slow-mo" is the wrong way round physically. It can only be had as a [FAKE] ability, or by making the game clock follow pilot time (E21, which makes things *harder*).
- **Verdict: MVP.** `COOLDOWN_DILATION_EXPONENT = 1.0` (exact). `ROTATION_DILATION_EXPONENT = 0.5` (softened for feel; 1.0 is exact). See Q4.

#### E3. Relativistic velocity addition for projectiles (and photon aberration at the muzzle)

- **Physics [EXACT].** The muzzle velocity `w'` is given in the shooter's frame P. In W, using proper velocities to avoid division problems:
  ```
  u'_w = γ_w' w'          γ_w' = 1/√(1 − w'²/c²)
  boost by shooter velocity v (γ = γ(v), v̂):
  u_∥ = γ (u'_w,∥ + γ_w' v)        u_⊥ = u'_w,⊥
  ```
  Equivalently, `v_proj = (w'_∥ + v + w'_⊥/γ) / (1 + v·w'/c²)`. For **photons** (`w' = c`, direction `n̂'` in P), the speed stays `c` and the direction aberrates:
  ```
  n_∥ = (n'_∥ + β) / (1 + β n'_∥)      n_⊥ = n'_⊥ / (γ (1 + β n'_∥))
  ```
- **Player sees:** bullets fired forward at speed only gain a little speed, and never exceed c. Shots fired sideways at speed are swept forward. Lasers always fly at exactly c.
- **Implementation:** `WeaponProjectileProvider` uses `Relativity.addVelocity(v_ship, w')` and `Relativity.aberratePhoton(β_ship, n̂')`. This fixes the existing bug where projectiles ignore the ship's velocity entirely: today a ship at 0.9c would outrun its own bullets. In M3, interpret aim angles in W. In M4 pilot view, aim angles come from the P-frame screen, which is exactly what this formula expects. Optional recoil: `Δu_ship = −(m_proj/m₀)·u_proj`, or photon momentum `E/c`. Default off.
- **Cost:** trivial, S.
- **Gameplay:** required for correctness, because Galilean addition breaks the c cap the moment a ship moves fast. **This promotes the game-design doc's "Maybe" item to MVP.**
- **Verdict: MVP.** `LASER = photon at c`; later `SLUG_SPEED_FRACTION = 0.7` (in the shooter frame).

#### E4. Doppler colour shift (starfield and sprites)

- **Physics [EXACT]** for the ray arriving at the pilot. With source velocity `β_s`, observer (pilot) velocity `β_o`, and `n̂` the propagation direction from source to observer in W:
  ```
  D = γ_o (1 − β_o·n̂) / ( γ_s (1 − β_s·n̂) )
  static star seen by a moving pilot:  D = γ (1 + β cos θ)   θ = angle between v and the direction to the star
  ```
  Head-on at β = 0.9, `D = 4.36`; straight behind, `D = 0.23`. These match Kraus (2000), who uses `d = 1/(γ(1 − β cos θ))` with `θ` measured at the source. A blackbody of temperature `T` is seen as a blackbody of temperature `D·T` [EXACT] (Kraus 2000, eqs. 1–3).
- **2D note:** these formulas are dimension-independent, since they only involve the plane containing `v` and the ray.
- **Player sees:** the part of the sky you fly toward turns blue-white, and the part behind turns red and dim. An enemy rushing at you looks blue, one fleeing looks red. That's a free threat readout.
- **Implementation:**
  - **Starfield** (depends on the M1 starfield redo). Each star gets a rest temperature `T` (2,500–30,000 K, log-uniform). It's drawn as a point or small quad in a custom mesh whose shader has uniforms `β_vec`, `γ`, and the player screen position. Per vertex: `D = γ(1 + β·ŝ)`, where `ŝ` is the unit vector from the player to the star on screen [FAKE mapping, EXACT D]. The vertex shader looks up `rgb = blackbodyLUT(D·T)` (a 1D texture, 1000–60000 K, precomputed offline from CIE 1931 colour matching or a published blackbody table). Two layers:
    1. an in-plane **dust/beacon layer** at real arena positions (thousands of static points). Physically exact in pilot view (M4);
    2. a far **parallax backdrop** whose angle from the player stands in for a 2D-universe star direction [FAKE projection].
  - **Sprites** (MVP): a CPU per-sprite tint `blackbodyRGB(T_ref·D)/blackbodyRGB(T_ref)` with `T_ref = 6500 K`, which shifts the white point [FAKE but evocative]. `SpriteBatch.setColor` can't exceed 1, so for `D > 1` draw a second additive pass with alpha `clamp(D^k − 1)`. Later (E26): a spectral IR-R-G-B-UV shader like OpenRelativity's.
  - In MVP, `n̂` comes from **current** W positions (no light delay). That's [APPROX], and exact for static objects only in the limit of zero delay. M4 swaps in retarded positions.
  - Low-pass the visual β with `VISUAL_BETA_SMOOTHING = 0.15 s` so field bounces don't flash the whole screen.
- **Cost:** GPU trivial (per-vertex LUT). CPU S. Complexity M, since it needs the starfield redo and the LUT.
- **Gameplay:** high readability value: approaching versus receding, and which way you're going. Risk: colour-blind players, so pair it with brightness (E10) and keep HUD arrows.
- **Verdict: MVP.** `DOPPLER_COLOR_STRENGTH = 1.0` (multiplies `ln D`), `DOPPLER_D_CLAMP = [0.25, 4]`.

#### E5. Length contraction (world view)

- **Physics [EXACT for C_W]:** a body moving at `v` in W has its extent along `v̂` contracted to `L₀/γ`. The world camera sees this directly, because every point of a flat in-plane object is the same distance from it (§0).
- **Player sees:** your own ship and fast enemies flatten along their direction of motion. At γ = 2.3, they're 44 % as long. Asteroids at rest stay round. It's an instant speedometer.
- **Implementation:** `RenderSystem` draws each sprite from four transformed corners, using `batch.draw(texture, float[20] vertices, …)`, instead of `sprite.draw`. For each corner offset `o` (rotated local corner): `o' = o − (1 − 1/γ)(o·v̂)v̂`. No shader is needed. `CONTRACTION_STRENGTH` blends `1/γ` toward 1.
- **Hitbox:** physically, the Box2D fixture should contract too. Rebuilding fixtures is M-sized work (circles become 8-vertex polygons). Defer to E20.
- **Cost:** CPU trivial, S.
- **Gameplay:** mostly feedback. A small mismatch with the uncontracted hitbox is tolerable.
- **Verdict: MVP.** `CONTRACTION_STRENGTH = 1.0`.

#### E6. HUD: β, γ, τ, t (plus speed zoom)

- **Shows:** `β` as a bar driven by **rapidity** `η/η_max`, so it keeps moving near c. `β` as a number to 3 decimals. `γ`. Ship time `τ` and world time `t`, both, with their ratio prominent (this answers the game-design doc's Q2). Optionally `D_front`.
- **Camera speed zoom:** at c = 60, the screen half-width is crossed in 0.4 s. Add `zoom_target = base · (1 + CAMERA_ZOOM_PER_BETA2 · β²)` with 0.6 to start, smoothed, inside the existing 0.5–2 range, multiplied by the wheel zoom.
- **Implementation:** `HudSystem`/`Hud.kt`, i18n keys in both `messages*.properties`. `CameraSystem` handles the zoom. **Cost:** S. **Verdict: MVP.**

#### E7. Relativistic mass in collisions, and impact energy

- **Physics:** Box2D's contact impulses conserve `Σ Mᵢvᵢ`. If we set each body's Box2D mass to `Mᵢ = γᵢm₀ᵢ` (frozen for the step), the impulses conserve **relativistic momentum** `Σ γᵢm₀ᵢvᵢ` [EXACT for momentum, to first order]. Energy is only conserved in the Newtonian sense [APPROX]. Read back `u = γ_used·v_new` (momentum-consistent). Impact energy for damage [EXACT]:
  ```
  γ_rel = γ_a γ_b (1 − v_a·v_b/c²)        K_rel = (γ_rel − 1) m_target c²   (or reduced mass)
  ```
- **Player sees:** a near-c ship hits like a freight train ("mass goes to infinity", straight from the owner's todo). Head-on collisions hurt far more than overtaking ones.
- **Implementation:** `body.setMassData` in `beforeStep` when γ has changed by more than 1 % (reuse one `MassData`). Cap at `GAMMA_MASS_CAP = 8` to keep Box2D's mass ratios sane. `ContactSystem` damage uses `K_rel` × `DAMAGE_PER_ENERGY`. That needs both velocities at contact time, so cache `vSet` (pre-step) per body.
- **Cost:** S.
- **Gameplay:** high. Ramming becomes a weapon and speed becomes dangerous to yourself. Risk: asteroids pinballing off a near-c player at up to 2c in Newtonian terms. That's handled by the clamp in `afterStep`.
- **Verdict: MVP (stretch: last M3 card).** An exact relativistic collision solver is E25 (skip).

### Tier 2: Next (milestone M4, "relativistic optics")

#### E8. Light-travel delay (retarded positions)

- **Physics [EXACT].** The pilot at event `(t, x_p(t))` sees object `o` as it was at the retarded time `t_r`, which solves:
  ```
  |x_p(t) − x_o(t_r)| = c (t − t_r)          unique root for subluminal motion
  ```
  This uses a **history buffer**. For an object with constant velocity there's a closed form (OpenRelativity eqs. 6–7), with `r₀ = x_o(t) − x_p(t)` and `T = t − t_r`:
  ```
  T = [ −(r₀·v_o) + √((r₀·v_o)² + (c² − v_o²)|r₀|²) ] / (c² − v_o²)
  ```
- **Player sees:** at c = 60, something 24 u away is seen 0.4 s late. You see where enemies *were*, so you must lead your shots. Newly spawned fragments "pop in" only when their light arrives. Explosions are seen late.
- **Implementation:**
  - A `LightHistory` store, kept **outside** Ashley entities and keyed by entity id, so it survives `RemoveSystem`. Dead objects stay visible as ghosts until their last light has passed the player.
  - Ring buffer per entity of `(t, x, y, angle, vx, vy)` written in `afterStep`. Size: `HISTORY_SECONDS = arena diagonal / c + 0.5` (2.9 s at 100 u, 5.2 s at 200 u), which is up to 235 samples × 24 B ≈ 5.6 KB per entity.
  - Solve: bisection on the monotone function `f(t_r) = c(t − t_r) − |x_p − x_o(t_r)|` over the buffer (about 8 iterations), then the closed-form quadratic inside the bracketing segment (linear motion). If the root is older than the buffer, extrapolate linearly. If it's before spawn, don't draw.
  - Render at interpolated time `t_prev + α·dt`.
- **Cost:** CPU about 1 µs per entity per frame. Memory: a few MB worst case. M.
- **Gameplay:** very high, a genuinely new aiming skill. **AI must follow the same rule (E13), or it's unfair.**
- **Risk:** in world view, mixing delayed and undelayed objects is confusing. Light delay only shows in pilot view, never in world view.
- **Verdict: Next (M4).**

#### E9. Aberration and the full pilot view (includes Terrell-like distortion and apparent stretch)

- **Physics [EXACT].** Take the retarded relative position `r = x_o(t_r) − x_p(t)` (so the emission event is `Δt = −|r|/c`). Boost it into P (velocity `v_p`, `γ`, `β`):
  ```
  r'_∥ = γ (r_∥ + β |r|)        r'_⊥ = r_⊥        |r'| = D·|r|,   D = γ(1 + β cos θ)
  cos θ' = (cos θ + β)/(1 + β cos θ)                  (aberration, θ' toward the motion)
  ```
  This one function `apparent(r, β⃗)` serves static points (closed form, no history) and moving objects (with `r` from E8).
- **2D specifics:** directions crowd forward. At β = 0.9, a star at 90° appears at 25.8°, so half the sky fits in a ±26° cone. In 2D the angular density of sources goes up by `D` (it would be `D²` in 3D). Apparent distances scale by `D`. **Things ahead look farther away and stretched, things behind look closer and squashed.** Lines become curves.
- **Relativity of simultaneity** and **Terrell rotation** are not separate features. They fall out automatically when each **sprite corner** is transformed with its own retarded time.
- **Player sees:** the world tunnels forward and spreads out ahead. Enemies behind you bunch up close and red. The arena walls bend. Aim with the mouse in this apparent space: the aim direction is a P-frame direction that E3 converts correctly. A shot fired at what you see travels to where it *was*.
- **Implementation:**
  - CPU per sprite: solve E8 for the centre, refine per corner with one Newton step from the centre's root, apply `apparent()`, and draw the quad via `batch.draw(texture, vertices)`. There are no per-sprite shader uniforms, so the batch stays intact.
  - Static dust, starfield and field strips: the same formula in a vertex shader with uniforms `β_vec`, `γ`, `x_p`.
  - Camera centred on the player, no map clamp. The player's own ship is drawn at rest (uncontracted).
  - Sliders `PILOT_VIEW_ABERRATION` (scales the β used in the boost) and `PILOT_VIEW_DELAY` (blends retarded with current position). The result is continuous but only exact at 1.0.
  - Toggle key (for example `V`) between world view and pilot view.
  - **Ship orientation:** a direction at angle `θ'` from `v` in P has `tan θ_W = γ tan θ'` in W. Use P angles for control and drawing in pilot view.
- **Cost:** CPU S per frame (4 solves per sprite). GPU trivial. Complexity M to L (the input mapping and camera changes are the hard part).
- **Gameplay:** high, but **readability and nausea risk**. OpenRelativity's authors report "moving in this environment can quickly induce severe motion sickness" (Sherin et al. 2016, §IV). Mitigations: off by default, sliders, β smoothing, auto zoom, and a minimap that always shows W truth.
- **Verdict: Next (M4).** Default `PILOT_VIEW_DEFAULT = false`, both sliders 1.0 when on.

#### E10. Searchlight / relativistic beaming (brightness)

- **Physics.** In `d` spatial dimensions, `I_ν/ν^d` is invariant (phase-space density; for 3D see Kraus 2000, eq. 2: `I(λ) = d⁵ I'(λ d)`). So:

  | | 3D | 2D (our plane) |
  |---|---|---|
  | Bolometric surface brightness | `∝ D⁴` | `∝ D³` |
  | Angular element | `dΩ' = dΩ/D²` | `dθ' = dθ/D` |
  | Point source, observer moving, flux at fixed W distance | `∝ D²` | `∝ D²` |

  [EXACT under our convention.] OpenRelativity applies `1/D³` to spectral luminosity with its inverse `D`, which is consistent.
- **Player sees:** stars and objects ahead flare up, and things behind fade to near-black. At β = 0.9, `D² = 0.05` behind.
- **Implementation:** a multiplier `B = clamp(D^k, B_MIN, B_MAX)` on sprite alpha (with the additive pass for `B > 1`) and on starfield point size and alpha. `k = BRIGHTNESS_EXPONENT = 2`.
- **Cost:** trivial, S.
- **Gameplay:** a "rear blind spot" at high speed is an interesting risk. Readability needs `B_MIN = 0.25`, plus outlines and HUD markers for hostiles.
- **Verdict: Next (M4)**, with a mild version (`k = 1`) allowed in MVP alongside E4.

#### E11. Apparent animation rate (kinematic time Doppler)

- **Physics [EXACT]:** the pilot receives an object's proper-time ticks at rate `D` per pilot second. Per world second that's `D/γ_p`.
- **Player sees:** enemies ahead blink, flicker and animate fast. Explosions behind play in slow motion.
- **Implementation:** advance per-sprite animation time by `D/γ_p · dt`. Needs D from E4/E8. **Cost:** S. **Gameplay:** subtle but very "physics-real". **Verdict: Next.**

#### E12. Doppler-scaled photon damage

- **Physics [EXACT]:** a photon emitted with energy `E` (in the shooter's frame) by shooter `s` is absorbed by target `t` with energy `E_t = E · D_{s→t}`, where `D_{s→t} = γ_t(1 − β_t·n̂) / (γ_s(1 − β_s·n̂))`. Here `n̂` is the photon's W-frame direction (the aberrated muzzle direction from E3), `β_s` is the shooter's velocity at emission and `β_t` is the target's velocity at absorption.
- **Player sees:** lasers hitting an enemy that charges at you deal extra damage. Shooting at something fleeing is weak. Also, shooting forward while charging hits harder.
- **Implementation:** in `ContactSystem`, store the shooter's `β` at emission in `ProjectileComponent`. **Cost:** S. **Gameplay:** high. It rewards head-on engagements and makes Doppler colour tactically meaningful. **Verdict: Next** (can go into M3 if there's spare capacity).

#### E13. AI perceives the past (light delay for enemies)

- **Physics [EXACT]:** enemies see the player at the player's retarded position (E8, using the player's single history buffer).
- **Player sees:** juking works. Enemies shoot at where you were 0.3 s ago. Speed makes you harder to hit for real reasons.
- **Implementation:** the AI targeting query reads `LightHistory.retarded(player, observer = enemy)`. **Cost:** S once E8 exists. **Verdict: Next (with M6 enemies).**

### Tier 3: Showcase, gravity and black holes (milestone M5)

#### E14. Gravity: one law for planets and black holes, plus light bending

- **Physics.** The game plane *is* the equatorial plane of a Schwarzschild hole, and Schwarzschild orbits are planar. The exact radial equation for massive particles, in proper time `τ` with `L = |r × u|` (specific angular momentum, conserved), is:
  ```
  d²r/dτ² = −GM/r² + L²/r³ − 3GM L²/(c² r⁴)
  ```
  This is the same as the Newtonian central force plus a `−3GML²/(c²r⁴)` term (Wikipedia, *Schwarzschild geodesics*, effective force). So we integrate, in Cartesian coordinates:
  ```
  du/dτ = −(GM/r³) (1 + 3L²/(c² r²)) r⃗           ⇒ in game time: du/dt = (that) / γ
  ```
  - This gives **exact orbit shapes** [EXACT shape]: perihelion precession, ISCO at `r = 3r_s`, the marginally bound orbit at `2r_s`, plunge orbits.
  - The time mapping `dt = γ dτ` uses SR γ instead of the Schwarzschild `dt/dτ` [APPROX timing]. Objects don't "freeze" at the horizon. They just cross `r_s` and get absorbed.
  - Planets: same law with a tiny `r_s` and softening at the planet radius. It reduces to Newtonian.
  - Alternative, if we only want massive bodies: the Paczyński–Wiita potential `Φ = −GM/(r − r_s)`, which has the right ISCO and marginally bound radii but wrong photon behaviour. **Not recommended.** The law above is just as cheap and also covers light.
- **Photons [EXACT shape].** Null geodesics satisfy `d²u/dφ² + u = (3/2) r_s u²`, which as a Cartesian central force with affine parameter is:
  ```
  dk/dλ = −(3GM/c²) (L_k² / r⁵) r⃗,   L_k = |r × k|,  k = c·n̂;  renormalise |k| = c each step
  ```
  The equation is homogeneous of degree 2 in `k`, so renormalising is only a reparametrisation and the path shape is preserved. Results: the photon sphere at `1.5 r_s`, capture for impact parameter `b < b_c = (3√3/2) r_s ≈ 2.598 r_s`, weak-field deflection `2r_s/b`.
- **Player sees:** slingshots, precessing orbits, lasers curving around the hole. A hole can bend your own shot back at you.
- **Implementation:** a `GravitySourceComponent { r_s, softening }` plus a step listener that adds `a_grav` to every relativistic body's accumulator and rotates photon directions. With O(sources × bodies) and fewer than 5 sources, it's trivial.
- **Scale:** `r_s = 2 u` gives `GM = 3600`. At 20 u, gravity is 9 u/s², gentle. Your engine (`a₀ = 60`) can no longer hold you at about 10 u, a natural danger radius.
- **Cost:** S to M. **Gameplay:** high (orbits, slingshots, curved bank shots). **Verdict: Showcase (M5).**

#### E15. Gravitational time dilation and redshift

- **Physics:** static clocks run at `dτ/dt = √(1 − r_s/r)` [EXACT]. Combined with motion in our hybrid, `dτ/dt ≈ √(1 − r_s/r) / γ` [APPROX]. Light from a static emitter at `r` reaches far away redshifted by `g = √(1 − r_s/r)`. For a circular-orbit emitter seen **face-on** (the top-down camera), `g = √(1 − 1.5 r_s/r)` [EXACT], which includes transverse Doppler.
- **Player sees:** skimming the hole costs proper time (your guns slow down), and objects near the hole look redder and dimmer. "Interstellar's planet": hide near the hole and the world races ahead.
- **Implementation:** multiply into `dτ` (E2), and add `g` to the E4 colour factor (`D_total = D·g_emitter/g_observer`). Clamp `r ≥ 1.01 r_s` inside the square root. **Cost:** S. **Verdict: Showcase.**

#### E16. Black hole object: horizon, photon sphere, ISCO rings

- **Physics:** horizon `r_s` (anything crossing it is absorbed: add `RemoveComponent`; the player dies). Photon sphere `1.5 r_s`. ISCO `3 r_s`, inside which no stable orbit exists, so without thrust you plunge. The top-down **shadow** has radius `b_c ≈ 2.6 r_s` [EXACT for a distant camera].
- **Player sees:** a black disk of radius 2.6 r_s, a faint ring at the photon sphere, and a dashed danger ring at the ISCO.
- **Implementation:** a static (or slowly drifting) entity with a sensor fixture at `r_s` and `GravitySourceComponent`. Rings are drawn with the accretion-disk or lensing shader or `ShapeRenderer`. The boundary field also acts on it if it moves. **Cost:** S to M. **Verdict: Showcase.**

#### E17. Accretion disk visuals

- **Physics:** the coordinate angular velocity of circular orbits in Schwarzschild is exactly `Ω = √(GM/r³)` [EXACT], for `r ≥ 3r_s`. Temperature profile (thin disk, simplified Shakura–Sunyaev / Page–Thorne shape): `T(r) = T_* (r/r_in)^(−3/4) (1 − √(r_in/r))^(1/4)` [APPROX shape, tuned scale]. For the top-down camera the disk is face-on, so there's **no left/right Doppler asymmetry**. Only `g⁴` dimming applies (3D bolometric, since light goes up to the camera) and the colour is `g·T` [EXACT for the face-on camera]. The famous lopsided beaming (Interstellar, Luminet) needs an inclined view. Here it appears physically in **pilot view**, where each disk particle gets its own `D` toward the pilot (E4/E10). The side swinging toward you blazes blue-white.
- **Implementation:** a GPU particle disk.
  - A static VBO of about 3000 particles with attributes `(r, φ₀, size, T)`. The vertex shader computes `φ = φ₀ + Ω(r)·t` from a time uniform, with no CPU updates and no shearing-texture problem. Additive blending. Blackbody LUT colour. A slow radial inward drift with respawn at the outer edge.
  - In world view, use `g`. In pilot view, apply `D_pilot` and `apparent()` per vertex. The particle velocity is analytic, so use the constant-velocity closed form instead of history.
  - Optional bloom FBO pass.
- **Cost:** GPU small. Complexity M. **Gameplay:** eye candy plus a "danger here" signal. This is the owner's explicit end-goal visual ("bright matter around it"). **Verdict: Showcase.** `DISK_INNER = 3 r_s`, `DISK_OUTER = 12 r_s`, `DISK_PARTICLES = 3000`, `DISK_T_PEAK = 12000 K`.

#### E18. Gravitational lensing

- **Top-down camera, backdrop [EXACT thin lens].** C_W looks down through the plane at the starfield backdrop, which sits conceptually at depth `D_bg` below the plane. That's the textbook point-lens geometry. For an orthographic camera (distance → ∞):
  ```
  Einstein radius in the plane: R_E = √(2 r_s D_bg)
  pixel at offset ρ from the hole samples the backdrop at   ρ_src = ρ (1 − R_E²/|ρ|²)
  inside |ρ| < b_c ≈ 2.6 r_s: black (shadow)
  ```
  Surface brightness is conserved, so sampling handles the magnification. This is a full-screen FBO post-process restricted to a quad of about `6 R_E` around each hole. Multiple holes: sum the deflections [APPROX]. Near the photon ring, the thin-lens formula is [APPROX]. For a sharper ring, use a precomputed deflection LUT `α(b)` from the photon equation in E14 (Bruneton 2020 uses precomputed tables; James et al. 2015 is the reference for Interstellar). Treat `R_E` as the tunable (`LENS_EINSTEIN_RADIUS = 4 r_s`, which implies `D_bg = 8 r_s`).
- **In-plane objects and the top-down camera:** **not lensed** [EXACT], because they're at the lens depth. Lensing every sprite would be [FAKE]. Recommend against it, or at most as an option.
- **Pilot view, in-plane lensing:** objects behind the hole relative to the pilot appear displaced, with a second image on the far side. That needs a two-point boundary solve per object near the hole (LUT plus Newton). **L, later or skip.**
- **Cost:** the backdrop pass is GPU S and complexity M (FBO plumbing, TeaVM-safe GLSL). Pilot lensing is L. **Gameplay:** "see the hole before you hit it", plus wow. **Verdict:** the backdrop pass is Showcase (M5). Pilot in-plane lensing is Maybe.

#### E19. Tidal stretching

- **Physics:** the radial tidal acceleration difference across a length `ℓ` is `Δa ≈ 2GMℓ/r³` [EXACT Newtonian limit]. Visual stretch with `1 + k·Δa` [FAKE scale].
- **Implementation:** an extra scale in the E5 corner transform along `r̂`, plus damage over time inside a radius (spaghettification). **Cost:** S. **Verdict: Later (M5 polish).**

### Tier 4: Maybe or skip

| ID | Effect | Note | Verdict |
|---|---|---|---|
| E20 | Hitbox length contraction | Rebuild or rescale Box2D fixtures along `v̂` by `1/γ` when γ changes by >5 %. Circles become 8-vertex polygons (`b2_maxPolygonVertices = 8`). Exact, and fast ships become thinner targets. M cost, and Box2D contact churn | Later |
| E21 | Game clock = pilot proper time | The world advances `γ·dt_real` per frame, so the world fast-forwards when you're fast. Physically the pilot's experience, but it multiplies per-step distances by γ, so it needs substeps up to `n = γ_max`, and it makes the game harder at speed | Maybe, as an optional mode or "proper-time race" |
| E22 | Relativistic rocket / fuel | `Δη = (v_e/c) ln(m_i/m_f)`, `β = tanh(η)`. Rapidity as a fuel budget is a nice roguelike resource | Maybe (M7) |
| E23 | Thomas–Wigner rotation | Ship attitude precesses under non-collinear boosts. Confusing, small at γ < 3 | Skip |
| E24 | Kerr frame dragging, ergosphere | Needs Kerr geodesics. Rotating holes could later drag `u` tangentially with a fake `a ∝ J/r³` | Skip (maybe [FAKE] later) |
| E25 | Exact relativistic collision solver | Two-body elastic collisions in the centre-of-momentum frame in the post-solve. E7 is good enough | Skip |
| E26 | Spectral IRGBU Doppler shader for sprites | OpenRelativity-style: RGB plus IR and UV channels, shifted by `D`, re-integrated with CIE curves. Needs extra textures or derived channels, and colour encoded in the vertex colour | Later (after M4) |
| E27 | Co-moving distant camera | Needs the future state of objects (§0) | Skip |
| — | Transverse Doppler and `1/γ⁴` dimming for C_W | Exact for the top-down camera, but invisible below γ ≈ 1.5 and conflicts with the pilot-Doppler tint | Skip |

---

## 5. Where things live (architecture summary)

| Layer | New or changed | Effects |
|---|---|---|
| Pure math (`physics/Relativity.kt`, `physics/BoundaryField.kt`, `physics/Gravity.kt`) | new, unit tested | everything |
| `PhysicsSystem` | per-step listener hook, substeps, clear forces per step | §2, §3 |
| Step listeners: `RelativisticDynamics`, `BoundaryFieldSystem`, `GravitySystem`, thrust | new | E1, E2, E14, §3 |
| Components: `RelativisticComponent`, `LifetimeComponent`, `GravitySourceComponent`, `PhotonComponent` | new | |
| `WeaponProjectileProvider` | velocity addition, photon aberration, raycast sweep | E3 |
| `ContactSystem` | γ_rel energy damage, photon D damage | E7, E12 |
| `RenderSystem` | 4-corner custom quads (contraction, pilot transform), Doppler tint plus additive pass | E4, E5, E9, E10 |
| Starfield (M1 redo) | custom mesh plus shader with `β`, `γ`, blackbody LUT | E4, E9, E10 |
| `LightHistory` store | ring buffers by id, survives removal | E8, E13 |
| FBO post-process | lensing, optional bloom | E18 |
| `HudSystem`, `CameraSystem` | β/γ/τ/t, speed zoom, pilot-view centring | E6, E9 |
| Removed | `WrapSystem`, `WrapComponent`, `moveForcesApplied`, `WeaponType.trapped` | §3 |

No effect needs a per-sprite shader uniform, so `SpriteBatch` batching survives. Per-object distortion is done on the CPU as quad corners. Shaders are only used for bulk point sets (stars, dust, disk), the field shimmer, and the lensing pass. All GLSL must be ES 1.00 (TeaVM).

---

## 6. Tunable constants (starting values)

| Constant | Start | Range | Effect |
|---|---|---|---|
| `SPEED_OF_LIGHT` | 60 u/s | 40–67 (n=1) | §1 |
| `PHYSICS_SUBSTEPS` | 1 | 1–3 | §1 |
| `GAMMA_MAX_MASSIVE` / `GAMMA_MAX_PROJECTILE` | 10 / 20 | 5–30 | §2 |
| `COLLISION_EPS` | 0.05 u/s | | §2 |
| `ENGINE_PROPER_ACCEL` | 60 u/s² | 30–120 | E1 |
| `REVERSE_THRUST_FRACTION` | 0.5 | | E1 |
| `COOLDOWN_DILATION_EXPONENT` | 1.0 | 0–1 | E2 |
| `ROTATION_DILATION_EXPONENT` | 0.5 | 0–1 | E2 |
| `LASER` | photon, c | | E3 |
| `SLUG_SPEED_FRACTION` | 0.7 | 0.3–0.95 | E3 |
| `PHOTON_WORLD_LIFETIME` / `PHOTON_MAX_BOUNCES` | 2.0 s / 3 | | §3 |
| `SLUG_PROPER_LIFETIME` | 1.2 s | | §3 |
| `FIELD_SKIN`, `FIELD_EXPONENT`, `FIELD_ACCEL_MAX` | 8 u, 2, 3000 u/s² | | §3 |
| `FIELD_DAMPING`, `FIELD_BACKSTOP_RESTITUTION`, `FIELD_PHOTON_MIRROR_Q` | 1.0 s⁻¹, 0.9, 0.5 | | §3 |
| `SHIMMER_BASE_ALPHA`, `SHIMMER_MAX_IMPACTS`, `SHIMMER_RIPPLE_SPEED`, `SHIMMER_DECAY` | 0.08, 16, c, 0.6 s | | §3 |
| `DOPPLER_COLOR_STRENGTH`, `DOPPLER_D_CLAMP` | 1.0, [0.25, 4] | 0–1 | E4 |
| `VISUAL_BETA_SMOOTHING` | 0.15 s | | E4 |
| `BRIGHTNESS_EXPONENT`, `B_MIN`, `B_MAX` | 2 (1 in MVP), 0.25, 3 | | E10 |
| `CONTRACTION_STRENGTH` | 1.0 | 0–1 | E5 |
| `GAMMA_MASS_ENABLED`, `GAMMA_MASS_CAP` | true, 8 | | E7 |
| `CAMERA_ZOOM_PER_BETA2` | 0.6 | 0–1 | E6 |
| `PILOT_VIEW_DEFAULT`, `PILOT_VIEW_ABERRATION`, `PILOT_VIEW_DELAY` | false, 1.0, 1.0 | 0–1 | E9 |
| `HISTORY_SECONDS` | diagonal/c + 0.5 | | E8 |
| `BH_RS` | 2 u | 1–4 | E14–E18 |
| `LENS_EINSTEIN_RADIUS` | 4 r_s | 2–8 r_s | E18 |
| `DISK_INNER`, `DISK_OUTER`, `DISK_PARTICLES`, `DISK_T_PEAK` | 3 r_s, 12 r_s, 3000, 12000 K | | E17 |

---

## 7. Prioritised implementation order

Each item is roughly one card. **(T)** means pure logic with unit tests. **(P)** means it needs a `playtest:` line.

**M2: Arena field**
1. `PhysicsSystem` per-step listener hook, clear forces every step, move thrust into a listener, delete `moveForcesApplied`. Optional `PHYSICS_SUBSTEPS`. (S, T)
2. `physics/BoundaryField.kt` force law, turn-around-depth tests. (S, T)
3. `BoundaryFieldSystem` (dynamic: force; kinematic: velocity kick), backstop, remove `WrapSystem`/`WrapComponent`/`trapped`. (M, T+P)
4. `LifetimeComponent`/`LifetimeSystem` (WORLD clock for now), photon bounces, `RemoveSystem` becomes a safety net (player clamped, never removed). (S, T)
5. Camera clamp margin, plus arena rectangle on the minimap. (S, P)
6. Field shimmer shader with impacts. Fallback: `ShapeRenderer`. (M, P)

**M3: Relativity MVP**

7. `SPEED_OF_LIGHT = 60`, projectile speeds as fractions of c, swept raycast for projectiles. (S, T)
8. `physics/Relativity.kt`: γ, `u↔v`, clamps, velocity addition, photon aberration, Doppler factor, force transform, γ_rel. (S, T)
9. `RelativisticComponent` plus `RelativisticDynamics` listener (thrust as proper acceleration, field on `u`, readback, clamp). Attach in all providers. (M, T)
10. Proper time: `dtProper` for cooldowns, lifetimes (PROPER for slugs), rotation dilation, asteroid spin `ω₀/γ`. (S, T)
11. Projectile velocity addition, photon lasers. (S, T)
12. HUD β/γ/τ/t (rapidity bar) plus speed zoom. (S, P)
13. Doppler colour: starfield LUT shader (needs the M1 starfield) plus sprite tint and additive pass, β smoothing, mild searchlight `k = 1`. (M, P)
14. Length contraction via 4-corner quads in `RenderSystem`. (S, P)
15. γ-mass collisions plus `K_rel` damage. (S, T)

**M4: Relativistic view**

16. `LightHistory` store plus retarded-time solver. (M, T)
17. `apparent()` transform, pilot-view render path, toggle, sliders, mouse aim in P. (M–L, T+P)
18. Searchlight `k = 2`, `B_MIN` floor, hostile outlines. (S, P)
19. Apparent animation rate. Doppler photon damage. (S, T)
20. Field strips and dust through the pilot transform. (S, P)

**M5: Gravity and black holes**

21. `physics/Gravity.kt` (massive plus photon laws) plus `GravitySystem` listener. (S, T)
22. Black-hole entity: absorb at `r_s`, rings, gravitational time dilation and redshift. (M, T+P)
23. Accretion disk particle shader. (M, P)
24. Lensing FBO pass (thin lens, shadow). (M, P)
25. Tidal stretch (polish). (S, P)

**M6 and later:** AI sees retarded player (E13), hitbox contraction (E20), spectral shader (E26), rapidity fuel (E22), pilot in-plane lensing (E18b).

---

## 8. Open questions for the owner

Each question has options and a recommended answer. The planner can plan around the recommendation and file a `needs-user` card.

1. **Value of `c`?** (a) 40, (b) 60, (c) 80 with 2 substeps, (d) keep 250 with ~170 Hz physics. **Recommend (b) 60 at 45 Hz.** Delay is visible at screen scale, bullets are fast enough, and Box2D needs no changes.
2. **Arena size?** (a) keep 100 × 100, (b) 150 × 150, (c) 200 × 200. **Recommend keeping 100 for M2, then (c) 200 × 200 from M3.** You need about 77 u of straight thrust to reach 0.9c. The asteroid count formula may need rescaling for the larger area.
3. **What is "real time": world time or pilot time?** (a) World time; the player's proper time drives the ship's own clocks. (b) Pilot time; the world fast-forwards by γ (E21). **Recommend (a).** It's simpler, stable with Box2D, and still makes time dilation matter. (b) could be a later race-mode variant.
4. **Should your own clock slow your guns and turning at speed?** (a) Exact: both scale by `1/γ`. (b) Guns exact, turning softened (`γ^−0.5`). (c) Neither (cosmetic τ only). **Recommend (b).** The trade-off stays real without making high speed uncontrollable.
5. **Default view?** (a) World view with Doppler tint, pilot view as a toggle. (b) Pilot view by default. (c) A blend slider at 0.5 by default. **Recommend (a) through M4, then revisit with playtests.** Motion sickness is reported by the OpenRelativity authors.
6. **Hard `γ` cap?** (a) Hard cap γ = 10 (β 0.995). (b) No cap. (c) A soft cap where engine efficiency drops with γ. **Recommend (a).** It's numerically safe and readable. Use (c) only if the cap feels like an invisible wall.
7. **Boundary field: lossless or damped?** (a) Lossless (`k_d = 0`). (b) Mild damping (`k_d = 1`). **Recommend (b)** for ships and asteroids. Photons always reflect losslessly but consume a bounce.
8. **Minimap: instantaneous truth or light-delayed?** (a) Instant W positions ("tactical computer", a deliberate cheat). (b) Light-delayed like the pilot view. **Recommend (a).** It's the readability anchor once pilot view distorts the main screen.
9. **Ramming at relativistic mass?** (a) Yes, γ-mass capped at 8, with damage from `K_rel`. (b) Rest-mass collisions only. **Recommend (a).** It's the "mass goes to infinity" fantasy, and cheap.
10. **Default weapon?** (a) Photon laser at exactly c (direction aberrates, energy Doppler-scales). (b) Massive slug at 0.7c (velocity addition, muon lifetime). **Recommend (a) as the default, with (b) as the first upgrade.** They play differently: you can't outrun light, but slugs inherit your speed.
11. **Black holes: static or moving, and may they leave the map?** (a) Static. (b) Slow drift, kept in by the boundary field. (c) Free-moving, may leave. **Recommend (a) for M5, (b) later.** Never (c). This agrees with the game-design doc's Q1.
12. **Lensing: honest or dramatic?** (a) Honest: only the backdrop is lensed for the top-down camera, and sprites aren't. (b) Lens everything on screen. **Recommend (a).** The Einstein ring on the backdrop plus the disk already sells it, and (b) would distort hitboxes visually.
13. **Should enemies obey light delay?** (a) Yes, AI targets your retarded position. (b) No. **Recommend (a).** It makes fast juking a real skill and keeps fairness with pilot view.
14. **Readability floor for searchlight dimming?** (a) Physical: things behind can go nearly black. (b) Floor at 25 % plus hostile outlines. **Recommend (b).**

---

## 9. References

- Sherin, Cheu, Tan, Kortemeyer, *Visualizing relativity: The OpenRelativity project*, Am. J. Phys. 84, 369 (2016), https://doi.org/10.1119/1.4938057 (PDF mirror: https://moodle2.units.it/pluginfile.php/483015/mod_resource/content/1/1.4938057.pdf). Used for: per-vertex light-cone quadratic (eqs. 6–7), Lorentz transform of emission events (eq. 8), the Doppler factor and IRGBU colour shift (eqs. 9–10), `1/D³` searchlight, constant-velocity and no-history limitations, motion-sickness warning. This is the toolkit behind MIT Game Lab's *A Slower Speed of Light*.
- OpenRelativity source: https://github.com/MITGameLab/OpenRelativity (the README describes the per-vertex Lorentz shader, Doppler fragment shader, and IR/UV textures).
- U. Kraus, *Brightness and color of rapidly moving objects: The visual appearance of a large sphere revisited*, Am. J. Phys. 68, 56 (2000). PDF: https://muj.optol.cz/~richterek/data/media/ref_str/kraus2000.pdf. Companion visualisations: https://www.spacetimetravel.org/sphere. Used for: `λ = λ'/d`, `I(λ) = d⁵I'(λd)` (so bolometric `d⁴` in 3D), and blackbody `T → dT`. The D values 0.23 and 4.36 at β = 0.9 match ours.
- TestTubeGames, *Velocity Raptor* (2D top-down relativity): https://testtubegames.com/velocityraptor_old.html, https://testtubegames.com/srel101.html. The pages returned HTTP 403 to our fetcher. Only the search-snippet statement about drawing distance on screen in a bird's-eye view was used.
- Box2D 2.4 `b2_common.h`, `b2_maxTranslation` (compile-time, "not meant to be adjusted"): https://box2d.org/doc_version_2_4/b2__common_8h.html
- Schwarzschild geodesics (effective force with the `3GML²/(c²r⁴)` term, photon sphere `1.5 r_s`, critical impact parameter `(3√3/2) r_s`): https://en.wikipedia.org/wiki/Schwarzschild_geodesics
- Abramowicz, *The Paczyński–Wiita potential: a step-by-step "derivation"*, https://arxiv.org/abs/0904.0913 (ISCO and marginally bound radii; considered as the alternative gravity law).
- E. Bruneton, *Real-time High-Quality Rendering of Non-Rotating Black Holes* (2020), https://arxiv.org/abs/2010.08735. Shader: https://github.com/ebruneton/black_hole_shader (precomputed deflection tables, accretion disk).
- O. James, E. von Tunzelmann, P. Franklin, K. S. Thorne, *Gravitational lensing by spinning black holes in astrophysics, and in the movie Interstellar*, CQG 32, 065001 (2015), https://arxiv.org/abs/1502.03808 (disk Doppler beaming at inclination, lensing reference).
- Standard SR results (velocity addition, aberration, relativistic Doppler, proper velocity, hyperbolic motion, force transformation) were derived from first principles here and cross-checked against the papers above.
