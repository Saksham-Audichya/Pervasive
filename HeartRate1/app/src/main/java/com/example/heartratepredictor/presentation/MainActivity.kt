package com.example.heartratepredictor.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null

    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private val heartRateData = mutableListOf<Float>()
    private var stepCount = 0
    private var cadence = 0f
    private var currentActivity: ActivityType? = null
    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSensors()
        requestPermissions()
        setupPowerManager()

        setContent {
            var backgroundColor by remember { mutableStateOf(Color.Black) }
            var currentHeartRate by remember { mutableStateOf(0f) }
            var activitySelected by remember { mutableStateOf(false) }
            var optimalRange by remember { mutableStateOf("N/A") }
            var steps by remember { mutableStateOf(0) }
            var cadenceValue by remember { mutableStateOf(0f) }

            WearApp(
                backgroundColor = backgroundColor,
                currentHeartRate = currentHeartRate,
                optimalRange = optimalRange,
                steps = steps,
                cadence = cadenceValue,
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

                        // Trigger vibration if heart rate is out of range
                        if (isHeartRateOutOfRange) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                },
                onExit = {
                    stopMonitoring()
                    backgroundColor = Color.Black
                    currentHeartRate = 0f
                    optimalRange = "N/A"
                    activitySelected = false
                    steps = 0
                    cadenceValue = 0f
                }
            )
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupPowerManager() {
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private fun startMonitoring(
        activity: ActivityType,
        onHeartRateStatusChange: (Float, Boolean) -> Unit
    ) {
        acquireWakeLock()
        isMonitoring = true
        heartRateData.clear()

        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)

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
        releaseWakeLock()
        scope.coroutineContext.cancelChildren()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values[0]
                heartRateData.add(heartRate)
                Log.d("HeartRate", "Current: $heartRate")
            }
            Sensor.TYPE_STEP_COUNTER -> {
                stepCount = event.values[0].toInt()
                cadence = calculateCadence()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

    private fun calculateCadence(): Float {
        return if (stepCount > 0) stepCount / (System.currentTimeMillis() / 1000f) * 60
        else 0f
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HeartRateApp:WakeLock"
            )
        }
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
}
