package com.example.heartratepredictor.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : ComponentActivity(), LocationListener, SensorEventListener {

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Main + Job()) // Change to Dispatchers.Main to ensure UI updates

    private var stepCount = mutableStateOf(0)
    private var cadence = mutableStateOf(0f)
    private var speed = mutableStateOf(0f)
    private var currentHeartRate = mutableStateOf(0f)

    private val strideLengthMap = mapOf(
        ActivityType.WALKING to 0.75f,
        ActivityType.RUNNING to 1.2f,
        ActivityType.CYCLING to 0f
    )

    private var currentActivity: ActivityType? = null
    private val heartRateData = mutableListOf<Float>() // List to hold recent heart rate values

    private var heartRateSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupServices()
        requestPermissions()

        setContent {
            var backgroundColor by remember { mutableStateOf(Color.Black) }
            var activitySelected by remember { mutableStateOf(false) }
            var optimalRange by remember { mutableStateOf("N/A") }

            WearApp(
                backgroundColor = backgroundColor,
                currentHeartRate = currentHeartRate.value,
                optimalRange = optimalRange,
                steps = stepCount.value,
                cadence = cadence.value,
                speed = speed.value,
                activitySelected = activitySelected,
                onActivitySelected = { activity ->
                    activitySelected = true
                    currentActivity = activity
                    optimalRange = when (activity) {
                        ActivityType.WALKING -> "80-120 BPM"
                        ActivityType.RUNNING -> "120-160 BPM"
                        ActivityType.CYCLING -> "120-150 BPM"
                        ActivityType.RESTING -> "40-85 BPM"
                    }
                    startMonitoring(activity) { heartRate, isHeartRateOutOfRange ->
                        currentHeartRate.value = heartRate
                        backgroundColor = if (isHeartRateOutOfRange) Color.Red else Color.Black

                        if (isHeartRateOutOfRange) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                },
                onExit = {
                    stopMonitoring()
                    backgroundColor = Color.Black
                    currentHeartRate.value = 0f
                    optimalRange = "N/A"
                    activitySelected = false
                    stepCount.value = 0
                    cadence.value = 0f
                    speed.value = 0f
                }
            )
        }
    }

    private fun setupServices() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startMonitoring(
        activity: ActivityType,
        onHeartRateStatusChange: (Float, Boolean) -> Unit
    ) {
        acquireWakeLock()
        isMonitoring = true

        // Register heart rate sensor listener
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        scope.launch {
            withContext(Dispatchers.Main) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L,
                            0f,
                            this@MainActivity
                        )
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                }
            }

            while (isMonitoring) {
                // Check if there are enough heart rate data points to analyze
                if (heartRateData.size >= 5) {
                    val predictedHeartRate = predictHeartRate(heartRateData.takeLast(5)) // Use last 5 values for prediction
                    val isOutOfRange = !isHeartRateInRange(predictedHeartRate, currentActivity!!)
                    onHeartRateStatusChange(predictedHeartRate, isOutOfRange)
                }
                delay(2000) // Update every 2 seconds
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Unregister heart rate sensor listener
        sensorManager.unregisterListener(this)

        releaseWakeLock()
        scope.coroutineContext.cancelChildren()
    }

    override fun onLocationChanged(location: Location) {
        val strideLength = strideLengthMap[currentActivity] ?: 0f
        if (strideLength > 0) {
            val distance = location.speed * strideLength
            stepCount.value = (distance / strideLength).toInt()  // Updating step count based on location speed and stride length
            cadence.value = location.speed * 60 / strideLength  // Updating cadence
            speed.value = location.speed * 3.6f  // Convert m/s to km/h for speed
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values.getOrNull(0) ?: return  // Handle case where heart rate data is unavailable
            addHeartRateData(heartRate)  // Add the heart rate value to the history
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::WakeLock")
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
    }

    // Function to add heart rate to the history list
    private fun addHeartRateData(heartRate: Float) {
        if (heartRateData.size >= 10) {
            heartRateData.removeAt(0) // Keep only the last 10 data points
        }
        heartRateData.add(heartRate)
    }

    private fun predictHeartRate(heartRateHistory: List<Float>): Float {
        val recentHeartRates = heartRateHistory.takeLast(5)
        if (recentHeartRates.isEmpty()) return 70f

        val currentRate = recentHeartRates.last()
        val trend = if (recentHeartRates.size > 1) {
            recentHeartRates.zipWithNext { a, b -> b - a }.average().toFloat()
        } else {
            0f
        }
        return currentRate + (trend * 5)
    }

    private fun isHeartRateInRange(heartRate: Float, activity: ActivityType): Boolean {
        return when (activity) {
            ActivityType.CYCLING -> heartRate in 120f..150f
            ActivityType.RUNNING -> heartRate in 120f..160f
            ActivityType.WALKING -> heartRate in 80f..120f
            ActivityType.RESTING -> heartRate in 40f..85f
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 1
    }
}
