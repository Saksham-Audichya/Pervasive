data class SensorUiState(
    val heartRate: Float = 0f,
    val speed: Float = 0f,
    val stepsPerMinute: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showWarning: Boolean = false,
    val warningMessage: String = "",
    val predictedHeartRate: Float = 0f,  // Add predicted heart rate
    val historicalDataCount: Int = 0     // Add historical data count
) {
    var showPredictedHeartRate: Boolean = false
}
