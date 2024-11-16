package com.example.heartratepredictor.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private val heartRateData = mutableListOf<Float>()
    private var currentActivity: ActivityType? = null
    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSensors()
        requestPermissions()

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setContent {
            var backgroundColor by remember { mutableStateOf(Color.Black) }  // Set black background for visibility
            var currentHeartRate by remember { mutableStateOf(70f) }
            var activitySelected by remember { mutableStateOf(false) }
            var optimalRange by remember { mutableStateOf("N/A") }

            WearApp(
                backgroundColor = backgroundColor,
                currentHeartRate = currentHeartRate,
                optimalRange = optimalRange,
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
                        currentHeartRate = heartRate
                        backgroundColor = if (isHeartRateOutOfRange) Color.Red else Color.Black
                        if (isHeartRateOutOfRange) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                },
                onExit = {
                    stopMonitoring()
                    backgroundColor = Color.Black
                    currentHeartRate = 70f
                    optimalRange = "N/A"
                    activitySelected = false
                }
            )
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startMonitoring(
        activity: ActivityType,
        onHeartRateStatusChange: (Float, Boolean) -> Unit
    ) {
        isMonitoring = true
        heartRateData.clear()

        sensorManager.registerListener(
            this,
            heartRateSensor,
            SensorManager.SENSOR_DELAY_FASTEST // Using fastest sampling rate
        )

        scope.launch {
            while (isMonitoring) {
                if (heartRateData.size >= 5) {
                    val predictedHeartRate = predictHeartRate(heartRateData.takeLast(5))
                    val isOutOfRange = !isHeartRateInRange(predictedHeartRate, currentActivity!!)
                    onHeartRateStatusChange(predictedHeartRate, isOutOfRange)
                }
                delay(5000)
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sensorManager.unregisterListener(this)
        scope.coroutineContext.cancelChildren()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values[0]

            // Filter out invalid (negative or zero) heart rate values
            if (heartRate >= 0) {
                heartRateData.add(heartRate)
            } else {
                Log.w("HeartRate", "Received invalid heart rate: $heartRate")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun predictHeartRate(heartRateHistory: List<Float>): Float {
        // If there are no values to predict from, return a reasonable default (e.g., 70)
        if (heartRateHistory.isEmpty()) return 70f

        // Ensure that all values are non-negative (no invalid sensor data)
        val filteredHeartRates = heartRateHistory.filter { it >= 0 }

        if (filteredHeartRates.isEmpty()) return 70f  // Default value if all data is invalid

        // Get the most recent heart rate and calculate the trend based on the previous values
        val currentRate = filteredHeartRates.last()

        val trend = if (filteredHeartRates.size > 1) {
            // Calculate the average rate of change between successive heart rates
            filteredHeartRates.zipWithNext { a, b -> b - a }.average().toFloat()
        } else {
            0f
        }

        // Predict the next heart rate based on current rate and trend
        val predictedHeartRate = currentRate + (trend * 5)

        // Ensure the predicted value is non-negative
        return if (predictedHeartRate < 0) 70f else predictedHeartRate
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
        private const val PERMISSION_REQUEST_CODE = 123
    }
}
