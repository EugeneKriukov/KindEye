package com.eyeguard.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Manages all WindowManager overlays.
 *
 * Types:
 *  1. Distance warning  — full-screen black, blocks touch, fades in 1.5s
 *  2. hideWithThankYou  — replaces icon with 🤗, fades out
 *  3. Transient warning — semi-transparent, touch-passthrough, auto-dismiss
 *  4. Break countdown   — full-screen black, 😴 icon, MM:SS countdown, fades in 1.5s
 *
 * KEY DESIGN: All overlay views are attached to WindowManager ONCE when the
 * service starts (via initAllOverlays), then shown/hidden by toggling visibility
 * and updating LayoutParams. This means the system overlay notification fires ONCE
 * per service session instead of on every message display.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Distance warning ──────────────────────────────────────────
    private var distanceView: FrameLayout? = null
    private var distanceIcon: TextView? = null
    private var distanceText: TextView? = null
    private var isDistanceVisible = false
    private var isFadingOut = false

    // ── Transient warning (5-min / 1-min pre-break alerts) ────────
    private var transientView: FrameLayout? = null
    private var transientText: TextView? = null
    private var isTransientVisible = false
    private val hideTransientRunnable = Runnable { hideTransient() }

    // ── Break countdown ───────────────────────────────────────────
    private var countdownView: FrameLayout? = null
    private var countdownMessage: TextView? = null
    private var countdownLabel: TextView? = null
    private var countdownDigits: TextView? = null
    private var countdownBar: ProgressBar? = null
    private var isCountdownVisible = false
    private var countdownTotalMs = 0L
    private var countdownEndMs = 0L
    private var countdownOnEnd: (() -> Unit)? = null
    private val countdownTickRunnable = object : Runnable {
        override fun run() {
            val remainingMs = countdownEndMs - System.currentTimeMillis()
            if (remainingMs <= 0) {
                hideCountdown()
                countdownOnEnd?.invoke()
            } else {
                updateCountdownUi(remainingMs)
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    // ── Attachment state ──────────────────────────────────────────
    private var allAttached = false

    // ─────────────────────────────────────────────────────────────
    // Layout params factories
    // ─────────────────────────────────────────────────────────────

    private fun makeBlockingParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /**
     * Break countdown params: blocks touch + shows on lock screen, but does NOT
     * keep the screen on. The device can sleep naturally per its own timeout settings.
     */
    private fun makeBreakParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            // No FLAG_KEEP_SCREEN_ON — device sleeps naturally during break
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun makePassthroughParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    // ─────────────────────────────────────────────────────────────
    // Initialisation — attach ALL views ONCE at service start
    // ─────────────────────────────────────────────────────────────

    /**
     * Call once from MonitoringService.handleFreshStart() / handleServiceRestart().
     * Attaches all three overlay views to WindowManager in passthrough (invisible) mode.
     * Three addView() calls total per service session = three system notifications total.
     * All subsequent show/hide operations use updateViewLayout() only — no new notifications.
     */
    fun initAllOverlays() {
        if (allAttached) return
        mainHandler.post {
            if (allAttached) return@post
            buildDistanceView()
            buildTransientView()
            buildCountdownView()
            try {
                windowManager.addView(distanceView,  makePassthroughParams())
                windowManager.addView(transientView, makePassthroughParams())
                windowManager.addView(countdownView, makePassthroughParams())
                allAttached = true
            } catch (_: Exception) {}
        }
    }

    /** Remove all overlay views when service stops. */
    fun releaseAllOverlays() {
        mainHandler.post {
            mainHandler.removeCallbacks(hideTransientRunnable)
            mainHandler.removeCallbacks(countdownTickRunnable)
            if (allAttached) {
                listOf(distanceView, transientView, countdownView).forEach { v ->
                    if (v != null) try { windowManager.removeView(v) } catch (_: Exception) {}
                }
                allAttached = false
            }
            distanceView  = null; distanceIcon  = null; distanceText  = null
            transientView = null; transientText = null
            countdownView = null; countdownDigits = null; countdownBar = null
            countdownMessage = null; countdownLabel = null
            isDistanceVisible = false; isTransientVisible = false; isCountdownVisible = false
            isFadingOut = false
            countdownEndMs = 0L; countdownOnEnd = null
        }
    }

    // ─────────────────────────────────────────────────────────────
    // View builders — called once during initAllOverlays()
    // ─────────────────────────────────────────────────────────────

    private fun buildDistanceView() {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.INVISIBLE
        }
        val linear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        distanceIcon = TextView(context).apply {
            text = "⚠️"; textSize = 72f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 24)
        }
        distanceText = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 34f; gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        linear.addView(distanceIcon); linear.addView(distanceText)
        container.addView(linear)
        distanceView = container
    }

    private fun buildTransientView() {
        val container = FrameLayout(context).apply {
            alpha = 0f; visibility = View.INVISIBLE
        }
        val linear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xDD000000.toInt()); setPadding(48, 40, 48, 40)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
        }
        linear.addView(TextView(context).apply {
            text = "🕐"; textSize = 48f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        })
        transientText = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 26f; gravity = Gravity.CENTER
            setPadding(24, 0, 24, 0)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        linear.addView(transientText)
        container.addView(linear)
        transientView = container
    }

    private fun buildCountdownView() {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK); alpha = 0f; visibility = View.INVISIBLE
        }
        val linear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(56, 40, 56, 40)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        linear.addView(TextView(context).apply {
            text = "😴"; textSize = 80f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        })
        countdownMessage = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 26f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(0, 0, 0, 32)
        }
        linear.addView(countdownMessage)
        countdownLabel = TextView(context).apply {
            setTextColor(Color.parseColor("#A8E6CF")); textSize = 16f
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 8)
        }
        linear.addView(countdownLabel)
        countdownDigits = TextView(context).apply {
            setTextColor(Color.parseColor("#4ECDC4")); textSize = 72f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 24)
        }
        linear.addView(countdownDigits)
        countdownBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = 1000
            progressDrawable?.setColorFilter(
                Color.parseColor("#4ECDC4"), android.graphics.PorterDuff.Mode.SRC_IN
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20)
        }
        linear.addView(countdownBar)
        container.addView(linear)
        countdownView = container
    }

    // ─────────────────────────────────────────────────────────────
    // Distance warning
    // ─────────────────────────────────────────────────────────────

    fun showDistanceWarning(message: String) {
        mainHandler.post {
            ensureAttached()
            if (isDistanceVisible && !isFadingOut) {
                distanceText?.text = message
                return@post
            }
            isFadingOut = false
            distanceIcon?.text = "⚠️"
            distanceText?.text = message
            // Switch to blocking, make visible, fade in
            try { windowManager.updateViewLayout(distanceView, makeBlockingParams()) } catch (_: Exception) {}
            distanceView?.alpha = 0f
            distanceView?.visibility = View.VISIBLE
            animateFadeIn(distanceView!!)
            isDistanceVisible = true
        }
    }

    fun hideWithThankYou(thankYouText: String, onDone: (() -> Unit)? = null) {
        mainHandler.post {
            if (!isDistanceVisible || isFadingOut || distanceView == null) return@post
            isFadingOut = true
            distanceIcon?.text = "🤗"
            distanceText?.text = thankYouText
            animateFadeOut(distanceView!!, startDelayMs = 600L) {
                distanceView?.visibility = View.INVISIBLE
                try { windowManager.updateViewLayout(distanceView, makePassthroughParams()) } catch (_: Exception) {}
                isDistanceVisible = false
                isFadingOut = false
                onDone?.invoke()
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (!isDistanceVisible || distanceView == null) return@post
            distanceView?.visibility = View.INVISIBLE
            try { windowManager.updateViewLayout(distanceView, makePassthroughParams()) } catch (_: Exception) {}
            isDistanceVisible = false
            isFadingOut = false
        }
    }

    fun isVisible() = isDistanceVisible && !isFadingOut

    // ─────────────────────────────────────────────────────────────
    // Transient warning (touch-passthrough, auto-dismiss)
    // ─────────────────────────────────────────────────────────────

    fun showWarningTransient(message: String, durationMs: Long = 5000L) {
        mainHandler.post {
            ensureAttached()
            mainHandler.removeCallbacks(hideTransientRunnable)
            transientText?.text = message
            if (!isTransientVisible) {
                try { windowManager.updateViewLayout(transientView, makePassthroughParams()) } catch (_: Exception) {}
                transientView?.alpha = 0f
                transientView?.visibility = View.VISIBLE
                animateFadeIn(transientView!!, durationMs = 800L)
                isTransientVisible = true
            }
            mainHandler.postDelayed(hideTransientRunnable, durationMs)
        }
    }

    private fun hideTransient() {
        val view = transientView ?: return
        animateFadeOut(view, durationMs = 600L) {
            view.visibility = View.INVISIBLE
            isTransientVisible = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Break countdown (full-screen blackout, 😴, MM:SS, fades in)
    // ─────────────────────────────────────────────────────────────

    fun showBreakCountdown(
        breakMessage: String,
        restLabel: String,
        totalMs: Long,
        onEnd: () -> Unit
    ) {
        mainHandler.post {
            ensureAttached()
            mainHandler.removeCallbacks(countdownTickRunnable)

            countdownTotalMs = totalMs
            countdownEndMs = System.currentTimeMillis() + totalMs
            countdownOnEnd = onEnd

            countdownMessage?.text = breakMessage
            countdownLabel?.text  = restLabel
            countdownDigits?.text = formatTime(totalMs)
            countdownBar?.progress = 1000

            // Switch to break params (no FLAG_KEEP_SCREEN_ON), make visible, fade in
            try { windowManager.updateViewLayout(countdownView, makeBreakParams()) } catch (_: Exception) {}
            countdownView?.alpha = 0f
            countdownView?.visibility = View.VISIBLE
            animateFadeIn(countdownView!!, durationMs = 1500L)
            isCountdownVisible = true

            mainHandler.post(countdownTickRunnable)
        }
    }

    /** Re-attach params after SCREEN_ON when break is still running */
    fun reattachBreakCountdownIfActive() {
        mainHandler.post {
            if (isCountdownVisible && countdownView != null) {
                try { windowManager.updateViewLayout(countdownView, makeBreakParams()) } catch (_: Exception) {}
            }
        }
    }

    fun dismissBreakCountdown() {
        mainHandler.post {
            mainHandler.removeCallbacks(countdownTickRunnable)
            hideCountdown()
            countdownEndMs = 0L
            countdownOnEnd = null
        }
    }

    fun isBreakCountdownActive() = countdownEndMs > 0 &&
            System.currentTimeMillis() < countdownEndMs

    private fun hideCountdown() {
        countdownView?.visibility = View.INVISIBLE
        try { windowManager.updateViewLayout(countdownView, makePassthroughParams()) } catch (_: Exception) {}
        isCountdownVisible = false
    }

    private fun updateCountdownUi(remainingMs: Long) {
        countdownDigits?.text = formatTime(remainingMs)
        val fraction = if (countdownTotalMs > 0)
            (remainingMs.toFloat() / countdownTotalMs).coerceIn(0f, 1f)
        else 0f
        countdownBar?.progress = (fraction * 1000).toInt()
    }

    /** Always two digits: "05:00", "01:30", "00:45" */
    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(totalSec / 60, totalSec % 60)
    }

    // ─────────────────────────────────────────────────────────────
    // Safety: if overlay was not pre-attached (edge case), attach now
    // ─────────────────────────────────────────────────────────────

    private fun ensureAttached() {
        if (allAttached) return
        if (distanceView  == null) buildDistanceView()
        if (transientView == null) buildTransientView()
        if (countdownView == null) buildCountdownView()
        try {
            windowManager.addView(distanceView,  makePassthroughParams())
            windowManager.addView(transientView, makePassthroughParams())
            windowManager.addView(countdownView, makePassthroughParams())
            allAttached = true
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────

    private fun animateFadeIn(view: View, durationMs: Long = 1500L) {
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = durationMs
            start()
        }
    }

    private fun animateFadeOut(
        view: View,
        durationMs: Long = 1000L,
        startDelayMs: Long = 0L,
        onEnd: () -> Unit
    ) {
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationMs
            startDelay = startDelayMs
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }
}
