package com.eyeguard.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_MIN_DISTANCE_CM        = "min_distance_cm"
        const val KEY_DISTANCE_MESSAGE       = "distance_message"
        const val KEY_DISTANCE_TTS           = "distance_tts"
        const val KEY_THANK_YOU_TTS          = "thank_you_tts"
        const val KEY_THANK_YOU_MESSAGE      = "thank_you_message"
        const val KEY_CALIBRATION_REF        = "calibration_ref_face_width"
        const val KEY_BREAK_INTERVAL_MIN     = "break_interval_min"
        const val KEY_BREAK_DURATION_MIN     = "break_duration_min"
        const val KEY_BREAK_MESSAGE          = "break_message"
        const val KEY_BREAK_TTS              = "break_tts"
        const val KEY_EXERCISE_TTS           = "exercise_tts"
        const val KEY_EXERCISE_DURING_BREAK  = "exercise_during_break"
        const val KEY_LANGUAGE               = "tts_language"
        const val KEY_SERVICE_ENABLED        = "service_enabled"
        const val KEY_SCREEN_OFF_TIME_MS     = "screen_off_time_ms"
        const val KEY_SESSION_ELAPSED_MS     = "session_elapsed_ms"
        const val KEY_PARENTAL_PASSWORD      = "parental_password"

        // ── Break state persistence ── survives service kill/restart on MIUI
        const val KEY_IS_BREAK_ACTIVE        = "is_break_active"
        const val KEY_BREAK_END_TIME_MS      = "break_end_time_ms"

        const val DEFAULT_MIN_DISTANCE_CM    = 40
        const val DEFAULT_BREAK_INTERVAL_MIN = 20
        const val DEFAULT_BREAK_DURATION_MIN = 5
        const val DEFAULT_LANGUAGE           = "en-US"
    }

    var minDistanceCm: Int
        get() = prefs.getInt(KEY_MIN_DISTANCE_CM, DEFAULT_MIN_DISTANCE_CM)
        set(v) = prefs.edit().putInt(KEY_MIN_DISTANCE_CM, v).apply()

    var distanceMessage: String
        get() = prefs.getString(KEY_DISTANCE_MESSAGE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DISTANCE_MESSAGE, v).apply()

    var distanceTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISTANCE_TTS, true)
        set(v) = prefs.edit().putBoolean(KEY_DISTANCE_TTS, v).apply()

    var thankYouTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_THANK_YOU_TTS, true)
        set(v) = prefs.edit().putBoolean(KEY_THANK_YOU_TTS, v).apply()

    var thankYouMessage: String
        get() = prefs.getString(KEY_THANK_YOU_MESSAGE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_THANK_YOU_MESSAGE, v).apply()

    var calibrationRefFaceWidth: Float
        get() = prefs.getFloat(KEY_CALIBRATION_REF, 0f)
        set(v) = prefs.edit().putFloat(KEY_CALIBRATION_REF, v).apply()

    var breakIntervalMin: Int
        get() = prefs.getInt(KEY_BREAK_INTERVAL_MIN, DEFAULT_BREAK_INTERVAL_MIN)
        set(v) = prefs.edit().putInt(KEY_BREAK_INTERVAL_MIN, v).apply()

    var breakDurationMin: Int
        get() = prefs.getInt(KEY_BREAK_DURATION_MIN, DEFAULT_BREAK_DURATION_MIN)
        set(v) = prefs.edit().putInt(KEY_BREAK_DURATION_MIN, v).apply()

    var breakMessage: String
        get() = prefs.getString(KEY_BREAK_MESSAGE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BREAK_MESSAGE, v).apply()

    var breakTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BREAK_TTS, true)
        set(v) = prefs.edit().putBoolean(KEY_BREAK_TTS, v).apply()

    var exerciseTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXERCISE_TTS, true)
        set(v) = prefs.edit().putBoolean(KEY_EXERCISE_TTS, v).apply()

    var exerciseDuringBreak: Boolean
        get() = prefs.getBoolean(KEY_EXERCISE_DURING_BREAK, true)
        set(v) = prefs.edit().putBoolean(KEY_EXERCISE_DURING_BREAK, v).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(v) = prefs.edit().putString(KEY_LANGUAGE, v).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, v).apply()

    var screenOffTimeMs: Long
        get() = prefs.getLong(KEY_SCREEN_OFF_TIME_MS, 0L)
        set(v) = prefs.edit().putLong(KEY_SCREEN_OFF_TIME_MS, v).apply()

    var sessionElapsedMs: Long
        get() = prefs.getLong(KEY_SESSION_ELAPSED_MS, 0L)
        set(v) = prefs.edit().putLong(KEY_SESSION_ELAPSED_MS, v).apply()

    /** Parental control password. Empty string = no password set. */
    var parentalPassword: String
        get() = prefs.getString(KEY_PARENTAL_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PARENTAL_PASSWORD, v).apply()

    /**
     * Persisted break state — survives MonitoringService kill by MIUI.
     * Written by startBreak(), cleared by endBreak() and stopMonitoring().
     */
    var isBreakActive: Boolean
        get() = prefs.getBoolean(KEY_IS_BREAK_ACTIVE, false)
        set(v) = prefs.edit().putBoolean(KEY_IS_BREAK_ACTIVE, v).apply()

    var breakEndTimeMs: Long
        get() = prefs.getLong(KEY_BREAK_END_TIME_MS, 0L)
        set(v) = prefs.edit().putLong(KEY_BREAK_END_TIME_MS, v).apply()

    fun effectiveDistanceMessage(context: Context): String =
        distanceMessage.ifBlank { context.getString(com.eyeguard.app.R.string.default_distance_message) }

    fun effectiveBreakMessage(context: Context): String =
        breakMessage.ifBlank { context.getString(com.eyeguard.app.R.string.default_break_message) }

    fun effectiveThankYouMessage(context: Context): String =
        thankYouMessage.ifBlank { context.getString(com.eyeguard.app.R.string.thank_you) }
}
