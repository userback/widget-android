# Userback Android SDK

The Userback Android SDK allows you to integrate the Userback feedback widget into your Android application.

- **Minimum SDK:** API 21 (Android 5.0)
- **Language:** Kotlin

## What's new in v2

- **Multi-project support** — `openForm` accepts an optional `projectKey` to route feedback to a specific Userback project when your app is set up with more than one.

This is additive. Existing v1 `openForm(mode, directTo)` calls keep working unchanged — no code changes required to upgrade.

### Upgrading from v1

```kotlin
dependencies {
    implementation("com.github.userback:widget-android:2.0.0")
}
```

If you're still passing a general web widget access token (`P-...`) as `accessToken`, switch to your app's **Mobile Key** instead — find it in the Userback app under **Workspace Settings → Mobile SDK**. The Mobile Key is required for multi-project routing.

---

## Installation

### Step 1

Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.userback:widget-android:1.0.0")
}
```

### Step 3

Add internet permission and `configChanges` to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<activity
    android:name=".YourActivity"
    android:configChanges="orientation|screenSize|screenLayout" />
```

### Step 4

Initialize Userback in your `Activity.onCreate()`. Use your app's **Mobile Key** — found in the Userback app under **Workspace Settings → Mobile SDK** — not a general web widget access token (`P-...`):

```kotlin
import io.userback.sdk.Userback

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Userback.init(
        context = this,
        accessToken = "YOUR_MOBILE_KEY",
        userData = mapOf(
            "id" to "user-123",
            "info" to mapOf(
                "name" to "Jane Smith",
                "email" to "jane@example.com"
            )
        )
    )
}
```

---

## Usage

### Open the feedback form

```kotlin
Userback.openForm()                        // default (general)
Userback.openForm(mode = "bug")
Userback.openForm(mode = "feature")

// Open and navigate directly to a target
Userback.openForm(mode = "general", directTo = "screenshot")

// Route to a specific project, if your app has more than one set up
Userback.openForm(mode = "general", projectKey = "YOUR_PROJECT_KEY")
```

### Open portal / roadmap / announcements

```kotlin
Userback.openPortal()
Userback.openRoadmap()
Userback.openAnnouncement()
```

### Close the widget

```kotlin
Userback.close()
```

### Destroy the SDK

Removes the WebView from the view hierarchy, releases all resources, and resets internal state. Call this when you no longer need the SDK — for example, on logout or when tearing down the activity.

```kotlin
Userback.destroy()
```

To re-enable the SDK after destroying it, call `Userback.init()` again.

### Identify a user

```kotlin
Userback.identify(
    userID = "user-123",
    userInfo = mapOf("email" to "jane@example.com", "plan" to "pro")
)

Userback.clearIdentity()
```

### Set user attributes

```kotlin
Userback.setEmail("jane@example.com")
Userback.setName("Jane Smith")
Userback.setCategories("android,beta")
Userback.setPriority("high")
Userback.setTheme("dark")
Userback.setData(mapOf("build" to "1.2.3", "env" to "production"))
```

### Network interceptor (OkHttp)

Attach the Userback interceptor to capture network requests in session replays:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(Userback.getInterceptor())
    .build()
```

---

## Sample App

A working example is in the [`Sample/`](Sample/) directory. To run it:

1. Open the project in Android Studio.
2. Set your Mobile Key in `local.properties`:
   ```
   USERBACK_TOKEN=YOUR_MOBILE_KEY
   ```
3. Run the `Sample.app` configuration on a device or emulator.
