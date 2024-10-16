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
import org.opencv.imgproc.Imgproc

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

            // Output video file path
            val outputFilePath = File(requireContext().getExternalFilesDir(null), "$filename.mp4").absolutePath
            val filter = Filter(requireContext())

            // Initialize VideoWriter
            val fourcc = VideoWriter.fourcc('H', '2', '6', '4') // Codec
            val videoWriter = VideoWriter(outputFilePath, fourcc, frameRate.toDouble(), org.opencv.core.Size(1280.0, 720.0), true)

            if (!videoWriter.isOpened) {
                println("Error: Could not open video writer.")
                retriever.release()
                return@launch
            }

            // Process each frame of the video
            var currentTimeUs = 0L
            while (currentTimeUs < videoDuration * 1000) { // Convert duration to microseconds
                val frameBitmap = retriever.getFrameAtTime(currentTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (frameBitmap != null) {
                    Log.d("Home", "Frame dimensions: ${frameBitmap.width} x ${frameBitmap.height}")
                }

                if (frameBitmap != null && frameBitmap.width > 0 && frameBitmap.height > 0) {

                    val frameMat = Mat(frameBitmap.height, frameBitmap.width, CvType.CV_8UC4)
                    Utils.bitmapToMat(frameBitmap, frameMat)
                    // Convert from CV_8UC4 to CV_8UC3 if needed
                    val processedMat = Mat()
                    Imgproc.cvtColor(frameMat, processedMat, Imgproc.COLOR_RGBA2BGR)

                    // Check if the frame is of the expected type
                    if (frameMat.type() != org.opencv.core.CvType.CV_8UC3) {
                        frameMat.convertTo(frameMat, org.opencv.core.CvType.CV_8UC3)
                    }

                    // Process the frame using your Filter class
                    val processedFrame = filter.filterFrame(frameMat)

                    // Ensure frame is of the correct size and type before writing
                    if (processedFrame.width() <= 1280 && processedFrame.height() <= 720 && processedFrame.type() == org.opencv.core.CvType.CV_8UC3) {
                        videoWriter.write(processedFrame)
                    } else {
                        Log.e("MatTypeError", "Invalid frame size or type")
                        break // Exit if the frame is not suitable
                    }

                    // Move to the next frame based on the frame rate
                    currentTimeUs += (1000000L / frameRate) // Increment by the duration of one frame in microseconds
                } else {
                    Log.e("MatTypeError", "No valid frame at $currentTimeUs")
                    break // Exit if no more frames are available
                }
            }


            // Cleanup
            videoWriter.release()
            retriever.release()
            filter.close()

            // Update UI on the main thread
            withContext(Dispatchers.Main) {
                selectedFilePathTextView.text = "Processed video saved to: $outputFilePath"
            }
        }
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