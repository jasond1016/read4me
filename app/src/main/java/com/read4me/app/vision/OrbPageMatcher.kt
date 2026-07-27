package com.read4me.app.vision

import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import java.io.File

class OrbPageMatcher(
    references: List<Reference>,
    private val minimumInliers: Int = 14,
    private val minimumInlierMargin: Int = 10,
    private val confirmationsRequired: Int = 2,
) : Closeable {
    data class Reference(
        val bookId: String,
        val spreadOrdinal: Int,
        val spreadId: String,
        val imageFile: File,
    ) {
        val key: String get() = "$bookId:$spreadId"
    }

    data class Score(
        val reference: Reference,
        val goodMatches: Int,
        val inliers: Int,
    )

    enum class State { NO_REFERENCES, TOO_FEW_FEATURES, LOW_INLIERS, AMBIGUOUS, CONFIRMING, CONFIRMED, STABLE }
    enum class SearchPath { NONE, ADJACENT, LIBRARY }

    data class Decision(
        val confirmed: Score?,
        val best: Score?,
        val second: Score?,
        val state: State,
        val confirmationCount: Int,
        val confirmationsRequired: Int,
        val indexedReferences: Int,
        val geometricallyVerified: Int,
        val searchPath: SearchPath,
    )

    private data class IndexedReference(
        val reference: Reference,
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat,
    )

    private val orb: ORB
    private val matcher: DescriptorMatcher
    private val index: List<IndexedReference>
    private val candidatesByKey: Map<String, IndexedReference>
    private val plannedCandidates: List<LayeredSearchPlanner.Candidate>
    private var pendingKey: String? = null
    private var pendingCount = 0
    private var confirmedKey: String? = null
    private var lastContext: LayeredSearchPlanner.Context? = null

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV failed to initialize" }
        orb = ORB.create(1_400, 1.2f, 8, 19, 0, 2, ORB.HARRIS_SCORE, 31, 12)
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val built = mutableListOf<IndexedReference>()
        try {
            references.mapNotNullTo(built, ::indexReference)
            index = built
            candidatesByKey = index.associateBy { it.reference.key }
            plannedCandidates = index.map { it.planningCandidate() }
        } catch (error: Throwable) {
            built.forEach {
                it.keypoints.release()
                it.descriptors.release()
            }
            orb.clear()
            matcher.clear()
            throw error
        }
    }

    fun evaluate(
        frame: CameraFrameAnalyzer.GrayFrame,
        context: LayeredSearchPlanner.Context? = null,
    ): Decision {
        if (context != lastContext) {
            resetPending()
            confirmedKey = null
            lastContext = context
        }
        if (index.isEmpty()) return decision(null, null, State.NO_REFERENCES, 0, SearchPath.NONE)
        val image = Mat(frame.height, frame.width, CvType.CV_8UC1).apply { put(0, 0, frame.pixels) }
        val queryKeypoints = MatOfKeyPoint()
        val queryDescriptors = Mat()
        val mask = Mat()
        return try {
            orb.detectAndCompute(image, mask, queryKeypoints, queryDescriptors)
            if (queryDescriptors.empty() || queryKeypoints.rows() < 12) {
                resetPending()
                decision(null, null, State.TOO_FEW_FEATURES, 0, SearchPath.NONE)
            } else {
                val adjacent = LayeredSearchPlanner.adjacent(context, plannedCandidates)
                    .mapNotNull { candidatesByKey[it.key] }
                val adjacentScores = adjacent
                    .map { score(queryKeypoints, it, ratioMatches(queryDescriptors, it)) }
                    .sortedWith(compareByDescending<Score> { it.inliers }.thenByDescending { it.goodMatches })
                val adjacentBest = adjacentScores.firstOrNull()
                val adjacentSecond = adjacentScores.getOrNull(1)
                val strongAdjacent = adjacentBest != null &&
                    adjacentBest.inliers >= minimumInliers + 8 &&
                    (adjacentSecond == null || adjacentBest.inliers - adjacentSecond.inliers >= minimumInlierMargin + 4)
                if (strongAdjacent) {
                    confirm(
                        adjacentBest,
                        adjacentSecond,
                        adjacentScores.count { it.goodMatches >= 4 },
                        SearchPath.ADJACENT,
                    )
                } else {
                    val adjacentByKey = adjacentScores.associateBy { it.reference.key }
                    val cheap = LayeredSearchPlanner.libraryCandidates(plannedCandidates).mapNotNull { candidate ->
                        candidatesByKey[candidate.key]?.let { indexed ->
                            val goodMatches = adjacentByKey[candidate.key]?.goodMatches
                                ?: ratioMatches(queryDescriptors, indexed).size
                            LayeredSearchPlanner.CheapScore(candidate, goodMatches)
                        }
                    }
                    val shortlist = LayeredSearchPlanner.shortlist(cheap)
                    val finalists = LayeredSearchPlanner.finalists(
                        adjacentScores.map { it.reference.planningCandidate() },
                        shortlist,
                    )
                    val scores = finalists.mapNotNull { candidate ->
                        adjacentByKey[candidate.key] ?: candidatesByKey[candidate.key]?.let {
                            score(queryKeypoints, it, ratioMatches(queryDescriptors, it))
                        }
                    }.sortedWith(compareByDescending<Score> { it.inliers }.thenByDescending { it.goodMatches })
                    confirm(
                        scores.firstOrNull(),
                        scores.getOrNull(1),
                        scores.count { it.goodMatches >= 4 },
                        SearchPath.LIBRARY,
                    )
                }
            }
        } finally {
            image.release()
            queryKeypoints.release()
            queryDescriptors.release()
            mask.release()
        }
    }

    fun requireReconfirmation() {
        resetPending()
        confirmedKey = null
    }

    private fun confirm(best: Score?, second: Score?, verified: Int, searchPath: SearchPath): Decision {
        val rejection = when {
            best == null -> State.NO_REFERENCES
            best.inliers < minimumInliers -> State.LOW_INLIERS
            second != null && best.inliers - second.inliers < minimumInlierMargin -> State.AMBIGUOUS
            else -> null
        }
        if (rejection != null) {
            resetPending()
            return decision(best, second, rejection, verified, searchPath)
        }

        checkNotNull(best)
        if (pendingKey == best.reference.key) pendingCount++ else {
            pendingKey = best.reference.key
            pendingCount = 1
        }
        if (confirmedKey == best.reference.key) return decision(best, second, State.STABLE, verified, searchPath)
        if (pendingCount < confirmationsRequired) return decision(best, second, State.CONFIRMING, verified, searchPath)
        confirmedKey = best.reference.key
        return Decision(best, best, second, State.CONFIRMED, pendingCount, confirmationsRequired, index.size, verified, searchPath)
    }

    private data class MatchIndices(val query: Int, val train: Int)

    private fun ratioMatches(queryDescriptors: Mat, reference: IndexedReference): List<MatchIndices> {
        val pairs = ArrayList<MatOfDMatch>()
        return try {
            matcher.knnMatch(queryDescriptors, reference.descriptors, pairs, 2)
            pairs.mapNotNull { pair ->
                val matches = pair.toArray()
                matches.getOrNull(0)?.takeIf { first ->
                    matches.getOrNull(1)?.let { second -> first.distance < 0.75f * second.distance } == true
                }?.let { MatchIndices(it.queryIdx, it.trainIdx) }
            }
        } finally {
            pairs.forEach(MatOfDMatch::release)
        }
    }

    private fun score(
        queryKeypoints: MatOfKeyPoint,
        reference: IndexedReference,
        good: List<MatchIndices>,
    ): Score {
        if (good.size < 4) return Score(reference.reference, good.size, 0)

        val queryPoints = queryKeypoints.toArray()
        val referencePoints = reference.keypoints.toArray()
        val source = MatOfPoint2f(*good.map { queryPoints[it.query].pt }.toTypedArray())
        val target = MatOfPoint2f(*good.map { referencePoints[it.train].pt }.toTypedArray())
        val mask = Mat()
        return try {
            val homography = Calib3d.findHomography(source, target, Calib3d.RANSAC, 4.0, mask)
            homography.release()
            Score(reference.reference, good.size, Core.countNonZero(mask))
        } finally {
            source.release()
            target.release()
            mask.release()
        }
    }

    private fun indexReference(reference: Reference): IndexedReference? {
        if (!reference.imageFile.exists()) return null
        val original = Imgcodecs.imread(reference.imageFile.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        if (original.empty()) {
            original.release()
            return null
        }
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        val mask = Mat()
        var resized: Mat? = null
        var transferred = false
        try {
            val image = if (maxOf(original.width(), original.height()) > 720) {
                val scale = 720.0 / maxOf(original.width(), original.height())
                Mat().also {
                    resized = it
                    Imgproc.resize(original, it, Size(), scale, scale, Imgproc.INTER_AREA)
                }
            } else {
                original
            }
            orb.detectAndCompute(image, mask, keypoints, descriptors)
            if (descriptors.empty()) return null
            transferred = true
            return IndexedReference(reference, keypoints, descriptors)
        } finally {
            mask.release()
            resized?.release()
            original.release()
            if (!transferred) {
                keypoints.release()
                descriptors.release()
            }
        }
    }

    private fun resetPending() {
        pendingKey = null
        pendingCount = 0
    }

    private fun decision(best: Score?, second: Score?, state: State, verified: Int, searchPath: SearchPath) =
        Decision(null, best, second, state, pendingCount, confirmationsRequired, index.size, verified, searchPath)

    private fun Reference.planningCandidate() = LayeredSearchPlanner.Candidate(key, bookId, spreadOrdinal)

    private fun IndexedReference.planningCandidate() = LayeredSearchPlanner.Candidate(
        reference.key,
        reference.bookId,
        reference.spreadOrdinal,
    )

    override fun close() {
        index.forEach {
            it.keypoints.release()
            it.descriptors.release()
        }
        orb.clear()
        matcher.clear()
    }
}
