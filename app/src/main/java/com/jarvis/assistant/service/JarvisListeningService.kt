package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Opt-in continuous listening service. Starts only after the user taps Start in the app.
 * Audio is processed locally until a voice utterance is detected, then sent to Gemini.
 */
class JarvisListeningService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.jarvis.assistant.action.START_LISTENING"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_LISTENING"
        const val ACTION_STATUS = "com.jarvis.assistant.action.STATUS"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_STATUS = "status"
        const val EXTRA_ANSWER = "answer"
        private const val CHANNEL_ID = "jarvis_silent_listening"
        private const val NOTIFICATION_ID = 4102
        private const val SAMPLE_RATE = 16000
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var running = false
    @Volatile private var apiKey: String = ""
    private var worker: Thread? = null
    @Volatile private var activeRecorder: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            ttsReady = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val key = intent.getStringExtra(EXTRA_API_KEY).orEmpty().trim()
                if (key.isBlank()) {
                    sendStatus("Gemini API key missing hai.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!running) {
                    apiKey = key
                    startForeground(NOTIFICATION_ID, buildNotification())
                    acquireWakeLock()
                    running = true
                    sendStatus("Jarvis background mein sun raha hai. Baat karo; rukne par jawab dega.")
                    worker = Thread { listenLoop() }.also { it.start() }
                }
            }
        }
        return START_STICKY
    }

    @Volatile private var liveSocket: okhttp3.WebSocket? = null
    @Volatile private var liveRecorder: AudioRecord? = null
    @Volatile private var liveEchoCanceler: AcousticEchoCanceler? = null
    @Volatile private var liveTrack: android.media.AudioTrack? = null
    @Volatile private var liveReady = false
    @Volatile private var liveFailed = false
    @Volatile private var liveMicThread: Thread? = null

    private fun listenLoop() {
        var retryDelayMs = 1000L
        sendStatus("Gemini Live se connection ho raha hai...")
        while (running && !Thread.currentThread().isInterrupted) {
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" +
                java.net.URLEncoder.encode(apiKey, "UTF-8"))
            .build()
        liveFailed = false
        liveSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                val setup = JSONObject()
                    .put("setup", JSONObject()
                        .put("model", "models/gemini-3.1-flash-live-preview")
                        .put("generationConfig", JSONObject()
                            .put("responseModalities", JSONArray().put("AUDIO")))
                        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text",
                                "You are Jarvis, a calm Android voice assistant. Speak naturally in Hindi or Hinglish, keep replies concise, and handle interruptions naturally. You may open YouTube, Chrome, WhatsApp, or the phone dialer only when the user clearly asks. Never place calls or send messages automatically. If a user asks to call someone or send a WhatsApp message, explain that you can open the dialer or WhatsApp, but the user must review and confirm the action themselves.")
                        )))
                        .put("inputAudioTranscription", JSONObject())
                        .put("outputAudioTranscription", JSONObject()))
                if (!webSocket.send(setup.toString())) {
                    sendStatus("Gemini Live setup send nahi hua.")
                }
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("setupComplete")) {
                        liveReady = true
                        sendStatus("Gemini Live connected. Hindi/Hinglish mein baat karo.")
                        startLiveMicrophone(webSocket)
                        return
                    }
                    val server = root.optJSONObject("serverContent")
                    if (server != null) {
                        val inputText = server.optJSONObject("inputTranscription")?.optString("text").orEmpty()
                        if (inputText.isNotBlank()) handleVoiceCommand(inputText)
                        val outputText = server.optJSONObject("outputTranscription")?.optString("text").orEmpty()
                        if (outputText.isNotBlank()) sendStatus("Jarvis: " + outputText.take(160), outputText)
                        val parts = server.optJSONObject("modelTurn")?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.optJSONObject(i) ?: continue
                                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                                val encoded = inline?.optString("data").orEmpty()
                                if (encoded.isNotBlank()) playLiveAudio(Base64.decode(encoded, Base64.DEFAULT))
                            }
                        }
                        if (server.optBoolean("turnComplete", false)) {
                            sendStatus("Gemini Live connected. Baat jaari rakho.")
                        }
                    }
                    if (root.has("error")) {
                        val error = root.optJSONObject("error")?.optString("message").orEmpty()
                        liveReady = false
                        liveFailed = true
                        sendStatus(ReliabilityErrors.forSocket(null, error))
                        webSocket.close(1000, "Gemini Live setup error")
                    }
                } catch (e: Exception) {
                    sendStatus("Live response parse nahi hua: " + (e.message ?: "unknown error").take(100))
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                liveReady = false
                liveFailed = true
                if (running) sendStatus(ReliabilityErrors.forSocket(response?.code, t.message ?: response?.message.orEmpty()))
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                liveReady = false
                liveFailed = true
                if (running && code != 1000) sendStatus(ReliabilityErrors.forSocket(code, reason))
            }
        })
        val connectStartedAt = System.currentTimeMillis()
        while (running && !Thread.currentThread().isInterrupted && !liveFailed &&
            (liveReady || System.currentTimeMillis() - connectStartedAt < 15000L)) {
            try { Thread.sleep(250) } catch (_: InterruptedException) { break }
        }
        if (running && !liveReady && !liveFailed) {
            liveFailed = true
            sendStatus("Gemini Live 15 second mein ready nahi hua. Network/API key check kar raha hoon; retry hoga.")
        }
        if (liveReady) retryDelayMs = 1000L
        liveReady = false
        try { liveSocket?.close(1000, "Jarvis stopped") } catch (_: Exception) {}
        liveSocket = null
        try { liveRecorder?.stop() } catch (_: Exception) {}
        try { liveRecorder?.release() } catch (_: Exception) {}
        liveRecorder = null
        try { liveEchoCanceler?.release() } catch (_: Exception) {}
        liveEchoCanceler = null
        try { liveTrack?.pause(); liveTrack?.flush(); liveTrack?.stop(); liveTrack?.release() } catch (_: Exception) {}
        liveTrack = null
        try { liveMicThread?.join(1200) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        liveMicThread = null
        if (running && !Thread.currentThread().isInterrupted) {
            sendStatus("Gemini Live reconnect ho raha hai...")
            try { Thread.sleep(retryDelayMs) } catch (_: InterruptedException) { break }
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(15000L)
        }
        }
    }

    private fun startLiveMicrophone(webSocket: okhttp3.WebSocket) {
        if (liveMicThread?.isAlive == true) return
        liveMicThread = Thread {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                sendStatus("Microphone buffer available nahi hai.")
                return@Thread
            }
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, 4096)
                )
                liveRecorder = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    sendStatus("Mic initialize nahi hua. Permission check karo.")
                    return@Thread
                }
                try {
                    liveEchoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)
                    liveEchoCanceler?.enabled = true
                } catch (_: Exception) {}
                recorder.startRecording()
                val shorts = ShortArray(1024)
                sendStatus("LIVE listening — bolte raho, Jarvis turant jawab dega.")
                while (running && liveReady && !Thread.currentThread().isInterrupted) {
                    val count = recorder.read(shorts, 0, shorts.size)
                    if (count <= 0) continue
                    val bytes = ByteArray(count * 2)
                    for (i in 0 until count) {
                        val value = shorts[i].toInt()
                        bytes[i * 2] = (value and 0xff).toByte()
                        bytes[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
                    }
                    val audio = JSONObject()
                        .put("realtimeInput", JSONObject().put("audio", JSONObject()
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            .put("mimeType", "audio/pcm;rate=16000")))
                    if (!webSocket.send(audio.toString())) {
                        sendStatus("Live audio send nahi hua; connection check karo.")
                        break
                    }
                }
            } catch (_: SecurityException) {
                sendStatus("Microphone permission allow karo.")
            } catch (e: Exception) {
                if (running) sendStatus("Live mic error: " + (e.message ?: "unknown").take(120))
            } finally {
                try { recorder?.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                if (liveRecorder === recorder) liveRecorder = null
                try { liveEchoCanceler?.release() } catch (_: Exception) {}
                liveEchoCanceler = null
                if (liveMicThread === Thread.currentThread()) liveMicThread = null
            }
        }.also { it.start() }
    }

    private fun playLiveAudio(pcm: ByteArray) {
        try {
            var track = liveTrack
            if (track == null) {
                val min = android.media.AudioTrack.getMinBufferSize(
                    24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(24000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(maxOf(min, 8192))
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build()
                liveTrack = track
                track.play()
            }
            track.write(pcm, 0, pcm.size)
        } catch (e: Exception) {
            sendStatus("Audio playback error: " + (e.message ?: "unknown").take(100))
        }
    }

    private fun handleVoiceCommand(spoken: String) {
        val text = spoken.lowercase(Locale.ROOT)
        val target = when {
            text.contains("open youtube") || text.contains("youtube kholo") || text.contains("यूट्यूब खोलो") -> "youtube"
            text.contains("open chrome") || text.contains("chrome kholo") || text.contains("क्रोम खोलो") -> "chrome"
            text.contains("open whatsapp") || text.contains("whatsapp kholo") || text.contains("व्हाट्सऐप खोलो") -> "whatsapp"
            text.contains("open dialer") || text.contains("dialer kholo") || text.contains("call screen kholo") || text.contains("dialer खोलो") -> "dialer"
            else -> return
        }
        sendBroadcast(Intent("com.jarvis.assistant.action.PHONE_COMMAND").setPackage(packageName)
            .putExtra("command", target))
        sendStatus("Command samjha: $target. App screen par action khulega.")
    }

    private fun askGemini(samples: ShortArray): String {
        return try {
            val wav = toWav(samples)
            val audioBase64 = Base64.encodeToString(wav, Base64.NO_WRAP)
            val parts = JSONArray()
                .put(JSONObject().put("text",
                    "You are Jarvis, a helpful Android voice assistant. The user speaks Hindi or Hinglish. Reply naturally in concise Hindi/Hinglish. Answer the user's request directly. If speech is unclear, politely ask them to repeat."))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "audio/wav")
                    .put("data", audioBase64)))
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = try {
                        JSONObject(responseText).getJSONObject("error").optString("message")
                    } catch (_: Exception) { "HTTP ${response.code}" }
                    sendStatus(ReliabilityErrors.forHttp(response.code, message))
                    return ""
                }
                JSONObject(responseText).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    .let { partsArray ->
                        (0 until partsArray.length()).mapNotNull { i ->
                            partsArray.optJSONObject(i)?.optString("text")
                        }.joinToString(" ").trim()
                    }
            }
        } catch (_: IOException) {
            sendStatus("Internet connection check karo.")
            ""
        } catch (_: Exception) {
            sendStatus("Audio process nahi ho saka. Dobara bolo.")
            ""
        }
    }

    private fun toWav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun writeInt(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }
        fun writeShort(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeInt(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeInt(16)
        writeShort(1)
        writeShort(1)
        writeInt(SAMPLE_RATE)
        writeInt(SAMPLE_RATE * 2)
        writeShort(2)
        writeShort(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeInt(dataSize)
        for (sample in samples) writeShort(sample.toInt())
        return out.toByteArray()
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val engine = tts ?: return
        val finished = CountDownLatch(1)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finished.countDown() }
            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String?) { finished.countDown() }
            override fun onError(utteranceId: String?, errorCode: Int) { finished.countDown() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        try { finished.await(25, TimeUnit.SECONDS) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun sendStatus(status: String, answer: String? = null) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).apply {
            putExtra(EXTRA_STATUS, status)
            if (answer != null) putExtra(EXTRA_ANSWER, answer)
        })
        if (running) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(status.take(80)))
        }
    }

    private fun buildNotification(line: String = "Listening silently in background"): Notification {
        val stopIntent = Intent(this, JarvisListeningService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 4103, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 4104, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Jarvis AI is active")
            .setContentText(line)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop listening", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis silent listening", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                description = "Silent ongoing notification while Jarvis listens"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisAI:ContinuousListening")
                .apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {
            sendStatus("Background wake lock nahi mila; battery saver listening rok sakta hai.")
        }
    }

    private fun stopListening() {
        running = false
        liveReady = false
        try { liveSocket?.close(1000, "Jarvis stopped") } catch (_: Exception) {}
        liveSocket = null
        try { liveRecorder?.stop() } catch (_: Exception) {}
        try { liveRecorder?.release() } catch (_: Exception) {}
        liveRecorder = null
        try { liveEchoCanceler?.release() } catch (_: Exception) {}
        liveEchoCanceler = null
        try { liveTrack?.pause(); liveTrack?.flush(); liveTrack?.stop(); liveTrack?.release() } catch (_: Exception) {}
        liveTrack = null
        try { worker?.interrupt() } catch (_: Exception) {}
        try { activeRecorder?.stop() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        worker = null
        try { tts?.stop() } catch (_: Exception) {}
        sendStatus("Jarvis listening band hai.")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopListening()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
