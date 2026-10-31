package com.chumbaq.messages.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import androidx.appcompat.view.ContextThemeWrapper
import com.chumbaq.messages.model.InAppMessage
import com.chumbaq.messages.model.MessageButton
import com.chumbaq.messages.model.MessageTheme
import com.chumbaq.messages.ui.InAppMessageDialog.Entrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * The dialog is the half of this module that only a rendered view can check.
 * Two of the things covered here were wrong in a way reading the source does
 * not show: a tapped button never closed the message, and the theme's
 * `borderRadius` and two of the three `imagePosition` values were read off
 * the message and then dropped.
 */
@RunWith(RobolectricTestRunner::class)
class InAppMessageDialogTest {

    private lateinit var context: ContextThemeWrapper
    private val events = mutableListOf<Pair<String, String?>>()
    private val tapped = mutableListOf<MessageButton>()

    @Before
    fun setUp() {
        // The dialog builds an AppCompat AlertDialog, which needs an
        // AppCompat theme; the bare application context has none.
        context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight,
        )
        events.clear()
        tapped.clear()
    }

    private fun message(
        messageType: String = "modal",
        buttons: List<MessageButton>? = null,
        theme: MessageTheme? = null,
        imageUrl: String? = null,
        imagePosition: String = "top",
        dismissOnTapOutside: Boolean = true,
        autoDismissSeconds: Int? = null,
    ) = InAppMessage(
        id = "m1",
        messageType = messageType,
        title = "Welcome",
        body = "Glad you are here",
        imageUrl = imageUrl,
        imagePosition = imagePosition,
        buttons = buttons,
        theme = theme,
        dismissOnTapOutside = dismissOnTapOutside,
        autoDismissSeconds = autoDismissSeconds,
        triggerDelay = 0,
    )

    /** The dialog, unshown — for the parts testable without a window. */
    private fun dialog(message: InAppMessage) = InAppMessageDialog(
        context = context,
        message = message,
        onEvent = { type, buttonId -> events.add(type to buttonId) },
        onButtonAction = { tapped.add(it) },
    )

    private fun show(message: InAppMessage) = InAppMessageDialog(
        context = context,
        message = message,
        onEvent = { type, buttonId -> events.add(type to buttonId) },
        onButtonAction = { tapped.add(it) },
    ).also {
        it.show()
        // `Dialog.show()` posts its show listener rather than calling it, so
        // the impression lands a tick later on a device too.
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun themeWith(animation: String?) = MessageTheme(
        backgroundColor = null, textColor = null, titleColor = null,
        buttonPrimaryColor = null, buttonSecondaryColor = null,
        overlayColor = null, borderRadius = null, animation = animation,
    )

    private fun button(id: String, action: String, style: String = "primary") =
        MessageButton(id = id, text = id, action = action, actionUrl = null, actionEvent = null, style = style)

    private fun shownDialog() = ShadowDialog.getLatestDialog()

    private fun View.descendants(): List<View> = when (this) {
        is ViewGroup -> (0 until childCount).flatMap { listOf(getChildAt(it)) + getChildAt(it).descendants() }
        else -> emptyList()
    }

    private fun contentOf(dialog: Dialog): List<View> =
        dialog.window!!.decorView.descendants()

    /**
     * The message's own content root, found through the title rather than by
     * position: the dialog containers wrap it in layouts of their own, and
     * several of those are `LinearLayout`s too.
     */
    private fun rootOf(dialog: Dialog): LinearLayout =
        contentOf(dialog)
            .filterIsInstance<TextView>()
            .first { it.text == "Welcome" }
            .parent as LinearLayout

    // ── A tapped button ends the message ───────────────────────────

    @Test
    fun `tapping a button closes the message`() {
        show(message(buttons = listOf(button("ok", "dismiss"))))
        val dialog = shownDialog()!!
        assertTrue(dialog.isShowing)

        contentOf(dialog).filterIsInstance<Button>().first { it.text == "ok" }.performClick()
        idle()

        assertEquals(listOf(button("ok", "dismiss")), tapped)
        // The whole point: a `dismiss` button used to do literally nothing.
        assertTrue("the message should be gone", !dialog.isShowing)
    }

    @Test
    fun `a deep-link button closes the message too`() {
        show(message(buttons = listOf(button("open", "deep_link"))))
        val dialog = shownDialog()!!

        contentOf(dialog).filterIsInstance<Button>().first().performClick()
        idle()

        // Otherwise the message is still sitting over the app when the user
        // comes back from wherever the link took them.
        assertTrue(!dialog.isShowing)
    }

    @Test
    fun `a tap is not also counted as a dismissal`() {
        show(message(buttons = listOf(button("ok", "dismiss"))))
        contentOf(shownDialog()!!).filterIsInstance<Button>().first().performClick()
        idle()

        // `dismissRate` is dismissals over impressions, so a CTA counted as
        // a dismissal makes every well-performing message look rejected.
        assertEquals(listOf("impression"), events.map { it.first })
    }

    @Test
    fun `closing it by hand is counted as a dismissal`() {
        show(message())
        shownDialog()!!.dismiss()
        idle()

        assertEquals(listOf("impression", "dismiss"), events.map { it.first })
    }

    // ── Layouts ────────────────────────────────────────────────────

    @Test
    fun `every message type reaches the screen`() {
        for (type in listOf("modal", "fullscreen", "bottom_sheet", "banner_top", "banner_bottom")) {
            events.clear()
            show(message(messageType = type))
            assertNotNull("$type showed nothing", shownDialog())
            assertTrue("$type showed nothing", shownDialog()!!.isShowing)
            assertEquals("$type reported no impression", listOf("impression"), events.map { it.first })
            shownDialog()!!.dismiss()
            idle()
        }
    }

    // ── Image positions ────────────────────────────────────────────

    @Test
    fun `an image at the top sits above the title`() {
        show(message(imageUrl = "https://cdn.test/i.png", imagePosition = "top"))
        val root = rootOf(shownDialog()!!)

        assertTrue(root.getChildAt(0) is ImageView)
    }

    @Test
    fun `an image in the centre sits after the copy`() {
        show(message(imageUrl = "https://cdn.test/i.png", imagePosition = "center"))
        val root = rootOf(shownDialog()!!)

        // `center` used to render nothing at all: the only branch here
        // tested for `top`, so the picture was silently dropped.
        assertTrue("no image was rendered", root.descendants().any { it is ImageView })
        // After the copy, which is what distinguishes it from `top`.
        assertTrue(root.getChildAt(0) !is ImageView)
        assertTrue(root.getChildAt(root.childCount - 1) is ImageView)
    }

    @Test
    fun `an image as the background goes behind the copy`() {
        show(message(imageUrl = "https://cdn.test/i.png", imagePosition = "background"))
        val root = rootOf(shownDialog()!!)

        // The copy's root is wrapped in a frame: picture, scrim, copy.
        val frame = root.parent as? FrameLayout
        assertNotNull("the image should be behind the copy, not dropped", frame)
        assertEquals(3, frame!!.childCount)
        assertTrue(frame.getChildAt(0) is ImageView)
        // The copy draws last, so it is on top of the picture.
        assertEquals(root, frame.getChildAt(2))
    }

    @Test
    fun `no image renders no image`() {
        show(message(imageUrl = null))

        assertTrue(rootOf(shownDialog()!!).descendants().none { it is ImageView })
    }

    // ── Theme ──────────────────────────────────────────────────────

    @Test
    fun `the theme's background is a shape that can carry a corner radius`() {
        show(message(theme = MessageTheme(
            backgroundColor = "#101828",
            textColor = null, titleColor = null,
            buttonPrimaryColor = null, buttonSecondaryColor = null,
            overlayColor = null, borderRadius = 24, animation = null,
        )))
        val root = rootOf(shownDialog()!!)

        // `borderRadius` used to be read off the theme into a local and then
        // never used, because the background was a flat colour and a flat
        // colour has no corners to round. The radius itself is not asserted:
        // `GradientDrawable.getCornerRadius()` is API 24 and Robolectric's
        // shadow does not implement it, so the shape is as far as a unit
        // test reaches.
        val background = root.background as GradientDrawable
        assertEquals(Color.parseColor("#101828"), shadowOf(background).lastSetColor)
    }

    @Test
    fun `a nonsense colour falls back instead of throwing`() {
        show(message(theme = MessageTheme(
            backgroundColor = "not a colour",
            textColor = "#", titleColor = "rgb(1,2,3)",
            buttonPrimaryColor = "", buttonSecondaryColor = null,
            overlayColor = null, borderRadius = null, animation = null,
        ), buttons = listOf(button("ok", "dismiss", style = "secondary"))))

        assertTrue(shownDialog()!!.isShowing)
    }

    // ── Entrance animation ─────────────────────────────────────────

    /**
     * `theme.animation` was parsed off the response and then never read, so
     * whichever of the five values the dashboard held, the message appeared
     * the same way.
     *
     * Asserted against a bare view rather than a shown dialog: an entrance
     * is cleared off the view the moment it completes, and under a paused
     * looper it has completed by the time `show()` returns control.
     */
    @Test
    fun `a fade theme animates the view`() {
        val view = View(context)

        dialog(message(theme = themeWith("fade"))).animateIn(view)

        assertTrue(view.animation is AlphaAnimation)
        assertEquals(InAppMessageDialog.ENTRANCE_MS, view.animation.duration)
    }

    @Test
    fun `a slide theme translates the view`() {
        val view = View(context)

        dialog(message(theme = themeWith("slide_up"))).animateIn(view)

        assertTrue(view.animation is TranslateAnimation)
    }

    @Test
    fun `a scale theme scales and fades together`() {
        val view = View(context)

        dialog(message(theme = themeWith("scale"))).animateIn(view)

        assertTrue(view.animation is AnimationSet)
    }

    @Test
    fun `a none theme animates nothing`() {
        val view = View(context)

        dialog(message(theme = themeWith("none"))).animateIn(view)

        assertNull(view.animation)
    }

    @Test
    fun `a theme with no animation animates nothing`() {
        val view = View(context)

        dialog(message(theme = themeWith(null))).animateIn(view)

        assertNull(view.animation)
    }

    /**
     * A campaign saved against a newer dashboard should arrive without an
     * animation rather than with the wrong one.
     */
    @Test
    fun `an unrecognised animation animates nothing`() {
        val view = View(context)

        dialog(message(theme = themeWith("barrel_roll"))).animateIn(view)

        assertNull(view.animation)
    }

    @Test
    fun `every animation the dashboard offers maps to an entrance`() {
        assertEquals(Entrance.FADE, Entrance.of("fade"))
        assertEquals(Entrance.SLIDE_UP, Entrance.of("slide_up"))
        assertEquals(Entrance.SLIDE_DOWN, Entrance.of("slide_down"))
        assertEquals(Entrance.SCALE, Entrance.of("scale"))
        assertEquals(Entrance.NONE, Entrance.of("none"))
        assertEquals(Entrance.NONE, Entrance.of(null))
        assertEquals(Entrance.NONE, Entrance.of("something new"))
    }

    /** The up and the down slide must not resolve to the same entrance. */
    @Test
    fun `slide up and slide down are different entrances`() {
        assertNotEquals(Entrance.of("slide_up"), Entrance.of("slide_down"))
    }

    /** Whatever the entrance, it must survive a real window. */
    @Test
    fun `a message shows with every animation the dashboard offers`() {
        for (animation in listOf("fade", "slide_up", "slide_down", "scale", "none")) {
            show(message(theme = themeWith(animation)))

            assertTrue("$animation should still show", shownDialog()!!.isShowing)
            shownDialog()!!.dismiss()
            idle()
        }
    }

    // ── Auto-dismiss ───────────────────────────────────────────────

    @Test
    fun `an auto-dismissed message is not also counted as a dismissal`() {
        show(message(autoDismissSeconds = 6))
        val dialog = shownDialog()!!

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))

        assertTrue(!dialog.isShowing)
        assertEquals(listOf("impression", "auto_dismiss"), events.map { it.first })
    }

    @Test
    fun `a banner leaves on its own`() {
        show(message(messageType = "banner_top"))
        val dialog = shownDialog()!!

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))

        // A banner has nothing to tap, so nothing would ever close it.
        assertTrue(!dialog.isShowing)
        assertEquals(listOf("impression", "auto_dismiss"), events.map { it.first })
    }

    @Test
    fun `a message with no auto-dismiss stays put`() {
        show(message())
        val dialog = shownDialog()!!

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))

        assertTrue(dialog.isShowing)
        assertNull(events.map { it.first }.firstOrNull { it != "impression" })
    }
}
