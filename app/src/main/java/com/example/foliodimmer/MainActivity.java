package com.example.foliodimmer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import java.util.Comparator;

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
    private KindleHomeView kindleHome;
    private EditText searchBar;
    private SeekBar brightnessBar;
    private boolean collectionMode;
    private String activeCollection;
    private boolean settingsPanel;
    private boolean sortPanel;
    private boolean brightnessPanel;
    private boolean languageExpanded;
    private boolean colorExpanded;
    private final Map<String, Bitmap> thumbnailCache = new HashMap<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        language = preferences.getString(LANGUAGE, "tr");
        setContentView(createScreen());
        immersive = true;
        applyImmersiveMode();
        updateDim(preferences.getInt(DIM_LEVEL, 0));
        rebuildLibrary();
        if (!preferences.getBoolean(SETUP_DONE, false)) showSetup();
    }

    private View createScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(232, 230, 222));

        libraryPage = new LinearLayout(this);
        libraryPage.setOrientation(LinearLayout.VERTICAL);
        kindleHome = new KindleHomeView(this);
        libraryPage.addView(kindleHome, new LinearLayout.LayoutParams(-1, -1));
        root.addView(libraryPage, new FrameLayout.LayoutParams(-1, -1));

        searchBar = new EditText(this);
        searchBar.setSingleLine(true);
        searchBar.setTextSize(14);
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
                if (kindleHome != null) kindleHome.invalidate();
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        searchBar.setTextColor(Color.rgb(58, 57, 53));
        searchBar.setHintTextColor(Color.rgb(117, 115, 108));
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(Color.rgb(232, 230, 222));
        searchBackground.setStroke(dp(1), Color.rgb(178, 176, 168));
        searchBackground.setCornerRadius(dp(4));
        searchBar.setBackground(searchBackground);
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(dp(156), dp(34));
        searchParams.leftMargin = dp(158);
        searchParams.topMargin = dp(6);
        root.addView(searchBar, searchParams);

        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        brightnessBar.setProgress(preferences.getInt(DIM_LEVEL, 0));
        brightnessBar.setVisibility(View.GONE);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateDim(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
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
        previewParams.setMargins(dp(16), dp(84), dp(16), dp(16));
        root.addView(pdfPreview, previewParams);

        ReaderToolbarView reader = new ReaderToolbarView(this);
        readerBar = reader;
        reader.setVisibility(View.GONE);
        root.addView(reader, new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP));
        pdfPreview.setOnTouchListener(createPdfTouchListener());

        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.BLACK);
        dimOverlay.setClickable(false);
        root.addView(dimOverlay, new FrameLayout.LayoutParams(-1, -1));
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
                saveCollection(uri, t("general"));
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
        Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                new HashSet<String>()));
        names.add(name);
        preferences.edit().putStringSet(COLLECTIONS, names)
                .putString(COLLECTION_PREFIX + uri.toString().hashCode(), name).apply();
        if (kindleHome != null) kindleHome.invalidate();
    }

    private void showNewCollectionDialog() {
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
                    .putInt("count_" + currentUri.hashCode(), renderer.getPageCount())
                    .putLong(OPENED_PREFIX + currentUri.hashCode(), System.currentTimeMillis())
                    .apply();
            if (pageSeekBar != null) pageSeekBar.setMax(Math.max(1, renderer.getPageCount() - 1));
            currentPage = preferences.getInt(PAGE_PREFIX + currentUri.hashCode(), 0);
            currentPage = Math.max(0, Math.min(currentPage, renderer.getPageCount() - 1));
            libraryPage.setVisibility(View.GONE);
            searchBar.setVisibility(View.GONE);
            brightnessBar.setVisibility(View.GONE);
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
        pdfPreview.setColorFilter(isColorMode() ? null : eInkFilter());
        if (pageLabel != null) pageLabel.setText((currentPage + 1) + " / " + renderer.getPageCount());
        if (pageSeekBar != null) pageSeekBar.setProgress(currentPage);
        pdfPreview.post(new Runnable() {
            @Override public void run() { fitPage(); }
        });
        if (readerBar != null) readerBar.invalidate();
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
            toolbarPaint.setTextSize(15);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            toolbarPaint.setTextAlign(Paint.Align.CENTER);
            int total = renderer == null ? 0 : renderer.getPageCount();
            canvas.drawText(total == 0 ? "0 / 0" : (currentPage + 1) + " / " + total,
                    width / 2f, 21, toolbarPaint);

            toolbarPaint.setColor(Color.rgb(100, 98, 91));
            toolbarPaint.setStrokeWidth(1.5f);
            float left = 76;
            float right = width - 108;
            canvas.drawLine(left, 47, right, 47, toolbarPaint);
            if (total > 1) {
                float knob = left + (right - left) * currentPage / (float) (total - 1);
                toolbarPaint.setColor(Color.rgb(63, 61, 55));
                canvas.drawCircle(knob, 47, 6, toolbarPaint);
            }
            drawReaderButton(canvas, 14, 30, false);
            drawReaderButton(canvas, width - 104, 30, true);
            drawCloseButton(canvas, width - 50, 30);
            toolbarPaint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }

        private void drawCloseButton(Canvas canvas, float x, float y) {
            toolbarPaint.setStyle(Paint.Style.FILL);
            toolbarPaint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(x, y - 24, x + 32, y + 8), 6, 6, toolbarPaint);
            toolbarPaint.setStyle(Paint.Style.STROKE);
            toolbarPaint.setStrokeWidth(2);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            canvas.drawLine(x + 10, y - 15, x + 22, y - 3, toolbarPaint);
            canvas.drawLine(x + 22, y - 15, x + 10, y - 3, toolbarPaint);
        }

        private void drawReaderButton(Canvas canvas, float x, float y, boolean next) {
            toolbarPaint.setStyle(Paint.Style.FILL);
            toolbarPaint.setColor(Color.rgb(220, 218, 211));
            canvas.drawRoundRect(new RectF(x, y - 24, x + 40, y + 16), 6, 6, toolbarPaint);
            toolbarPaint.setStyle(Paint.Style.STROKE);
            toolbarPaint.setStrokeWidth(2);
            toolbarPaint.setColor(Color.rgb(55, 53, 48));
            if (next) {
                canvas.drawLine(x + 16, y - 5, x + 22, y, toolbarPaint);
                canvas.drawLine(x + 22, y, x + 16, y + 5, toolbarPaint);
            } else {
                canvas.drawLine(x + 24, y - 5, x + 18, y, toolbarPaint);
                canvas.drawLine(x + 18, y, x + 24, y + 5, toolbarPaint);
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
                dragging = y > 28 && y < 60 && x > 60 && x < width - 90;
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
                    if (x < 65) showPage(currentPage - 1);
                    else if (x > width - 110 && x < width - 56) showPage(currentPage + 1);
                    else if (x >= width - 56) closeReader();
                }
                return true;
            }
            return true;
        }

        private void seekTo(float x, float width) {
            if (renderer == null || renderer.getPageCount() < 2) return;
            float ratio = Math.max(0, Math.min(1, (x - 76) / (width - 184)));
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
        @Override public int getOpacity() { return -3; }
        @Override public int getIntrinsicWidth() { return 20; }
        @Override public int getIntrinsicHeight() { return 20; }
    }

    private void closeReader() {
        closePdf();
        readerBar.setVisibility(View.GONE);
        pdfPreview.setVisibility(View.GONE);
        libraryPage.setVisibility(View.VISIBLE);
        if (searchBar != null) searchBar.setVisibility(View.VISIBLE);
        if (brightnessBar != null) brightnessBar.setVisibility(View.GONE);
        brightnessPanel = false;
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
        closePdf();
        if (pageBitmap != null) pageBitmap.recycle();
        super.onDestroy();
    }

    private void updateDim(int value) {
        if (dimOverlay != null) {
            dimOverlay.setAlpha(Math.min(0.32f, value / 100f));
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
                    26, 30, 12, Color.rgb(78, 71, 57), false);
            drawIconButton(canvas, width - 160, 8, "☼");
            drawIconButton(canvas, width - 115, 8, "+");
            drawIconButton(canvas, width - 70, 8, "⚙");

            drawText(canvas, t("books"), 28, 76, 16, Color.rgb(56, 49, 37), !collectionMode);
            drawText(canvas, t("collections"), 120, 76, 16,
                    collectionMode ? Color.rgb(56, 49, 37) : Color.rgb(108, 98, 80), collectionMode);
            int sortMode = preferences.getInt(SORT_MODE, 2);
            String sortLabel = sortMode == 0 ? t("sort_az")
                    : sortMode == 1 ? t("sort_za") : t("sort_recent");
            drawText(canvas, t("filter") + ": " + sortLabel, width - 160, 76, 13,
                    Color.rgb(84, 76, 61), false);
            paint.setColor(Color.rgb(70, 63, 50));
            if (collectionMode) canvas.drawRect(118, 91, 218, 94, paint);
            else canvas.drawRect(26, 91, 93, 94, paint);
            paint.setColor(Color.rgb(178, 176, 168));
            canvas.drawRect(24, 112, width - 24, 113, paint);
            if (collectionMode && activeCollection != null) {
                drawText(canvas, "‹  " + activeCollection, 28, 142, 18,
                        Color.rgb(65, 63, 57), true);
            }

            ArrayList<String> books = visibleBooks();
            int left = 28;
            int top = collectionMode && activeCollection != null ? 160 : 132;
            int gap = 16;
            int cardWidth = Math.max(dp(92), (width - 56 - gap * 2) / 3);
            int coverHeight = dp(148);
            if (collectionMode && activeCollection == null) {
                drawNewCollectionButton(canvas, width);
                top = 174;
                drawCollections(canvas, left, top, cardWidth, coverHeight, width);
            } else if (books.isEmpty()) {
                drawText(canvas, t("empty_library"), 28, top + 45, 15, Color.rgb(113, 101, 80), false);
                drawText(canvas, "+  " + t("open_pdf"), 28, top + 82, 15, Color.rgb(74, 63, 43), true);
            } else {
                int visible = Math.min(6, books.size());
                for (int i = 0; i < visible; i++) {
                    int column = i % 3;
                    int row = i / 3;
                    int x = left + column * (cardWidth + gap);
                    int y = top + row * (coverHeight + 74);
                    int[] colors = {Color.rgb(111, 74, 55), Color.rgb(196, 155, 92),
                            Color.rgb(96, 116, 105), Color.rgb(75, 87, 111)};
                    Bitmap thumbnail = loadThumbnail(books.get(i));
                    if (thumbnail != null) {
                        paint.setColorFilter(isColorMode() ? null : eInkFilter());
                        canvas.drawBitmap(thumbnail, null, new RectF(x, y, x + cardWidth,
                                y + coverHeight), paint);
                        paint.setColorFilter(null);
                    } else {
                        paint.setColor(Color.rgb(188, 185, 175));
                        canvas.drawRect(x, y, x + cardWidth, y + coverHeight, paint);
                    }
                    drawText(canvas, "FOLIO", x + 8, y + 22, 9, Color.rgb(245, 244, 239), true);
                    String title = displayName(Uri.parse(books.get(i))).replace("▸  ", "");
                    if (title.length() > 18) title = title.substring(0, 16) + "…";
                    drawText(canvas, title, x + 8, y + coverHeight - 18, 11, Color.rgb(245, 244, 239), true);
                    int total = preferences.getInt("count_" + books.get(i).hashCode(), 0);
                    int page = preferences.getInt(PAGE_PREFIX + books.get(i).hashCode(), 0);
                    int percent = total > 0 ? (page >= total - 1 ? 100 : ((page + 1) * 100) / total) : 0;
                    drawText(canvas, total > 0 ? "%" + percent + " " + t("read") : t("new_book"),
                            x, y + coverHeight + 22, 12, Color.rgb(75, 73, 67), false);
                    paint.setColor(Color.rgb(145, 142, 133));
                    canvas.drawRect(x, y + coverHeight + 32, x + cardWidth, y + coverHeight + 35, paint);
                    if (total > 0) {
                        paint.setColor(Color.rgb(70, 68, 63));
                        canvas.drawRect(x, y + coverHeight + 32,
                                x + (cardWidth * percent / 100),
                                y + coverHeight + 35, paint);
                    }
                }
            }

            if (brightnessPanel) drawBrightnessPanel(canvas, width);
            if (sortPanel) drawSortPanel(canvas, width);
            if (settingsPanel) drawSettingsPanel(canvas, width);
            canvas.restore();
        }

        private ArrayList<String> visibleBooks() {
            ArrayList<String> books = new ArrayList<>(preferences.getStringSet(LIBRARY_URIS,
                    new HashSet<String>()));
            final int mode = preferences.getInt(SORT_MODE, 2);
            Collections.sort(books, new Comparator<String>() {
                @Override public int compare(String first, String second) {
                    if (mode == 2) {
                        long firstOpened = preferences.getLong(OPENED_PREFIX + first.hashCode(), 0L);
                        long secondOpened = preferences.getLong(OPENED_PREFIX + second.hashCode(), 0L);
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

        private ArrayList<String> collectionNames() {
            Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                    new HashSet<String>()));
            for (String book : preferences.getStringSet(LIBRARY_URIS, new HashSet<String>())) {
                names.add(collectionFor(book));
            }
            ArrayList<String> result = new ArrayList<>(names);
            Collections.sort(result);
            return result;
        }

        private String collectionFor(String bookUri) {
            String value = preferences.getString(COLLECTION_PREFIX + bookUri.hashCode(), null);
            return value == null ? t("general") : value;
        }

        private void drawSortPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRect(width - 270, 104, width - 22, 274, paint);
            drawText(canvas, t("sort"), width - 246, 132, 16, Color.rgb(54, 53, 49), true);
            String[] labels = {t("sort_az"), t("sort_za"), t("sort_recent")};
            int selected = preferences.getInt(SORT_MODE, 2);
            for (int i = 0; i < labels.length; i++) {
                float cy = 163 + i * 34;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(Color.rgb(80, 78, 71));
                canvas.drawCircle(width - 242, cy - 5, 8, paint);
                if (selected == i) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(width - 242, cy - 5, 4, paint);
                }
                drawText(canvas, labels[i], width - 220, cy, 14, Color.rgb(61, 60, 55), false);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawBrightnessPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            canvas.drawRoundRect(new RectF(width - 270, 54, width - 22, 146), 7, 7, paint);
            drawText(canvas, t("brightness"), width - 246, 82, 16,
                    Color.rgb(54, 53, 49), true);
            paint.setColor(Color.rgb(80, 78, 71));
            paint.setStrokeWidth(2);
            canvas.drawLine(width - 246, 118, width - 46, 118, paint);
            float knob = width - 246 + 200 * (100 - Math.min(100,
                    preferences.getInt(DIM_LEVEL, 0))) / 100f;
            paint.setColor(Color.rgb(55, 53, 48));
            canvas.drawCircle(knob, 118, 7, paint);
        }

        private void drawSettingsPanel(Canvas canvas, int width) {
            paint.setColor(Color.argb(45, 40, 39, 35));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            paint.setColor(Color.rgb(239, 237, 230));
            int colorTop = languageExpanded ? 214 : 148;
            int panelBottom = colorExpanded ? colorTop + 82 : colorTop + 36;
            canvas.drawRoundRect(new RectF(width - 270, 54, width - 22, panelBottom), 7, 7, paint);
            drawText(canvas, t("settings"), width - 246, 82, 16, Color.rgb(54, 53, 49), true);
            drawText(canvas, t("language"), width - 246, 116, 14, Color.rgb(61, 60, 55), true);
            if (languageExpanded) {
                drawText(canvas, "Türkçe", width - 220, 148, 14,
                        "tr".equals(language) ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        "tr".equals(language));
                drawText(canvas, "English", width - 220, 180, 14,
                        "en".equals(language) ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        "en".equals(language));
                colorTop = 214;
            }
            drawText(canvas, t("colors"), width - 246, colorTop, 14, Color.rgb(61, 60, 55), true);
            if (colorExpanded) {
                drawText(canvas, t("mono_color"), width - 220, colorTop + 32, 14,
                        isColorMode() ? Color.rgb(130, 127, 119) : Color.rgb(45, 44, 40),
                        !isColorMode());
                drawText(canvas, t("color_color"), width - 220, colorTop + 64, 14,
                        isColorMode() ? Color.rgb(45, 44, 40) : Color.rgb(130, 127, 119),
                        isColorMode());
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCollections(Canvas canvas, int left, int top, int cardWidth,
                                     int coverHeight, int width) {
            Set<String> names = new HashSet<>(preferences.getStringSet(COLLECTIONS,
                    new HashSet<String>()));
            Set<String> books = preferences.getStringSet(LIBRARY_URIS, new HashSet<String>());
            for (String book : books) names.add(collectionFor(book));
            if (names.isEmpty()) {
                drawText(canvas, t("empty_collections"), left, top + 46, 15,
                        Color.rgb(113, 101, 80), false);
                return;
            }
            ArrayList<String> folders = new ArrayList<>(names);
            Collections.sort(folders);
            int folderWidth = (width - 56 - 16) / 2;
            for (int i = 0; i < folders.size(); i++) {
                int x = left + (i % 2) * (folderWidth + 16);
                int y = top + (i / 2) * 112;
                paint.setColor(isColorMode() ? Color.rgb(218, 201, 165) : Color.rgb(214, 212, 204));
                canvas.drawRoundRect(new RectF(x, y, x + folderWidth, y + 92), 6, 6, paint);
                paint.setColor(isColorMode() ? Color.rgb(133, 106, 63) : Color.rgb(91, 89, 83));
                canvas.drawRect(x + 14, y + 27, x + 35, y + 43, paint);
                canvas.drawRect(x + 16, y + 23, x + 27, y + 29, paint);
                drawText(canvas, folders.get(i), x + 14, y + 61, 14, Color.rgb(71, 59, 43), true);
                int count = 0;
                for (String book : books) if (folders.get(i).equals(collectionFor(book))) count++;
                drawText(canvas, count + " " + t("books_count"), x + 14, y + 81, 11,
                        Color.rgb(120, 104, 77), false);
            }
        }

        private void drawNewCollectionButton(Canvas canvas, int width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.rgb(126, 123, 115));
            canvas.drawRoundRect(new RectF(width - 196, 128, width - 28, 158), 4, 4, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, "+  " + t("new_collection"), width - 180, 148, 13,
                    Color.rgb(66, 64, 58), true);
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
            canvas.drawRoundRect(new RectF(x, y, x + 36, y + 34), 8, 8, paint);
            paint.setColor(Color.rgb(65, 56, 43));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            float cx = x + 18;
            float cy = y + 17;
            if ("☼".equals(icon)) {
                canvas.drawCircle(cx, cy, 5, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    float x1 = cx + (float) Math.cos(angle) * 8;
                    float y1 = cy + (float) Math.sin(angle) * 8;
                    float x2 = cx + (float) Math.cos(angle) * 11;
                    float y2 = cy + (float) Math.sin(angle) * 11;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else if ("+".equals(icon)) {
                paint.setStrokeWidth(2.8f);
                canvas.drawLine(cx - 7, cy, cx + 7, cy, paint);
                canvas.drawLine(cx, cy - 7, cx, cy + 7, paint);
            } else if ("⚙".equals(icon)) {
                canvas.drawCircle(cx, cy, 7, paint);
                canvas.drawCircle(cx, cy, 2, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    float x1 = cx + (float) Math.cos(angle) * 8;
                    float y1 = cy + (float) Math.sin(angle) * 8;
                    float x2 = cx + (float) Math.cos(angle) * 11;
                    float y2 = cy + (float) Math.sin(angle) * 11;
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
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX(); touchDownY = event.getY(); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float density = getResources().getDisplayMetrics().density;
                float x = event.getX() / density; float y = event.getY() / density;
                float width = getWidth() / density; float height = getHeight() / density;
                if (brightnessPanel) {
                    if (x >= width - 270 && x <= width - 22 && y >= 90 && y <= 145) {
                        int value = Math.max(0, Math.min(100,
                                Math.round(((width - 46) - x) / 200f * 100f)));
                        updateDim(value);
                        invalidate();
                        return true;
                    }
                    brightnessPanel = false;
                    invalidate();
                    return true;
                }
                if (sortPanel) {
                    if (x >= width - 270 && x <= width - 22 && y >= 140 && y <= 270) {
                        int selected = Math.max(0, Math.min(2, (int) ((y - 140) / 34)));
                        preferences.edit().putInt(SORT_MODE, selected).apply();
                    }
                    sortPanel = false;
                    invalidate();
                    return true;
                }
                if (settingsPanel) {
                    if (x >= width - 270 && x <= width - 22 && y >= 92 && y <= 130) {
                        languageExpanded = !languageExpanded;
                        colorExpanded = false;
                        invalidate();
                    } else if (languageExpanded && x >= width - 270 && x <= width - 22
                            && y > 130 && y <= 198) {
                        language = y <= 164 ? "tr" : "en";
                        preferences.edit().putString(LANGUAGE, language).apply();
                        languageExpanded = false;
                        recreate();
                    } else if (x >= width - 270 && x <= width - 22
                            && y >= (languageExpanded ? 198 : 130)
                            && y <= (languageExpanded ? 232 : 170)) {
                        colorExpanded = !colorExpanded;
                        invalidate();
                    } else if (colorExpanded && x >= width - 270 && x <= width - 22
                            && y > (languageExpanded ? 232 : 170)
                            && y <= (languageExpanded ? 300 : 238)) {
                        boolean color = y > (languageExpanded ? 266 : 202);
                        preferences.edit().putString(COLOR_MODE, color ? "color" : "mono").apply();
                        if (pdfPreview != null) pdfPreview.setColorFilter(color ? null : eInkFilter());
                        colorExpanded = false;
                        invalidate();
                    } else {
                        settingsPanel = false;
                        languageExpanded = false;
                        colorExpanded = false;
                        invalidate();
                    }
                    return true;
                }
                if (y < 58 && x >= width - 160 && x < width - 115) {
                    brightnessPanel = !brightnessPanel;
                    settingsPanel = false;
                    sortPanel = false;
                    brightnessBar.setVisibility(View.GONE);
                    searchBar.clearFocus();
                    invalidate();
                    return true;
                }
                if (y < 58 && x >= width - 115 && x < width - 70) {
                    settingsPanel = false;
                    sortPanel = false;
                    brightnessPanel = false;
                    pickPdf(); return true;
                }
                if (y < 58 && x >= width - 70) {
                    settingsPanel = !settingsPanel;
                    sortPanel = false;
                    brightnessPanel = false;
                    languageExpanded = false;
                    colorExpanded = false;
                    searchBar.clearFocus();
                    invalidate(); return true;
                }
                if (y > 55 && y < 105 && x > width - 180) {
                    sortPanel = !sortPanel;
                    settingsPanel = false;
                    brightnessPanel = false;
                    invalidate();
                    return true;
                }
                if (y > 55 && y < 105 && x > 100 && x < 260) {
                    collectionMode = true;
                    activeCollection = null;
                    invalidate(); return true;
                }
                if (y > 55 && y < 105 && x < 105) {
                    collectionMode = false;
                    activeCollection = null;
                    invalidate(); return true;
                }
                if (collectionMode && activeCollection != null && y > 112 && y < 158 && x < 260) {
                    activeCollection = null;
                    invalidate();
                    return true;
                }
                if (collectionMode && activeCollection == null && y >= 120 && y <= 164
                        && x >= width - 205) {
                    showNewCollectionDialog();
                    return true;
                }
                if (y > 165 && y < 620 && collectionMode && activeCollection == null) {
                    ArrayList<String> folders = collectionNames();
                    int folderWidth = (int) ((width - 56 - 16) / 2);
                    int index = Math.max(0, (int) ((y - 174) / 112)) * 2
                            + Math.max(0, Math.min(1, (int) ((x - 28) / (folderWidth + 16))));
                    if (index < folders.size()) {
                        activeCollection = folders.get(index);
                        invalidate();
                    }
                    return true;
                }
                if (y > 120 && y < 620) {
                    int cardWidth = Math.max(92, (int) ((width - 56 - 16 * 2) / 3));
                    int column = Math.max(0, Math.min(2, (int) ((x - 28) / (cardWidth + 16))));
                    int bookTop = collectionMode && activeCollection != null ? 160 : 132;
                    int row = Math.max(0, (int) ((y - bookTop) / (148 + 74)));
                    int index = row * 3 + column;
                    ArrayList<String> books = visibleBooks();
                    if (index >= 0 && index < books.size()) tryOpenPdf(Uri.parse(books.get(index)));
                    else if (books.isEmpty()) pickPdf();
                    return true;
                }
            }
            return true;
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
        if ("mono_color".equals(key)) return english ? "Original" : "Orijinal";
        if ("color_color".equals(key)) return english ? "Color" : "Renkli";
        if ("new_collection".equals(key)) return english ? "New collection" : "Yeni koleksiyon";
        if ("new_collection_message".equals(key)) return english ? "Name this collection." : "Bu koleksiyona bir ad ver.";
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
        if ("empty_collections".equals(key)) return english ? "Create a collection when adding a PDF." : "PDF eklerken bir koleksiyon oluştur.";
        if ("books_count".equals(key)) return english ? "books" : "kitap";
        if ("choose_collection".equals(key)) return english ? "Add to a collection" : "Koleksiyona ekle";
        if ("collection_message".equals(key)) return english ? "Name a folder for this PDF." : "Bu PDF için bir klasör adı yaz.";
        if ("collection_hint".equals(key)) return english ? "Collection name" : "Koleksiyon adı";
        if ("save".equals(key)) return english ? "Save" : "Kaydet";
        if ("skip".equals(key)) return english ? "Skip" : "Atla";
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


