package com.eyeguard.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.eyeguard.app.R

class PermissionsActivity : AppCompatActivity() {

    private data class PermItem(
        val id: String,
        val emoji: String,
        val title: String,
        val reason: String,
        var statusView: TextView? = null,
        var actionBtn: TextView? = null,
        var cardView: View? = null
    )

    private lateinit var permItems: List<PermItem>
    private lateinit var tvSummary: TextView

    private val requestCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateAll() }

    private val requestNotifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateAll() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permItems = listOf(
            PermItem("camera",       "📷", getString(R.string.perm_camera_title),       getString(R.string.perm_camera_reason)),
            PermItem("overlay",      "🪟", getString(R.string.perm_overlay_title),      getString(R.string.perm_overlay_reason)),
            PermItem("notification", "🔔", getString(R.string.perm_notification_title), getString(R.string.perm_notification_reason)),
        )

        setContentView(buildUi())
        supportActionBar?.title = getString(R.string.title_permissions)
        updateAll()
    }

    override fun onResume() {
        super.onResume()
        updateAll()
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

        root.addView(TextView(this).apply {
            text = "🔒  ${getString(R.string.title_permissions)}"
            textSize = 24f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(4)) }
        })

        tvSummary = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setSingleLine(false)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(color(R.color.bg_card_teal))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(16)) }
        }
        root.addView(tvSummary)

        // Permission cards
        permItems.forEach { item ->
            root.addView(buildPermCard(item))
            root.addView(spacer(dp(10)))
        }

        // ── MIUI / HyperOS instruction section ───────────────────
        root.addView(spacer(dp(16)))
        root.addView(buildInstructionSection())

        // ── General disclaimer ────────────────────────────────────
        root.addView(spacer(dp(16)))
        root.addView(buildDisclaimerCard())
        root.addView(spacer(dp(24)))

        scroll.addView(root)
        return scroll
    }

    private fun buildPermCard(item: PermItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(color(R.color.bg_card_pink))
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        item.cardView = card

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        titleRow.addView(TextView(this).apply {
            text = item.emoji; textSize = 26f; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(12), 0) }
        })
        titleRow.addView(TextView(this).apply {
            text = item.title; textSize = 16f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val tvStatus = TextView(this).apply {
            textSize = 22f; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        }
        item.statusView = tvStatus
        titleRow.addView(tvStatus)

        val tvReason = TextView(this).apply {
            text = item.reason; textSize = 13f
            setTextColor(color(R.color.text_secondary)); setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(38), dp(6), 0, dp(12)) }
        }

        val btnAction = TextView(this).apply {
            text = "  ${getString(R.string.btn_grant)}  "; textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(color(R.color.coral_accent)); cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { handlePermissionAction(item.id) }
        }
        item.actionBtn = btnAction

        card.addView(titleRow)
        card.addView(tvReason)
        card.addView(btnAction)
        return card
    }

    // ─────────────────────────────────────────────────────────────
    // MIUI / HyperOS instruction section
    // ─────────────────────────────────────────────────────────────

    private fun buildInstructionSection(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF0F8FF"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), color(R.color.teal_primary))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Section header
        card.addView(TextView(this).apply {
            text = "📋  ${getString(R.string.perm_instruction_title)}"
            textSize = 17f
            setTextColor(color(R.color.teal_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = fullWidth().apply { setMargins(0, 0, 0, dp(10)) }
        })

        // Disclaimer about MIUI/HyperOS
        card.addView(buildInfoChip(getString(R.string.perm_instruction_disclaimer)))
        card.addView(spacer(dp(12)))

        // Step cards
        listOf(
            R.string.perm_instruction_1_title to R.string.perm_instruction_1_body,
            R.string.perm_instruction_2_title to R.string.perm_instruction_2_body,
            R.string.perm_instruction_3_title to R.string.perm_instruction_3_body,
            R.string.perm_instruction_4_title to R.string.perm_instruction_4_body
        ).forEachIndexed { idx, (titleRes, bodyRes) ->
            if (idx > 0) card.addView(spacer(dp(10)))
            card.addView(buildStepCard(getString(titleRes), getString(bodyRes)))
        }

        return card
    }

    private fun buildStepCard(title: String, body: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(1).toFloat()
            layoutParams = fullWidth()
        }
        row.addView(TextView(this).apply {
            text = title; textSize = 14f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = fullWidth().apply { setMargins(0, 0, 0, dp(4)) }
        })
        row.addView(TextView(this).apply {
            text = body; textSize = 13f
            setTextColor(color(R.color.text_secondary))
            setSingleLine(false)
            layoutParams = fullWidth()
        })
        return row
    }

    // ─────────────────────────────────────────────────────────────
    // General disclaimer
    // ─────────────────────────────────────────────────────────────

    private fun buildDisclaimerCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF8E6"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), color(R.color.orange_caution))
            }
            elevation = dp(2).toFloat()
            layoutParams = fullWidth()
        }
        card.addView(TextView(this).apply {
            text = "⚠️  ${getString(R.string.perm_general_disclaimer_title)}"
            textSize = 15f
            setTextColor(color(R.color.orange_caution))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = fullWidth().apply { setMargins(0, 0, 0, dp(8)) }
        })
        card.addView(TextView(this).apply {
            text = getString(R.string.perm_general_disclaimer_body)
            textSize = 13f
            setTextColor(color(R.color.text_secondary))
            setSingleLine(false)
            layoutParams = fullWidth()
        })
        return card
    }

    // ─────────────────────────────────────────────────────────────
    // Permission logic
    // ─────────────────────────────────────────────────────────────

    private fun updateAll() {
        permItems.forEach { updatePermItem(it) }
        val allOk = permItems.all { isGranted(it.id) }
        tvSummary.text = if (allOk) getString(R.string.perm_all_ok) else getString(R.string.perm_not_all)
        tvSummary.setTextColor(
            if (allOk) color(R.color.green_ok) else color(R.color.orange_caution)
        )
    }

    private fun updatePermItem(item: PermItem) {
        val granted = isGranted(item.id)
        item.statusView?.text = if (granted) "✅" else "❌"
        (item.cardView?.background as? GradientDrawable)?.setColor(
            if (granted) color(R.color.bg_card) else color(R.color.bg_card_pink)
        )
        item.actionBtn?.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun isGranted(id: String): Boolean = when (id) {
        "camera" -> ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        "overlay" -> Settings.canDrawOverlays(this)

        "notification" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else true
        else -> false
    }

    private fun handlePermissionAction(id: String) {
        when (id) {
            "camera"       -> requestCameraLauncher.launch(Manifest.permission.CAMERA)
            "overlay"      -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            "notification" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun buildInfoChip(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f
        setTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card_teal)); cornerRadius = dp(10).toFloat()
        }
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setSingleLine(false)
        layoutParams = fullWidth()
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun color(resId: Int) = resources.getColor(resId, theme)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }
}
