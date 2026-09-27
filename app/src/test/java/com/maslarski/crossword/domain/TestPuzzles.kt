package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser

/**
 * ```
 * C A T      1 Across CAT, 3 Across BOW
 * A # O      1 Down CAB,   2 Down TOW
 * B O W
 * ```
 */
const val MINI_JSON = """
{
  "id": "mini",
  "title": "Mini",
  "difficulty": "EASY",
  "order": 1,
  "grid": ["CAT", "A#O", "BOW"],
  "clues": {
    "across": { "1": "Feline", "3": "Archer's weapon" },
    "down": { "1": "Taxi", "2": "Pull a car" }
  }
}
"""

fun miniPuzzle(): Puzzle = PuzzleParser().parse(MINI_JSON, PuzzleType.LEVEL)
