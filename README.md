# Folio v0.1

Minimal native Java Android prototype targeting Android 5.1.1 (API 22).

The first usable prototype provides:

- A quiet, library-style reading screen.
- A native PDF picker for opening a PDF stored on the tablet.
- A first-page PDF preview using Android's built-in `PdfRenderer` (API 21+).
- An extra-dim slider that adds a black overlay from 0% to 80%.
- A fullscreen toggle using Android immersive sticky mode.
- No runtime or UI dependencies outside the Android SDK.

## Build

Open this folder in Android Studio with an Android SDK and Gradle support installed. The project uses Android Gradle Plugin `8.13.2`, Gradle `8.13`, compiles against SDK 35, and targets/minimizes API 22. Android Studio should use JDK 17 for Gradle.

From a terminal with Gradle available:

```text
gradle assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

If the Android SDK or Gradle tooling is missing, install Android Studio and SDK Platform 35, then open this project and let Android Studio sync and build it. The source files are self-contained and require no runtime dependencies beyond the Android build plugin.


