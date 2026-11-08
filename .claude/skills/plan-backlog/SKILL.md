---
name: plan-backlog
description: Turn a Star Trap roadmap item, idea or research doc into task cards in docs/backlog.md via the planner agent. Use when the owner says "plan X", "add X to the backlog", or after new research lands in docs/research/.
argument-hint: "<roadmap item, idea, or path to a research doc>"
---

1. If the argument is a new idea that isn't in `docs/design/todo.md` or `docs/design/game-design.md` yet, add one line for it under the right heading in `game-design.md` (Ideas / parking lot), so the design doc stays the source of truth.
2. Run the **planner** agent (`agents/planner.md`) with: "Plan: <argument>. Append cards to docs/backlog.md following your card format and routing rules."
3. Show the owner the new cards as a compact list (ID, title, agent, size) and any `needs-user` questions. Don't commit unless asked; auto-dev commits the backlog together with the first card it implements.
