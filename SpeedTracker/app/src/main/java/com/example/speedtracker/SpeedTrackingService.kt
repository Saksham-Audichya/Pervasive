package com.example.speedtracker

import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

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
        } else {
            Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
        }
        return START_STICKY
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // LocationListener method: called when a location update is received
    override fun onLocationChanged(location: Location) {
        previousLocation?.let {
            val speed = SpeedTrackingUtils.calculateSpeed(it, location)
            broadcastSpeedAndLocation(speed, location)
        }
        previousLocation = location
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun broadcastSpeedAndLocation(speed: Float, location: Location) {
        val intent = Intent("com.example.speedtracker.LOCATION_UPDATE")
        intent.putExtra("speed", speed)
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        sendBroadcast(intent)
    }

    // Stop location updates when the service is destroyed
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }
}
