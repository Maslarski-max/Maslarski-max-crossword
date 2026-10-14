#!/usr/bin/env python3
"""Generate the 720 slot-stable Classic puzzles from hand-made and catalog themes.

Usage:
    python3 tools/level_generator.py
    python3 tools/level_generator.py --start 1 --end 20 --attempts-per-level 12 \
        --max-attempts-per-level 400 \
        --levels-dir /home/ubuntu/level-generator-smoke

Full runs stage all levels in a temporary directory and replace the asset directory only after every level succeeds.
Daily puzzles and the word bank are untouched.
"""

import argparse
from collections import defaultdict
import json
import os
import random
import re
import shutil
import sys
import tempfile
from pathlib import Path

from puzzle_builder import ACROSS, DOWN, Layout, number_grid, parse_source, to_json


ROOT = Path(__file__).resolve().parents[1]
CATALOG_DIR = ROOT / "tools" / "catalog"
SOURCES_DIR = ROOT / "tools" / "puzzle-sources"
WORD_BANK = ROOT / "app" / "src" / "main" / "assets" / "puzzles" / "wordbank.txt"
DEFAULT_LEVELS_DIR = ROOT / "app" / "src" / "main" / "assets" / "puzzles" / "levels"
HANDMADE_IDS = ("easy-01", "easy-02", "medium-01", "medium-02", "hard-01", "hard-02", "expert-01")
HANDMADE_BY_TIER = {
    "EASY": ("easy-01", "easy-02"),
    "MEDIUM": ("medium-01", "medium-02"),
    "HARD": ("hard-01", "hard-02", "expert-01"),
}
LEVEL_COUNT = 720
MAX_CANDIDATE_OPTIONS = 512
PRIMARY_WORDS_PER_ANCHOR = 4
FALLBACK_WORDS_PER_ANCHOR = 16
MAX_WORD_LEVEL_USES = 12
MIN_WORD_LENGTH = 3
PREFERRED_FILLER_LENGTH = 4
FALLBACK_WORD_LEVEL_USES = 20


class FrameLayout(Layout):
    """A Layout whose letter coordinates stay inside its absolute square frame."""

    def __init__(self, size):
        super().__init__(size)
        self._bounds = None

    def bounds(self):
        return self._bounds

    def fits(self, word, row, col, direction):
        dr, dc = direction
        end_row = row + dr * (len(word) - 1)
        end_col = col + dc * (len(word) - 1)
        if row < 0 or col < 0 or end_row >= self.size or end_col >= self.size:
            return -1
        if self.cells.get((row - dr, col - dc)) or self.cells.get((end_row + dr, end_col + dc)):
            return -1
        crossings = 0
        for i, letter in enumerate(word):
            cell_row, cell_col = row + dr * i, col + dc * i
            existing = self.cells.get((cell_row, cell_col))
            if existing:
                if existing != letter or direction in self.directions[(cell_row, cell_col)]:
                    return -1
                crossings += 1
            elif self.cells.get((cell_row + dc, cell_col + dr)) or self.cells.get(
                (cell_row - dc, cell_col - dr)
            ):
                return -1
        if self.words and crossings == 0:
            return -1
        if crossings == len(word):
            return -1
        return crossings

    def place(self, word, clue, row, col, direction):
        super().place(word, clue, row, col, direction)
        dr, dc = direction
        end_row = row + dr * (len(word) - 1)
        end_col = col + dc * (len(word) - 1)
        if self._bounds is None:
            self._bounds = (min(row, end_row), min(col, end_col), max(row, end_row), max(col, end_col))
        else:
            r0, c0, r1, c1 = self._bounds
            self._bounds = (
                min(r0, row, end_row),
                min(c0, col, end_col),
                max(r1, row, end_row),
                max(c1, col, end_col),
            )


def word_count(order):
    return 6 + (order - 1) * 51 // LEVEL_COUNT


def grid_size(order):
    return 8 + (order - 1) * 15 // LEVEL_COUNT


def tier_for(order):
    if order <= 240:
        return "EASY"
    if order <= 480:
        return "MEDIUM"
    return "HARD"


