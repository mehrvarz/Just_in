/*
 * Copyright (C) 2011 Timur Mehrvarz Duesseldorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.timur.glticker;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.os.Environment;
import android.os.Process;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.SystemClock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Picture;
import android.graphics.Canvas;

import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

// needed to store snapshot pictures as PNG
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.BufferedReader;
//import java.io.BufferedInputStream;
//import java.io.InputStreamReader;

public class MyWebView extends WebView
{
  private final static String LOGTAG = "MyWebView";

  protected JsObject jsObject = null;
  protected boolean active=false;
  private boolean init=false;
  protected volatile boolean acceptWebViewPictureState = false;
  protected volatile boolean webViewError = false;
  private int instNumber=-1;
  private static long slideToMsgMS = -1l;
  private static long lastWatchedMsgTimeMs = -1l;
  private static int snapshotCounter=0;
  private static LinkedList<EntryTopic> messageList;

  private final static String fetchUrlString = "file:///android_asset/webapp/index.html";
  protected final static int NUMBER_OF_WEBVIEWS = 1;  // todo: issue if NUMBER_OF_WEBVIEWS > 1: onNewPicture will not be called
  protected static MyWebView[] myWebViewArray = new MyWebView[NUMBER_OF_WEBVIEWS];
  protected static int webViewWantedWidth = 0;    // used by setLayoutParams()
  protected static int webViewWantedHeight = 0;
  private static int maxRenderHeight = 0;
  private static FrameLayout frameLayout = null;
  private static GlTickerView mView = null;
  private static int[] pxls;
  private static Context context;

  MyWebView(Context context, LinkedList<EntryTopic> setMessageList, int instNumber)		// context = GlTicker Activity
  {
    super(context);
    this.context = context;
    messageList = setMessageList;
    this.instNumber = instNumber;
    //if(Config.LOGD) Log.i(LOGTAG, "new JsObject(this)...");
    jsObject = new JsObject(this,setMessageList,instNumber);
    if(jsObject!=null)
    {
      //if(Config.LOGD) Log.i(LOGTAG, "addJavascriptInterface(jsObject)...");
      addJavascriptInterface(jsObject, "JsObject");
    }
  }

  public JsObject getJsObject() {
    return jsObject;
  }

  public static void slideToMsg(long setLastWatchedMsgTimeMs) {
    slideToMsgMS = SystemClock.uptimeMillis();
    lastWatchedMsgTimeMs = setLastWatchedMsgTimeMs;
  }

  public static void setWantetWebviewDimensions(int setWantedWidth, int setWantedHeight) {
    webViewWantedWidth = setWantedWidth;
    webViewWantedHeight = setWantedHeight;
    if(Config.LOGD) Log.i(LOGTAG, "setWantetWebviewDimensions alloc pxls[] intwebViewWantedWidth="+webViewWantedWidth+" webViewWantedHeight="+webViewWantedHeight+" (+200) bytes="+(webViewWantedWidth*(webViewWantedHeight+200)));
    pxls = new int[webViewWantedWidth*(webViewWantedHeight+200)]; 
    // +200 to prevent
    // java.lang.ArrayIndexOutOfBoundsException
    // at android.graphics.Bitmap.checkPixelsAccess(Bitmap.java:920)
    // at android.graphics.Bitmap.getPixels(Bitmap.java:862)
    // at org.timur.glticker.MyWebView$3.takeSnapshot(MyWebView.java:296)
  }
  
  public static void setMaxRenderHeight(int setMaxRenderHeight) {
    maxRenderHeight = setMaxRenderHeight; // set by onCreate()
  }

  public static void setFrameLayout(FrameLayout setFrameLayout) {
    frameLayout = setFrameLayout;
  }
  
  public static void setView(GlTickerView setGlTickerView) {
    mView = setGlTickerView;
  }
  
  public static void startNextWebview() {
    // search the first webview that has active set to false
    int i;
    for(i=0; i<NUMBER_OF_WEBVIEWS; i++) {
      if(myWebViewArray[i]==null)
        return;
      if(myWebViewArray[i].active==false)
        break;
    }
    if(i==NUMBER_OF_WEBVIEWS) {
      //if(Config.LOGD) Log.e(LOGTAG, "startNextWebview() NO INACTIVE WEBVIEW FOUND");
      return;
    }

    if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() i="+i);
    myWebViewArray[i].active = true;

    final int inst = i;
    final MyWebView myWebView = myWebViewArray[inst];
    //myWebView.setDrawingCacheEnabled(true);

    //if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() i="+i+" new RelativeLayout.LayoutParams()");
    //final RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(webViewWantedWidth,webViewWantedHeight);
    //final RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(webViewWantedWidth,RelativeLayout.LayoutParams.WRAP_CONTENT);
    myWebView.setLayoutParams(new LinearLayout.LayoutParams(webViewWantedWidth, 150)); //ViewGroup.LayoutParams.WRAP_CONTENT));
    //if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() myWebView.getWidth()="+myWebView.getWidth()+" myWebView.getHeight()="+myWebView.getHeight());

    WebSettings webSettings = myWebView.getSettings();
    if(webSettings!=null) {
      webSettings.setJavaScriptEnabled(true);
      webSettings.setPluginsEnabled(false);
      webSettings.setSavePassword(false);
      webSettings.setSaveFormData(false);
      webSettings.setSupportZoom(false);
      webSettings.setBuiltInZoomControls(false);
      webSettings.setRenderPriority(WebSettings.RenderPriority.LOW); // making UI/GL threads smoother
    }

    myWebView.setWebViewClient(new WebViewClient() {
      @Override
      public void onReceivedError(WebView webview, int errorCode, String description, String  failingUrl) {
        ((MyWebView)webview).webViewError=true;
        if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onReceivedError() errorCode="+errorCode+" description="+description+" failingUrl="+failingUrl);
      }

      @Override
      public void onPageStarted(WebView webview, String url, Bitmap favicon) {
        //if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onPageStarted() url="+url);
        Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST); // to make UI/GL threads smoother
      }

      @Override
      public void onScaleChanged(WebView webview, float oldScale, float newScale) {
        if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onScaleChanged() oldScale="+oldScale+" newScale="+newScale);
      }

      @Override
      public void onUnhandledKeyEvent(WebView webview, KeyEvent event) {
        if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onUnhandledKeyEvent()");
      }

      @Override
      public void onPageFinished(WebView webview, String  url) {
        if(!((MyWebView)webview).webViewError /*&& url.startsWith(fetchUrlString)*/) {
          ((MyWebView)webview).acceptWebViewPictureState=true;
          //if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onPageFinished() set acceptWebViewPictureState=true");
        } else {
          //if(Config.LOGD) Log.i(LOGTAG+" MyWebViewClient", " onPageFinished() NOT set acceptWebViewPictureState=true");
        }
      }
    });

    myWebView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onConsoleMessage(String message, int lineNumber, String sourceID)
      {
        // note: this is for console.log() messages
        // note: JS client can also use JsObject.debug()
        Log.i(LOGTAG+" MyWebChromeClient", "consoleDebug:"+ message + " -- From line " + lineNumber + " of " + sourceID);
        //Log.i(LOGTAG+" MyWebChromeClient", "consoleDebug:"+ message + " -- line:"+lineNumber);
        //Log.i(LOGTAG+" MyWebChromeClient", "consoleDebug:"+ message);
      }
    });

    myWebView.setPictureListener(new WebView.PictureListener() {
      @Override
    	public void onNewPicture(WebView webview, Picture picture) {
    	  //if(activityDestroyed) // todo
    	  //  return;

    	  if(!((MyWebView)webview).acceptWebViewPictureState) { 
    	    // not all images of the webdocument are loaded yet
    	    // there must be a preceeding onPageFinished() and no onReceivedError()
          //if(Config.LOGD) Log.d(LOGTAG, "onNewPicture picture.getHeight()="+picture.getHeight()+" picture.getWidth()="+picture.getWidth()+" NO acceptWebViewPictureState !!!!!");
    	    return;
    	  }

        ((MyWebView)webview).acceptWebViewPictureState=false;
        //if(Config.LOGD) Log.d(LOGTAG, "onNewPicture picture.getHeight()="+picture.getHeight()+" webViewWantedHeight="+webViewWantedHeight);
        Bitmap bitmap = takeSnapshot(webview);
        EntryTopic entryTopic = myWebView.getJsObject().entryTopic;

        //if(Config.LOGD) Log.i(LOGTAG, "onNewPicture loadUrl next; this="+entryTopic.title); //+" msgsInQueue="+messageList.size());
        myWebView.webViewError=false;
        myWebView.loadUrl(fetchUrlString);

        if(bitmap!=null) {
          //if(Config.LOGD) Log.d(LOGTAG, "onNewPicture addBitmap for title="+entryTopic.title);
          // allow only msgs with a title
          if(entryTopic!=null && entryTopic.title!=null && entryTopic.title.length()>0) {
//            if(Config.LOGD) Log.d(LOGTAG, "onNewPicture picture.getHeight()="+picture.getHeight()+
//                                         " webViewWantedHeight="+webViewWantedHeight+
//                                         " bitmap.getHeight()="+bitmap.getHeight()+
//                                         " title="+entryTopic.title);
            if(mView.addBitmap(bitmap,entryTopic)<0) {
              // the bitmap was not added
              Log.e(LOGTAG, "onNewPicture failed to addBitmap");
            }  
          } else {
            // the bitmap was not added (this is not an error on the very 1st snapshot)
            Log.e(LOGTAG, "onNewPicture entryTopic/title empty after takeSnapshot()");
          }
          //bitmap.recycle(); bitmap=null;
        } else {
          // the bitmap was not added
          Log.e(LOGTAG, "onNewPicture bitmap=null after takeSnapshot()");
        }
      }

      private Bitmap takeSnapshot(WebView webView)
      {
        if(webView!=null) {
          Picture picture = webView.capturePicture();
          if(picture.getWidth()>0 && picture.getHeight()>0) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST); // make UI/GL threads smoother

            int newBitmapWidth = picture.getWidth();
            int newBitmapHeight = picture.getHeight();
            //if(Config.LOGD) Log.d(LOGTAG, "takeSnapshot() picture.getHeight()="+picture.getHeight()+"/"+newBitmapHeight+" picture.getWidth()="+picture.getWidth()+"/"+newBitmapWidth);

            newBitmapHeight = Math.min(newBitmapHeight, ((MyWebView)webView).jsObject.renderHeight+5);   // renderHeight = the actual used height
            newBitmapHeight = Math.max(newBitmapHeight,webView.getHeight());


            try {            
              Bitmap bitmap = Bitmap.createBitmap(newBitmapWidth, newBitmapHeight, Bitmap.Config.ARGB_8888);
              if(bitmap==null) {
                Log.e(LOGTAG, "takeSnapshot() Bitmap.createBitmap() returned null");
                return null;
              }


              if(Config.LOGD) Log.i(LOGTAG,String.format("inst=%d takeSnapshot() newBitmapHeight=%d webView.getWidth()=%d webView.getHeight()=%d renderheight=%d lineBytes=%d",
                inst,newBitmapHeight,webView.getWidth(),webView.getHeight(),((MyWebView)webView).jsObject.renderHeight,bitmap.getRowBytes()));

              Canvas canvas = new Canvas(bitmap);
              picture.draw(canvas);

              {    
                // create transparent black background
                // whenever the color of a pixel is full black in the webview-bitmap, we will set it's opacity to '0' as well, this makes black transparent
                //if(Config.LOGD) Log.d(LOGTAG, "takeSnapshot() create transparent black for "+newBitmapWidth+" x "+newBitmapHeight+" bytes");
                bitmap.getPixels(pxls, 0, newBitmapWidth, 0, 0, newBitmapWidth, newBitmapHeight);
                for(int xa=0, len=pxls.length; xa<len; xa++) {
                  if((pxls[xa] & 0x00FFFFFF)==0x000000) {  
                    // we got ARGB format: if RGB are all 00 (black), we set alpha to 00
                    //pxls[xa] = 0x80000000;    // half transparent
                    pxls[xa] = 0x40000000;      // 3-quarter transparent
                    //pxls[xa] = 0x00000000;    // full transparent
                  }
                }
                bitmap.setPixels(pxls, 0, newBitmapWidth, 0, 0, newBitmapWidth, newBitmapHeight);
              }


            //if(Config.LOGD) Log.d(LOGTAG, "takeSnapshot() bitmap.getHeight="+bitmap.getHeight()+" bitmap.getWidth="+bitmap.getWidth()+" density="+bitmap.getDensity()+" hasAlpha="+bitmap.hasAlpha());

//                      // this is how we can store a snapshot picture as PNG on sdcard
//                      try {
//                        File sdcard = Environment.getExternalStorageDirectory();
//                        File dir = new File(sdcard.getAbsolutePath() + "/GlTicker");
//                        if(Config.LOGD) Log.d(LOGTAG, "mkdir "+dir.toString());
//                        dir.mkdirs();

//                        {
//                          String filename = (String)"snap_"+snapshotCounter+".bm";
//                          File file = new File(dir, filename);
//                          if(Config.LOGD) Log.d(LOGTAG, "create "+file.toString());
//                          FileOutputStream fileOutputStream = new FileOutputStream(file);
//                          bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
//                        }

//                        snapshotCounter++;
//                      } catch (Exception e) {
//                         e.printStackTrace();
//                      }

              //if(Config.LOGD) Log.d(LOGTAG, "takeSnapshot() done");
              return bitmap;

            } catch(Exception ex) {
              Log.e(LOGTAG, "takeSnapshot() exception "+ex);
              Toast.makeText(context, ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
          }
        }
        return null;
      }
    });

    if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() frameLayout.addView(myWebView)");
    frameLayout.addView(myWebView,1);

    //if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() myWebView.getJsObject().setMaxRenderHeight("+maxRenderHeight+")");
    // the web rendering should never produce taller heights than this
    myWebView.getJsObject().setMaxRenderHeight(maxRenderHeight);  // value from onCreate()

          // hack! we sneek in an empty message, because the very 1st message (after warm start)
          // is likely to get rendered before we receive "onScaleChanged() oldScale=1.5 newScale=1.0" and will be deformed
          // we filter this empty msg out in onNewPicture(), where we make sure entryTopic.title.length()>0)
          EntryTopic entryTopic = new EntryTopic(0, 0, "", "", "", "", 0l);
          messageList.addFirst(entryTopic);

    myWebView.webViewError=false;
    if(Config.LOGD) Log.i(LOGTAG, "startNextWebview() layout webViewWantedWidth="+webViewWantedWidth+" webViewWantedHeight="+webViewWantedHeight+" fetchUrlString="+fetchUrlString);
    myWebView.layout(0, 0, webViewWantedWidth, webViewWantedHeight);
    myWebView.loadUrl(fetchUrlString);
    // livecastServiceClient in the background will messageList.add()
    // and JS will call jsobject.getEntry() -> messageList.removeFirst()
  }
}

