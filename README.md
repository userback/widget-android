# Userback Android SDK

The Userback Android SDK allows you to easily integrate Userback's feedback widget into your Android applications.

## Installation

Add the following to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":userback-sdk"))
}
```

## Usage

### Initialization

Initialize the SDK in your `Activity` or `Application` class:

```kotlin
import io.userback.sdk.Userback

Userback.init(context, "YOUR_API_KEY_HERE")
```

### Show Widget

To trigger the Userback widget, call:

```kotlin
Userback.show()
```

## Sample App

A sample application is provided in the `Sample/app` directory to demonstrate the integration.
