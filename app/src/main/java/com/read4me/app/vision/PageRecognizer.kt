package com.read4me.app.vision

class PageRecognizer(
    private val candidates: List<Candidate>,
    private val minimumSimilarity: Float = 0.82f,
    private val minimumMargin: Float = 0.025f,
    private val confirmationsRequired: Int = 3,
) {
    data class Candidate(
        val bookId: String,
        val spreadOrdinal: Int,
        val fingerprint: ByteArray,
    ) {
        val key: String get() = "$bookId:$spreadOrdinal"
    }

    data class Match(
        val candidate: Candidate,
        val similarity: Float,
        internal val rankingScore: Float = similarity,
    )

    enum class State {
        NO_CANDIDATES,
        LOW_SIMILARITY,
        AMBIGUOUS,
        CONFIRMING,
        CONFIRMED,
        STABLE,
    }

    data class Decision(
        val confirmed: Match?,
        val best: Match?,
        val second: Match?,
        val state: State,
        val confirmationCount: Int,
        val confirmationsRequired: Int,
    )

    private var pendingKey: String? = null
    private var pendingCount = 0
    private var confirmedKey: String? = null
    private var confirmedCandidate: Candidate? = null

    fun accept(fingerprint: ByteArray): Match? = evaluate(fingerprint).confirmed

    fun evaluate(fingerprint: ByteArray): Decision {
        val scored = candidates
            .asSequence()
            .map {
                val similarity = VisualFingerprint.similarity(fingerprint, it.fingerprint)
                Match(it, similarity, similarity + contextBoost(it))
            }
            .sortedByDescending(Match::rankingScore)
            .take(2)
            .toList()
        val best = scored.firstOrNull()
        val second = scored.getOrNull(1)
        val state = when {
            best == null -> State.NO_CANDIDATES
            best.similarity < minimumSimilarity -> State.LOW_SIMILARITY
            second != null && best.rankingScore - second.rankingScore < minimumMargin -> State.AMBIGUOUS
            else -> null
        }

        if (state != null) {
            pendingKey = null
            pendingCount = 0
            return Decision(null, best, second, state, 0, confirmationsRequired)
        }

        checkNotNull(best)
        if (pendingKey == best.candidate.key) {
            pendingCount++
        } else {
            pendingKey = best.candidate.key
            pendingCount = 1
        }

        if (confirmedKey == best.candidate.key) {
            return Decision(null, best, second, State.STABLE, pendingCount, confirmationsRequired)
        }
        if (pendingCount < confirmationsRequired) {
            return Decision(null, best, second, State.CONFIRMING, pendingCount, confirmationsRequired)
        }
        confirmedKey = best.candidate.key
        confirmedCandidate = best.candidate
        return Decision(best, best, second, State.CONFIRMED, pendingCount, confirmationsRequired)
    }

    private fun contextBoost(candidate: Candidate): Float {
        val current = confirmedCandidate ?: return 0f
        if (candidate.key == current.key) return 0.03f
        if (candidate.bookId != current.bookId) return 0f
        return if (kotlin.math.abs(candidate.spreadOrdinal - current.spreadOrdinal) == 1) 0.02f else 0.005f
    }
}
