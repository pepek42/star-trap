# Agent-driven development workflow

Star Trap is developed mostly by AI agents, and the owner gives as little input as possible. The setup works in **Claude Code** and **opencode**; this file explains it.

## Tool layout

| What | Source (edit here) | Claude Code | opencode |
|---|---|---|---|
| Project instructions | `AGENTS.md` | `CLAUDE.md` imports it (`@AGENTS.md`) | reads `AGENTS.md` directly |
| Agents | `agents/<name>.md` (neutral: `tier`, `readonly`, `web`) | `.claude/agents/` (generated) | `.opencode/agent/` (generated) |
| Skills | `.claude/skills/<name>/SKILL.md` | native | native (opencode reads `.claude/skills/`); `/auto-dev` and `/plan-backlog` via `.opencode/command/` (generated) |

After editing anything in `agents/`, run `scripts/sync-agents.py` (`--check` reports stale files). Tiers map to models per tool: in Claude Code cheap = Haiku/low, standard = Sonnet/medium, strong = Opus/high (an `effort:` line in the source overrides the default). In opencode the generated agents have no `model:`, so they use the session's model, unless `agents/models.json` maps tiers to opencode model ids, e.g. `{"opencode": {"cheap": "ollama/qwen3.5", "standard": "lmstudio/qwen/qwen3.5-9b"}}`.

## The team (`agents/`)

| Agent | Tier | Role | Writes |
|---|---|---|---|
| `planner` | strong (medium effort) | Roadmap + research → small, ordered task cards, routed to the cheapest capable agent | `docs/backlog.md` |
| `kotlin-dev` | standard (orchestrator may raise it) | Implements size-M cards: systems, components, algorithms, shaders, with tests | code + tests |
| `junior-dev` | cheap | Size-S mechanical cards with an exact spec | code, config, docs |
| `verifier` | cheap | Finds the root cause of unclear build/test failures, with file:line. Read-only. auto-dev runs Gradle itself and calls it only when needed | — |
| `reviewer` | standard | Reviews the diff against the card, project invariants and physics. Read-only | — |
| `physics-researcher` | strong | Relativity, gravity and force-field physics → game-ready specs with formulas, cost and verdicts | `docs/research/*.md` |

Most of the volume goes to the cheap and standard tiers. The strong tier is used only for planning and physics research, which happen rarely and where mistakes cost the most.

## Skills (`.claude/skills/`)

- **`/auto-dev [N | ST-xxx]`**: the orchestrator loop. It picks a ready card (`scripts/backlog.py next`) → the implementer → Gradle build → (reviewer for M) → commits `ST-NNN: title` on the `AI-slop` branch → moves lasting facts into the docs and closes (deletes) the card. At the end it reports a playtest checklist and open questions. Unattended in Claude Code: `/loop /auto-dev 3`.
- **`/plan-backlog <idea | research doc>`**: has the planner turn something into cards.

## Lifecycle of a card

```mermaid
flowchart LR
  todo["docs/design/todo.md<br/>game-design.md"] --> planner
  research["docs/research/*.md"] --> planner
  planner --> backlog["docs/backlog.md<br/>status: ready"]
  backlog --> impl["junior-dev / kotlin-dev"]
  impl --> build["gradle test<br/>(verifier if unclear)"]
  build -- FAIL (≤2 rounds) --> impl
  build -- PASS --> reviewer
  reviewer -- changes --> impl
  reviewer -- approve --> commit["commit ST-NNN<br/>facts → docs<br/>card closed"]
  impl -- owner decision needed --> needsuser["status: needs-user<br/>question + recommendation"]
```

## Where the owner comes in

1. **Questions**: `needs-user` cards, and §11 of `docs/design/game-design.md`. Each one has a recommendation, so a reply like "go with the recommendations" is enough.
2. **Playtests**: each `/auto-dev` run ends with a checklist from the `playtest:` lines. Run `./gradlew lwjgl3:run`.
3. **New wishes**: add them to `docs/design/todo.md` (in any form). The planner picks them up when the backlog runs dry, or right away with `/plan-backlog`.

## Conventions for agents

- `AGENTS.md` is loaded automatically. Read only the doc sections a card names on its `refs:` line, plus the parts of `docs/tech/architecture.md` that cover the code you touch.

## Saving tokens

