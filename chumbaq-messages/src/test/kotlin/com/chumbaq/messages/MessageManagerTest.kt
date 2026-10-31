package com.chumbaq.messages

import android.app.Activity
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.chumbaq.messages.internal.MessageApiClient
import com.chumbaq.messages.internal.MessageCheckContext
import com.chumbaq.messages.internal.MessageEventTracker
import com.chumbaq.messages.model.InAppMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * Covers the two defects in how a check's result reaches the screen.
 *
 * The server returns up to three eligible messages, sorted priority
 * descending. They used to be handed to `postDelayed` in a `forEach`, under
 * a comment reading "one at a time (top priority first)", which produced
 * neither: all three were posted, so they stacked with the *lowest* priority
 * drawn last and therefore on top, and three impressions were reported for
 * one check. The same `forEach` captured the calling Activity and showed
 * against it after the delay, which throws `BadTokenException` once that
 * Activity has gone — a crash in the host app, since the trigger points
 * only guard the check.
 */
@RunWith(RobolectricTestRunner::class)
class MessageManagerTest {

    private lateinit var apiClient: MessageApiClient
    private lateinit var activity: Activity

    /** Every event the manager reported, in order. */
    private val reported = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        reported.clear()
        apiClient = mockk()
        coEvery {
            apiClient.recordEvent(any(), any(), any(), any(), any(), any())
        } answers {
            reported.add(firstArg<String>() to arg<String>(4))
        }

