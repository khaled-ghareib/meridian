package com.meridian.finance;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Meridian runs as a local web app inside a WebView. Everything is loaded from
 * the APK itself (no internet), and the data is saved twice on the phone:
 * in the WebView's storage and in a private file (data.json) as a safety copy.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int PICK_FILE = 41;

    // Dark and light colours for the strips behind the status bar and the navigation bar.
    private static final int DARK_BG = Color.parseColor("#0D1117");
    private static final int DARK_NAV = Color.parseColor("#161B22");
    private static final int LIGHT_BG = Color.parseColor("#F4F2ED");
    private static final int LIGHT_NAV = Color.parseColor("#FFFFFF");

    private LinearLayout root;
    private View topStrip;
    private View bottomStrip;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private boolean lightTheme = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout: [status bar strip] [web app] [navigation bar / keyboard strip]
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        topStrip = new View(this);
        bottomStrip = new View(this);
        web = new WebView(this);
        root.addView(topStrip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottomStrip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 30) {
            // Draw edge-to-edge (required on Android 15+) and keep the app clear of the
            // status bar, the navigation bar and the on-screen keyboard.
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                setHeight(topStrip, bars.top);
                setHeight(bottomStrip, Math.max(bars.bottom, ime.bottom));
                v.setPadding(bars.left, 0, bars.right, 0);
                return WindowInsets.CONSUMED;
            });
        }
        // Start in the phone's own mode until the app reports the user's choice.
        applyBarColors(!isSystemDarkNow());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose a Meridian backup"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START);
    }

    private boolean isSystemDarkNow() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Colours the strips behind the system bars and switches the bar icons dark or light. */
    @SuppressWarnings("deprecation")
    private void applyBarColors(boolean light) {
        lightTheme = light;
        int bg = light ? LIGHT_BG : DARK_BG;
        int nav = light ? LIGHT_NAV : DARK_NAV;
        root.setBackgroundColor(bg);
        topStrip.setBackgroundColor(bg);
        bottomStrip.setBackgroundColor(nav);
        web.setBackgroundColor(bg);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        } else {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(nav);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private static void setHeight(View v, int h) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The phone switched between dark and light mode: tell the app (it decides
        // whether to follow, based on the user's Appearance setting).
        boolean dark = isSystemDarkNow();
        web.evaluateJavascript("window.meridianSystemTheme && window.meridianSystemTheme(" + dark + ")", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Let the app close an open panel or return to Overview first.
        web.evaluateJavascript("(window.meridianBack && window.meridianBack()) ? 'yes' : 'no'", value -> {
            if (value == null || !value.contains("yes")) finish();
        });
    }

    /** Functions the web app can call as window.Android.xxx(...) */
    private class Bridge {

        @JavascriptInterface
        public void persist(String json) {
            try {
                File tmp = new File(getFilesDir(), "data.json.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                File dest = new File(getFilesDir(), "data.json");
                if (!tmp.renameTo(dest)) {
                    try (FileOutputStream out = new FileOutputStream(dest)) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    tmp.delete();
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String restore() {
            File f = new File(getFilesDir(), "data.json");
            if (!f.exists()) return null;
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                return buf.toString("UTF-8");
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public boolean isSystemDark() {
            return isSystemDarkNow();
        }

        @JavascriptInterface
        public void setTheme(final boolean light) {
            runOnUiThread(() -> applyBarColors(light));
        }

        @JavascriptInterface
        public void shareFile(final String name, final String mime, final String content) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "exports");
                    if (!dir.exists()) dir.mkdirs();
                    String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                    File f = new File(dir, safe);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(mime);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_SUBJECT, safe);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Save or send " + safe));
                } catch (Exception ignored) { }
            });
        }
    }
}
