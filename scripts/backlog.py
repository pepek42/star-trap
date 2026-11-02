#!/usr/bin/env python3
"""Backlog helper for /auto-dev, so agents never have to read the whole backlog.

Usage (from the repo root):
  scripts/backlog.py next          print the next card to work on (full text), or NONE
  scripts/backlog.py show ST-042   print one card (looks in the archive too)
  scripts/backlog.py archive ST-042 [ST-043 ...]
                                   move done cards to docs/backlog-done.md and drop
                                   them from the implementation order
  scripts/backlog.py archive       same, for every done card in docs/backlog.md
  scripts/backlog.py list          one line per open card: ID, status, agent, size, title

"Next" = the first card in `## Implementation order` (or in file order, if there is
no such list) with `status: ready` whose `depends` are all done. A card is done when
its status starts with `done` or it is in the archive.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIVE = ROOT / "docs" / "backlog.md"
DONE = ROOT / "docs" / "backlog-done.md"
DONE_HEADER = (
    "# Backlog archive\n\n"
    "Finished cards, moved here from `docs/backlog.md` by `scripts/backlog.py archive`. "
    "Kept for history; agents don't need to read this file. "
    "Look up one card with `scripts/backlog.py show ST-NNN`.\n"
)

CARD_RE = re.compile(r"^### (ST-\d+)\b")
ID_RE = re.compile(r"ST-\d+")


def is_boundary(line):
    return line.startswith("### ") or line.startswith("## ") or line.strip() == "---"


def parse(lines):
    """Return {id: (start, end, milestone)} with [start, end) line ranges."""
    cards, milestone, i = {}, None, 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("## "):
            milestone = line[3:].strip()
        m = CARD_RE.match(line)
        if m:
            j = i + 1
            while j < len(lines) and not is_boundary(lines[j]):
                j += 1
            cards[m.group(1)] = (i, j, milestone)
            i = j
            continue
        i += 1
    return cards


def field(block, name):
    for line in block:
        if line.startswith(f"- {name}:"):
            return line.split(":", 1)[1].strip()
    return ""


def read(path):
    return path.read_text().splitlines(keepends=True) if path.exists() else []


def done_ids(live, live_cards):
    ids = set(parse(read(DONE)).keys())
    for cid, (s, e, _) in live_cards.items():
        if field(live[s:e], "status").startswith("done"):
            ids.add(cid)
    return ids


def order_section(live):
    """Return (start, end) of the `## Implementation order` section, or None."""
    for i, line in enumerate(live):
        if line.strip() == "## Implementation order":
            j = i + 1
            while j < len(live) and not live[j].startswith("## "):
                j += 1
            return i, j
    return None


def cmd_next():
    live = read(LIVE)
    cards = parse(live)
    done = done_ids(live, cards)
    sec = order_section(live)
    if sec:
        order = []
        for line in live[sec[0]:sec[1]]:
            m = re.match(r"^\s*\S+\.\s+(ST-\d+)", line)
            if m:
                order.append(m.group(1))
        order += [c for c in cards if c not in order]
    else:
        order = list(cards)
    waiting = []
    for cid in order:
        if cid not in cards:
            continue
        s, e, _ = cards[cid]
        block = live[s:e]
        if field(block, "status") != "ready":
            continue
        missing = [d for d in ID_RE.findall(field(block, "depends")) if d not in done]
        if missing:
            waiting.append(f"{cid} waits on {', '.join(missing)}")
            continue
        sys.stdout.write("".join(block).rstrip() + "\n")
        return 0
    print("NONE")
    for w in waiting[:10]:
        print(w)
    return 1


def cmd_show(cid):
    for path in (LIVE, DONE):
        lines = read(path)
        cards = parse(lines)
        if cid in cards:
            s, e, _ = cards[cid]
            sys.stdout.write("".join(lines[s:e]).rstrip() + "\n")
            return 0
    print(f"{cid} not found", file=sys.stderr)
    return 1


def cmd_list():
    live = read(LIVE)
    for cid, (s, e, _) in parse(live).items():
        b = live[s:e]
        title = b[0][4:].strip()[len(cid):].strip()
        print(f"{cid} | {field(b, 'status')} | {field(b, 'agent')} | {field(b, 'size')} | {title}")
    return 0


def cmd_archive(ids):
    live = read(LIVE)
    cards = parse(live)
    if not ids:
        ids = [c for c, (s, e, _) in cards.items() if field(live[s:e], "status").startswith("done")]
    for cid in ids:
        if cid not in cards:
            print(f"{cid} not in docs/backlog.md", file=sys.stderr)
            return 1
        s, e, _ = cards[cid]
        if not field(live[s:e], "status").startswith("done"):
            print(f"{cid} is not done; not archived", file=sys.stderr)
            return 1

    done = read(DONE) or DONE_HEADER.splitlines(keepends=True)
    moved = set(ids)

    def append_to_done(milestone, text, at_top=False):
        nonlocal done
        heading = f"## {milestone}\n"
        if heading not in done:
            if done and done[-1].strip():
                done.append("\n")
            done += [heading, "\n"]
            idx = len(done)
        else:
            idx = done.index(heading) + 1
            if at_top:
                done.insert(idx, "\n")
                idx += 1
            else:
                while idx < len(done) and not done[idx].startswith("## "):
                    idx += 1
        chunk = text.rstrip("\n").splitlines(keepends=True) + ["\n", "\n"]
        done[idx:idx] = chunk

    for cid in sorted(ids, key=lambda c: cards[c][0]):
        s, e, ms = cards[cid]
        append_to_done(ms, "".join(live[s:e]))

    # Rebuild live: drop moved cards and their implementation-order lines.
    sec = order_section(live)
    out = []
    starts = {cards[c][0]: cards[c][1] for c in moved}
    i = 0
    while i < len(live):
        if i in starts:
            i = starts[i]
            continue
        line = live[i]
        m = re.match(r"^\s*\S+\.\s+(ST-\d+)", line)
        if sec and sec[0] <= i < sec[1] and m and m.group(1) in moved:
            i += 1
            continue
        out.append(line)
        i += 1

    # Move the preamble of milestones that have no cards left; drop empty separators.
    out = move_empty_milestones(out, append_to_done)
    text = re.sub(r"\n{3,}", "\n\n", "".join(out))
    text = re.sub(r"(\n---\n)(\n---\n)+", r"\1", text)
    LIVE.write_text(text)
    DONE.write_text(re.sub(r"\n{3,}", "\n\n", "".join(done)))
    print(f"archived {len(ids)} card(s)")
    return 0


def move_empty_milestones(lines, append_to_done):
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("## ") and line.strip() != "## Implementation order":
            j = i + 1
            while j < len(lines) and not lines[j].startswith("## "):
                j += 1
            section = lines[i:j]
            if not any(CARD_RE.match(l) for l in section):
                body = "".join(l for l in section[1:] if l.strip() != "---").strip()
                if body:
                    append_to_done(line[3:].strip(), body + "\n", at_top=True)
                i = j
                continue
        out.append(line)
        i += 1
    return out


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    cmd, args = argv[0], argv[1:]
    if cmd == "next":
        return cmd_next()
    if cmd == "show" and len(args) == 1:
        return cmd_show(args[0])
    if cmd == "list":
        return cmd_list()
    if cmd == "archive":
        return cmd_archive(args)
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
