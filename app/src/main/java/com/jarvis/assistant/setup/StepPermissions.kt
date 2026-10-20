package com.jarvis.assistant.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.*

@Composable
fun StepPermissions(
    items: List<JarvisPermission>,
    statuses: Map<String, PermStatus>,
    micGranted: Boolean,
    onAllowAll: () -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Let Jarvis use your phone", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "One tap -- Android asks for each in turn. Only the microphone is required; the rest make it useful.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(items) { perm ->
                PermissionRow(perm, statuses[perm.id] ?: PermStatus.PENDING)
                Spacer(Modifier.height(10.dp))
            }
        }

        Button(
            onClick = onAllowAll,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Allow all")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onContinue,
            enabled = micGranted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue")
        }
        if (!micGranted) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Continue unlocks once the microphone is allowed.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionRow(perm: JarvisPermission, status: PermStatus) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NavyCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, NavyCardBorder)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(perm.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(perm.reason, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            when (status) {
                PermStatus.GRANTED -> Icon(Icons.Filled.Check, contentDescription = "Granted", tint = SuccessGreen)
                PermStatus.PENDING -> StatusPill("Pending", PendingAmber)
                PermStatus.DENIED -> StatusPill("Denied", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
