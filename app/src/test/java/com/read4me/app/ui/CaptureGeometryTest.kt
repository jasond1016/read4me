package com.read4me.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGeometryTest {
    @Test
    fun framingAspectRatiosMatchCaptureFormats() {
        assertEquals(0.75f, captureAspectRatio(CaptureFraming.SINGLE_PAGE), 0.0001f)
        assertEquals(1.5f, captureAspectRatio(CaptureFraming.TWO_PAGE_SPREAD), 0.0001f)
    }

    @Test
    fun portraitFrameIsCenteredAndContained() {
        assertCenteredAndContained(360f, 592f, CaptureFraming.SINGLE_PAGE)
        assertCenteredAndContained(360f, 592f, CaptureFraming.TWO_PAGE_SPREAD)
    }

    @Test
    fun landscapeFrameIsCenteredAndContained() {
        assertCenteredAndContained(1_920f, 1_080f, CaptureFraming.SINGLE_PAGE)
        assertCenteredAndContained(1_920f, 1_080f, CaptureFraming.TWO_PAGE_SPREAD)
        assertCenteredAndContained(300f, 500f, CaptureFraming.TWO_PAGE_SPREAD)
    }

    @Test
    fun staleOrientationResultCannotCommitAutomaticPageTurn() {
        assertFalse(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 5_000L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 0L,
                postOrientationGuardUntilMs = 0L,
                analyzedOrientation = 2,
                currentOrientation = 1,
                appliedOrientation = 2,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 4L,
                allowedDetectorGeneration = 4L,
            ),
        )
    }

    @Test
    fun orientationSettleDeadlineBlocksThenAllowsMatchingResult() {
        assertFalse(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 1_000L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 2_000L,
                postOrientationGuardUntilMs = 0L,
                analyzedOrientation = 1,
                currentOrientation = 1,
                appliedOrientation = 1,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 4L,
                allowedDetectorGeneration = 4L,
            ),
        )
        assertTrue(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 2_001L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 2_000L,
                postOrientationGuardUntilMs = 0L,
                analyzedOrientation = 1,
                currentOrientation = 1,
                appliedOrientation = 1,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 4L,
                allowedDetectorGeneration = 4L,
            ),
        )
    }

    @Test
    fun queuedTransitionGenerationCannotCommitAfterAutomaticDetectionResumes() {
        assertFalse(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 10_000L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 0L,
                postOrientationGuardUntilMs = 0L,
                analyzedOrientation = 1,
                currentOrientation = 1,
                appliedOrientation = 1,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 7L,
                allowedDetectorGeneration = 8L,
            ),
        )
    }

    @Test
    fun stableFramesBeforeSettleDeadlineCannotAccumulate() {
        assertFalse(canAccumulateStableFrame(nowMs = 2_999L, settleNotBeforeMs = 3_000L))
        assertTrue(canAccumulateStableFrame(nowMs = 3_000L, settleNotBeforeMs = 3_000L))
    }

    @Test
    fun orientationAngleBucketsIgnoreUnknownAndDetectRotationStart() {
        assertEquals(ORIENTATION_BUCKET_PORTRAIT, orientationBucketForAngle(0))
        assertEquals(ORIENTATION_BUCKET_LANDSCAPE, orientationBucketForAngle(90))
        assertEquals(ORIENTATION_BUCKET_PORTRAIT, orientationBucketForAngle(180))
        assertEquals(ORIENTATION_BUCKET_LANDSCAPE, orientationBucketForAngle(270))
        assertEquals(null, orientationBucketForAngle(-1))

        assertFalse(
            shouldFreezeForOrientation(
                angleDegrees = 10,
                stableAngleDegrees = 0,
                stableBucket = ORIENTATION_BUCKET_PORTRAIT,
                configurationBucket = ORIENTATION_BUCKET_PORTRAIT,
            ),
        )
        assertTrue(
            shouldFreezeForOrientation(
                angleDegrees = 25,
                stableAngleDegrees = 0,
                stableBucket = ORIENTATION_BUCKET_PORTRAIT,
                configurationBucket = ORIENTATION_BUCKET_PORTRAIT,
            ),
        )
        assertTrue(
            shouldFreezeForOrientation(
                angleDegrees = 90,
                stableAngleDegrees = 0,
                stableBucket = ORIENTATION_BUCKET_PORTRAIT,
                configurationBucket = ORIENTATION_BUCKET_PORTRAIT,
            ),
        )
    }

    @Test
    fun postOrientationGuardRejectsInsideAndAllowsAtBoundary() {
        assertFalse(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 8_000L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 0L,
                postOrientationGuardUntilMs = 8_001L,
                analyzedOrientation = 1,
                currentOrientation = 1,
                appliedOrientation = 1,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 4L,
                allowedDetectorGeneration = 4L,
            ),
        )
        assertTrue(
            shouldCommitAutomaticPageTurn(
                pageTurned = true,
                nowMs = 8_001L,
                suppressUntilMs = 0L,
                settleNotBeforeMs = 0L,
                postOrientationGuardUntilMs = 8_001L,
                analyzedOrientation = 1,
                currentOrientation = 1,
                appliedOrientation = 1,
                automaticPageTurnsFrozen = false,
                detectorGeneration = 4L,
                allowedDetectorGeneration = 4L,
            ),
        )
    }

    private fun assertCenteredAndContained(width: Float, height: Float, framing: CaptureFraming) {
        val frame = centeredCaptureFrame(width, height, framing)
        assertTrue(frame.left >= 0f)
        assertTrue(frame.top >= 0f)
        assertTrue(frame.left + frame.width <= width + 0.0001f)
        assertTrue(frame.top + frame.height <= height + 0.0001f)
        assertEquals((width - frame.width) / 2f, frame.left, 0.0001f)
        assertEquals((height - frame.height) / 2f, frame.top, 0.0001f)
        assertEquals(captureAspectRatio(framing), frame.width / frame.height, 0.0001f)
    }
}
