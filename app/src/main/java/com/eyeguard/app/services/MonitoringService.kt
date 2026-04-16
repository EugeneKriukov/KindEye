package com.eyeguard.app.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.eyeguard.app.R
import com.eyeguard.app.overlay.OverlayManager
import com.eyeguard.app.ui.EyeExerciseActivity
import com.eyeguard.app.ui.MainActivity
import com.eyeguard.app.utils.AppPreferences
import com.eyeguard.app.utils.FaceDistanceAnalyzer
import com.eyeguard.app.utils.TtsManager
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MonitoringService : Service(), LifecycleOwner {

    companion object {
        private const val TAG              = "MonitoringService"
        private const val NOTIF_CHANNEL_ID = "eyeguard_channel"
        private const val NOTIF_ID         = 1001

        const val ACTION_STOP          = "com.eyeguard.app.STOP"
        const val ACTION_CALIBRATE     = "com.eyeguard.app.CALIBRATE"
        const val ACTION_EXERCISE_DONE = "com.eyeguard.app.EXERCISE_DONE"
        // Sent by AlarmManager on swipe-kill restart — triggers session restore, not fresh start
        const val ACTION_RESTART       = "com.eyeguard.app.RESTART"

        const val BROADCAST_CALIBRATION_RESULT = "com.eyeguard.app.CALIBRATION_RESULT"
        const val EXTRA_CALIBRATION_SUCCESS    = "calibration_success"

        private const val WARNING_5MIN_MS  = 5 * 60 * 1000L
        private const val WARNING_1MIN_MS  = 1 * 60 * 1000L
        private const val RESTART_DELAY_MS = 500L
    }

    // ── Lifecycle for CameraX ─────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // ── Core dependencies ─────────────────────────────────────────
    private lateinit var prefs: AppPreferences
    private lateinit var overlayManager: OverlayManager
    private lateinit var ttsManager: TtsManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceAnalyzer: FaceDistanceAnalyzer
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Distance monitoring state ─────────────────────────────────
    private var isTooClose = false

    // Runnable for the delayed thank-you message (3s after child moves back).
    // Cancelled immediately if the child gets too close again within that window.
    private var pendingThankYouRunnable: Runnable? = null
    private val THANK_YOU_DELAY_MS = 2_000L

    // ── Break state ───────────────────────────────────────────────
    // In-memory values; prefs are the durable source of truth across kills.
    private var isBreakActive    = false
    private var isBreakTtsPlayed = false
    private var breakEndTimeMs   = 0L
    private var breakStartedMs   = 0L

    // ── Session timing ────────────────────────────────────────────
    private var sessionStartMs         = 0L
    private var sessionElapsedBeforeMs = 0L
    private var warning5MinSent        = false
    private var warning1MinSent        = false

    // ── Calibration ───────────────────────────────────────────────
    private var waitingForCalibration = false

    // ── WakeLock ─────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Audio focus — pauses background media apps during break ──
    private var audioFocusRequest: AudioFocusRequest? = null   // API 26+

    // ── Periodic break check (every 30 s) ────────────────────────
    private val breakCheckRunnable = object : Runnable {
        override fun run() {
            checkBreakTime()
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON  -> onScreenOn()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Locale — service must resolve strings in the user's language
    // ─────────────────────────────────────────────────────────────
    override fun attachBaseContext(base: Context) {
        val tag = try {
            androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(base)
                .getString(AppPreferences.KEY_LANGUAGE, AppPreferences.DEFAULT_LANGUAGE)
                ?: AppPreferences.DEFAULT_LANGUAGE
        } catch (_: Exception) { AppPreferences.DEFAULT_LANGUAGE }
        val locale = parseLocale(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    // ─────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs          = AppPreferences(this)
        overlayManager = OverlayManager(this)
        ttsManager     = TtsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        ttsManager.init(parseLocale(prefs.language))
        setupFaceAnalyzer()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX 3: If the service was already cleaned up (lifecycle DESTROYED) but the process
        // wasn't killed yet, refuse to restart in this instance. The AlarmManager will fire
        // again after the process dies and a fresh instance is created.
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            Log.d(TAG, "onStartCommand on destroyed instance — ignoring")
            return START_NOT_STICKY
        }

        // Named actions — handled and returned immediately
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_CALIBRATE -> {
                waitingForCalibration = true
                return START_STICKY
            }
            ACTION_EXERCISE_DONE -> {
                onExerciseDone()
                return START_STICKY
            }
            ACTION_RESTART -> {
                // AlarmManager restart after swipe-kill — restore session, don't reset
                handleServiceRestart()
                return START_STICKY
            }
        }

        // intent == null → Android restarted the service via START_STICKY after kill
        // intent != null → explicit start from UI, BootReceiver, or AlarmManager
        if (intent == null) {
            handleServiceRestart()
        } else {
            // Guard against duplicate starts from MainActivity.onResume while alive
            if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
                Log.d(TAG, "Already running — ignoring duplicate start")
                return START_STICKY
            }
            handleFreshStart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    /** Schedule restart via AlarmManager after swipe-kill on MIUI */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!prefs.serviceEnabled) return
        Log.d(TAG, "onTaskRemoved — saving state and scheduling restart")
        // Persist current session elapsed time so restoreSession() can continue from here
        if (!isBreakActive) {
            prefs.sessionElapsedMs = sessionElapsedBeforeMs +
                    (System.currentTimeMillis() - sessionStartMs)
            prefs.screenOffTimeMs  = System.currentTimeMillis()
        }
        val pi = PendingIntent.getService(
            applicationContext, 1,
            Intent(applicationContext, MonitoringService::class.java).apply {
                action = ACTION_RESTART   // distinguishes from user-initiated start
            },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        // setExactAndAllowWhileIdle fires even in Doze mode — more reliable on MIUI
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pi
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Start handlers
    // ─────────────────────────────────────────────────────────────

    private fun handleFreshStart() {
        prefs.serviceEnabled = true
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        acquireWakeLock()
        // Attach distance warning view once — prevents repeated system overlay notifications
        overlayManager.initAllOverlays()
        startNewSession()
        Log.d(TAG, "Fresh start")
    }

    /**
     * Called on START_STICKY restart (intent == null).
     * In-memory state is lost — restore from SharedPreferences.
     */
    private fun handleServiceRestart() {
        Log.d(TAG, "Service restarted by system — restoring state")
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        acquireWakeLock()
        // Re-attach distance warning view after restart
        overlayManager.initAllOverlays()

        if (prefs.isBreakActive) {
            val remaining = prefs.breakEndTimeMs - System.currentTimeMillis()
            if (remaining > 3_000L) {
                isBreakActive    = true
                breakEndTimeMs   = prefs.breakEndTimeMs
                isBreakTtsPlayed = true   // don't re-announce
                requestAudioFocusForBreak()
                startCountdownOverlay(remaining)
            } else {
                // Break expired while the service was dead
                clearBreakState()
                restoreSession()
            }
        } else {
            restoreSession()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Screen ON / OFF
    // ─────────────────────────────────────────────────────────────

    private fun onScreenOff() {
        Log.d(TAG, "Screen OFF")
        prefs.sessionElapsedMs = sessionElapsedBeforeMs +
                (System.currentTimeMillis() - sessionStartMs)
        prefs.screenOffTimeMs = System.currentTimeMillis()
        mainHandler.removeCallbacks(breakCheckRunnable)
        stopCamera()
    }

    private fun onScreenOn() {
        Log.d(TAG, "Screen ON")
        if (isBreakActive) {
            // Countdown overlay may have been removed while screen was off — reattach
            overlayManager.reattachBreakCountdownIfActive()
            return
        }

        val screenOffAt = prefs.screenOffTimeMs
        if (screenOffAt == 0L) { startNewSession(); return }

        val offDurationMs   = System.currentTimeMillis() - screenOffAt
        val breakDurationMs = prefs.breakDurationMin * 60_000L

        if (offDurationMs >= breakDurationMs) {
            // Device was off long enough to count as a natural break
            startNewSession()
            if (prefs.breakTtsEnabled) ttsManager.speak(getString(R.string.new_session_start))
        } else {
            // Resume existing session
            sessionElapsedBeforeMs = prefs.sessionElapsedMs
            sessionStartMs = System.currentTimeMillis()
            startCamera()
            scheduleBreakCheck()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Session management
    // ─────────────────────────────────────────────────────────────

    private fun startNewSession() {
        sessionStartMs         = System.currentTimeMillis()
        sessionElapsedBeforeMs = 0L
        prefs.sessionElapsedMs = 0L
        prefs.screenOffTimeMs  = 0L
        warning5MinSent = false
        warning1MinSent = false
        startCamera()
        scheduleBreakCheck()
        Log.d(TAG, "New session started")
    }

    /**
     * Restore session after service restart (swipe-kill or system kill by MIUI).
     * Unlike startNewSession(), this preserves elapsed session time from SharedPreferences
     * so the break interval countdown continues from where it left off.
     *
     * Logic: if the gap between service death and restart is less than one break duration,
     * we treat it as the same session. Otherwise (e.g. device was off for a long time),
     * start fresh.
     */
    private fun restoreSession() {
        val savedElapsedMs  = prefs.sessionElapsedMs
        val savedOffTimeMs  = prefs.screenOffTimeMs
        val breakDurationMs = prefs.breakDurationMin * 60_000L

        // How long was the service dead?
        val gapMs = if (savedOffTimeMs > 0L)
            System.currentTimeMillis() - savedOffTimeMs
        else 0L

        if (savedElapsedMs > 0L && gapMs < breakDurationMs) {
            // Continue the existing session — restore elapsed time
            sessionElapsedBeforeMs = savedElapsedMs
            sessionStartMs = System.currentTimeMillis()
            warning5MinSent = false
            warning1MinSent = false
            Log.d(TAG, "Session restored: elapsed=${savedElapsedMs}ms, gap=${gapMs}ms")
        } else {
            // Gap too large or no saved state — start fresh
            Log.d(TAG, "Session gap too large (${gapMs}ms) or no state — starting new session")
            startNewSession()
            return
        }

        // FIX 2: Only start camera and schedule break check if screen is ON.
        // If screen is off, leave screenOffTimeMs intact so onScreenOn() can
        // resume the session correctly when the screen wakes.
        val isScreenOn = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
            .isInteractive
        if (isScreenOn) {
            prefs.screenOffTimeMs = 0L
            startCamera()
            scheduleBreakCheck()
        } else {
            Log.d(TAG, "Session restored but screen is OFF — waiting for onScreenOn()")
            // screenOffTimeMs stays intact so onScreenOn() knows when screen went off
        }
    }

    private fun scheduleBreakCheck() {
        mainHandler.removeCallbacks(breakCheckRunnable)
        mainHandler.postDelayed(breakCheckRunnable, 30_000L)
    }

    // ─────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────

    private fun setupFaceAnalyzer() {
        faceAnalyzer = FaceDistanceAnalyzer { distanceCm, faceWidthPx ->
            // Calibration mode — capture face width and calibrate immediately
            if (faceWidthPx != null && waitingForCalibration && faceWidthPx > 0f) {
                faceAnalyzer.calibrate(prefs.minDistanceCm.toFloat(), faceWidthPx)
                prefs.calibrationRefFaceWidth = faceWidthPx
                waitingForCalibration = false
                sendCalibrationResult(true)
                return@FaceDistanceAnalyzer
            }
            if (isBreakActive) return@FaceDistanceAnalyzer
            mainHandler.post { handleDistanceResult(distanceCm) }
        }
        // Restore previous calibration if available
        val savedRef = prefs.calibrationRefFaceWidth
        if (savedRef > 0f) faceAnalyzer.calibrate(prefs.minDistanceCm.toFloat(), savedRef)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        if (isBreakActive) return
        ProcessCameraProvider.getInstance(this).addListener({
            try {
                val cp = ProcessCameraProvider.getInstance(this).get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().also { it.setAnalyzer(cameraExecutor, faceAnalyzer) }
                cp.unbindAll()
                cp.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                Log.d(TAG, "Camera started")
            } catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            ProcessCameraProvider.getInstance(this).addListener({
                try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // Distance handling
    // ─────────────────────────────────────────────────────────────

    private fun handleDistanceResult(distanceCm: Float?) {
        if (distanceCm == null) { if (isTooClose) scheduleThankYouAndHide(); return }
        val minDist = prefs.minDistanceCm.toFloat()
        if (distanceCm < minDist && !isTooClose) {
            isTooClose = true
            // Cancel any pending thank-you — child got close again
            cancelPendingThankYou()
            showDistanceWarning()
        } else if (distanceCm >= minDist && isTooClose) {
            isTooClose = false
            scheduleThankYouAndHide()
        }
    }

    private fun showDistanceWarning() {
        val msg = prefs.effectiveDistanceMessage(this)
        overlayManager.showDistanceWarning(msg)
        if (prefs.distanceTtsEnabled) ttsManager.speak(msg)
    }

    /**
     * Schedule the thank-you message with a 3-second delay.
     * If the child gets too close again before the delay fires, the runnable
     * is cancelled so the thank-you never appears mid-warning.
     */
    private fun scheduleThankYouAndHide() {
        cancelPendingThankYou()
        val r = Runnable { showThankYouAndHide() }
        pendingThankYouRunnable = r
        mainHandler.postDelayed(r, THANK_YOU_DELAY_MS)
    }

    private fun cancelPendingThankYou() {
        pendingThankYouRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingThankYouRunnable = null
    }

    private fun showThankYouAndHide() {
        pendingThankYouRunnable = null
        val thankYou = prefs.effectiveThankYouMessage(this)
        overlayManager.hideWithThankYou(thankYou) {
            if (prefs.thankYouTtsEnabled) ttsManager.speak(thankYou)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Break timing
    // ─────────────────────────────────────────────────────────────

    private fun checkBreakTime() {
        if (isBreakActive) return
        val totalElapsedMs  = sessionElapsedBeforeMs +
                (System.currentTimeMillis() - sessionStartMs)
        val breakIntervalMs = prefs.breakIntervalMin * 60_000L
        val timeUntilMs     = breakIntervalMs - totalElapsedMs

        when {
            timeUntilMs <= 0 -> startBreak()
            timeUntilMs <= WARNING_1MIN_MS && !warning1MinSent -> {
                warning1MinSent = true
                val msg = getString(R.string.break_warning_1min)
                overlayManager.showWarningTransient(msg, durationMs = 5000L)
                if (prefs.breakTtsEnabled) ttsManager.speak(msg)
            }
            timeUntilMs <= WARNING_5MIN_MS && !warning5MinSent -> {
                warning5MinSent = true
                val msg = getString(R.string.break_warning_5min)
                overlayManager.showWarningTransient(msg, durationMs = 5000L)
                if (prefs.breakTtsEnabled) ttsManager.speak(msg)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Break flow
    // ─────────────────────────────────────────────────────────────

    private fun startBreak() {
        if (isBreakActive) return
        isBreakActive    = true
        isBreakTtsPlayed = false
        isTooClose       = false
        breakStartedMs   = System.currentTimeMillis()

        val breakDurationMs = prefs.breakDurationMin * 60_000L
        breakEndTimeMs = breakStartedMs + breakDurationMs

        // Persist immediately — survives MIUI service kill
        prefs.isBreakActive  = true
        prefs.breakEndTimeMs = breakEndTimeMs

        overlayManager.dismiss()   // clear any active distance warning
        cancelPendingThankYou()    // cancel pending 3s thank-you if any
        stopCamera()
        mainHandler.removeCallbacks(breakCheckRunnable)

        // Pause background media apps (e.g. YouTube Kids)
        requestAudioFocusForBreak()

        // TTS — read the full break message from settings
        if (prefs.breakTtsEnabled && !isBreakTtsPlayed) {
            isBreakTtsPlayed = true
            ttsManager.speak(prefs.effectiveBreakMessage(this))
        }

        Log.d(TAG, "Break started: ${prefs.breakDurationMin} min, exercise=${prefs.exerciseDuringBreak}")

        if (prefs.exerciseDuringBreak) {
            // Eye exercises first; countdown starts after they finish
            launchEyeExercise()
        } else {
            startCountdownOverlay(breakDurationMs)
        }
    }

    private fun launchEyeExercise() {
        try {
            startActivity(Intent(this, EyeExerciseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        } catch (e: Exception) {
            // startActivity() can be blocked on MIUI even from a foreground service.
            // Fall back: skip exercise and go straight to break countdown.
            Log.e(TAG, "launchEyeExercise failed — falling back to countdown", e)
            val remainingMs = breakEndTimeMs - System.currentTimeMillis()
            if (remainingMs > 0) startCountdownOverlay(remainingMs) else endBreak()
        }
    }

    /**
     * Shows the WindowManager countdown overlay for the given duration.
     * FLAG_SHOW_WHEN_LOCKED keeps it visible through lock/unlock cycles.
     * onEnd fires on the main thread when the timer reaches zero.
     */
    private fun startCountdownOverlay(durationMs: Long) {
        overlayManager.showBreakCountdown(
            breakMessage = prefs.effectiveBreakMessage(this),
            restLabel    = getString(R.string.break_rest_remaining),
            totalMs      = durationMs,
            onEnd        = { mainHandler.post { endBreak() } }
        )
    }

    /**
     * Called via ACTION_EXERCISE_DONE (from EyeExerciseActivity.afterExercise()).
     * Delivered through startForegroundService() — reliable on MIUI/Android 13+.
     */
    private fun onExerciseDone() {
        if (!isBreakActive) {
            // Service may have been restarted during the exercise — restore from prefs
            if (prefs.isBreakActive) {
                isBreakActive  = true
                breakEndTimeMs = prefs.breakEndTimeMs
                Log.d(TAG, "onExerciseDone: break state restored from prefs")
            } else {
                Log.w(TAG, "onExerciseDone: no active break found")
                return
            }
        }
        val remainingMs = breakEndTimeMs - System.currentTimeMillis()
        Log.d(TAG, "Exercise done — break remaining: ${remainingMs}ms")
        if (remainingMs > 3_000L) startCountdownOverlay(remainingMs) else endBreak()
    }

    private fun endBreak() {
        isBreakActive    = false
        isBreakTtsPlayed = false
        isTooClose       = false   // reset so distance logic starts fresh
        clearBreakState()
        abandonAudioFocus()
        cancelPendingThankYou()
        overlayManager.dismissBreakCountdown()
        overlayManager.dismiss()
        if (prefs.breakTtsEnabled) ttsManager.speak(getString(R.string.break_over))
        startNewSession()
        Log.d(TAG, "Break ended — new session")
    }

    private fun clearBreakState() {
        prefs.isBreakActive  = false
        prefs.breakEndTimeMs = 0L
    }

    // ─────────────────────────────────────────────────────────────
    // Audio focus — pauses media in background apps during break
    // ─────────────────────────────────────────────────────────────

    private fun requestAudioFocusForBreak() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // setWillPauseWhenDucked(true) REQUIRES setOnAudioFocusChangeListener —
                // omitting it throws IllegalStateException and crashes the service.
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { /* no-op: we keep the overlay regardless */ }
                    .setWillPauseWhenDucked(true)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestAudioFocusForBreak failed", e)
        }
    }

    private fun abandonAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Calibration
    // ─────────────────────────────────────────────────────────────

    private fun sendCalibrationResult(success: Boolean) {
        sendBroadcast(Intent(BROADCAST_CALIBRATION_RESULT).apply {
            putExtra(EXTRA_CALIBRATION_SUCCESS, success)
            `package` = packageName
        })
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // System broadcasts must be registered WITHOUT RECEIVER_NOT_EXPORTED
        registerReceiver(screenStateReceiver, filter)
    }

    // ─────────────────────────────────────────────────────────────
    // WakeLock / Stop / Cleanup
    // ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Kind Eye::MonitoringWakeLock"
        ).apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopMonitoring() {
        prefs.serviceEnabled = false
        clearBreakState()
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        mainHandler.removeCallbacks(breakCheckRunnable)
        cancelPendingThankYou()
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        abandonAudioFocus()
        overlayManager.dismissBreakCountdown()
        overlayManager.dismiss()
        overlayManager.releaseAllOverlays()
        ttsManager.shutdown()
        if (::faceAnalyzer.isInitialized) faceAnalyzer.shutdown()
        cameraExecutor.shutdown()
        releaseWakeLock()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    // ─────────────────────────────────────────────────────────────
    // Foreground notification
    // ─────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        // No Stop action in the notification — the user must stop protection from
        // inside the app so that the parental password gate is enforced.
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun parseLocale(tag: String): Locale = try {
        val parts = tag.split("-")
        if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
    } catch (_: Exception) { Locale("en", "US") }
}
