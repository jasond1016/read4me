package com.read4me.app.vision

/** Pure planning for the native ORB matcher. */
object LayeredSearchPlanner {
    data class Context(val bookId: String, val spreadOrdinal: Int)
    data class Candidate(
        val key: String,
        val bookId: String,
        val spreadOrdinal: Int,
        val groupKey: String = key,
    )
    data class CheapScore(val candidate: Candidate, val goodMatches: Int)

    /** Keeps the strongest reference per spread before best/second and margin decisions. */
    fun <T> aggregateBySpread(
        scores: List<T>,
        groupKey: (T) -> String,
        inliers: (T) -> Int,
        goodMatches: (T) -> Int,
    ): List<T> = scores.groupBy(groupKey).values.map { group ->
        group.maxWith(compareBy<T> { inliers(it) }.thenBy { goodMatches(it) })
    }.sortedWith(compareByDescending<T> { inliers(it) }.thenByDescending { goodMatches(it) })

    fun adjacent(context: Context?, candidates: List<Candidate>): List<Candidate> =
        if (context == null) emptyList() else candidates.filter {
            it.bookId == context.bookId && kotlin.math.abs(it.spreadOrdinal - context.spreadOrdinal) <= 1
        }

    fun shortlist(scores: List<CheapScore>, limit: Int = 10): List<Candidate> {
        require(limit > 0)
        return scores
            .groupBy { it.candidate.groupKey }
            .values
            .map { group -> group.maxWith(compareBy<CheapScore> { it.goodMatches }.thenByDescending { it.candidate.key }) }
            .sortedWith(compareByDescending<CheapScore> { it.goodMatches }.thenBy { it.candidate.key })
            .take(limit)
            .map { it.candidate }
    }

    /** Expands selected spread groups back to every reference before geometric verification. */
    fun expandShortlistedGroups(shortlisted: List<Candidate>, planned: List<Candidate>): List<Candidate> {
        val groups = shortlisted.mapTo(hashSetOf(), Candidate::groupKey)
        return planned.filter { it.groupKey in groups }
    }

    fun finalists(preverified: List<Candidate>, shortlisted: List<Candidate>): List<Candidate> =
        (preverified + shortlisted).distinctBy(Candidate::key)

    /** A failed adjacent stage always expands to every book, permitting jumps and switches. */
    fun libraryCandidates(candidates: List<Candidate>): List<Candidate> = candidates
}
