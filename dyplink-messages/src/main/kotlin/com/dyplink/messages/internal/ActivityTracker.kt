package com.dyplink.messages.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Keeps a weak handle on the Activity currently in the foreground.
 *
 * A message is displayed some time after the check that found it — up to
 * [com.dyplink.messages.model.InAppMessage.triggerDelay] seconds later — and
 * a dialog needs a *live* Activity window to attach to. Holding the Activity
 * that happened to make the check retained it for the whole delay and then
 * threw `BadTokenException` from `show()` if it had gone, which is a crash in
 * the host app: the trigger points only guard the check, not the display.
 *
 * So the Activity is resolved at display time instead, from here. A
 * [WeakReference] because this is a process-lifetime singleton and anything
 * stronger is the same leak by another route.
 */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var registered = false

    @Volatile
    private var current: WeakReference<Activity>? = null

    /**
     * Starts tracking, using whatever [context] the host handed the trigger
     * point. Safe to call on every trigger; only the first registers.
     */
    fun register(context: Context) {
        // Already-resumed Activities are seeded from the caller: registration
        // only hears about the *next* lifecycle event, so without this the
        // first message after init has nothing to attach to.
        context.findActivity()?.let { current = WeakReference(it) }

        if (registered) return
        val application = context.applicationContext as? Application ?: return
        synchronized(this) {
            if (registered) return
            application.registerActivityLifecycleCallbacks(this)
            registered = true
        }
    }

    /**
     * The Activity a dialog can be shown on right now, or `null` when there
     * is none — the app is in the background, or the one we knew about has
     * been destroyed. A caller with `null` must drop the presentation.
     */
    val displayActivity: Activity?
        get() = current?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        // Left in place deliberately: a dialog shown from onPause's window is
        // still valid, and clearing here would drop every message triggered
        // across a screen transition.
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    /** Resets tracking state. **For testing only.** */
    internal fun resetForTesting() {
        current = null
        registered = false
    }
}

/**
 * Unwraps [this] to the Activity behind it, or `null` for a context that has
 * none — an application or service context. `ContextWrapper` because a themed
 * context handed to a view wraps the Activity rather than being one.
 */
internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
