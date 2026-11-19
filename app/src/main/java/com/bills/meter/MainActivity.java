package com.bills.meter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int CONTACT=20, SAVE=21, OPEN=22;
  private WebView web;
  private int contactIndex=-1;
  private String pendingBackup="";
  private ValueCallback<Uri[]> uploadCallback;
  @Override public void onCreate(Bundle b){super.onCreate(b);web=new WebView(this);setContentView(web);
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
    web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){
      if(url.startsWith("https://wa.me/")||url.startsWith("sms:")){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("لا يوجد تطبيق لفتح الرابط");}return true;}return false;
    }});
    web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> callback,FileChooserParams params){uploadCallback=callback;try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,OPEN);return true;}catch(Exception e){uploadCallback=null;return false;}}});
    web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.loadUrl("file:///android_asset/index.html");
  }
  private void toast(String t){android.widget.Toast.makeText(this,t,android.widget.Toast.LENGTH_LONG).show();}
  private void js(String script){runOnUiThread(()->web.evaluateJavascript(script,null));}
  public class Bridge {
    @JavascriptInterface public void chooseContact(int index){contactIndex=index;runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI);try{startActivityForResult(i,CONTACT);}catch(Exception e){toast("تعذر فتح جهات الاتصال");}});}
    @JavascriptInterface public void saveBackup(String json){pendingBackup=json;runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"yemen-bills-backup.json");startActivityForResult(i,SAVE);});}
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
    if(request==OPEN){if(uploadCallback!=null){uploadCallback.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);uploadCallback=null;}return;}
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    if(request==SAVE){try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));toast("تم حفظ النسخة الاحتياطية");}catch(Exception e){toast("فشل حفظ النسخة");}return;}
    if(request==CONTACT){try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){
      if(c!=null&&c.moveToFirst()){String name=c.getString(0),phone=c.getString(1);String n=org.json.JSONObject.quote(name),p=org.json.JSONObject.quote(phone);js("receiveContact("+contactIndex+","+n+","+p+")");}
    }catch(Exception e){toast("تعذر قراءة جهة الاتصال");}}
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
