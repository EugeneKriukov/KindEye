package com.eyeguard.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.eyeguard.app.R

class WHOInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.title_who_info)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg_main))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(64))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(tv(this).apply {
            text = "🏥  ${getString(R.string.who_title)}"
            textSize = 24f
            setTextColor(color(R.color.teal_primary))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            setPadding(0, dp(8), 0, dp(20))
        })

        // ── 20-20-20 ──
        root.addView(sectionHeader("⏱", getString(R.string.who_rule_title)))
        root.addView(Rule202020View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160))
                .apply { setMargins(0, dp(8), 0, dp(8)) }
        })
        root.addView(infoCard(getString(R.string.who_rule_desc), color(R.color.bg_card_teal)))
        root.addView(spacer(dp(20)))

        // ── Recommended Distances ──
        root.addView(sectionHeader("📏", getString(R.string.who_distance_title)))
        root.addView(spacer(dp(8)))
        root.addView(hintCard(getString(R.string.who_distance_note)))
        root.addView(spacer(dp(10)))

        val distRows = listOf(
            Triple("📱", getString(R.string.who_dist_phone),        getString(R.string.who_dist_phone_val)),
            Triple("📱", getString(R.string.who_dist_tablet_small), getString(R.string.who_dist_tablet_small_val)),
            Triple("📱", getString(R.string.who_dist_tablet_large), getString(R.string.who_dist_tablet_large_val)),
            Triple("💻", getString(R.string.who_dist_laptop),       getString(R.string.who_dist_laptop_val)),
            Triple("🖥", getString(R.string.who_dist_monitor),      getString(R.string.who_dist_monitor_val))
        )
        root.addView(buildDistanceTable(distRows))
        root.addView(spacer(dp(20)))

        // ── Screen Time by Age ──
        root.addView(sectionHeader("👶", getString(R.string.who_age_title)))
        root.addView(spacer(dp(8)))

        val ageRows = listOf(
            Triple(getString(R.string.who_age_0),  "❌", getString(R.string.who_age_0_norm)),
            Triple(getString(R.string.who_age_2),  "⏱",  getString(R.string.who_age_2_norm)),
            Triple(getString(R.string.who_age_6),  "⏱⏱", getString(R.string.who_age_6_norm)),
            Triple(getString(R.string.who_age_12), "✅",  getString(R.string.who_age_12_norm))
        )
        root.addView(buildAgeTable(ageRows))
        root.addView(spacer(dp(8)))
        root.addView(hintCard(getString(R.string.who_source)))
        root.addView(spacer(dp(20)))

        // ── Tips ──
        root.addView(sectionHeader("💡", getString(R.string.who_tips_title)))
        root.addView(spacer(dp(8)))

        listOf(
            "🌅" to getString(R.string.who_tip_1),
            "🪑" to getString(R.string.who_tip_2),
            "🌿" to getString(R.string.who_tip_3),
            "😴" to getString(R.string.who_tip_4),
            "🔆" to getString(R.string.who_tip_5)
        ).forEach { (emoji, text) ->
            root.addView(tipRow(emoji, text))
            root.addView(spacer(dp(6)))
        }

        scroll.addView(root)
        return scroll
    }

    // ─────────────────────────────────────────────────────────────
    // Distance table
    // ─────────────────────────────────────────────────────────────

    private fun buildDistanceTable(rows: List<Triple<String, String, String>>): View {
        val isRu = prefs_lang() == "ru-RU"

        val card = tableCard()

        // Header
        card.addView(tableRow(
            col1 = if (isRu) "Устройство" else "Device",
            col2 = if (isRu) "Расстояние" else "Distance",
            isHeader = true
        ))

        rows.forEachIndexed { i, (emoji, name, value) ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (i % 2 == 0) Color.parseColor("#F7FFFE") else Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowLayout.addView(tv(this).apply {
                text = "$emoji  $name"
                textSize = 14f
                setTextColor(color(R.color.text_primary))
                setSingleLine(false)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            rowLayout.addView(tv(this).apply {
                text = value
                textSize = 13f
                setTextColor(color(R.color.teal_dark))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setSingleLine(false)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            card.addView(rowLayout)
        }
        return card
    }

    // ─────────────────────────────────────────────────────────────
    // Age table
    // ─────────────────────────────────────────────────────────────

    private fun buildAgeTable(rows: List<Triple<String, String, String>>): View {
        val isRu = prefs_lang() == "ru-RU"

        val card = tableCard()

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color(R.color.teal_primary))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(tv(this).apply {
            text = if (isRu) "Возраст" else "Age"
            textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        })
        header.addView(tv(this).apply {
            text = if (isRu) "Норма" else "Limit"
            textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.END
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        })
        card.addView(header)

        rows.forEachIndexed { i, (age, icon, norm) ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (i % 2 == 0) Color.parseColor("#F7FFFE") else Color.WHITE)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowLayout.addView(tv(this).apply {
                text = age; textSize = 14f
                setTextColor(color(R.color.text_primary))
                setSingleLine(false)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            rowLayout.addView(tv(this).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            rowLayout.addView(tv(this).apply {
                text = norm; textSize = 14f
                setTextColor(color(R.color.text_primary))
                gravity = Gravity.END
                setSingleLine(false)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            card.addView(rowLayout)
        }
        return card
    }

    // ─────────────────────────────────────────────────────────────
    // Shared table helpers
    // ─────────────────────────────────────────────────────────────

    private fun tableCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card))
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun tableRow(col1: String, col2: String, isHeader: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (isHeader) color(R.color.teal_primary) else Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        fun cell(text: String, flex: Float, align: Int = Gravity.START) = tv(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (isHeader) Color.WHITE else color(R.color.text_primary))
            if (isHeader) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = align
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, flex)
        }
        row.addView(cell(col1, 2f))
        row.addView(cell(col2, 1f, Gravity.END))
        return row
    }

    // ─────────────────────────────────────────────────────────────
    // Widget builders
    // ─────────────────────────────────────────────────────────────

    private fun sectionHeader(emoji: String, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(4), 0, dp(4)) }
        addView(View(context).apply {
            setBackgroundColor(color(R.color.teal_primary))
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, dp(3), dp(12), dp(3)) }
        })
        addView(tv(context).apply {
            text = "$emoji  $title"
            textSize = 18f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun infoCard(body: String, bgColor: Int) = tv(this).apply {
        text = body
        textSize = 14f
        setTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply { setColor(bgColor); cornerRadius = dp(14).toFloat() }
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setSingleLine(false)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun hintCard(text: String) = tv(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply { setColor(color(R.color.bg_card_teal)); cornerRadius = dp(10).toFloat() }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setSingleLine(false)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun tipRow(emoji: String, text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addView(tv(context).apply {
            this.text = emoji; textSize = 22f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, dp(14), 0) }
        })
        addView(tv(context).apply {
            this.text = text; textSize = 14f
            setTextColor(color(R.color.text_secondary))
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun prefs_lang(): String {
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        return p.getString("tts_language", "en-US") ?: "en-US"
    }

    private fun tv(ctx: android.content.Context) = TextView(ctx)
    private fun color(resId: Int) = resources.getColor(resId, theme)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ─────────────────────────────────────────────────────────────────
// Rule 20-20-20 custom view
// ─────────────────────────────────────────────────────────────────

class Rule202020View @JvmOverloads constructor(
    context: android.content.Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8FAF9") }
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5568"); textAlign = Paint.Align.CENTER
    }
    private val paintArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        color = Color.parseColor("#A8E6CF"); strokeCap = Paint.Cap.ROUND
    }

    private val circleColors = intArrayOf(
        Color.parseColor("#4ECDC4"), Color.parseColor("#FF6B9D"), Color.parseColor("#48BB78")
    )
    private val emojis = listOf("📱", "👁", "🌿")

    private fun labels(): List<List<String>> {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val isRu = (prefs.getString("tts_language", "en-US") ?: "en-US") == "ru-RU"
        return if (isRu) listOf(listOf("минут"), listOf("секунд"), listOf("футов", "(6 метров)"))
        else listOf(listOf("minutes"), listOf("seconds"), listOf("feet", "(6 meters)"))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), dp(16f), dp(16f), paintBg)

        val r = h * 0.27f; val cy = h * 0.44f
        val xs = listOf(w * 0.18f, w * 0.5f, w * 0.82f)
        val lbls = labels()

        xs.forEachIndexed { i, cx ->
            paintCircle.color = circleColors[i]
            canvas.drawCircle(cx, cy, r, paintCircle)
            paintNum.textSize = r * 0.62f
            canvas.drawText(emojis[i], cx, cy - r * 0.05f, paintNum)
            paintNum.color = Color.WHITE; paintNum.textSize = r * 0.52f
            canvas.drawText("20", cx, cy + r * 0.68f, paintNum)
            paintLabel.textSize = dp(10.5f)
            lbls[i].forEachIndexed { li, line ->
                canvas.drawText(line, cx, cy + r + dp(18f) + li * dp(14f), paintLabel)
            }
            if (i < xs.lastIndex) {
                val x1 = cx + r + dp(5f); val x2 = xs[i + 1] - r - dp(5f)
                canvas.drawLine(x1, cy, x2, cy, paintArrow)
                canvas.drawLine(x2, cy, x2 - dp(8f), cy - dp(5f), paintArrow)
                canvas.drawLine(x2, cy, x2 - dp(8f), cy + dp(5f), paintArrow)
            }
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
