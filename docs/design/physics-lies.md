# Physics lies register

Every place where Star Trap deliberately departs from real physics for gameplay, readability or performance. The owner asked for this list (2026-11-02) so the "lies" stay visible and can be revisited.

**Rule:** any card that introduces a FAKE or an APPROX (research tags, see `docs/research/relativity-effects.md`) adds or updates a row here in the same commit. `/auto-dev` checks this at commit time.

Tags: **FAKE** is a deliberate departure. **APPROX** is a simplification that is close to correct in the game's regime. **ENGINE** is a limit imposed by Box2D or the CPU.

## Simulation and time

| # | Lie | Tag | Why | Where | Exact alternative / revisit |
|---|---|---|---|---|---|
| 1 | The pilot clock's fast-forward is capped at ~12× (`TIME_WARP_MAX_LIMIT`). Past the cap the arena stops speeding up relative to the pilot | FAKE, ENGINE | Each step multiplies CPU cost (γ physics steps per frame) | ST-068, ST-069 | Raise the cap if profiling allows |
| 2 | The arena is simulated in one frame (the arena rest frame), and Box2D collisions/contacts aren't Lorentz-covariant | APPROX | Box2D can only work in one inertial frame | whole sim | — |
| 3 | Box2D clamps movement to 2 u per step, so c = 20 u/s, well under the ~90 u/s Box2D cap at 60 Hz | ENGINE | Box2D limitation | `PhysicsConstants.kt`, ST-101 | Substeps (costs CPU) |
| 4 | γ safety bound at 50 | ENGINE | Float precision | ST-055 | — |
| 5 | Ships rotate as non-relativistic rigid bodies | APPROX | Rim speed ωr ≪ c, so it's exact enough in the ship frame | `ShipRotationController` | — |

## Ship and weapons

| # | Lie | Tag | Why | Where | Exact alternative / revisit |
|---|---|---|---|---|---|
| 6 | Engines are a constant proper acceleration with unlimited fuel and no reaction mass | FAKE | Gameplay | `MoveSystem` | A rocket equation with fuel as a resource |
| 7 | Braking boost: engines push up to 3× harder against the current motion | FAKE | The owner wants quicker deceleration | ST-066 | — |
| 8 | The Box2D mass in collisions is `min(γ, 8)·m₀` (inertia scales the same way). Above γ = 8 a body hits like 8× its rest mass, though its speed and proper velocity stay exact. Contacts conserve relativistic momentum Σ m₀u (u = γUsed·v after the solve, v then rewritten as u/γ so it stays below c), not relativistic energy exactly | APPROX, ENGINE | Box2D contacts get mushy above ~10:1 mass ratios; own cap, independent of the γ safety bound (50) | ST-047 (`RelativisticDynamics`, `GAMMA_MASS_CAP`) | Raise the cap if the solver copes; `RelativitySettings.gammaMassEnabled` switches the whole effect off |
| 9 | Projectiles have a lifetime and are removed off-arena | FAKE | Performance | ST-024, ST-067 | — |
| 10 | Targeting trance: holding LMB slows time, and the ship turns instantly | FAKE | Optional control mode (parked) | ST-091 | Physically, speed makes the world age *faster* for the pilot |
| 27 | The player's gun cooldown runs on real time, so it never dilates, even past the warp cap when the arena can't keep pace with γ | FAKE | The pilot's clock never slows; fire rate feels constant | ST-040 (`properDelta`) | Run it on `worldDt/γ` (differs only past the cap) |
| 28 | The player's turn controller is slowed by 1/warp, not 1/γ, so past the warp cap turning is slower in real time | FAKE | Physics stays in the arena frame; the warp is what the arena actually runs at | ST-040 (`rotationTimeFactor`) | Raise the warp cap |
| 29 | Free spin is ω₀/γ, applied as a per-step rescale of the angular velocity; Box2D angular damping still acts in world time | APPROX | Exact for constant γ and survives collisions | ST-040 (`RelativisticDynamics`) | — |
| 30 | A slowed-clock PD controller is stepped at the world step (kd·f applied once per world step) | APPROX | Turn timing is within 15 % and overshoot under 5° in tests | ST-040 (`PhysicsSystem.applyRotation`) | Substep the controller in proper time |
| 36 | Laser damage scales with the exact photon energy ratio γ_t(1 − β_t·n̂)/(γ_s(1 − β_s·n̂)), but the factor is clamped to [0.25, 4] (`PHOTON_DAMAGE_D_MIN/MAX`), and it is off when relativity is off | FAKE | Keeps extreme head-on/fleeing shots from one-shotting or doing nothing | ST-081 (`photonEnergyFactor`, `ContactSystem.applyProjectileHit`) | Drop the clamp; the tactical overlay shows the expected factor next to the aimed target (ST-133, `TacticalSettings.showLaserDamage`); `RelativitySettings.photonDopplerDamage` switches the effect off |

## Arena boundary field

| # | Lie | Tag | Why | Where | Exact alternative / revisit |
|---|---|---|---|---|---|
| 11 | The boundary field gives the same acceleration to any mass and grows with depth (∝ q²) | FAKE | Keeps everything inside the arena without a hard wall | `physics/BoundaryField.kt` | A charge in a static potential is exact only for charged bodies |
| 12 | Backstop: bodies are clamped inside and their outward velocity is reflected at restitution 0.95. The field damping (4.5 1/s) also loses about 4.8 % of the normal speed per bounce (95.2 % kept) | FAKE | Safety net for fast bodies that the field can't stop in one step; the damping makes the bounce lossy so the game runs a little slower | `BoundaryFieldSystem`, `physics/BoundaryField.kt`, ST-023 | — |
| 13 | The field ignores projectiles: shots fly out and the field only shows a ripple | FAKE | The owner wants shots to leave the arena | ST-067 | — |

