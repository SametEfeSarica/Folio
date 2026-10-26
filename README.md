# Folio v0.15.2

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
- In-place brightness controls with no blinking search cursor until search is activated.
- Optional monochrome or color e-ink display mode.
- Immersive fullscreen enabled automatically at launch.
- Two-finger vertical swipes to hide or reveal the PDF toolbar.
- A redesigned custom PDF toolbar matching the library theme.
- Correct 100% completion for the final page.
- Multiple PDF selection with return to the library instead of opening immediately.
- Search icon integrated into the search field.
- E-ink-aware collection cards and reversed dimming direction.
- Separate boxed next and close controls in the PDF toolbar.
- Rounded compact brightness and settings panels with a 0–100 dim range.
- Search field background matched to the e-ink library surface.
- Collection create, rename, and delete actions with an in-theme editor panel.
- Larger portrait-oriented typography and controls.
- Continuous brightness slider dragging.
- Bottom full-screen reader toolbar for tablet ergonomics.
- Six recently opened cover cards plus a scrollable all-books list.
- Deeper extra-dim range and an adjustable blue-light filter.
- Collections start empty and support rename, delete, five folder colors, and library-only multi-select book assignment.
- Fixed collection-screen crash caused by unassigned books.
- Added a dedicated All books tab with a cover grid.
- Larger spaced settings and sorting panels with a dimmed search field.
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


