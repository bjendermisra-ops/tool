# Sketchware Plus Android integration (WebView → APK)

This is a **website-first project**, not an APK yet. The web app runs without Android code in a normal browser. The optional native bridge is required for reliable file downloads and file-sharing on Android WebView, especially for generated PNG and PDF blobs.

## 1. Publish the website
- Connect this GitHub repository to a static hosting service such as Vercel, or enable GitHub Pages for the main branch.
- Set the project root as the static website directory; no build command is required.
- Open the published HTTPS URL in Chrome first, then in the Sketchware WebView.
- Do **not** open it using \`file://\`; use HTTPS for clipboard, share, and security.

## 2. Add the bridge Java class
- Copy \`ToolBridge.java\` into your Sketchware project Java sources.
- Replace \`package com.example.toolverse;\` with **your app's actual package**.
- In Sketchware Plus, add the Java class through the Java source / custom source feature supported by your build.
- Your Android SDK must compile against API 29 or later (Android 10 introduced scoped-storage Downloads API).
- Start with a blank WebView project, test without AdMob first, and only then integrate ads.

## 3. Imports and fields for MainActivity
In MainActivity, add these imports if missing:

\`\`\`java
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.content.Intent;
\`\`\`

And fields inside MainActivity (but outside methods):

\`\`\`java
private ToolBridge toolBridge;
private ValueCallback<Uri[]> toolFileCallback;
private static final int TOOL_FILE_PICKER = 8320;
\`\`\`

## 4. WebView setup in onCreate, after setContentView / component setup
Use your existing WebView variable name; below it is \`webview1\`:

\`\`\`java
webview1.getSettings().setJavaScriptEnabled(true);
webview1.getSettings().setDomStorageEnabled(true);
webview1.getSettings().setAllowFileAccess(false);
webview1.getSettings().setAllowContentAccess(true);
webview1.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
webview1.getSettings().setSupportMultipleWindows(false);
webview1.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
webview1.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

toolBridge = new ToolBridge(MainActivity.this);
webview1.addJavascriptInterface(toolBridge, "ToolBridge");

// Keep the installed web origin inside the APK; open external sites externally.
// Replace example.com with YOUR real hosting hostname.
final String allowedHost = "example.com";
webview1.setWebViewClient(new WebViewClient() {
    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        if ("https".equalsIgnoreCase(u.getScheme()) &&
              allowedHost.equalsIgnoreCase(u.getHost())) return false;
        if ("http".equalsIgnoreCase(u.getScheme()) ||
            "https".equalsIgnoreCase(u.getScheme())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
            catch (Exception ignored) {}
        }
        return true;
    }
    @Override public void onReceivedSslError(WebView view,
        android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
        handler.cancel(); // NEVER bypass invalid TLS certificates
    }
});

webview1.setWebChromeClient(new WebChromeClient() {
    @Override public boolean onShowFileChooser(WebView view,
            ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (toolFileCallback != null) toolFileCallback.onReceiveValue(null);
        toolFileCallback = callback;
        try {
            startActivityForResult(params.createIntent(), TOOL_FILE_PICKER);
            return true;
        } catch (Exception ex) {
            toolFileCallback = null;
            callback.onReceiveValue(null);
            return false;
        }
    }
});
webview1.loadUrl("https://example.com/");
\`\`\`

Security: attach the bridge **only to your trusted first-party site**. Do not load third-party web pages, unknown HTML, or non-HTTPS URLs in this WebView. Also review any third-party JavaScript libraries before production.

## 5. Route activity results back to both file picker and save dialog
Merge the following into your existing MainActivity.onActivityResult (do not duplicate the override if it already exists):

\`\`\`java
@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (toolBridge != null && toolBridge.handleActivityResult(requestCode, resultCode, data)) return;
    if (requestCode == TOOL_FILE_PICKER) {
        if (toolFileCallback != null) {
            toolFileCallback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            toolFileCallback = null;
        }
    }
}
\`\`\`

## 6. Manifest
At minimum:

\`\`\`xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
\`\`\`

No legacy blanket storage permission is needed for the supported paths:
- Android 10+: new exports saved with MediaStore under **Downloads/Toolverse**.
- Android 9 or older: system **Save As** document picker.
- Sharing uses an Android \`ACTION_SEND\` chooser and content URIs.

Use default status-bar edge handling from Android, not fullscreen mode unless you implement safe-area insets; the website includes bottom \`env(safe-area-inset-bottom)\` support.

## 7. QA checklist on real phones
1. Android 14/15: choose an image; edit B&W; download PNG; verify Downloads/Toolverse; share PNG to WhatsApp.
2. Select an image by system Documents and Gallery pickers.
3. PDF: upload a PDF, add numbers, save, open with a PDF reader; share through the native chooser.
4. Android 9 or below (if supported): verify Save As opens and creates a valid file.
5. Large image (e.g. 15 MB), slow network and offline behavior; PDF library may need a first-time download.
6. Toggle dark/light mode, search by a plain-language word, save tools, revisit recent.
7. Back navigation and Android system gesture navigation; device notch, keyboard and bottom navigation.
8. Do not enable AdMob over action controls or the Android system UI.

## Known intentional boundaries
- Background Eraser uses local **solid-color flood-fill**; it is not AI portrait segmentation.
- AMP quick audit and hreflang checks are local markup checks; they do not issue fake live success/error statuses.
- The PDF library is lazily loaded from a pinned public CDN. To make PDF editing fully offline, self-host a reviewed copy under the same origin and change the URL in \`assets/tools.js\`.
- Android integration requires **one-time Sketchware configuration and device testing**; the presence of these files alone does not generate a tested APK.
