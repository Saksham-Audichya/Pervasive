package com.example.speedtracker

import android.location.Location

object SpeedTrackingUtils {

    // Function to calculate speed between two locations (in meters per second)
    fun calculateSpeed(previousLocation: Location, currentLocation: Location): Float {
        val distance = previousLocation.distanceTo(currentLocation) // in meters
        val timeDelta = (currentLocation.time - previousLocation.time) / 1000f // in seconds
        return if (timeDelta > 0) distance / timeDelta else 0f
    }
}
