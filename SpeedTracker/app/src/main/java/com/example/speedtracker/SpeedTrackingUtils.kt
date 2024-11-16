package com.example.speedtracker

import android.location.Location

object SpeedTrackingUtils {

    fun calculateSpeed(previousLocation: Location, currentLocation: Location): Float {
        val distance = previousLocation.distanceTo(currentLocation) // meters
        val timeDelta = (currentLocation.time - previousLocation.time) / 1000.0f // seconds
        return if (timeDelta > 0) {
            distance / timeDelta // speed in meters per second (m/s)
        } else {
            0f
        }
    }
}
