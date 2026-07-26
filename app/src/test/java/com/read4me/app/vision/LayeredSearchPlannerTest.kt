package com.read4me.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredSearchPlannerTest {
    private val candidates = listOf(
        candidate("a", 1), candidate("a", 2), candidate("a", 3), candidate("a", 8), candidate("b", 4),
    )

    @Test fun selectsCurrentAndAdjacentSpreads() {
        assertEquals(listOf("a:1", "a:2", "a:3"), LayeredSearchPlanner.adjacent(LayeredSearchPlanner.Context("a", 2), candidates).map { it.key })
    }

    @Test fun shortlistIsBoundedAndOrderedByCheapScore() {
        val scores = (1..20).map { LayeredSearchPlanner.CheapScore(candidate("book", it), it) }
        val result = LayeredSearchPlanner.shortlist(scores, 8)
        assertEquals(8, result.size)
        assertEquals(20, result.first().spreadOrdinal)
    }

    @Test fun noContextSkipsAdjacentAndSearchesWholeLibrary() {
        assertTrue(LayeredSearchPlanner.adjacent(null, candidates).isEmpty())
        assertEquals(candidates, LayeredSearchPlanner.libraryCandidates(candidates))
    }

    @Test fun failedFastPathRetainsJumpAndOtherBookCandidates() {
        val library = LayeredSearchPlanner.libraryCandidates(candidates)
        assertTrue(candidate("a", 8) in library)
        assertTrue(candidate("b", 4) in library)
    }

    @Test fun preverifiedAdjacentCandidateRemainsFinalistOutsideCheapShortlist() {
        val adjacent = candidate("a", 2)
        val cheapShortlist = listOf(candidate("b", 4), candidate("a", 8))

        val finalists = LayeredSearchPlanner.finalists(listOf(adjacent), cheapShortlist)

        assertEquals(listOf(adjacent, candidate("b", 4), candidate("a", 8)), finalists)
    }

    private fun candidate(book: String, spread: Int) =
        LayeredSearchPlanner.Candidate("$book:$spread", book, spread)
}
