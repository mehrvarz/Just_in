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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.Config;
import android.util.Log;
import android.util.DisplayMetrics;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.os.Environment;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.media.MediaPlayer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.lang.reflect.Constructor;
import java.io.File;
import java.lang.reflect.Method;

public abstract class GlActivityAbstract extends Activity
{
  protected static String appname = "GlTicker";
  protected static String LOGTAG = appname+"Activity";

  protected static String glViewClassName = null;
  protected static String serviceClassName = null;
  public static String TICKER_NEW_ENTRY_BROADCAST_ACTION = null; 
  protected static String serviceArg1 = null; // consumerKey
  protected static String serviceArg2 = null; // consumerSecret
  protected static int MAX_NUMBER_ONLOAD_MSGS = 0;
  protected static String PREFS_NAME = null;

  protected static DisplayMetrics displayMetrics = null; // current actual width/height
  protected static int portraitWidth=0, portraitHeight=0; // current width/height, if the device was in portrait (height>=width)
  protected static int webViewWantedWidth=0, webViewWantedHeight=0; // calculated optimal texture width/height

  private static SharedPreferences preferences = null;    // set by 1st call to getPreferences()
  private static SharedPreferences.Editor editor = null;  // set by 1st call to getPreferences()
  protected LinkedList<EntryTopic> messageList = new LinkedList<EntryTopic>();
  protected volatile long lastWatchedMsgTimeMs = 0l;    // set by getPreferences() and storeLastWatchedMsgTimeMs(), directly accessed from ServiceClient.java
  private int snapshotCounter = 0;
  public GlTickerView glView = null;
  private boolean glViewAdded = false;
  private Constructor glViewConstructor = null;
  private LinearLayout linearLayout = null;
  public static FrameLayout frameLayout = null;
  protected ServiceClientInterface serviceClientObject = null;
  protected volatile boolean activityDestroying = false;
  protected volatile boolean activityDestroyed = false;
  protected CurrentPositionView currentPositionView = null;
  public static ShowTouchAreaView showTouchAreaView = null;
  public static long showTouchAreaViewActiveSince = 0l;
  protected volatile boolean activityResumed = false;
  private Method webkitPause = null, webkitResume = null;
  protected static volatile long lastCurrentVisibleUpdateMS = 0l;
  protected long autoForwardDelay = 6000l;
  protected long autoScreenDimDelay = 15000l;
  public static Context context = null;
  public static long lastJingleTime=0l;

  protected abstract void setAppConfig();
    //appname = "ThisJustIn";
    //LOGTAG = appname+"Activity";
    //glViewClassName = "org.timur.glticker.GlTicker2View";
    //autoForwardDelay = 4000l;
    //serviceClassName = "com.vodafone.twitter.service.TwitterService";
    //serviceArg1 = "_________________";
    //serviceArg2 = "_________________________________________";
    //TICKER_NEW_ENTRY_BROADCAST_ACTION = "org.timur.thisjustin.NewMessage";
    //MAX_NUMBER_ONLOAD_MSGS = org.timur.glticker.GlTickerView.MAX_BITMAPS;
    //PREFS_NAME = "org.timur.thisjustin";


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    /*final Context*/ context = this;
    activityResumed = false;
    super.onCreate(savedInstanceState);
    activityDestroyed = activityDestroying = false;
    if(Config.LOGD) Log.i(LOGTAG, "onCreate ********************************************************************");

    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setAppConfig();

    frameLayout = new FrameLayout(this);
    setContentView(frameLayout);
    View rootView = frameLayout.getRootView();
    rootView.setBackgroundColor(android.R.color.black);

    getMetrics("onCreate");

    showTouchAreaView = new ShowTouchAreaView(this,displayMetrics);
    showTouchAreaView.setLayout(displayMetrics.widthPixels,displayMetrics.heightPixels);
    showTouchAreaView.showButtons(false); showTouchAreaViewActiveSince = 0l;
    frameLayout.addView(showTouchAreaView);

