package com.example.foliodimmer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
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
import java.util.HashMap;
import java.util.Map;
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
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private View dimOverlay;
    private TextView amountLabel;
    private TextView pageLabel;
    private SeekBar pageSeekBar;
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
    private float multiDownY;
    private float multiLastY;
    private boolean multiGesture;
    private boolean toolbarVisible = true;
    private String currentUri;
    private boolean immersive;
    private String language;
    private FrameLayout setupOverlay;
    private FrameLayout root;
    private View kindleHome;
    private ModernHomeView modernHome;
    private EditText searchBar;
    private SeekBar brightnessBar;
    private boolean collectionMode;
    private boolean allBooksMode;
    private String activeCollection;
    private boolean settingsPanel;
    private boolean sortPanel;
    private boolean brightnessPanel;
    private boolean languageExpanded;
    private boolean colorExpanded;
    private boolean collectionEditor;
    private boolean collectionActionPanel;
    private boolean collectionColorPanel;
    private boolean collectionPickerPanel;
    private EditText collectionInput;
    private String editingCollection;
    private String pendingCollectionAction;
    private String collectionPickerTarget;
    private float collectionPickerOffset;
    private boolean collectionPickerDragging;
    private final Set<String> selectedCollectionBooks = new HashSet<>();
    private float homeContentOffset;
    private boolean homeDragging;
    private View blueLightOverlay;
    private View panelDimOverlay;
    private final Map<String, Bitmap> thumbnailCache = new HashMap<>();
    private final ExecutorService pageExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int pageRenderToken;
    private int pendingDimValue;

    private static final int PICK_PDF = 40;
    private static final String PREFS = "folio_preferences";
    private static final String LAST_PDF = "last_pdf";
    private static final String DIM_LEVEL = "dim_level";
    private static final String LIBRARY_URIS = "library_uris";
    private static final String PAGE_PREFIX = "page_";
    private static final String LANGUAGE = "language";
    private static final String SETUP_DONE = "setup_done";
    private static final String COLLECTIONS = "collections";
    private static final String COLLECTION_PREFIX = "collection_";
    private static final String SORT_MODE = "sort_mode";
    private static final String OPENED_PREFIX = "opened_";
    private static final String COLOR_MODE = "color_mode";
    private static final String BLUE_LIGHT_LEVEL = "blue_light_level";
    private static final String COLLECTION_COLOR_PREFIX = "collection_color_";
    private static final String STATE_READER_URI = "state_reader_uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        language = preferences.getString(LANGUAGE, "tr");
        migratePreferenceKeys();
        migrateLegacyGeneralCollection();
        setContentView(createScreen());
        immersive = true;
        applyImmersiveMode();
        updateDim(preferences.getInt(DIM_LEVEL, 0));
        rebuildLibrary();
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_READER_URI)) {
            String readerUri = savedInstanceState.getString(STATE_READER_URI);
            if (readerUri != null) tryOpenPdf(Uri.parse(readerUri));
        } else if (!preferences.getBoolean(SETUP_DONE, false)) {
            showSetup();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pdfPreview != null && pdfPreview.getVisibility() == View.VISIBLE && currentUri != null) {
            outState.putString(STATE_READER_URI, currentUri);
        }
        super.onSaveInstanceState(outState);
    }

    private View createScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(232, 230, 222));

        libraryPage = new LinearLayout(this);
        libraryPage.setOrientation(LinearLayout.VERTICAL);
        modernHome = new ModernHomeView(this);
        kindleHome = modernHome;
        libraryPage.addView(kindleHome, new LinearLayout.LayoutParams(-1, -1));
        root.addView(libraryPage, new FrameLayout.LayoutParams(-1, -1));

        searchBar = new EditText(this);
        searchBar.setSingleLine(true);
        searchBar.setTextSize(16);
        searchBar.setHint(t("search_library"));
        searchBar.setPadding(dp(12), 0, dp(12), 0);
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setCompoundDrawablesWithIntrinsicBounds(new SearchDrawable(), null, null, null);
        searchBar.setCompoundDrawablePadding(dp(6));
        searchBar.setCursorVisible(false);
        searchBar.setOnFocusChangeListener((view, hasFocus) -> searchBar.setCursorVisible(hasFocus));
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                homeContentOffset = 0;
                if (kindleHome != null) kindleHome.invalidate();
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        searchBar.setCompoundDrawablesWithIntrinsicBounds(new SearchDrawable(), null,
                new ClearDrawable(), null);
        searchBar.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && event.getX() > searchBar.getWidth() - dp(48)) {
                searchBar.setText("");
                searchBar.clearFocus();
                root.requestFocus();
                return true;
            }
            return false;
        });
        searchBar.setTextColor(Color.rgb(58, 57, 53));
        searchBar.setHintTextColor(Color.rgb(117, 115, 108));
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(Color.rgb(232, 230, 222));
        searchBackground.setStroke(dp(1), Color.rgb(178, 176, 168));
        searchBackground.setCornerRadius(dp(4));
        searchBar.setBackground(searchBackground);
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(-1, dp(44));
        searchParams.leftMargin = dp(24);
        searchParams.rightMargin = dp(24);
        searchParams.topMargin = dp(52);
        root.addView(searchBar, searchParams);

        panelDimOverlay = new View(this);
        panelDimOverlay.setBackgroundColor(Color.BLACK);
        panelDimOverlay.setAlpha(0.24f);
        panelDimOverlay.setVisibility(View.GONE);
        panelDimOverlay.setClickable(false);
        root.addView(panelDimOverlay, new FrameLayout.LayoutParams(-1, -1));

        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        brightnessBar.setProgress(preferences.getInt(DIM_LEVEL, 0));
        brightnessBar.setVisibility(View.GONE);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value, false);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { updateDim(bar.getProgress()); }
        });
        FrameLayout.LayoutParams brightnessParams = new FrameLayout.LayoutParams(dp(210), dp(42));
        brightnessParams.leftMargin = dp(118);
        brightnessParams.topMargin = dp(8);
        root.addView(brightnessBar, brightnessParams);

        pdfPreview = new ImageView(this);
        pdfPreview.setBackgroundColor(Color.WHITE);
        pdfPreview.setScaleType(ImageView.ScaleType.MATRIX);
        pdfPreview.setVisibility(View.GONE);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(-1, -1);
        previewParams.setMargins(0, 0, 0, 0);
        root.addView(pdfPreview, previewParams);

        ReaderToolbarView reader = new ReaderToolbarView(this);
        readerBar = reader;
        reader.setVisibility(View.GONE);
        root.addView(reader, new FrameLayout.LayoutParams(-1, dp(82), Gravity.BOTTOM));
        pdfPreview.setOnTouchListener(createPdfTouchListener());

        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.BLACK);
        dimOverlay.setClickable(false);
        root.addView(dimOverlay, new FrameLayout.LayoutParams(-1, -1));
        blueLightOverlay = new View(this);
        blueLightOverlay.setBackgroundColor(Color.rgb(255, 188, 72));
        blueLightOverlay.setClickable(false);
        root.addView(blueLightOverlay, new FrameLayout.LayoutParams(-1, -1));
        updateBlueLight(preferences.getInt(BLUE_LIGHT_LEVEL, 0));
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        return root;
    }

    private View createLegacyScreen() {
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
        slider.setMax(100);
        slider.setProgress(0);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value, false);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { updateDim(bar.getProgress()); }
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
        if (requestCode == PICK_PDF && resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> selected = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    selected.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                selected.add(data.getData());
            }
            for (Uri uri : selected) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                rememberPdf(uri);
            }
            if (!selected.isEmpty()) {
                preferences.edit().putString(LAST_PDF, selected.get(0).toString()).apply();
                if (kindleHome != null) kindleHome.invalidate();
            }
        }
    }

    private void rememberPdf(Uri uri) {
        Set<String> saved = new HashSet<>(preferences.getStringSet(LIBRARY_URIS,
                new HashSet<String>()));
        saved.add(uri.toString());
        preferences.edit().putStringSet(LIBRARY_URIS, saved).apply();
        rebuildLibrary();
    }

    private void removePdf(Uri uri) {
        String value = uri.toString();
        Set<String> saved = new HashSet<>(preferences.getStringSet(LIBRARY_URIS,
                new HashSet<String>()));
        if (!saved.remove(value)) return;
        SharedPreferences.Editor editor = preferences.edit().putStringSet(LIBRARY_URIS, saved)
                .remove(CollectionStorage.key(COLLECTION_PREFIX, value))
                .remove(CollectionStorage.key(PAGE_PREFIX, value))
                .remove(CollectionStorage.key("count_", value))
                .remove(CollectionStorage.key(OPENED_PREFIX, value));
        if (value.equals(preferences.getString(LAST_PDF, null))) editor.remove(LAST_PDF);
        editor.apply();
    }

    private void migrateLegacyGeneralCollection() {
        Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                new HashSet<String>()));
        names.remove("Genel");
        names.remove("General");
        SharedPreferences.Editor editor = preferences.edit().putStringSet(COLLECTIONS, names);
        for (String book : preferences.getStringSet(LIBRARY_URIS, new HashSet<String>())) {
            String value = preferences.getString(CollectionStorage.key(COLLECTION_PREFIX, book), null);
            if ("Genel".equals(value) || "General".equals(value)) {
                editor.remove(CollectionStorage.key(COLLECTION_PREFIX, book));
            }
        }
        editor.apply();
    }

    private void migratePreferenceKeys() {
        Set<String> books = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
        for (String book : books) {
            migratePreferenceKey(COLLECTION_PREFIX, book);
            migratePreferenceKey(PAGE_PREFIX, book);
            migratePreferenceKey("count_", book);
            migratePreferenceKey(OPENED_PREFIX, book);
        }
        Set<String> names = preferences.getStringSet(COLLECTIONS, new HashSet<String>());
        for (String name : names) migratePreferenceKey(COLLECTION_COLOR_PREFIX, name);
    }

    private void migratePreferenceKey(String prefix, String value) {
        String legacyKey = prefix + value.hashCode();
        String stableKey = CollectionStorage.key(prefix, value);
        if (!preferences.contains(legacyKey)) return;
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains(stableKey)) {
            Object legacyValue = preferences.getAll().get(legacyKey);
            if (legacyValue instanceof String) editor.putString(stableKey, (String) legacyValue);
            else if (legacyValue instanceof Integer) editor.putInt(stableKey, (Integer) legacyValue);
            else if (legacyValue instanceof Long) editor.putLong(stableKey, (Long) legacyValue);
        }
        editor.remove(legacyKey).apply();
    }

    private void showCollectionDialog(final Uri uri) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(t("collection_hint"));
        input.setPadding(dp(20), dp(8), dp(20), dp(8));
        new AlertDialog.Builder(this).setTitle(t("choose_collection"))
                .setMessage(t("collection_message"))
                .setView(input)
                .setPositiveButton(t("save"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() == 0) name = t("general");
                    saveCollection(uri, name);
                    tryOpenPdf(uri);
                })
                .setNegativeButton(t("skip"), (dialog, which) -> tryOpenPdf(uri))
                .show();
    }

    private void saveCollection(Uri uri, String name) {
        String normalized = CollectionStorage.normalizeName(name);
        if (normalized == null) return;
        Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                new HashSet<String>()));
        names.add(normalized);
        preferences.edit().putStringSet(COLLECTIONS, names)
                .putString(CollectionStorage.key(COLLECTION_PREFIX, uri.toString()), normalized).apply();
        if (kindleHome != null) kindleHome.invalidate();
    }

    private String collectionForBook(String bookUri) {
        String collection = preferences.getString(CollectionStorage.key(COLLECTION_PREFIX, bookUri), null);
        return collection != null ? collection
                : preferences.getString(COLLECTION_PREFIX + bookUri.hashCode(), null);
    }

    private void showNewCollectionDialog() {
        showCollectionEditor(null);
    }

    private void showCollectionEditor(String existingName) {
        collectionActionPanel = false;
        collectionColorPanel = false;
        collectionEditor = true;
        editingCollection = existingName;
        collectionInput = new EditText(this);
        collectionInput.setSingleLine(true);
        collectionInput.setText(existingName == null ? "" : existingName);
        collectionInput.setTextSize(17);
        collectionInput.setTextColor(Color.rgb(55, 53, 48));
        collectionInput.setHintTextColor(Color.rgb(130, 127, 119));
        collectionInput.setHint(t("collection_hint"));
        collectionInput.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.rgb(232, 230, 222));
        inputBackground.setStroke(dp(1), Color.rgb(150, 147, 138));
        inputBackground.setCornerRadius(dp(4));
        collectionInput.setBackground(inputBackground);
        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(dp(300), dp(44));
        inputParams.leftMargin = dp(70);
        inputParams.topMargin = dp(258);
        root.addView(collectionInput, inputParams);
        collectionInput.requestFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(collectionInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        refreshPanelDimming();
        if (kindleHome != null) kindleHome.invalidate();
    }

    private void finishCollectionEditor(boolean save) {
        if (save && collectionInput != null) {
            String name = CollectionStorage.normalizeName(collectionInput.getText().toString());
            if (name == null) {
                collectionInput.setError(t("collection_name_required"));
                return;
            }
            Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                    new HashSet<String>()));
            if (CollectionStorage.isDuplicateName(names, name, editingCollection)) {
                collectionInput.setError(t("collection_exists"));
                return;
            }
            SharedPreferences.Editor editor = preferences.edit();
            if (editingCollection != null) {
                names.remove(editingCollection);
                int color = preferences.getInt(CollectionStorage.key(
                        COLLECTION_COLOR_PREFIX, editingCollection), 0);
                editor.remove(CollectionStorage.key(COLLECTION_COLOR_PREFIX, editingCollection));
                editor.putInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name), color);
                for (String book : preferences.getStringSet(LIBRARY_URIS,
                        new HashSet<String>())) {
                    if (editingCollection.equals(collectionForBook(book))) {
                        editor.putString(CollectionStorage.key(COLLECTION_PREFIX, book), name);
                    }
                }
            }
            names.add(name);
            editor.putStringSet(COLLECTIONS, names).apply();
        }
        if (collectionInput != null) {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(collectionInput.getWindowToken(), 0);
            root.removeView(collectionInput);
        }
        collectionInput = null;
        collectionEditor = false;
        editingCollection = null;
        refreshPanelDimming();
        if (kindleHome != null) kindleHome.invalidate();
    }

    private void deleteCollection(String name) {
        Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                new HashSet<String>()));
        names.remove(name);
        SharedPreferences.Editor editor = preferences.edit().putStringSet(COLLECTIONS, names);
        for (String book : preferences.getStringSet(LIBRARY_URIS, new HashSet<String>())) {
            if (name.equals(collectionForBook(book))) {
                editor.remove(CollectionStorage.key(COLLECTION_PREFIX, book));
            }
        }
        editor.remove(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name));
        editor.apply();
        collectionActionPanel = false;
        collectionColorPanel = false;
        pendingCollectionAction = null;
        refreshPanelDimming();
        if (kindleHome != null) kindleHome.invalidate();
    }

    private void confirmDeleteCollection(String name) {
        new AlertDialog.Builder(this)
                .setTitle(t("delete_collection"))
                .setMessage(t("delete_collection_message"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("delete"), (dialog, which) -> deleteCollection(name))
                .show();
    }

    private void showLegacyCollectionDialog(final Uri uri) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(t("collection_hint"));
        input.setPadding(dp(20), dp(8), dp(20), dp(8));
        new AlertDialog.Builder(this).setTitle(t("new_collection"))
                .setMessage(t("new_collection_message"))
                .setView(input)
                .setPositiveButton(t("save"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() > 0) {
                        Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                                new HashSet<String>()));
                        names.add(name);
                        preferences.edit().putStringSet(COLLECTIONS, names).apply();
                        if (kindleHome != null) kindleHome.invalidate();
                    }
                })
                .setNegativeButton(t("cancel"), null)
                .show();
    }

    private void rebuildLibrary() {
        if (kindleHome != null) kindleHome.invalidate();
        if (libraryList == null || preferences == null) return;
        libraryList.removeAllViews();
        Set<String> saved = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
        for (String value : new HashSet<>(saved)) {
            try (ParcelFileDescriptor ignored = getContentResolver().openFileDescriptor(
                    Uri.parse(value), "r")) {
                if (ignored == null) removePdf(Uri.parse(value));
            } catch (Exception error) {
                removePdf(Uri.parse(value));
            }
        }
        saved = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
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
            int total = preferences.getInt(CollectionStorage.key("count_", bookUri), 0);
            int page = preferences.getInt(CollectionStorage.key(PAGE_PREFIX, bookUri), 0);
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
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) { }
        if (name == null || name.length() == 0) name = uri.getLastPathSegment();
        if (name == null || name.length() == 0) name = "Untitled PDF";
        try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) { }
        return "▸  " + name;
    }

    private void pickPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("application/pdf");
        startActivityForResult(intent, PICK_PDF);
    }

    private void tryOpenPdf(Uri uri) {
        try {
            closePdf();
            pdfFile = getContentResolver().openFileDescriptor(uri, "r");
            renderer = new PdfRenderer(pdfFile);
            currentUri = uri.toString();
            preferences.edit()
                    .putInt(CollectionStorage.key("count_", currentUri), renderer.getPageCount())
                    .putLong(CollectionStorage.key(OPENED_PREFIX, currentUri), System.currentTimeMillis())
                    .apply();
            if (pageSeekBar != null) pageSeekBar.setMax(Math.max(1, renderer.getPageCount() - 1));
            currentPage = preferences.getInt(CollectionStorage.key(PAGE_PREFIX, currentUri), 0);
            currentPage = Math.max(0, Math.min(currentPage, renderer.getPageCount() - 1));
            libraryPage.setVisibility(View.GONE);
            searchBar.setVisibility(View.GONE);
            brightnessBar.setVisibility(View.GONE);
            readerBar.setVisibility(View.VISIBLE);
            toolbarVisible = true;
            pdfPreview.setVisibility(View.VISIBLE);
            applyBlueLightToReader();
            showPage(currentPage);
        } catch (Exception error) {
            closePdf();
            libraryPage.setVisibility(View.VISIBLE);
            readerBar.setVisibility(View.GONE);
            pdfPreview.setVisibility(View.GONE);
            Toast.makeText(this, t("pdf_open_failed"), Toast.LENGTH_LONG).show();
        }
    }

    private void showPage(int pageNumber) {
        if (renderer == null || pageNumber < 0 || pageNumber >= renderer.getPageCount()) return;
        currentPage = pageNumber;
        if (currentUri != null) {
            preferences.edit().putInt(CollectionStorage.key(PAGE_PREFIX, currentUri), currentPage).apply();
        }
        final int token = ++pageRenderToken;
        final int requestedPage = currentPage;
        pageExecutor.execute(() -> {
            try {
                PdfRenderer.Page page;
                synchronized (MainActivity.this) {
                    if (renderer == null || requestedPage >= renderer.getPageCount()) return;
                    page = renderer.openPage(requestedPage);
                }
                Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2,
                        Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                mainHandler.post(() -> {
                    if (token != pageRenderToken || renderer == null) { bitmap.recycle(); return; }
                    if (pageBitmap != null) pageBitmap.recycle();
                    pageBitmap = bitmap;
                    pdfPreview.setImageBitmap(pageBitmap);
                    pdfPreview.setColorFilter(isColorMode() ? null : eInkFilter());
                    if (pageLabel != null) pageLabel.setText((requestedPage + 1) + " / " + renderer.getPageCount());
                    if (pageSeekBar != null) pageSeekBar.setProgress(requestedPage);
                    pdfPreview.post(this::fitPage);
                    if (readerBar != null) readerBar.invalidate();
                });
            } catch (Exception error) {
                mainHandler.post(() -> Toast.makeText(this, t("pdf_page_failed"),
                        Toast.LENGTH_SHORT).show());
            }
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
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                        && event.getPointerCount() >= 2) {
                    multiGesture = true;
                    multiDownY = averagePointerY(event);
                    multiLastY = multiDownY;
                    return true;
                }
                if (multiGesture) {
                    if (scaleDetector.isInProgress()) {
                        multiGesture = false;
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                            && event.getPointerCount() >= 2) {
                        multiLastY = averagePointerY(event);
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        float change = multiLastY - multiDownY;
                        if (Math.abs(change) > dp(45)) {
                            toolbarVisible = change > 0;
                            readerBar.setVisibility(toolbarVisible ? View.VISIBLE : View.GONE);
                        }
                        multiGesture = false;
                        return true;
                    }
                    return true;
                }
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.getX(); lastTouchY = event.getY();
                        downX = event.getX(); downY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE
                            && currentScale > 1.05f) {
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

    private float averagePointerY(MotionEvent event) {
        float total = 0;
        for (int i = 0; i < event.getPointerCount(); i++) total += event.getY(i);
        return total / Math.max(1, event.getPointerCount());
    }

    private class ReaderToolbarView extends View {
        private final Paint toolbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX;
        private boolean dragging;

        ReaderToolbarView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.rgb(232, 230, 222));
        }

        @Override protected void onDraw(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.scale(density, density);
            float width = getWidth() / density;
            toolbarPaint.setStyle(Paint.Style.FILL);
            toolbarPaint.setColor(Color.rgb(232, 230, 222));
            canvas.drawRect(0, 0, width, getHeight() / density, toolbarPaint);
            toolbarPaint.setTypeface(Typeface.create("serif", Typeface.BOLD));
            toolbarPaint.setTextSize(18);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            toolbarPaint.setTextAlign(Paint.Align.CENTER);
            int total = renderer == null ? 0 : renderer.getPageCount();
            canvas.drawText(total == 0 ? "0 / 0" : (currentPage + 1) + " / " + total,
                    width / 2f, 27, toolbarPaint);

            toolbarPaint.setColor(Color.rgb(100, 98, 91));
            toolbarPaint.setStrokeWidth(1.5f);
            float left = 84;
            float right = width - 124;
            canvas.drawLine(left, 58, right, 58, toolbarPaint);
            if (total > 1) {
                float knob = left + (right - left) * currentPage / (float) (total - 1);
                toolbarPaint.setColor(Color.rgb(63, 61, 55));
                canvas.drawCircle(knob, 58, 7, toolbarPaint);
            }
            drawReaderButton(canvas, 14, 40, false);
            drawReaderButton(canvas, width - 116, 40, true);
            drawCloseButton(canvas, width - 58, 40);
            toolbarPaint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }

        private void drawCloseButton(Canvas canvas, float x, float y) {
            toolbarPaint.setStyle(Paint.Style.FILL);
            toolbarPaint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(x, y - 27, x + 40, y + 19), 7, 7, toolbarPaint);
            toolbarPaint.setStyle(Paint.Style.STROKE);
            toolbarPaint.setStrokeWidth(2);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            canvas.drawLine(x + 12, y - 14, x + 28, y + 2, toolbarPaint);
            canvas.drawLine(x + 28, y - 14, x + 12, y + 2, toolbarPaint);
        }

        private void drawReaderButton(Canvas canvas, float x, float y, boolean next) {
            toolbarPaint.setStyle(Paint.Style.FILL);
            toolbarPaint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(x, y - 27, x + 48, y + 19), 7, 7, toolbarPaint);
            toolbarPaint.setStyle(Paint.Style.STROKE);
            toolbarPaint.setStrokeWidth(2);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            if (next) {
                canvas.drawLine(x + 19, y - 6, x + 26, y, toolbarPaint);
                canvas.drawLine(x + 26, y, x + 19, y + 6, toolbarPaint);
            } else {
                canvas.drawLine(x + 29, y - 6, x + 22, y, toolbarPaint);
                canvas.drawLine(x + 22, y, x + 29, y + 6, toolbarPaint);
            }
            toolbarPaint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float density = getResources().getDisplayMetrics().density;
            float x = event.getX() / density;
            float y = event.getY() / density;
            float width = getWidth() / density;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x;
                dragging = y > 35 && y < 75 && x > 70 && x < width - 106;
                if (dragging) seekTo(x, width);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && dragging) {
                seekTo(x, width);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                dragging = false;
                if (Math.abs(x - downX) < 10 && y < 60) {
                    if (x < 74) showPage(currentPage - 1);
                    else if (x > width - 122 && x < width - 62) showPage(currentPage + 1);
                    else if (x >= width - 62) closeReader();
                }
                return true;
            }
            return true;
        }

        private void seekTo(float x, float width) {
            if (renderer == null || renderer.getPageCount() < 2) return;
            float ratio = Math.max(0, Math.min(1, (x - 84) / (width - 208)));
            showPage(Math.round(ratio * (renderer.getPageCount() - 1)));
        }
    }

    private static class SearchDrawable extends Drawable {
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SearchDrawable() {
            iconPaint.setColor(Color.rgb(65, 63, 57));
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(2.2f);
        }

        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float cx = bounds.left + 9;
            float cy = bounds.centerY() - 1;
            canvas.drawCircle(cx, cy, 5, iconPaint);
            canvas.drawLine(cx + 4, cy + 4, cx + 9, cy + 9, iconPaint);
        }

        @Override public void setAlpha(int alpha) { iconPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            iconPaint.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 20; }
        @Override public int getIntrinsicHeight() { return 20; }
    }

    private static class ClearDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ClearDrawable() {
            paint.setColor(Color.rgb(85, 82, 75));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            setBounds(0, 0, 24, 24);
        }
        @Override public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            canvas.drawLine(b.left + 7, b.top + 7, b.right - 7, b.bottom - 7, paint);
            canvas.drawLine(b.right - 7, b.top + 7, b.left + 7, b.bottom - 7, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 24; }
        @Override public int getIntrinsicHeight() { return 24; }
    }

    private void closeReader() {
        closePdf();
        readerBar.setVisibility(View.GONE);
        pdfPreview.setVisibility(View.GONE);
        libraryPage.setVisibility(View.VISIBLE);
        if (searchBar != null) searchBar.setVisibility(View.VISIBLE);
        if (brightnessBar != null) brightnessBar.setVisibility(View.GONE);
        brightnessPanel = false;
        refreshPanelDimming();
        rebuildLibrary();
    }

    private boolean isColorMode() {
        return "color".equals(preferences.getString(COLOR_MODE, "mono"));
    }

    private ColorMatrixColorFilter eInkFilter() {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                0.35f, 0.55f, 0.10f, 0, 0,
                0.35f, 0.55f, 0.10f, 0, 0,
                0.35f, 0.55f, 0.10f, 0, 0,
                0, 0, 0, 1, 0
        }));
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
        pageRenderToken++;
        pageExecutor.shutdownNow();
        closePdf();
        if (pageBitmap != null) pageBitmap.recycle();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (collectionEditor) { finishCollectionEditor(false); return; }
        if (collectionPickerPanel) { collectionPickerPanel = false; selectedCollectionBooks.clear(); refreshPanelDimming(); kindleHome.invalidate(); return; }
        if (collectionActionPanel || settingsPanel || sortPanel || brightnessPanel) {
            collectionActionPanel = false;
            collectionColorPanel = false;
            settingsPanel = false;
            sortPanel = false;
            brightnessPanel = false;
            refreshPanelDimming();
            kindleHome.invalidate();
            return;
        }
        if (activeCollection != null) { activeCollection = null; homeContentOffset = 0; kindleHome.invalidate(); return; }
        if (collectionMode) { collectionMode = false; allBooksMode = false; kindleHome.invalidate(); return; }
        if (pdfPreview != null && pdfPreview.getVisibility() == View.VISIBLE) { closeReader(); return; }
        super.onBackPressed();
    }

    private void updateDim(int value) {
        updateDim(value, true);
    }

    private void updateDim(int value, boolean persist) {
        pendingDimValue = Math.max(0, Math.min(100, value));
        if (dimOverlay != null) {
            dimOverlay.setAlpha(Math.min(0.88f, pendingDimValue / 100f * 0.88f));
        }
        if (amountLabel != null) {
            amountLabel.setText(pendingDimValue + "%");
        }
        if (persist && preferences != null) {
            preferences.edit().putInt(DIM_LEVEL, pendingDimValue).apply();
        }
    }

    private void updateBlueLight(int value) {
        int safe = Math.max(0, Math.min(100, value));
        if (blueLightOverlay != null) blueLightOverlay.setAlpha(safe / 100f * 0.32f);
        if (preferences != null) preferences.edit().putInt(BLUE_LIGHT_LEVEL, safe).apply();
    }

    private void applyBlueLightToReader() {
        if (pdfPreview != null) pdfPreview.setColorFilter(isColorMode() ? null : eInkFilter());
    }

    private void refreshPanelDimming() {
        boolean dimSearch = settingsPanel || sortPanel || brightnessPanel
                || collectionActionPanel || collectionEditor || collectionPickerPanel;
        if (searchBar != null) searchBar.setAlpha(1f);
        if (panelDimOverlay != null) panelDimOverlay.setVisibility(dimSearch ? View.VISIBLE : View.GONE);
    }

    private void applyImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(immersive ? flags : 0);
    }

    private ArrayList<String> modernBookUris(boolean all) {
        ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                new HashSet<String>()));
        final int mode = preferences.getInt(SORT_MODE, 2);
        Collections.sort(books, (a, b) -> {
            if (mode == 2) {
                int recent = Long.compare(preferences.getLong(CollectionStorage.key(OPENED_PREFIX, b), 0L),
                        preferences.getLong(CollectionStorage.key(OPENED_PREFIX, a), 0L));
                if (recent != 0) return recent;
            }
            String an = displayName(Uri.parse(a)).toLowerCase(Locale.getDefault());
            String bn = displayName(Uri.parse(b)).toLowerCase(Locale.getDefault());
            return mode == 1 ? bn.compareTo(an) : an.compareTo(bn);
        });
        String query = searchBar == null ? "" : searchBar.getText().toString().trim().toLowerCase(Locale.getDefault());
        ArrayList<String> result = new ArrayList<>();
        for (String book : books) {
            if (activeCollection != null && !activeCollection.equals(collectionForBook(book))) continue;
            if (query.length() > 0 && !displayName(Uri.parse(book)).toLowerCase(Locale.getDefault()).contains(query)) continue;
            result.add(book);
        }
        if (!all && result.size() > 6) return new ArrayList<>(result.subList(0, 6));
        return result;
    }

    private String modernQuery() {
        return searchBar == null ? "" : searchBar.getText().toString().trim().toLowerCase(Locale.getDefault());
    }

    private void confirmRemovePdf(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(t("remove_book"))
                .setMessage(displayName(uri))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("delete"), (dialog, which) -> { removePdf(uri); if (modernHome != null) modernHome.rebuild(); })
                .show();
    }

    private void showModernCollectionActions(String name) {
        String[] options = {t("rename"), t("folder_color"), t("delete")};
        new AlertDialog.Builder(this).setTitle(name).setItems(options, (dialog, which) -> {
            if (which == 0) showModernCollectionEditor(name);
            else if (which == 1) showModernColorDialog(name);
            else confirmDeleteCollection(name);
        }).show();
    }

    private void showModernCollectionEditor(String existingName) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(existingName == null ? "" : existingName);
        input.setHint(t("collection_hint"));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(existingName == null ? t("new_collection") : t("rename"))
                .setView(input)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("save"), (dialog, which) -> {
                    String name = CollectionStorage.normalizeName(input.getText().toString());
                    if (name == null) {
                        Toast.makeText(this, t("collection_name_required"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                            new HashSet<String>()));
                    if (CollectionStorage.isDuplicateName(names, name, existingName)) {
                        Toast.makeText(this, t("collection_exists"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor editor = preferences.edit();
                    if (existingName != null) {
                        names.remove(existingName);
                        int color = preferences.getInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, existingName), 0);
                        editor.remove(CollectionStorage.key(COLLECTION_COLOR_PREFIX, existingName));
                        editor.putInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name), color);
                        for (String book : preferences.getStringSet(LIBRARY_URIS, new HashSet<String>())) {
                            if (existingName.equals(collectionForBook(book))) {
                                editor.putString(CollectionStorage.key(COLLECTION_PREFIX, book), name);
                            }
                        }
                        if (existingName.equals(activeCollection)) activeCollection = name;
                    }
                    names.add(name);
                    editor.putStringSet(COLLECTIONS, names).apply();
                    if (modernHome != null) modernHome.rebuild();
                }).show();
    }

    private void showModernColorDialog(String name) {
        String[] colors = {"Bej", "Mavi", "Pembe", "Yeşil", "Mor"};
        int selected = preferences.getInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name), 0);
        new AlertDialog.Builder(this).setTitle(t("folder_color"))
                .setSingleChoiceItems(colors, selected, (dialog, which) -> {
                    preferences.edit().putInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name), which).apply();
                    dialog.dismiss();
                    if (modernHome != null) modernHome.rebuild();
                }).show();
    }

    private void showModernCollectionPicker(String target) {
        ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                new HashSet<String>()));
        Collections.sort(books, (a, b) -> displayName(Uri.parse(a)).compareToIgnoreCase(displayName(Uri.parse(b))));
        String[] labels = new String[books.size()];
        boolean[] checked = new boolean[books.size()];
        for (int i = 0; i < books.size(); i++) {
            labels[i] = displayName(Uri.parse(books.get(i))).replace("▸  ", "");
            checked[i] = target.equals(collectionForBook(books.get(i)));
        }
        new AlertDialog.Builder(this).setTitle(t("add_from_library"))
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("save"), (dialog, which) -> {
                    SharedPreferences.Editor editor = preferences.edit();
                    for (int i = 0; i < books.size(); i++) {
                        if (checked[i]) editor.putString(CollectionStorage.key(COLLECTION_PREFIX, books.get(i)), target);
                        else if (target.equals(collectionForBook(books.get(i)))) editor.remove(CollectionStorage.key(COLLECTION_PREFIX, books.get(i)));
                    }
                    editor.apply();
                    if (modernHome != null) modernHome.rebuild();
                }).show();
    }

    private void showSettingsDialog() {
        String[] options = {t("language"), t("colors"), t("fullscreen")};
        new AlertDialog.Builder(this).setTitle(t("settings")).setItems(options, (dialog, which) -> {
            if (which == 0) showLanguageDialog();
            else if (which == 1) {
                String[] colors = {t("mono_color"), t("color_color")};
                int selected = isColorMode() ? 1 : 0;
                new AlertDialog.Builder(this).setTitle(t("colors")).setSingleChoiceItems(colors, selected, (d, item) -> {
                    preferences.edit().putString(COLOR_MODE, item == 1 ? "color" : "mono").apply();
                    d.dismiss();
                    if (modernHome != null) modernHome.rebuild();
                }).show();
            } else { immersive = !immersive; applyImmersiveMode(); }
        }).show();
    }

    private class ModernHomeView extends android.widget.ScrollView {
        private final LinearLayout content = new LinearLayout(MainActivity.this);
        private boolean rebuilding;

        ModernHomeView(android.content.Context context) {
            super(context);
            setFillViewport(true);
            setBackgroundColor(Color.rgb(232, 230, 222));
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(24), dp(104), dp(24), dp(28));
            addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
            post(this::rebuild);
        }

        @Override public void invalidate() {
            super.invalidate();
            if (!rebuilding) post(this::rebuild);
        }

        private TextView label(String value, int size, boolean bold) {
            TextView v = text(value, size, Color.rgb(58, 53, 45));
            v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
            v.setPadding(0, dp(4), 0, dp(4));
            return v;
        }

        private Button action(String value) {
            Button b = button(value);
            b.setAllCaps(false);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(dp(10), 0, dp(10), 0);
            return b;
        }

        void rebuild() {
            if (rebuilding || content == null) return;
            rebuilding = true;
            content.removeAllViews();

            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = label("FOLIO", 24, true);
            top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button light = action("☼");
            light.setOnClickListener(v -> showBrightnessDialog());
            top.addView(light, new LinearLayout.LayoutParams(dp(52), dp(48)));
            Button add = action("+");
            add.setOnClickListener(v -> pickPdf());
            top.addView(add, new LinearLayout.LayoutParams(dp(52), dp(48)));
            Button settings = action("⚙");
            settings.setOnClickListener(v -> showSettingsDialog());
            top.addView(settings, new LinearLayout.LayoutParams(dp(58), dp(48)));
            content.addView(top);

            TextView subtitle = label(t("pick_up"), 15, false);
            subtitle.setTextColor(Color.rgb(112, 105, 94));
            content.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(36)));

            LinearLayout tabs = new LinearLayout(MainActivity.this);
            tabs.setGravity(Gravity.CENTER_VERTICAL);
            Button recent = action(t("books"));
            recent.setOnClickListener(v -> { collectionMode = false; allBooksMode = false; activeCollection = null; rebuild(); });
            Button all = action(t("all_books"));
            all.setOnClickListener(v -> { collectionMode = false; allBooksMode = true; activeCollection = null; rebuild(); });
            Button folders = action(t("collections"));
            folders.setOnClickListener(v -> { collectionMode = true; allBooksMode = false; activeCollection = null; rebuild(); });
            tabs.addView(recent, new LinearLayout.LayoutParams(0, dp(46), 1));
            tabs.addView(all, new LinearLayout.LayoutParams(0, dp(46), 1));
            tabs.addView(folders, new LinearLayout.LayoutParams(0, dp(46), 1));
            content.addView(tabs);

            LinearLayout tools = new LinearLayout(MainActivity.this);
            tools.setGravity(Gravity.CENTER_VERTICAL);
            TextView mode = label(collectionMode ? t("collections") : (allBooksMode ? t("all_books") : t("recent_books")), 18, true);
            tools.addView(mode, new LinearLayout.LayoutParams(0, dp(46), 1));
            Button sort = action(t("filter"));
            sort.setOnClickListener(v -> showSortDialog());
            tools.addView(sort, new LinearLayout.LayoutParams(dp(100), dp(46)));
            content.addView(tools);

            if (collectionMode) buildCollections();
            else buildBooks();
            rebuilding = false;
        }

        private void buildBooks() {
            ArrayList<String> books = modernBookUris(allBooksMode);
            if (books.isEmpty()) {
                content.addView(label(modernQuery().length() == 0 ? t("empty_library") : t("no_search_results"), 17, false));
                Button add = action("+  " + t("open_pdf"));
                add.setOnClickListener(v -> pickPdf());
                content.addView(add, new LinearLayout.LayoutParams(-1, dp(54)));
                return;
            }
            for (String value : books) addBookCard(value);
        }

        private void addBookCard(String value) {
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(8), dp(6), dp(8));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(244, 242, 235));
            background.setStroke(dp(1), Color.rgb(193, 189, 178));
            background.setCornerRadius(dp(6));
            card.setBackground(background);
            TextView title = label(displayName(Uri.parse(value)).replace("▸  ", ""), 16, true);
            title.setSingleLine(true);
            card.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
            Button remove = action("×");
            remove.setOnClickListener(v -> confirmRemovePdf(Uri.parse(value)));
            card.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.setOnClickListener(v -> tryOpenPdf(Uri.parse(value)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(72));
            params.bottomMargin = dp(8);
            content.addView(card, params);
        }

        private void buildCollections() {
            if (activeCollection != null) {
                Button back = action("‹  " + activeCollection);
                back.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                back.setOnClickListener(v -> { activeCollection = null; rebuild(); });
                content.addView(back, new LinearLayout.LayoutParams(-1, dp(50)));
                Button add = action("+  " + t("add_from_library"));
                add.setOnClickListener(v -> showModernCollectionPicker(activeCollection));
                content.addView(add, new LinearLayout.LayoutParams(-1, dp(50)));
                for (String value : modernBookUris(true)) addBookCard(value);
                return;
            }
            Button create = action("+  " + t("new_collection"));
            create.setOnClickListener(v -> showModernCollectionEditor(null));
            content.addView(create, new LinearLayout.LayoutParams(-1, dp(50)));
            ArrayList<String> names = collectionNamesForModern();
            if (names.isEmpty()) content.addView(label(t("empty_collections"), 17, false));
            for (String name : names) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView folder = label("▰  " + name, 17, true);
                row.addView(folder, new LinearLayout.LayoutParams(0, dp(58), 1));
                Button menu = action("⋯");
                menu.setOnClickListener(v -> showModernCollectionActions(name));
                row.addView(menu, new LinearLayout.LayoutParams(dp(56), dp(48)));
                row.setOnClickListener(v -> { activeCollection = name; rebuild(); });
                content.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
            }
        }

        private ArrayList<String> collectionNamesForModern() {
            ArrayList<String> result = new ArrayList<>(preferences.getStringSet(COLLECTIONS, new HashSet<String>()));
            String query = modernQuery();
            if (query.length() > 0) {
                ArrayList<String> filtered = new ArrayList<>();
                for (String name : result) if (name.toLowerCase(Locale.getDefault()).contains(query)) filtered.add(name);
                result = filtered;
            }
            Collections.sort(result);
            return result;
        }
    }

    private class KindleHomeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float touchDownX;
        private float touchDownY;

        KindleHomeView(android.content.Context context) {
            super(context);
            paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            setBackgroundColor(Color.rgb(232, 230, 222));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.scale(density, density);
            int width = (int) (getWidth() / density);
            int height = (int) (getHeight() / density);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(232, 230, 222));
            canvas.drawRect(0, 0, width, height, paint);

            drawText(canvas, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()),
                    24, 29, 15, Color.rgb(78, 71, 57), false);
            drawIconButton(canvas, width - 166, 8, "☼");
            drawIconButton(canvas, width - 116, 8, "+");
            drawIconButton(canvas, width - 66, 8, "⚙");

            drawText(canvas, t("books"), 24, 129, 19, Color.rgb(56, 49, 37), !collectionMode && !allBooksMode);
            drawText(canvas, t("collections"), 142, 129, 19,
                    collectionMode ? Color.rgb(56, 49, 37) : Color.rgb(108, 98, 80), collectionMode);
            drawText(canvas, t("all_books"), 286, 129, 19,
                    allBooksMode ? Color.rgb(56, 49, 37) : Color.rgb(108, 98, 80), allBooksMode);
            int sortMode = preferences.getInt(SORT_MODE, 2);
            String sortLabel = sortMode == 0 ? t("sort_az")
                    : sortMode == 1 ? t("sort_za") : t("sort_recent");
            paint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(width - 190, 105, width - 22, 142), 8, 8, paint);
            drawText(canvas, t("filter") + ": " + sortLabel, width - 178, 129, 14,
                    Color.rgb(84, 76, 61), false);
            paint.setColor(Color.rgb(70, 63, 50));
            if (collectionMode) canvas.drawRect(142, 145, 270, 148, paint);
            else if (allBooksMode) canvas.drawRect(286, 145, 390, 148, paint);
            else canvas.drawRect(24, 145, 104, 148, paint);
            paint.setColor(Color.rgb(178, 176, 168));
            canvas.drawRect(22, 164, width - 22, 165, paint);
            if (collectionMode && activeCollection != null) {
                drawText(canvas, "‹  " + activeCollection, 24, 192, 21,
                        Color.rgb(65, 63, 57), true);
            }
            canvas.save();
            canvas.clipRect(0, 166, width, height);
            canvas.translate(0, homeContentOffset);
            ArrayList<String> books = visibleBooks();
            if (collectionMode && activeCollection == null) {
                drawNewCollectionButton(canvas, width);
                drawCollections(canvas, 24, 232, 0, 0, width);
            } else if (collectionMode && activeCollection != null) {
                drawCollectionBookList(canvas, books, 24, 230, width);
            } else if (allBooksMode) {
                drawAllBooksGrid(canvas, books, 24, 184, width);
            } else {
                int contentTop = collectionMode && activeCollection != null ? 214 : 184;
                if (collectionMode && activeCollection != null) drawCollectionAddButton(canvas, width);
                drawBookShelf(canvas, books, 24, contentTop, width);
            }
            canvas.restore();

            if (brightnessPanel) drawBrightnessPanel(canvas, width);
            if (sortPanel) drawSortPanel(canvas, width);
            if (settingsPanel) drawSettingsPanel(canvas, width);
            if (collectionActionPanel) drawCollectionActionPanel(canvas, width);
            if (collectionEditor) drawCollectionEditorPanel(canvas, width);
            if (collectionPickerPanel) drawCollectionPickerPanel(canvas, width);
            canvas.restore();
        }

        private void drawBookShelf(Canvas canvas, ArrayList<String> books, int left, int top, int width) {
            if (books.isEmpty()) {
                drawText(canvas, searchQuery().length() > 0 ? t("no_search_results") : t("empty_library"), left, top + 52, 18,
                        Color.rgb(113, 101, 80), false);
                if (searchQuery().length() == 0) drawText(canvas, "+  " + t("open_pdf"), left, top + 94, 17,
                        Color.rgb(74, 63, 43), true);
                return;
            }
            int gap = 16;
            int columns = bookColumnCount(width);
            int cardWidth = Math.max(110, (width - 48 - gap * (columns - 1)) / columns);
            int coverHeight = Math.max(170, (int) (cardWidth * 1.38f));
            drawText(canvas, t("recent_books"), left, top - 12, 18,
                    Color.rgb(58, 52, 43), true);
            ArrayList<String> recentBooks = recentBooks();
            int recentCount = Math.min(6, recentBooks.size());
            for (int i = 0; i < recentCount; i++) {
                int x = left + (i % columns) * (cardWidth + gap);
                int y = top + (i / columns) * (coverHeight + 60);
                drawBookCard(canvas, recentBooks.get(i), x, y, cardWidth, coverHeight);
            }
        }

        private void drawAllBooksGrid(Canvas canvas, ArrayList<String> books, int left,
                                      int top, int width) {
            if (books.isEmpty()) {
                drawText(canvas, searchQuery().length() > 0 ? t("no_search_results") : t("empty_library"), left, top + 52, 18,
                        Color.rgb(113, 101, 80), false);
                if (searchQuery().length() == 0) drawText(canvas, "+  " + t("open_pdf"), left, top + 94, 17,
                        Color.rgb(74, 63, 43), true);
                return;
            }
            int gap = 16;
            int columns = bookColumnCount(width);
            int cardWidth = Math.max(110, (width - 48 - gap * (columns - 1)) / columns);
            int coverHeight = Math.max(170, (int) (cardWidth * 1.38f));
            drawText(canvas, t("all_books"), left, top - 12, 18,
                    Color.rgb(58, 52, 43), true);
            for (int i = 0; i < books.size(); i++) {
                int x = left + (i % columns) * (cardWidth + gap);
                int y = top + (i / columns) * (coverHeight + 60);
                drawBookCard(canvas, books.get(i), x, y, cardWidth, coverHeight);
            }
        }

        private void drawBookCard(Canvas canvas, String book, int x, int y, int width, int height) {
            Bitmap thumbnail = loadThumbnail(book);
            if (thumbnail != null) {
                paint.setColorFilter(isColorMode() ? null : eInkFilter());
                canvas.drawBitmap(thumbnail, null, new RectF(x, y, x + width, y + height), paint);
                paint.setColorFilter(null);
            } else {
                paint.setColor(Color.rgb(188, 185, 175));
                canvas.drawRect(x, y, x + width, y + height, paint);
            }
            drawText(canvas, "FOLIO", x + 9, y + 24, 12, Color.rgb(245, 244, 239), true);
            String title = displayName(Uri.parse(book)).replace("▸  ", "");
            if (title.length() > 18) title = title.substring(0, 16) + "…";
            drawText(canvas, title, x + 9, y + height - 18, 14, Color.rgb(245, 244, 239), true);
            drawProgress(canvas, book, x, y + height + 22, width);
        }

        private void drawBookRow(Canvas canvas, String book, int x, int y, int right) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.rgb(93, 88, 78));
            canvas.drawRoundRect(new RectF(x, y + 6, x + 38, y + 46), 4, 4, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(x + 8, y + 15, x + 30, y + 17, paint);
            canvas.drawRect(x + 8, y + 23, x + 30, y + 25, paint);
            canvas.drawRect(x + 8, y + 31, x + 25, y + 33, paint);
            String title = displayName(Uri.parse(book)).replace("▸  ", "");
            if (title.length() > 28) title = title.substring(0, 26) + "…";
            drawText(canvas, title, x + 54, y + 23, 16, Color.rgb(58, 55, 49), true);
            drawProgress(canvas, book, x + 54, y + 42, right - x - 54);
        }

        private void drawCollectionBookList(Canvas canvas, ArrayList<String> books,
                                            int left, int top, int width) {
            drawText(canvas, books.size() + " " + t("books_count"), left, top + 18, 14,
                    Color.rgb(112, 109, 101), false);
            if (books.isEmpty()) {
                drawText(canvas, searchQuery().length() > 0 ? t("no_search_results")
                        : t("empty_collection"), left, top + 66, 18,
                        Color.rgb(113, 101, 80), false);
                if (searchQuery().length() == 0) {
                    drawText(canvas, "+  " + t("add_from_library"), left, top + 106, 16,
                            Color.rgb(74, 63, 43), true);
                }
                return;
            }
            paint.setColor(Color.rgb(213, 210, 201));
            canvas.drawRect(left, top + 32, width - 24, top + 33, paint);
            for (int i = 0; i < books.size(); i++) {
                int rowTop = top + 40 + i * 76;
                drawBookRow(canvas, books.get(i), left, rowTop, width - 24);
                paint.setColor(Color.rgb(213, 210, 201));
                canvas.drawRect(left, rowTop + 70, width - 24, rowTop + 71, paint);
            }
        }

        private void drawProgress(Canvas canvas, String book, int x, int y, int width) {
            int total = preferences.getInt(CollectionStorage.key("count_", book), 0);
            int page = preferences.getInt(CollectionStorage.key(PAGE_PREFIX, book), 0);
            int percent = total > 0 ? (page >= total - 1 ? 100 : ((page + 1) * 100) / total) : 0;
            drawText(canvas, total > 0 ? "%" + percent + " " + t("read") : t("new_book"),
                    x, y, 13, Color.rgb(85, 80, 70), false);
            paint.setColor(Color.rgb(145, 142, 133));
            canvas.drawRect(x, y + 9, x + width, y + 12, paint);
            if (total > 0) {
                paint.setColor(Color.rgb(70, 68, 63));
                canvas.drawRect(x, y + 9, x + width * percent / 100f, y + 12, paint);
            }
        }

        private ArrayList<String> visibleBooks() {
            ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                    new HashSet<String>()));
            final int mode = preferences.getInt(SORT_MODE, 2);
            Collections.sort(books, new Comparator<String>() {
                @Override public int compare(String first, String second) {
                    if (mode == 2) {
                        long firstOpened = preferences.getLong(CollectionStorage.key(OPENED_PREFIX, first), 0L);
                        long secondOpened = preferences.getLong(CollectionStorage.key(OPENED_PREFIX, second), 0L);
                        int recent = Long.compare(secondOpened, firstOpened);
                        if (recent != 0) return recent;
                    }
                    String firstName = displayName(Uri.parse(first)).toLowerCase(Locale.getDefault());
                    String secondName = displayName(Uri.parse(second)).toLowerCase(Locale.getDefault());
                    return mode == 1 ? secondName.compareTo(firstName) : firstName.compareTo(secondName);
                }
            });
            if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
                String query = searchBar.getText().toString().trim().toLowerCase();
                if (query.length() > 0) {
                    ArrayList<String> filtered = new ArrayList<>();
                    for (String book : books) {
                        if (displayName(Uri.parse(book)).toLowerCase().contains(query)) filtered.add(book);
                    }
                    books = filtered;
                }
            }
            if (collectionMode && activeCollection != null) {
                ArrayList<String> filtered = new ArrayList<>();
                for (String book : books) if (activeCollection.equals(collectionFor(book))) filtered.add(book);
                books = filtered;
            }
            return books;
        }

        private ArrayList<String> recentBooks() {
            ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                    new HashSet<String>()));
            Collections.sort(books, new Comparator<String>() {
                @Override public int compare(String first, String second) {
                    long firstOpened = preferences.getLong(CollectionStorage.key(OPENED_PREFIX, first), 0L);
                    long secondOpened = preferences.getLong(CollectionStorage.key(OPENED_PREFIX, second), 0L);
                    int recent = Long.compare(secondOpened, firstOpened);
                    if (recent != 0) return recent;
                    return displayName(Uri.parse(first)).compareToIgnoreCase(displayName(Uri.parse(second)));
                }
            });
            if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
                String query = searchBar.getText().toString().trim().toLowerCase();
                if (query.length() > 0) {
                    ArrayList<String> filtered = new ArrayList<>();
                    for (String book : books) {
                        if (displayName(Uri.parse(book)).toLowerCase().contains(query)) filtered.add(book);
                    }
                    books = filtered;
                }
            }
            if (collectionMode && activeCollection != null) {
                ArrayList<String> filtered = new ArrayList<>();
                for (String book : books) if (activeCollection.equals(collectionFor(book))) filtered.add(book);
                books = filtered;
            }
            return books;
        }

        private ArrayList<String> collectionNames() {
            Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                    new HashSet<String>()));
            ArrayList<String> result = new ArrayList<>(names);
            String query = collectionMode && activeCollection == null ? searchQuery() : "";
            if (query.length() > 0) {
                ArrayList<String> filtered = new ArrayList<>();
                for (String name : result) {
                    if (name.toLowerCase(Locale.getDefault()).contains(query)) filtered.add(name);
                }
                result = filtered;
            }
            Collections.sort(result);
            return result;
        }

        private String searchQuery() {
            return searchBar == null ? "" : searchBar.getText().toString().trim()
                    .toLowerCase(Locale.getDefault());
        }

        private int bookColumnCount(int width) {
            return Math.max(1, Math.min(6, (width - 48 + 16) / (152 + 16)));
        }

        private String collectionFor(String bookUri) {
            return MainActivity.this.collectionForBook(bookUri);
        }

        private void drawSortPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(width - 300, 104, width - 22, 294), 10, 10, paint);
            drawText(canvas, t("sort"), width - 272, 138, 19, Color.rgb(54, 53, 49), true);
            String[] labels = {t("sort_az"), t("sort_za"), t("sort_recent")};
            int selected = preferences.getInt(SORT_MODE, 2);
            for (int i = 0; i < labels.length; i++) {
                float cy = 178 + i * 42;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(Color.rgb(80, 78, 71));
                canvas.drawCircle(width - 266, cy - 5, 9, paint);
                if (selected == i) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(width - 242, cy - 5, 4, paint);
                }
                drawText(canvas, labels[i], width - 238, cy, 17, Color.rgb(61, 60, 55), false);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawBrightnessPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(width - 300, 54, width - 22, 226), 10, 10, paint);
            drawText(canvas, t("extra_dim"), width - 276, 86, 19,
                    Color.rgb(54, 53, 49), true);
            drawText(canvas, preferences.getInt(DIM_LEVEL, 0) + "%", width - 72, 86, 15,
                    Color.rgb(112, 109, 101), true);
            paint.setColor(Color.rgb(80, 78, 71));
            paint.setStrokeWidth(2);
            canvas.drawLine(width - 276, 124, width - 46, 124, paint);
            float knob = width - 276 + 230 * (100 - Math.min(100,
                    preferences.getInt(DIM_LEVEL, 0))) / 100f;
            paint.setColor(Color.rgb(55, 53, 48));
            canvas.drawCircle(knob, 124, 8, paint);
            drawText(canvas, t("blue_light"), width - 276, 164, 17,
                    Color.rgb(54, 53, 49), true);
            drawText(canvas, preferences.getInt(BLUE_LIGHT_LEVEL, 0) + "%", width - 72, 164, 15,
                    Color.rgb(112, 109, 101), true);
            paint.setColor(Color.rgb(80, 78, 71));
            canvas.drawLine(width - 276, 198, width - 46, 198, paint);
            float blueKnob = width - 276 + 230 * preferences.getInt(BLUE_LIGHT_LEVEL, 0) / 100f;
            paint.setColor(Color.rgb(121, 101, 58));
            canvas.drawCircle(blueKnob, 198, 8, paint);
        }

        private void drawSettingsPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            int colorTop = languageExpanded ? 238 : 154;
            int panelBottom = colorExpanded ? colorTop + 104 : colorTop + 48;
            canvas.drawRoundRect(new RectF(width - 300, 54, width - 22, panelBottom), 10, 10, paint);
            drawText(canvas, t("settings"), width - 272, 88, 21, Color.rgb(54, 53, 49), true);
            drawText(canvas, t("language"), width - 272, 128, 18, Color.rgb(61, 60, 55), true);
            if (languageExpanded) {
                drawText(canvas, "Türkçe", width - 242, 166, 17,
                        "tr".equals(language) ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        "tr".equals(language));
                drawText(canvas, "English", width - 242, 204, 17,
                        "en".equals(language) ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        "en".equals(language));
                colorTop = 238;
            }
            drawText(canvas, t("colors"), width - 272, colorTop, 18, Color.rgb(61, 60, 55), true);
            if (colorExpanded) {
                drawText(canvas, t("mono_color"), width - 242, colorTop + 42, 17,
                        isColorMode() ? Color.rgb(130, 127, 119) : Color.rgb(45, 44, 40),
                        !isColorMode());
                drawText(canvas, t("color_color"), width - 242, colorTop + 82, 17,
                        isColorMode() ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        isColorMode());
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCollections(Canvas canvas, int left, int top, int cardWidth,
                                     int coverHeight, int width) {
            ArrayList<String> folders = collectionNames();
            Set<String> books = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
            if (folders.isEmpty()) {
                drawText(canvas, searchQuery().length() > 0 ? t("no_search_results")
                        : t("empty_collections"), left, top + 50, 17,
                        Color.rgb(113, 101, 80), false);
                return;
            }
            int gap = 16;
            int columns = Math.max(1, Math.min(3, (width - 56 + gap) / (220 + gap)));
            int folderWidth = (width - 56 - gap * (columns - 1)) / columns;
            for (int i = 0; i < folders.size(); i++) {
                int x = left + (i % columns) * (folderWidth + gap);
                int y = top + (i / columns) * 128;
                paint.setColor(collectionCardColor(folders.get(i)));
                canvas.drawRoundRect(new RectF(x, y, x + folderWidth, y + 108), 7, 7, paint);
                paint.setColor(isColorMode() ? Color.rgb(133, 106, 63) : Color.rgb(91, 89, 83));
                canvas.drawRect(x + 14, y + 27, x + 35, y + 43, paint);
                canvas.drawRect(x + 16, y + 23, x + 27, y + 29, paint);
                paint.setColor(isColorMode() ? Color.rgb(208, 188, 151) : Color.rgb(202, 200, 192));
                canvas.drawRoundRect(new RectF(x + folderWidth - 48, y + 10,
                        x + folderWidth - 12, y + 42), 6, 6, paint);
                drawText(canvas, "…", x + folderWidth - 38, y + 32, 18,
                        Color.rgb(75, 72, 65), true);
                drawText(canvas, folders.get(i), x + 14, y + 72, 17, Color.rgb(71, 59, 43), true);
                int count = 0;
                for (String book : books) if (folders.get(i).equals(collectionFor(book))) count++;
                drawText(canvas, count + " " + t("books_count"), x + 14, y + 96, 13,
                        Color.rgb(120, 104, 77), false);
            }
        }

        private void drawNewCollectionButton(Canvas canvas, int width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.rgb(126, 123, 115));
            canvas.drawRoundRect(new RectF(width - 210, 176, width - 24, 216), 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, "+  " + t("new_collection"), width - 194, 202, 15,
                    Color.rgb(66, 64, 58), true);
        }

        private void drawCollectionAddButton(Canvas canvas, int width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(Color.rgb(126, 123, 115));
            canvas.drawRoundRect(new RectF(width - 210, 160, width - 24, 200), 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, "+  " + t("add_from_library"), width - 194, 186, 15,
                    Color.rgb(66, 64, 58), true);
        }

        private int collectionCardColor(String name) {
            int selected = preferences.getInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX, name), 0);
            int[] color = isColorMode()
                    ? new int[] {Color.rgb(218, 201, 165), Color.rgb(190, 207, 215),
                    Color.rgb(211, 191, 193), Color.rgb(196, 207, 182), Color.rgb(207, 196, 220)}
                    : new int[] {Color.rgb(214, 212, 204), Color.rgb(205, 211, 213),
                    Color.rgb(214, 205, 206), Color.rgb(207, 213, 202), Color.rgb(210, 205, 215)};
            return color[Math.max(0, Math.min(4, selected))];
        }

        private void drawCollectionPickerPanel(Canvas canvas, int width) {
            int height = (int) (getHeight() / getResources().getDisplayMetrics().density);
            paint.setColor(Color.argb(72, 40, 39, 35));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(22, 104, width - 22, height - 28), 10, 10, paint);
            drawText(canvas, t("add_from_library"), 46, 142, 21,
                    Color.rgb(54, 53, 49), true);
            drawText(canvas, collectionPickerTarget == null ? "" : collectionPickerTarget,
                    46, 169, 14, Color.rgb(112, 109, 101), false);
            ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                    new HashSet<String>()));
            Collections.sort(books, new Comparator<String>() {
                @Override public int compare(String a, String b) {
                    return displayName(Uri.parse(a)).compareToIgnoreCase(displayName(Uri.parse(b)));
                }
            });
            int rowTop = 184;
            int listBottom = height - 104;
            int visibleHeight = Math.max(0, listBottom - rowTop);
            int maxOffset = Math.max(0, books.size() * 58 - visibleHeight);
            collectionPickerOffset = Math.max(-maxOffset, Math.min(0, collectionPickerOffset));
            canvas.save();
            canvas.clipRect(34, rowTop, width - 34, listBottom);
            canvas.translate(0, collectionPickerOffset);
            for (int i = 0; i < books.size(); i++) {
                String book = books.get(i);
                int y = rowTop + i * 58;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);
                paint.setColor(Color.rgb(90, 87, 80));
                canvas.drawRoundRect(new RectF(46, y, 76, y + 30), 4, 4, paint);
                if (selectedCollectionBooks.contains(book)) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(51, y + 5, 71, y + 25, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                String title = displayName(Uri.parse(book));
                if (title.length() > 32) title = title.substring(0, 30) + "…";
                String currentCollection = collectionFor(book);
                String suffix = currentCollection == null || currentCollection.equals(collectionPickerTarget)
                        ? "" : " · " + t("in_collection") + " " + currentCollection;
                drawText(canvas, title + suffix, 92, y + 21, 16, Color.rgb(58, 55, 49), true);
            }
            canvas.restore();
            drawText(canvas, t("cancel"), width - 178, height - 56, 15,
                    Color.rgb(112, 109, 101), true);
            drawText(canvas, t("add"), width - 92, height - 56, 15,
                    Color.rgb(61, 60, 55), true);
        }

        private void drawCollectionActionPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(55, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(width - 282, 132, width - 24,
                    collectionColorPanel ? 330 : 292), 10, 10, paint);
            drawText(canvas, pendingCollectionAction == null ? "" : pendingCollectionAction,
                    width - 256, 164, 18, Color.rgb(54, 53, 49), true);
            drawText(canvas, t("rename"), width - 256, 204, 17, Color.rgb(61, 60, 55), false);
            drawText(canvas, t("folder_color"), width - 256, 242, 17, Color.rgb(61, 60, 55), false);
            drawText(canvas, t("delete"), width - 256, collectionColorPanel ? 306 : 276,
                    17, Color.rgb(100, 62, 55), false);
            if (collectionColorPanel) {
                int[] swatches = {Color.rgb(218, 201, 165), Color.rgb(190, 207, 215),
                        Color.rgb(211, 191, 193), Color.rgb(196, 207, 182), Color.rgb(207, 196, 220)};
                for (int i = 0; i < swatches.length; i++) {
                    paint.setColor(swatches[i]);
                    canvas.drawCircle(width - 242 + i * 38, 274, 12, paint);
                }
            }
            drawText(canvas, t("cancel"), width - 256, collectionColorPanel ? 328 : 286,
                    14, Color.rgb(112, 109, 101), false);
        }

        private void drawCollectionEditorPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(55, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(48, 170, width - 48, 372), 8, 8, paint);
            drawText(canvas, editingCollection == null ? t("new_collection") : t("rename"),
                    72, 208, 20, Color.rgb(54, 53, 49), true);
            drawText(canvas, t("collection_hint"), 72, 244, 14,
                    Color.rgb(112, 109, 101), false);
            drawText(canvas, t("cancel"), width - 170, 350, 14,
                    Color.rgb(112, 109, 101), true);
            drawText(canvas, t("save"), width - 92, 350, 14,
                    Color.rgb(61, 60, 55), true);
        }

        private Bitmap loadThumbnail(String bookUri) {
            if (thumbnailCache.containsKey(bookUri)) return thumbnailCache.get(bookUri);
            try {
                ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(Uri.parse(bookUri), "r");
                PdfRenderer pdf = new PdfRenderer(descriptor);
                PdfRenderer.Page page = pdf.openPage(0);
                int width = 180;
                int height = Math.max(220, (int) (width * ((float) page.getHeight() / page.getWidth())));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                pdf.close();
                descriptor.close();
                thumbnailCache.put(bookUri, bitmap);
                return bitmap;
            } catch (Exception error) {
                thumbnailCache.put(bookUri, null);
                return null;
            }
        }

        private void drawIconButton(Canvas canvas, int x, int y, String icon) {
            paint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(x, y, x + 42, y + 40), 8, 8, paint);
            paint.setColor(Color.rgb(65, 56, 43));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            float cx = x + 21;
            float cy = y + 20;
            if ("☼".equals(icon)) {
                canvas.drawCircle(cx, cy, 5, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    float x1 = cx + (float) Math.cos(angle) * 9;
                    float y1 = cy + (float) Math.sin(angle) * 9;
                    float x2 = cx + (float) Math.cos(angle) * 13;
                    float y2 = cy + (float) Math.sin(angle) * 13;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else if ("+".equals(icon)) {
                paint.setStrokeWidth(2.8f);
                canvas.drawLine(cx - 7, cy, cx + 7, cy, paint);
                canvas.drawLine(cx, cy - 7, cx, cy + 7, paint);
            } else if ("⚙".equals(icon)) {
                canvas.drawCircle(cx, cy, 8, paint);
                canvas.drawCircle(cx, cy, 2, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    float x1 = cx + (float) Math.cos(angle) * 9;
                    float y1 = cy + (float) Math.sin(angle) * 9;
                    float x2 = cx + (float) Math.cos(angle) * 13;
                    float y2 = cy + (float) Math.sin(angle) * 13;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else if ("⛶".equals(icon)) {
                canvas.drawLine(x + 9, y + 14, x + 9, y + 9, paint);
                canvas.drawLine(x + 9, y + 9, x + 14, y + 9, paint);
                canvas.drawLine(x + 27, y + 14, x + 27, y + 9, paint);
                canvas.drawLine(x + 27, y + 9, x + 22, y + 9, paint);
                canvas.drawLine(x + 9, y + 20, x + 9, y + 25, paint);
                canvas.drawLine(x + 9, y + 25, x + 14, y + 25, paint);
                canvas.drawLine(x + 27, y + 20, x + 27, y + 25, paint);
                canvas.drawLine(x + 27, y + 25, x + 22, y + 25, paint);
            } else {
                canvas.drawCircle(x + 16, y + 15, 6, paint);
                canvas.drawLine(x + 20, y + 20, x + 26, y + 26, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawText(Canvas canvas, String value, float x, float y, float size,
                              int color, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create(bold ? "serif" : "sans",
                    bold ? Typeface.BOLD : Typeface.NORMAL));
            canvas.drawText(value, x, y, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (brightnessPanel && (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE)) {
                float density = getResources().getDisplayMetrics().density;
                float x = event.getX() / density;
                float y = event.getY() / density;
                float width = getWidth() / density;
                if (x >= width - 300 && x <= width - 22 && y >= 92 && y <= 146) {
                    int value = Math.max(0, Math.min(100,
                            Math.round(((width - 46) - x) / 230f * 100f)));
                    updateDim(value);
                    invalidate();
                } else if (x >= width - 300 && x <= width - 22 && y >= 166 && y <= 220) {
                    int value = Math.max(0, Math.min(100,
                            Math.round((x - (width - 276)) / 230f * 100f)));
                    updateBlueLight(value);
                    invalidate();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                searchBar.clearFocus();
                root.requestFocus();
                touchDownX = event.getX(); touchDownY = event.getY();
                homeDragging = false;
                collectionPickerDragging = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && collectionPickerPanel) {
                float density = getResources().getDisplayMetrics().density;
                float y = event.getY() / density;
                float height = getHeight() / density;
                float delta = (event.getY() - touchDownY) / density;
                if (y >= 184 && y < height - 104 && Math.abs(delta) > 12) {
                    ArrayList<String> books = new ArrayList<>(preferences.getStringSet(
                            LIBRARY_URIS, new HashSet<String>()));
                    int visibleHeight = Math.max(0, (int) height - 258);
                    int maxOffset = Math.max(0, books.size() * 58 - visibleHeight);
                    collectionPickerOffset = Math.max(-maxOffset,
                            Math.min(0, collectionPickerOffset + delta));
                    touchDownY = event.getY();
                    collectionPickerDragging = true;
                    invalidate();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && !collectionEditor
                    && !collectionActionPanel && !collectionPickerPanel && !settingsPanel
                    && !sortPanel && !brightnessPanel
                    && event.getY() / getResources().getDisplayMetrics().density > 160) {
                float density = getResources().getDisplayMetrics().density;
                float delta = (event.getY() - touchDownY) / density;
                if (Math.abs(delta) > 12) {
                    homeContentOffset = Math.max(-homeMaxScroll(), Math.min(0,
                            homeContentOffset + delta));
                    touchDownY = event.getY();
                    homeDragging = true;
                    invalidate();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float density = getResources().getDisplayMetrics().density;
                float x = event.getX() / density; float y = event.getY() / density;
                float width = getWidth() / density; float height = getHeight() / density;
                if (collectionEditor) {
                    if (y >= 320 && y <= 372 && x >= width - 190 && x <= width - 34) {
                        finishCollectionEditor(true);
                    } else if (y >= 320 && y <= 372 && x >= width - 290 && x < width - 190) {
                        finishCollectionEditor(false);
                    }
                    return true;
                }
                if (collectionPickerPanel) {
                    if (collectionPickerDragging) {
                        collectionPickerDragging = false;
                        return true;
                    }
                    ArrayList<String> pickerBooks = new ArrayList<>(preferences.getStringSet(
                            LIBRARY_URIS, new HashSet<String>()));
                    Collections.sort(pickerBooks, new Comparator<String>() {
                        @Override public int compare(String a, String b) {
                            return displayName(Uri.parse(a)).compareToIgnoreCase(displayName(Uri.parse(b)));
                        }
                    });
                    int listBottom = (int) height - 104;
                    if (y >= 184 && y < listBottom && x >= 34 && x <= width - 34) {
                        int index = (int) ((y - collectionPickerOffset - 184) / 58);
                        if (index >= 0 && index < pickerBooks.size()) {
                            String book = pickerBooks.get(index);
                            if (selectedCollectionBooks.contains(book)) selectedCollectionBooks.remove(book);
                            else selectedCollectionBooks.add(book);
                            invalidate();
                        }
                    } else if (y >= height - 92 && x >= width - 130) {
                        for (String book : pickerBooks) {
                            if (collectionPickerTarget.equals(collectionFor(book))
                                    && !selectedCollectionBooks.contains(book)) {
                                preferences.edit().remove(CollectionStorage.key(
                                        COLLECTION_PREFIX, book)).apply();
                            }
                        }
                        for (String book : selectedCollectionBooks) {
                            saveCollection(Uri.parse(book), collectionPickerTarget);
                        }
                        selectedCollectionBooks.clear();
                        collectionPickerPanel = false;
                        collectionPickerTarget = null;
                        collectionPickerOffset = 0;
                        refreshPanelDimming();
                        invalidate();
                    } else if (y >= height - 92 && x >= width - 220) {
                        selectedCollectionBooks.clear();
                        collectionPickerPanel = false;
                        collectionPickerTarget = null;
                        collectionPickerOffset = 0;
                        refreshPanelDimming();
                        invalidate();
                    }
                    return true;
                }
                if (collectionActionPanel) {
                    if (x >= width - 282 && x <= width - 24 && y >= 178 && y < 220) {
                        showCollectionEditor(pendingCollectionAction);
                    } else if (x >= width - 282 && x <= width - 24 && y >= 220 && y < 260) {
                        collectionColorPanel = !collectionColorPanel;
                        invalidate();
                    } else if (collectionColorPanel && x >= width - 282 && x <= width - 30
                            && y >= 258 && y < 292) {
                        int color = Math.max(0, Math.min(4, (int) ((x - (width - 260)) / 38)));
                        preferences.edit().putInt(CollectionStorage.key(COLLECTION_COLOR_PREFIX,
                                pendingCollectionAction), color).apply();
                        collectionColorPanel = false;
                        collectionActionPanel = false;
                        pendingCollectionAction = null;
                        refreshPanelDimming();
                        invalidate();
                    } else if (x >= width - 282 && x <= width - 24
                            && y >= (collectionColorPanel ? 286 : 260)
                            && y < (collectionColorPanel ? 322 : 292)) {
                        confirmDeleteCollection(pendingCollectionAction);
                    } else {
                        collectionActionPanel = false;
                        collectionColorPanel = false;
                        pendingCollectionAction = null;
                        refreshPanelDimming();
                        invalidate();
                    }
                    return true;
                }
                if (brightnessPanel) {
                    if (x >= width - 300 && x <= width - 22 && y >= 92 && y <= 146) {
                        int value = Math.max(0, Math.min(100,
                                Math.round(((width - 46) - x) / 230f * 100f)));
                        updateDim(value);
                        invalidate();
                        return true;
                    }
                    if (x >= width - 300 && x <= width - 22 && y >= 166 && y <= 220) {
                        int value = Math.max(0, Math.min(100,
                                Math.round((x - (width - 276)) / 230f * 100f)));
                        updateBlueLight(value);
                        invalidate();
                        return true;
                    }
                    brightnessPanel = false;
                    refreshPanelDimming();
                    invalidate();
                    return true;
                }
                if (sortPanel) {
                    if (x >= width - 300 && x <= width - 22 && y >= 150 && y <= 294) {
                        int selected = Math.max(0, Math.min(2, (int) ((y - 150) / 42)));
                        preferences.edit().putInt(SORT_MODE, selected).apply();
                    }
                    sortPanel = false;
                    refreshPanelDimming();
                    invalidate();
                    return true;
                }
                if (settingsPanel) {
                    if (x >= width - 300 && x <= width - 22 && y >= 98 && y <= 142) {
                        languageExpanded = !languageExpanded;
                        colorExpanded = false;
                        invalidate();
                    } else if (languageExpanded && x >= width - 300 && x <= width - 22
                            && y > 142 && y <= 220) {
                        language = y <= 184 ? "tr" : "en";
                        preferences.edit().putString(LANGUAGE, language).apply();
                        languageExpanded = false;
                        settingsPanel = false;
                        refreshPanelDimming();
                        recreate();
                    } else if (x >= width - 300 && x <= width - 22
                            && y >= (languageExpanded ? 220 : 142)
                            && y <= (languageExpanded ? 266 : 190)) {
                        colorExpanded = !colorExpanded;
                        invalidate();
                    } else if (colorExpanded && x >= width - 300 && x <= width - 22
                            && y > (languageExpanded ? 266 : 190)
                            && y <= (languageExpanded ? 348 : 272)) {
                        boolean color = y > (languageExpanded ? 306 : 230);
                        preferences.edit().putString(COLOR_MODE, color ? "color" : "mono").apply();
                        if (pdfPreview != null) pdfPreview.setColorFilter(color ? null : eInkFilter());
                        colorExpanded = false;
                        invalidate();
                    } else {
                        settingsPanel = false;
                        languageExpanded = false;
                        colorExpanded = false;
                        refreshPanelDimming();
                        invalidate();
                    }
                    return true;
                }
                if (y < 55 && x >= width - 182 && x < width - 132) {
                    brightnessPanel = !brightnessPanel;
                    settingsPanel = false;
                    sortPanel = false;
                    brightnessBar.setVisibility(View.GONE);
                    searchBar.clearFocus();
                    refreshPanelDimming();
                    invalidate();
                    return true;
                }
                if (y < 55 && x >= width - 132 && x < width - 82) {
                    settingsPanel = false;
                    sortPanel = false;
                    brightnessPanel = false;
                    refreshPanelDimming();
                    pickPdf(); return true;
                }
                if (y < 55 && x >= width - 82) {
                    settingsPanel = !settingsPanel;
                    sortPanel = false;
                    brightnessPanel = false;
                    languageExpanded = false;
                    colorExpanded = false;
                    searchBar.clearFocus();
                    refreshPanelDimming();
                    invalidate(); return true;
                }
                if (y >= 105 && y <= 150 && x > width - 205) {
                    sortPanel = !sortPanel;
                    settingsPanel = false;
                    brightnessPanel = false;
                    refreshPanelDimming();
                    invalidate();
                    return true;
                }
                if (y >= 105 && y <= 150 && x > 110 && x < 285) {
                    collectionMode = true;
                    allBooksMode = false;
                    activeCollection = null;
                    homeContentOffset = 0;
                    invalidate(); return true;
                }
                if (y >= 105 && y <= 150 && x >= 285 && x <= width - 210) {
                    collectionMode = false;
                    allBooksMode = true;
                    activeCollection = null;
                    homeContentOffset = 0;
                    invalidate(); return true;
                }
                if (y >= 105 && y <= 150 && x <= 110) {
                    collectionMode = false;
                    allBooksMode = false;
                    activeCollection = null;
                    homeContentOffset = 0;
                    invalidate(); return true;
                }
                if (collectionMode && activeCollection != null && y > 166 && y < 214 && x < 290) {
                    activeCollection = null;
                    homeContentOffset = 0;
                    invalidate();
                    return true;
                }
                if (collectionMode && activeCollection == null && y >= 166 && y <= 214
                        && x >= width - 205) {
                    showNewCollectionDialog();
                    return true;
                }
                if (homeDragging) return true;
                float contentY = y - homeContentOffset;
                if (collectionMode && activeCollection != null && contentY >= 160
                        && contentY <= 206 && x >= width - 220) {
                    collectionPickerTarget = activeCollection;
                    selectedCollectionBooks.clear();
                    for (String book : preferences.getStringSet(LIBRARY_URIS,
                            new HashSet<String>())) {
                        if (activeCollection.equals(collectionFor(book))) {
                            selectedCollectionBooks.add(book);
                        }
                    }
                    collectionPickerOffset = 0;
                    collectionPickerDragging = false;
                    collectionPickerPanel = true;
                    refreshPanelDimming();
                    invalidate();
                    return true;
                }
                if (collectionMode && activeCollection == null) {
                    ArrayList<String> folders = collectionNames();
                    int gap = 16;
                    int columns = Math.max(1, Math.min(3, (int) (width - 56 + gap) / (220 + gap)));
                    int folderWidth = ((int) width - 56 - gap * (columns - 1)) / columns;
                    if (contentY >= 232) {
                        int row = (int) ((contentY - 232) / 128);
                        int column = Math.max(0, Math.min(columns - 1,
                                (int) ((x - 28) / (folderWidth + gap))));
                        int index = row * columns + column;
                        int folderX = 28 + column * (folderWidth + gap);
                        int folderY = 232 + row * 128;
                        if (index < folders.size() && x >= folderX && x <= folderX + folderWidth
                                && contentY >= folderY && contentY <= folderY + 108) {
                            if (x >= folderX + folderWidth - 62) {
                                pendingCollectionAction = folders.get(index);
                                collectionActionPanel = true;
                                collectionColorPanel = false;
                                refreshPanelDimming();
                            } else {
                                activeCollection = folders.get(index);
                                homeContentOffset = 0;
                            }
                            invalidate();
                        }
                    }
                    return true;
                }
                if (collectionMode && activeCollection != null) {
                    if (contentY >= 270 && x >= 24 && x <= width - 24) {
                        int row = (int) ((contentY - 270) / 76);
                        int rowTop = 270 + row * 76;
                        ArrayList<String> books = visibleBooks();
                        if (contentY <= rowTop + 70 && row >= 0 && row < books.size()) {
                            tryOpenPdf(Uri.parse(books.get(row)));
                        }
                    }
                    return true;
                }
                if (contentY > 170 && contentY < height) {
                    ArrayList<String> books = visibleBooks();
                    ArrayList<String> recentBooks = recentBooks();
                    int gap = 16;
                    int columns = bookColumnCount((int) width);
                    int cardWidth = Math.max(110, (int) ((width - 48 - gap * (columns - 1)) / columns));
                    int coverHeight = Math.max(170, (int) (cardWidth * 1.38f));
                    int bookTop = 184;
                    ArrayList<String> source = allBooksMode ? books : recentBooks;
                    if (contentY >= bookTop && x >= 24 && x <= width - 24) {
                        int column = (int) ((x - 24) / (cardWidth + gap));
                        int row = (int) ((contentY - bookTop) / (coverHeight + 60));
                        int cardLeft = 24 + column * (cardWidth + gap);
                        int cardTop = bookTop + row * (coverHeight + 60);
                        int index = row * columns + column;
                        if (column < columns && x <= cardLeft + cardWidth
                                && contentY >= cardTop && contentY <= cardTop + coverHeight + 44
                                && index >= 0 && index < source.size()) {
                            tryOpenPdf(Uri.parse(source.get(index)));
                        } else if (books.isEmpty() && searchQuery().length() == 0) {
                            pickPdf();
                        }
                        return true;
                    }
                }
            }
            return true;
        }

        private float homeMaxScroll() {
            int width = (int) (getWidth() / getResources().getDisplayMetrics().density);
            int height = (int) (getHeight() / getResources().getDisplayMetrics().density);
            if (collectionMode && activeCollection == null) {
                int columns = Math.max(1, Math.min(3, (width - 56 + 16) / (220 + 16)));
                int rows = (collectionNames().size() + columns - 1) / columns;
                return Math.max(0, 232 + rows * 128 + 40 - height + 36);
            }
            int count = visibleBooks().size();
            int gap = 16;
            if (collectionMode && activeCollection != null) {
                return Math.max(0, 270 + count * 76 + 40 - height + 36);
            }
            int columns = bookColumnCount(width);
            int cardWidth = Math.max(110, (width - 48 - gap * (columns - 1)) / columns);
            int coverHeight = Math.max(170, (int) (cardWidth * 1.38f));
            int rows = allBooksMode ? Math.max(1, (count + columns - 1) / columns)
                    : Math.max(1, (Math.min(6, count) + columns - 1) / columns);
            int contentHeight = 184 + rows * (coverHeight + 60) + 40;
            return Math.max(0, contentHeight - height + 36);
        }
    }

    private void showSortDialog() {
        String[] options = {t("sort_az"), t("sort_za"), t("sort_recent")};
        int selected = preferences.getInt(SORT_MODE, 2);
        new AlertDialog.Builder(this)
                .setTitle(t("sort"))
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    preferences.edit().putInt(SORT_MODE, which).apply();
                    dialog.dismiss();
                    if (kindleHome != null) kindleHome.invalidate();
                })
                .show();
    }

    private void showBrightnessDialog() {
        final SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(preferences.getInt(DIM_LEVEL, 0));
        brightness.setPadding(dp(20), dp(12), dp(20), dp(12));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value, false);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { updateDim(bar.getProgress()); }
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
        if ("recent_books".equals(key)) return english ? "Recently opened" : "Son açılanlar";
        if ("all_books".equals(key)) return english ? "All books" : "Tüm kitaplar";
        if ("collections".equals(key)) return english ? "Collections" : "Koleksiyonlar";
        if ("authors".equals(key)) return english ? "Authors" : "Yazarlar";
        if ("filter_all".equals(key)) return english ? "Filter: All" : "Filtrele: Tümü";
        if ("filter".equals(key)) return english ? "Sort" : "Sırala";
        if ("sort".equals(key)) return english ? "Sort books" : "Kitapları sırala";
        if ("sort_az".equals(key)) return "A-Z";
        if ("sort_za".equals(key)) return "Z-A";
        if ("sort_recent".equals(key)) return english ? "Recently opened" : "Son açılanlar";
        if ("settings".equals(key)) return english ? "Settings" : "Ayarlar";
        if ("colors".equals(key)) return english ? "Display colors" : "Renkler";
        if ("mono_color".equals(key)) return english ? "Grayscale" : "Gri tonlama";
        if ("color_color".equals(key)) return english ? "Color" : "Renkli";
        if ("new_collection".equals(key)) return english ? "New collection" : "Yeni koleksiyon";
        if ("new_collection_message".equals(key)) return english ? "Name this collection." : "Bu koleksiyona bir ad ver.";
        if ("rename".equals(key)) return english ? "Rename" : "Düzenle";
        if ("delete".equals(key)) return english ? "Delete" : "Sil";
        if ("folder_color".equals(key)) return english ? "Folder color" : "Klasör rengi";
        if ("add_from_library".equals(key)) return english ? "Add from library" : "Kitaplıktan ekle";
        if ("add".equals(key)) return english ? "Add" : "Ekle";
        if ("cancel".equals(key)) return english ? "Cancel" : "İptal";
        if ("extra_dim".equals(key)) return english ? "Extra dim" : "Ekstra karartma";
        if ("open_pdf".equals(key)) return english ? "Add PDF" : "PDF ekle";
        if ("fullscreen".equals(key)) return english ? "Fullscreen" : "Tam ekran";
        if ("discover".equals(key)) return english ? "Discover" : "Keşfet";
        if ("more".equals(key)) return english ? "More" : "Daha fazla";
        if ("empty_library".equals(key)) return english ? "Your books will appear here." : "Kitapların burada görünecek.";
        if ("pdf_document".equals(key)) return english ? "PDF document" : "PDF belgesi";
        if ("read".equals(key)) return english ? "read" : "okundu";
        if ("new_book".equals(key)) return english ? "New" : "Yeni";
        if ("general".equals(key)) return english ? "General" : "Genel";
        if ("empty_collections".equals(key)) return english ? "No collections yet. Create one above." : "Henüz koleksiyon yok. Yukarıdan oluştur.";
        if ("empty_collection".equals(key)) return english ? "This collection is empty. Add books from the button above." : "Bu koleksiyon boş. Yukarıdaki düğmeden kitap ekle.";
        if ("no_search_results".equals(key)) return english ? "No matches. Try another title." : "Eşleşme yok. Başka bir başlık dene.";
        if ("collection_name_required".equals(key)) return english ? "Enter a collection name." : "Bir koleksiyon adı yaz.";
        if ("collection_exists".equals(key)) return english ? "A collection with this name already exists." : "Bu adda bir koleksiyon zaten var.";
        if ("delete_collection".equals(key)) return english ? "Delete this collection?" : "Bu koleksiyon silinsin mi?";
        if ("delete_collection_message".equals(key)) return english ? "Its books will stay in your library, but will be removed from this collection." : "Kitaplar kitaplığında kalır; yalnızca bu koleksiyondan çıkarılır.";
        if ("in_collection".equals(key)) return english ? "In" : "Şu koleksiyonda:";
        if ("books_count".equals(key)) return english ? "books" : "kitap";
        if ("choose_collection".equals(key)) return english ? "Add to a collection" : "Koleksiyona ekle";
        if ("collection_message".equals(key)) return english ? "Name a folder for this PDF." : "Bu PDF için bir klasör adı yaz.";
        if ("collection_hint".equals(key)) return english ? "Collection name" : "Koleksiyon adı";
        if ("save".equals(key)) return english ? "Save" : "Kaydet";
        if ("skip".equals(key)) return english ? "Skip" : "Atla";
        if ("remove_book".equals(key)) return english ? "Remove book?" : "Kitap kaldırılsın mı?";
        if ("pdf_open_failed".equals(key)) return english ? "This PDF could not be opened." : "Bu PDF açılamadı.";
        if ("pdf_page_failed".equals(key)) return english ? "This page could not be rendered." : "Bu sayfa görüntülenemedi.";
        if ("brightness".equals(key)) return english ? "Brightness" : "Parlaklık";
        if ("blue_light".equals(key)) return english ? "Warm light" : "Sarı ışık";
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

    private Button premiumIconButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.rgb(66, 57, 43));
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(220, 218, 211));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}


