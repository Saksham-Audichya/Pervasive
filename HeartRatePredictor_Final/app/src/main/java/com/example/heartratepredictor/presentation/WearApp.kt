package com.example.heartratepredictor.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items

@Composable
fun WearApp(
    onActivitySelected: (ActivityType) -> Unit,
    onExit: () -> Unit,
    uiState: SensorUiState,
    selectedActivity: ActivityType? = null
) {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedActivity == null) {
                        // Header
                        item {
                            Text(
                                text = "Select Activity",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Activity list
                        items(ActivityType.entries) { activity ->
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { onActivitySelected(activity) },
                                label = {
                                    Text(
                                        activity.displayName,
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Column {
                                        Text(
                                            activity.description,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "Range: ${activity.range} bpm",
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        // Monitoring screen header
                        item {
                            Text(
                                text = selectedActivity.displayName,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Heart Rate
                        item {
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { },
                                label = {
                                    Text(
                                        "Heart Rate",
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        "${uiState.heartRate.toInt()} bpm",
                                        color = Color.White
                                    )
                                }
                            )
                        }

                        // Predicted Heart Rate
                        item {
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { },
                                label = {
                                    Text(
                                        "Predicted HR",
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        "${uiState.predictedHeartRate.toInt()} bpm",
                                        color = if (uiState.showWarning) Color.Red else Color.White
                                    )
                                }
                            )
                        }

                        // Speed
                        item {
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { },
                                label = {
                                    Text(
                                        "Speed",
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        "%.1f m/s".format(uiState.speed),
                                        color = Color.White
                                    )
                                }
                            )
                        }

                        // Steps per Minute
                        item {
                            Chip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { },
                                label = {
                                    Text(
                                        "Steps/min",
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        "${uiState.stepsPerMinute.toInt()}",
                                        color = Color.White
                                    )
                                }
                            )
                        }

                        // Warning Banner if active
                        if (uiState.showWarning) {
                            item {
                                Card(
                                    onClick = { },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                                        startBackgroundColor = Color.Red.copy(alpha = 0.7f),
                                        endBackgroundColor = Color.Red.copy(alpha = 0.7f)
                                    )
                                ) {
                                    Text(
                                        text = uiState.warningMessage,
                                        modifier = Modifier.padding(8.dp),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Exit Button
                        item {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                onClick = onExit
                            ) {
                                Text(
                                    "Stop Monitoring",
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}