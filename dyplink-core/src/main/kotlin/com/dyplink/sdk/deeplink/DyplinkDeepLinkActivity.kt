package com.dyplink.sdk.deeplink

import android.app.Activity
import android.os.Bundle
import com.dyplink.sdk.Dyplink

/**
 * Transparent trampoline activity for handling deep links.
 *
 * Declare this activity in the manifest with the appropriate
 * intent filters when the host app's main activity is not
 * configured to receive deep links directly.  It parses the
 * incoming link, forwards the result to the registered
 * [DeepLinkListener], and finishes immediately so the user
 * never sees an extra screen.
 *
 * Example manifest entry:
 * ```xml
 * <activity
 *     android:name="com.dyplink.sdk.deeplink.DyplinkDeepLinkActivity"
 *     android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *     android:exported="true">
 *     <intent-filter android:autoVerify="true">
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="https" android:host="links.example.com" />
 *     </intent-filter>
 * </activity>
 * ```
 */
class DyplinkDeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse and dispatch; the listener is invoked inside handleDeepLink.
        @Suppress("UNUSED_VARIABLE")
        val result = Dyplink.handleDeepLink(intent)

        finish()
    }
}
