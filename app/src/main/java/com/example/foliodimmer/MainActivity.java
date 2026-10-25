package com.example.foliodimmer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private View dimOverlay;
    private TextView amountLabel;
    private TextView pageLabel;
    private ImageView pdfPreview;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pdfFile;
    private Bitmap pageBitmap;
    private Matrix imageMatrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private float lastTouchY;
    private float currentScale = 1f;
    private int currentPage;
    private SharedPreferences preferences;
    private LinearLayout libraryPage;
    private View readerBar;
    private LinearLayout libraryList;
    private EditText librarySearch;
    private float downX;
    private float downY;
    private boolean toolbarVisible = true;
    private String currentUri;
    private boolean immersive;
    private String language;
    private FrameLayout setupOverlay;
    private FrameLayout root;

    private static final int PICK_PDF = 40;
    private static final String PREFS = "folio_preferences";
    private static final String LAST_PDF = "last_pdf";
    private static final String DIM_LEVEL = "dim_level";
    private static final String LIBRARY_URIS = "library_uris";
    private static final String PAGE_PREFIX = "page_";
    private static final String LANGUAGE = "language";
    private static final String SETUP_DONE = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        language = preferences.getString(LANGUAGE, "tr");
        setContentView(createScreen());
        updateDim(preferences.getInt(DIM_LEVEL, 0));
        rebuildLibrary();
        String savedPdf = preferences.getString(LAST_PDF, null);
        if (savedPdf != null) {
            tryOpenPdf(Uri.parse(savedPdf));
        }
        if (!preferences.getBoolean(SETUP_DONE, false)) showSetup();
    }

    private View createScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 244, 238));

        LinearLayout page = new LinearLayout(this);
        libraryPage = page;
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));

        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 12,
                Color.rgb(92, 88, 82));
        statusBar.addView(time, new LinearLayout.LayoutParams(0, dp(34), 1));
        Button brightnessQuick = smallButton("☼");
        brightnessQuick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showBrightnessDialog(); }
        });
        statusBar.addView(brightnessQuick, new LinearLayout.LayoutParams(dp(44), dp(38)));
        statusBar.addView(smallButton("⌕"), new LinearLayout.LayoutParams(dp(44), dp(38)));
        page.addView(statusBar, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView eyebrow = text("FOLIO  ·  " + t("reading_space"), 12, Color.rgb(108, 99, 255));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(eyebrow, wrap());

        TextView title = text(t("library"), 30, Color.rgb(24, 24, 29));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(12);
        page.addView(title, titleParams);

        TextView subtitle = text(t("pick_up"), 16,
                Color.rgb(92, 88, 82));
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(6);
        page.addView(subtitle, subtitleParams);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        TextView booksTab = text(t("books"), 16, Color.rgb(35, 35, 38));
        booksTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tabs.addView(booksTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView authorsTab = text(t("authors"), 16, Color.rgb(125, 118, 108));
        tabs.addView(authorsTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView filter = text(t("filter_all"), 14, Color.rgb(92, 88, 82));
        filter.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tabs.addView(filter, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(42));
        tabsParams.topMargin = dp(14);
        page.addView(tabs, tabsParams);

        librarySearch = new EditText(this);
        librarySearch.setSingleLine(true);
        librarySearch.setTextSize(15);
        librarySearch.setHint(t("search_library"));
        librarySearch.setPadding(dp(16), 0, dp(16), 0);
        librarySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                rebuildLibrary();
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(20);
        page.addView(librarySearch, searchParams);

        TextView card = text("01\n" + t("quiet_page") + "\n\n" + t("adjust_light"), 20,
                Color.rgb(50, 45, 41));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackgroundColor(Color.rgb(231, 225, 213));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(170));
        cardParams.topMargin = dp(28);
        page.addView(card, cardParams);

        TextView libraryTitle = text(t("books"), 21, Color.rgb(30, 30, 34));
        libraryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams libraryTitleParams = wrap();
        libraryTitleParams.topMargin = dp(26);
        page.addView(libraryTitle, libraryTitleParams);

        libraryList = new LinearLayout(this);
        libraryList.setOrientation(LinearLayout.VERTICAL);
        page.addView(libraryList, wrap());

        TextView controlTitle = text(t("extra_dim"), 17, Color.rgb(30, 30, 34));
        LinearLayout.LayoutParams controlTitleParams = wrap();
        controlTitleParams.topMargin = dp(30);
        page.addView(controlTitle, controlTitleParams);

        amountLabel = text("0%", 14, Color.rgb(92, 88, 82));
        amountLabel.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(24));
        page.addView(amountLabel, labelParams);

        SeekBar slider = new SeekBar(this);
        slider.setMax(80);
        slider.setProgress(0);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        slider.setProgress(preferences == null ? 0 : preferences.getInt(DIM_LEVEL, 0));
        page.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));

        Button openButton = button(t("open_pdf"));
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.setType("application/pdf");
                startActivityForResult(intent, PICK_PDF);
            }
        });
        LinearLayout.LayoutParams openButtonParams = new LinearLayout.LayoutParams(-1, dp(52));
        openButtonParams.topMargin = dp(10);
        page.addView(openButton, openButtonParams);

        Button immersiveButton = button(t("fullscreen"));
        immersiveButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                immersive = !immersive;
                applyImmersiveMode();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(16);
        page.addView(immersiveButton, buttonParams);

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(0, dp(12), 0, 0);
        Button libraryNav = button(t("library"));
        libraryNav.setAllCaps(false);
        bottomNav.addView(libraryNav, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button discoverNav = button(t("discover"));
        discoverNav.setAllCaps(false);
        bottomNav.addView(discoverNav, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button moreNav = button(t("more"));
        moreNav.setAllCaps(false);
        moreNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showLanguageDialog(); }
        });
        bottomNav.addView(moreNav, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(60)));

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        pdfPreview = new ImageView(this);
        pdfPreview.setBackgroundColor(Color.WHITE);
        pdfPreview.setScaleType(ImageView.ScaleType.MATRIX);
        pdfPreview.setVisibility(View.GONE);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(-1, -1);
        previewParams.setMargins(dp(16), dp(72), dp(16), dp(16));
        root.addView(pdfPreview, previewParams);
        LinearLayout readerBar = new LinearLayout(this);
        this.readerBar = readerBar;
        readerBar.setGravity(Gravity.CENTER_VERTICAL);
        readerBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        readerBar.setBackgroundColor(Color.rgb(242, 238, 230));
        Button previous = smallButton("‹");
        previous.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(currentPage - 1); }
        });
        readerBar.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        pageLabel = text("0 / 0", 15, Color.rgb(50, 45, 41));
        pageLabel.setGravity(Gravity.CENTER);
        readerBar.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button next = smallButton("›");
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(currentPage + 1); }
        });
        readerBar.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button close = smallButton("×");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { closeReader(); }
        });
        readerBar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        readerBar.setVisibility(View.GONE);
        root.addView(readerBar, new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP));
        pdfPreview.setOnTouchListener(createPdfTouchListener());
        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.BLACK);
        dimOverlay.setAlpha(0f);
        dimOverlay.setClickable(false);
        root.addView(dimOverlay, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            rememberPdf(data.getData());
            preferences.edit().putString(LAST_PDF, data.getData().toString()).apply();
            tryOpenPdf(data.getData());
        }
    }

    private void rememberPdf(Uri uri) {
        Set<String> saved = new HashSet<>(preferences.getStringSet(LIBRARY_URIS,
                new HashSet<String>()));
        saved.add(uri.toString());
        preferences.edit().putStringSet(LIBRARY_URIS, saved).apply();
        rebuildLibrary();
    }

    private void rebuildLibrary() {
        if (libraryList == null || preferences == null) return;
        libraryList.removeAllViews();
        Set<String> saved = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
        ArrayList<String> books = new ArrayList<>(saved);
        Collections.sort(books);
        String lastPdf = preferences.getString(LAST_PDF, null);
        if (lastPdf != null && books.remove(lastPdf)) books.add(0, lastPdf);
        if (books.isEmpty()) {
            TextView empty = text(t("empty_library"), 15, Color.rgb(110, 104, 96));
            empty.setPadding(dp(4), dp(10), dp(4), dp(10));
            libraryList.addView(empty, wrap());
            return;
        }
        for (final String bookUri : books) {
            String name = displayName(Uri.parse(bookUri));
            String query = librarySearch == null ? "" : librarySearch.getText().toString().trim().toLowerCase();
            if (query.length() > 0 && !name.toLowerCase().contains(query)) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackgroundColor(Color.rgb(250, 248, 243));
            TextView bookMark = text("▤", 25, Color.rgb(108, 99, 255));
            bookMark.setGravity(Gravity.CENTER);
            row.addView(bookMark, new LinearLayout.LayoutParams(dp(42), -1));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(name.replace("▸  ", ""), 16, Color.rgb(50, 45, 41));
            title.setSingleLine(true);
            details.addView(title, wrap());
            int total = preferences.getInt("count_" + bookUri.hashCode(), 0);
            int page = preferences.getInt(PAGE_PREFIX + bookUri.hashCode(), 0);
            TextView progressText = text(total > 0 ? (page + 1) + " / " + total : t("pdf_document"),
                    12, Color.rgb(125, 118, 108));
            details.addView(progressText, wrap());
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(total > 0 ? Math.min(100, ((page + 1) * 100) / total) : 0);
            details.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
            row.addView(details, new LinearLayout.LayoutParams(0, -1, 1));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(68));
            rowParams.topMargin = dp(4);
            libraryList.addView(row, rowParams);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { tryOpenPdf(Uri.parse(bookUri)); }
            });
        }
    }

    private String displayName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.length() == 0) name = "Untitled PDF";
        try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) { }
        return "▸  " + name;
    }

    private void tryOpenPdf(Uri uri) {
        try {
            closePdf();
            pdfFile = getContentResolver().openFileDescriptor(uri, "r");
            renderer = new PdfRenderer(pdfFile);
            currentUri = uri.toString();
            preferences.edit().putInt("count_" + currentUri.hashCode(), renderer.getPageCount()).apply();
            currentPage = preferences.getInt(PAGE_PREFIX + currentUri.hashCode(), 0);
            currentPage = Math.max(0, Math.min(currentPage, renderer.getPageCount() - 1));
            libraryPage.setVisibility(View.GONE);
            readerBar.setVisibility(View.VISIBLE);
            toolbarVisible = true;
            pdfPreview.setVisibility(View.VISIBLE);
            showPage(currentPage);
        } catch (Exception error) {
            closePdf();
            libraryPage.setVisibility(View.VISIBLE);
            readerBar.setVisibility(View.GONE);
            pdfPreview.setVisibility(View.GONE);
        }
    }

    private void showPage(int pageNumber) {
        if (renderer == null || pageNumber < 0 || pageNumber >= renderer.getPageCount()) return;
        currentPage = pageNumber;
        if (currentUri != null) {
            preferences.edit().putInt(PAGE_PREFIX + currentUri.hashCode(), currentPage).apply();
        }
        PdfRenderer.Page page = renderer.openPage(currentPage);
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2,
                Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        if (pageBitmap != null) pageBitmap.recycle();
        pageBitmap = bitmap;
        pdfPreview.setImageBitmap(pageBitmap);
        pageLabel.setText((currentPage + 1) + " / " + renderer.getPageCount());
        pdfPreview.post(new Runnable() {
            @Override public void run() { fitPage(); }
        });
    }

    private void fitPage() {
        if (pageBitmap == null || pdfPreview.getWidth() == 0 || pdfPreview.getHeight() == 0) return;
        float scale = Math.min((float) pdfPreview.getWidth() / pageBitmap.getWidth(),
                (float) pdfPreview.getHeight() / pageBitmap.getHeight());
        currentScale = 1f;
        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate((pdfPreview.getWidth() - pageBitmap.getWidth() * scale) / 2f,
                (pdfPreview.getHeight() - pageBitmap.getHeight() * scale) / 2f);
        pdfPreview.setImageMatrix(imageMatrix);
    }

    private View.OnTouchListener createPdfTouchListener() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                currentScale = Math.max(1f, Math.min(5f, currentScale * factor));
                imageMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                pdfPreview.setImageMatrix(imageMatrix);
                return true;
            }
        });
        return new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.getX(); lastTouchY = event.getY();
                        downX = event.getX(); downY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        imageMatrix.postTranslate(event.getX() - lastTouchX, event.getY() - lastTouchY);
                        pdfPreview.setImageMatrix(imageMatrix);
                        lastTouchX = event.getX(); lastTouchY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (currentScale <= 1.05f && Math.abs(dx) > dp(80)
                                && Math.abs(dx) > Math.abs(dy)) {
                            showPage(dx < 0 ? currentPage + 1 : currentPage - 1);
                        } else if (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12)) {
                            toolbarVisible = !toolbarVisible;
                            readerBar.setVisibility(toolbarVisible ? View.VISIBLE : View.GONE);
                        }
                    }
                }
                return true;
            }
        };
    }

    private void closeReader() {
        closePdf();
        readerBar.setVisibility(View.GONE);
        pdfPreview.setVisibility(View.GONE);
        libraryPage.setVisibility(View.VISIBLE);
        rebuildLibrary();
    }

    private void closePdf() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (pdfFile != null) {
            try {
                pdfFile.close();
            } catch (Exception ignored) { }
            pdfFile = null;
        }
    }

    @Override
    protected void onDestroy() {
        closePdf();
        if (pageBitmap != null) pageBitmap.recycle();
        super.onDestroy();
    }

    private void updateDim(int value) {
        if (dimOverlay != null) {
            dimOverlay.setAlpha(value / 100f);
        }
        if (amountLabel != null) {
            amountLabel.setText(value + "%");
        }
        if (preferences != null) {
            preferences.edit().putInt(DIM_LEVEL, value).apply();
        }
    }

    private void applyImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(immersive ? flags : 0);
    }

    private void showBrightnessDialog() {
        final SeekBar brightness = new SeekBar(this);
        brightness.setMax(80);
        brightness.setProgress(preferences.getInt(DIM_LEVEL, 0));
        brightness.setPadding(dp(20), dp(12), dp(20), dp(12));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        new AlertDialog.Builder(this).setTitle(t("brightness"))
                .setMessage(t("brightness_hint"))
                .setView(brightness)
                .setPositiveButton(t("done"), null)
                .show();
    }

    private void showLanguageDialog() {
        final String[] languages = {"Türkçe", "English"};
        int selected = "en".equals(language) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle(t("language"))
                .setSingleChoiceItems(languages, selected, (dialog, which) -> {
                    language = which == 1 ? "en" : "tr";
                    preferences.edit().putString(LANGUAGE, language).putBoolean(SETUP_DONE, true).apply();
                    dialog.dismiss();
                    recreate();
                }).show();
    }

    private void showSetup() {
        setupOverlay = new FrameLayout(this);
        setupOverlay.setBackgroundColor(Color.rgb(246, 244, 238));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(32), dp(48), dp(32), dp(32));

        TextView mark = text("FOLIO", 14, Color.rgb(108, 99, 255));
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(mark, wrap());
        TextView welcome = text(t("welcome"), 30, Color.rgb(24, 24, 29));
        welcome.setGravity(Gravity.CENTER);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams welcomeParams = wrap();
        welcomeParams.topMargin = dp(28);
        content.addView(welcome, welcomeParams);
        TextView choose = text(t("choose_language"), 16, Color.rgb(92, 88, 82));
        choose.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chooseParams = wrap();
        chooseParams.topMargin = dp(10);
        content.addView(choose, chooseParams);

        Button turkish = button("Türkçe");
        turkish.setOnClickListener(view -> finishSetup("tr"));
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(-1, dp(54));
        languageParams.topMargin = dp(32);
        content.addView(turkish, languageParams);
        Button english = button("English");
        english.setOnClickListener(view -> finishSetup("en"));
        content.addView(english, new LinearLayout.LayoutParams(-1, dp(54)));
        setupOverlay.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(setupOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void finishSetup(String selectedLanguage) {
        language = selectedLanguage;
        preferences.edit().putString(LANGUAGE, language).putBoolean(SETUP_DONE, true).apply();
        recreate();
    }

    private String t(String key) {
        boolean english = "en".equals(language);
        if ("reading_space".equals(key)) return english ? "READING SPACE" : "OKUMA ALANI";
        if ("library".equals(key)) return english ? "Library" : "Kitaplık";
        if ("pick_up".equals(key)) return english ? "Pick up where you left off." : "Kaldığın yerden devam et.";
        if ("search_library".equals(key)) return english ? "Search your library" : "Kitaplığında ara";
        if ("quiet_page".equals(key)) return english ? "A quiet page" : "Sessiz bir sayfa";
        if ("adjust_light".equals(key)) return english ? "Adjust the light before you read." : "Okumadan önce ışığı ayarla.";
        if ("books".equals(key)) return english ? "Books" : "Kitaplar";
        if ("authors".equals(key)) return english ? "Authors" : "Yazarlar";
        if ("filter_all".equals(key)) return english ? "Filter: All" : "Filtrele: Tümü";
        if ("extra_dim".equals(key)) return english ? "Extra dim" : "Ekstra karartma";
        if ("open_pdf".equals(key)) return english ? "Add PDF" : "PDF ekle";
        if ("fullscreen".equals(key)) return english ? "Fullscreen" : "Tam ekran";
        if ("discover".equals(key)) return english ? "Discover" : "Keşfet";
        if ("more".equals(key)) return english ? "More" : "Daha fazla";
        if ("empty_library".equals(key)) return english ? "Your books will appear here." : "Kitapların burada görünecek.";
        if ("pdf_document".equals(key)) return english ? "PDF document" : "PDF belgesi";
        if ("brightness".equals(key)) return english ? "Brightness" : "Parlaklık";
        if ("brightness_hint".equals(key)) return english ? "Adjust extra dim for comfortable reading." : "Rahat okuma için karartma seviyesini ayarla.";
        if ("done".equals(key)) return english ? "Done" : "Tamam";
        if ("language".equals(key)) return english ? "Language" : "Dil";
        if ("welcome".equals(key)) return english ? "Welcome to Folio" : "Merhaba, Folio'ya hoş geldin";
        if ("choose_language".equals(key)) return english ? "Choose your reading language." : "Okuma dilini seç.";
        return key;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(24);
        return button;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}