def read_catalog(path):
    themes = []
    current = None
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("=== "):
            match = re.fullmatch(r"===\s*(.+?)\s*\|\s*(EASY|MEDIUM|HARD)\s*", line)
            if not match:
                raise ValueError(f"{path}:{line_number}: malformed theme header")
            current = {"title": match.group(1), "difficulty": match.group(2), "entries": []}
            themes.append(current)
            continue
        if "|" not in line or current is None:
            raise ValueError(f"{path}:{line_number}: malformed theme entry")
        word, clue = (part.strip() for part in line.split("|", 1))
        current["entries"].append((word.upper(), clue))
    return themes


def load_themes():
    handmade = {}
    for source_id in HANDMADE_IDS:
        path = SOURCES_DIR / f"{source_id}.txt"
        meta, entries = parse_source(path)
        handmade[source_id] = {
            "title": meta["title"],
            "difficulty": meta["difficulty"].upper(),
            "entries": entries,
        }

    catalog = [theme for path in sorted(CATALOG_DIR.glob("*.txt")) for theme in read_catalog(path)]
    tiers = {}
    for tier, source_ids in HANDMADE_BY_TIER.items():
        tiers[tier] = [handmade[source_id] for source_id in source_ids]
        tiers[tier].extend(theme for theme in catalog if theme["difficulty"] == tier)
        if len(tiers[tier]) != 240:
            raise ValueError(f"{tier} has {len(tiers[tier])} themes, expected 240")
    return tiers, handmade, catalog


def build_word_bank(handmade, catalog):
    clues = defaultdict(list)
    seen_pairs = set()

    def add(word, clue):
        word = word.upper()
        pair = (word, clue)
        if pair not in seen_pairs:
            seen_pairs.add(pair)
            clues[word].append(clue)

    for theme in list(handmade.values()) + catalog:
        for word, clue in theme["entries"]:
            add(word, clue)
    for line in WORD_BANK.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "|" not in line:
            continue
        word, clue = (part.strip() for part in line.split("|", 1))
        if word and clue:
            add(word, clue)

    words = list(clues)
    index = defaultdict(list)
    positions_by_letter = defaultdict(list)
    for word in words:
        for position, letter in enumerate(word):
            index[(letter, position)].append(word)
            if position not in positions_by_letter[letter]:
                positions_by_letter[letter].append(position)
    return clues, words, index, positions_by_letter


def layout_bounds(layout):
    if not layout.cells:
        return None
    return layout.bounds()


def edge_gain(layout, word, row, col, direction):
    old = layout_bounds(layout)
    if old is None:
        return 0
    dr, dc = direction
    end_row = row + dr * (len(word) - 1)
    end_col = col + dc * (len(word) - 1)
    r0, c0, r1, c1 = old
    old_gap = r0 + c0 + (layout.size - 1 - r1) + (layout.size - 1 - c1)
    new_r0, new_c0 = min(r0, row, end_row), min(c0, col, end_col)
    new_r1, new_c1 = max(r1, row, end_row), max(c1, col, end_col)
    new_gap = new_r0 + new_c0 + (layout.size - 1 - new_r1) + (layout.size - 1 - new_c1)
    return old_gap - new_gap


def choose_layout_candidate(layout, word, candidates, rng):
    if not candidates:
        return None
    highest = max(candidate[3] for candidate in candidates)
    pool = [candidate for candidate in candidates if candidate[3] == highest] if rng.random() < 0.5 else candidates
    scored = [(candidate, edge_gain(layout, word, *candidate[:3])) for candidate in pool]
    best_gain = max(gain for _, gain in scored)
    best = [candidate for candidate, gain in scored if gain == best_gain]
    return rng.choice(best)


def place_theme_words(layout, entries, target, rng, word_usage):
    entries = [
        (word, clue)
        for word, clue in entries
        if MIN_WORD_LENGTH <= len(word) <= layout.size
        and word_usage.get(word, 0) < MAX_WORD_LEVEL_USES
    ]
    pending = sorted(entries, key=lambda entry: -len(entry[0]) + rng.random() * 4)
    placed_words = set()
    while pending and len(layout.words) < target:
        progress = False
        for word, clue in list(pending):
            if word in placed_words:
                pending.remove((word, clue))
                continue
            if not layout.words:
                starts = [(0, 0, ACROSS, 0), (0, 0, DOWN, 0)]
                rng.shuffle(starts)
                candidate = next((item for item in starts if layout.fits(word, *item[:3]) >= 0), None)
            else:
                options = list(set(layout.candidates(word)))
                candidate = choose_layout_candidate(layout, word, options, rng)
            if candidate is None:
                continue
            row, col, direction, _ = candidate
            layout.place(word, clue, row, col, direction)
            placed_words.add(word)
            pending.remove((word, clue))
            progress = True
            if len(layout.words) >= target:
                break
        if not progress:
            break
    return placed_words


