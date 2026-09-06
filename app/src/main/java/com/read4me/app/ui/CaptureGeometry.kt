package com.read4me.app.ui

/** The two capture compositions supported by the camera workflows. */
internal enum class CaptureFraming(val label: String) {
    SINGLE_PAGE("单页／封面"),
    TWO_PAGE_SPREAD("左右两页"),
}

internal data class CaptureFrameBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal fun captureAspectRatio(framing: CaptureFraming): Float = when (framing) {
    CaptureFraming.SINGLE_PAGE -> 3f / 4f
    CaptureFraming.TWO_PAGE_SPREAD -> 3f / 2f
}

internal const val ORIENTATION_BUCKET_PORTRAIT = 0
internal const val ORIENTATION_BUCKET_LANDSCAPE = 1

internal fun orientationBucketForAngle(angleDegrees: Int): Int? {
    if (angleDegrees !in 0..359) return null
    return when (((angleDegrees + 45) / 90) % 4) {
        0, 2 -> ORIENTATION_BUCKET_PORTRAIT
        else -> ORIENTATION_BUCKET_LANDSCAPE
    }
}

internal fun shouldFreezeForOrientation(
    angleDegrees: Int,
    stableAngleDegrees: Int,
    stableBucket: Int?,
    configurationBucket: Int,
): Boolean {
    val bucket = orientationBucketForAngle(angleDegrees) ?: return false
    val angleDistance = if (stableAngleDegrees in 0..359) {
        val distance = kotlin.math.abs(angleDegrees - stableAngleDegrees)
        minOf(distance, 360 - distance)
    } else 0
    return (stableBucket?.let { bucket != it } ?: (bucket != configurationBucket)) ||
        angleDistance >= 20
}

/** Returns the largest centered rectangle with [aspectRatio] inside the given bounds. */
internal fun centeredCaptureFrame(
    containerWidth: Float,
    containerHeight: Float,
    aspectRatio: Float,
): CaptureFrameBounds {
    require(containerWidth >= 0f && containerHeight >= 0f) { "Container bounds must be non-negative" }
    require(aspectRatio > 0f) { "Aspect ratio must be positive" }
    val width = minOf(containerWidth, containerHeight * aspectRatio)
    val height = width / aspectRatio
    return CaptureFrameBounds(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height,
    )
}

internal fun centeredCaptureFrame(
    containerWidth: Float,
    containerHeight: Float,
    framing: CaptureFraming,
): CaptureFrameBounds = centeredCaptureFrame(
    containerWidth,
    containerHeight,
    captureAspectRatio(framing),
)

internal fun canAccumulateStableFrame(nowMs: Long, settleNotBeforeMs: Long): Boolean =
    nowMs >= settleNotBeforeMs

/**
 * Keeps an analysis result from committing an automatic page turn while the
 * camera is settling on a new device orientation.
 */
internal fun shouldCommitAutomaticPageTurn(
    pageTurned: Boolean,
    nowMs: Long,
    suppressUntilMs: Long,
    settleNotBeforeMs: Long,
    postOrientationGuardUntilMs: Long,
    analyzedOrientation: Int,
    currentOrientation: Int,
    appliedOrientation: Int,
    automaticPageTurnsFrozen: Boolean,
    detectorGeneration: Long,
    allowedDetectorGeneration: Long,
): Boolean = pageTurned &&
    !automaticPageTurnsFrozen &&
    nowMs >= suppressUntilMs &&
    nowMs >= settleNotBeforeMs &&
    nowMs >= postOrientationGuardUntilMs &&
    analyzedOrientation == currentOrientation &&
    appliedOrientation == currentOrientation &&
    detectorGeneration == allowedDetectorGeneration
