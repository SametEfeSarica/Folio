# Folio

Folio is a lightweight, distraction-free PDF reading environment designed for older Android tablets and e-ink-style displays.

It combines a simple personal library, collections, reading progress, and a focused PDF reader in a native Java application that remains usable on low-power hardware.

## Highlights

- Native PDF reading powered by `PdfRenderer`
- Personal library with persistent document access
- Recently opened and all-books views
- Search by document title
- Sort by title or recently opened
- Collections with add, remove, rename, recolor, and delete actions
- Saved page position across sessions
- E-ink-friendly grayscale mode and optional color mode
- Extra dimming and warm-light controls
- Turkish and English interface
- Rotation and keyboard-state handling for older tablets
- No third-party runtime dependencies

## Design goals

Folio is intentionally built around a small and dependable native stack:

- Java and Android framework views
- Minimum Android API 22 (Android 5.1.1)
- Local preferences for lightweight library and reading-state storage
- Background PDF page rendering to keep the interface responsive
- Layouts that adapt to portrait and landscape tablet screens

The home screen uses regular Android views for layout, touch handling, dialogs, and scrolling. This keeps interaction areas aligned with what is drawn on screen and avoids fragile, resolution-specific touch coordinates.

## Requirements

- Android Studio
- JDK 17
- Android SDK Platform 35
- Gradle 8.13 (provided through the Gradle wrapper)

The application targets older devices while using a modern compile toolchain:

```text
minSdkVersion 22
targetSdkVersion 22
compileSdk 35
```

## Build and test

Open the project in Android Studio and allow Gradle sync to complete.

From a terminal:

```text
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

On Windows:

```text
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Getting started

1. Launch Folio and choose the interface language.
2. Add one or more PDF files with the add button.
3. Open a document from the library to start reading.
4. Use the brightness button for extra dimming and warm light.
5. Create collections to organize books into folders.

Folio requests persistent read access for documents selected through Android’s document picker so that books remain available after restarting the app.

## Project structure

```text
app/
  src/main/java/com/example/foliodimmer/
    MainActivity.java       Main library, reader, and UI flows
    CollectionStorage.java  Stable preference-key helpers
  src/test/                 Unit tests for collection storage behavior
```

## Device testing

The project builds without a connected device. For the best results, test on the intended tablet hardware before distributing a release build. In particular, verify:

- E-ink contrast and refresh behavior
- Touch accuracy and scrolling thresholds
- PDF rendering speed and memory use
- Persistent document permissions from the system file picker
- Rotation and keyboard behavior

## Current scope

Folio focuses on local PDF reading. It does not include cloud synchronization, account management, annotations, or an online bookstore.

## License

No license has been declared yet. Add a license before publishing Folio for redistribution.

