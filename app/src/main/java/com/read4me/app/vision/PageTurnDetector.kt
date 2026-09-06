package com.read4me.app.vision

import kotlin.math.abs

/**
 * Detects a page turn as motion followed by a stable image whose content differs
 * from the last committed spread. Signatures are low-resolution luma samples.
 */
class PageTurnDetector(
    private val motionThreshold: Float = 18f,
    private val stableThreshold: Float = 5f,
    private val pageChangeThreshold: Float = 10f,
    private val stableFramesRequired: Int = 5,
) {
    data class Result(
        val motionScore: Float,
        val isMoving: Boolean,
        val pageTurned: Boolean,
        /** Generation of the detector baseline used for this result. */
        val generation: Long,
    )

    private var previousFrame: ByteArray? = null
    private var committedSpread: ByteArray? = null
    private var moving = false
    private var stableFrames = 0
    private var generation = 0L

    @Synchronized
    fun accept(signature: ByteArray): Result {
        val previous = previousFrame
        if (previous == null || previous.size != signature.size) {
            previousFrame = signature.copyOf()
            committedSpread = signature.copyOf()
            return Result(0f, isMoving = false, pageTurned = false, generation = generation)
        }

        val motionScore = difference(previous, signature)
        previousFrame = signature.copyOf()

        if (!moving && motionScore >= motionThreshold) {
            moving = true
            stableFrames = 0
        }

        if (moving) {
            if (motionScore <= stableThreshold) {
                stableFrames++
            } else {
                stableFrames = 0
            }

            if (stableFrames >= stableFramesRequired) {
                moving = false
                stableFrames = 0
                val changed = committedSpread?.let {
                    it.size == signature.size && difference(it, signature) >= pageChangeThreshold
                } ?: true
                if (changed) {
                    committedSpread = signature.copyOf()
                    return Result(motionScore, isMoving = false, pageTurned = true, generation = generation)
                }
            }
        }

        return Result(motionScore, isMoving = moving, pageTurned = false, generation = generation)
    }

    @Synchronized
    fun reset(): Long {
        generation++
        previousFrame = null
        committedSpread = null
        moving = false
        stableFrames = 0
        return generation
    }

    private fun difference(left: ByteArray, right: ByteArray): Float {
        var sum = 0L
        for (index in left.indices) {
            sum += abs((left[index].toInt() and 0xff) - (right[index].toInt() and 0xff))
        }
        return sum.toFloat() / left.size
    }
}
