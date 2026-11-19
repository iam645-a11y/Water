package com.bills.meter;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int CONTACT=20, SAVE=21, OPEN=22, EXCEL=23;
  private WebView web;
  private int contactIndex=-1;
  private String pendingBackup="";
  private String pendingExcel="";
  private ValueCallback<Uri[]> uploadCallback;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    web=new WebView(this);
    setContentView(web);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
        if(url.startsWith("https://wa.me/")||url.startsWith("sms:")){
          try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
          catch(Exception e){toast("لا يوجد تطبيق لفتح الرابط");}
          return true;
        }
        return false;
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(uploadCallback!=null) uploadCallback.onReceiveValue(null);
        uploadCallback=callback;
        try{
          Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("*/*");
          startActivityForResult(i,OPEN);
          return true;
        }catch(Exception e){
          uploadCallback=null;
          return false;
        }
      }
    });
    web.addJavascriptInterface(new Bridge(),"AndroidBridge");
    web.loadUrl("file:///android_asset/index.html");
  }

  private void toast(String t){android.widget.Toast.makeText(this,t,android.widget.Toast.LENGTH_LONG).show();}
  private void js(String script){runOnUiThread(()->web.evaluateJavascript(script,null));}

  public class Bridge {
    @JavascriptInterface public void saveExcel(String base64){
      runOnUiThread(()->{
        pendingExcel=base64;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE,"yemen-bills.xlsx");
        try{startActivityForResult(i,EXCEL);}catch(Exception e){pendingExcel="";toast("تعذر فتح نافذة حفظ Excel");}
      });
    }

    @JavascriptInterface public void chooseContact(int index){
      contactIndex=index;
      runOnUiThread(()->{
        Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try{startActivityForResult(i,CONTACT);}catch(Exception e){toast("تعذر فتح جهات الاتصال");}
      });
    }
    @JavascriptInterface public void saveBackup(String json){
      runOnUiThread(()->{
        pendingBackup=json;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"yemen-bills-backup.json");
        try{startActivityForResult(i,SAVE);}catch(Exception e){toast("تعذر فتح نافذة الحفظ");}
      });
    }
  }

  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==OPEN){
      if(uploadCallback!=null){
        uploadCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);
        uploadCallback=null;
      }
      return;
    }
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    if(request==EXCEL){
      try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new Exception("No output stream");
        out.write(android.util.Base64.decode(pendingExcel,android.util.Base64.DEFAULT));
        toast("تم حفظ ملف Excel");
      }catch(Exception e){toast("فشل حفظ Excel");}
      finally{pendingExcel="";}
      return;
    }
    if(request==SAVE){
      try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new Exception("No output stream");
        out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
        toast("تم حفظ النسخة الاحتياطية");
      }catch(Exception e){toast("فشل حفظ النسخة");}
      finally{pendingBackup="";}
      return;
    }
    if(request==CONTACT){
      try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){
        if(c!=null&&c.moveToFirst()){
          String name=c.getString(0),phone=c.getString(1);
          js("receiveContact("+contactIndex+","+org.json.JSONObject.quote(name)+","+org.json.JSONObject.quote(phone)+")");
        }
      }catch(Exception e){toast("تعذر قراءة جهة الاتصال");}
    }
  }

  private void confirmExit(){
    new AlertDialog.Builder(this)
      .setTitle("تأكيد الخروج")
      .setMessage("هل تريد الخروج من تطبيق فواتير الماء والكهرباء؟")
      .setNegativeButton("إلغاء",(dialog,which)->dialog.dismiss())
      .setPositiveButton("خروج",(dialog,which)->finish())
      .show();
  }

  @Override public void onBackPressed(){
    // The app uses JavaScript tabs, not WebView navigation history.
    web.evaluateJavascript("(function(){try{return document.getElementById('home') && !document.getElementById('home').classList.contains('hidden') ? 'home' : 'other';}catch(e){return 'home';}})()",result->{
      if("\"other\"".equals(result)){
        web.evaluateJavascript("if(typeof go==='function')go('home');",null);
      }else{
        confirmExit();
      }
    });
  }

  @Override protected void onDestroy(){
    if(uploadCallback!=null){uploadCallback.onReceiveValue(null);uploadCallback=null;}
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
