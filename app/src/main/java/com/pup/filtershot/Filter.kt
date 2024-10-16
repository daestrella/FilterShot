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
            CvType.CV_32FC3 -> {
                // Already in the expected format, proceed with processing
                Log.e("", "CVType<specific> = ${CvType.CV_32FC3}")
            }
            CvType.CV_32FC4 -> {
                // Convert from RGBA to RGB
                val convertedFrame = Mat()
                Imgproc.cvtColor(landscapeFrame, convertedFrame, Imgproc.COLOR_RGBA2BGR)
                return filterFrame(convertedFrame) // Recur with the converted frame
            }
            CvType.CV_8UC4 -> {
                // Convert from RGBA to RGB (strip the alpha channel)
                Log.e("", "CVType<specific> = ${CvType.CV_8UC4}")
                val convertedFrame = Mat()
                landscapeFrame.convertTo(convertedFrame, CvType.CV_32F, 1.0 / 255.0f) // Normalize to range [0, 1]
                return filterFrame(convertedFrame) // Recur with the converted frame
            }
            else -> {
                Log.e("MatTypeError", "Unsupported frame type: ${landscapeFrame.type()}")
                return Mat() // Return an empty Mat or handle error appropriately
            }
        }

        // Proceed with the filtering logic
        val output = FloatArray(landscapeFrame.rows() * landscapeFrame.cols() * landscapeFrame.channels())
        landscapeFrame.get(0, 0, output)

        // Instead of dividing the values by 255 here, we process them directly.
        val output3D = Array(1) {
            Array(landscapeFrame.rows()) { row ->
                Array(landscapeFrame.cols()) { col ->
                    FloatArray(landscapeFrame.channels()) { channel ->
                        output[row * landscapeFrame.cols() * landscapeFrame.channels() + col * landscapeFrame.channels() + channel]
                    }
                }
            }
        }

        val filteredOutput = Array(1) {
            Array(landscapeFrame.rows()) {
                Array(landscapeFrame.cols()) { FloatArray(landscapeFrame.channels()) }
            }
        }

        interpreter?.run(output3D, filteredOutput)

        // Create an array for the Mat's output, converting directly to floating point values
        val outputMatArray = FloatArray(landscapeFrame.rows() * landscapeFrame.cols() * landscapeFrame.channels())

        for (row in filteredOutput[0].indices) {
            for (col in filteredOutput[0][row].indices) {
                for (channel in filteredOutput[0][row][col].indices) {
                    // Write the floating-point values directly to the array without rescaling by 255
                    outputMatArray[row * landscapeFrame.cols() * landscapeFrame.channels() + col * landscapeFrame.channels() + channel] =
                        filteredOutput[0][row][col][channel]
                }
            }
        }

        // Create the output Mat in the correct type (still floating-point)
        val outputMat = Mat(landscapeFrame.rows(), landscapeFrame.cols(), CvType.CV_32FC3)
        outputMat.put(0, 0, outputMatArray)

        // Optionally convert back to 8-bit if needed for display or further processing
        val convertedOutputMat = Mat()
        outputMat.convertTo(convertedOutputMat, CvType.CV_8UC3, 255.0)

        // Cleanup
        landscapeFrame.release() // Release if no longer needed
        return convertedOutputMat
    }


    fun close() {
        interpreter?.close()
    }

}