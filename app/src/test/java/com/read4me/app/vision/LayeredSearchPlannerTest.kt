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

    @Test fun manyReferencesFromOneSpreadDoNotCrowdOutAnotherSpread() {
        val crowded = (1..12).map {
            LayeredSearchPlanner.Candidate("crowded:r$it", "book", 1, "crowded")
        }
        val other = LayeredSearchPlanner.Candidate("other:r1", "book", 2, "other")
        val scores = crowded.mapIndexed { index, candidate ->
            LayeredSearchPlanner.CheapScore(candidate, 100 - index)
        } + LayeredSearchPlanner.CheapScore(other, 1)

        val shortlisted = LayeredSearchPlanner.shortlist(scores, 10)

        assertEquals(listOf("crowded", "other"), shortlisted.map { it.groupKey })
    }

    @Test fun selectedSpreadExpandsAllItsReferencesForGeometry() {
        val planned = listOf(
            LayeredSearchPlanner.Candidate("one:r1", "book", 1, "one"),
            LayeredSearchPlanner.Candidate("one:r2", "book", 1, "one"),
            LayeredSearchPlanner.Candidate("two:r1", "book", 2, "two"),
        )

        val expanded = LayeredSearchPlanner.expandShortlistedGroups(listOf(planned.first()), planned)

        assertEquals(listOf("one:r1", "one:r2"), expanded.map { it.key })
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

    @Test fun multipleReferencesOfOneSpreadAreAggregatedBeforeRunnerUp() {
        data class S(val group: String, val inliers: Int, val matches: Int)
        val result = LayeredSearchPlanner.aggregateBySpread(
            listOf(S("same", 30, 35), S("same", 29, 40), S("other", 22, 28)),
            S::group, S::inliers, S::matches,
        )
        assertEquals(listOf("same", "other"), result.map { it.group })
        assertEquals(30, result.first().inliers)
    }

    @Test fun differentSpreadsRemainCompetingCandidates() {
        data class S(val group: String, val inliers: Int)
        val result = LayeredSearchPlanner.aggregateBySpread(
            listOf(S("one", 20), S("two", 19)), S::group, S::inliers, S::inliers,
        )
        assertEquals(2, result.size)
    }

    private fun candidate(book: String, spread: Int) =
        LayeredSearchPlanner.Candidate("$book:$spread", book, spread)
}