    if(Config.LOGD) Log.i(LOGTAG, "instantiate "+glViewClassName+" ...");
    try {
      Class glViewClass = Class.forName(glViewClassName); // something like "org.timur.glticker.GlTicker2View"
      glViewConstructor = glViewClass.getConstructor(android.content.Context.class, boolean.class, int.class, int.class, int.class, int.class);
    } catch(java.lang.ClassNotFoundException cnfex) {
      Log.e(LOGTAG, "FAILED initializing glViewClass ClassNotFoundException "+cnfex);
      return;
    } catch(java.lang.NoSuchMethodException nsmex) {
      Log.e(LOGTAG, "FAILED initializing glViewClass NoSuchMethodException "+nsmex);
      return;
    }

    if(glViewConstructor!=null) {
      try {
        glView = (GlTickerView)glViewConstructor.newInstance(context,true,16,0,displayMetrics.widthPixels,displayMetrics.heightPixels);
        if(glView==null) {
          if(Config.LOGD) Log.e(LOGTAG, "onCreate initializing GlTicker2View returns glView==null");
          return;
        }
      } catch(java.lang.InstantiationException instex) {
        Log.e(LOGTAG, "FAILED initializing glViewClass InstantiationException "+instex);
        return;
      } catch(java.lang.IllegalAccessException illaccex) {
        Log.e(LOGTAG, "FAILED initializing glViewClass IllegalAccessException "+illaccex);
        return;
      } catch(java.lang.reflect.InvocationTargetException invtarex) {
        Log.e(LOGTAG, "FAILED initializing glViewClass InvocationTargetException "+invtarex);
        return;
      }
    }

