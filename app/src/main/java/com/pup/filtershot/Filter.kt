package com.pup.filtershot

import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import android.content.Context
import org.opencv.core.Core
import org.tensorflow.lite.Interpreter
import java.io.BufferedInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

class Filter(context: Context) {
    private var openCVInitialized = false
    private var interpreter: Interpreter? = null

    init {
        if (!openCVInitialized) {
            openCVInitialized = OpenCVLoader.initDebug()
            if (!openCVInitialized) {
                println("OpenCV did not initialize.")
            } else {
                println("OpenCV initialized successfully.")
            }
        }

        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("filtershot.tflite")
        val inputStream = BufferedInputStream(context.assets.open("filtershot.tflite"))
        val fileChannel = (inputStream as FileInputStream).channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, assetFileDescriptor.length)
    }

    private fun ensureImgIsLandscape(img: Mat): Mat {
        return if (img.rows() < img.cols()) {
            img
        } else {
            val rotatedImg = Mat()
            Core.rotate(img, rotatedImg, Core.ROTATE_90_CLOCKWISE)
            rotatedImg
        }
    }

    fun filterFrame(frame: Mat): Mat {
        val landscapeFrame = ensureImgIsLandscape(frame)
        val output = FloatArray(landscapeFrame.rows() * landscapeFrame.cols() * landscapeFrame.channels())
        landscapeFrame.get(0, 0, output)

        val filteredOutput = FloatArray(720 * 1280 * 3)
        interpreter?.run(output, filteredOutput)

        val outputMat = Mat(720, 1280, CvType.CV_32FC3)
        outputMat.put(0, 0, filteredOutput)

        return outputMat
    }

    fun close() {
        interpreter?.close()
    }

}