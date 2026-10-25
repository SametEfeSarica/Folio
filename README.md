# Folio Dimmer

Minimal native Java Android prototype targeting Android 5.1.1 (API 22).

The prototype provides:

- A quiet, library-style reading screen.
- An extra-dim slider that adds a black overlay from 0% to 80%.
- A fullscreen toggle using Android immersive sticky mode.
- No runtime or UI dependencies outside the Android SDK.

## Build

Open this folder in Android Studio with an Android SDK and Gradle support installed. The project uses Android Gradle Plugin `3.5.4`, compiles against SDK 28, and targets/minimizes API 22.

From a terminal with Gradle available:

```text
gradle assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

If the Android SDK or Gradle tooling is missing, install Android Studio and SDK Platform 28, then open this project and let Android Studio sync and build it. This checkout does not include a Gradle wrapper. The source files are self-contained and require no runtime dependencies beyond the Android build plugin.


