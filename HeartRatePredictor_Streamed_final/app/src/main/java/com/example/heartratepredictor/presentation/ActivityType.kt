package com.example.heartratepredictor.presentation

// ActivityType.kt
enum class ActivityType(val displayName: String, val range: String) {
    CYCLING("Cycling", "120-150"),
    RUNNING("Running", "120-160"),
    WALKING("Walking", "80-120"),
    RESTING("Resting", "40-85")
}
