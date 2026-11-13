# Release physics fidelity audit (ST-185)

Owner, 2026-11-12: "I want all the physics to be as close as possible." This audit goes through every row of `docs/design/physics-lies.md`. For each row it decides whether to **remove** the lie before release, keep it because the **engine** forces it (Box2D, float precision, web performance), or keep it for **gameplay** (owner decision). It also covers the owner's ST-185 answers: Q1 keeps the braking boost, Q2 adds no fuel but asks for a fuel curiosity section, Q3 and Q5 take the recommended values, and Q4 is research only.

Observers follow `relativity-effects.md` §0. **W** is the arena rest frame (the Box2D/ECS "true state"). **P** is the pilot's instantaneous rest frame. **C_W** is the Outside view, a distant camera at rest above the plane. The **pilot view** shows what P's eyes receive, which is light that travels in the plane. Symbols: β = v/c, γ = 1/√(1−β²), the Doppler factor `D = γ_o(1 − β_o·n̂)/(γ_s(1 − β_s·n̂))` with n̂ pointing from the source to the observer, `g = gravFactor = ∏ √(1 − r_s,i/r_i)` (row 19d), and rapidity η = artanh β.

## 1. Verdicts per row

Tags: **remove**, which becomes a follow-up card. **keep-engine** means Box2D, float or web performance forces the lie. **keep-gameplay** means the owner decides; the row stays and is revisited after release. **keep-exact** means the APPROX is already within a few percent in the game's regime, so removing it would buy nothing visible. "Web risk" is the cost of the exact version on the TeaVM build.

