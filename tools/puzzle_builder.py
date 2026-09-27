#!/usr/bin/env python3
"""Builds crossword puzzle JSON files from a simple word/clue list.

Usage:
    python3 tools/puzzle_builder.py tools/puzzle-sources/*.txt
    python3 tools/puzzle_builder.py tools/puzzle-sources/easy-03.txt --attempts 20000

Levels are written to --levels-dir (default app/src/main/assets/puzzles/levels), daily puzzles to
--daily-dir (default app/src/main/assets/puzzles/daily).

Source format (see tools/puzzle-sources/*.txt):

    id: easy-01
    title: Beach Day
    difficulty: EASY          # EASY | MEDIUM | HARD
    order: 1                  # position in the level list (levels only)
    type: level               # level | daily
    size: 9                   # maximum width and height of the grid
    seed: 7                   # optional; layouts are deterministic for a given seed
    ---
    SUN | It rises in the east
    BEACH | Sandy strip by the sea

The builder lays the words out as a freestyle crossword (every cell belongs to at least one word,
no unintended adjacent letters), numbers the grid the same way the app does, and writes JSON the
app's PuzzleParser understands. Words that don't fit are reported and left out.
"""
import argparse
import json
import random
import sys
from pathlib import Path

ACROSS, DOWN = (0, 1), (1, 0)


def parse_source(path):
    meta, entries, in_body = {}, [], False
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip() if not in_body else raw.strip()
        if not line:
            continue
        if line == "---":
            in_body = True
            continue
        if in_body:
            word, clue = (part.strip() for part in line.split("|", 1))
            word = word.upper()
            if not word.isalpha() or len(word) < 2:
                raise ValueError(f"{path}: invalid answer {word!r}")
            entries.append((word, clue))
        else:
            key, value = (part.strip() for part in line.split(":", 1))
            meta[key] = value
    return meta, entries


class Layout:
    def __init__(self, size):
        self.size = size
        self.cells = {}
        self.words = []  # (word, clue, row, col, direction)

    def bounds(self):
        rows = [r for r, _ in self.cells]
        cols = [c for _, c in self.cells]
        return min(rows), min(cols), max(rows), max(cols)

    def fits(self, word, row, col, d):
        dr, dc = d
        if self.cells.get((row - dr, col - dc)) or self.cells.get((row + dr * len(word), col + dc * len(word))):
            return -1
        crossings = 0
        for i, ch in enumerate(word):
            r, c = row + dr * i, col + dc * i
            existing = self.cells.get((r, c))
            if existing:
                if existing != ch:
                    return -1
                crossings += 1
            else:
                # Perpendicular neighbours must be empty, otherwise we'd form a new unintended word.
                if self.cells.get((r + dc, c + dr)) or self.cells.get((r - dc, c - dr)):
                    return -1
        if self.words and crossings == 0:
            return -1
        if crossings == len(word):
            return -1
        rows = [r for r, _ in self.cells] + [row, row + dr * (len(word) - 1)]
        cols = [c for _, c in self.cells] + [col, col + dc * (len(word) - 1)]
        if max(rows) - min(rows) + 1 > self.size or max(cols) - min(cols) + 1 > self.size:
            return -1
        return crossings

    def place(self, word, clue, row, col, d):
        for i, ch in enumerate(word):
            self.cells[(row + d[0] * i, col + d[1] * i)] = ch
        self.words.append((word, clue, row, col, d))

    def candidates(self, word):
        if not self.words:
            return [(0, 0, ACROSS, 0)]
        out = []
        for (r, c), ch in list(self.cells.items()):
            for i, wch in enumerate(word):
                if wch != ch:
                    continue
                for d in (ACROSS, DOWN):
                    row, col = r - d[0] * i, c - d[1] * i
                    crossings = self.fits(word, row, col, d)
                    if crossings > 0:
                        out.append((row, col, d, crossings))
        return out


