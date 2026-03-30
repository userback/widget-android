# Userback Android SDK

The Userback Android SDK allows you to integrate the Userback feedback widget into your Android application.

- **Minimum SDK:** API 21 (Android 5.0)
- **Language:** Kotlin

---

## Installation

### Option 1 — Local module (source)

1. Copy the `userback-sdk` directory into the root of your project.

2. Register the module in `settings.gradle.kts`:

```kotlin
include(":userback-sdk")
```

3. Add the dependency in your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":userback-sdk"))
}
```

### Option 2 — AAR file

1. Build the release AAR:
   ```
   ./gradlew :userback-sdk:assembleRelease
   ```
   The output is at `userback-sdk/build/outputs/aar/userback-sdk-release.aar`.

2. Place the AAR in your app's `libs/` folder.

3. Add it as a dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/userback-sdk-release.aar"))
    // Required transitive dependencies
    implementation("com.squareup.okhttp3:okhttp:4.x.x")
}
```

---

## Manifest

Add internet permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

To prevent the Activity from recreating on rotation (required for the widget to survive orientation changes):

```xml
<activity
    android:name=".YourActivity"
    android:configChanges="orientation|screenSize|screenLayout" />
```

---

## Setup

### 1. Initialize the SDK

Call `Userback.init()` once, typically in your `Activity.onCreate()` or `Application` class:

```kotlin
import io.userback.sdk.Userback

Userback.init(
    context = this,
    accessToken = "YOUR_ACCESS_TOKEN",
    userData = mapOf(
        "id" to "user-123",
        "info" to mapOf(
            "name" to "Jane Smith",
            "email" to "jane@example.com"
        )
    )
)
```

### 2. Create and attach the WebView

Create the Userback WebView and add it as an overlay on top of your root layout:

```kotlin
Userback.createWebView(this).apply {
    layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    visibility = View.GONE
    rootContainer.addView(this)  // rootContainer is your top-level FrameLayout
}
```

---

## Usage

### Open the feedback form

```kotlin
Userback.openForm()                        // default (general)
Userback.openForm(mode = "bug")
Userback.openForm(mode = "feature")
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
2. Set your access token in `Sample/app/build.gradle.kts` under `buildConfigField("String", "USERBACK_TOKEN", ...)`.
3. Run the `Sample.app` configuration on a device or emulator.
