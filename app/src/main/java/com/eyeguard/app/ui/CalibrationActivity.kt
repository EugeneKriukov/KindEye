package com.eyeguard.app.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.eyeguard.app.R
import com.eyeguard.app.services.MonitoringService
import com.eyeguard.app.utils.AppPreferences
import com.eyeguard.app.utils.FaceDistanceAnalyzer
import com.eyeguard.app.utils.TtsManager
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CalibrationActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var tts: TtsManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceAnalyzer: FaceDistanceAnalyzer

    // Views
    private lateinit var previewView: PreviewView
    private lateinit var ovalView: OvalOverlayView
    private lateinit var tvHint: TextView
    private lateinit var tvDistance: TextView
    private lateinit var seekDistance: SeekBar
    private lateinit var btnCalibrate: TextView
    private lateinit var tvStatus: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    private var faceDetected = false
    private var lastFaceWidthPx: Float = 0f
    private var calibrationDone = false
    private var stableFrameCount = 0
    private val STABLE_FRAMES_REQUIRED = 3

    private val calibrationReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(
                MonitoringService.EXTRA_CALIBRATION_SUCCESS, false
            ) ?: false
            runOnUiThread { onCalibrationResult(success) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.title_calibration)
        prefs = AppPreferences(this)
        tts = TtsManager(this)
        tts.init(parseLocale(prefs.language))
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContentView(buildUi())
        startCamera()
        registerCalibrationReceiver()

        mainHandler.postDelayed({
            tts.speak(getString(R.string.calibration_place_face))
        }, 800)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(calibrationReceiver) } catch (_: Exception) {}
        if (::faceAnalyzer.isInitialized) faceAnalyzer.shutdown()
        cameraExecutor.shutdown()
        tts.shutdown()
    }

    // ─────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        ovalView = OvalOverlayView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Нижняя панель
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
            setBackgroundColor(Color.parseColor("#E6000000"))
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        tvHint = TextView(this).apply {
            text = getString(R.string.calibration_place_face)
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }

        val distanceLabel = TextView(this).apply {
            text = getString(R.string.calibration_distance_label)
            textSize = 13f
            setTextColor(Color.parseColor("#AACCDD"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }

        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        seekDistance = SeekBar(this).apply {
            max = 60  // 20..80 см
            progress = prefs.minDistanceCm - 20
            progressDrawable.setColorFilter(
                Color.parseColor("#4ECDC4"), PorterDuff.Mode.SRC_IN
            )
            thumb.setColorFilter(
                Color.parseColor("#4ECDC4"), PorterDuff.Mode.SRC_IN
            )
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val cm = p + 20
                    tvDistance.text = getString(R.string.label_distance_cm, cm)
                    if (fromUser) prefs.minDistanceCm = cm
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        tvDistance = TextView(this).apply {
            text = getString(R.string.label_distance_cm, prefs.minDistanceCm)
            textSize = 16f
            setTextColor(Color.parseColor("#4ECDC4"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minWidth = dp(72)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        seekRow.addView(seekDistance)
        seekRow.addView(tvDistance)

        btnCalibrate = TextView(this).apply {
            text = getString(R.string.btn_calibrate)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4ECDC4"))
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
            setOnClickListener { onCalibrateClicked() }
        }

        tvStatus = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        bottomPanel.addView(tvHint)
        bottomPanel.addView(distanceLabel)
        bottomPanel.addView(seekRow)
        bottomPanel.addView(btnCalibrate)
        bottomPanel.addView(tvStatus)

        root.addView(previewView)
        root.addView(ovalView)
        root.addView(bottomPanel)
        return root
    }

    // ─────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        faceAnalyzer = FaceDistanceAnalyzer { _, faceWidthPx ->
            val detected = faceWidthPx != null && faceWidthPx > 0f
            if (detected) {
                stableFrameCount++
                lastFaceWidthPx = faceWidthPx!!
            } else {
                stableFrameCount = 0
            }
            val isStable = stableFrameCount >= STABLE_FRAMES_REQUIRED
            mainHandler.post {
                if (faceDetected != isStable) {
                    faceDetected = isStable
                    onFaceDetectionChanged(isStable)
                }
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cp = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().also { it.setAnalyzer(cameraExecutor, faceAnalyzer) }

                cp.unbindAll()
                cp.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                android.util.Log.e("Calibration", "Camera failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ─────────────────────────────────────────────────────────────
    // Логика
    // ─────────────────────────────────────────────────────────────

    private fun onFaceDetectionChanged(detected: Boolean) {
        ovalView.setFaceDetected(detected)
        if (detected) {
            tvHint.text = getString(R.string.calibration_face_found)
            tvHint.setTextColor(Color.parseColor("#A8E6CF"))
            tts.speak(getString(R.string.calibration_face_found))
        } else if (!calibrationDone) {
            tvHint.text = getString(R.string.calibration_place_face)
            tvHint.setTextColor(Color.WHITE)
        }
    }

    private fun onCalibrateClicked() {
        if (!faceDetected || lastFaceWidthPx <= 0f) {
            tvStatus.text = getString(R.string.calibration_no_face)
            tvStatus.setTextColor(Color.parseColor("#FC8181"))
            tvStatus.visibility = View.VISIBLE
            tts.speak(getString(R.string.calibration_no_face))
            shakeView(btnCalibrate)
            return
        }

        val refDistCm = prefs.minDistanceCm.toFloat()
        faceAnalyzer.calibrate(refDistCm, lastFaceWidthPx)
        prefs.calibrationRefFaceWidth = lastFaceWidthPx
        onCalibrationResult(true)
    }

    private fun onCalibrationResult(success: Boolean) {
        calibrationDone = success
        if (success) {
            tvStatus.text = getString(R.string.calibration_success)
            tvStatus.setTextColor(Color.parseColor("#A8E6CF"))
            tvStatus.visibility = View.VISIBLE
            tvHint.text = getString(R.string.calibration_success)
            tvHint.setTextColor(Color.parseColor("#A8E6CF"))
            (btnCalibrate.background as? GradientDrawable)?.setColor(Color.parseColor("#48BB78"))
            btnCalibrate.text = "✅ ${getString(R.string.calibration_success)}"
            // FIX 1: Only say "Откалибровано" — no distance number
            tts.speak(getString(R.string.calibration_success))
            mainHandler.postDelayed({ finish() }, 2500)
        } else {
            tvStatus.text = getString(R.string.calibration_no_face)
            tvStatus.setTextColor(Color.parseColor("#FC8181"))
            tvStatus.visibility = View.VISIBLE
        }
    }

    private fun registerCalibrationReceiver() {
        val filter = IntentFilter(MonitoringService.BROADCAST_CALIBRATION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(calibrationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(calibrationReceiver, filter)
        }
    }

    private fun shakeView(view: View) {
        android.view.animation.TranslateAnimation(0f, 18f, 0f, 0f).apply {
            duration = 80
            repeatCount = 5
            repeatMode = android.view.animation.Animation.REVERSE
            view.startAnimation(this)
        }
    }

    private fun parseLocale(tag: String): Locale = try {
        val parts = tag.split("-")
        if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
    } catch (_: Exception) { Locale("ru", "RU") }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ─────────────────────────────────────────────────────────────────
// OvalOverlayView
// ─────────────────────────────────────────────────────────────────

class OvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
    }

    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.WHITE
    }

    private val paintClear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var faceDetected = false
    private var pulseValue = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulseValue = it.animatedFraction
            invalidate()
        }
    }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun setFaceDetected(detected: Boolean) {
        if (faceDetected == detected) return
        faceDetected = detected
        if (detected) pulseAnimator.start() else { pulseAnimator.cancel(); pulseValue = 0f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val ovalW = w * 0.65f
        val ovalH = ovalW * 1.35f
        val cx = w / 2f
        val cy = h * 0.42f
        val rect = RectF(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f)

        canvas.drawRect(0f, 0f, w, h, paintOverlay)
        canvas.drawOval(rect, paintClear)

        val borderColor = if (faceDetected) Color.parseColor("#4ECDC4") else Color.WHITE
        paintBorder.color = borderColor
        paintBorder.alpha = if (faceDetected) (180 + (pulseValue * 75).toInt()).coerceIn(0, 255) else 200
        paintBorder.strokeWidth = if (faceDetected) 5f + pulseValue * 7f else 4f
        canvas.drawOval(rect, paintBorder)

        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        val len = ovalW * 0.12f
        canvas.drawLine(rect.left, rect.top + len * 2f, rect.left, rect.top + len * 0.5f, mp)
        canvas.drawLine(rect.right, rect.top + len * 2f, rect.right, rect.top + len * 0.5f, mp)
        canvas.drawLine(rect.left, rect.bottom - len * 2f, rect.left, rect.bottom - len * 0.5f, mp)
        canvas.drawLine(rect.right, rect.bottom - len * 2f, rect.right, rect.bottom - len * 0.5f, mp)
    }
}
