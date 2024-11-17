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
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.io.BufferedReader

class MainActivity : ComponentActivity() {
    private lateinit var vibrator: Vibrator
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var interpreter: Interpreter

    // Current values
    private var currentHeartRate: Float = 0f
    private var currentSpeed: Float = 0f
    private var currentStepsPerMinute: Float = 0f

    private var currentActivity: ActivityType? = null
    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Historical data for prediction
    private val historicalData = mutableListOf<Triple<Float, Float, Float>>()

    private val maxTimesteps = 120 // Model expects 120 timesteps

    // UI State management
    private var _uiState by mutableStateOf(SensorUiState())
    private val uiState: SensorUiState
        get() = _uiState

    private fun updateUiState(update: (SensorUiState) -> SensorUiState) {
        _uiState = update(_uiState)
    }

    private fun updateBuffer(buffer: MutableList<Float>, newValue: Float) {
        if (buffer.size >= maxTimesteps) {
            buffer.removeAt(0) // Remove the oldest value
        }
        buffer.add(newValue) // Add the new value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadModel()
        requestPermissions()
        setupVibrator()

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

    private fun setupVibrator() {
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun loadModel() {
        try {
            interpreter = Interpreter(FileUtil.loadMappedFile(this, "model_v1_rnn.tflite"))
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

    private fun loadPreRecordedData(fileName: String): List<Triple<Float, Float, Float>> {
        val data = mutableListOf<Triple<Float, Float, Float>>()
        try {
            // Open the file from assets
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            reader.readLine() // Skip the header if there's one

            reader.forEachLine { line ->
                val values = line.split(",")
                if (values.size == 3) {
                    val heartRate = values[0].toFloat()
                    val speed = values[1].toFloat()
                    val stepsPerMinute = values[2].toFloat()
                    data.add(Triple(heartRate, speed, stepsPerMinute))

                    //Log.d("PreRecordedData", "Loaded: $heartRate, $speed, $stepsPerMinute")
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("PreRecordedData", "Error reading pre-recorded data: ${e.message}")
        }
        return data
    }


    private fun startMonitoring(activity: ActivityType) {
        updateUiState { it.copy(isLoading = true, error = null) }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            currentActivity = activity
            isMonitoring = true
            historicalData.clear()  // Clear historical data when starting new monitoring

            scope.launch {
                val preRecordedData = loadPreRecordedData("filtered_data.csv")
                simulateDataPlayback(preRecordedData)
            }

        } catch (e: Exception) {
            updateUiState { it.copy(
                error = "Error starting monitoring: ${e.message}"
            ) }
        } finally {
            updateUiState { it.copy(isLoading = false) }
        }
    }

    private suspend fun simulateDataPlayback(data: List<Triple<Float, Float, Float>>) {
        data.forEach { (heartRate, speed, stepsPerMinute) ->
            // Update current values
            currentHeartRate = heartRate
            currentSpeed = speed
            currentStepsPerMinute = stepsPerMinute

            // Log the current values
            Log.d("DataPlayback", """
        Heart Rate: $currentHeartRate
        Speed: $currentSpeed
        Steps per Minute: $currentStepsPerMinute
        """.trimIndent())

            // Add current values to historical data
            historicalData.add(Triple(currentHeartRate, currentSpeed, currentStepsPerMinute))

            // Keep only last 120 data points
            while (historicalData.size > 120) {
                historicalData.removeAt(0)
            }

            // Update normalization parameters after adding new data
            updateNormalizationParams(historicalData)

            // Make prediction
            predictHeartRate()

            // Delay to simulate 1-second interval between readings
            delay(100)
        }
    }


    private fun stopMonitoring() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isMonitoring = false
        scope.coroutineContext.cancelChildren()
    }

    private var df_mean = floatArrayOf(0f, 0f, 0f) // Heart rate, speed, steps
    private var df_std = floatArrayOf(0f, 0f, 0f) // Heart rate, speed, steps

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

        //Log.d("Normalization", "Updated mean: ${df_mean.joinToString()}, std: ${df_std.joinToString()}")
    }


    private fun calculateStdDev(values: List<Float>): Float {
        val mean = values.average().toFloat()
        var sum = 0.0
        for (value in values) {
            sum += (value - mean) * (value - mean)
        }
        return Math.sqrt(sum / values.size).toFloat()
    }


    private fun normalizeInputData(historicalData: List<Triple<Float, Float, Float>>): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(120 * 3 * 4) // 120 timesteps * 3 features * 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())

        Log.d("Normalization", "Mean: ${df_mean.joinToString()}, Std: ${df_std.joinToString()}")

        historicalData.forEach { (heartRate, speed, steps) ->
            // Normalize heart rate, speed, and steps only if std is non-zero
            val normalizedHeartRate = if (df_std[0] != 0f) (heartRate - df_mean[0]) / df_std[0] else 0f
            val normalizedSpeed = if (df_std[1] != 0f) (speed - df_mean[1]) / df_std[1] else 0f
            val normalizedSteps = if (df_std[2] != 0f) (steps - df_mean[2]) / df_std[2] else 0f

            inputBuffer.putFloat(normalizedHeartRate)
            inputBuffer.putFloat(normalizedSpeed)
            inputBuffer.putFloat(normalizedSteps)

            // Debugging normalized values
            //Log.d("Normalization", "Normalized values: $normalizedHeartRate, $normalizedSpeed, $normalizedSteps")
        }

        inputBuffer.rewind()
        return inputBuffer
    }


    private fun denormalizePredictedHeartRate(predictedHeartRate: Float): Float {
        return (predictedHeartRate * df_std[0]) + df_mean[0]
    }


    private fun predictHeartRate() {
        try {
            // Only predict when we have enough data
            if (historicalData.size < 120) {
                Log.d("Prediction", "Collecting data: ${historicalData.size}/120 points")
                return
            }

            // Normalize the input data before passing it to the model
            val inputBuffer = normalizeInputData(historicalData)

            // Buffer for single predicted value
            val outputBuffer = ByteBuffer.allocateDirect(4) // 1 float value
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference
            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val normalizedPredictedHeartRate = outputBuffer.float

            Log.d("Prediction", "Normalized predicted heart rate: $normalizedPredictedHeartRate")

            // Denormalize the predicted heart rate
            val predictedHeartRate = denormalizePredictedHeartRate(normalizedPredictedHeartRate)

            Log.d("Prediction", """
            Latest Input Values:
            - Heart Rate: $currentHeartRate bpm
            - Speed: $currentSpeed m/s
            - Steps/min: $currentStepsPerMinute
            Historical Data Points: ${historicalData.size}
            Predicted Heart Rate (5s ahead): $predictedHeartRate bpm
        """.trimIndent())

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




    private fun isHeartRateInRange(heartRate: Float, activity: ActivityType): Boolean {
        return try {
            val (minRange, maxRange) = activity.range.split("-").map { it.toInt() }
            heartRate >= minRange && heartRate <= maxRange
        } catch (e: Exception) {
            Log.e("HeartRate", "Error parsing range for ${activity.displayName}: ${e.message}")
            false
        }
    }

    private fun vibrate() {
        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun showWarning(message: String) {
        updateUiState { it.copy(warningMessage = message) }
    }

    private fun hideWarning() {
        updateUiState { it.copy(warningMessage = "null") }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
    }
}
