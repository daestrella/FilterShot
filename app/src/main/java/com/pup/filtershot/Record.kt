package com.pup.filtershot

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.util.*

class Record : Fragment() {

    private lateinit var textureView: TextureView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var cameraId: String
    private var previewSize: Size? = null

    // Registering the permission launcher to handle multiple permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioPermissionGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val storagePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false

        if (cameraPermissionGranted && audioPermissionGranted && storagePermissionGranted) {
            openCamera() // Open the camera if all permissions are granted
        } else {
            Toast.makeText(context, "Permissions denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_record, container, false)

        textureView = view.findViewById(R.id.textureView)
        startButton = view.findViewById(R.id.btnStartRecord)
        stopButton = view.findViewById(R.id.btnStopRecord)

        stopButton.visibility = View.GONE // Hide stop button initially

        startButton.setOnClickListener {
            if (arePermissionsGranted()) {
                startRecording()
            } else {
                requestPermissions() // Request permissions using the modern API
            }
        }

        stopButton.setOnClickListener {
            stopRecording()
        }

        textureView.surfaceTextureListener = surfaceTextureListener

        return view
    }

    private fun arePermissionsGranted(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        val audioPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
        val storagePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                audioPermission == PackageManager.PERMISSION_GRANTED &&
                storagePermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        // Use the ActivityResultContracts API to request permissions
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (arePermissionsGranted()) {
                openCamera() // Open camera when surface is available
            } else {
                requestPermissions() // Request permissions if not granted
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun openCamera() {
        val cameraManager = requireActivity().getSystemService(CameraManager::class.java)

        // Find the front-facing camera
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                cameraId = id
                val streamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                previewSize = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)?.get(0)
                break
            }
        }

        try {
            cameraManager.openCamera(cameraId, stateCallback, null)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Camera permission denied!", Toast.LENGTH_SHORT).show()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview() // Once the camera is opened, start the preview
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }
    }

    private fun startPreview() {
        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize?.width ?: 0, previewSize?.height ?: 0)
        val surface = Surface(surfaceTexture)

        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(
            Collections.singletonList(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null
        )
    }

    private fun startRecording() {
        try {
            mediaRecorder = MediaRecorder()

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(getOutputMediaFile()) // Set output file path
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(previewSize!!.width, previewSize!!.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }

            val surfaceTexture = textureView.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            val surface = Surface(surfaceTexture)
            val recorderSurface = mediaRecorder.surface

            val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.addTarget(recorderSurface)

            cameraDevice.createCaptureSession(
                listOf(surface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                            mediaRecorder.start() // Start media recorder

                            startButton.visibility = View.GONE
                            stopButton.visibility = View.VISIBLE
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder.stop() // Stop the recording
            mediaRecorder.reset()
            cameraCaptureSession.close()

            startPreview()

            startButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOutputMediaFile(): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Filtershot")
        if (!dir.exists()) {
            dir.mkdirs() // Create directory if it doesn't exist
        }
        return File(dir, "video_${System.currentTimeMillis()}.mp4").absolutePath
    }

    override fun onPause() {
        super.onPause()
        cameraDevice.close()
    }
}