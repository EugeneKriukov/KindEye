package com.eyeguard.app.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = ArrayDeque<String>()

    companion object {
        private const val TAG = "TtsManager"
    }

    fun init(locale: Locale = Locale("ru", "RU"), onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default locale
                    tts?.setLanguage(Locale.getDefault())
                    Log.w(TAG, "Locale $locale not supported, using default")
                }
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                isReady = true
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS init failed with status: $status")
            }
        }
    }

    fun setLocale(locale: Locale) {
        if (isReady) {
            tts?.setLanguage(locale)
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, "eyeguard_${System.currentTimeMillis()}")
    }

    fun speakWithCallback(text: String, onDone: () -> Unit) {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val utteranceId = "cb_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking() = tts?.isSpeaking == true

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
