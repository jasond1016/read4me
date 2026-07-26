package com.read4me.app.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class CameraFrameAnalyzer(
    private val detector: PageTurnDetector,
    private val onFrame: (PageTurnDetector.Result, ByteArray, GrayFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    data class GrayFrame(val width: Int, val height: Int, val pixels: ByteArray)

    private var frameCounter = 0

    override fun analyze(image: ImageProxy) {
        try {
            val signature = lumaSignature(image, columns = 32, rows = 24)
            val result = detector.accept(signature)
            frameCounter++
            if (result.pageTurned || frameCounter % 3 == 0) {
                onFrame(result, signature, grayFrame(image))
            }
        } finally {
            image.close()
        }
    }

    private fun lumaSignature(image: ImageProxy, columns: Int, rows: Int): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val result = ByteArray(columns * rows)

        var target = 0
        for (row in 0 until rows) {
            val y = crop.top + ((row + 0.5f) * height / rows).toInt().coerceIn(0, height - 1)
            for (column in 0 until columns) {
                val x = crop.left + ((column + 0.5f) * width / columns).toInt().coerceIn(0, width - 1)
                result[target++] = buffer.get(y * rowStride + x * pixelStride)
            }
        }
        return result
    }

    private fun grayFrame(image: ImageProxy, maximumWidth: Int = 640): GrayFrame {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val crop = image.cropRect
        val scale = minOf(1f, maximumWidth.toFloat() / crop.width())
        val targetWidth = (crop.width() * scale).toInt().coerceAtLeast(1)
        val targetHeight = (crop.height() * scale).toInt().coerceAtLeast(1)
        val pixels = ByteArray(targetWidth * targetHeight)
        var target = 0
        for (row in 0 until targetHeight) {
            val y = crop.top + ((row + 0.5f) * crop.height() / targetHeight)
                .toInt()
                .coerceIn(0, crop.height() - 1)
            for (column in 0 until targetWidth) {
                val x = crop.left + ((column + 0.5f) * crop.width() / targetWidth)
                    .toInt()
                    .coerceIn(0, crop.width() - 1)
                pixels[target++] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
            }
        }
        return GrayFrame(targetWidth, targetHeight, pixels)
    }
}
