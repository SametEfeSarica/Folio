# Folio v0.16.0

A lightweight native Java PDF reader and personal book library for Android tablets, including older devices (minimum Android 5.1.1 / API 22).

## Features

- PDF library with recently opened and all-books views, title search, and sorting.
- Collections with their own book-list view; add/remove books, rename, color, and delete collections.
- PDF reading with saved page position and document access across app restarts.
- Extra screen dimming, warm-light filter, grayscale/color reading mode, and Turkish/English interface.
- Layout adapts to screen width and can be used in portrait or landscape.

## Build

Open this folder in Android Studio and allow Gradle sync. The project uses Android Gradle Plugin `8.13.2`, Gradle `8.13`, JDK 17, and Android SDK Platform 35. Minimum SDK is API 22.

From a terminal:

```text
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

To build successfully, install Android Studio with JDK 17 and Android SDK Platform 35. No third-party runtime libraries are required; JUnit is used only by the local unit tests.

## Device testing

The project builds without a connected device. Before release, test the collection flows and the reading-light controls on the intended older tablet, since display contrast, touch accuracy, and PDF rendering performance depend on the hardware.
