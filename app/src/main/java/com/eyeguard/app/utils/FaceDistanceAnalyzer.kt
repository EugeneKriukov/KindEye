package com.eyeguard.app.utils

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Analyzes camera frames to estimate face distance.
 *
 * Distance estimation method:
 *   distance_cm = (REAL_FACE_WIDTH_CM * focal_length_px) / face_bounding_box_width_px
 *
 * REAL_FACE_WIDTH_CM = 14.0 cm (average child/adult face width)
 *
 * Focal length (focal_length_px) is calibrated once at the known reference distance:
 *   focal_length_px = (ref_face_width_px * ref_distance_cm) / REAL_FACE_WIDTH_CM
 *
 * Without calibration, we use a relative threshold:
 *   if face_width_px / image_width_px > threshold → too close
 */
class FaceDistanceAnalyzer(
    private val onDistanceResult: (distanceCm: Float?, faceWidthPx: Float?) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FaceDistanceAnalyzer"
        const val REAL_FACE_WIDTH_CM = 14.0f

        // Without calibration: if face occupies > 55% of image width → treat as ~<30cm
        // This is device-agnostic fallback
        const val CLOSE_FACE_FRACTION = 0.55f

        // Estimated focal lengths for common front cameras (px, landscape)
        // For Redmi Pad SE 5MP front camera (~2592×1944) approximate focal ~1900px portrait
        const val DEFAULT_FOCAL_LENGTH_PX = 1900f
    }

    // Calibrated focal length (set from outside after calibration)
    var focalLengthPx: Float = DEFAULT_FOCAL_LENGTH_PX
    var calibrationRefFaceWidth: Float = 0f  // stored face width px at known distance

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
    )

    private var lastAnalysisTimeMs = 0L
    private val analysisIntervalMs = 500L  // analyze every 500ms to save resources

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTimeMs < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalysisTimeMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val imageWidth = if (imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270)
            imageProxy.height.toFloat() else imageProxy.width.toFloat()

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onDistanceResult(null, null)
                } else {
                    val face = faces.maxByOrNull { it.boundingBox.width() }!!
                    val faceWidthPx = face.boundingBox.width().toFloat()
                    val distanceCm = estimateDistance(faceWidthPx)
                    onDistanceResult(distanceCm, faceWidthPx)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                onDistanceResult(null, null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Estimate distance in cm from face width in pixels.
     * Uses calibrated focal length if available, otherwise default.
     */
    fun estimateDistance(faceWidthPx: Float): Float {
        return (REAL_FACE_WIDTH_CM * focalLengthPx) / faceWidthPx
    }

    /**
     * Calibrate focal length given the known reference distance and measured face width.
     */
    fun calibrate(refDistanceCm: Float, measuredFaceWidthPx: Float) {
        focalLengthPx = (measuredFaceWidthPx * refDistanceCm) / REAL_FACE_WIDTH_CM
        calibrationRefFaceWidth = measuredFaceWidthPx
        Log.d(TAG, "Calibrated: focal=${focalLengthPx}px at refDist=${refDistanceCm}cm, faceW=${measuredFaceWidthPx}px")
    }

    fun shutdown() {
        detector.close()
    }
}
