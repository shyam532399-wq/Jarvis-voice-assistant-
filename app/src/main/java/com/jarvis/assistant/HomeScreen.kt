package com.jarvis.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.service.JarvisListeningService
import com.jarvis.assistant.ui.theme.AccentBlue
import com.jarvis.assistant.ui.theme.AccentOrange
import com.jarvis.assistant.ui.theme.TextSecondary

@Composable
fun HomeScreen(apiKey: String) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Jarvis ready hai. Start dabao aur seedha baat karo.") }
    var answer by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.jarvis.assistant.action.PHONE_COMMAND") {
                    when (intent.getStringExtra("command")) {
                        "youtube" -> launchPhoneApp(context ?: return, "com.google.android.youtube")
                        "chrome" -> launchPhoneApp(context ?: return, "com.android.chrome")
                        "whatsapp" -> launchPhoneApp(context ?: return, "com.whatsapp")
                        "dialer" -> {
                            try {
                                (context ?: return).startActivity(
                                    Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                status = "Phone dialer nahi khula."
                            }
                        }
                    }
                    return
                }
                if (intent?.action != JarvisListeningService.ACTION_STATUS) return
                status = intent.getStringExtra(JarvisListeningService.EXTRA_STATUS) ?: status
                val newAnswer = intent.getStringExtra(JarvisListeningService.EXTRA_ANSWER)
                if (!newAnswer.isNullOrBlank()) answer = newAnswer
                listening = !status.contains("band hai", ignoreCase = true) &&
                    !status.contains("permission allow", ignoreCase = true) &&
                    !status.contains("initialize nahi", ignoreCase = true)
            }
        }
        val filter = IntentFilter().apply {
            addAction(JarvisListeningService.ACTION_STATUS)
            addAction("com.jarvis.assistant.action.PHONE_COMMAND")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startJarvisService(context, apiKey) {
            listening = true
            status = "Jarvis background mein sun raha hai. Baat karo; rukne par jawab dega."
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
            Modifier.size(170.dp).background(
                Brush.radialGradient(
                    listOf(
                        if (listening) AccentOrange else AccentBlue,
                        AccentBlue.copy(alpha = 0.18f),
                        Color.Transparent
                    )
                ), CircleShape
            )
        )
        Spacer(Modifier.height(24.dp))
        Text("JARVIS AI", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (listening) "CONTINUOUS LISTENING" else "READY",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(status, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (listening) {
                    context.startService(
                        Intent(context, JarvisListeningService::class.java)
                            .setAction(JarvisListeningService.ACTION_STOP)
                    )
                    listening = false
                    status = "Jarvis listening band kar raha hai..."
                } else if (apiKey.isBlank()) {
                    status = "Gemini API key missing hai. Setup dobara check karo."
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    startJarvisService(context, apiKey) {
                        listening = true
                        status = "Jarvis background mein sun raha hai. Baat karo; rukne par jawab dega."
                    }
                } else {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text(if (listening) "Stop Jarvis" else "Start Jarvis")
        }


        Spacer(Modifier.height(24.dp))
        Text("PHONE SHORTCUTS", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { launchPhoneApp(context, "com.google.android.youtube") }) { Text("YouTube") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { launchPhoneApp(context, "com.android.chrome") }) { Text("Chrome") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { launchPhoneApp(context, "com.whatsapp") }) { Text("WhatsApp") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                try { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { status = "Phone dialer nahi khula." }
            }) { Text("Call / Dialer") }
        }

        Spacer(Modifier.height(12.dp))
        if (answer.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("JARVIS RESPONSE", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Start dabane ke baad Gemini Live se real-time two-way audio chalta hai. Hindi/Hinglish mein baat karo. Mic start/stop aur notification silent hain. Voice se YouTube, Chrome, WhatsApp ya dialer khul sakta hai.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Note: Wake-word 'Jarvis' abhi alag se enabled nahi hai; continuous mode Start button se chalu hota hai. Gemini ke liye internet zaroori hai.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun startJarvisService(context: Context, apiKey: String, onStarted: () -> Unit) {
    val intent = Intent(context, JarvisListeningService::class.java)
        .setAction(JarvisListeningService.ACTION_START)
        .putExtra(JarvisListeningService.EXTRA_API_KEY, apiKey)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
        onStarted()
    } catch (_: Exception) {
        // Avoid claiming the service started if Android rejected the request.
    }
}

private fun launchPhoneApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            android.widget.Toast.makeText(context, "Yeh app installed nahi hai.", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "App open nahi ho saki.", android.widget.Toast.LENGTH_SHORT).show()
    }
}
