package com.read4me.app.ui

import com.read4me.app.vision.PageTurnDetector
import org.junit.Assert.*
import org.junit.Test

class RecordingCaptureStateTest {
    @Test fun newCompositionInvalidatesQueuedResultsAndStableFrames() {
        val detector = PageTurnDetector()
        val state = RecordingCaptureState(detector, 1)
        val queued = detector.accept(ByteArray(768))
        state.stableFrameCount.set(3)
        state.stableFrameWidth.set(640)
        state.freeze(1_000L)
        assertTrue(state.automaticPageTurnsFrozen.get())
        assertEquals(0, state.stableFrameCount.get())
        assertEquals(0, state.stableFrameWidth.get())
        assertNotEquals(queued.generation, state.stableDetectorGeneration.get())
        assertEquals(Long.MIN_VALUE, state.allowedDetectorGeneration.get())
        assertEquals(4_000L, state.settleNotBeforeMs.get())
        assertEquals(9_000L, state.postOrientationGuardUntilMs.get())
    }

    @Test fun secondRotationRestartsTheSettlingWindow() {
        val state = RecordingCaptureState(PageTurnDetector(), 1)
        state.freeze(0L)
        val generation = state.stableDetectorGeneration.get()
        state.freeze(7_000L)
        assertEquals(15_000L, state.postOrientationGuardUntilMs.get())
        assertTrue(state.stableDetectorGeneration.get() > generation)
        assertFalse(canAccumulateStableFrame(9_000L, state.settleNotBeforeMs.get()))
    }

    @Test fun continuedSensorMotionExtendsGuardButSmallJitterDoesNot() {
        val state = RecordingCaptureState(PageTurnDetector(), 1)
        state.onSensorAngle(20, 0L)
        state.freeze(0L)
        state.onSensorAngle(23, 1_000L)
        assertEquals(8_000L, state.postOrientationGuardUntilMs.get())
        state.onSensorAngle(27, 2_000L)
        assertEquals(10_000L, state.postOrientationGuardUntilMs.get())
        state.onSensorAngle(-1, 3_000L)
        assertEquals(27, state.sensorLatestAngle.get())
    }
}
