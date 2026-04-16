package com.eyeguard.app.ui

import android.animation.ObjectAnimator
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.eyeguard.app.R
import com.eyeguard.app.services.MonitoringService
import com.eyeguard.app.utils.AppPreferences
import com.eyeguard.app.utils.TtsManager
import java.util.Locale

class EyeExerciseActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var tts: TtsManager
    private var currentExercise = 0
    private var timer: CountDownTimer? = null

    data class Exercise(
        val emoji: String,
        val titleRes: Int,
        val descRes: Int,
        val durationSec: Int,
        val bgColor: Int
    )

    private lateinit var exercises: List<Exercise>

    // Views
    private lateinit var tvProgress: TextView
    private lateinit var tvEmoji: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSkip: TextView
    private lateinit var btnNext: TextView
    private lateinit var cardContainer: LinearLayout
    private lateinit var confettiView: ConfettiView
    private lateinit var tvDoneMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        prefs = AppPreferences(this)

        // FIX 2: all durations = 30 seconds
        exercises = listOf(
            Exercise("🦋", R.string.exercise_1_title, R.string.exercise_1_desc, 30, Color.parseColor("#FFF0F8FF")),
            Exercise("⬆️", R.string.exercise_2_title, R.string.exercise_2_desc, 30, Color.parseColor("#FFF0FFF4")),
            Exercise("⬅️", R.string.exercise_3_title, R.string.exercise_3_desc, 30, Color.parseColor("#FFFFF0F0")),
            Exercise("⭕", R.string.exercise_4_title, R.string.exercise_4_desc, 30, Color.parseColor("#FFFFF8E6")),
            Exercise("👆", R.string.exercise_5_title, R.string.exercise_5_desc, 30, Color.parseColor("#FFF0F0FF")),
            Exercise("🙌", R.string.exercise_6_title, R.string.exercise_6_desc, 30, Color.parseColor("#FFFFE8F5"))
        )

        setContentView(buildLayout())

        tts = TtsManager(this)
        tts.init(parseLocale(prefs.language)) {
            runOnUiThread { showExercise(0) }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UI Build
    // ─────────────────────────────────────────────────────────────

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F0F8FF"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        confettiView = ConfettiView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val linear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(48))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvProgress = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#718096"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(16)) }
        }

        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(28).toFloat()
            }
            elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvEmoji = TextView(this).apply {
            textSize = 72f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvTitle = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(8)) }
        }

        tvDescription = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#4A5568"))
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                setMargins(0, dp(4), 0, dp(16))
            }
        }

        tvCounter = TextView(this).apply {
            textSize = 64f
            setTextColor(Color.parseColor("#4ECDC4"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        progressBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
            ).apply { setMargins(0, dp(12), 0, 0) }
            progressDrawable.setColorFilter(
                Color.parseColor("#4ECDC4"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }

        cardContainer.addView(tvEmoji)
        cardContainer.addView(tvTitle)
        cardContainer.addView(tvDescription)
        cardContainer.addView(tvCounter)
        cardContainer.addView(progressBar)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(24), 0, 0) }
        }

        btnSkip = buildButton("⏭  ${getString(R.string.exercise_skip)}", Color.parseColor("#FC8181"))
        btnSkip.setOnClickListener { finishExercises() }
        btnSkip.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(0, 0, dp(8), 0) }

        btnNext = buildButton("${getString(R.string.exercise_next)}  ▶", Color.parseColor("#48BB78"))
        btnNext.setOnClickListener { nextExercise() }
        btnNext.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(dp(8), 0, 0, 0) }

        btnLayout.addView(btnSkip)
        btnLayout.addView(btnNext)

        tvDoneMessage = TextView(this).apply {
            text = getString(R.string.exercise_done)
            textSize = 22f
            setTextColor(Color.parseColor("#4ECDC4"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(24), dp(16), dp(8)) }
            visibility = View.GONE
        }

        linear.addView(tvProgress)
        linear.addView(cardContainer)
        linear.addView(btnLayout)
        linear.addView(tvDoneMessage)

        scroll.addView(linear)
        root.addView(scroll)
        root.addView(confettiView)

        return root
    }

    private fun buildButton(label: String, bgColor: Int) = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(4).toFloat()
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    // ─────────────────────────────────────────────────────────────
    // Exercise logic
    // ─────────────────────────────────────────────────────────────

    private fun showExercise(index: Int) {
        if (index >= exercises.size) {
            finishExercises()
            return
        }
        currentExercise = index
        timer?.cancel()

        val ex = exercises[index]

        cardContainer.scaleX = 0.88f
        cardContainer.scaleY = 0.88f
        cardContainer.alpha = 0f
        cardContainer.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()

        (cardContainer.background as? GradientDrawable)?.setColor(ex.bgColor)

        tvProgress.text = "${getString(R.string.exercise_of)} ${index + 1} ${getString(R.string.exercise_of_total)} ${exercises.size}"
        tvEmoji.text = ex.emoji
        tvTitle.text = getString(ex.titleRes)
        tvDescription.text = getString(ex.descRes)
        progressBar.max = ex.durationSec
        progressBar.progress = ex.durationSec
        tvCounter.text = ex.durationSec.toString()

        if (prefs.exerciseTtsEnabled) {
            tts.speak(getString(ex.descRes))
        }

        timer = object : CountDownTimer(ex.durationSec * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000).toInt()
                tvCounter.text = sec.toString()
                progressBar.progress = sec
                if (sec <= 5) {
                    ObjectAnimator.ofFloat(tvCounter, "scaleX", 1f, 1.25f, 1f).apply {
                        duration = 500; start()
                    }
                    ObjectAnimator.ofFloat(tvCounter, "scaleY", 1f, 1.25f, 1f).apply {
                        duration = 500; start()
                    }
                }
            }
            override fun onFinish() {
                tvCounter.text = "✓"
                progressBar.progress = 0
                showExercise(index + 1)
            }
        }.start()
    }

    private fun nextExercise() {
        timer?.cancel()
        showExercise(currentExercise + 1)
    }

    private fun finishExercises() {
        timer?.cancel()

        tvDoneMessage.visibility = View.VISIBLE
        tvDoneMessage.alpha = 0f
        tvDoneMessage.animate().alpha(1f).setDuration(500).start()
        confettiView.start()

        if (prefs.exerciseTtsEnabled) {
            tts.speakWithCallback(getString(R.string.exercise_done)) {
                runOnUiThread {
                    Handler(Looper.getMainLooper()).postDelayed({ afterExercise() }, 1500)
                }
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ afterExercise() }, 2500)
        }
    }

    private fun afterExercise() {
        confettiView.stop()

        /**
         * Notify MonitoringService via startService(ACTION_EXERCISE_DONE).
         * More reliable than sendBroadcast on MIUI/Android 13+.
         */
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_EXERCISE_DONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    private fun parseLocale(tag: String): Locale = try {
        val parts = tag.split("-")
        if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
    } catch (_: Exception) { Locale("en", "US") }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        tts.shutdown()
        confettiView.stop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* disabled during exercises */ }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
