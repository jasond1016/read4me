package com.read4me.app.vision

import com.read4me.app.model.PhotoQuality
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/** Offline, deterministic photo checks. Thresholds are advisory, never save gates. */
object PhotoQualityAnalyzer {
    const val MIN_LAPLACIAN_VARIANCE = 75.0
    const val MIN_KEYPOINTS = 80
    const val MIN_MEAN_BRIGHTNESS = 55.0
    const val MAX_DARK_RATIO = 0.45
    const val MAX_OVEREXPOSED_RATIO = 0.25
    private const val MAX_EDGE = 720.0

    fun analyze(file: File): PhotoQuality = runCatching {
        check(file.isFile && OpenCVLoader.initLocal()) { "Image or OpenCV unavailable" }
        val original = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        val image = Mat()
        val laplacian = Mat()
        val dark = Mat()
        val bright = Mat()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        val mask = Mat()
        var orb: ORB? = null
        try {
            check(!original.empty()) { "JPEG cannot be decoded" }
            val scale = minOf(1.0, MAX_EDGE / maxOf(original.width(), original.height()))
            Imgproc.resize(original, image, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.Laplacian(image, laplacian, org.opencv.core.CvType.CV_64F)
            val mean = Core.mean(image).`val`[0]
            val meanMat = MatOfDouble(); val stddev = MatOfDouble()
            val variance = try { Core.meanStdDev(laplacian, meanMat, stddev); stddev.toArray()[0].let { it * it } }
                finally { meanMat.release(); stddev.release() }
            orb = ORB.create()
            orb.detectAndCompute(image, mask, keypoints, descriptors)
            Core.compare(image, org.opencv.core.Scalar(35.0), dark, Core.CMP_LT)
            Core.compare(image, org.opencv.core.Scalar(245.0), bright, Core.CMP_GT)
            val pixels = image.total().toDouble().coerceAtLeast(1.0)
            val darkRatio = Core.countNonZero(dark) / pixels
            val brightRatio = Core.countNonZero(bright) / pixels
            val issues = buildList {
                if (variance < MIN_LAPLACIAN_VARIANCE) add(PhotoQuality.Issue.BLURRY)
                if (keypoints.rows() < MIN_KEYPOINTS) add(PhotoQuality.Issue.TOO_FEW_DETAILS)
                if (mean < MIN_MEAN_BRIGHTNESS || darkRatio > MAX_DARK_RATIO) add(PhotoQuality.Issue.TOO_DARK)
                if (brightRatio > MAX_OVEREXPOSED_RATIO) add(PhotoQuality.Issue.OVEREXPOSED)
            }
            PhotoQuality(if (issues.isEmpty()) PhotoQuality.Status.GOOD else PhotoQuality.Status.ISSUES,
                variance, keypoints.rows(), mean, darkRatio, brightRatio, issues)
        } finally {
            original.release(); image.release(); laplacian.release(); dark.release(); bright.release()
            keypoints.release(); descriptors.release(); mask.release(); orb?.clear()
        }
    }.getOrElse { PhotoQuality(PhotoQuality.Status.UNAVAILABLE) }
}
