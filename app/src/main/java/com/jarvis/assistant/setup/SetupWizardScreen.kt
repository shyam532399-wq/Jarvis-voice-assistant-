package com.jarvis.assistant.setup

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.data.PrefsManager
import com.jarvis.assistant.data.GeminiKeyTester
import com.jarvis.assistant.ui.theme.AccentBlue
import com.jarvis.assistant.ui.theme.NavyCardBorder

private const val KEY_PAGE_URL = "https://aistudio.google.com/apikey"

@Composable
fun SetupWizardScreen(prefs: PrefsManager, onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    var step by remember { mutableStateOf(1) }
    val permissions = remember { PermissionItems.list() }
    var statuses by remember {
        mutableStateOf(permissions.associate { it.id to PermStatus.PENDING })
    }

    fun refreshStatuses() {
        statuses = permissions.associate { perm ->
            val granted = perm.manifestPermission?.let {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } ?: false
            perm.id to if (granted) PermStatus.GRANTED else PermStatus.PENDING
        }
    }

    LaunchedEffect(Unit) { refreshStatuses() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatuses() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun isDefaultAssistant(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    Column(Modifier.fillMaxSize()) {
        WizardHeader(step = step)

        when (step) {
            1 -> StepApiKey(
                initialKey = prefs.geminiApiKey,
                onOpenKeyPage = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEY_PAGE_URL)))
                },
                onTestKey = { key, callback -> GeminiKeyTester.test(key, callback) },
                onContinue = { key ->
                    prefs.geminiApiKey = key
                    step = 2
                }
            )
            2 -> StepPermissions(
                items = permissions,
                statuses = statuses,
                micGranted = statuses["mic"] == PermStatus.GRANTED,
                onAllowAll = {
                    val toRequest = permissions.mapNotNull { it.manifestPermission }.toTypedArray()
                    permissionLauncher.launch(toRequest)
                },
                onContinue = { step = 3 }
            )
            3 -> StepDefaultAssistant(
                isDefault = isDefaultAssistant(),
                onRequestDefault = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                        } else {
                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                        }
                    } else {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }
                },
                onFinish = {
                    prefs.setupComplete = true
                    onSetupComplete()
                }
            )
        }
    }
}

@Composable
private fun WizardHeader(step: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Set up Jarvis", style = MaterialTheme.typography.titleLarge)
        Text("Step $step of 3", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(3) { i ->
                val filled = i < step
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .padding(end = if (i < 2) 6.dp else 0.dp)
                        .background(
                            if (filled) AccentBlue else NavyCardBorder,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