def indexed_candidates(layout, unused_words, index, positions_by_letter, rng, words_per_anchor):
    if not layout.cells:
        return []
    anchors = []
    for (cell_row, cell_col), letter in layout.cells.items():
        for position in positions_by_letter.get(letter, ()):
            if position >= layout.size:
                continue
            for direction in (ACROSS, DOWN):
                if direction in layout.directions[(cell_row, cell_col)]:
                    continue
                row = cell_row - direction[0] * position
                col = cell_col - direction[1] * position
                if not (0 <= row < layout.size and 0 <= col < layout.size):
                    continue
                anchors.append((cell_row, cell_col, letter, position, row, col, direction))
    rng.shuffle(anchors)
    found = {}
    for cell_row, cell_col, letter, position, row, col, direction in anchors:
        bucket = index[(letter, position)]
        eligible = [word for word in bucket if word in unused_words and len(word) <= layout.size]
        if not eligible:
            continue
        if words_per_anchor is None or words_per_anchor >= len(eligible):
            sample = eligible
            rng.shuffle(sample)
        else:
            sample = rng.sample(eligible, words_per_anchor)
        for word in sample:
            key = (word, row, col, direction)
            if key in found:
                continue
            crossings = layout.fits(word, row, col, direction)
            if crossings > 0:
                found[key] = (word, row, col, direction, crossings)
        if len(found) >= MAX_CANDIDATE_OPTIONS:
            break

    return list(found.values())


def choose_bank_candidate(layout, candidates, rng):
    if not candidates:
        return None
    highest = max(candidate[4] for candidate in candidates)
    pool = [candidate for candidate in candidates if candidate[4] == highest]
    longer = [candidate for candidate in pool if len(candidate[0]) >= PREFERRED_FILLER_LENGTH]
    if longer:
        pool = longer
    scored = [
        (candidate, edge_gain(layout, candidate[0], candidate[1], candidate[2], candidate[3]))
        for candidate in pool
    ]
    best_gain = max(gain for _, gain in scored)
    best = [candidate for candidate, gain in scored if gain == best_gain]
    return rng.choice(best)


def fill_from_bank(
    layout,
    used_words,
    target,
    clues,
    bank_words,
    index,
    positions_by_letter,
    rng,
    word_usage,
    max_word_level_uses=MAX_WORD_LEVEL_USES,
):
    unused_words = {
        word for word in bank_words
        if word not in used_words
        and MIN_WORD_LENGTH <= len(word) <= layout.size
        and word_usage.get(word, 0) < max_word_level_uses
    }
    while len(layout.words) < target:
        candidates = indexed_candidates(layout, unused_words, index, positions_by_letter, rng, PRIMARY_WORDS_PER_ANCHOR)
        if not candidates:
            candidates = indexed_candidates(
                layout, unused_words, index, positions_by_letter, rng, FALLBACK_WORDS_PER_ANCHOR
            )
        if not candidates:
            candidates = indexed_candidates(layout, unused_words, index, positions_by_letter, rng, None)
        candidate = choose_bank_candidate(layout, candidates, rng)
        if candidate is None:
            break
        word, row, col, direction, _ = candidate
        layout.place(word, rng.choice(clues[word]), row, col, direction)
        used_words.add(word)
        unused_words.remove(word)


def make_attempt(
    order,
    theme,
    target,
    size,
    clues,
    bank_words,
    index,
    positions_by_letter,
    seed,
    word_usage,
    filler_max_word_uses=MAX_WORD_LEVEL_USES,
):
    rng = random.Random(seed)
    layout = FrameLayout(size)
    used_words = place_theme_words(layout, theme["entries"], target, rng, word_usage)
    if len(layout.words) < target:
        fill_from_bank(
            layout,
            used_words,
            target,
            clues,
            bank_words,
            index,
            positions_by_letter,
            rng,
            word_usage,
            filler_max_word_uses,
        )
    return layout


