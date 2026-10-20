package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jarvis.assistant.data.PrefsManager
import com.jarvis.assistant.setup.SetupWizardScreen
import com.jarvis.assistant.ui.theme.JarvisAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsManager(applicationContext)

        setContent {
            JarvisAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var setupDone by remember { mutableStateOf(prefs.setupComplete && prefs.hasKey()) }

                    if (setupDone) {
                        HomeScreen()
                    } else {
                        SetupWizardScreen(prefs = prefs, onSetupComplete = { setupDone = true })
                    }
                }
            }
        }
    }
}
