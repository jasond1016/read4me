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
        val imageFile: File,
    ) {
        val key: String get() = "$bookId:$spreadOrdinal"
    }

    data class Score(
        val reference: Reference,
        val goodMatches: Int,
        val inliers: Int,
    )

    enum class State { NO_REFERENCES, TOO_FEW_FEATURES, LOW_INLIERS, AMBIGUOUS, CONFIRMING, CONFIRMED, STABLE }

    data class Decision(
        val confirmed: Score?,
        val best: Score?,
        val second: Score?,
        val state: State,
        val confirmationCount: Int,
        val confirmationsRequired: Int,
    )

    private data class IndexedReference(
        val reference: Reference,
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat,
    )

    private val orb: ORB
    private val matcher: DescriptorMatcher
    private val index: List<IndexedReference>
    private var pendingKey: String? = null
    private var pendingCount = 0
    private var confirmedKey: String? = null

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV failed to initialize" }
        orb = ORB.create(1_400, 1.2f, 8, 19, 0, 2, ORB.HARRIS_SCORE, 31, 12)
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        index = references.mapNotNull(::indexReference)
    }

    fun evaluate(frame: CameraFrameAnalyzer.GrayFrame): Decision {
        if (index.isEmpty()) return decision(null, null, State.NO_REFERENCES)
        val image = Mat(frame.height, frame.width, CvType.CV_8UC1).apply { put(0, 0, frame.pixels) }
        val queryKeypoints = MatOfKeyPoint()
        val queryDescriptors = Mat()
        val mask = Mat()
        return try {
            orb.detectAndCompute(image, mask, queryKeypoints, queryDescriptors)
            if (queryDescriptors.empty() || queryKeypoints.rows() < 12) {
                resetPending()
                decision(null, null, State.TOO_FEW_FEATURES)
            } else {
                val scores = index
                    .map { score(queryKeypoints, queryDescriptors, it) }
                    .sortedWith(compareByDescending<Score> { it.inliers }.thenByDescending { it.goodMatches })
                confirm(scores.firstOrNull(), scores.getOrNull(1))
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

    private fun confirm(best: Score?, second: Score?): Decision {
        val rejection = when {
            best == null -> State.NO_REFERENCES
            best.inliers < minimumInliers -> State.LOW_INLIERS
            second != null && best.inliers - second.inliers < minimumInlierMargin -> State.AMBIGUOUS
            else -> null
        }
        if (rejection != null) {
            resetPending()
            return decision(best, second, rejection)
        }

        checkNotNull(best)
        if (pendingKey == best.reference.key) pendingCount++ else {
            pendingKey = best.reference.key
            pendingCount = 1
        }
        if (confirmedKey == best.reference.key) return decision(best, second, State.STABLE)
        if (pendingCount < confirmationsRequired) return decision(best, second, State.CONFIRMING)
        confirmedKey = best.reference.key
        return Decision(best, best, second, State.CONFIRMED, pendingCount, confirmationsRequired)
    }

    private fun score(
        queryKeypoints: MatOfKeyPoint,
        queryDescriptors: Mat,
        reference: IndexedReference,
    ): Score {
        val pairs = ArrayList<MatOfDMatch>()
        matcher.knnMatch(queryDescriptors, reference.descriptors, pairs, 2)
        val good = pairs.mapNotNull { pair ->
            val matches = pair.toArray()
            pair.release()
            matches.getOrNull(0)?.takeIf { first ->
                matches.getOrNull(1)?.let { second -> first.distance < 0.75f * second.distance } == true
            }
        }
        if (good.size < 4) return Score(reference.reference, good.size, 0)

        val queryPoints = queryKeypoints.toArray()
        val referencePoints = reference.keypoints.toArray()
        val source = MatOfPoint2f(*good.map { queryPoints[it.queryIdx].pt }.toTypedArray())
        val target = MatOfPoint2f(*good.map { referencePoints[it.trainIdx].pt }.toTypedArray())
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
        val image = if (maxOf(original.width(), original.height()) > 720) {
            val scale = 720.0 / maxOf(original.width(), original.height())
            Mat().also { Imgproc.resize(original, it, Size(), scale, scale, Imgproc.INTER_AREA) }
        } else {
            original
        }
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        val mask = Mat()
        try {
            orb.detectAndCompute(image, mask, keypoints, descriptors)
        } catch (error: Throwable) {
            keypoints.release()
            descriptors.release()
            throw error
        } finally {
            mask.release()
            if (image !== original) image.release()
            original.release()
        }
        if (descriptors.empty()) {
            keypoints.release()
            descriptors.release()
            return null
        }
        return IndexedReference(reference, keypoints, descriptors)
    }

    private fun resetPending() {
        pendingKey = null
        pendingCount = 0
    }

    private fun decision(best: Score?, second: Score?, state: State) =
        Decision(null, best, second, state, pendingCount, confirmationsRequired)

    override fun close() {
        index.forEach {
            it.keypoints.release()
            it.descriptors.release()
        }
        orb.clear()
        matcher.clear()
    }
}
