package com.dyplink.messages.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Dialog
import androidx.appcompat.app.AlertDialog
import coil.load
import com.dyplink.messages.model.InAppMessage
import com.dyplink.messages.model.MessageButton
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Renders an [InAppMessage] on screen.
 *
 * The container comes from [InAppMessage.messageType]:
 * - `modal` / `fullscreen` → [AlertDialog]
 * - `bottom_sheet` → [BottomSheetDialog]
 * - `banner_top` / `banner_bottom` → lightweight overlay [AlertDialog]
 *
 * The contents come from the rest of the message: the copy, the buttons and
 * their styles, the theme's colours and corner radius, and the picture at
 * whichever of the three [InAppMessage.imagePosition] values the campaign
 * chose. Every one of those is a field the dashboard offers, so anything
 * read and then dropped here is a setting that silently does nothing.
 *
 * Whatever ends the message — a tapped button, a timer, the user — it is
 * reported once and only once. See [closeReason].
 */
internal class InAppMessageDialog(
    private val context: Context,
    private val message: InAppMessage,
    private val onEvent: (eventType: String, buttonId: String?) -> Unit,
    private val onButtonAction: (button: MessageButton) -> Unit,
    /**
     * Runs when the message leaves the screen, whatever took it away.
     *
     * Distinct from [onEvent]: exactly one close *reason* is reported, and
     * a CTA tap reports through [onButtonAction] instead — so no single
     * event tells a caller the screen is free again. [MessageManager] needs
     * that to show the next message.
     */
    private val onClosed: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    /**
     * Why the dialog is closing, when something other than the user closing
     * it is the reason.
     *
     * Android calls the dismiss listener however the dialog goes away, so
     * without this a CTA tap reported `cta_click` *and* `dismiss`, and an
     * auto-dismiss reported `auto_dismiss` *and* `dismiss`. `dismissRate` is
     * dismissals over impressions, so every tap of every button counted
     * against the message.
     */
    private var closeReason: String? = null

    /** The dialog on screen, so a tapped button can close it. */
    private var activeDialog: Dialog? = null

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
            dialog.window?.setLayout(MATCH_PARENT, MATCH_PARENT)
        }

        present(dialog, contentView, autoDismissAfter = message.autoDismissSeconds)
    }

    // ── Bottom Sheet ───────────────────────────────────────────────

    private fun showBottomSheet() {
        val contentView = buildContentView(compact = true)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(contentView)
        dialog.setCancelable(message.dismissOnTapOutside)

        present(dialog, contentView, autoDismissAfter = message.autoDismissSeconds)
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
            setLayout(MATCH_PARENT, WRAP_CONTENT)
        }

        // A banner has no buttons to speak of and sits over the app, so it
        // leaves on its own unless the campaign says otherwise.
        present(
            dialog,
            contentView,
            autoDismissAfter = message.autoDismissSeconds ?: BANNER_AUTO_DISMISS_SECONDS,
        )
    }

    // ── Content View ───────────────────────────────────────────────

    private fun buildContentView(compact: Boolean): View {
        val theme = message.theme
        val bgColor = parseColor(theme?.backgroundColor, Color.WHITE)
        val hasImage = !message.imageUrl.isNullOrBlank()
        val asBackground = hasImage && message.imagePosition == "background"

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // `borderRadius` used to be read off the theme and then dropped:
            // a flat `setBackgroundColor` has no corners to round.
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(theme?.borderRadius ?: DEFAULT_CORNER_RADIUS_DP).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // An image above the copy. `center` places it after the body
        // instead, and `background` puts it behind everything — see below.
        if (hasImage && message.imagePosition == "top") {
            root.addView(imageView(compact))
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
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            }
            val bodyView = TextView(context).apply {
                text = message.body
                textSize = 14f
                setTextColor(parseColor(theme?.textColor, Color.DKGRAY))
            }
            scroll.addView(bodyView)
            root.addView(scroll)
        }

        // `center` is "between the copy and the call to action", which is
        // the only reading that differs from `top` — and it never rendered
        // at all while the only branch here tested for `top`.
        if (hasImage && message.imagePosition == "center") {
            root.addView(imageView(compact))
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
                val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                params.topMargin = dp(8)
                layoutParams = params
                setOnClickListener { onButtonTapped(btn) }
            }
            root.addView(buttonView)
        }

        if (!asBackground) return root

        // `background` fills the card behind the copy. A scrim goes between
        // the two, because the theme's text colours were chosen against the
        // background *colour* and a photograph can swallow either of them.
        return FrameLayout(context).apply {
            addView(
                imageView(compact = false),
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
            )
            addView(
                View(context).apply { setBackgroundColor(parseColor(theme?.overlayColor, BACKGROUND_SCRIM)) },
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
            )
            root.background = null
            addView(root, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(theme?.borderRadius ?: DEFAULT_CORNER_RADIUS_DP).toFloat()
            }
            clipToOutline = true
        }
    }

    /** The message's image, sized for the container it is going into. */
    private fun imageView(compact: Boolean): ImageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                if (compact) dp(120) else dp(200),
            )
            load(message.imageUrl)
        }

    // ── Lifecycle ──────────────────────────────────────────────────

    /**
     * Shows [dialog], reports its impression and dismissal, plays the
     * theme's entrance on [contentView], and arms auto-dismiss where the
     * message asks for one.
     *
     * The dismissal is only reported when nothing else has already explained
     * the close — see [closeReason].
     */
    private fun present(dialog: Dialog, contentView: View, autoDismissAfter: Int?) {
        activeDialog = dialog

        dialog.setOnShowListener {
            onEvent("impression", null)
            // Once the window exists. The message's own card animates, not
            // the decor view: the decor carries the dim scrim behind it, and
            // fading that in with the card is a different effect entirely.
            animateIn(contentView)
        }
        dialog.setOnDismissListener {
            cancelAutoDismiss()
            activeDialog = null
            if (closeReason == null) onEvent("dismiss", null)
            onClosed()
        }

        dialog.show()

        val seconds = autoDismissAfter ?: return
        autoDismissRunnable = Runnable { close("auto_dismiss") }
            .also { handler.postDelayed(it, seconds * 1000L) }
    }

    /** Closes the message for a reason that is not the user dismissing it. */
    private fun close(reason: String) {
        val dialog = activeDialog ?: return
        closeReason = reason
        if (reason == "auto_dismiss") onEvent(reason, null)
        dialog.dismiss()
    }

    /**
     * A tapped button ends the message.
     *
     * Nothing used to close it: a button whose action is `dismiss` did
     * literally nothing, and a deep-link button opened its destination and
     * left the message sitting over the app for the user to come back to.
     * The handler that owns the action runs first, so a deep link is already
     * on its way before the dialog goes.
     */
    private fun onButtonTapped(button: MessageButton) {
        onButtonAction(button)
        close("cta_click")
    }

    // ── Entrance ───────────────────────────────────────────────────

    /**
     * Plays the entrance the theme's `animation` asks for on [view].
     *
     * `animation` was parsed off the theme and then dropped, so the
     * dashboard's animation setting did nothing whichever value it held.
     *
     * A view [Animation] rather than a window animation style, because a
     * window style has to come from a resource and the three containers
     * here each build their window differently — the view is the one thing
     * they share. Relative offsets for the same reason the duration is
     * fixed: the view has not been measured yet when this runs, so there is
     * no height to translate by in pixels.
     */
    internal fun animateIn(view: View) {
        animationFor(Entrance.of(message.theme?.animation))
            ?.let { view.startAnimation(it) }
    }

    /**
     * The entrances the dashboard offers, resolved from the raw string so
     * the mapping can be tested without a window.
     *
     * An unrecognised value is [NONE] rather than a default animation: a
     * campaign saved against a newer dashboard should arrive without one,
     * not with the wrong one.
     */
    internal enum class Entrance {
        NONE,
        FADE,
        SLIDE_UP,
        SLIDE_DOWN,
        SCALE,
        ;

        internal companion object {
            internal fun of(animation: String?): Entrance = when (animation) {
                "fade" -> FADE
                "slide_up" -> SLIDE_UP
                "slide_down" -> SLIDE_DOWN
                "scale" -> SCALE
                else -> NONE
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

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

    internal companion object {
        const val BANNER_AUTO_DISMISS_SECONDS = 4
        const val DEFAULT_CORNER_RADIUS_DP = 12

        /** Short enough not to delay a message the user triggered. */
        const val ENTRANCE_MS = 220L

        /** Where `scale` starts: a nudge, not a zoom. */
        const val SCALE_FROM = 0.9f

        /** The [Animation] an [Entrance] plays, or `null` for [Entrance.NONE]. */
        internal fun animationFor(entrance: Entrance): Animation? = when (entrance) {
            Entrance.NONE -> null
            Entrance.FADE -> AlphaAnimation(0f, 1f)
            Entrance.SLIDE_UP -> slide(fromYDelta = 1f)
            Entrance.SLIDE_DOWN -> slide(fromYDelta = -1f)
            Entrance.SCALE -> AnimationSet(true).apply {
                addAnimation(AlphaAnimation(0f, 1f))
                addAnimation(
                    ScaleAnimation(
                        SCALE_FROM, 1f, SCALE_FROM, 1f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                    ),
                )
            }
        }?.apply { duration = ENTRANCE_MS }

        /** Travels [fromYDelta] of its own height to rest. */
        private fun slide(fromYDelta: Float): Animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, fromYDelta,
            Animation.RELATIVE_TO_SELF, 0f,
        )

        /** Black at 40%, dark enough for white copy over a bright photo. */
        const val BACKGROUND_SCRIM = 0x66000000.toInt()

        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
