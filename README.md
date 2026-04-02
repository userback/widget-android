# Userback Android SDK

The Userback Android SDK allows you to integrate the Userback feedback widget into your Android application.

- **Minimum SDK:** API 21 (Android 5.0)
- **Language:** Kotlin

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

Initialize Userback in your `Activity.onCreate()`:

```kotlin
import io.userback.sdk.Userback

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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
2. Set your access token in `local.properties`:
   ```
   USERBACK_TOKEN=YOUR_ACCESS_TOKEN
   ```
3. Run the `Sample.app` configuration on a device or emulator.