| # | Lie (short) | Verdict | Cost to remove | Web risk | Exact replacement | Card |
|---|---|---|---|---|---|---|
| 1 | Pilot-clock warp capped at 12× | keep-engine | S code, but every extra warp step is a full Box2D step | high: `MAX_STEPS_PER_FRAME` × JS Box2D | Warp = `γ/g` with no cap; needs substeps ∝ γ | — (rows 27/28 make the inside of the cap exact, see ST-199) |
| 2 | One-frame (W) Box2D sim, contacts not covariant | keep-engine | L (custom solver) | — | Covariant collision solve in the CoM frame (E25) | — |
| 3 | c = 20 under the Box2D 2 u/step cap | keep-engine | substeps | high | `C_MAX_BOX2D_SAFE = 0.75·2·tick` already derives it | — |
| 4 | γ safety bound 50 | keep-engine (never binds) | — | — | A straight engine run across the 200 u arena reaches γ ≈ 1 + d·a₀/c² ≈ 5.1, so 50 is only a float guard | — |
| 5 | Rigid-body rotation is Newtonian | keep-exact | — | — | Rim speed ωr ≪ c | — |
| 6 | No fuel or reaction mass | keep-gameplay (owner Q2) | M | — | Photon rocket `m₀/m₁ = exp(∫a dτ/c)`, see §4 | — |
| 7 | Braking boost up to 3× | keep-gameplay (owner Q1) | S | — | Thrust independent of the velocity direction | ST-189 reviews `BRAKE_BOOST_K` |
| 8 | Box2D mass `min(γ, 8)·m₀` | keep-engine (rarely binds: γ > 8 needs β > 0.992) | S to raise | low | Box2D mass `γ·m₀`; the solver gets mushy past about 10:1 | — |
| 9 | Projectile lifetime; removed off-arena | **remove** the photon lifetime (keep the off-arena removal, which is exact: nothing is outside) | S | medium: 2 to 5× more live photon bodies (`bullet = true`) | A photon flies until it leaves the arena: lifetime ≥ arena diagonal / c | ST-198 (needs-user: changes laser range) |
| 10 | Targeting trance | retired: ST-091 closed, code gone | — | — | — | row marked retired |
| 27 | Player gun cooldown on real time past the warp cap | **remove** | S | none | `properDelta = worldDt·g/γ` for the player too | ST-199 |
| 28 | Player turn controller slowed by 1/warp past the cap | **remove** | S | none | `rotationTimeFactor = g/γ` for the player too | ST-199 |
| 29 | Free spin ω₀/γ per-step rescale | keep-exact | — | — | — | — |
| 30 | Slowed PD controller stepped at the world step | keep-exact (within 15 % in tests) | M | low | Substep in proper time | — |
| 36 | Photon damage clamp [0.25, 4] | **remove** (widen to [0.1, 10], owner Q5), and add the missing gravitational photon energy `g_s/g_t` | S | none | `E_t/E_s = D_{s→t}·g_s/g_t` | ST-196 |
| 46 | Lock-on homing on the true target state | keep-gameplay (assist) | M | low | Seeker on the retarded image | ST-186 rewrites the row |
| 47 | Lock-on engines without fuel | keep-gameplay | — | — | Rocket equation | ST-186 rewrites the row |
| 48 | Target lock on the true state, ammo regenerates | keep-gameplay (assist) | — | — | — | — |
| 49 | Rocket motor without fuel, no blast, no recoil; plume not light-delayed | **remove** the plume part; keep the rest (gameplay) | S | none | Plume on iff the rocket was burning at its retarded time: `burnStart ≤ t_ret < burnEnd` | ST-197 |
| 11 | Boundary field: same acceleration for any mass | keep-gameplay | — | — | Charged body in a static potential | — |
| 12 | Backstop clamp and lossy bounce | keep-engine | — | — | — | — |
| 13 | Field ignores projectiles | keep-gameplay | — | — | — | — |
| 14 | Source–source gravity × 0.1 | keep-gameplay for release; research below (§3) | M plus balance | low | Coupling 1 with bound initial orbits | — (not cheap, see §3) |
| 15 | Sources move on Newtonian paths | keep-exact (sources < 0.13 c even at coupling 1) | — | — | — | — |
| 16 | Player's pull on sources `playerGm` = 30 | keep-gameplay | S | — | A physical player gm is about 2 at the planets' G (planet gm 25.8 for mass 393), but holes and planets don't share one G anyway | — |
| 17 | Instant merges, no gravitational waves | keep-engine | L | — | Inspiral with GW loss | — |
| 18 | Disk particles without dynamics | keep-engine (3000 GPU particles) | L | high | Gas dynamics | — |
| 19 | Lensing: backdrop only, tapered, no lens-on-lens | keep-engine | L | high | Per-object lensing (E18 pilot) | — |
| 19b | A hole that eats a planet doesn't grow | **remove** | S | none | `mergeSources` with the planet's gm: momentum conserved, `r_s` grows by `2·gm_planet/c²` (+13 % at the defaults) | ST-200 |
| 19c | Planet gm tuned to half the engine thrust | keep-gameplay | — | — | — | — |
| 19d | Clock factor as a product over sources | keep-exact (equal to the weak-field sum to first order; neither is exact for several holes) | S | — | Metric of moving sources | — |
| 19e | Tidal stress scale and damage | keep-gameplay | — | — | — | — |
| 20 | Sprite Doppler shifts a white point | keep-engine | L (spectral IRGBU, E26) | medium (extra textures) | Spectral shader | — |
| 21 | Searchlight brightness floor (no outlines since 2026-11-12) | **remove** in part: the floor drops 0.25 → 0.1 | S | none | `B = D^k` with no floor | ST-194 |
| 22 | Parallax backdrop stands in for a sky | keep-engine (a 2D universe has no sky) | — | — | — | — |
| 23 | Lighting emitter extrapolated | keep-engine | M | medium | History lookup | — |
| 24 | No Doppler at the lit object | keep-engine | M | medium | Per-light boost | — |
| 25 | Lighting ignores contraction; one point light; ambient | keep-engine | M | medium | Integrate the annulus | — |
| 26 | Pilot-view disk: Galilean sum, Keplerian Ω, no lensed image, beam D¹ clamped | **remove** the beam exponent and the double-counted transverse Doppler (the baked face-on `g` already has it); keep Galilean (error O(β_hole·β_orb)) and lensing (L) | S | none (one shader line) | Particle point flux `B = D_tot²`, `D_tot = (γ_o(1 − β_o·n̂)/g_o)·√(1 − 1.5 r_s/r)/(1 − β_src·n̂)` | ST-194 (exponent), ST-195 (g terms) |
| 31 | Contraction floor 25 % | **remove** in part: the floor drops to 10 % (owner Q3) | S | none | `L = L₀/γ` | ST-194 |
| 32 | Hitboxes not contracted | keep-engine (E20, Box2D fixture churn) | M | medium | Rebuild the fixtures along v̂ | — |
| 33 | Starfield D clamp [0.25, 4], brightness D¹ [0.25, 3] | **remove** in part: D clamp [0.1, 10], brightness `D²` per star in the pilot view, clamp [0.1, 10] | S | none | Point source in 2D: flux ∝ D² (E10 table), aberration supplies the rest | ST-194 |
| 34 | Tactical computer uses the true state | keep-gameplay (assist by design) | — | — | — | — |
| 35 | Ram damage from the pre-step γ_rel, scales | keep-gameplay | M | low | Energy lost in the solve (postSolve impulses) | — |
| 37 | Computer sounds | keep-gameplay (assist, toggle) | — | — | Silence | — |
| 38 | Disk lighting as one point light | keep-engine | M | medium | Ring of lights | — |
| 39 | Pilot-view lighting emitter delay skipped | keep-engine (error < 1 u) | M | medium | History lookup at `t_ret − |p − e|/c` | — |
| 40 | Received light not Doppler-shifted at the object | keep-engine | M | medium | Per-light Doppler | — |
| 41 | Lit texels ignore contraction | keep-engine | S to M | low | Contract the texel positions and normals | — |
| 42 | Ambient 1 without a star | keep-gameplay | — | — | — | — |
| 43 | Asteroid dome normal blend | keep-gameplay | — | — | — | — |
| 44 | Shadows limited and soft | keep-engine | M | high (fragment cost) | — | — |
| 45 | Enemy AI ignores its own aberration | keep-exact (enemies < 0.5 c) | S | — | `apparentOffset` for the enemy | — |
| 50 | Disk damage rate | keep-gameplay | — | — | Gas model | — |
| 51 (new) | Visual β is low-passed (0.15 s) before the boost and the Doppler | keep-gameplay (nausea on field bounces) | S | — | Instantaneous β | — |
| 52 (new) | Outside view paints the pilot's colours on C_W positions | keep-gameplay (readable default view) | S | — | C_W at rest: no starfield shift, sprites `D = 1/γ_s` (transverse) | — |
| 53 (new) | No gravitational red/blueshift in sprite and star colours | **remove** | S to M | low (a few flops per sprite) | `D_tot = D·g_e/g_o` (stars: `g_e = 1`) | ST-195 |

