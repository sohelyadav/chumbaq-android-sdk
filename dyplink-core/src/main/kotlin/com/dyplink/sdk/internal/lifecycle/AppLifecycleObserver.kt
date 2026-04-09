package com.dyplink.sdk.internal.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dyplink.sdk.internal.storage.EventQueueManager
import com.dyplink.sdk.internal.util.ConnectivityMonitor
import com.dyplink.sdk.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observes the process lifecycle to coordinate session tracking,
 * event flushing, and connectivity monitoring when the app moves
 * between foreground and background.
 */
internal class AppLifecycleObserver(
    private val queueManager: EventQueueManager,
    private val sessionTracker: SessionTracker,
    private val connectivityMonitor: ConnectivityMonitor,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * App entered the foreground.
     *
     * - Notifies the session tracker (may start a new session).
     * - Triggers a queue flush if the device is currently connected.
     */
    override fun onStart(owner: LifecycleOwner) {
        Logger.d("AppLifecycleObserver: app foregrounded")
        sessionTracker.onAppForegrounded()

        if (connectivityMonitor.isConnected) {
            queueManager.triggerFlush()
        }
    }

    /**
     * App entered the background.
     *
     * - Notifies the session tracker (records background timestamp).
     * - Flushes any queued events before the process may be killed.
     */
    override fun onStop(owner: LifecycleOwner) {
        Logger.d("AppLifecycleObserver: app backgrounded")
        sessionTracker.onAppBackgrounded()

        scope.launch {
            try {
                queueManager.flush()
            } catch (e: Exception) {
                Logger.e("AppLifecycleObserver: flush on background failed: ${e.message}", e)
            }
        }
    }
}