def build(entries, size, rng, attempts=30000):
    best, best_score = None, None
    for _ in range(attempts):
        order = sorted(entries, key=lambda e: -len(e[0]) + rng.random() * 4)
        layout = Layout(size)
        pending = list(order)
        progress = True
        while pending and progress:
            progress = False
            for entry in list(pending):
                options = layout.candidates(entry[0])
                if not options:
                    continue
                top = max(o[3] for o in options)
                pool = [o for o in options if o[3] == top] if rng.random() < 0.5 else options
                row, col, d, _ = rng.choice(pool)
                layout.place(entry[0], entry[1], row, col, d)
                pending.remove(entry)
                progress = True
        r0, c0, r1, c1 = layout.bounds()
        area = (r1 - r0 + 1) * (c1 - c0 + 1)
        crossings = sum(len(w) for w, *_ in layout.words) - len(layout.cells)
        score = (len(layout.words), crossings, -area)
        if best_score is None or score > best_score:
            best, best_score = layout, score
    return best


def number_grid(grid):
    rows, cols = len(grid), len(grid[0])
    open_ = lambda r, c: 0 <= r < rows and 0 <= c < cols and grid[r][c] != "#"
    numbers, n = {}, 0
    for r in range(rows):
        for c in range(cols):
            if not open_(r, c):
                continue
            across = not open_(r, c - 1) and open_(r, c + 1)
            down = not open_(r - 1, c) and open_(r + 1, c)
            if across or down:
                n += 1
                numbers[(r, c)] = n
    return numbers


def to_json(meta, layout):
    r0, c0, r1, c1 = layout.bounds()
    grid = [["#"] * (c1 - c0 + 1) for _ in range(r1 - r0 + 1)]
    for (r, c), ch in layout.cells.items():
        grid[r - r0][c - c0] = ch
    rows = ["".join(row) for row in grid]
    numbers = number_grid(rows)
    across, down = {}, {}
    for word, clue, row, col, d in layout.words:
        num = numbers[(row - r0, col - c0)]
        (across if d == ACROSS else down)[str(num)] = clue
    across = dict(sorted(across.items(), key=lambda kv: int(kv[0])))
    down = dict(sorted(down.items(), key=lambda kv: int(kv[0])))
    puzzle = {
        "id": meta["id"],
        "title": meta["title"],
        "author": meta.get("author", "Crossword Team"),
        "difficulty": meta["difficulty"].upper(),
        "grid": rows,
        "clues": {"across": across, "down": down},
    }
    if "order" in meta:
        puzzle["order"] = int(meta["order"])
    return puzzle


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sources", nargs="+")
    parser.add_argument("--levels-dir", default="app/src/main/assets/puzzles/levels")
    parser.add_argument("--daily-dir", default="app/src/main/assets/puzzles/daily")
    parser.add_argument("--attempts", type=int, default=30000)
    args = parser.parse_args()

    for source in args.sources:
        meta, entries = parse_source(source)
        rng = random.Random(int(meta.get("seed", 1)))
        layout = build(entries, int(meta.get("size", 13)), rng, args.attempts)
        placed = {w for w, *_ in layout.words}
        missing = [w for w, _ in entries if w not in placed]
        puzzle = to_json(meta, layout)
        out_dir = Path(args.daily_dir if meta.get("type", "level") == "daily" else args.levels_dir)
        out_dir.mkdir(parents=True, exist_ok=True)
        out = out_dir / f"{meta['id']}.json"
        out.write_text(json.dumps(puzzle, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        size = f"{len(puzzle['grid'])}x{len(puzzle['grid'][0])}"
        print(f"{out}: {size}, {len(layout.words)}/{len(entries)} words" + (f", skipped {missing}" if missing else ""))
        for row in puzzle["grid"]:
            print("   ", row.replace("#", "."))
    return 0


if __name__ == "__main__":
    sys.exit(main())
