package com.example.heartratepredictor.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepCounter: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var vibrator: Vibrator
    private lateinit var interpreter: Interpreter

    // Current values
    private var currentHeartRate: Float = 0f
    private var currentSpeed: Float = 0f
    private var currentStepsPerMinute: Float = 0f
    private var predictedHeartRate: Float = 0f

    // Step counting variables
    private var lastStepCount: Int = 0
    private var lastStepTimestamp = System.currentTimeMillis()

    // Accelerometer variables for speed calculation
    private val accelerometerReadings = mutableListOf<Triple<Float, Float, Float>>()
    private val windowSize = 50
    private var lastAccelerometerTimestamp = 0L

    private var currentActivity: ActivityType? = null
    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Historical data for model prediction
    private val historicalData = mutableListOf<Triple<Float, Float, Float>>()

    // Normalization parameters
    private var df_mean = floatArrayOf(0f, 0f, 0f) // Heart rate, speed, steps
    private var df_std = floatArrayOf(0f, 0f, 0f) // Heart rate, speed, steps

    // UI State management
    private var _uiState by mutableStateOf(SensorUiState())
    private val uiState: SensorUiState
        get() = _uiState

    private fun updateUiState(update: (SensorUiState) -> SensorUiState) {
        _uiState = update(_uiState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSensors()
        loadModel()
        requestPermissions()

        setContent {
            var selectedActivity by remember { mutableStateOf<ActivityType?>(null) }

            WearApp(
                onActivitySelected = {
                    selectedActivity = it
                    startMonitoring(it)
                },
                onExit = {
                    stopMonitoring()
                    selectedActivity = null
                },
                uiState = uiState,
                selectedActivity = selectedActivity
            )
        }
    }

    private fun setupSensors() {
        try {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            Log.d("Sensors", "Available sensors:")
            sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
                Log.d("Sensors", "${sensor.name} (${sensor.stringType})")
            }

            if (heartRateSensor == null) {
                updateUiState { it.copy(
                    error = "Heart rate sensor not available"
                ) }
                return
            }

            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            Log.e("Sensors", "Error setting up sensors", e)
            updateUiState { it.copy(
                error = "Error setting up sensors: ${e.message}"
            ) }
        }
    }

    private fun loadModel() {
        try {
            interpreter = Interpreter(FileUtil.loadMappedFile(this, "model_v1_20s.tflite"))
            Log.d("Model", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("Model", "Error loading model", e)
            updateUiState { it.copy(
                error = "Error loading ML model: ${e.message}"
            ) }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
            }
        }
    }
    private fun startMonitoring(activity: ActivityType) {
        updateUiState { it.copy(isLoading = true, error = null) }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            currentActivity = activity
            isMonitoring = true
            lastStepCount = 0
            lastStepTimestamp = System.currentTimeMillis()
            historicalData.clear()
            accelerometerReadings.clear()
            lastAccelerometerTimestamp = 0L

            // Register sensors
            sensorManager.registerListener(
                this,
                heartRateSensor,
                TimeUnit.SECONDS.toMicros(1).toInt()
            )

            sensorManager.registerListener(
                this,
                stepCounter,
                TimeUnit.SECONDS.toMicros(1).toInt()
            )

            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )

            scope.launch {
                while (isMonitoring) {
                    predictHeartRate()
                    delay(1000)
                }
            }

        } catch (e: Exception) {
            updateUiState { it.copy(
                error = "Error starting monitoring: ${e.message}"
            ) }
        } finally {
            updateUiState { it.copy(isLoading = false) }
        }
    }

    private fun calculateSpeedFromAccelerometer(): Float {
        if (accelerometerReadings.size < 2) return 0f

        try {
            val alpha = 0.8f
            var gravity = Triple(0f, 0f, 0f)
            var speed = 0f
            var lastTimestamp = lastAccelerometerTimestamp - (accelerometerReadings.size * 20L)

            accelerometerReadings.forEach { (x, y, z) ->
                gravity = Triple(
                    alpha * gravity.first + (1 - alpha) * x,
                    alpha * gravity.second + (1 - alpha) * y,
                    alpha * gravity.third + (1 - alpha) * z
                )

                val linearX = x - gravity.first
                val linearY = y - gravity.second
                val linearZ = z - gravity.third

                val acceleration = sqrt(
                    linearX * linearX + linearY * linearY + linearZ * linearZ
                )

                if (acceleration > 0.3f) {
                    speed += acceleration * 0.02f
                }

                lastTimestamp += 20L
            }

            speed = when (currentActivity) {
                ActivityType.WEIGHT_CONTROL -> speed * 0.8f
                ActivityType.AEROBIC_ENDURANCE -> speed * 1.2f
                ActivityType.AEROBIC_HARDCORE -> speed * 1.5f
                else -> speed
            }

            return speed.coerceIn(0f, 10f)

        } catch (e: Exception) {
            Log.e("Speed", "Error calculating speed", e)
            return 0f
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                currentHeartRate = event.values[0]
                Log.d("Sensor", "Heart Rate: $currentHeartRate")
                updateSensorValues()
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values[0].toInt()
                val currentTime = System.currentTimeMillis()
                val timeDiff = (currentTime - lastStepTimestamp) / 1000f

                if (lastStepCount > 0 && timeDiff > 0) {
                    val stepsDiff = steps - lastStepCount
                    currentStepsPerMinute = (stepsDiff / timeDiff) * 60f
                    Log.d("Sensor", "Steps/min: $currentStepsPerMinute")
                }

                lastStepCount = steps
                lastStepTimestamp = currentTime
                updateSensorValues()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val timestamp = System.currentTimeMillis()
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])

                accelerometerReadings.add(Triple(x, y, z))
                if (accelerometerReadings.size > windowSize) {
                    accelerometerReadings.removeAt(0)
                }

                if (lastAccelerometerTimestamp > 0) {
                    currentSpeed = calculateSpeedFromAccelerometer()
                    updateSensorValues()
                }
                lastAccelerometerTimestamp = timestamp
            }
        }
    }

    private fun updateNormalizationParams(historicalData: List<Triple<Float, Float, Float>>) {
        val heartRates = historicalData.map { it.first }
        val speeds = historicalData.map { it.second }
        val steps = historicalData.map { it.third }

        df_mean[0] = heartRates.average().toFloat()
        df_mean[1] = speeds.average().toFloat()
        df_mean[2] = steps.average().toFloat()

        df_std[0] = calculateStdDev(heartRates).takeIf { it != 0f } ?: 1f
        df_std[1] = calculateStdDev(speeds).takeIf { it != 0f } ?: 1f
        df_std[2] = calculateStdDev(steps).takeIf { it != 0f } ?: 1f

        Log.d("Normalization", "Mean: ${df_mean.joinToString()}, Std: ${df_std.joinToString()}")
    }

    private fun calculateStdDev(values: List<Float>): Float {
        val mean = values.average().toFloat()
        var sum = 0.0
        for (value in values) {
            sum += (value - mean) * (value - mean)
        }
        return sqrt(sum / values.size).toFloat()
    }

    private fun normalizeInputData(historicalData: List<Triple<Float, Float, Float>>): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(120 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        historicalData.forEach { (heartRate, speed, steps) ->
            val normalizedHeartRate = if (df_std[0] != 0f) (heartRate - df_mean[0]) / df_std[0] else 0f
            val normalizedSpeed = if (df_std[1] != 0f) (speed - df_mean[1]) / df_std[1] else 0f
            val normalizedSteps = if (df_std[2] != 0f) (steps - df_mean[2]) / df_std[2] else 0f

            inputBuffer.putFloat(normalizedHeartRate)
            inputBuffer.putFloat(normalizedSpeed)
            inputBuffer.putFloat(normalizedSteps)
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun denormalizePredictedHeartRate(predictedHeartRate: Float): Float {
        return (predictedHeartRate * df_std[0]) + df_mean[0]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun predictHeartRate() {
        try {
            historicalData.add(Triple(currentHeartRate, currentSpeed, currentStepsPerMinute))

            while (historicalData.size > 120) {
                historicalData.removeAt(0)
            }

            if (historicalData.size < 120) {
                Log.d("Prediction", "Collecting data: ${historicalData.size}/120 points")
                return
            }

            // Update normalization parameters and normalize input
            updateNormalizationParams(historicalData)
            val inputBuffer = normalizeInputData(historicalData)

            val outputBuffer = ByteBuffer.allocateDirect(4)
            outputBuffer.order(ByteOrder.nativeOrder())

            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val normalizedPredictedHeartRate = outputBuffer.float
            predictedHeartRate = denormalizePredictedHeartRate(normalizedPredictedHeartRate)

            Log.d("Prediction", """
                Latest Input Values:
                - Heart Rate: $currentHeartRate bpm
                - Speed: $currentSpeed m/s
                - Steps/min: $currentStepsPerMinute
                Historical Data Points: ${historicalData.size}
                Normalized Prediction: $normalizedPredictedHeartRate
                Denormalized Prediction (5s ahead): $predictedHeartRate bpm
            """.trimIndent())

            updateSensorValues()

            if (!isHeartRateInRange(predictedHeartRate, currentActivity!!)) {
                vibrate()
                showWarning("Warning: Heart rate predicted to go out of range! Slow down!")
            } else {
                hideWarning()
            }

        } catch (e: Exception) {
            Log.e("Prediction", "Error running model inference", e)
            showWarning("Error predicting heart rate")
        }
    }

    private fun updateSensorValues() {
        updateUiState { it.copy(
            heartRate = currentHeartRate,
            speed = currentSpeed,
            stepsPerMinute = currentStepsPerMinute,
            predictedHeartRate = predictedHeartRate
        ) }
    }

    private fun isHeartRateInRange(heartRate: Float, activity: ActivityType): Boolean {
        return when (activity) {
            ActivityType.WEIGHT_CONTROL -> heartRate in 80f..120f
            ActivityType.AEROBIC_ENDURANCE -> heartRate in 105f..140f
            ActivityType.AEROBIC_HARDCORE -> heartRate in 130f..180f
        }
    }

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }

    private fun showWarning(message: String) {
        updateUiState { it.copy(
            showWarning = true,
            warningMessage = message
        ) }
    }

    private fun hideWarning() {
        updateUiState { it.copy(
            showWarning = false,
            warningMessage = ""
        ) }
    }

    private fun stopMonitoring() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isMonitoring = false
        sensorManager.unregisterListener(this)
        scope.coroutineContext.cancelChildren()
        hideWarning()

        accelerometerReadings.clear()
        lastAccelerometerTimestamp = 0L
        historicalData.clear()
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        if (isMonitoring) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        interpreter.close()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
}