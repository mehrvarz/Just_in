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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.widget.Scroller;
import android.widget.Toast;
import android.net.Uri;
import android.net.wifi.WifiManager;

import android.view.Window;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.WindowManager;

public class JsObject
{
  private final static String LOGTAG = "JsObject";

  private MyWebView mWebView = null;
  private Context mContext = null;
  private LinkedList<EntryTopic> messageList = null;
  private int instNumber=-1;

  protected EntryTopic entryTopic = null;
  protected volatile int renderHeight = 0;
  protected volatile int maxRenderHeight = 0;
  protected volatile boolean currentPositionViewBusy = false;

  public JsObject(MyWebView webview, LinkedList<EntryTopic> messageList, int instNumber) {
    mWebView = webview;
    if(mWebView!=null)
      mContext = mWebView.getContext();
    this.messageList = messageList;
    this.instNumber = instNumber;
  }

  public void setMaxRenderHeight(int height) {     // called by app
    if(Config.LOGD) Log.i(LOGTAG,"setMaxRenderHeight() "+height);
    maxRenderHeight = height;
  }

  public int getMaxRenderHeight() {     // called by JS
    return maxRenderHeight;
  }

  public void setRenderHeight(int height) {     // called by JS
    //if(Config.LOGD) Log.i(LOGTAG,"inst="+instNumber+" setRenderHeight() "+height);
    renderHeight = height;
  }

  public boolean getEntry() {          // called by JS
    if(messageList!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,String.format("inst=%d getEntry() messageList.size()=%d",instNumber,messageList.size()));
      synchronized(messageList) {
        while(messageList.size()<=0) {
          // finished webview-rendering in the background - cancel busy state
          if(currentPositionViewBusy) {
            currentPositionViewBusy = false;
            //if(Config.LOGD) Log.i(LOGTAG,"getEntry() finished webview-rendering");
            ((GlActivityAbstract)mContext).runOnUiThread(new Runnable() {
              public void run() { ((GlActivityAbstract)mContext).currentPositionView.setBusy(false); }
            });
          }

          try {
            messageList.wait();
          } catch(java.lang.InterruptedException iex) {
            Log.e(LOGTAG, "getEntry() InterruptedException iex="+iex);
          };

        }

        // start webview-rendering in the background - signal busy state
        if(!currentPositionViewBusy) {
          currentPositionViewBusy = true;
          //if(Config.LOGD) Log.i(LOGTAG,"getEntry() start webview-rendering");
          ((GlActivityAbstract)mContext).runOnUiThread(new Runnable() {
            public void run() { ((GlActivityAbstract)mContext).currentPositionView.setBusy(true); }
          });
        }

        //if(Config.LOGD) Log.i(LOGTAG,"inst="+instNumber+" getEntry() after messageList.wait() messageList.size()="+messageList.size());
        entryTopic = messageList.removeFirst();
        return true;
      }
    }
    if(Config.LOGD) Log.i(LOGTAG,"getEntry() return false");
    return false;
  }

  public String getEntryChannel() {       // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"getEntryChannel() "+entryTopic.channelName);
      return entryTopic.channelName;
    }
    return null;
  }

  public String getEntryTitle() {         // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"inst="+instNumber+" getEntryTitle() title="+entryTopic.title);
      //if(Config.LOGD) Log.i(LOGTAG,"inst="+instNumber+" getEntryTitle() activityDestroyed="+((GlActivity)mContext).activityDestroyed);
      return entryTopic.title;
    }
    return null;
  }

  public String getEntryDescription() {   // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"getEntryDescription() "+entryTopic.description);
      return entryTopic.description;
    }
    return null;
  }

  public String getEntryLink() {          // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"getEntryLink() "+entryTopic.link);
      return entryTopic.link;
    }
    return null;
  }

  public String getEntryImageUrl() {      // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"getEntryImageUrl() "+entryTopic.imageUrl);
      return entryTopic.imageUrl;
    }
    return null;
  }

  public long getEntryTime() {            // called by JS
    if(entryTopic!=null) {
      //if(Config.LOGD) Log.i(LOGTAG,"getEntryTime() "+entryTopic.createTimeMs);
      return entryTopic.createTimeMs;
    }
    return 0l;
  }

  public void debug(String logmsg) {      // called by JS
    // out-comment to make JS msgs disappear
    //if(Config.LOGD) Log.i(LOGTAG,"JS: "+logmsg);
  }
}

