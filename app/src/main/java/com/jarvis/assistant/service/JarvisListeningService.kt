package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
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

    private fun listenLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            sendStatus("Is device par microphone recording start nahi ho saki.")
            stopListening()
            stopSelf()
            return
        }

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 4096)
            )
            activeRecorder = audioRecord
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                sendStatus("Microphone initialize nahi hua. Permission check karo.")
                stopListening()
                stopSelf()
                return
            }
            audioRecord.startRecording()
            val buffer = ShortArray(1024)
            val utterance = ArrayList<Short>(SAMPLE_RATE * 8)
            var voiceStarted = false
            var silenceSamples = 0
            var speechSamples = 0
            val silenceLimit = (SAMPLE_RATE * 1.2).toInt()
            val maxSpeechSamples = SAMPLE_RATE * 12
            val threshold = 650.0

            while (running && !Thread.currentThread().isInterrupted) {
                val count = audioRecord.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                var sum = 0.0
                for (i in 0 until count) sum += buffer[i].toDouble() * buffer[i].toDouble()
                val rms = sqrt(sum / count)
                if (!voiceStarted && rms >= threshold) {
                    voiceStarted = true
                    utterance.clear()
                    speechSamples = 0
                    silenceSamples = 0
                    sendStatus("Jarvis sun raha hai...")
                }
                if (voiceStarted) {
                    for (i in 0 until count) utterance.add(buffer[i])
                    speechSamples += count
                    if (rms < threshold * 0.55) silenceSamples += count else silenceSamples = 0
                    if (silenceSamples >= silenceLimit || speechSamples >= maxSpeechSamples) {
                        val captured = utterance.toShortArray()
                        voiceStarted = false
                        utterance.clear()
                        silenceSamples = 0
                        speechSamples = 0
                        if (captured.size >= SAMPLE_RATE / 2) {
                            val answer = askGemini(captured)
                            if (answer.isNotBlank() && running) {
                                sendStatus("Jarvis: $answer", answer)
                                try { audioRecord.stop() } catch (_: Exception) {}
                                speak(answer)
                                if (running) {
                                    try { audioRecord.startRecording() } catch (_: Exception) {
                                        sendStatus("Mic dobara start nahi hua. Jarvis stop karke phir start karo.")
                                        running = false
                                    }
                                }
                            } else if (running) {
                                sendStatus("Jawab nahi mila. Phir se bolo.")
                            }
                        }
                        if (running) sendStatus("Jarvis background mein sun raha hai. Baat karo.")
                    }
                }
            }
        } catch (_: SecurityException) {
            sendStatus("Microphone permission allow karo, phir Jarvis start karo.")
        } catch (_: Exception) {
            if (running) sendStatus("Background listening ruk gayi. App dobara kholkar Start karo.")
        } finally {
            try { audioRecord?.stop() } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            activeRecorder = null
        }
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
                    sendStatus("Gemini error ${response.code}: ${message.take(180)}")
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status.take(80)))
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
