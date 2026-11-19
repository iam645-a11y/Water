package com.bills.meter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.util.Base64;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    private static final int CONTACT_REQUEST = 101;
    private static final int FILE_REQUEST = 102;
    private static final int SAVE_REQUEST = 103;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private int selectedPerson = -1;
    private String pendingBackup;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, FILE_REQUEST); }
                catch (Exception ex) { fileCallback.onReceiveValue(null); fileCallback = null; return false; }
                return true;
            }
        });
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void chooseContact(int index) {
                runOnUiThread(() -> {
                    selectedPerson = index;
                    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                    try { startActivityForResult(intent, CONTACT_REQUEST); }
                    catch (Exception e) { webView.evaluateJavascript("alert('تعذر فتح جهات الاتصال')", null); }
                });
            }
            @JavascriptInterface public void saveBackup(String json) {
                runOnUiThread(() -> {
                    pendingBackup = json;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "yemen-bills-backup.json");
                    try { startActivityForResult(intent, SAVE_REQUEST); }
                    catch (Exception e) { webView.evaluateJavascript("alert('تعذر فتح حفظ الملفات')", null); }
                });
            }
        }, "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void sendContact(String name, String phone) {
        String js = "receiveContact(" + selectedPerson + "," + org.json.JSONObject.quote(name) + "," + org.json.JSONObject.quote(phone) + ")";
        webView.evaluateJavascript(js, null);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_REQUEST) {
            if (fileCallback != null) {
                Uri[] uris = result == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
                fileCallback.onReceiveValue(uris);
                fileCallback = null;
            }
        } else if (request == CONTACT_REQUEST && result == RESULT_OK && data != null) {
            try (Cursor c = getContentResolver().query(data.getData(),
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
                if (c != null && c.moveToFirst()) sendContact(c.getString(0), c.getString(1));
            } catch (Exception e) { webView.evaluateJavascript("alert('تعذر قراءة جهة الاتصال')", null); }
        } else if (request == SAVE_REQUEST && result == RESULT_OK && data != null && data.getData() != null && pendingBackup != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new Exception("No output stream");
                out.write(pendingBackup.getBytes("UTF-8"));
                out.flush();
                webView.evaluateJavascript("alert('تم حفظ النسخة الاحتياطية')", null);
            } catch (Exception e) { webView.evaluateJavascript("alert('فشل حفظ النسخة الاحتياطية')", null); }
            pendingBackup = null;
        }
    }
}
