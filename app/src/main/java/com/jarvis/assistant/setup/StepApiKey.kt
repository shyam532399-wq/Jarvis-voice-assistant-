package com.jarvis.assistant.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.data.KeyTestResult
import com.jarvis.assistant.ui.theme.*

@Composable
fun StepApiKey(
    initialKey: String,
    onOpenKeyPage: () -> Unit,
    onTestKey: (String, (KeyTestResult) -> Unit) -> Unit,
    onContinue: (String) -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<KeyTestResult?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add your Gemini key", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Jarvis's voice and brain run on Google's Gemini. Without a key it cannot hear you or answer. The key is free and takes about a minute.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))

        NumberedLine(1, "Tap \"Get a free key\" -- Google AI Studio opens. Sign in with any Google account.")
        NumberedLine(2, "Tap \"Create API key\", pick any project, and copy the key. It starts with \"AQ.\" (older keys start with \"Alza\").")
        NumberedLine(3, "Come back and paste it below -- the key itself, not your name or email.")

        Spacer(Modifier.height(20.dp))

        OutlinedButton(onClick = onOpenKeyPage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get a free key")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it; testResult = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("AQ....") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                testing = true
                testResult = null
                onTestKey(key) { result ->
                    testing = false
                    testResult = result
                }
            },
            enabled = key.isNotBlank() && !testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Testing...")
            } else {
                Text("Test key")
            }
        }

        testResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            when (result) {
                is KeyTestResult.Success -> Text("Key works.", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                is KeyTestResult.Failure -> Text(result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "The key is free. It is stored encrypted on your phone and used only to run Jarvis for you.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onContinue(key) },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save & continue")
        }
    }
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier.size(24.dp).background(AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
