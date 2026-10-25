package com.example.foliodimmer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private boolean immersive;

    private static final int PICK_PDF = 40;
    private static final String PREFS = "folio_preferences";
    private static final String LAST_PDF = "last_pdf";
    private static final String DIM_LEVEL = "dim_level";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(createScreen());
        updateDim(preferences.getInt(DIM_LEVEL, 0));
        String savedPdf = preferences.getString(LAST_PDF, null);
        if (savedPdf != null) {
            tryOpenPdf(Uri.parse(savedPdf));
        }
    }

    private View createScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 244, 238));

        LinearLayout page = new LinearLayout(this);
        libraryPage = page;
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));

        TextView eyebrow = text("FOLIO LIBRARY", 12, Color.rgb(108, 99, 255));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(eyebrow, wrap());

        TextView title = text("The quiet shelf", 30, Color.rgb(24, 24, 29));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(12);
        page.addView(title, titleParams);

        TextView subtitle = text("A small reading space for the end of the day.", 16,
                Color.rgb(92, 88, 82));
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(6);
        page.addView(subtitle, subtitleParams);

        TextView card = text("01\nA softer page\n\nAdjust the light before you read.", 20,
                Color.rgb(50, 45, 41));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackgroundColor(Color.rgb(231, 225, 213));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(170));
        cardParams.topMargin = dp(28);
        page.addView(card, cardParams);

        TextView controlTitle = text("Extra dim", 17, Color.rgb(30, 30, 34));
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

        Button openButton = new Button(this);
        openButton.setText("Open PDF");
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

        Button immersiveButton = new Button(this);
        immersiveButton.setText("Toggle fullscreen");
        immersiveButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                immersive = !immersive;
                applyImmersiveMode();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(16);
        page.addView(immersiveButton, buttonParams);

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            preferences.edit().putString(LAST_PDF, data.getData().toString()).apply();
            tryOpenPdf(data.getData());
        }
    }

    private void tryOpenPdf(Uri uri) {
        try {
            closePdf();
            pdfFile = getContentResolver().openFileDescriptor(uri, "r");
            renderer = new PdfRenderer(pdfFile);
            currentPage = 0;
            libraryPage.setVisibility(View.GONE);
            readerBar.setVisibility(View.VISIBLE);
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
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        imageMatrix.postTranslate(event.getX() - lastTouchX, event.getY() - lastTouchY);
                        pdfPreview.setImageMatrix(imageMatrix);
                        lastTouchX = event.getX(); lastTouchY = event.getY();
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

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
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


