#!/usr/bin/env python3
"""Backlog helper for /auto-dev, so agents never have to read the whole backlog.

Usage (from the repo root):
  scripts/backlog.py next          print the next card to work on (full text), or NONE
  scripts/backlog.py show ST-042   print one open card; for a closed card, its commit(s)
  scripts/backlog.py close ST-042 [ST-043 ...]
                                   delete done cards from docs/backlog.md and from the
                                   implementation order (git history keeps them)
  scripts/backlog.py close         same, for every done card in docs/backlog.md
  scripts/backlog.py list          one line per open card: ID, status, agent, size, title

"Next" = the first card in `## Implementation order` (or in file order, if there is
no such list) with `status: ready` whose `depends` are all done. A card is done when
its status starts with `done`, or when it is no longer in the backlog (closed) and its
ID is below the next free ID. There is no archive: lasting knowledge from a finished
card belongs in the docs, and the card text stays in git history.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIVE = ROOT / "docs" / "backlog.md"
NEXT_FREE_RE = re.compile(r"next free ID is \*\*ST-(\d+)\*\*")

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


def next_free(live):
    m = NEXT_FREE_RE.search("".join(live[:20]))
    return int(m.group(1)) if m else None


def is_done(cid, live, live_cards):
    """Done = marked done in the backlog, or closed (absent and below the next free ID)."""
    if cid in live_cards:
        s, e, _ = live_cards[cid]
        return field(live[s:e], "status").startswith("done")
    nf = next_free(live)
    return nf is not None and int(cid[3:]) < nf


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
        missing = [d for d in ID_RE.findall(field(block, "depends")) if not is_done(d, live, cards)]
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
    lines = read(LIVE)
    cards = parse(lines)
    if cid in cards:
        s, e, _ = cards[cid]
        sys.stdout.write("".join(lines[s:e]).rstrip() + "\n")
        return 0
    log = subprocess.run(
        ["git", "log", "--format=%h %ad %s", "--date=short", f"--grep=^{cid}:"],
        cwd=ROOT, capture_output=True, text=True,
    ).stdout.strip()
    if log:
        print(f"{cid} is closed. Commits (see `git show <sha>`):\n{log}")
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


def cmd_close(ids):
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
            print(f"{cid} is not done; not closed", file=sys.stderr)
            return 1

    closed = set(ids)
    sec = order_section(live)
    out = []
    starts = {cards[c][0]: cards[c][1] for c in closed}
    i = 0
    while i < len(live):
        if i in starts:
            i = starts[i]
            continue
        line = live[i]
        m = re.match(r"^\s*\S+\.\s+(ST-\d+)", line)
        if sec and sec[0] <= i < sec[1] and m and m.group(1) in closed:
            i += 1
            continue
        out.append(line)
        i += 1

    out = drop_empty_milestones(out)
    text = re.sub(r"\n{3,}", "\n\n", "".join(out))
    text = re.sub(r"(\n---\n)(\n---\n)+", r"\1", text)
    LIVE.write_text(text)
    print(f"closed {len(ids)} card(s)")
    return 0


def drop_empty_milestones(lines):
    """Remove milestone sections (other than the order list) that have no cards left."""
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("## ") and line.strip() != "## Implementation order":
            j = i + 1
            while j < len(lines) and not lines[j].startswith("## "):
                j += 1
            if not any(CARD_RE.match(l) for l in lines[i:j]):
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
    if cmd == "close":
        return cmd_close(args)
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
