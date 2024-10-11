package com.pup.filtershot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

private const val CHANNEL_ID = "filtershot_channel"
private const val NOTIFICATION_ID = 1
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
private const val PREFS_NAME = "FilterShotPrefs"
private const val SWITCH_STATE_KEY = "switchState"

class Live : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel if necessary
        createNotificationChannel()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_live, container, false)

        // Reference to the Switch and TextView
        val switch: Switch = view.findViewById(R.id.switch2)
        val resultTextView: TextView = view.findViewById(R.id.state)

        // Load saved switch state from SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val switchState = sharedPreferences.getBoolean(SWITCH_STATE_KEY, false) // Default is false (off)

        // Set the switch's checked state based on saved preferences
        switch.isChecked = switchState

        // Update the TextView based on the switch state
        if (switchState) {
            resultTextView.text = "FilterShot is Currently Running"
            resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Go))
        } else {
            resultTextView.text = "FilterShot is Paused"
            resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Error))
        }

        // Set a listener on the Switch to detect changes and save the state
        switch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean(SWITCH_STATE_KEY, isChecked)
            editor.apply() // Save the switch state

            // Update the TextView and notification
            if (isChecked) {
                resultTextView.text = "FilterShot is Currently Running"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Go))
                showNotification("FilterShot is Currently Running")
            } else {
                resultTextView.text = "FilterShot is Paused"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Error))
                showNotification("FilterShot is Paused")
            }
        }

        return view
    }

    // Show notification
    private fun showNotification(message: String) {
        Log.d("LiveFragment", "Preparing to show notification: $message")

        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_fiber_manual_record_24)  // Valid icon resource
            .setContentTitle("FilterShot Status")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d("LiveFragment", "Notification displayed: $message")
    }

    // Create a notification channel (required for Android 8.0 and above)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FilterShot Channel"
            val descriptionText = "Channel for FilterShot notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("LiveFragment", "Notification channel created: $CHANNEL_ID")
        }
    }

    // Handle permission result for notifications (Android 13+)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show()
                Log.d("LiveFragment", "Notification permission granted")
            } else {
                Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT).show()
                Log.d("LiveFragment", "Notification permission denied")
            }
        }
    }
}
