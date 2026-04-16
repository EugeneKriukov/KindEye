package com.eyeguard.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.eyeguard.app.R
import com.eyeguard.app.services.MonitoringService
import com.eyeguard.app.utils.AppPreferences

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * In-memory authentication flag.
         * Set to true when the correct parental password is entered.
         * Reset to false in onStop() so the password is required every time
         * the user returns to the app from the background.
         */
        private const val PASSWORD_OVERLAY_TAG = "eyeguard_password_overlay"
        var isAuthenticatedThisSession = false
    }

    private lateinit var prefs: AppPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var btnToggle: TextView
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        setContentView(buildUi())
        supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()

        // If the service should be running but MIUI killed it and START_STICKY
        // didn't restart it, kick it here. The service's onStartCommand safely
        // ignores duplicate starts when already running.
        if (prefs.serviceEnabled) {
            val intent = Intent(this, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else
                startService(intent)
        }

        // Ask for parental password every time the app comes to the foreground.
        // isAuthenticatedThisSession was reset to false in onStop().
        if (prefs.parentalPassword.isNotEmpty() && !isAuthenticatedThisSession) {
            showPasswordDialog()
        } else {
            updateStatus()
        }
    }

    /**
     * FIX (Task 2): Reset the auth flag whenever MainActivity leaves the
     * foreground. This covers: swipe to home, recent apps, screen off,
     * switching to another app. The next onResume() will then demand the
     * password again.
     *
     * Note: onStop() is NOT called when navigating between activities within
     * the same app (e.g. MainActivity → SettingsActivity), because onStop()
     * only fires after the new activity has fully started — preventing a
     * false password prompt when returning from settings.
     */
    override fun onStop() {
        super.onStop()
        isAuthenticatedThisSession = false
    }

    // ─────────────────────────────────────────────────────────────
    // Password gate
    // ─────────────────────────────────────────────────────────────

    private fun showPasswordDialog() {
        val decorView = window.decorView as FrameLayout

        // Remove any leftover overlay from a previous call (e.g. screen
        // rotated before the user entered the password).
        decorView.findViewWithTag<View>(PASSWORD_OVERLAY_TAG)?.let {
            decorView.removeView(it)
        }

        val overlay = FrameLayout(this).apply {
            tag = PASSWORD_OVERLAY_TAG
            setBackgroundColor(color(R.color.bg_main))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // CRITICAL: intercept ALL touch events so nothing underneath is tappable.
            // Without this, tapping outside the card reaches the scroll view behind.
            isClickable = true
            isFocusable = true
            setOnClickListener { /* consume — do not propagate to views below */ }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            elevation = dp(8).toFloat()
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        card.addView(TextView(this).apply {
            text = "🔐"
            textSize = 52f
            gravity = Gravity.CENTER
            layoutParams = rowParams().apply { setMargins(0, 0, 0, dp(8)) }
        })

        card.addView(TextView(this).apply {
            text = getString(R.string.parental_enter_password)
            textSize = 18f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = rowParams().apply { setMargins(0, 0, 0, dp(20)) }
        })

        val etPassword = EditText(this).apply {
            hint = getString(R.string.parental_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(color(R.color.text_primary))
            setHintTextColor(color(R.color.text_secondary))
            background = GradientDrawable().apply {
                setColor(color(R.color.bg_card_teal))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), color(R.color.teal_primary))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = rowParams().apply { setMargins(0, 0, 0, dp(8)) }
        }
        card.addView(etPassword)

        val tvError = TextView(this).apply {
            text = getString(R.string.parental_wrong_password)
            textSize = 13f
            setTextColor(color(R.color.red_warning))
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE  // reserve space but hidden until wrong attempt
            layoutParams = rowParams().apply { setMargins(0, 0, 0, dp(12)) }
        }
        card.addView(tvError)

        card.addView(TextView(this).apply {
            text = getString(R.string.parental_btn_enter)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(color(R.color.teal_primary))
                cornerRadius = dp(14).toFloat()
            }
            elevation = dp(4).toFloat()
            layoutParams = rowParams()
            setOnClickListener {
                val entered = etPassword.text.toString()
                if (entered == prefs.parentalPassword) {
                    isAuthenticatedThisSession = true
                    overlay.visibility = View.GONE
                    updateStatus()
                } else {
                    tvError.visibility = View.VISIBLE
                    etPassword.text.clear()
                    etPassword.requestFocus()
                }
            }
        })

        overlay.addView(card)
        decorView.addView(overlay)

        etPassword.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    // ─────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg_main))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(48))
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(color(R.color.bg_main))
        }

        root.addView(buildHeader())
        root.addView(spacer(dp(24)))
        root.addView(buildStatusCard())
        root.addView(spacer(dp(20)))

        btnToggle = buildMainButton()
        root.addView(btnToggle)
        root.addView(spacer(dp(16)))

        root.addView(buildActionButton("⚙️", getString(R.string.btn_settings), color(R.color.purple_accent)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(spacer(dp(10)))

        root.addView(buildActionButton("🔒", getString(R.string.btn_permissions), color(R.color.coral_accent)) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        })
        root.addView(spacer(dp(10)))

        root.addView(buildActionButton("🏥", getString(R.string.btn_who_info), color(R.color.teal_primary)) {
            startActivity(Intent(this, WHOInfoActivity::class.java))
        })
        root.addView(spacer(dp(10)))

        root.addView(buildActionButton("👤", getString(R.string.btn_about), color(R.color.blue_info)) {
            startActivity(Intent(this, AboutActivity::class.java))
        })
        root.addView(spacer(dp(24)))

        tvInfo = buildInfoCard()
        root.addView(tvInfo)

        scroll.addView(root)
        return scroll
    }

    private fun buildHeader(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        container.addView(TextView(this).apply {
            text = "👁️"
            textSize = 72f
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "Kind Eye"
            textSize = 32f
            setTextColor(color(R.color.teal_primary))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 15f
            setTextColor(color(R.color.text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        return container
    }

    private fun buildStatusCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = roundedBg(color(R.color.bg_card), radius = dp(20).toFloat())
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tvStatus = TextView(this).apply {
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tvStatusSub = TextView(this).apply {
            textSize = 13f
            setTextColor(color(R.color.text_secondary))
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, 0) }
        }
        card.addView(tvStatus)
        card.addView(tvStatusSub)
        return card
    }

    private fun buildMainButton(): TextView {
        return TextView(this).apply {
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            elevation = dp(6).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { toggleService() }
        }
    }

    private fun buildActionButton(emoji: String, label: String, bgColor: Int, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = "$emoji  $label"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBg(bgColor, radius = dp(16).toFloat())
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun buildInfoCard(): TextView {
        return TextView(this).apply {
            textSize = 13f
            setTextColor(color(R.color.text_secondary))
            background = roundedBg(color(R.color.bg_card_teal), radius = dp(16).toFloat())
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Logic
    // ─────────────────────────────────────────────────────────────

    private fun updateStatus() {
        val running = prefs.serviceEnabled
        if (running) {
            tvStatus.text = "✅ ${getString(R.string.status_running)}"
            tvStatus.setTextColor(color(R.color.green_ok))
            tvStatusSub.text = getString(R.string.status_sub_running)
            btnToggle.text = "⏹  ${getString(R.string.btn_stop_monitoring)}"
            btnToggle.background = roundedBg(color(R.color.red_warning), radius = dp(20).toFloat())
        } else {
            tvStatus.text = "😴 ${getString(R.string.status_stopped)}"
            tvStatus.setTextColor(color(R.color.text_secondary))
            tvStatusSub.text = getString(R.string.status_sub_stopped)
            btnToggle.text = "▶  ${getString(R.string.btn_start_monitoring)}"
            btnToggle.background = roundedBg(color(R.color.teal_primary), radius = dp(20).toFloat())
        }
        updateInfoText()
    }

    private fun updateInfoText() {
        tvInfo.text = buildString {
            append("⚙️  ${getString(R.string.info_current_settings)}\n")
            append("• ${getString(R.string.info_distance, prefs.minDistanceCm)}\n")
            append("• ${getString(R.string.info_break, prefs.breakIntervalMin, prefs.breakDurationMin)}\n")
            append("• ${getString(R.string.info_language, prefs.language)}\n")
            append("\n")
            append("📋  ${getString(R.string.info_who_title)}\n")
            append("• ${getString(R.string.info_who_1)}\n")
            append("• ${getString(R.string.info_who_2)}\n")
            append("• ${getString(R.string.info_who_3)}")
        }
    }

    private fun toggleService() {
        if (prefs.serviceEnabled) {
            startService(Intent(this, MonitoringService::class.java).apply {
                action = MonitoringService.ACTION_STOP
            })
            prefs.serviceEnabled = false
            updateStatus()
        } else {
            if (!hasEssentialPermissions()) {
                Toast.makeText(this, getString(R.string.perm_not_all), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, PermissionsActivity::class.java))
                return
            }
            val intent = Intent(this, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else
                startService(intent)
            prefs.serviceEnabled = true
            updateStatus()
        }
    }

    private fun hasEssentialPermissions(): Boolean {
        val hasCam = checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasOverlay = android.provider.Settings.canDrawOverlays(this)
        return hasCam && hasOverlay
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun rowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun roundedBg(bgColor: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(bgColor)
        cornerRadius = radius
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun color(resId: Int) = resources.getColor(resId, theme)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