**The biggest cost is a long orchestrator session.** Every model call re-sends the whole conversation. Measured 2026-11-08 from Claude Code session logs: a fresh session starts at about 45k tokens (tool definitions, instructions, memory); sessions that did 6 or more cards ran 150 to 370 calls at about 107k tokens per call and hit the 165k compaction limit, which is 15M to 40M cached tokens per session. Screenshots read for visual checks (up to about 30 per session) stayed in the context for every later call. So:
- Run `/auto-dev 3`, then clear the context (`/clear`, or a new opencode session) before the next run. The skill stops and says so.
- The orchestrator opens a screenshot only on a web-check FAIL or for a visual playtest card.
- Small local models (e.g. a 9B model with a 32k window) can't run the orchestrator; give them junior-dev cards only, through `agents/models.json`.

Other rules:
- `docs/backlog.md` holds only open cards. Finished cards are closed (deleted): their lasting knowledge goes into the docs, their text stays in git history (`scripts/backlog.py show ST-NNN` lists the commits). Nobody reads the backlog in full: `scripts/backlog.py next | show ST-NNN | list | close [ST-NNN]`.
- Only cards near the top of the order carry a full spec; far-future cards are short stubs until they come up, so specs don't go stale.
- Cards carry a `refs:` line naming the doc sections the implementer needs, so nobody reads all of `docs/research/relativity-effects.md`.
- The orchestrator runs Gradle itself; the verifier agent is only for unclear failures.
- Trivial review findings are fixed in the same round; the rest of a card's follow-ups go into one card.
- One card per commit, on the `AI-slop` branch only. Never commit to `master` or `stack-update`, and never push.
- Pure logic is pulled out and unit tested. GL code stays thin.
- If a card changes the architecture, `docs/tech/architecture.md` is updated in the same commit.

## Visual checks

Agents can render the game headlessly-ish (a display is needed) and inspect the result with an image-reading tool (each image stays in the context, so look only when needed):

```bash
./gradlew lwjgl3:run --args="--screenshot=/abs/path/st.png --frames=120"
```

Options (parsed by `parseLaunchOptions` in `core/.../debug/ScreenshotHarness.kt`):

- `--screenshot=<path.png>`: enables the harness. Use an absolute path, because the run task's working dir is `assets/`.
- `--frames=<N>`: frames to render before capturing (default 180).
- `--scene=play|menu|cleared`: `play` skips the main menu (default), `menu` stays on it, `cleared` opens the between-levels screen.
- `--menu-page=main|new_game|scenarios|settings`: the main-menu page to open (with `--scene=menu`).
- `--settings-tab=controls|ship_computer|graphics|audio|dev`: the Settings tab to open (with `--menu-page=settings`).
- `--start-level=N`: the run starts at level N (New game offers 1 and 5).
- `--scenario=<id>`: plays a built-in scenario (`flight_school`, `combat`, `relativity`, `gravity`; see `scenario/Scenarios.kt`).
- `--thrust`: hold the thrusters during the run.
- `--seed=<long>`: seeds all gameplay randomness (one `RandomXS128` in the DI context). Without it the seed comes from the clock. Screenshot runs also use a fixed 1/60 s frame delta, so the same args give byte-identical PNGs.
- `--near-asteroids=<n>`: additionally places n large asteroids in a ring 8-15 u around the player (seeded RNG), so near-field effects such as the pilot view are visible on level 1.
- `--near-enemies=<n>`: places n enemy ships evenly on a 12 u circle around the player (starting straight above), so enemies and their markers are in view on any level. Web: `?near-enemies=2`.
- `--hole-near[=<u>]`: places the first black hole `<u>` (default 25, `HOLE_NEAR_DISTANCE`) right of the player spawn so the hole and its disk are in view. `--invulnerable` keeps the player alive (also past the horizon) for long runs near a hole. Harness only; normal spawning and death are unchanged. Web: `?hole-near=18&invulnerable`.
- `--controls=twin|auto`: picks the control scheme (`ControlScheme.fromFlag`); wins over the saved menu setting. Also works outside screenshot runs.
- `--debug-panel`: allows the F1 debug panel even when the "Debug mode" setting is off. Also works outside screenshot runs.
- `--play`: skips the main menu and starts a level, also outside screenshot runs.
- `--pilot-view`: starts in the Pilot view (light-delayed, lit sprites) instead of the saved or default perspective. Web: `?pilot-view`.
- `--shadow-demo`: shadow test scene (ST-098): a hole 25 u right of the player (as `--hole-near`), a planet 15 u right of the player, an asteroid in the planet's shadow and a control asteroid at the same distance from the hole outside it, all at rest. Use with `--invulnerable --frames=60 --seed=42`; toggle "shadows" in the debug panel (or flip the `shadowsEnabled` default) for an A/B. Web: `?shadow-demo`.
- `--no-shadows`: starts with shadows off (`RelativitySettings.shadowsEnabled = false`), so `--shadow-demo` can be shot with and without shadows. Harness only. Web: `?shadow-demo&no-shadows`.
- `--star-angle=<deg>`: overrides the level's rolled star direction (ST-099; the roll still happens, so spawns don't shift). Web: `?star-angle=45`.
- `--no-lensing`: starts with backdrop lensing off (`RelativitySettings.lensingEnabled = false`, ST-088), for A/B shots. The accretion disk covers much of the Einstein ring in both views. Web: `?no-lensing`.
- `--no-star`: starts with the distant star light off (`RelativitySettings.starLightEnabled = false`), ambient back to 1. Web: `?no-star`.

