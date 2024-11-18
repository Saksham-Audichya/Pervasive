package com.example.heartratepredictor.presentation

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Predictor(private val context: Context) {

    // Load the model using the context to access the file system
    private val interpreter = Interpreter(FileUtil.loadMappedFile(context, "model_v1_rnn.tflite"))

    // Method to make a prediction
    fun predictHeartRate(
        recentHeartRateData: List<Float>,
        recentSpeedData: List<Float>,
        recentStepsData: List<Float>,
        maxTimesteps: Int
    ): String {
        if (recentHeartRateData.size < maxTimesteps ||
            recentSpeedData.size < maxTimesteps ||
            recentStepsData.size < maxTimesteps
        ) {
            Log.e("Prediction", "Not enough data for prediction")
            return "Not enough data for prediction"
        }

        // Create input buffer
        val inputBuffer = ByteBuffer.allocateDirect(maxTimesteps * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Fill input buffer with the data
        for (i in 0 until maxTimesteps) {
            inputBuffer.putFloat(recentHeartRateData[i])
            inputBuffer.putFloat(recentSpeedData[i])
            inputBuffer.putFloat(recentStepsData[i])
        }
        inputBuffer.rewind()

        // Create output buffer
        val outputBuffer = ByteBuffer.allocateDirect(4)
        outputBuffer.order(ByteOrder.nativeOrder())

        try {
            // Run model inference
            interpreter.run(inputBuffer, outputBuffer)

            // Extract the predicted heart rate
            outputBuffer.rewind()
            val predictedHeartRate = outputBuffer.float

            Log.d("Prediction", "Predicted Heart Rate: $predictedHeartRate")
            return "Predicted Heart Rate: $predictedHeartRate"
        } catch (e: Exception) {
            Log.e("Prediction", "Error during model inference", e)
            return "Prediction failed"
        }
    }
}
