# Star Trap docs

Agent instructions live in [`../AGENTS.md`](../AGENTS.md) (Claude Code reads it through `CLAUDE.md`); agent prompts in [`../agents/`](../agents/).

| Doc | What |
|---|---|
| [design/todo.md](design/todo.md) | The owner's raw roadmap and wishes (input) |
| [design/game-design.md](design/game-design.md) | Game design document: pitch, pillars, modes, entities, milestones, open questions |
| [design/main-menu.md](design/main-menu.md) | Main menu design: layout, controls list, player settings, persistence |
| [design/physics-lies.md](design/physics-lies.md) | Register of every deliberate physics departure (FAKE / APPROX / ENGINE) |
| [research/relativity-effects.md](research/relativity-effects.md) | Physics research: SR/GR effects, formulas, 2D adaptation, cost, verdicts (by `physics-researcher`) |
| [backlog.md](backlog.md) | Open task cards that agents implement (`/auto-dev`); query with `scripts/backlog.py` |
| [tech/architecture.md](tech/architecture.md) | How the code works now: ECS order, physics loop, invariants, known issues |
| [tech/balance.md](tech/balance.md) | Balance pass (ST-101): headless sim sweep, tables, chosen c / engine / asteroid / damage constants |
| [tech/sound.md](tech/sound.md) | Procedural SFX generator (`tool:generateSounds`), recipes, tuning |
| [tech/ktx-cheatsheet.md](tech/ktx-cheatsheet.md) | libGDX/KTX/Ashley/Box2D idioms and gotchas |
| [tech/dev-workflow.md](tech/dev-workflow.md) | The agent team (Claude Code + opencode), skills, card lifecycle, token budget, and where the owner comes in |
