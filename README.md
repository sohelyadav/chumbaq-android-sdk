# Chumbaq Android SDK

Deep linking, deferred deep links, attribution, events, in-app messages,
banners, and push notifications for Chumbaq.

`minSdk` 21, `compileSdk` 34.

## Installation

Four modules. `chumbaq-core` is the only mandatory one, and each of the
others depends on it. The BOM keeps their versions aligned:

```kotlin
dependencies {
    implementation(platform("in.chumbaq:chumbaq-bom:0.1.0"))

    implementation("in.chumbaq:chumbaq-core")
    implementation("in.chumbaq:chumbaq-push")       // optional
    implementation("in.chumbaq:chumbaq-banners")    // optional
    implementation("in.chumbaq:chumbaq-messages")   // optional
}
```

| Module | What it does |
|---|---|
| `chumbaq-core` | Identity, events, deep links, deferred matching, offline queue, sessions |
| `chumbaq-push` | FCM token registration, campaign engagement, rich notification rendering |
| `chumbaq-banners` | A native carousel of a widget's banners |
| `chumbaq-messages` | In-app messages: modal, sheet, full screen, and top and bottom banners |

## Getting started

```kotlin
val config = ChumbaqConfig.Builder(
    baseUrl = "https://your-chumbaq-host",
    apiKey = "<project API key>",
    projectId = "<project id>",
).build()

Chumbaq.init(context, config)

// Where a deep link should take the app. `DeepLinkListener` is a fun
// interface, so a lambda is enough.
Chumbaq.setDeepLinkListener { result -> router.go(result) }
```

Then, from the app's own entry points:

```kotlin
// An app link or custom-scheme intent.
Chumbaq.handleDeepLink(intent)

// After a fresh install, to claim the click that led to it.
val match = Chumbaq.matchDeferredDeepLink()

// Who this is, once they log in.
Chumbaq.identify(IdentifyParams.Builder().externalUserId("u_42").build())
```

## In-app messages

```kotlin
ChumbaqMessages.setMessageListener(listener)

ChumbaqMessages.onAppOpen(context)                  // app foreground
ChumbaqMessages.onScreenView(context, "Home")       // screen change
ChumbaqMessages.onEvent(context, "purchase")        // custom event
```

The SDK draws the message itself, in whichever of the five layouts the
campaign chose, with its picture, its theme and its buttons. A tapped button
ends the message; a `deep_link` or `url` one is opened with `ACTION_VIEW`
after the listener has had its turn, so an app that routes its own links
should do it there.

## Push notifications

One call, and that is the whole integration:

```kotlin
ChumbaqPush.init(context)
ChumbaqPush.registerToken(fcmToken)   // or let the service pick it up
```

`ChumbaqMessagingService` is declared in the module's own manifest, so
nothing needs adding to the app's. It registers tokens, reports delivery,
impressions, clicks and dismissals, and renders the notification —
including the rich content no provider carries natively.

### Handling messages yourself

Subclass `ChumbaqMessagingService` and declare the subclass. Return `true`
from `onChumbaqMessage` to suppress the default notification; reporting and
token registration keep working either way.

```kotlin
class MyMessagingService : ChumbaqMessagingService() {
    override fun onChumbaqMessage(message: RemoteMessage): Boolean {
        // …handle it; true means "do not show the default notification"
        return true
    }

    override fun onChumbaqTokenRefresh(token: String) {
        // The Chumbaq backend has already been told.
    }
}
```

```xml
<service
    android:name=".MyMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### Keeping your own service instead

An app that must keep its own `FirebaseMessagingService` can remove the
SDK's at manifest-merge time, so the two do not both receive messages:

```xml
<service
    android:name="com.chumbaq.push.ChumbaqMessagingService"
    tools:node="remove" />
```

That needs `xmlns:tools="http://schemas.android.com/tools"` on the
`<manifest>` element, and it gives up `delivered` reporting and the rich
notification rendering — so subclassing is the supported path.

### What the notification can show

| | |
|---|---|
| Image | Rendered as a big picture in the expanded notification |
| Carousel | `DecoratedCustomViewStyle` with `RemoteViews`, in the expanded view, with next/previous |
| Countdown | The system chronometer, API 24+ |
| Action buttons | Built from the payload. **Android renders at most three**; the system drops the rest |

The fallback chain is carousel → image → plain, so a campaign with rich
content still arrives on a device that cannot draw it.

## Related SDKs

- [iOS SDK](https://github.com/sohelyadav/chumbaq-ios-sdk)
- [Flutter SDK](https://github.com/sohelyadav/chumbaq-flutter-sdk)

## License

Copyright 2026 The Chumbaq Authors.

Licensed under the [Apache License, Version 2.0](LICENSE).