## Gravity and black holes

| # | Lie | Tag | Why | Where | Exact alternative / revisit |
|---|---|---|---|---|---|
| 14 | Source–source gravity (black holes, planets) is scaled by `sourceCoupling = 0.1` | FAKE | At physical strength two holes 60 u apart merge in ~6 s | ST-092, ST-083 | **Owner's preferred future fix:** start the whole system with initial velocities (bound orbits, enough spacing) so it's gravitationally balanced at coupling 1 |
| 15 | Sources (holes, planets, debris) move on Newtonian paths, with no relativistic dynamics | APPROX | They stay far below 0.1 c | ST-083 | — |
| 16 | The player's pull on sources uses a tunable `playerGm` | FAKE | Gameplay tuning | ST-083 | — |
| 17 | Black-hole merges are instant and keep momentum, with no gravitational waves or ringdown | FAKE | Simplicity | ST-085 | — |
| 18 | The accretion disk has no inward drift of matter | APPROX | Visual only | ST-087 | — |
| 19 | Lensing is applied to the backdrop only, fades at the edge of the lens area, and overlapping lens areas don't lens each other | FAKE | Cost, and to hide seams | ST-088 | Per-object lensing (research: L, skip) |

## Rendering and light

| # | Lie | Tag | Why | Where | Exact alternative / revisit |
|---|---|---|---|---|---|
| 20 | Sprite Doppler tint shifts a white point (`T_ref = 6500 K`) rather than real spectra | FAKE | Evocative and cheap | ST-045 | — |
| 21 | Searchlight brightness has a floor, and sprites get outlines | FAKE | Readability | ST-080 | — |
| 22 | Parallax backdrop direction stands in for a star direction in a 2D universe | FAKE | No real "sky" exists in a 2D plane | starfield, ST-043 | — |
| 23 | Lighting: the emitter position is extrapolated, with no extra light-travel delay from disk to object | FAKE | Cost | ST-097 (FAKE 1) | — |
| 24 | Lighting: light received by a moving object has no Doppler shift or aberration at the object | FAKE | Cost | ST-097 (FAKE 2) | — |
| 25 | Lighting ignores length contraction of the lit sprite, and the disk is one soft point light, plus an ambient floor of 0.35 | FAKE | Cost and readability | ST-095, ST-097 (FAKE 3) | — |
| 26 | The disk's pilot-view centre uses the hole's retarded position (approximation) | APPROX | Cost | ST-089 | — |
| 31 | World-view length contraction stops at 25 % of rest length (`CONTRACTION_MIN_SCALE`), so past γ = 4 sprites stop shrinking | FAKE | Readability: true 1/γ makes fast ships invisible slivers | ST-044 (`render/RelativityVisuals.kt`) | — |
| 32 | Hitboxes aren't length-contracted; only sprites are | APPROX | Box2D fixtures are fixed shapes | ST-044 | Research E20 |
| 33 | Starfield Doppler: D is clamped to [0.25, 4], star brightness scales as D¹ (not D³–D⁴) clamped to [0.25, 3], and colour is a blackbody LUT lookup at T·D | FAKE | Readability: stars behind dim but never vanish, stars ahead don't blow out | ST-043 (`render/RelativityVisuals.kt`, `Starfield`) | Raise `BRIGHTNESS_EXPONENT_MVP` / widen clamps; GPU shader path (research E4/E10) |
| 34 | The tactical computer overlay shows every body's true current arena-frame position and velocity, i.e. information no light has delivered yet; tier 2 intercept markers (shot leads) use the true state too | FAKE | Readability: the light-delayed, aberrated pilot view is unplayable on its own (owner 2026-11-03) | ST-123, ST-125 (`TacticalOverlaySystem`, `trueLeadDirection`) | Light-delayed overlay (would defeat its purpose) |
| 35 | Ram damage is applied once at `beginContact` from the pre-step relative γ, using the rest masses and an arbitrary damage scale; the player takes `PLAYER_RAM_DAMAGE_FACTOR` (0.5) of it; asteroid health scales with rest mass; no damage under `γrel − 1 = 0.02`; freshly split fragments are ram-immune for `FRAGMENT_RAM_IMMUNITY_STEPS` steps | FAKE | Gameplay: ramming at near-c is a weapon that also hurts the player | ST-048 (`ContactSystem.ramDamage`) | Damage from the actual energy lost in the solve (postSolve impulses) |
| 37 | Computer sounds: asteroid break-ups are sonified by the ship's computer. They play when the event's light reaches the pilot (`|x_pilot - x_event| <= c (t - t_event)`), pitch is the source's Doppler factor clamped to [0.5, 2], volume is `1/(1 + d/d0)` with `d0 = c x 1 s`, pan is the bearing, at most 8 voices. No sound reaches the pilot through vacuum | FAKE (by design, toggleable) | Assist: readability and feel; off (`computer_sounds`) is the realistic silent vacuum | ST-136 (`ComputerSoundSystem`, `audio/ComputerSoundMix.kt`) | Silence; or a real sonification model with energy-based loudness |
