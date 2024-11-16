package com.example.speedtracker

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat  // Add this import

class SpeedTrackingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var previousLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkLocationPermission()) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000, 5f, this
            )
        }
        return START_STICKY
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onLocationChanged(location: Location) {
        previousLocation?.let {
            val speed = SpeedTrackingUtils.calculateSpeed(it, location)
            showSpeed(speed)
        }
        previousLocation = location
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showSpeed(speed: Float) {
        Toast.makeText(this, "Current Speed: $speed m/s", Toast.LENGTH_SHORT).show()
    }
}
