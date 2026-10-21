package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ui.theme.AccentBlue
import com.jarvis.assistant.ui.theme.AccentOrange
import com.jarvis.assistant.ui.theme.TextSecondary
import com.jarvis.assistant.voice.GeminiVoiceController

@Composable
fun HomeScreen(apiKey: String) {
    val context = LocalContext.current
    val controller = remember { GeminiVoiceController(context) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Jarvis is ready") }
    var answer by remember { mutableStateOf("") }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = controller.startRecording()
            listening = started
            status = if (started) "Listening... Baat bolo, phir Stop dabao." else
                "Microphone start nahi hua. App permission aur mic check karo."
        } else {
            status = "Microphone permission zaroori hai. Android Settings mein allow karo."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(160.dp).background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(if (listening) AccentOrange else AccentBlue, androidx.compose.ui.graphics.Color.Transparent)
                ), CircleShape
            )
        )
        Spacer(Modifier.height(24.dp))
        Text(if (listening) "Listening..." else "Jarvis AI", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(status, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (listening) {
                    listening = false
                    controller.stopAndAsk(apiKey) { newStatus, newAnswer ->
                        status = newStatus
                        if (newAnswer != null) answer = newAnswer
                    }
                } else {
                    answer = ""
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        val started = controller.startRecording()
                        listening = started
                        status = if (started) "Listening... Baat bolo, phir Stop dabao." else
                            "Microphone start nahi hua. App permission aur mic check karo."
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(52.dp).widthIn(min = 200.dp)
        ) {
            Text(if (listening) "Stop & Ask Jarvis" else "Start Listening")
        }
        if (answer.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(answer, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap Start, bolo, phir Stop & Ask dabao. Mic start/stop par app koi beep nahi bajati. Screen-off wake word abhi enabled nahi hai.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
