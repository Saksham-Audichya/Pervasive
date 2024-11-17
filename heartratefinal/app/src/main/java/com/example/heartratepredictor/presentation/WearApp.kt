package com.example.heartratepredictor.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WearApp(
    backgroundColor: Color,
    currentHeartRate: Float,
    optimalRange: String,
    steps: Int,
    cadence: Float,
    speed: Float,
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(scrollState), // Make it scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp) // Reduced spacing between elements
        ) {
            if (!activitySelected) {
                // Heading for "Select Activity"
                Text(
                    text = "Select Activity",
                    style = MaterialTheme.typography.h4.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    modifier = Modifier.padding(top = 35.dp)
                )
                // Activity Selection Cards with icons
                ActivitySelectionCards(onActivitySelected)
            } else {
                // Display Current Heart Rate and Activity Details
                Text(
                    text = "Heart Rate: ${currentHeartRate.toInt()} BPM",
                    style = MaterialTheme.typography.h4.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp // Increased font size for visibility
                    ),
                    modifier = Modifier.padding(top = 40.dp)
                )

                // Display Optimal Range
                Text(
                    text = "Optimal Range: $optimalRange",
                    style = MaterialTheme.typography.body1.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(top = 10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Display Cadence with Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsRun, // Running icon
                        contentDescription = "Cadence Icon",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = "${cadence.toInt()} steps/min",
                        style = MaterialTheme.typography.body1.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display Speed with Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed, // Speed icon
                        contentDescription = "Speed Icon",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = "${"%.2f".format(speed)} km/h (Avg)",
                        style = MaterialTheme.typography.body1.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Exit Button with Icon
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(8.dp), // Adjusted padding
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue) // Blue color
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExitToApp,
                        contentDescription = "Exit Monitoring",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp)) // Space between icon and text
                    Text(
                        text = "Exit Monitoring",
                        style = MaterialTheme.typography.button.copy(color = Color.White),
                    )
                }
            }
        }
    }
}

@Composable
fun ActivitySelectionCards(onActivitySelected: (ActivityType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp), // Reduced spacing
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Walking Card
        ActivityCard(
            onClick = { onActivitySelected(ActivityType.WALKING) },
            title = "Walking",
            icon = Icons.Filled.DirectionsWalk,
            backgroundColor = Color(0xFF81C784) // Green color for Walking
        )

        // Running Card
        ActivityCard(
            onClick = { onActivitySelected(ActivityType.RUNNING) },
            title = "Running",
            icon = Icons.Filled.DirectionsRun,
            backgroundColor = Color(0xFF2196F3) // Blue color for Running
        )

        // Cycling Card
        ActivityCard(
            onClick = { onActivitySelected(ActivityType.CYCLING) },
            title = "Cycling",
            icon = Icons.Filled.DirectionsBike,
            backgroundColor = Color(0xFFFFEB3B) // Yellow color for Cycling
        )

        // Resting Card
        ActivityCard(
            onClick = { onActivitySelected(ActivityType.RESTING) },
            title = "Resting",
            icon = Icons.Filled.Snooze,
            backgroundColor = Color(0xFFFF7043) // Orange color for Resting
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ActivityCard(
    onClick: () -> Unit,
    title: String,
    icon: ImageVector,
    backgroundColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = backgroundColor,
        elevation = 5.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$title Icon",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.h6.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
