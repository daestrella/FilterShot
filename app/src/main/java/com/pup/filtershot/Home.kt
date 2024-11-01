package com.pup.filtershot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.media.MediaMetadataRetriever
import org.opencv.core.Mat
import org.opencv.android.Utils
import org.opencv.videoio.VideoWriter
import org.opencv.videoio.VideoWriter.fourcc
import android.os.Environment
import java.io.File
import android.app.AlertDialog
import android.util.Log
import android.widget.EditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.CvType
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import org.pytorch.Tensor
import android.graphics.Bitmap

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val PICK_FILE_REQUEST_CODE = 1

class Home : Fragment() {
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var selectedFilePathTextView: TextView
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views
        val pickFileButton: Button = view.findViewById(R.id.pick_file_button)
        selectedFilePathTextView = view.findViewById(R.id.selected_file_path)

        // Set up button click listener
        pickFileButton.setOnClickListener {
            openFilePicker()
        }

        return view
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*" // Set MIME type to filter for video files
        }
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                videoUri = uri
                handleFileUri(uri)
            }
        }
    }

    private fun handleFileUri(uri: Uri) {
        Log.d("FileURI", "Selected URI: $uri")
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        inputStream?.let {
            // Use inputStream as needed, e.g., read bytes or process file
            // Don't forget to close the inputStream after use
            it.close()
        } ?: run {
            Log.e("FileAccess", "Failed to open InputStream for URI: $uri")
        }
        // Proceed with showing filename dialog
        showFilenameInputDialog()
    }

    private fun showFilenameInputDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext())
        val input = EditText(requireContext()).apply {
            hint = "Enter filename"
        }

        dialogBuilder.setTitle("Save Video")
            .setMessage("Enter a name for the processed video:")
            .setView(input)
            .setPositiveButton("Denoise") { dialog, _ ->
                val filename = input.text.toString()
                if (filename.isNotBlank() && videoUri != null) {
                    selectedFilePathTextView.text = "Denoising the Video..."
                    processVideo(videoUri!!, filename)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private fun processVideo(uri: Uri, filename: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)

            // Get video duration and frame rate
            val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toInt() ?: 24
            val vidBatchSize = 4

            // Output video file path
            val segmentedFilePath = File(requireContext().getExternalFilesDir(null), "$filename-segmented.mp4").absolutePath
            val denoisedFilePath = File(requireContext().getExternalFilesDir(null), "$filename-denoised.mp4").absolutePath
            val filter = Filter(requireContext())

            // Initialize VideoWriter
            val fourcc = VideoWriter.fourcc('H', '2', '6', '4') // Codec
            val segmentedVideoWriter = VideoWriter(segmentedFilePath, fourcc, frameRate.toDouble(), org.opencv.core.Size(1280.0, 720.0), true)
            val denoisedVideoWriter = VideoWriter(denoisedFilePath, fourcc, frameRate.toDouble(), org.opencv.core.Size(1280.0, 720.0), true)

            if (!videoWriter.isOpened) {
                retriever.release()
                return@launch
            }

            val totalFrames = (videoDuration / (1000 / frameRate)).toInt()
            val frameBatches = (totalFrames / vidBatchSize).coerceAtMost(totalFrames)

            for (batch in 0 until frameBatches) {
                val frameBatch = mutableListOf<Mat>()

                for (frame in 0 until vidBatchSize) {
                    val currentFrameTimeUs = ((vidBatchSize * batch) + frame) * 1000L
                    val frameBitmap = retriever.getFrameAtTime(currentFrameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                    if (frameBitmap == null) {
                        Log.w("BitmapInfo", "No bitmap is loaded for time $currentFrameTimeUs")
                        break
                    }

                    Log.d("Home", "Frame dimensions: ${frameBitmap.width} x ${frameBitmap.height}")

                    var frameMatrix = Mat(frameBitmap.height, frameBitmap.width, CvType.CV_32FC3)
                    Utils.bitmapToMat(frameBitmap, frameMatrix)
                    frameMatrix.convertTo(frameMatrix, CvType.CV_32F, 1.0 / 255.0f)

                    frameBatch.add(frameMatrix)
                }

                val (segmentedBatch, denoisedBatch) = filter.filterFrames(frameBatch)

                for (frame in denoisedBatch) {
                    denoisedVideoWriter.write(frame)
                    frame.release()
                }

                for (frame in segmentedBatch) {
                    segmentedVideoWriter.write(frame)
                    frame.release()
                }

                frameBatch.forEach { it.release() }
            }

            // Cleanup
            videoWriter.release()
            retriever.release()
            filter.close()

            // Update UI on the main thread
            withContext(Dispatchers.Main) {
                selectedFilePathTextView.text = "Denoised video saved to: $denoisedFilePath\nSegmented video saved to: $segmentedFilePath"
            }
        }
    }

    // Method to normalize the Mat
    private fun normalizeMat(mat: Mat) {
        mat.convertTo(mat, CvType.CV_32F) // Convert to float type
        Core.normalize(mat, mat, 0.0, 1.0, Core.NORM_MINMAX) // Normalize to [0, 1]
    }

    // Method to convert Mat to Tensor
    private fun matToTensor(mat: Mat): Tensor {
        // Ensure the Mat is of type CV_32F (float)
        if (mat.type() != CvType.CV_32F) {
            mat.convertTo(mat, CvType.CV_32F) // Convert to float type if needed
        }

        // Get size and create a tensor
        val size = mat.size()
        val tensor = Tensor.empty(size.height.toInt(), size.width.toInt(), mat.channels())

        // Copy data from Mat to the tensor
        mat.get(0, 0, tensor.dataPointer()) // Assuming mat is in the format that matches tensor

        // Reshape tensor to 4D: (1, width, height, channels)
        return tensor.unsqueeze(0) // Adding batch dimension
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Home().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}