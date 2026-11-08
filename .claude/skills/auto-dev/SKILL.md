---
name: auto-dev
description: Autonomous development loop for Star Trap. It picks the next ready card from docs/backlog.md, delegates it to the cheapest suitable agent (junior-dev / kotlin-dev / physics-researcher), verifies, reviews, commits and moves to the next card. Use when the owner says "continue development", "work on the backlog", "/auto-dev [N|ST-xxx]", or runs it under /loop.
argument-hint: "[number of cards, default 3 | specific card id like ST-004]"
---

# Auto-dev loop

You are the **orchestrator**. You don't write game code yourself. You route work to the project agents (`agents/*.md`), check their results and keep `docs/backlog.md` truthful. The owner wants as little interruption as possible, so make reasonable decisions yourself and collect the real questions for the end.

Argument: a number N (cards to finish this run, default 3), or a card id to do just that one.

## 0. Preconditions
- `git status --short` must be clean apart from backlog/docs edits. If it's dirty with unknown changes, stop and ask.
- Work on the **`AI-slop`** branch (`git switch AI-slop`, or `git switch -c AI-slop` if missing). Never commit to `master` or `stack-update`. Never push.

## 1. Pick a card
Run `scripts/backlog.py next` (or `scripts/backlog.py show ST-xxx`). It prints the first `ready` card in `## Implementation order` whose `depends` are all done. **Don't read `docs/backlog.md` in full**; to edit a card, Grep for its `### ST-NNN` line and Read only that range.
- `NONE`, but there are roadmap items with no cards (`docs/design/todo.md`, game-design milestones) or new research in `docs/research/`: run the **planner** agent with "Plan the next milestone", then run `next` again.
- A card has `size: L`: have the **planner** split it first.
- Nothing left: stop and report.

Set the card to `status: in-progress`.

## 2. Implement
Run the agent named in the card's `agent:` field. Give it: "Card ST-NNN from docs/backlog.md (`scripts/backlog.py show ST-NNN`). Repo root: <cwd>." plus any extra context you already have (e.g. the failing lines from a previous attempt). Don't paste docs into the prompt; the agent reads its own `refs:`.

| card agent | tier | notes |
|---|---|---|
| junior-dev | cheap | |
| kotlin-dev | standard | for shader or relativity-math cards, raise its effort or use a stronger model if your tool allows |
| physics-researcher | strong | output is docs: skip the review step |
| planner | strong | planning card: output is new cards. Skip verify/review, commit the backlog only |

If the agent reports `blocked` with a question:
- You can answer it from the docs or code: send the answer to the same agent (continue its session if your tool supports that, otherwise rerun it with the card plus the answer).
- It's an owner decision: set the card to `needs-user`, add a `question:` line, look at the diff, `git checkout -- .` the partial work, and go back to step 1.

## 3. Verify
Run the build yourself (skip for docs-only cards):
`./gradlew core:test lwjgl3:compileKotlin teavm:compileKotlin --console=plain -q 2>&1 | tail -60` (timeout 10 min). No output plus exit code 0 means PASS. Also check `git status --short` for stray files (build output, `*.log`, scratch files).
- **Web check** (`scripts/web-check.sh --play`, ~1 min; `docs/tech/dev-workflow.md` "Web check"): after a card that touches a web-risk area (`AGENTS.md` "Web first"), after every 3rd committed card, and once at the end of the run. Trust its `RESULT:` line. A FAIL counts as a test failure of the card that caused it.
- Run the **verifier** agent only when the failure output doesn't show the cause clearly.
- `FAIL`: send only the failing lines to the same implementer. Allow 2 fix rounds.
- Still failing: escalate once to `kotlin-dev` at a stronger setting (for a junior-dev card, kotlin-dev as normal). Then mark the card `blocked`, add a `blocker:` line, revert the work, and continue with the next card.

## 4. Review (size M only)
Run the **reviewer** agent. On `CHANGES_REQUESTED` with a blocker or major issue, send the issues to the implementer, then verify again. Allow one review round. Send `minor` issues marked trivial along in that same round (or, if there is no round, fix them yourself when they are a line or two). Only non-trivial minor issues become a follow-up card, and all of a card's follow-ups go into **one** card.

## 5. Commit and record
- `git add` only the files the task touched, plus `docs/backlog.md`. No `git add -A`, never stage build output.
- Commit message: `ST-NNN: <card title>`, a blank line, a one-line summary, then any attribution trailer your tool requires.
- Make sure anything worth keeping from the card (design decisions, tunables, invariants, known limits) is in the docs (`architecture.md`, design docs, `physics-lies.md`); the card is about to be deleted. Then set `status: done`, run `scripts/backlog.py close ST-NNN`, `git add docs/backlog.md`, and `git commit --amend --no-edit`.
- FOLLOW-UPS from the agent become **one** `status: ready` (or `needs-user`) card at the end of the milestone, placed in `## Implementation order` right after the card that produced it: `size: S` or `M`, agent per the routing rules in `agents/planner.md`, a `refs:` line, and bump the next free ID in the backlog header.
- A new sim effect that hurts readability needs an assist (`AGENTS.md` "Vision") or a follow-up card for one.
- A new or changed FAKE/APPROX gets its row in `docs/design/physics-lies.md` in the same commit.
- If the card changed the system order, components or invariants, `docs/tech/architecture.md` is updated in the same commit (junior-dev can do it).
- If `agents/` changed, run `scripts/sync-agents.py` and commit the generated files too.

## 6. Loop
Repeat from step 1 until N cards are done, or nothing is ready.

## 7. Final report to the owner (short)
- Cards done: ID, title, commit sha.
- **Playtest checklist**: the `playtest:` lines of the cards that are done. Playtest in the browser (`./gradlew teavm:javaScriptDevServer`, then http://localhost:8080) or with `./gradlew lwjgl3:run`.
- **Questions**: every `needs-user` card, with its options and your recommendation.
- Blocked cards and why.
- End with: "Clear the context before the next run" (`/clear` in Claude Code, a new session in opencode).

## Token budget
Every model call re-sends the whole conversation, so a long orchestrator session costs much more per card than a short one (measured 2026-11-08: sessions doing 6+ cards averaged ~107k tokens per call and hit the 165k compaction limit).
- **Stop after N cards (default 3)** and tell the owner to clear the context. Under `/loop`, each iteration does at most 3 cards in a fresh context.
- **Screenshots:** don't open the web-check screenshot when `RESULT: PASS`. Open at most one image, and only on FAIL or for a card whose `playtest:` is visual. Images stay in the context for every later call.
- Prefer junior-dev. Use kotlin-dev only when the card needs design inside the code.
- Don't re-read big files to check an agent's work. Trust the build output and the reviewer report; look at `git diff --stat`, not the full diff.
- Never load the whole backlog or a whole research doc; use `scripts/backlog.py` and Grep + ranged Read.
- Pass build failures as the failing lines only, not the full log.
- Run implementers one at a time, because they share one working tree.