        // The dialog builds an AppCompat AlertDialog, which needs an
        // AppCompat theme; a bare Robolectric Activity has none.
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
    }

    @After
    fun tearDown() {
        ShadowDialog.reset()
    }

    // ── One at a time, highest priority first ──────────────────────

    /**
     * All three used to be on screen at once. Only the first of the batch —
     * the highest priority, since that is the order the server returns them
     * in — may be showing after a check.
     */
    @Test
    fun `only the highest priority message is shown`() = runTest {
        givenEligible(message("high"), message("middle"), message("low"))

        manager().checkAndShow(MessageCheckContext())
        idle()

        assertEquals("high", shownTitle())
    }

    @Test
    fun `the next message waits for the first to close`() = runTest {
        givenEligible(message("high"), message("middle"))

        manager().checkAndShow(MessageCheckContext())
        idle()
        assertEquals("high", shownTitle())

        shownDialog()!!.dismiss()
        idle()

        assertEquals("middle", shownTitle())
    }

    @Test
    fun `a second check does not stack on the message already showing`() = runTest {
        val manager = manager()
        givenEligible(message("first"))
        manager.checkAndShow(MessageCheckContext())
        idle()

        givenEligible(message("second"))
        manager.checkAndShow(MessageCheckContext(screen = "Home"))
        idle()

        // "second" is queued, not drawn over "first".
        assertEquals("first", shownTitle())

        shownDialog()!!.dismiss()
        idle()
        assertEquals("second", shownTitle())
    }

    /**
     * Two triggers in a row each return a slice of up to three. The second
     * slice queues *behind* the first — it is not interleaved by priority
     * and it is not dropped.
     *
     * Append is the deliberate choice, and the only honest one: the server
     * sorts its slice by priority but `DeliveredMessage` carries no
     * priority field, so the SDK has nothing to merge two slices on. Even
     * with one, a message that only became eligible on the second check
     * should not jump ahead of one already waiting.
     */
    @Test
    fun `a second batch queues behind the first, in order`() = runTest {
        val manager = manager()
        givenEligible(message("a1"), message("a2"), message("a3"))
        manager.checkAndShow(MessageCheckContext())
        idle()

        givenEligible(message("b1"), message("b2"), message("b3"))
        manager.checkAndShow(MessageCheckContext(screen = "Home"))
        idle()

        assertEquals(
            listOf("a1", "a2", "a3", "b1", "b2", "b3"),
            drainShownTitles(),
        )
    }

    /**
     * Eligibility rules overlap, so two checks seconds apart usually return
     * the *same* slice. The repeats are skipped rather than queued again —
     * and they do not report a second `delivered` either.
     */
    @Test
    fun `an overlapping second check queues nothing new`() = runTest {
        val manager = manager()
        val batch = arrayOf(message("a1"), message("a2"), message("a3"))

        givenEligible(*batch)
        manager.checkAndShow(MessageCheckContext())
        idle()
        givenEligible(*batch)
        manager.checkAndShow(MessageCheckContext(screen = "Home"))
        idle()

        assertEquals(listOf("a1", "a2", "a3"), drainShownTitles())
        assertEquals(
            listOf("a1" to "delivered", "a2" to "delivered", "a3" to "delivered"),
            reported.filter { it.second == "delivered" },
        )
    }

    /**
     * Overlapping triggers return the same eligible set; the same message
     * twice in a row is never what the campaign asked for.
     */
    @Test
    fun `a message already queued is not queued twice`() = runTest {
        val manager = manager()
        givenEligible(message("only"), message("next"))
        manager.checkAndShow(MessageCheckContext())
        idle()

        manager.checkAndShow(MessageCheckContext(screen = "Home"))
        idle()

        shownDialog()!!.dismiss()
        idle()
        assertEquals("next", shownTitle())

        shownDialog()!!.dismiss()
        idle()
        // Nothing left: "only" and "next" were each queued once.
        assertNull(shownTitle())
    }

    // ── The trigger delay ──────────────────────────────────────────

    @Test
    fun `a message with a trigger delay waits for it`() = runTest {
        givenEligible(message("delayed", triggerDelay = 5))

        manager().checkAndShow(MessageCheckContext())
        idle()
        assertNull("must not show before its delay", shownTitle())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals("delayed", shownTitle())
    }

    /**
     * The delay used to run against the Activity that made the check. When
     * it fired after that Activity was gone, `show()` threw
     * `BadTokenException` out of a `postDelayed` runnable — uncatchable from
     * anywhere in the SDK, so it took the host app down.
     */
    @Test
    fun `a message is dropped when no activity is in the foreground`() = runTest {
        givenEligible(message("orphan", triggerDelay = 5))

        // No Activity: the one that triggered the check has gone.
        manager(displayActivity = { null }).checkAndShow(MessageCheckContext())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertNull(shownTitle())
    }

    /**
     * A drop releases the screen like a dismissal does, or the message
     * behind it would wait on one that is never coming.
     */
    @Test
    fun `a dropped message does not block the one behind it`() = runTest {
        givenEligible(message("gone"), message("shown"))

        // The first message misses its window; the app is back for the second.
        var first = true
        val displayActivity = { if (first) null.also { first = false } else activity }

        manager(displayActivity = displayActivity).checkAndShow(MessageCheckContext())
        idle()

        assertEquals("shown", shownTitle())
    }

    // ── Reporting ──────────────────────────────────────────────────

    /**
     * `delivered` is the honest top of the funnel — the message reached the
     * device — and `impression` belongs only to the one that reached the
     * screen. Reporting three impressions for a batch of three made the
     * funnel read inverted, and `delivered` was never sent at all, so its
     * report column was permanently zero.
     */
    @Test
    fun `every delivered message is reported but only the shown one gets an impression`() = runTest {
        givenEligible(message("high"), message("middle"), message("low"))

        manager().checkAndShow(MessageCheckContext())
        idle()

        assertEquals(
            listOf("high" to "delivered", "middle" to "delivered", "low" to "delivered"),
            reported.filter { it.second == "delivered" },
        )
        assertEquals(listOf("high" to "impression"), reported.filter { it.second == "impression" })
    }

    @Test
    fun `a dropped message reports no impression`() = runTest {
        givenEligible(message("orphan"))

        manager(displayActivity = { null }).checkAndShow(MessageCheckContext())
        idle()

        assertTrue(reported.none { it.second == "impression" })
        // It still reached the device, which is what `delivered` records.
        assertEquals(listOf("orphan" to "delivered"), reported)
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * The manager under test. Its event tracker runs on [Dispatchers.Unconfined]
     * so a report has landed by the time the call that caused it returns.
     */
    private fun manager(displayActivity: () -> Activity? = { activity }): MessageManager {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return MessageManager(
            apiClient = apiClient,
            scope = scope,
            projectId = PROJECT_ID,
            deviceFingerprint = FINGERPRINT,
            distinctId = { null },
            displayActivity = displayActivity,
            eventTracker = MessageEventTracker(
                apiClient, scope, PROJECT_ID, FINGERPRINT, { null }, Dispatchers.Unconfined,
            ),
        )
    }

    private fun givenEligible(vararg messages: InAppMessage) {
        coEvery {
            apiClient.checkMessages(any(), any(), any(), any())
        } returns messages.toList()
    }

    /**
     * A message identified by its title, which is the only field a rendered
     * dialog exposes — [id] matches so reports can be read the same way.
     */
    private fun message(name: String, triggerDelay: Int = 0) = InAppMessage(
        id = name,
        messageType = "modal",
        title = name,
        body = null,
        imageUrl = null,
        imagePosition = "top",
        buttons = null,
        theme = null,
        dismissOnTapOutside = true,
        autoDismissSeconds = null,
        triggerDelay = triggerDelay,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * Every message the queue shows, in order, dismissing each to reach the
     * next. Bounded so a queue that refuses to drain fails the test rather
     * than hanging it.
     */
    private fun drainShownTitles(): List<String> {
        val shown = mutableListOf<String>()
        while (shown.size <= DRAIN_LIMIT) {
            val title = shownTitle() ?: return shown
            shown.add(title)
            shownDialog()!!.dismiss()
            idle()
        }
        throw AssertionError("queue still showing after $DRAIN_LIMIT messages: $shown")
    }

    private fun shownDialog() = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    /** The title of the message on screen, or `null` when none is. */
    private fun shownTitle(): String? {
        val dialog = shownDialog() ?: return null
        return dialog.window?.decorView?.findTitle()
    }

    private fun android.view.View.findTitle(): String? = when (this) {
        is TextView -> text?.toString()?.takeIf { it.isNotEmpty() }
        is android.view.ViewGroup ->
            (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findTitle() }
        else -> null
    }

    private companion object {
        const val PROJECT_ID = "proj-messages"
        const val FINGERPRINT = "fp-device"

        /** Two full server slices, plus room to notice an extra. */
        const val DRAIN_LIMIT = 8
    }
}