def find_level_layout(
    order,
    theme,
    target,
    size,
    args,
    clues,
    bank_words,
    index,
    positions_by_letter,
    word_usage,
    filler_max_word_uses=MAX_WORD_LEVEL_USES,
):
    best = None
    best_fill = -1.0
    best_partial = (0, None)
    for batch_start in range(0, args.max_attempts_per_level, args.attempts_per_level):
        batch_end = min(batch_start + args.attempts_per_level, args.max_attempts_per_level)
        batch_best = None
        batch_best_fill = -1.0
        for attempt in range(batch_start, batch_end):
            layout = make_attempt(
                order,
                theme,
                target,
                size,
                clues,
                bank_words,
                index,
                positions_by_letter,
                order + attempt * 1_000_003,
                word_usage,
                filler_max_word_uses,
            )
            bounds = layout.bounds()
            expected_bounds = (0, 0, size - 1, size - 1)
            if len(layout.words) != target or bounds != expected_bounds:
                if len(layout.words) > best_partial[0]:
                    best_partial = (len(layout.words), bounds)
                continue
            fill = len(layout.cells) / (size * size)
            if fill > batch_best_fill:
                batch_best, batch_best_fill = layout, fill
        if batch_best is not None:
            best, best_fill = batch_best, batch_best_fill
            break
    if best is None:
        for retry in range(1, 21):
            layout = make_attempt(
                order,
                theme,
                target,
                size,
                clues,
                bank_words,
                index,
                positions_by_letter,
                order * 1000 + retry,
                word_usage,
                filler_max_word_uses,
            )
            bounds = layout.bounds()
            expected_bounds = (0, 0, size - 1, size - 1)
            if len(layout.words) != target or bounds != expected_bounds:
                if len(layout.words) > best_partial[0]:
                    best_partial = (len(layout.words), bounds)
                continue
            fill = len(layout.cells) / (size * size)
            if fill > best_fill:
                best, best_fill = layout, fill
    return best, best_fill, best_partial


def create_puzzle(order, theme, layout):
    identifier = HANDMADE_IDS[order - 1] if order <= len(HANDMADE_IDS) else f"level-{order:03d}"
    meta = {
        "id": identifier,
        "title": theme["title"],
        "difficulty": tier_for(order),
        "order": str(order),
        "seed": str(order),
    }
    puzzle = to_json(meta, layout)
    if len(puzzle["grid"]) != layout.size or any(len(row) != layout.size for row in puzzle["grid"]):
        raise ValueError(f"level {order} serialized to a non-{layout.size}x{layout.size} grid")
    number_grid(puzzle["grid"])
    return puzzle


