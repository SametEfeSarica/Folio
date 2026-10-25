# Folio v0.10

Minimal native Java Android prototype targeting Android 5.1.1 (API 22).

The eighth prototype provides:

- A quiet, library-style reading screen.
- A native PDF picker for opening a PDF stored on the tablet.
- A first-page PDF preview using Android's built-in `PdfRenderer` (API 21+).
- Previous/next page navigation and a page counter.
- Pinch-to-zoom and drag-to-pan controls.
- Remembered last PDF selection and extra-dim level.
- A focused reading view with a simple book-like toolbar.
- A Kindle-inspired in-app library for previously opened PDFs.
- Resume from the last page when reopening a book.
- Tap the page to hide or show the reading toolbar.
- Swipe left/right to move between pages.
- A Kindle-inspired library home with search, calm typography, progress bars, and recent-book ordering.
- Lightweight UI built from native Android views with no animation-heavy effects.
- Kindle-inspired status bar, tabs, filter label, and bottom navigation.
- First-launch language setup with Turkish and English options.
- Saved language selection with a language picker under More.
- Quick brightness/extra-dim dialog from the top bar.
- A Kindle-inspired home screen drawn as a lightweight native canvas for consistent tablet layout.
- Three-column book cover grid with reading percentage and bottom navigation.
- First-run language setup presented above the library screen.
- Direct page seeking with a page slider between the previous/next controls.
- Page swipes that change pages without dragging the document at normal zoom.
- Kindle-style top controls for brightness, adding PDFs, settings, fullscreen, and search.
- Functional Collections tab replacing Authors.
- Collection assignment when adding a PDF.
- Real first-page thumbnails for books in the library.
- A direct inline brightness seekbar instead of a separate brightness window.
- In-place Kindle-style settings and sorting panels without white modal dialogs.
- Functional collection creation, folder browsing, and back navigation.
- A single low-saturation e-ink visual theme across library and reader controls.
- A persistent A-Z, Z-A, and recently opened sorting menu.
- Recent-book ordering based on the last time each PDF was opened.
- Search controls positioned in the top bar without covering the library content.
- Kindle-like serif headings, warm e-ink palette, and framed top controls.
- An extra-dim slider that adds a black overlay from 0% to 80%.
- A fullscreen toggle using Android immersive sticky mode.
- No runtime or UI dependencies outside the Android SDK.

## Build

Open this folder in Android Studio with an Android SDK and Gradle support installed. The project uses Android Gradle Plugin `8.13.2`, Gradle `8.13`, compiles against SDK 35, and targets/minimizes API 22. Android Studio should use JDK 17 for Gradle. The plugin repositories are declared in `settings.gradle` so the Android plugin can be resolved from Google's Maven repository.

From a terminal with Gradle available:

```text
gradle assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

If the Android SDK or Gradle tooling is missing, install Android Studio and SDK Platform 35, then open this project and let Android Studio sync and build it. The source files are self-contained and require no runtime dependencies beyond the Android build plugin.


