package com.pup.filtershot

import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import android.content.Context
import android.util.Log
import org.opencv.core.Core
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.IValue

class Filter(context: Context) {
    private var openCVInitialized = false
    private var model: Module? = null

    init {
        if (!openCVInitialized) {
            openCVInitialized = OpenCVLoader.initDebug()
            if (!openCVInitialized) {
                println("OpenCV did not initialize.")
            } else {
                println("OpenCV initialized successfully.")
            }
        }
        try {
            model = LiteModuleLoader.load("model.ptl")
        } catch (e: Exception) {
            Log.e("Filter", "Failed to load model: ${e.message}")
        }
    }

    private fun isImgLandscape(img: Mat): Boolean {
        return img.rows() < img.cols()
    }

    private fun convertImgToLandscape(img: Mat): Mat {
        val rotatedImg = Mat()
        Core.rotate(img, rotatedImg, Core.ROTATE_90_COUNTERCLOCKWISE)
        return rotatedImg
    }

    private fun convertImgBackToPortrait(img: Mat): Mat {
        val rotatedImg = Mat()
        Core.rotate(img, rotatedImg, Core.ROTATE_90_CLOCKWISE)
        return rotatedImg
    }

    private fun convertMatToTensor(mat: Mat): Tensor {
        val height = mat.rows()
        val width = mat.cols()
        val channels = mat.channels()
        val array = FloatArray(height * width * channels)

        // Convert Mat to FloatArray
        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixelValues = mat.get(i, j) // Returns an array of channel values (e.g., [R, G, B] for 3 channels)

                for (k in 0 until channels) {
                    array[(((i * width) + j) * channels) + k] = pixelValues[k].toFloat() / 255.0f // Normalize if needed
                }
            }
        }

        // Convert FloatArray to Tensor
        return Tensor.fromBlob(array, longArrayOf(1, height.toLong(), width.toLong(), channels.toLong()))
    }

    fun filterFrames(frames: MutableList<Mat>): Pair<MutableList<Mat>, MutableList<Mat>> {
        val isLandscape = isImgLandscape(frames[0])
        val inputTensors = frames.map {
            val img = if (isLandscape) it else convertImgToLandscape(it)
            convertMatToTensor(img)
        }

        val inputTensor = Tensor.stack(inputTensors.toTypedArray())

        // Log the type of the landscape frame
        Log.d("MatInfo", "Type: ${frames[0].type()}")

        val outputs = model?.forward(IValue.from(inputTensor))?.toTuple()
        val denoisedTensor = outputs?.get(0)?.toTensor()
        val segmentedTensor = outputs?.get(1)?.toTensor()

        val segmentedList = mutableListOf<Mat>()
        val denoisedList = mutableListOf<Mat>()

        segmentedList?.let {
            val outputArray = it.dataAsFloatArray
            val width = frames[0].cols()
            val height = frames[0].rows()

            for (i in frames.indices) {
                val outputMat = Mat(height, width, CvType.CV_32FC3)
                outputMat.put(0, 0, outputArray, i * height * width * 3, height * width * 3)

                val finalMat = Mat()
                outputMat.convertTo(finalMat, CvType.CV_8UC3, 255.0)

                // Rotate back to portrait if originally in portrait mode
                segmentedList.add(if (isLandscape) finalMat else convertImgBackToPortrait(finalMat))
            }
        }

        denoisedList?.let {
            val outputArray = it.dataAsFloatArray
            val width = frames[0].cols()
            val height = frames[0].rows()

            for (i in frames.indices) {
                val outputMat = Mat(height, width, CvType.CV_32FC3)
                outputMat.put(0, 0, outputArray, i * height * width * 3, height * width * 3)

                val finalMat = Mat()
                outputMat.convertTo(finalMat, CvType.CV_8UC3, 255.0)

                // Rotate back to portrait if originally in portrait mode
                denoisedList.add(if (isLandscape) finalMat else convertImgBackToPortrait(finalMat))
            }
        }

        return Pair(segmentedList, denoisedList)
    }

    fun close() {
        model?.destroy()
    }

}