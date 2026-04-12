package com.dyplink.messages.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.dyplink.messages.model.InAppMessage
import com.dyplink.messages.model.MessageButton
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Renders an [InAppMessage] on screen.
 *
 * Chooses the appropriate container based on [InAppMessage.messageType]:
 * - `modal` / `fullscreen` → [AlertDialog]
 * - `bottom_sheet` → [BottomSheetDialog]
 * - `banner_top` / `banner_bottom` → lightweight overlay [AlertDialog]
 */
internal class InAppMessageDialog(
    private val context: Context,
    private val message: InAppMessage,
    private val onEvent: (eventType: String, buttonId: String?) -> Unit,
    private val onButtonAction: (button: MessageButton) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    fun show() {
        when (message.messageType) {
            "bottom_sheet" -> showBottomSheet()
            "banner_top", "banner_bottom" -> showBanner()
            else -> showModal()
        }
    }

    // ── Modal / Fullscreen ─────────────────────────────────────────

    private fun showModal() {
        val contentView = buildContentView(compact = false)
        val dialog = AlertDialog.Builder(context)
            .setView(contentView)
            .setCancelable(message.dismissOnTapOutside)
            .create()

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (message.messageType == "fullscreen") {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        dialog.setOnShowListener { onEvent("impression", null) }
        dialog.setOnDismissListener {
            cancelAutoDismiss()
            onEvent("dismiss", null)
        }

        dialog.show()
        scheduleAutoDismiss { dialog.dismiss() }
    }

    // ── Bottom Sheet ───────────────────────────────────────────────

    private fun showBottomSheet() {
        val contentView = buildContentView(compact = true)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(contentView)
        dialog.setCancelable(message.dismissOnTapOutside)

        dialog.setOnShowListener { onEvent("impression", null) }
        dialog.setOnDismissListener {
            cancelAutoDismiss()
            onEvent("dismiss", null)
        }

        dialog.show()
        scheduleAutoDismiss { dialog.dismiss() }
    }

    // ── Banner ─────────────────────────────────────────────────────

    private fun showBanner() {
        val contentView = buildContentView(compact = true)
        val dialog = AlertDialog.Builder(context)
            .setView(contentView)
            .setCancelable(message.dismissOnTapOutside)
            .create()

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val gravity = if (message.messageType == "banner_top") Gravity.TOP else Gravity.BOTTOM
            setGravity(gravity)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialog.setOnShowListener { onEvent("impression", null) }
        dialog.setOnDismissListener {
            cancelAutoDismiss()
            onEvent("dismiss", null)
        }

        dialog.show()
        // Banners auto-dismiss after 4s unless overridden
        val delay = message.autoDismissSeconds ?: 4
        scheduleAutoDismiss(delay) { dialog.dismiss() }
    }

    // ── Content View ───────────────────────────────────────────────

    private fun buildContentView(compact: Boolean): View {
        val theme = message.theme
        val bgColor = parseColor(theme?.backgroundColor, Color.WHITE)
        val radius = (theme?.borderRadius ?: 12).toFloat()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Image
        if (!message.imageUrl.isNullOrBlank() && message.imagePosition != "background") {
            val imageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    if (compact) dp(120) else dp(200),
                )
            }
            imageView.load(message.imageUrl)
            root.addView(imageView)
        }

        // Title
        if (message.title.isNotBlank()) {
            val titleView = TextView(context).apply {
                text = message.title
                textSize = if (compact) 16f else 20f
                setTextColor(parseColor(theme?.titleColor, Color.BLACK))
                setPadding(0, dp(8), 0, dp(4))
            }
            root.addView(titleView)
        }

        // Body
        if (!message.body.isNullOrBlank()) {
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            }
            val bodyView = TextView(context).apply {
                text = message.body
                textSize = 14f
                setTextColor(parseColor(theme?.textColor, Color.DKGRAY))
            }
            scroll.addView(bodyView)
            root.addView(scroll)
        }

        // Buttons
        message.buttons?.forEach { btn ->
            val buttonView = Button(context).apply {
                text = btn.text
                val btnColor = when (btn.style) {
                    "secondary" -> parseColor(theme?.buttonSecondaryColor, Color.LTGRAY)
                    "text" -> Color.TRANSPARENT
                    else -> parseColor(theme?.buttonPrimaryColor, Color.parseColor("#1976D2"))
                }
                setBackgroundColor(btnColor)
                setTextColor(if (btn.style == "text") Color.parseColor("#1976D2") else Color.WHITE)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                params.topMargin = dp(8)
                layoutParams = params
                setOnClickListener { onButtonAction(btn) }
            }
            root.addView(buttonView)
        }

        return root
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun scheduleAutoDismiss(delaySecs: Int? = message.autoDismissSeconds, action: () -> Unit) {
        val effectiveDelaySecs = delaySecs ?: return
        autoDismissRunnable = Runnable {
            onEvent("auto_dismiss", null)
            action()
        }.also { handler.postDelayed(it, effectiveDelaySecs * 1000L) }
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
    }

    private fun parseColor(hex: String?, fallback: Int): Int = try {
        if (hex.isNullOrBlank()) fallback else Color.parseColor(hex)
    } catch (_: IllegalArgumentException) {
        fallback
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