**Web equivalent.** The web launcher maps the URL query to the same flags (`queryToLaunchArgs`): `index.html?seed=42&play&controls=twin&hole-near`. `screenshot` is ignored on web.

No player input is sent otherwise. After frame N the framebuffer is saved as PNG and the app exits by itself. Wrap the command in `timeout 120` to be safe. Unknown flags and invalid values are ignored.

## Optional: IntelliJ MCP

`.mcp.json` registers the IntelliJ MCP server (`idea`). When the IDE is running, agents can use `mcp__idea__get_file_problems` for Kotlin inspections, since the project has no CLI linter. Gradle remains the source of truth.

## Web check

`scripts/web-check.sh [--play] [--query="seed=42&play&hole-near"] [--out=/abs/path.png]` builds the TeaVM bundle (`teavm:generateJavaScript`, about 25 s) and opens it in headless Firefox (`scripts/web-smoke.mjs`: Node >= 22, WebDriver BiDi, no npm deps). `--play` loads `index.html?seed=42&play`, so a level starts without clicking; `--query=...` replaces that query (implies `--play`-style waiting). It prints the console log, the canvas size and `RESULT: PASS|FAIL`, and saves a screenshot to inspect with the image tool. TeaVM logs uncaught Java exceptions at info level ("Fatal Error", "Exception N:"); the script matches on that text. `node scripts/web-smoke.mjs --query=...` reruns the browser part on an already built bundle.

Web gotchas found so far:
- **Component constructors.** gdx-teavm's `ClassReflection.getConstructor(c)` returns `getConstructors()[0]`, not the no-arg constructor. Components must have exactly one public, parameterless constructor (no Kotlin constructor parameters with defaults, no `data class`); `ComponentConstructorTest` enforces it.
- **Reflection patterns** in `teavm/build.gradle.kts` are globs: a package needs `.**` (`com.github.pepek42.asteroids.component.**`).
- **Stale reflection list.** An incremental `teavm:generateJavaScript` kept an old reflection class list, so a new component (`EnemyComponent`) failed only at runtime with "missing no-arg constructor" (2026-11-07). `web-check.sh` therefore runs `teavm:clean` first. The default `--play` query starts level 1, which has no enemies; use `--query="seed=42&play&near-enemies=2"` to exercise them.
- **Same error, cause unknown (ST-163, 2026-11-07).** `WeaponLoadoutComponent` failed with "missing no-arg constructor" even after `teavm:clean`, while it was in the reflection list. It passed once the class held only fields, the constructor and `reset()`, with its logic in top-level extension functions; whether that was the fix is unconfirmed. If a new component hits this, try that shape first.
- The 2026-11-04 TeaVM worker SIGSEGVs were faulty RAM, not TeaVM; `WEB_XINT=1` (interpreter-only worker) remains as a fallback.

## Web release (GitHub Pages)

`.github/workflows/pages.yaml` builds `teavm:generateJavaScript` and deploys `teavm/build/dist/js/webapp` to https://pepek42.github.io/star-trap/ (launch flags work as a query, e.g. `?seed=42&play`). It runs only by hand: Actions → "Deploy web build to GitHub Pages" → Run workflow. One-time setup: Settings → Pages → Source "GitHub Actions"; the `github-pages` environment only allows the default branch until another branch (e.g. `AI-slop`) is added under Settings → Environments → github-pages → Deployment branches. Run `scripts/web-check.sh --play` before publishing.