**Gaps covered by cards that already exist** (not lies, just not built yet): per-corner retarded time / Terrell (ST-075), sky aberration and the bent field band (ST-077), ghosts of removed bodies (ST-079), pilot-view searchlight k = 2 (ST-080). They come before the cards above in the implementation order.

## 2. The most visible wins (recommended for release)

1. **ST-195: gravitational redshift in colours (new row 53).** Hover near a hole and the whole sky turns blue (`1/g_o`: ×3.3 at 1.1 r_s). Things deep in a well look red. This is the "Interstellar" picture, and the warp (row 1) already speeds up the world there, so the colour now tells the same story. It also fixes the pilot-view disk's double-counted transverse Doppler, which overstates the inner edge's redshift by about 9 % (`√(1 − 0.5 r_s/r)` at 3 r_s).
2. **ST-194: exact searchlight exponents and 10 % floors (Q3, rows 21, 26, 31, 33).** In pilot view, sprites get `D³` (2D surface brightness), and stars and disk particles get `D²` (2D point flux; aberration crowds the points by another factor D, so the sky's total also goes as D³). Clamps are [0.1, 10], and contraction stops at 10 % (γ = 10). At 0.9 c the rear sprites drop to D³ ≈ 0.012, so they hit the 0.1 floor, and things ahead blaze with the glow.
3. **ST-198: photons fly until they leave the arena (row 9).** A laser bolt that vanishes after 40 u in the middle of an empty arena is the most obvious lie on screen. It changes the laser range, so it's `needs-user`.
4. **ST-197: light-delayed rocket plume (row 49).** At c = 20 a rocket 40 u away is seen 2 s late, longer than its 1.5 s burn. Today the plume goes out on screen before the pilot could have seen it light up.
5. **ST-196: laser damage [0.1, 10] plus `g_s/g_t` (Q5, row 36).** A head-on shot at 0.9 c closing (D ≈ 4.4 shooter at rest) is no longer cut at 4, and a photon falling into a hole's well hits harder.
6. Small exactness cards: **ST-199** (pilot clock exact past the warp cap, rows 27/28; changes feel only past the cap, mostly near holes) and **ST-200** (a hole grows when it eats a planet).

Brightness exponents in detail (`relativity-effects.md` E10, 2D column): the invariant is `I_ν/ν³`, so surface brightness goes as `D³`. An extended sprite drawn with its mapped geometry gets `D³` per pixel. A point drawn at a fixed pixel size (star, disk particle) carries flux `∝ D³·dθ`, and `dθ' = dθ/D` gives `D²`. The density of points per screen angle rises by D through the aberration mapping itself (ST-077 for stars, already done for the disk), so the sky's total brightness follows `D³` without double counting. This refines the Q3 "D³" answer: D³ is right for surfaces, D² for points. The Outside view (C_W) keeps the mild `D¹`, because its colours are the pilot's colours painted on a distant camera's positions (row 52) and it is the readable default.

## 3. Q4: physical source–source gravity (research only)

Gravitational parameters: a hole has `gm = r_s c²/2` = 200 u³/s² at c = 20 (it scales as c²). A planet has `gm = 0.5·a₀·R²` ≈ 25.8 (it scales as c, through a₀). Levels: one hole and one planet on levels 1 and 2, two holes and one planet on level 3, two of each from level 4. Holes are at least 60 u apart, and planets at least 30 u from anything.

- **From rest at coupling 1**, free-fall time `t_ff = (π/2)·√(d³/(2·gm_tot))`: two holes 60 u apart merge in 25.8 s, and a hole and a planet 60 u apart in 34.4 s (both scale as 1/c). That is the problem row 14 hides.
- **A circular binary at coupling 1 doesn't merge** (sources are Newtonian, and there are no GW losses, row 17). The relative speed is `v = √(gm_tot/a)`, so `β_rel = √(Σr_s/(2a))`, independent of c. Two holes at a = 60 u: β_rel = 0.129, each hole moves at 0.065 c on a 30 u circle, period 146 s. A hole and a planet at 60 u: β_rel = 0.097, period 194 s, and the planet sweeps a 53 u-radius circle around the barycentre.
- **Two holes with planets don't fit the arena.** A circumbinary (P-type) planet is stable only beyond about 2.4·a_bin for a circular equal-mass binary (Holman & Wiegert 1999), which is at least 144 u, more than the 200 u arena allows with a 25 u wall margin. A planet orbiting one hole (S-type) must stay within about 0.27·a_bin ≈ 16 u. That puts it at the edge of the accretion disk (10.2 r_s) and inside the lensing area, which is ugly and deadly.
- **Other disturbances:** asteroids hit planets (a large asteroid is 159 against 393 for the planet, so a hit shifts the orbit visibly), the player's 30 gm pull (row 16) and the boundary field all perturb the orbits.

**Verdict:** this is not cheap. Levels 1 and 2 could get a planet on a circular orbit around the hole (S), but that turns the planet into a 0.09 c moving hazard sweeping the middle of the arena, which needs a balance pass. Levels 3 and up need a new layout rule, or a bigger arena for the two-hole levels. No card before release; row 14 stays. If wanted later: an M card "Bound initial orbits at coupling 1" with a placement solver (hierarchical: binary of holes, planets S-type at ≤ 0.25·a_bin or no planets on two-hole levels), plus a balance re-run.

## 4. Fuel curiosity (owner Q2): a perfect antimatter photon rocket

No fuel mechanic ships (row 6 stays). This section answers the owner's question about how much fuel the ship would need.

**Model.** Matter and antimatter annihilate completely into photons, and all of them leave straight backwards (exhaust speed c, the ideal photon rocket). Momentum and energy balance give `dm/dτ = −m·a/c` for proper acceleration `a`, so for **any** path:

```
m₀/m₁ = exp( ∫ a dτ / c )          (proper acceleration integrated over proper time)
one straight burn 0 → β:  m₀/m₁ = exp(artanh β) = √((1+β)/(1−β))
```

Rapidity is the fuel currency: going there and stopping again costs the square of a one-way burn. Braking with the braking boost burns the same fuel per unit of Δη, just three times faster. Half of the burned mass is antimatter.

**Mass ratios** (the same in the game and in reality, because only β enters):

| Burn | Δη | m₀/m₁ | 0 → β → 0 (two burns) | γ at the end of the burn | Efficiency (γ−1)/(m₀/m₁ − 1) |
|---|---|---|---|---|---|
| 0 → 0.5 c | 0.549 | 1.73 | 3.0 | 1.155 | 21 % |
| 0 → 0.9 c | 1.472 | 4.36 | 19 | 2.294 | 39 % |
| 0 → 0.99 c | 2.647 | 14.1 | 199 | 7.09 | 47 % |
| 0 → 0.999 c | 3.800 | 44.7 | 1999 | 22.4 | 49 % |

**The game's engine.** The proper acceleration is `a₀ = c·γβ(0.9)/5 s = 0.413 c per second`, the same fraction of c at any c (`ENGINE_ACCEL_PER_C`). Every second of full thrust on the pilot clock therefore multiplies the required mass ratio by `e^0.413 = 1.51`, and every second of boosted braking by `e^1.24 = 3.45`. That is 12.6 g if the 1.33 u ship is 20 m long (1 u = 15 m, so c_game = 300 m/s), and 12.6 million g at the real c.

**Absolute numbers for a 100 t dry ship** (fuel = matter + antimatter, energy = fuel·c²):

| Manoeuvre | m₀/m₁ | Fuel | Energy at the real c (3.0·10⁸ m/s) | Energy at the game c (300 m/s) |
|---|---|---|---|---|
| 0 → 0.5 c | 1.73 | 73 t (37 t antimatter) | 6.6·10²¹ J ≈ 1.6 million Mt, about 11 years of world energy use | 6.6·10⁹ J ≈ 1.6 t TNT |
| 0 → 0.9 c | 4.36 | 336 t | 3.0·10²² J | 3.0·10¹⁰ J ≈ 7 t TNT |
| 0 → 0.99 c | 14.1 | 1311 t | 1.2·10²³ J | 1.2·10¹¹ J ≈ 28 t TNT |
| 0 → 0.9 c → 0 | 19 | 1800 t | 1.6·10²³ J | 1.6·10¹¹ J |
| 10 s of thrust | 62 | 6100 t | 5.5·10²³ J | 5.5·10¹¹ J |
| 30 s of thrust | 2.4·10⁵ | 2.4·10¹⁰ kg | 2.2·10²⁷ J ≈ 5.6 s of the Sun's output | 2.2·10¹⁵ J ≈ 0.5 Mt |
| 60 s of thrust (a busy level, estimate) | 5.8·10¹⁰ | 5.8·10¹⁵ kg ≈ 4 Deimos masses | 5.2·10³² J ≈ 16 days of the Sun's output | 5.2·10²⁰ J ≈ a year of world energy use |

At the same β the energy scales as `(c_game/c)² ≈ 10⁻¹²`, while the fuel mass is identical. One oddity: at c = 300 m/s, E = mc² is only 9·10⁴ J/kg, about 1/480 of kerosene, so in the game's universe antimatter would be a poor fuel. (Chemistry wouldn't survive such a c anyway: the fine-structure constant scales as 1/c.) The 60 s figure is an estimate. The balance bots could log the real engine-on time per level if the owner wants a measured number.