def generate_levels(args, tiers, clues, bank_words, index, positions_by_letter, levels_dir):
    levels_dir.mkdir(parents=True, exist_ok=True)
    stats = ["n\twords\tsize\tfill%"]
    theme_cursor = {"EASY": 0, "MEDIUM": 0, "HARD": 0}
    accepted = {}
    word_usage = defaultdict(int)
    for order in range(1, args.end + 1):
        tier = tier_for(order)
        theme_index = theme_cursor[tier]
        theme = tiers[tier][theme_index]
        theme_cursor[tier] += 1
        if order < args.start:
            continue
        target = word_count(order)
        size = grid_size(order)
        best, best_fill, best_partial = find_level_layout(
            order,
            theme,
            target,
            size,
            args,
            clues,
            bank_words,
            index,
            positions_by_letter,
            word_usage,
        )
        filler_fallback_used = False
        theme_fallback_used = False
        if best is None:
            filler_fallback_used = True
            print(
                f"Fallback: level {order} used filler word-usage cap "
                f"{FALLBACK_WORD_LEVEL_USES} for theme {theme['title']!r}",
                flush=True,
            )
            best, best_fill, fallback_partial = find_level_layout(
                order,
                theme,
                target,
                size,
                args,
                clues,
                bank_words,
                index,
                positions_by_letter,
                word_usage,
                FALLBACK_WORD_LEVEL_USES,
            )
            if fallback_partial[0] > best_partial[0]:
                best_partial = fallback_partial
        if best is None:
            next_theme_index = theme_cursor[tier]
            if next_theme_index < len(tiers[tier]):
                previous_theme = theme
                theme = tiers[tier][next_theme_index]
                tiers[tier][theme_index] = theme
                tiers[tier][next_theme_index] = previous_theme
                theme_fallback_used = True
                print(
                    f"Fallback: level {order} swapped theme {previous_theme['title']!r} "
                    f"with next unused theme {theme['title']!r}; filler cap "
                    f"{FALLBACK_WORD_LEVEL_USES}",
                    flush=True,
                )
                best, best_fill, fallback_partial = find_level_layout(
                    order,
                    theme,
                    target,
                    size,
                    args,
                    clues,
                    bank_words,
                    index,
                    positions_by_letter,
                    word_usage,
                    FALLBACK_WORD_LEVEL_USES,
                )
                if fallback_partial[0] > best_partial[0]:
                    best_partial = fallback_partial
        if best is None:
            retry_summary = f"{args.max_attempts_per_level} primary attempts and 20 alternate seeds"
            if filler_fallback_used:
                retry_summary += f", then the same schedule at filler cap {FALLBACK_WORD_LEVEL_USES}"
            if theme_fallback_used:
                retry_summary += ", then the same schedule with the swapped theme"
            elif filler_fallback_used and theme_cursor[tier] >= len(tiers[tier]):
                retry_summary += "; no unused same-tier theme remained for a swap"
            raise SystemExit(
                f"Unable to generate level {order} ({theme['title']}, {tier}, {target} words, "
                f"{size}x{size}) after {retry_summary}; best attempt placed "
                f"{best_partial[0]} words in {best_partial[1]}"
            )
        for word in {entry[0] for entry in best.words}:
            word_usage[word] += 1
        puzzle = create_puzzle(order, theme, best)
        output = levels_dir / f"{puzzle['id']}.json"
        output.write_text(json.dumps(puzzle, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        stats.append(f"{order}\t{target}\t{size}\t{best_fill * 100:.2f}")
        accepted[order] = best_fill
        if args.progress:
            print(f"{order:03d}/{args.end}: {target} words, {size}x{size}, {best_fill * 100:.2f}%")

    stats_text = "\n".join(stats) + "\n"
    return accepted, stats_text


def replace_directory(staged, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        os.replace(staged, destination)
        return
    with tempfile.TemporaryDirectory(prefix=f".{destination.name}.backup-", dir=destination.parent) as backup:
        previous = Path(backup) / "previous"
        os.replace(destination, previous)
        try:
            os.replace(staged, destination)
        except Exception:
            os.replace(previous, destination)
            raise


def atomic_write_text(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.tmp-", delete=False
        ) as output:
            temporary = Path(output.name)
            output.write(text)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def generate(args):
    tiers, handmade, catalog = load_themes()
    clues, bank_words, index, positions_by_letter = build_word_bank(handmade, catalog)
    levels_dir = Path(args.levels_dir)
    if args.start == 1 and args.end == LEVEL_COUNT:
        levels_dir.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=f".{levels_dir.name}.staging-", dir=levels_dir.parent) as staged:
            accepted, stats_text = generate_levels(
                args, tiers, clues, bank_words, index, positions_by_letter, Path(staged)
            )
            replace_directory(Path(staged), levels_dir)
    else:
        accepted, stats_text = generate_levels(args, tiers, clues, bank_words, index, positions_by_letter, levels_dir)
    atomic_write_text(Path(args.stats), stats_text)
    print(stats_text, end="")
    return accepted


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--start", type=int, default=1)
    parser.add_argument("--end", type=int, default=LEVEL_COUNT)
    parser.add_argument("--attempts-per-level", type=int, default=12)
    parser.add_argument("--max-attempts-per-level", type=int, default=400)
    parser.add_argument("--levels-dir", type=Path, default=DEFAULT_LEVELS_DIR)
    parser.add_argument("--stats", type=Path, default=Path("/home/ubuntu/level_stats.txt"))
    parser.add_argument("--progress", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.start <= args.end <= LEVEL_COUNT:
        parser.error("start/end must be within 1..720 and start <= end")
    if args.attempts_per_level < 2:
        parser.error("at least two attempts per level are required")
    if args.max_attempts_per_level < args.attempts_per_level:
        parser.error("max-attempts-per-level must be at least attempts-per-level")
    if args.max_attempts_per_level > 400:
        parser.error("max-attempts-per-level cannot exceed 400")
    generate(args)


if __name__ == "__main__":
    sys.exit(main())