    currentPositionView = new CurrentPositionView(this,appname,GlTickerView.MAX_BITMAPS,portraitWidth,portraitHeight);
    if(currentPositionView!=null) {
      frameLayout.addView(currentPositionView,new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
      glView.setCurrentPositionView(currentPositionView); // so glView can on addBitmap() call setLeftRight() 
    }

    getPreferences();  // get lastWatchedMsgTimeMs
    if(Config.LOGD) Log.i(LOGTAG, "onCreate from preferences: lastWatchedMsgTimeMs="+lastWatchedMsgTimeMs);

    if(Config.LOGD) Log.i(LOGTAG, "onCreate instantiate ServiceClient for service "+serviceClassName);
    serviceClientObject = new ServiceClient(this,GlTickerView.MAX_BITMAPS,MAX_NUMBER_ONLOAD_MSGS);
    if(serviceClientObject==null) {
      if(Config.LOGD) Log.e(LOGTAG, "onCreate FAILED initializing ServiceClient");
      Toast.makeText(context, "Unable to initialze serviceclient", Toast.LENGTH_LONG).show();
      return;
    }
    // bind our configurable serviceClassImplementation (Push-Livecast or Push-Twitter)
    if(Config.LOGD) Log.i(LOGTAG, "onCreate serviceClientObject.init");
    serviceClientObject.init(serviceClassName,TICKER_NEW_ENTRY_BROADCAST_ACTION,serviceArg1,serviceArg2); // startService(), bindService(), ...
    if(Config.LOGD) Log.i(LOGTAG, "onCreate serviceClientObject.init done");

    // todo: we only use NUMBER_OF_WEBVIEWS = 1
    for(int i=0; i<MyWebView.NUMBER_OF_WEBVIEWS; i++) {
      if(Config.LOGD) Log.i(LOGTAG, "onCreate new MyWebView() i="+i);
      MyWebView.myWebViewArray[i] = new MyWebView(this,messageList,i);
      if(Config.LOGD) Log.i(LOGTAG, "onCreate MyWebView.myWebViewArray[i].setVisibility(View.INVISIBLE)");
      MyWebView.myWebViewArray[i].setVisibility(View.INVISIBLE); // put in comment to make webview-rendering live visible
    }

    if(Config.LOGD) Log.i(LOGTAG, "onCreate MyWebView.setWantetWebviewDimensions()...");
    MyWebView.setWantetWebviewDimensions(webViewWantedWidth,webViewWantedHeight); // so that MyWebView creates webpages with the desired maximum dimensions
    MyWebView.setMaxRenderHeight(webViewWantedHeight); // so that MyWebView is able to tell it's JsObject's about the max desired renderHeight
    MyWebView.setFrameLayout(frameLayout); // so that MyWebView can (in startNextWebview()) add webviews to the frameLayout
    MyWebView.setView(glView); // so that MyWebView can add bitmaps to glView
    // we will start our webview below, after the service is connected

    // start general purpose background thread
    if(Config.LOGD) Log.i(LOGTAG, "onCreate start general purpose background thread...");
    (new Thread() { public void run() {
      // wait for service (which might use a webview to login the user) to become connected...
      if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread wait for service to become connected activityDestroying="+activityDestroying+" isConnected()="+serviceClientObject.isConnected()+" ...");
      while(!activityDestroying && !serviceClientObject.isConnected()) {
        final String errMsg = serviceClientObject.getErrMsg();
        if(errMsg!=null) {
          if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread service errMsg="+errMsg);
          ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
            public void run() {
              Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show();
            }
          });
        }
        try { Thread.sleep(200); } catch(Exception ex) { }
      }
        
      if(serviceClientObject.isConnected()) {
        if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread service is now connected - showTouchAreaViewActiveSince="+showTouchAreaViewActiveSince);
        // start actvity-background-loop...
        int lastCurrentEntryPos = -1;
        boolean lastAutoForward = false;
        int loopCount=0;
        lastCurrentVisibleUpdateMS = SystemClock.uptimeMillis();
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock mWakeLock = null;
        while(!activityDestroying) {
          // do things periodically ...
          try { Thread.sleep(250); } catch(Exception ex) { }
          loopCount++;

          // when currentEntryPos has changed, store it in preferences
          if(glView!=null && (glView.currentEntryPos != lastCurrentEntryPos || glView.autoForward!=lastAutoForward)) {
            if(glView.currentEntryPos<0 || glView.currentEntryPos>=glView.MAX_BITMAPS) {
              //Log.e(LOGTAG,String.format("onCreate background thread STORE new currentEntryPos=%d ArrayIndexOutOfBoundsException", glView.currentEntryPos));
            } else {
              if(glView.entryTopicArray[glView.currentEntryPos]!=null) {
                if(glView.currentEntryPos != lastCurrentEntryPos) {
                  storeLastWatchedMsgTimeMs(glView.entryTopicArray[glView.currentEntryPos].createTimeMs);
                  //if(Config.LOGD) Log.i(LOGTAG,String.format("onCreate background thread STORE new currentEntryPos=%d", glView.currentEntryPos));
                  lastCurrentVisibleUpdateMS = SystemClock.uptimeMillis();
                  //if(Config.LOGD) Log.i(LOGTAG,String.format("STORED new currentEntryPos=%d", glView.currentEntryPos));
                }
              }

              lastCurrentEntryPos = glView.currentEntryPos;
              lastAutoForward = glView.autoForward;
            }
          }

          if(glView!=null && glView.autoForward) {
            //if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread glView.autoForward lastCurrentEntryPos="+lastCurrentEntryPos+" bitmapCount="+glView.bitmapCount+" ageMS="+(SystemClock.uptimeMillis()-lastCurrentVisibleUpdateMS));

            if(lastCurrentEntryPos<glView.bitmapCount-1) {
              if(SystemClock.uptimeMillis()-lastCurrentVisibleUpdateMS > autoForwardDelay) {
                lastCurrentVisibleUpdateMS = SystemClock.uptimeMillis();

                if(mWakeLock==null) {
                  mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP, LOGTAG);
                  mWakeLock.acquire();
                  //if(Config.LOGD) Log.i(LOGTAG,onCreate background thread SCREEN_BRIGHT_WAKE_LOCK");
                }

                // todo: jingle only if last jingle is 10s or older time date
                if(SystemClock.uptimeMillis()-lastJingleTime>15000l) {
                  MediaPlayer mediaPlayer = MediaPlayer.create(context, org.timur.justin.R.raw.confirm8bit); // non-alert
                  if(mediaPlayer!=null) {
                    mediaPlayer.start();
                  }
                }

                lastJingleTime = SystemClock.uptimeMillis();

                ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
                  public void run() {
                    glView.zAnimStep = glView.zAnimStepSlow;
                    glView.switchNextPage();
                  }
                });
              }
            }

            if(mWakeLock!=null && SystemClock.uptimeMillis()-lastCurrentVisibleUpdateMS > autoScreenDimDelay) {
              //if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread SCREEN_BRIGHT_WAKE_LOCK OFF");
              mWakeLock.release();
              mWakeLock=null;
            }
          }     

//          // auto-deactivate help
//          if(showTouchAreaViewActiveSince>0l) {
//            if(SystemClock.uptimeMillis()>showTouchAreaViewActiveSince+2000l) {
//              if(Config.LOGD) Log.i(LOGTAG, "onCreate background thread - deactivate showTouchArea #######################");
//              ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
//                public void run() {
//                  showTouchAreaView.showButtons(false);
//                  showTouchAreaViewActiveSince = 0l;
//                }
//              });
//            }
//          }

          // every 5 minutes: scan through our tmp-folder and remove all "files older that the oldest loaded msg"
          if(loopCount % (5*60*4) == 0) {
            if(Config.LOGD) Log.i(LOGTAG,"onCreate background thread CHECK FOR DELETE");
            if(glView!=null && glView.bitmapCount==glView.MAX_BITMAPS) {
              // delete outdated bm's, the ones in the tmp-folder that are older than the oldest active msg
              long oldestMs = glView.entryTopicArray[0].createTimeMs;
              int delCount=0;
              //if(Config.LOGD) Log.i(LOGTAG,String.format("onCreate background thread CHECK FOR DELETE oldestMs=%d", oldestMs));
              if(oldestMs>0l) {
                // loop through all files in tmp folder
                File tmpDirectory = context.getCacheDir();
                if(tmpDirectory!=null) {
                  //if(Config.LOGD) Log.i(LOGTAG,String.format("onCreate background thread CHECK FOR DELETE successfully created tmpDirectory"));
                  File[] files = tmpDirectory.listFiles();
                  if(files!=null && files.length>0) {
                    if(Config.LOGD) Log.i(LOGTAG,String.format("onCreate background thread CHECK FOR DELETE got list of files"));
                    for(File file : files) {
                      String filename = file.getName();
                      int idxExtensionBm = filename.indexOf(".bm");
                      if(idxExtensionBm>=0) {
                        //if(Config.LOGD) Log.i(LOGTAG,String.format("CHECK FOR DELETE=%s", filename));
                        String baseFilename = filename.substring(0,idxExtensionBm);
                        long timeMsOfFile = new Long(baseFilename).longValue();
                        if(timeMsOfFile<oldestMs) {
                          //if(Config.LOGD) Log.i(LOGTAG,String.format("DELETING=%s", filename));
                          file.delete();
                          delCount++;
                        }
                      }
                    }
                  }
                }
                if(delCount>0) {
                  if(Config.LOGD) Log.i(LOGTAG, String.format("onCreate background thread DELETED %d", delCount));
                }
              }
            }
          }
        }
      }
    }}).start();

    if(Config.LOGD) Log.i(LOGTAG, "onCreate() done");
  }

  @Override
  protected void onRestart() {
    // activity comes to foreground after onStop: onRestart() will be called instead of onCreate()
    super.onRestart();
    if(Config.LOGD) Log.i(LOGTAG, "onRestart");
  }

  @Override 
  protected void onResume() {
    // activity will start interacting with the user (after onStart() or after onPause())
    // glView.onSurfaceCreated() will happen in parallel
    if(Config.LOGD) Log.i(LOGTAG, "onResume glView="+glView);
    super.onResume(); 
    activityResumed = true;

    // todo: check error conditions: glView==null, serviceClientObject==null, MyWebView.myWebViewArray[0]==null

    if(serviceClientObject!=null) {
      final Context context = this;
      (new Thread() { public void run() {
        if(!glViewAdded) {
          // we want make our glView visible only AFTER we are connected
          // because in order to get connected, our service may need to open a dedicated login view, which would quickly destroy our glView
          // so wait (up to 20 secs) for service to connect to server
          for(int j=0; j<200; j++) {
            if(!activityResumed)
              return;
            if(activityDestroying)
              return;

            try { Thread.sleep(100); } catch(Exception ex) { }
            if(serviceClientObject.isConnected()) {
              if(Config.LOGD) Log.i(LOGTAG, "onResume service is connected after "+(j*100)+" ms");
              break;
            }
            if(j>=10 && !serviceClientObject.isConnecting()) {
              Log.e(LOGTAG, "onResume service is not connected and not connecting after "+(j*100)+" ms");
              return;
            }
          }

          if(!serviceClientObject.isConnected() && !serviceClientObject.isConnecting()) {
            if(Config.LOGD) Log.i(LOGTAG, "onResume service not connected and not connecting after wait loop");
            return;
          }

          if(glView==null) {
            Log.e(LOGTAG, "onResume glView==null");
            return;
          }

          // now save to activate our glView 
          if(Config.LOGD) Log.i(LOGTAG, "onResume add glView glView==null; startNextWebview()");
          ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
            public void run() { 
              frameLayout.addView(glView,0); 
              glViewAdded=true; 
              MyWebView.startNextWebview(); // -> frameLayout.addView(myWebView) -> loadUrl()
            }
          });
        }

        if(glView==null) {
          if(Config.LOGD) Log.i(LOGTAG, "onResume glView==null");
          return;
        }

        if(Config.LOGD) Log.i(LOGTAG, "onResume glView.onResume() glView="+glView+" glView.bitmapCount="+glView.bitmapCount);
        glView.onResume();

        // fetch data from service after sleep/wake: we may receive a complete replacement of our entryTopicArray[]
        // the following code tries to handle these situations by making sure not too many zoom animations result from this
        if(Config.LOGD) Log.i(LOGTAG, "onResume serviceClientObject.start() isConnected()="+serviceClientObject.isConnected()+" isConnecting()="+serviceClientObject.isConnecting());
        final int newMsgsCount = serviceClientObject.start(); // -> fetchTimeline() -> pullUpdateFromServer()
        // currentEntryPos= -1 on warmstart
        if(Config.LOGD) Log.i(LOGTAG, "onResume newMsgsCount="+newMsgsCount+" currentEntryPos="+glView.currentEntryPos+" bitmapCount="+glView.bitmapCount);
        if(newMsgsCount>0 && glView.bitmapCount+newMsgsCount>glView.MAX_BITMAPS && newMsgsCount > glView.currentEntryPos+1) {
          int slideForward = newMsgsCount - glView.currentEntryPos+1;
          if(glView.currentEntryPos+slideForward > glView.bitmapCount)
            slideForward -= glView.currentEntryPos+slideForward - glView.bitmapCount;

//          while(glView.currentEntryPos+slideForward >= glView.MAX_BITMAPS-1)
//            slideForward--;
          if(slideForward>=glView.MAX_BITMAPS)
            slideForward = glView.MAX_BITMAPS-1;
          while(glView.currentEntryPos+slideForward >= glView.MAX_BITMAPS-1)
            slideForward--;

          if(slideForward>0) {
            glView.targetEyeZ -= slideForward * glView.pageZdist;
            // double safety:
            //if(glView.targetEyeZ<defaultCamToTextureDistance)
            //  glView.targetEyeZ=defaultCamToTextureDistance;
            glView.eyeZ = glView.targetEyeZ;
          }

          if(Config.LOGD) Log.i(LOGTAG, "onResume serviceClientObject.start() newMsgsCount="+newMsgsCount+" > glView.currentEntryPos="+glView.currentEntryPos+" bitmapCount="+glView.bitmapCount+" slideForward="+slideForward);
          if(newMsgsCount > 5) {
            ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
              public void run() { Toast.makeText(context,"loading "+newMsgsCount+" new msgs ...", Toast.LENGTH_LONG).show(); }
            });
          }
        }
      }}).start();
    }

    if(MyWebView.myWebViewArray[0]!=null) {
      // see: http://stackoverflow.com/questions/2040963/webview-threads-never-stop-webviewcorethread-cookiesyncmanager-http0-3
      try {
        if(webkitResume==null)
          webkitResume = Class.forName("android.webkit.WebView").getMethod("onResume", (Class[]) null);
        webkitResume.invoke(MyWebView.myWebViewArray[0], (Object[]) null);
      } catch(Exception ex) {
        Log.e(LOGTAG, "onResume failed to call onResume on myWebView");
      }
    }

    // trigger MyWebView to continue converting msgs from the messageList into textures
    synchronized(messageList) {
      messageList.notify();
    }
  }

  @Override
  protected void onStart() {
    // activity is becoming visible to the user (after onCreate() or after onRestart(), before onResume())
    if(Config.LOGD) Log.i(LOGTAG, "onStart");
    super.onStart();
    getMetrics("onStart");
  }
  
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    if(Config.LOGD) Log.i(LOGTAG, "onWindowFocusChanged hasFocus="+hasFocus);
    super.onWindowFocusChanged(hasFocus);
    if(glView!=null)
      glView.onWindowFocusChanged(hasFocus);
    if(Config.LOGD) Log.i(LOGTAG, "onWindowFocusChanged done hasFocus="+hasFocus);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Log.i(LOGTAG, "onConfigurationChanged() orientation="+newConfig.orientation);
    // mView.onSurfaceChanged() will also be called now
    getMetrics("onConfigurationChanged");
    if(showTouchAreaView!=null)
      showTouchAreaView.setLayout(displayMetrics.widthPixels,displayMetrics.heightPixels);
  }

  @Override 
  protected void onPause() {
    // activity will become invisible (maybe going to sleep)
    // used to commit unsaved changes, to stop animations and other things that may be consuming CPU
    // other activity waits for this to finish before it gets into foreground (be quick)
    activityResumed = false;
    super.onPause();
    if(Config.LOGD) Log.i(LOGTAG, "onPause glView="+glView);

    try {
      if(glView!=null) {
        glView.onPause(); // this will destroy our GL context
      }
    } catch(Exception ex) {
      ex.printStackTrace();
    }
    if(serviceClientObject!=null)
      serviceClientObject.pause();  // -> activityPaused=true;

    if(MyWebView.myWebViewArray[0]!=null) {
      // see: http://stackoverflow.com/questions/2040963/webview-threads-never-stop-webviewcorethread-cookiesyncmanager-http0-3
      try {
        if(webkitPause==null)
          webkitPause = Class.forName("android.webkit.WebView").getMethod("onPause", (Class[]) null);
        webkitPause.invoke(MyWebView.myWebViewArray[0], (Object[]) null);
      } catch(Exception ex) {
        Log.e(LOGTAG, "onPause failed to call onPause on android.webkit.WebView");
      }
    }
  }

  @Override
  protected void onStop() {
    // our activity has now become invisible
    activityResumed = false;
    super.onStop();
    if(glView!=null) {
      glView.onStop();
    }
  }

  @Override
  protected void onDestroy() {
    activityResumed = false;
    activityDestroying = true;
    if(Config.LOGD) Log.i(LOGTAG, "onDestroy messageList notfy...");

    // release a webview instance possibly being stucked in JsObject.getEntry() messageList.wait();
    synchronized(messageList) {
            // JsObject.getEntry() was hanging (causing user triggered restart issues)
            // quick fix: add one dummy entry to messageList before notify()
            // todo: must revisit this
            EntryTopic entryTopic = new EntryTopic(0, 0, "", "", "", "", 0l);
            messageList.addFirst(entryTopic);
      messageList.notify();
    }
    if(Config.LOGD) Log.i(LOGTAG, "onDestroy messageList notfied");

    for(int i=0; i<MyWebView.NUMBER_OF_WEBVIEWS; i++) {
      if(MyWebView.myWebViewArray[i]!=null) {
        MyWebView.myWebViewArray[i].destroy();
        MyWebView.myWebViewArray[i]=null;
      }
    }
    if(Config.LOGD) Log.i(LOGTAG, "onDestroy myWebViews were destroyed...");

    if(serviceClientObject!=null) {
      if(Config.LOGD) Log.i(LOGTAG, "onDestroy serviceClient destroy...");
      serviceClientObject.destroy(); // -> unbindService(serviceConnection)
      if(Config.LOGD) Log.i(LOGTAG, "onDestroy serviceClient destroyed");
    }

    super.onDestroy();
    activityDestroyed=true;

    if(Config.LOGD) Log.i(LOGTAG, "onDestroy done ****************************************");
  }

  ////////////////////////////////////////////////////////////////////////

  public void storeLastWatchedMsgTimeMs(long setLastWatchedMsgTimeMs) {
    lastWatchedMsgTimeMs = setLastWatchedMsgTimeMs;
    if(editor==null)
      editor = preferences.edit();
    if(editor!=null) {
      if(lastWatchedMsgTimeMs>0l) {
        //if(Config.LOGD) Log.i(LOGTAG, "storeLastWatchedMsgTimeMs lastWatchedMsgTimeMs="+lastWatchedMsgTimeMs+" title="+glView.currentlyShownEntry().title);
        editor.putString("lastWatchedMsgTimeMs", ""+lastWatchedMsgTimeMs);
        editor.commit();
      }
    }
  }

  private void getMetrics(String info) {
    if(displayMetrics==null)
      displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    
    // fix a Android bug
    if(android.os.Build.MODEL.equals("Milestone") && displayMetrics.ydpi==96f)
      displayMetrics.ydpi=240;
    
    if(Config.LOGD) Log.i(LOGTAG, info+" displayMetrics.width="+displayMetrics.widthPixels+" displayMetrics.height="+displayMetrics.heightPixels+" density="+displayMetrics.density+" scaledDensity="+displayMetrics.scaledDensity+" dpi="+displayMetrics.xdpi+"/"+displayMetrics.ydpi);
    // Galaxy 10'' HoneyComb: displayMetrics.width=1280 displayMetrics.height=800 density=1.0 scaledDensity=1.0 dpi=160.15764/160.0

    // our webview render box should be a little smaller than a phone screen - and much smaller than a tablet screen
    if(displayMetrics.widthPixels>displayMetrics.heightPixels) {
      portraitWidth = displayMetrics.heightPixels;
      portraitHeight = displayMetrics.widthPixels;
    } else {
      portraitWidth = displayMetrics.widthPixels;
      portraitHeight = displayMetrics.heightPixels;
    }
    webViewWantedWidth = Math.min(580,portraitWidth-50);
    webViewWantedHeight = 480; // note: because we further zoom in landscape mode, a msg of 500 pix height is all that fits 800-x screen width
    // webViewWantedWidth and webViewWantedHeight don't need to depend on portraitWidth and portraitHeight
    // because we dynamically attach a zoom-factor deüending on screen dimensions in onSurfaceChanged()
    if(Config.LOGD) Log.i(LOGTAG, info+" webViewWantedWidth="+webViewWantedWidth+" webViewWantedHeight="+webViewWantedHeight+" portraitWidth="+portraitWidth);
  }

  protected void getPreferences() {
    if(preferences==null)
      preferences = getSharedPreferences(PREFS_NAME, MODE_WORLD_WRITEABLE);

    if(preferences!=null) {
      String lastWatchedMsgTimeMsString = preferences.getString("lastWatchedMsgTimeMs", "");
      if(lastWatchedMsgTimeMsString!=null && lastWatchedMsgTimeMsString.length()>0) {
        try {
          lastWatchedMsgTimeMs = new Long(lastWatchedMsgTimeMsString).longValue();
        } catch(java.lang.NumberFormatException nfex) {
          if(Config.LOGD) Log.e(LOGTAG, "getPreferences() lastWatchedMsgTimeMs nfex=",nfex);
        }
        if(Config.LOGD) Log.i(LOGTAG, "getPreferences() loaded lastWatchedMsgTimeMs="+lastWatchedMsgTimeMs);
      }
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if(showTouchAreaView!=null)
      showTouchAreaView.showButtons(false);
    boolean ret = glView.dispatchKeyEvent(event);
    //if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() ret="+ret);
    if(!ret)
      ret = super.dispatchKeyEvent(event);
    return ret;
  }

  /////////////////////////////////////////////////////////////////////////////// DIALOG
  public static final int DIALOG_ABOUT = 0;
  public static final int DIALOG_USE_MSG = 1;
  public static final int DIALOG_MORE = 2;

  protected static EntryTopic currentEntryTopic = null;

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    if(Config.LOGD) Log.i(LOGTAG, "onPrepareDialog() id="+id);
    if(id==DIALOG_USE_MSG) {
      currentEntryTopic = glView.currentlyShownEntry();
      if(currentEntryTopic!=null) {
        if(Config.LOGD) Log.i(LOGTAG, "onPrepareDialog() link="+currentEntryTopic.link);
      } else {
        if(Config.LOGD) Log.i(LOGTAG, "onPrepareDialog()");
      }
    }
    super.onPrepareDialog(id, dialog);
  }

  /////////////////////////////////////////////////////////////////////////////// MENU
  public static final int MENU_AUTO_FORWARD = 1;
  public static final int MENU_TOUCHAREAS = 2;
  public static final int MENU_PROCESS_MSG = 3;
  public static final int MENU_MORE = 4;

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    // todo: icons for menu items: .setIcon(R.drawable.menu_quit_icon);
    menu.add(Menu.NONE, MENU_AUTO_FORWARD, Menu.NONE, "Auto-forward");
    if(showTouchAreaView!=null)
      menu.add(Menu.NONE, MENU_TOUCHAREAS, Menu.NONE, "Touch areas");
    menu.add(Menu.NONE, MENU_PROCESS_MSG, Menu.NONE, "Use message");
    menu.add(Menu.NONE, MENU_MORE, Menu.NONE, "More ...");
    return super.onCreateOptionsMenu(menu);
  }

  @Override 
  public boolean onOptionsItemSelected(MenuItem item) {
    if(Config.LOGD) Log.i(LOGTAG, "onOptionsItemSelected() id="+item.getItemId());

    if(showTouchAreaView!=null)
      showTouchAreaView.showButtons(false);

    switch(item.getItemId()) {
      case MENU_AUTO_FORWARD:
        if(glView!=null) {
          if(glView.autoForward)
            glView.autoForward = false;
          else
            glView.autoForward = true;
          if(Config.LOGD) Log.i(LOGTAG, "onOptionsItemSelected() autoForward="+glView.autoForward);
        }
        return true;

      case MENU_TOUCHAREAS:
        if(showTouchAreaView!=null) {
          if(showTouchAreaViewActiveSince==0l) {
            showTouchAreaView.showButtons(true);
            showTouchAreaViewActiveSince = SystemClock.uptimeMillis();
          } else {
            showTouchAreaView.showButtons(false);
            showTouchAreaViewActiveSince = 0l;
          }
        }
        return true;
        
      case MENU_PROCESS_MSG:
        if(Config.LOGD) Log.i(LOGTAG, "onOptionsItemSelected() -> showDialog(DIALOG_USE_MSG)");
        showDialog(DIALOG_USE_MSG);
        return true;

      case MENU_MORE:
        showDialog(DIALOG_MORE);
        return true;
    }

    return false;
  }
}

