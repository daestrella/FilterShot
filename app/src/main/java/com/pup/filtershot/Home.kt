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
import android.widget.EditText

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
        val filePath = uri.path // Get the file path from the Uri
        selectedFilePathTextView.text = filePath ?: "No file selected" // Display the file path

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
            .setPositiveButton("OK") { dialog, _ ->
                val filename = input.text.toString()
                if (filename.isNotBlank() && videoUri != null) {
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
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(requireContext(), uri)

        // Get video duration and frame rate
        val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toInt() ?: 30

        // Output video file path
        val outputFilePath = File(Environment.getExternalStorageDirectory(), "$filename.mp4").absolutePath

        // Initialize VideoWriter
        val fourcc = fourcc('H', '2', '6', '4') // Codec
        val videoWriter = VideoWriter(outputFilePath, fourcc, frameRate.toDouble(), org.opencv.core.Size(1280.0, 720.0), true)

        if (!videoWriter.isOpened) {
            println("Error: Could not open video writer.")
            retriever.release()
            return
        }

        // Process each frame of the video
        var currentTimeUs = 0L
        while (currentTimeUs < videoDuration * 1000) { // Convert duration to microseconds
            val frameBitmap = retriever.getFrameAtTime(currentTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)

            if (frameBitmap != null) {
                val frameMat = Mat()
                Utils.bitmapToMat(frameBitmap, frameMat)

                // Process the frame using your Filter class
                val filter = Filter(requireContext())
                val processedFrame = filter.filterFrame(frameMat)

                // Write the processed frame to the video
                videoWriter.write(processedFrame)

                // Move to the next frame based on the frame rate
                currentTimeUs += (1000000L / frameRate) // Increment by the duration of one frame in microseconds
            } else {
                break // Exit if no more frames are available
            }
        }

        // Cleanup
        videoWriter.release()
        retriever.release()

        selectedFilePathTextView.text = "Processed video saved to: $outputFilePath"
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