package com.example.toolverse; // CHANGE to your Sketchware package name

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ToolBridge {
    public static final int SAVE_DOCUMENT_REQUEST = 8319;
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Transfer> transfers =
            Collections.synchronizedMap(new HashMap<String, Transfer>());
    private Transfer pendingDocument;

    private static final class Transfer {
        String name, mime;
        File file;
        FileOutputStream output;
        long bytes;
        boolean share;
    }

    public ToolBridge(Activity activity) { this.activity = activity; }

    @JavascriptInterface
    public String beginFile(String fileName, String mimeType) {
        try {
            if (transfers.size() >= 3) return "";
            String safeName = fileName == null ? "toolverse-file" :
                    fileName.replaceAll("[^a-zA-Z0-9._ -]", "_");
            if (safeName.length() > 96) safeName = safeName.substring(0, 96);
            if (safeName.isEmpty() || safeName.equals(".") || safeName.equals(".."))
                safeName = "toolverse-file";
            String cleanMime = (mimeType != null && mimeType.matches("[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+"))
                    ? mimeType : "application/octet-stream";
            Transfer t = new Transfer();
            t.name = safeName;
            t.mime = cleanMime;
            t.file = new File(activity.getCacheDir(), "toolverse-" + UUID.randomUUID() + ".tmp");
            t.output = new FileOutputStream(t.file);
            String token = UUID.randomUUID().toString();
            transfers.put(token, t);
            return token;
        } catch (Exception ignored) { return ""; }
    }

    @JavascriptInterface
    public boolean appendChunk(String token, String base64Chunk) {
        Transfer t = transfers.get(token);
        if (t == null || base64Chunk == null || base64Chunk.length() > 120000) return false;
        try {
            byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);
            synchronized (t) {
                if (t.output == null || bytes.length == 0 || t.bytes + bytes.length > MAX_FILE_BYTES)
                    throw new IllegalStateException("Invalid chunk or file exceeds limit");
                t.output.write(bytes);
                t.bytes += bytes.length;
            }
            return true;
        } catch (Exception ex) {
            cancelFile(token);
            return false;
        }
    }

    @JavascriptInterface
    public boolean finishFile(String token, boolean share) {
        final Transfer t = transfers.remove(token);
        if (t == null) return false;
        try {
            synchronized (t) {
                if (t.output != null) {
                    t.output.flush();
                    t.output.close();
                    t.output = null;
                }
                if (t.bytes == 0) throw new IllegalStateException("Empty file");
            }
            t.share = share;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                new Thread(new Runnable() {
                    @Override public void run() { publishFile(t); }
                }, "Toolverse-Export").start();
            } else {
                main.post(new Runnable() {
                    @Override public void run() { publishFile(t); }
                });
            }
            return true;
        } catch (Exception ex) {
            closeAndDelete(t);
            show("Could not finish file");
            return false;
        }
    }

    @JavascriptInterface
    public void cancelFile(String token) {
        Transfer t = transfers.remove(token);
        if (t != null) closeAndDelete(t);
    }

    @JavascriptInterface
    public void copyText(final String text) {
        main.post(new Runnable() {
            @Override public void run() {
                ClipboardManager manager =
                        (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (manager != null) {
                    manager.setPrimaryClip(ClipData.newPlainText("Toolverse", text));
                    Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void publishFile(Transfer t) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri destination = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, t.name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, t.mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Toolverse");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                destination = activity.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (destination == null) throw new IllegalStateException("Downloads unavailable");
                OutputStream output = activity.getContentResolver().openOutputStream(destination);
                if (output == null) throw new IllegalStateException("Cannot write file");
                try { copy(t.file, output); }
                finally { output.close(); }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                activity.getContentResolver().update(destination, done, null, null);
                if (t.share) {
                    final Uri shared = destination;
                    final String mime = t.mime;
                    main.post(new Runnable() {
                        @Override public void run() { shareUri(shared, mime); }
                    });
                } else show("Saved to Downloads/Toolverse");
            } catch (Exception ex) {
                if (destination != null) {
                    try { activity.getContentResolver().delete(destination, null, null); }
                    catch (Exception ignored) {}
                }
                show("Save failed: " + ex.getMessage());
            } finally { closeAndDelete(t); }
        } else {
            if (pendingDocument != null) {
                closeAndDelete(t);
                show("Finish the previous file save first");
                return;
            }
            pendingDocument = t;
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(t.mime);
                intent.putExtra(Intent.EXTRA_TITLE, t.name);
                activity.startActivityForResult(intent, SAVE_DOCUMENT_REQUEST);
            } catch (Exception ex) {
                pendingDocument = null;
                closeAndDelete(t);
                show("No file picker available");
            }
        }
    }

    // Call from MainActivity.onActivityResult(requestCode, resultCode, data).
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SAVE_DOCUMENT_REQUEST) return false;
        Transfer t = pendingDocument;
        pendingDocument = null;
        if (t == null) return true;
        try {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                OutputStream output = activity.getContentResolver().openOutputStream(uri);
                if (output == null) throw new IllegalStateException("File could not be opened");
                try { copy(t.file, output); }
                finally { output.close(); }
                if (t.share) shareUri(uri, t.mime);
                else Toast.makeText(activity, "File saved", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) { show("Save failed: " + ex.getMessage()); }
        finally { closeAndDelete(t); }
        return true;
    }

    private void shareUri(Uri uri, String mime) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newUri(activity.getContentResolver(), "Toolverse export", uri));
        activity.startActivity(Intent.createChooser(share, "Share file"));
    }

    private static void copy(File source, OutputStream destination) throws Exception {
        InputStream input = new FileInputStream(source);
        try {
            byte[] buf = new byte[32768];
            int n;
            while ((n = input.read(buf)) != -1) destination.write(buf, 0, n);
            destination.flush();
        } finally { input.close(); }
    }

    private static void closeAndDelete(Transfer t) {
        try { if (t.output != null) t.output.close(); }
        catch (Exception ignored) {}
        if (t.file != null) t.file.delete();
    }

    private void show(final String message) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}