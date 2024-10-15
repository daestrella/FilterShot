package com.pup.filtershot

import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import android.content.Context
import android.util.Log
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.Videoio
import org.opencv.videoio.Videoio.CAP_PROP_TRIGGER
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
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = (inputStream as FileInputStream).channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
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

        // Log the type of the landscape frame
        Log.d("MatInfo", "Type: ${landscapeFrame.type()}")

        // Check the frame type and convert if necessary
        when (landscapeFrame.type()) {
            CvType.CV_8UC3 -> {
                // Already in the expected format, proceed with processing
            }
            CvType.CV_8UC1 -> {
                // Convert from grayscale to RGB
                val convertedFrame = Mat()
                Imgproc.cvtColor(landscapeFrame, convertedFrame, Imgproc.COLOR_GRAY2BGR)
                return filterFrame(convertedFrame) // Recur with the converted frame
            }
            CvType.CV_32FC3 -> {
                // If it's in float format, convert to CV_8UC3
                val convertedFrame = Mat()
                landscapeFrame.convertTo(convertedFrame, CvType.CV_8UC3)
                return filterFrame(convertedFrame) // Recur with the converted frame
            }
            CvType.CV_8UC4 -> {
                // Convert from RGBA to RGB (strip the alpha channel)
                Log.e("", "CVType<specific> = ${CvType.CV_8UC4}")
                val convertedFrame = Mat()
                Imgproc.cvtColor(landscapeFrame, convertedFrame, Imgproc.COLOR_RGBA2BGR)
                return filterFrame(convertedFrame) // Recur with the converted frame
            }
            Videoio.CAP_PROP_TRIGGER -> {
                Log.e("","CAP_PROP_TRIGGER")
            }
            else -> {
                Log.e("MatTypeError", "Unsupported frame type: ${landscapeFrame.type()}")
                Log.e("", "CVType<specific> = ${CvType.CV_8UC3}")
                Log.e("", "CVType<specific> = ${CvType.CV_8UC1}")
                Log.e("", "CVType<specific> = ${CvType.CV_32FC3}")
                return Mat() // Return an empty Mat or handle error appropriately
            }
        }

        // Proceed with the filtering logic
        val output = FloatArray(landscapeFrame.rows() * landscapeFrame.cols() * landscapeFrame.channels())
        landscapeFrame.get(0, 0, output)

        // Normalize pixel values if your model expects values between 0 and 1
        for (i in output.indices) {
            output[i] /= 255.0f
        }

        val filteredOutput = FloatArray(720 * 1280 * 3) // Adjust size as necessary
        interpreter?.run(output, filteredOutput)

        val outputMat = Mat(720, 1280, CvType.CV_32FC3)
        outputMat.put(0, 0, filteredOutput)

        // Cleanup
        landscapeFrame.release() // Release if no longer needed
        return outputMat
    }

    fun close() {
        interpreter?.close()
    }

}