package com.booxbook.core.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.booxbook.core.tts.model.TtsSessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TtsService : Service() {

    @Inject
    lateinit var ttsEngineWrapper: TtsEngineWrapper

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                ttsEngineWrapper.pause()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
        fun getEngine(): TtsEngineWrapper = ttsEngineWrapper
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)

        observeState()
    }

    private fun observeState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            ttsEngineWrapper.state.collect { state ->
                if (state.isActive) {
                    val notification = buildNotification(state)
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                requestAudioFocus()
                ttsEngineWrapper.play()
            }
            ACTION_PAUSE -> {
                ttsEngineWrapper.pause()
                abandonAudioFocus()
            }
            ACTION_NEXT -> ttsEngineWrapper.next()
            ACTION_PREV -> ttsEngineWrapper.previous()
            ACTION_STOP -> {
                ttsEngineWrapper.stop()
                abandonAudioFocus()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> ttsEngineWrapper.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> ttsEngineWrapper.resume()
                    }
                }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        ttsEngineWrapper.pause()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BooxBook Đọc Giọng Nói (TTS)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Điều khiển đọc văn bản thành giọng nói trên nền"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: TtsSessionState): Notification {
        val playPauseAction = if (state.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Tạm dừng",
                createActionIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Tiếp tục",
                createActionIntent(ACTION_PLAY)
            )
        }

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Câu trước",
            createActionIntent(ACTION_PREV)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Câu sau",
            createActionIntent(ACTION_NEXT)
        )

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Dừng",
            createActionIntent(ACTION_STOP)
        )

        val title = state.bookTitle.ifBlank { "BooxBook Audio" }
        val chapter = if (state.chapterTitle.isNotBlank()) "${state.chapterTitle} • " else ""
        val subtitle = "$chapter Câu ${state.currentSentenceIndex + 1}/${state.totalSentences.coerceAtLeast(1)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Throwable) {}
        abandonAudioFocus()
    }

    companion object {
        const val CHANNEL_ID = "booxbook_tts_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.booxbook.action.TTS_PLAY"
        const val ACTION_PAUSE = "com.booxbook.action.TTS_PAUSE"
        const val ACTION_NEXT = "com.booxbook.action.TTS_NEXT"
        const val ACTION_PREV = "com.booxbook.action.TTS_PREV"
        const val ACTION_STOP = "com.booxbook.action.TTS_STOP"
    }
}
