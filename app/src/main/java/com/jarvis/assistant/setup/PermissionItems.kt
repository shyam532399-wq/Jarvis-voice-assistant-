package com.jarvis.assistant.setup

import android.Manifest
import android.os.Build

enum class PermStatus { PENDING, GRANTED, DENIED }

data class JarvisPermission(
    val id: String,
    val title: String,
    val reason: String,
    val manifestPermission: String?,
    val required: Boolean = false
)

object PermissionItems {

    fun list(): List<JarvisPermission> = buildList {
        add(JarvisPermission(
            id = "mic",
            title = "Microphone",
            reason = "So you can talk to Jarvis. Required.",
            manifestPermission = Manifest.permission.RECORD_AUDIO,
            required = true
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(JarvisPermission(
                id = "notifications",
                title = "Notifications",
                reason = "Jarvis's own notification, which keeps it running in the background.",
                manifestPermission = Manifest.permission.POST_NOTIFICATIONS
            ))
        }
        add(JarvisPermission(
            id = "camera",
            title = "Camera",
            reason = "So it can take a photo or look at what you point the phone at.",
            manifestPermission = Manifest.permission.CAMERA
        ))
        add(JarvisPermission(
            id = "phone",
            title = "Phone calls",
            reason = "So it can place or answer a call for you.",
            manifestPermission = Manifest.permission.CALL_PHONE
        ))
        add(JarvisPermission(
            id = "contacts",
            title = "Contacts",
            reason = "So a name is enough -- it looks up the number.",
            manifestPermission = Manifest.permission.READ_CONTACTS
        ))
        add(JarvisPermission(
            id = "sms",
            title = "SMS",
            reason = "So it can send a text message.",
            manifestPermission = Manifest.permission.SEND_SMS
        ))
        add(JarvisPermission(
            id = "location",
            title = "Location",
            reason = "Weather, navigation, and where you are.",
            manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }
}
