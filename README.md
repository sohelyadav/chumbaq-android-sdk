# Dyplink Android SDK

Deep linking, deferred deep links, attribution, events, in-app messages,
banners, and push notifications for Dyplink.

`minSdk` 21, `compileSdk` 34.

## Installation

Four modules. `dyplink-core` is the only mandatory one, and each of the
others depends on it. The BOM keeps their versions aligned:

```kotlin
dependencies {
    implementation(platform("in.dyplink:dyplink-bom:0.1.0"))

    implementation("in.dyplink:dyplink-core")
    implementation("in.dyplink:dyplink-push")       // optional
    implementation("in.dyplink:dyplink-banners")    // optional
    implementation("in.dyplink:dyplink-messages")   // optional
}
```

| Module | What it does |
|---|---|
| `dyplink-core` | Identity, events, deep links, deferred matching, offline queue, sessions |
| `dyplink-push` | FCM token registration, campaign engagement, rich notification rendering |
| `dyplink-banners` | A native carousel of a widget's banners |
| `dyplink-messages` | In-app messages: modal, sheet, full screen, and top and bottom banners |

## Getting started

```kotlin
val config = DyplinkConfig.Builder(
    baseUrl = "https://your-dyplink-host",
    apiKey = "<project API key>",
    projectId = "<project id>",
).build()

Dyplink.init(context, config)

// Where a deep link should take the app. `DeepLinkListener` is a fun
// interface, so a lambda is enough.
Dyplink.setDeepLinkListener { result -> router.go(result) }
```

Then, from the app's own entry points:

```kotlin
// An app link or custom-scheme intent.
Dyplink.handleDeepLink(intent)

// After a fresh install, to claim the click that led to it.
val match = Dyplink.matchDeferredDeepLink()

// Who this is, once they log in.
Dyplink.identify(IdentifyParams.Builder().externalUserId("u_42").build())
```

## In-app messages

```kotlin
DyplinkMessages.setMessageListener(listener)

DyplinkMessages.onAppOpen(context)                  // app foreground
DyplinkMessages.onScreenView(context, "Home")       // screen change
DyplinkMessages.onEvent(context, "purchase")        // custom event
```

The SDK draws the message itself, in whichever of the five layouts the
campaign chose, with its picture, its theme and its buttons. A tapped button
ends the message; a `deep_link` or `url` one is opened with `ACTION_VIEW`
after the listener has had its turn, so an app that routes its own links
should do it there.

## Push notifications

Declare the SDK's messaging service, and that is the whole integration:

```xml
<service
    android:name="com.dyplink.push.DyplinkMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

```kotlin
DyplinkPush.init(context)
DyplinkPush.registerToken(fcmToken)   // or let the service pick it up
```

It registers tokens, reports delivery and clicks, and renders the
notification — including the rich content no provider carries natively.

An app that already has its own `FirebaseMessagingService` should **not**
use this one. Call `DyplinkPush.onTokenRefresh(token)` and
`PushNotificationHandler.showNotification(context, message)` from the
existing service instead; `DyplinkMessagingService` is a thin wrapper around
exactly those two calls, and `onDyplinkMessage` is there to take the message
over without replacing the service.

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

- [iOS SDK](https://github.com/sohelyadav/dyplink-ios-sdk)
- [Flutter SDK](https://github.com/sohelyadav/dyplink-flutter-sdk)

## License

TBD
