package com.eyeguard.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Вьюха с анимацией конфетти — показывается после завершения зарядки.
 * Запустить: confettiView.start()
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val colors = listOf(
        Color.parseColor("#FF6B9D"),  // розовый
        Color.parseColor("#4ECDC4"),  // бирюзовый
        Color.parseColor("#FFE66D"),  // жёлтый
        Color.parseColor("#A8E6CF"),  // мятный
        Color.parseColor("#FF8A65"),  // коралловый
        Color.parseColor("#BB86FC"),  // фиолетовый
        Color.parseColor("#FFFFFF")   // белый
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        val color: Int,
        val size: Float,
        val speedX: Float,
        val speedY: Float,
        val rotation: Float,
        val rotationSpeed: Float,
        var angle: Float = 0f,
        var alpha: Float = 1f
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var progress = 0f

    fun start() {
        particles.clear()

        // Создаём 120 частиц
        repeat(120) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 18f + 8f
            particles.add(
                Particle(
                    x            = width / 2f,
                    y            = height / 3f,
                    color        = colors[Random.nextInt(colors.size)],
                    size         = Random.nextFloat() * 14f + 6f,
                    speedX       = speed * cos(Math.toRadians(angle.toDouble())).toFloat(),
                    speedY       = speed * sin(Math.toRadians(angle.toDouble())).toFloat() - 12f,
                    rotation     = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 12f
                )
            )
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                progress = anim.animatedFraction
                updateParticles()
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        particles.clear()
        invalidate()
    }

    private fun updateParticles() {
        particles.forEach { p ->
            p.x     += p.speedX
            p.y     += p.speedY + (progress * 3f)  // гравитация
            p.angle += p.rotationSpeed
            p.alpha  = (1f - progress * 1.2f).coerceIn(0f, 1f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        particles.forEach { p ->
            if (p.alpha <= 0f) return@forEach
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt()

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)

            // Рисуем прямоугольник (конфетти)
            canvas.drawRect(
                -p.size / 2f, -p.size / 4f,
                p.size / 2f, p.size / 4f,
                paint
            )
            canvas.restore()
        }
    }
}
