package com.example.speedtracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startSpeedTrackingService()
            } else {
                Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var speedTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var startTrackingButton: Button
    private lateinit var stopTrackingButton: Button

    private val speedLocationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val speed = intent?.getFloatExtra("speed", 0f)
            val lat = intent?.getDoubleExtra("latitude", 0.0)
            val lon = intent?.getDoubleExtra("longitude", 0.0)

            // Display speed and location on the UI
            speedTextView.text = "Speed: ${speed?.toString() ?: "N/A"} m/s"
            locationTextView.text = "Lat: $lat, Lon: $lon"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speedTextView = findViewById(R.id.speed_text_view)
        locationTextView = findViewById(R.id.location_text_view)
        startTrackingButton = findViewById(R.id.start_tracking_button)
        stopTrackingButton = findViewById(R.id.stop_tracking_button)

        startTrackingButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startSpeedTrackingService()
            } else {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        stopTrackingButton.setOnClickListener {
            stopSpeedTrackingService()
        }
    }

    // Register the receiver in onStart and unregister it in onStop
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.speedtracker.LOCATION_UPDATE")
        registerReceiver(speedLocationReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(speedLocationReceiver)
    }

    private fun startSpeedTrackingService() {
        val serviceIntent = Intent(this, SpeedTrackingService::class.java)
        startService(serviceIntent)
        startTrackingButton.isEnabled = false
        stopTrackingButton.isEnabled = true
        Toast.makeText(this, "Speed Tracking Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSpeedTrackingService() {
        val serviceIntent = Intent(this, SpeedTrackingService::class.java)
        stopService(serviceIntent)
        startTrackingButton.isEnabled = true
        stopTrackingButton.isEnabled = false
        Toast.makeText(this, "Speed Tracking Stopped", Toast.LENGTH_SHORT).show()
    }
}
