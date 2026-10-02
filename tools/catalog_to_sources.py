#!/usr/bin/env python3
"""Splits the themed level catalog (tools/catalog/*.txt) into puzzle_builder sources.

Usage:
    python3 tools/catalog_to_sources.py
    python3 tools/puzzle_builder.py tools/puzzle-sources/level-*.txt

Catalog format: each level starts with a "=== Title | DIFFICULTY" header followed by "WORD | clue"
lines. Levels are numbered in file order, continuing after the hand-made levels in
tools/puzzle-sources, and each difficulty gets a fixed maximum grid size.
"""
import argparse
import re
from pathlib import Path

SIZES = {"EASY": 11, "MEDIUM": 13, "HARD": 15}
HEADER = re.compile(r"^===\s*(.+?)\s*\|\s*(EASY|MEDIUM|HARD)\s*$")


def read_catalog(paths):
    levels = []
    for path in paths:
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line:
                continue
            header = HEADER.match(line)
            if header:
                levels.append({"title": header.group(1), "difficulty": header.group(2), "entries": []})
                continue
            if not levels:
                raise SystemExit(f"{path}: entry before the first level header: {line}")
            word, sep, clue = line.partition("|")
            word = word.strip().upper()
            if not sep or not word.isalpha() or not clue.strip():
                raise SystemExit(f"{path}: bad entry: {line}")
            levels[-1]["entries"].append((word, clue.strip()))
    return levels


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--catalog-dir", default="tools/catalog", type=Path)
    parser.add_argument("--out-dir", default="tools/puzzle-sources", type=Path)
    parser.add_argument("--first-order", default=8, type=int)
    args = parser.parse_args()

    levels = read_catalog(sorted(args.catalog_dir.glob("*.txt")))
    titles = set()
    for i, level in enumerate(levels):
        order = args.first_order + i
        words = [w for w, _ in level["entries"]]
        if len(set(words)) != len(words):
            raise SystemExit(f"{level['title']}: duplicate answers")
        if level["title"] in titles:
            raise SystemExit(f"duplicate title: {level['title']}")
        titles.add(level["title"])
        body = "\n".join(f"{w} | {c}" for w, c in level["entries"])
        (args.out_dir / f"level-{order:03d}.txt").write_text(
            f"id: level-{order:03d}\ntitle: {level['title']}\ndifficulty: {level['difficulty']}\n"
            f"order: {order}\ntype: level\nsize: {SIZES[level['difficulty']]}\nseed: {order}\n---\n{body}\n",
            encoding="utf-8",
        )
    print(f"wrote {len(levels)} sources")


if __name__ == "__main__":
    main()
