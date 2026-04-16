package com.eyeguard.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.eyeguard.app.R
import com.eyeguard.app.services.MonitoringService
import com.eyeguard.app.utils.AppPreferences
import com.eyeguard.app.utils.LocaleHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    private lateinit var etDistanceMsg: EditText
    private lateinit var swDistanceTts: Switch
    private lateinit var etThankYouMsg: EditText
    private lateinit var swThankYouTts: Switch
    private lateinit var seekInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var seekBreakDur: SeekBar
    private lateinit var tvBreakDurValue: TextView
    private lateinit var etBreakMsg: EditText
    private lateinit var swBreakTts: Switch
    private lateinit var swExerciseTts: Switch
    private lateinit var swExerciseDuringBreak: Switch
    private lateinit var spinnerLang: Spinner
    private lateinit var tvCalibStatus: TextView

    // Parental control views
    private lateinit var tvParentalStatus: TextView
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvParentalMsg: TextView
    private lateinit var btnSetPassword: TextView
    private lateinit var btnRemovePassword: TextView

    private var spinnerReady = false

    private val languages = listOf(
        "en-US" to "🇺🇸  English (US)",
        "ru-RU" to "🇷🇺  Русский"
    )

    private val calibrationReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(
                MonitoringService.EXTRA_CALIBRATION_SUCCESS, false
            ) ?: false
            runOnUiThread {
                tvCalibStatus.text = if (success)
                    "✅ ${getString(R.string.calibration_success)}"
                else
                    "❌ ${getString(R.string.calibration_no_face)}"
                tvCalibStatus.setTextColor(
                    if (success) color(R.color.green_ok) else color(R.color.red_warning)
                )
                tvCalibStatus.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        setContentView(buildUi())
        supportActionBar?.title = getString(R.string.title_settings)
        loadValues()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MonitoringService.BROADCAST_CALIBRATION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(calibrationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(calibrationReceiver, filter)
        }
        refreshParentalStatus()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(calibrationReceiver) } catch (_: Exception) {}
        prefs.distanceMessage = etDistanceMsg.text.toString().trim()
        prefs.thankYouMessage = etThankYouMsg.text.toString().trim()
        prefs.breakMessage    = etBreakMsg.text.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────
    // Load values
    // ─────────────────────────────────────────────────────────────

    private fun loadValues() {
        etDistanceMsg.setText(
            prefs.distanceMessage.ifBlank { getString(R.string.default_distance_message) }
        )
        swDistanceTts.isChecked = prefs.distanceTtsEnabled

        etThankYouMsg.setText(
            prefs.thankYouMessage.ifBlank { getString(R.string.thank_you) }
        )
        swThankYouTts.isChecked = prefs.thankYouTtsEnabled

        seekInterval.progress = prefs.breakIntervalMin - 5
        tvIntervalValue.text = getString(R.string.label_minutes, prefs.breakIntervalMin)

        seekBreakDur.progress = prefs.breakDurationMin - 1
        tvBreakDurValue.text = getString(R.string.label_minutes, prefs.breakDurationMin)

        etBreakMsg.setText(
            prefs.breakMessage.ifBlank { getString(R.string.default_break_message) }
        )
        swBreakTts.isChecked = prefs.breakTtsEnabled
        swExerciseTts.isChecked = prefs.exerciseTtsEnabled
        swExerciseDuringBreak.isChecked = prefs.exerciseDuringBreak

        spinnerReady = false
        val idx = languages.indexOfFirst { it.first == prefs.language }.coerceAtLeast(0)
        spinnerLang.setSelection(idx)
        spinnerReady = true

        refreshParentalStatus()
    }

    private fun refreshParentalStatus() {
        val hasPassword = prefs.parentalPassword.isNotEmpty()
        if (hasPassword) {
            tvParentalStatus.text = "✅ ${getString(R.string.parental_status_active)}"
            tvParentalStatus.setTextColor(color(R.color.green_ok))
            btnSetPassword.text = getString(R.string.parental_btn_change)
            btnRemovePassword.visibility = View.VISIBLE
        } else {
            tvParentalStatus.text = getString(R.string.parental_status_inactive)
            tvParentalStatus.setTextColor(color(R.color.text_secondary))
            btnSetPassword.text = getString(R.string.parental_btn_set)
            btnRemovePassword.visibility = View.GONE
        }
        tvParentalMsg.visibility = View.GONE
        etNewPassword.text.clear()
        etConfirmPassword.text.clear()
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
            setPadding(dp(20), dp(16), dp(20), dp(48))
            setBackgroundColor(color(R.color.bg_main))
        }

        // ── 1. Language ───────────────────────────────────────────
        root.addView(sectionHeader("🌐", getString(R.string.settings_section_language)))

        spinnerLang = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages.map { it.second }
            )
            background = GradientDrawable().apply {
                setColor(color(R.color.bg_card))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), color(R.color.mint_accent))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(8)) }

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!spinnerReady) return
                    val tag = languages[position].first
                    if (tag != prefs.language) {
                        prefs.language = tag
                        prefs.distanceMessage = ""
                        prefs.thankYouMessage = ""
                        prefs.breakMessage    = ""
                        LocaleHelper.applyLocale(tag)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        root.addView(spinnerLang)
        root.addView(hintCard(getString(R.string.lang_switch_hint)))
        root.addView(spacer(dp(4)))

        // ── 2. Calibration ────────────────────────────────────────
        root.addView(sectionHeader("🎯", getString(R.string.settings_section_calibration)))
        root.addView(hintCard(getString(R.string.calibration_full_hint)))
        root.addView(spacer(dp(10)))
        root.addView(actionBtn(getString(R.string.btn_calibrate_start), color(R.color.blue_info)) {
            prefs.distanceMessage = etDistanceMsg.text.toString().trim()
            prefs.thankYouMessage = etThankYouMsg.text.toString().trim()
            prefs.breakMessage    = etBreakMsg.text.toString().trim()
            startActivity(Intent(this, CalibrationActivity::class.java))
        })
        tvCalibStatus = TextView(this).apply {
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), 0)
            visibility = View.GONE
        }
        root.addView(tvCalibStatus)
        root.addView(spacer(dp(4)))

        // ── 3. Distance warning ───────────────────────────────────
        root.addView(sectionHeader("⚠️", getString(R.string.pref_distance_message)))
        etDistanceMsg = editText(getString(R.string.default_distance_message))
        root.addView(etDistanceMsg)
        swDistanceTts = toggle(getString(R.string.pref_distance_tts)) { prefs.distanceTtsEnabled = it }
        root.addView(swDistanceTts)

        // ── 4. Praise ─────────────────────────────────────────────
        root.addView(spacer(dp(4)))
        root.addView(sectionHeader("💚", getString(R.string.section_praise)))
        etThankYouMsg = editText(getString(R.string.thank_you))
        root.addView(etThankYouMsg)
        swThankYouTts = toggle(getString(R.string.pref_thank_you_tts)) { prefs.thankYouTtsEnabled = it }
        root.addView(swThankYouTts)

        // ── 5. Breaks ─────────────────────────────────────────────
        root.addView(spacer(dp(4)))
        root.addView(sectionHeader("⏱", getString(R.string.settings_section_break)))

        root.addView(label(getString(R.string.break_interval_label)))
        val (sInt, tvInt) = seekRow(max = 55) { p ->
            tvIntervalValue.text = getString(R.string.label_minutes, p + 5)
            prefs.breakIntervalMin = p + 5
        }
        seekInterval = sInt; tvIntervalValue = tvInt
        root.addView(seekContainer(seekInterval, tvIntervalValue))

        root.addView(label(getString(R.string.break_duration_label)))
        val (sDur, tvDur) = seekRow(max = 14) { p ->
            tvBreakDurValue.text = getString(R.string.label_minutes, p + 1)
            prefs.breakDurationMin = p + 1
        }
        seekBreakDur = sDur; tvBreakDurValue = tvDur
        root.addView(seekContainer(seekBreakDur, tvBreakDurValue))

        root.addView(label(getString(R.string.pref_break_message)))
        etBreakMsg = editText(getString(R.string.default_break_message))
        root.addView(etBreakMsg)
        swBreakTts = toggle(getString(R.string.pref_break_tts)) { prefs.breakTtsEnabled = it }
        root.addView(swBreakTts)

        // ── 6. Eye exercises ──────────────────────────────────────
        root.addView(spacer(dp(4)))
        root.addView(sectionHeader("👁", getString(R.string.settings_section_exercise)))
        swExerciseDuringBreak = toggle(getString(R.string.pref_exercise_during_break)) {
            prefs.exerciseDuringBreak = it
        }
        root.addView(swExerciseDuringBreak)
        swExerciseTts = toggle(getString(R.string.pref_exercise_tts)) { prefs.exerciseTtsEnabled = it }
        root.addView(swExerciseTts)
        root.addView(spacer(dp(8)))
        root.addView(actionBtn("▶️  ${getString(R.string.try_exercise)}", color(R.color.teal_primary)) {
            prefs.distanceMessage = etDistanceMsg.text.toString().trim()
            prefs.thankYouMessage = etThankYouMsg.text.toString().trim()
            prefs.breakMessage    = etBreakMsg.text.toString().trim()
            startActivity(Intent(this, EyeExerciseActivity::class.java))
        })

        // ── 7. Parental Control (last section) ────────────────────
        root.addView(spacer(dp(4)))
        root.addView(sectionHeader("🔐", getString(R.string.settings_section_parental)))
        root.addView(hintCard(getString(R.string.parental_hint)))
        root.addView(spacer(dp(10)))

        tvParentalStatus = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(tvParentalStatus)

        etNewPassword = passwordField(getString(R.string.parental_new_password_hint))
        root.addView(etNewPassword)

        etConfirmPassword = passwordField(getString(R.string.parental_confirm_password_hint))
        root.addView(etConfirmPassword)

        tvParentalMsg = TextView(this).apply {
            textSize = 13f
            setPadding(dp(4), dp(2), dp(4), dp(4))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(tvParentalMsg)

        btnSetPassword = actionBtn(getString(R.string.parental_btn_set), color(R.color.teal_primary)) {
            savePassword()
        }
        root.addView(btnSetPassword)

        btnRemovePassword = actionBtn(getString(R.string.parental_btn_remove), color(R.color.red_warning)) {
            removePassword()
        }
        root.addView(btnRemovePassword)

        root.addView(spacer(dp(32)))
        scroll.addView(root)
        return scroll
    }

    // ─────────────────────────────────────────────────────────────
    // Parental control logic
    // ─────────────────────────────────────────────────────────────

    private fun savePassword() {
        val newPwd = etNewPassword.text.toString().trim()
        val confirmPwd = etConfirmPassword.text.toString().trim()

        if (newPwd.isEmpty()) {
            showParentalMsg(getString(R.string.parental_error_empty), isError = true)
            return
        }
        if (newPwd.length < 4) {
            showParentalMsg(getString(R.string.parental_error_too_short), isError = true)
            return
        }
        if (newPwd != confirmPwd) {
            showParentalMsg(getString(R.string.parental_error_mismatch), isError = true)
            etConfirmPassword.text.clear()
            return
        }

        prefs.parentalPassword = newPwd
        // Reset session flag so the new password is required next time
        MainActivity.isAuthenticatedThisSession = false
        showParentalMsg(getString(R.string.parental_saved), isError = false)
        refreshParentalStatus()
    }

    private fun removePassword() {
        prefs.parentalPassword = ""
        MainActivity.isAuthenticatedThisSession = false
        showParentalMsg(getString(R.string.parental_removed), isError = false)
        refreshParentalStatus()
    }

    private fun showParentalMsg(text: String, isError: Boolean) {
        tvParentalMsg.text = text
        tvParentalMsg.setTextColor(
            if (isError) color(R.color.red_warning) else color(R.color.green_ok)
        )
        tvParentalMsg.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // Widget helpers
    // ─────────────────────────────────────────────────────────────

    private fun sectionHeader(emoji: String, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(20), 0, dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, dp(2), dp(12), dp(2)) }
            setBackgroundColor(color(R.color.teal_primary))
        })
        addView(TextView(context).apply {
            text = "$emoji  $title"
            textSize = 18f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.text_secondary))
        setPadding(dp(4), dp(4), 0, dp(2))
    }

    private fun hintCard(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card_teal))
            cornerRadius = dp(10).toFloat()
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setSingleLine(false)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun editText(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(color(R.color.text_primary))
        setHintTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), color(R.color.mint_accent))
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(8)) }
    }

    private fun passwordField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setTextColor(color(R.color.text_primary))
        setHintTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), color(R.color.mint_accent))
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(8)) }
    }

    private fun toggle(label: String, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        setTextColor(color(R.color.text_primary))
        textSize = 15f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(4)) }
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun seekRow(max: Int, onMove: (Int) -> Unit): Pair<SeekBar, TextView> {
        val seek = SeekBar(this).apply {
            this.max = max
            progressDrawable.setColorFilter(color(R.color.teal_primary), android.graphics.PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(color(R.color.teal_primary), android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onMove(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val tv = TextView(this).apply {
            textSize = 15f
            setTextColor(color(R.color.teal_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minWidth = dp(72)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        return Pair(seek, tv)
    }

    private fun seekContainer(seek: SeekBar, label: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { setColor(color(R.color.bg_card)); cornerRadius = dp(12).toFloat() }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(2), 0, dp(8)) }
        addView(seek); addView(label)
    }

    private fun actionBtn(text: String, bgColor: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(bgColor); cornerRadius = dp(16).toFloat() }
        elevation = dp(4).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(8)) }
        setOnClickListener { onClick() }
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun color(resId: Int) = resources.getColor(resId, theme)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
