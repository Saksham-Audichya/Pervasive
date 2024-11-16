package com.example.heartratepredictor.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WearApp(
    backgroundColor: Color,
    currentHeartRate: Float,
    optimalRange: String,
    activitySelected: Boolean,
    onActivitySelected: (ActivityType) -> Unit,
    onExit: () -> Unit
) {
    val scrollState = rememberScrollState()  // This is used for scrolling

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),  // Apply vertical scroll
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activitySelected) {
                // Centering "Current BPM" text vertically in the Box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(top = 25.dp)  // Adjust this padding as needed to center better
                ) {
                    Text(
                        text = "Current BPM: ${currentHeartRate.toInt()}",
                        style = MaterialTheme.typography.h4.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .padding(vertical = 16.dp)  // Adjust this for more space between the text and other elements
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Optimal Range: $optimalRange",
                    style = MaterialTheme.typography.body1.copy(color = Color.White),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(onClick = onExit) {
                    Text(text = "Exit Monitoring")
                }
            } else {
                Text(
                    text = "Select Activity",
                    style = MaterialTheme.typography.h4.copy(color = Color.White),
                    modifier = Modifier
                        .padding(top = 40.dp)  // Adjust heading position slightly down
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { onActivitySelected(ActivityType.WALKING) }) {
                    Text(text = "Walking")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onActivitySelected(ActivityType.RUNNING) }) {
                    Text(text = "Running")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onActivitySelected(ActivityType.CYCLING) }) {
                    Text(text = "Cycling")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onActivitySelected(ActivityType.RESTING) }) {
                    Text(text = "Resting")
                }
            }
        }
    }
}
