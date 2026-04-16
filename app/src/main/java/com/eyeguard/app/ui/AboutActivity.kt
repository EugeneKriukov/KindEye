package com.eyeguard.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.eyeguard.app.R

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.title_about)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.bg_main))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(64))
            setBackgroundColor(color(R.color.bg_main))
        }

        // ── Header ────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "👤  ${getString(R.string.about_author_header)}"
            textSize = 24f
            setTextColor(color(R.color.teal_primary))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = fullWidthParams().apply { setMargins(0, dp(8), 0, dp(20)) }
        })

        // ── LinkedIn card ─────────────────────────────────────────
        root.addView(sectionHeader("🔗", getString(R.string.about_linkedin_title)))
        root.addView(spacer(dp(8)))
        root.addView(buildClickCard(
            emoji      = "💼",
            title      = "Eugene Gk",
            subtitle   = "linkedin.com/in/eugene-gk",
            bgColor    = color(R.color.bg_card_teal),
            accentColor = Color.parseColor("#0A66C2")
        ) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/eugene-gk"))
            )
        })

        root.addView(spacer(dp(24)))

        // ── Donate section ────────────────────────────────────────
        root.addView(sectionHeader("💰", getString(R.string.about_donate_title)))
        root.addView(spacer(dp(8)))
        root.addView(buildHintCard(getString(R.string.about_donate_hint)))
        root.addView(spacer(dp(12)))

        // ETH
        root.addView(buildCryptoCard(
            network = "Ethereum (ETH)",
            emoji   = "⟠",
            address = "0x1aeF63aAd1A5191Cba292dF18B2f001788e6651F",
            bgColor = Color.parseColor("#F0F0FF"),
            accentColor = Color.parseColor("#627EEA"),
            toastMsg = getString(R.string.about_copied_eth)
        ))
        root.addView(spacer(dp(10)))

        // SOL
        root.addView(buildCryptoCard(
            network = "Solana (SOL)",
            emoji   = "◎",
            address = "Bj89QJQ7mkEQXaxs1cHxYaZy9Pjeb8CFwHNzztQCc1qz",
            bgColor = Color.parseColor("#F0FFF4"),
            accentColor = Color.parseColor("#9945FF"),
            toastMsg = getString(R.string.about_copied_sol)
        ))
        root.addView(spacer(dp(10)))

        // TRON
        root.addView(buildCryptoCard(
            network = "TRON (TRX)",
            emoji   = "⬡",
            address = "TJuEirngqtVPHuB3WyuWVXkczVMAZCK15C",
            bgColor = Color.parseColor("#FFF8F0"),
            accentColor = Color.parseColor("#EF0027"),
            toastMsg = getString(R.string.about_copied_trx)
        ))

        root.addView(spacer(dp(24)))

        // ── App info footer ───────────────────────────────────────
        root.addView(buildHintCard(getString(R.string.about_free_note)))

        scroll.addView(root)
        return scroll
    }

    // ─────────────────────────────────────────────────────────────
    // Cards
    // ─────────────────────────────────────────────────────────────

    /** Tappable card for LinkedIn link */
    private fun buildClickCard(
        emoji: String,
        title: String,
        subtitle: String,
        bgColor: Int,
        accentColor: Int,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), accentColor)
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(4).toFloat()
            layoutParams = fullWidthParams()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        card.addView(TextView(this).apply {
            text = emoji
            textSize = 36f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(16), 0) }
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
        })
        textCol.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(accentColor)
            setSingleLine(false)
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(textCol)

        // Arrow indicator
        card.addView(TextView(this).apply {
            text = "→"
            textSize = 20f
            setTextColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        })

        return card
    }

    /** Crypto address card — address copies to clipboard on tap */
    private fun buildCryptoCard(
        network: String,
        emoji: String,
        address: String,
        bgColor: Int,
        accentColor: Int,
        toastMsg: String
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), accentColor)
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(3).toFloat()
            layoutParams = fullWidthParams()
        }

        // Network header row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = fullWidthParams()
        }
        headerRow.addView(TextView(this).apply {
            text = emoji
            textSize = 26f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(10), 0) }
        })
        headerRow.addView(TextView(this).apply {
            text = network
            textSize = 16f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        card.addView(headerRow)
        card.addView(spacer(dp(10)))

        // Address box — tap to copy
        val addressBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18000000"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = fullWidthParams()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                copyToClipboard(network, address)
                Toast.makeText(this@AboutActivity, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }

        addressBox.addView(TextView(this).apply {
            text = address
            textSize = 12f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.DEFAULT
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        addressBox.addView(TextView(this).apply {
            text = "📋"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        })

        card.addView(addressBox)

        card.addView(TextView(this).apply {
            text = getString(R.string.about_tap_to_copy)
            textSize = 11f
            setTextColor(accentColor)
            setPadding(0, dp(6), 0, 0)
        })

        return card
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun sectionHeader(emoji: String, title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = fullWidthParams().apply { setMargins(0, dp(4), 0, dp(4)) }
        addView(View(context).apply {
            setBackgroundColor(color(R.color.teal_primary))
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, dp(3), dp(12), dp(3)) }
        })
        addView(TextView(context).apply {
            text = "$emoji  $title"
            textSize = 18f
            setTextColor(color(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(false)
            layoutParams = fullWidthParams()
        })
    }

    private fun buildHintCard(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.text_secondary))
        background = GradientDrawable().apply {
            setColor(color(R.color.bg_card_teal))
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setSingleLine(false)
        layoutParams = fullWidthParams()
    }

    private fun fullWidthParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun color(resId: Int) = resources.getColor(resId, theme)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
