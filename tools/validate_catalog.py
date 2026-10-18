#!/usr/bin/env python3
"""Validate catalog structure, unique titles, and new-theme freshness."""

from collections import Counter
import argparse
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "tools" / "catalog"
HANDMADE = ROOT / "tools" / "puzzle-sources"
WORD_BANK = ROOT / "app" / "src" / "main" / "assets" / "puzzles" / "wordbank.txt"
EXPECTED_CATALOG_COUNTS = {"EASY": 238, "MEDIUM": 238, "HARD": 237}
EXPECTED_NEW_COUNTS = {"EASY": 139, "MEDIUM": 140, "HARD": 141}
NEW_PREFIX = "11-new-"


def answer_from_line(line):
    if "|" not in line:
        return None
    word = line.split("|", 1)[0].strip().upper()
    return word if re.fullmatch(r"[A-Z]{3,12}", word) else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", type=Path, help="Validate one catalog file instead of the complete catalog")
    parser.add_argument("--fresh-minimum", type=int, default=8)
    parser.add_argument("--max-new-theme-reuse", type=int, default=6)
    args = parser.parse_args()

    errors = []
    warnings = []
    titles = {}
    counts = {difficulty: 0 for difficulty in EXPECTED_CATALOG_COUNTS}
    new_counts = {difficulty: 0 for difficulty in EXPECTED_NEW_COUNTS}
    new_word_themes = Counter()
    preexisting_words = set()

    def register_title(title, source, is_new=False):
        key = title.casefold()
        if key in titles:
            previous_source, previous_is_new = titles[key]
            if is_new or previous_is_new:
                errors.append(f"{source}: duplicate title {title!r} (also in {previous_source})")
        else:
            titles[key] = (source, is_new)

    catalog_paths = sorted(CATALOG.glob("*.txt"))
    if args.file:
        requested_path = args.file if args.file.is_absolute() else ROOT / args.file
        requested_path = requested_path.resolve()
        selected_path = next((path for path in catalog_paths if path.resolve() == requested_path), None)
        if selected_path is None:
            parser.error(f"{requested_path} is not a catalog file")
        paths_to_validate = [selected_path]
    else:
        paths_to_validate = catalog_paths

    for path in sorted(HANDMADE.glob("*.txt")):
        text = path.read_text(encoding="utf-8")
        match = re.search(r"^title:\s*(.+)$", text, re.MULTILINE)
        if match:
            register_title(match.group(1).strip(), path.name)
        for line in text.splitlines():
            word = answer_from_line(line)
            if word:
                preexisting_words.add(word)

    for line in WORD_BANK.read_text(encoding="utf-8").splitlines():
        answer = answer_from_line(line)
        if answer:
            preexisting_words.add(answer)

    for path in catalog_paths:
        if path in paths_to_validate:
            continue
        is_new = path.name.startswith(NEW_PREFIX)
        theme_words = set()
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.startswith("=== "):
                if is_new:
                    new_word_themes.update(theme_words)
                theme_words = set()
                title = line[4:].rsplit(" | ", 1)[0].strip()
                register_title(title, f"{path.name}:{line_number}", is_new)
            else:
                answer = answer_from_line(line)
                if answer:
                    theme_words.add(answer)
                    if not is_new:
                        preexisting_words.add(answer)
        if is_new:
            new_word_themes.update(theme_words)

    for path in paths_to_validate:
        is_new = path.name.startswith(NEW_PREFIX)
        current_title = None
        current_difficulty = None
        entries = []
        line_number = 0

        def finish_theme():
            if current_title is None:
                return
            words = [word for word, _ in entries]
            if len(words) != len(set(words)):
                errors.append(f"{path.name}: duplicate answer in {current_title!r}")
            if is_new:
                if len(entries) != 20:
                    errors.append(f"{path.name}: {current_title!r} has {len(entries)} answers, expected 20")
                compound_answers = sorted(
                    (word, other)
                    for word in words
                    for other in words
                    if word != other and len(word) < len(other) and (other.startswith(word) or other.endswith(word))
                )
                if compound_answers:
                    warnings.append(
                        f"{path.name}: {current_title!r} has substring-compound answers: {compound_answers}"
                    )
                clues = [clue.casefold() for _, clue in entries]
                duplicate_clues = {clue for clue, count in Counter(clues).items() if count > 1}
                if duplicate_clues:
                    errors.append(f"{path.name}: {current_title!r} has duplicate clues: {sorted(duplicate_clues)}")
                fresh_count = sum(word not in preexisting_words for word in words)
                if fresh_count < args.fresh_minimum:
                    errors.append(
                        f"{path.name}: {current_title!r} has only {fresh_count} fresh answers, "
                        f"expected at least {args.fresh_minimum}"
                    )
                new_word_themes.update(set(words))

        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.startswith("=== "):
                finish_theme()
                try:
                    current_title, current_difficulty = line[4:].rsplit(" | ", 1)
                except ValueError:
                    errors.append(f"{path.name}:{line_number}: malformed theme header")
                    current_title, current_difficulty = line[4:], ""
                current_title = current_title.strip()
                register_title(current_title, f"{path.name}:{line_number}", is_new)
                if is_new and ("&" in current_title or re.search(r"season", current_title, re.IGNORECASE)):
                    errors.append(f"{path.name}:{line_number}: new title must be a single natural topic: {current_title!r}")
                if current_difficulty not in counts:
                    errors.append(f"{path.name}:{line_number}: unknown difficulty {current_difficulty!r}")
                else:
                    counts[current_difficulty] += 1
                    if is_new:
                        new_counts[current_difficulty] += 1
                entries = []
                continue
            if not line.strip():
                continue
            if "|" not in line or current_title is None:
                errors.append(f"{path.name}:{line_number}: malformed entry")
                continue
            word, clue = (part.strip() for part in line.split("|", 1))
            if not re.fullmatch(r"[A-Z]+", word):
                errors.append(f"{path.name}:{line_number}: invalid answer {word!r}")
            if is_new and not re.fullmatch(r"[A-Z]{3,12}", word):
                errors.append(f"{path.name}:{line_number}: new answer must be 3–12 letters: {word!r}")
            if not clue:
                errors.append(f"{path.name}:{line_number}: empty clue")
            if is_new and clue.endswith("."):
                errors.append(f"{path.name}:{line_number}: new clue must not end in a period")
            if re.search(rf"(?<![A-Z]){re.escape(word)}(?![A-Z])", clue, re.IGNORECASE):
                errors.append(f"{path.name}:{line_number}: clue contains answer {word!r}")
            entries.append((word, clue))
            if not is_new:
                answer = answer_from_line(line)
                if answer:
                    preexisting_words.add(answer)
        finish_theme()

    if not args.file:
        for difficulty, expected in EXPECTED_CATALOG_COUNTS.items():
            if counts[difficulty] != expected:
                errors.append(f"{difficulty}: found {counts[difficulty]} catalog themes, expected {expected}")
        if new_counts != EXPECTED_NEW_COUNTS:
            errors.append(f"new theme counts are {new_counts}, expected {EXPECTED_NEW_COUNTS}")
    overused = {
        word: themes for word, themes in new_word_themes.items() if themes > args.max_new_theme_reuse
    }
    for word, themes in sorted(overused.items()):
        errors.append(
            f"{word}: appears in {themes} new themes, maximum is {args.max_new_theme_reuse}"
        )

    for warning in warnings:
        print(f"Warning: {warning}")
    if errors:
        raise SystemExit("\n".join(errors))
    fresh_words = {word for word, themes in new_word_themes.items() if word not in preexisting_words and themes}
    if args.file:
        print(
            f"{args.file}: valid; {sum(counts.values())} themes; "
            f"{len(fresh_words)} fresh words; max reuse {max(new_word_themes.values())}."
        )
    else:
        print(
            "Catalog valid: "
            + ", ".join(f"{difficulty} {counts[difficulty]}" for difficulty in counts)
            + " themes; "
            + ", ".join(f"{difficulty} +{new_counts[difficulty]}" for difficulty in new_counts)
            + f"; {len(fresh_words)} fresh words; max reuse {max(new_word_themes.values())}."
        )


if __name__ == "__main__":
    main()
