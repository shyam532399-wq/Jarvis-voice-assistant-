package com.jarvis.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.AccentBlue
import com.jarvis.assistant.ui.theme.AccentOrange
import com.jarvis.assistant.ui.theme.TextSecondary

@Composable
fun HomeScreen() {
    var listening by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(160.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(if (listening) AccentOrange else AccentBlue, androidx.compose.ui.graphics.Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Spacer(Modifier.height(28.dp))
        Text(if (listening) "Listening..." else "Jarvis is idle", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Setup saved on this phone. Wake-word and command handling plug in here.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { listening = !listening },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(52.dp).widthIn(min = 200.dp)
        ) {
            Text(if (listening) "Stop listening" else "Start listening")
        }
    }
}
