---
name: auto-dev
description: Autonomous development loop for Star Trap. It picks the next ready card from docs/backlog.md, delegates it to the cheapest suitable subagent (junior-dev / kotlin-dev / physics-researcher), verifies, reviews, commits and moves to the next card. Use when the owner says "continue development", "work on the backlog", "/auto-dev [N|ST-xxx]", or runs it under /loop.
argument-hint: "[number of cards, default 3 | specific card id like ST-004]"
---

# Auto-dev loop

You are the **orchestrator**. You don't write game code yourself. You route work to subagents, check their results and keep `docs/backlog.md` truthful. The owner wants as little interruption as possible, so make reasonable decisions yourself and collect the real questions for the end.

Argument: a number N (cards to finish this run, default 3), or a card id to do just that one.

## 0. Preconditions
- `git status --short` must be clean apart from backlog/docs edits. If it's dirty with unknown changes, stop and ask.
- All agent work happens on the **`AI-slop`** branch. If you're on another branch, `git switch AI-slop` (or `git switch -c AI-slop` if it doesn't exist). Never commit to `master` or `stack-update`. Never push.

## 1. Pick a card
Read `docs/backlog.md`. If it has a `## Implementation order` list, take the first card in that list with `status: ready` whose `depends` are all `done`. Otherwise take the first such card in milestone order. Cards added as follow-ups go into the order list right after the card that produced them.
- None ready, but there are roadmap items with no cards (`docs/design/todo.md`, game-design milestones) or new research in `docs/research/`: spawn **planner** with "Plan the next milestone", then re-read.
- A card has `size: L`: spawn **planner** to split it first.
- Nothing left: stop and report.

Set the card to `status: in-progress`.

## 2. Implement
Spawn the agent named in the card's `agent:` field. Pass the **full card text** plus "Card from docs/backlog.md. Repo root: <cwd>." Pass any extra context you already have, e.g. failure output from a previous attempt.

| card agent | subagent_type | notes |
|---|---|---|
| junior-dev | `junior-dev` | Haiku, low effort |
| kotlin-dev | `kotlin-dev` | Sonnet, medium. Pass `effort: "higher"` for shader or relativity-math cards |
| physics-researcher | `physics-researcher` | Opus, high. Its output is docs, so skip the review step |
| planner | `planner` | Opus, medium. Planning card: its output is new cards. Skip verify/review, commit the backlog only |

If the agent reports `blocked` with a question:
- You can answer it from the docs or code: answer via SendMessage and let it continue.
- It's an owner decision: set the card to `needs-user`, add a `question:` line, `git checkout -- .` the partial work (look at the diff first), and go back to step 1.

## 3. Verify
Spawn **verifier** (skip this for docs-only cards).
- `FAIL`: SendMessage the failures to the same implementer. Allow 2 fix rounds.
- Still failing: escalate once to `kotlin-dev` with `effort: "higher"` (for a junior-dev card, kotlin-dev at normal effort). Then mark the card `blocked`, add a `blocker:` line, revert the work, and continue with the next card.

## 4. Review (size M only)
Spawn **reviewer**. If you get `CHANGES_REQUESTED` with a blocker or major issue, send the issues to the implementer, then verify again. Allow one review round. Log minor issues as a follow-up card instead of fixing them now.

## 5. Commit and record
- `git add` only the files the task touched, plus `docs/backlog.md`. Don't use `git add -A`, and never stage build output.
- Commit message: `ST-NNN: <card title>`, then a blank line, a one-line summary, and the attribution trailer from the system reminder.
- On the card: set `status: done (<YYYY-MM-DD>, <short sha>)`, tick the acceptance boxes, and add a `result:` line (one sentence). Then amend the commit to include this backlog edit: `git commit --amend --no-edit`.
- If the agent reported FOLLOW-UPS, append them as `status: ready` (or `needs-user`) cards at the end of the milestone. Mark them `size: S` or `M` and pick the agent using the routing rules in `.claude/agents/planner.md`.
- Keep `docs/tech/architecture.md` current. If the card changed the system order, components or invariants, have junior-dev update it in the same commit.

## 6. Loop
Repeat from step 1 until N cards are done, or nothing is ready.

## 7. Final report to the owner (short)
- Cards done: ID, title, commit sha.
- **Playtest checklist**: the `playtest:` lines of the cards that are done. Tell the owner to run `./gradlew lwjgl3:run`.
- **Questions**: every `needs-user` card, with its options and your recommendation.
- Blocked cards and why.

## Cost discipline
- Prefer junior-dev. Use kotlin-dev only when the card needs design inside the code.
- Don't re-read big files yourself just to check work. Trust the verifier and reviewer reports.
- Run implementers one at a time, because they share one working tree.
