package com.read4me.app.vision

import kotlin.math.sqrt

/** Brightness- and contrast-normalized thumbnail used for closed-library matching. */
object VisualFingerprint {
    const val COLUMNS = 32
    const val ROWS = 24
    private const val OUTPUT_COLUMNS = 16
    private const val OUTPUT_ROWS = 12

    fun fromLuma(luma: ByteArray): ByteArray {
        require(luma.size == COLUMNS * ROWS)
        val reduced = FloatArray(OUTPUT_COLUMNS * OUTPUT_ROWS)
        var target = 0
        for (row in 0 until OUTPUT_ROWS) {
            for (column in 0 until OUTPUT_COLUMNS) {
                var sum = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val source = (row * 2 + dy) * COLUMNS + column * 2 + dx
                        sum += luma[source].toInt() and 0xff
                    }
                }
                reduced[target++] = sum / 4f
            }
        }

        val mean = reduced.average().toFloat()
        var variance = 0f
        reduced.forEach { value ->
            val delta = value - mean
            variance += delta * delta
        }
        val deviation = sqrt(variance / reduced.size).coerceAtLeast(8f)

        return ByteArray(reduced.size) { index ->
            val normalized = ((reduced[index] - mean) / deviation).coerceIn(-2.5f, 2.5f)
            (((normalized + 2.5f) / 5f) * 255f).toInt().toByte()
        }
    }

    fun similarity(left: ByteArray, right: ByteArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f
        if (left.size != OUTPUT_COLUMNS * OUTPUT_ROWS) return exactSimilarity(left, right)

        var best = 0f
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                var difference = 0L
                var compared = 0
                for (row in 0 until OUTPUT_ROWS) {
                    val otherRow = row + offsetY
                    if (otherRow !in 0 until OUTPUT_ROWS) continue
                    for (column in 0 until OUTPUT_COLUMNS) {
                        val otherColumn = column + offsetX
                        if (otherColumn !in 0 until OUTPUT_COLUMNS) continue
                        difference += kotlin.math.abs(
                            (left[row * OUTPUT_COLUMNS + column].toInt() and 0xff) -
                                (right[otherRow * OUTPUT_COLUMNS + otherColumn].toInt() and 0xff),
                        )
                        compared++
                    }
                }
                val score = 1f - difference.toFloat() / (compared * 255f)
                best = maxOf(best, score)
            }
        }
        return best.coerceIn(0f, 1f)
    }

    private fun exactSimilarity(left: ByteArray, right: ByteArray): Float {
        var difference = 0L
        for (index in left.indices) {
            difference += kotlin.math.abs(
                (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff),
            )
        }
        return (1f - difference.toFloat() / (left.size * 255f)).coerceIn(0f, 1f)
    }
}