## 5. Implementation order (follow-up cards, M7, after ST-080 and before ST-189)

1. ST-194 Exact searchlight exponents and 10 % floors (Q3)
2. ST-195 Gravitational red- and blueshift in colours
3. ST-196 Laser damage: wider clamp and gravitational photon energy (Q5)
4. ST-197 Pilot view: light-delayed rocket plume
5. ST-198 Photons fly until they leave the arena (needs-user)
6. ST-199 Pilot clock exact past the warp cap
7. ST-200 A hole that eats a planet grows

ST-189 (rebalance) comes after all of them, because ST-196 and ST-198 change the laser's damage and range.

## 6. Open questions for the owner

- **ST-198:** photons fly until they leave the arena, so the laser range grows from 40 u to the whole arena. Rec: yes, plus a web-check on photon body counts, and ST-189 rebalances the enemies' laser range.
- **ST-199:** past the warp cap the player's own guns and turning slow down in real time (exact: the pilot's clock runs at `warp·g/γ` per real second). Rec: yes. It only shows near holes or above γ ≈ 10.
- **Q4, later:** a "Bound initial orbits at coupling 1" card after release (§3)? Rec: no for release, revisit with a bigger arena.

## References

- Holman & Wiegert (1999), *Long-Term Stability of Planets in Binary Systems*: https://ar5iv.labs.arxiv.org/html/astro-ph/9809315 (2.39·a_bin circumbinary limit as quoted in https://arxiv.org/pdf/2108.07815)
- Relativistic rocket equation: https://en.wikipedia.org/wiki/Relativistic_rocket ; photon rocket rest mass `m = m₀·exp(−ψ)`: https://arxiv.org/pdf/2007.03530
- Brightness exponents in 2D, Doppler and disk redshift: `docs/research/relativity-effects.md` E4, E10, E15, E17 (Kraus 2000 and others cited there)
