package com.read4me.app.vision

/** Pure planning for the native ORB matcher. */
object LayeredSearchPlanner {
    data class Context(val bookId: String, val spreadOrdinal: Int)
    data class Candidate(val key: String, val bookId: String, val spreadOrdinal: Int)
    data class CheapScore(val candidate: Candidate, val goodMatches: Int)

    fun adjacent(context: Context?, candidates: List<Candidate>): List<Candidate> =
        if (context == null) emptyList() else candidates.filter {
            it.bookId == context.bookId && kotlin.math.abs(it.spreadOrdinal - context.spreadOrdinal) <= 1
        }

    fun shortlist(scores: List<CheapScore>, limit: Int = 10): List<Candidate> {
        require(limit > 0)
        return scores
            .sortedWith(compareByDescending<CheapScore> { it.goodMatches }.thenBy { it.candidate.key })
            .take(limit)
            .map { it.candidate }
    }

    fun finalists(preverified: List<Candidate>, shortlisted: List<Candidate>): List<Candidate> =
        (preverified + shortlisted).distinctBy(Candidate::key)

    /** A failed adjacent stage always expands to every book, permitting jumps and switches. */
    fun libraryCandidates(candidates: List<Candidate>): List<Candidate> = candidates
}
