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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepDetector: Sensor? = null
    private lateinit var vibrator: Vibrator
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var interpreter: Interpreter

    // Current values
    private var currentHeartRate: Float = 0f
    private var currentSpeed: Float = 0f
    private var currentStepsPerMinute: Float = 0f

    // Step counting variables
    private var stepCount = 0
    private var lastStepTimestamp = 0L
    private val stepTimeWindow = 60000L // 1 minute in milliseconds
    private val steps = mutableListOf<Long>() // List to store step timestamps

    private var currentActivity: ActivityType? = null
    private var isMonitoring = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val recentHeartRateData: MutableList<Float> = mutableListOf()
    private val recentSpeedData: MutableList<Float> = mutableListOf()
    private val recentStepsData: MutableList<Float> = mutableListOf()

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
            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            if (heartRateSensor == null) {
                updateUiState { it.copy(
                    error = "Heart rate sensor not available on this device"
                ) }
                return
            }

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            updateUiState { it.copy(
                error = "Error setting up sensors: ${e.message}"
            ) }
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
    private fun startMonitoring(activity: ActivityType) {
        updateUiState { it.copy(isLoading = true, error = null) }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            currentActivity = activity
            isMonitoring = true
            stepCount = 0
            lastStepTimestamp = System.currentTimeMillis()
            steps.clear()

            // Register sensors with 1-second sampling rate
            sensorManager.registerListener(
                this,
                heartRateSensor,
                TimeUnit.SECONDS.toMicros(1).toInt()
            )

            sensorManager.registerListener(
                this,
                stepDetector,
                TimeUnit.SECONDS.toMicros(1).toInt()
            )

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                updateUiState { it.copy(
                    error = "Location permission required for speed tracking"
                ) }
                return
            }

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

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            val locationRequest = LocationRequest.Builder(1000L)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(1000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentSpeed = if (location.hasSpeed()) location.speed else 0f
                updateBuffer(recentSpeedData, currentSpeed)
                updateSensorValues()
            }
        }
    }



    private fun updateStepsPerMinute() {
        val currentTime = System.currentTimeMillis()
        steps.removeAll { it < currentTime - stepTimeWindow }
        currentStepsPerMinute = (steps.size * 60f)
        updateSensorValues()
    }

    private fun stopMonitoring() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isMonitoring = false
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.coroutineContext.cancelChildren()
        hideWarning()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                currentHeartRate = event.values[0]
                updateBuffer(recentHeartRateData, currentHeartRate)
                updateSensorValues()
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                steps.add(System.currentTimeMillis())
                updateStepsPerMinute()
                updateBuffer(recentStepsData, currentStepsPerMinute)
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSensorValues() {
        updateUiState { it.copy(
            heartRate = currentHeartRate,
            speed = currentSpeed,
            stepsPerMinute = currentStepsPerMinute
        ) }
    }

    private fun predictHeartRate() {
        try {
            // Check if the buffers have sufficient data
            if (recentHeartRateData.size < maxTimesteps ||
                recentSpeedData.size < maxTimesteps ||
                recentStepsData.size < maxTimesteps) {
                Log.e("Prediction", """
                Not enough data for prediction.
                Buffer sizes:
                - HeartRateData: ${recentHeartRateData.size}
                - SpeedData: ${recentSpeedData.size}
                - StepsData: ${recentStepsData.size}
                Expected: $maxTimesteps
            """.trimIndent())
                return
            }

            // Prepare the input buffer for the ML model
            val inputBuffer = ByteBuffer.allocateDirect(maxTimesteps * 3 * 4) // 120 timesteps * 3 features * 4 bytes
            inputBuffer.order(ByteOrder.nativeOrder())

            // Populate the input buffer with data
            for (i in 0 until maxTimesteps) {
                inputBuffer.putFloat(recentHeartRateData[i])
                inputBuffer.putFloat(recentSpeedData[i])
                inputBuffer.putFloat(recentStepsData[i])
            }
            inputBuffer.rewind()

            // Allocate the output buffer for the predicted heart rate
            val outputBuffer = ByteBuffer.allocateDirect(4) // 1 float value
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference on the model
            interpreter.run(inputBuffer, outputBuffer)

            // Extract the predicted heart rate from the output buffer
            outputBuffer.rewind()
            val predictedHeartRate = outputBuffer.float

            Log.d("Prediction", """
            Predicted Heart Rate: $predictedHeartRate
            Input Data (Last Timestep):
            - Heart Rate: ${recentHeartRateData.last()} bpm
            - Speed: ${recentSpeedData.last()} m/s
            - Steps/min: ${recentStepsData.last()}
        """.trimIndent())

            // Additional logic for handling the prediction result
            if (!isHeartRateInRange(predictedHeartRate, currentActivity!!)) {
                vibrate()
                showWarning("Warning: Predicted heart rate out of range! Slow down!")
            } else {
                hideWarning()
            }

        } catch (e: Exception) {
            Log.e("Prediction", "Error running model inference", e)
            showWarning("Error predicting heart rate. Please try again.")
        }
    }




    private fun isHeartRateInRange(heartRate: Float, activity: ActivityType): Boolean {
        return when (activity) {
            ActivityType.CYCLING -> heartRate in 120f..150f
            ActivityType.RUNNING -> heartRate in 120f..160f
            ActivityType.WALKING -> heartRate in 80f..120f
            ActivityType.RESTING -> heartRate in 40f..85f
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