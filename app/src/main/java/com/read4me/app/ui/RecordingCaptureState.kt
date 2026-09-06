package com.read4me.app.ui

import com.read4me.app.vision.PageTurnDetector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** State shared by camera analysis and the main-thread orientation/lifecycle callbacks. */
internal class RecordingCaptureState(private val detector: PageTurnDetector, orientation: Int) {
    val appliedOrientation = AtomicInteger(orientation)
    val automaticPageTurnsFrozen = AtomicBoolean(true)
    val automaticGateInitialized = AtomicBoolean(false)
    val allowedDetectorGeneration = AtomicLong(Long.MIN_VALUE)
    val stableDetectorGeneration = AtomicLong(Long.MIN_VALUE)
    val stableFrameCount = AtomicInteger(0)
    val stableFrameWidth = AtomicInteger(0)
    val stableFrameHeight = AtomicInteger(0)
    val settleNotBeforeMs = AtomicLong(0L)
    val postOrientationGuardUntilMs = AtomicLong(0L)
    val sensorStableAngle = AtomicInteger(-1)
    val sensorStableBucket = AtomicInteger(-1)
    val sensorLatestAngle = AtomicInteger(-1)
    val sensorTransitionLatched = AtomicBoolean(false)
    private val settlingAngle = AtomicInteger(-1)

    fun freeze(nowMs: Long) {
        settlingAngle.set(sensorLatestAngle.get())
        automaticPageTurnsFrozen.set(true)
        sensorTransitionLatched.set(true)
        settleNotBeforeMs.set(nowMs + 3_000L)
        postOrientationGuardUntilMs.set(nowMs + 8_000L)
        allowedDetectorGeneration.set(Long.MIN_VALUE)
        stableFrameCount.set(0)
        stableFrameWidth.set(0)
        stableFrameHeight.set(0)
        stableDetectorGeneration.set(detector.reset())
    }

    fun onSensorAngle(angle: Int, nowMs: Long) {
        if (orientationBucketForAngle(angle) == null) return
        sensorLatestAngle.set(angle)
        val previous = settlingAngle.get()
        if (sensorTransitionLatched.get() && previous >= 0) {
            val distance = kotlin.math.abs(previous - angle)
            if (minOf(distance, 360 - distance) >= 5) freeze(nowMs)
        }
    }

    fun markSensorDirectionStable() {
        val latestAngle = sensorLatestAngle.get()
        sensorStableAngle.set(latestAngle)
        sensorStableBucket.set(orientationBucketForAngle(latestAngle) ?: -1)
        sensorTransitionLatched.set(false)
    }
}
