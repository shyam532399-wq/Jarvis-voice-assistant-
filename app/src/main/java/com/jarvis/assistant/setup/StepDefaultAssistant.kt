package com.jarvis.assistant.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.TextPrimary
import com.jarvis.assistant.ui.theme.TextSecondary

@Composable
fun StepDefaultAssistant(
    isDefault: Boolean,
    onRequestDefault: () -> Unit,
    onFinish: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("One last thing", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make Jarvis your phone's default assistant so it opens from the home/side button, like Maya does.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        if (isDefault) {
            Text("Jarvis is set as your default assistant.", color = com.jarvis.assistant.ui.theme.SuccessGreen)
        } else {
            Text(
                "This opens Android's own settings screen -- pick Jarvis there and confirm.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onRequestDefault,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isDefault) "Change again" else "Set as default assistant")
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isDefault) "Done" else "Skip for now")
        }
    }
}
