package com.example.heartratepredictor.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.heartratepredictor.presentation.ActivityType

@Composable
fun WearApp(
    backgroundColor: Color,
    currentHeartRate: Float,
    optimalRange: String,
    steps: Int,
    cadence: Float,
    activitySelected: Boolean,
    onActivitySelected: (ActivityType) -> Unit,
    onExit: () -> Unit
) {
    // Enable scrolling using ScrollState
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState), // Make it scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!activitySelected) {
                // Heading for "Select Activity"
                Text(
                    text = "Select Activity",
                    style = MaterialTheme.typography.h4.copy(color = Color.White),
                    modifier = Modifier.padding(top = 35.dp)
                )
                // Activity Selection Buttons
                ActivitySelectionButtons(onActivitySelected)
            } else {
                // Display Current Heart Rate and Details
                Text(
                    text = "Heart Rate: ${currentHeartRate.toInt()} BPM",
                    style = MaterialTheme.typography.h4.copy(color = Color.White),
                    modifier = Modifier.padding(top = 35.dp)
                )
                Text(
                    text = "Optimal Range: $optimalRange",
                    style = MaterialTheme.typography.body1.copy(color = Color.White),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Steps: $steps",
                    style = MaterialTheme.typography.body1.copy(color = Color.White),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Cadence: ${cadence.toInt()} steps/min",
                    style = MaterialTheme.typography.body1.copy(color = Color.White),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Exit Button
                Button(onClick = onExit) {
                    Text(text = "Exit Monitoring")
                }
            }
        }
    }
}

@Composable
fun ActivitySelectionButtons(onActivitySelected: (ActivityType) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { onActivitySelected(ActivityType.WALKING) }) {
            Text(text = "Walking")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onActivitySelected(ActivityType.RUNNING) }) {
            Text(text = "Running")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onActivitySelected(ActivityType.CYCLING) }) {
            Text(text = "Cycling")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onActivitySelected(ActivityType.RESTING) }) {
            Text(text = "Resting")
        }
    }
}
