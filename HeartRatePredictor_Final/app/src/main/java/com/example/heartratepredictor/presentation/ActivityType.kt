package com.example.heartratepredictor.presentation

enum class ActivityType(val displayName: String, val description: String, val range: String) {
    WEIGHT_CONTROL("Weight Control", "Fitness / Fat burn", "80-120"),
    AEROBIC_ENDURANCE("Aerobic Endurance", "Cardio training / Endurance", "105-140"),
    AEROBIC_HARDCORE("Aerobic Hardcore", "Hardcore Training / Maximum Effort", "130-180")
}