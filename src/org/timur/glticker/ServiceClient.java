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

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedList;
import java.net.URLEncoder;

import android.net.Uri;
import android.util.Config;
import android.util.Log;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Message;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Vibrator;
import android.widget.Toast;

public class ServiceClient implements ServiceClientInterface
{
  private final static String LOGTAG = "ServiceClient";
  private Context mContext = null;  // startService(), bindService(), ...
  private TickerServiceAbstract mService = null;
  private volatile boolean activityPaused = false; // set by start() / pause()

  // this will always contain the lastMs-value of the very newest msg fetched
  // we use this in pullUpdateFromServer() to only request newer msgs
  protected long newestMessageMS = 0l;

  // both values are overridden by the constructor
  private int messageListMaxSize = 250;   // will be set in constructer (=42)
  protected int maxRequestElements = 100; // will be set in constructer (=40) must be <= messageListMaxSize

  private LinkedList<EntryTopic> messageList = null;
  
  private Intent serviceIntent = null; // set in init()
  private String intentFilter = null; // set by start()

  private volatile int pullUpdateCounter = 0;
  private static Vibrator vibrator = null;

  public ServiceClient(Context context, int messageListMaxSize, int maxRequestElements) {
    mContext = context;
    if(mContext!=null)
      messageList = ((GlActivityAbstract)mContext).messageList;  // todo: we should probably just hand over the messageList object
    this.maxRequestElements = maxRequestElements;
    this.messageListMaxSize = messageListMaxSize;
    newestMessageMS = 0l;
    vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void init(String serviceClass, String intentFilter, String consumerKey, String consumerSecret) {
    // usually called on activity onCreate()
    if(Config.LOGD) Log.i(LOGTAG, "init("+serviceClass+")");
    this.intentFilter = intentFilter;
    if(mContext!=null) {
      if(Config.LOGD) Log.i(LOGTAG, "init() startService() intentFilter="+intentFilter);
      serviceIntent = new Intent();
      serviceIntent.setClassName(mContext, serviceClass);
      serviceIntent.putExtra("consumerKey", consumerKey);
      serviceIntent.putExtra("consumerSecret", consumerSecret);
      serviceIntent.putExtra("intentFilter", intentFilter);
      serviceIntent.putExtra("linkify", "true");
      mContext.startService(serviceIntent); // -> service.onStartCommand() -> try to connect to twitter with new ConnectThread() 

      if(Config.LOGD) Log.i(LOGTAG, "init() bindService()");
      mContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

      // start listening to one specific type of broadcast (coming from the service)
      if(Config.LOGD) Log.i(LOGTAG, "init() registerReceiver() ...");
      mContext.registerReceiver(broadcastReceiver, new IntentFilter(intentFilter));

      if(Config.LOGD) Log.i(LOGTAG, "init() bindService() done");
    }
  }

  public int start() {
    // will be called on activity onResume()
    if(Config.LOGD) Log.i(LOGTAG, "start()");
    activityPaused = false;
    if(mService!=null) {
      mService.onResume(); // -> if not connected, and no ConnectThread is pending, then start a new connectThread (which will fetch the timeline)
      if(isConnected()) {
        int readCount = pullUpdateFromServer();
        if(Config.LOGD) Log.i(LOGTAG, "start() service isConnected; readCount="+readCount+" from pullUpdateFromServer()");
        return readCount;
      }
      Log.e(LOGTAG, "start() service is not connected");
    } else {
      Log.e(LOGTAG, "start() no service object exist");
    }
    return 0;
  }

  public void pause() {
    // will be called on activity onPause()
    if(mService!=null) {
      mService.onPause(); 
      // different services may act differently to this
      // twitter service will stop broadcasting and may instead buzz if a certain amount of messages are received while the activity (the device) sleeps
      // livecast service does nothing special - it continues to work and broadcast the activity
    }
    activityPaused=true;
  }

  public void destroy() {
    // will be called on activity onDestroy()
    if(Config.LOGD) Log.i(LOGTAG, "destroy() serviceConnection="+serviceConnection+" mService="+mService);

    if(serviceConnection!=null /*&& mService!=null*/) {
      mContext.unbindService(serviceConnection);
      if(Config.LOGD) Log.i(LOGTAG, "destroy() unbindService() done");
      // our service will continue to run, since we used startService() in front of bindService()
      // we let the user end everything manually from a menu entry
      serviceConnection = null;
    }

    try {
      mContext.unregisterReceiver(broadcastReceiver);
    } catch(java.lang.IllegalArgumentException iaex) {
      Log.e(LOGTAG, "stop() IllegalArgumentException "+iaex);
    }

    if(Config.LOGD) Log.i(LOGTAG, "destroy() done");
  }

  public boolean isConnected() {
    if(mService!=null)
      return mService.isConnected();
    return false;
  }

  public boolean isConnecting() {
    if(mService!=null)
      return mService.isConnecting();
    return false;
  }

  public String getErrMsg() {
    if(mService!=null)
      return mService.getErrMsg();
    return null;
  }

  public TickerServiceAbstract getServiceObject() {
    return mService;
  }

  // sendToWebClient() called by:
  // - appStart -> onServiceConnected() 
  // - broadcastReceiver.onReceive() -> pullUpdateFromServer()
  // - onResume() -> start() -> pullUpdateFromServer()
  private void sendToWebClient(EntryTopic[] entryTopicArray) {
    if(entryTopicArray.length>0) {
      //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() entryTopicArray.length="+entryTopicArray.length+" lastWatchedMsgTimeMs="+((GlActivityAbstract)mContext).lastWatchedMsgTimeMs);

      if(messageList!=null) {
        synchronized(messageList) {
          // 1st queue in the msg that is equal to last watched
	        for(int i=0; i<entryTopicArray.length; i++) {
	          if(entryTopicArray[i].createTimeMs == ((GlActivityAbstract)mContext).lastWatchedMsgTimeMs) {
              if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() FOUND lastWatchedMsgTimeMs i="+i+" ms="+entryTopicArray[i].createTimeMs);
	            if(!messageList.contains(entryTopicArray[i])) {
                //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() FOUND lastWatchedMsgTimeMs add "+entryTopicArray[i].title+" size="+messageList.size());
                if(entryTopicArray[i].createTimeMs > newestMessageMS)
                  newestMessageMS = entryTopicArray[i].createTimeMs;
                messageList.add(entryTopicArray[i]);
  	            if(messageList.size()>messageListMaxSize) {
                  EntryTopic entryTopic = messageList.removeFirst();
                  //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() messageList.removeFirst() "+entryTopic.title);
                }
              }
              break;
            }
          }

          // 2nd queue in the msgs that are newer than the last watched
	        for(int i=0; i<entryTopicArray.length; i++) {
	          if(entryTopicArray[i].createTimeMs > ((GlActivityAbstract)mContext).lastWatchedMsgTimeMs) {
              //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() NEWER than lastWatchedMsgTimeMs i="+i+" ms="+entryTopicArray[i].createTimeMs);
	            if(!messageList.contains(entryTopicArray[i])) {
                //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() NEWER than lastWatchedMsgTimeMs add "+entryTopicArray[i].title+" size="+messageList.size()+" ms="+entryTopicArray[i].createTimeMs);
                if(entryTopicArray[i].createTimeMs > newestMessageMS)
                  newestMessageMS = entryTopicArray[i].createTimeMs;
                messageList.add(entryTopicArray[i]);
  	            if(messageList.size()>messageListMaxSize) {
                  EntryTopic entryTopic = messageList.removeFirst();
                  //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() messageList.removeFirst() "+entryTopic.title);
                }
              }
            }
          }

          // 3rd queue in the msgs that are older than the last watched
	        for(int i=0; i<entryTopicArray.length; i++) {
	          if(entryTopicArray[i].createTimeMs < ((GlActivityAbstract)mContext).lastWatchedMsgTimeMs) {
              //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() OLDER than lastWatchedMsgTimeMs i="+i+" ms="+entryTopicArray[i].createTimeMs);
	            if(!messageList.contains(entryTopicArray[i])) {
  	            if(messageList.size()<messageListMaxSize) {
                  //if(Config.LOGD) Log.i(LOGTAG, "sendToWebClient() OLDER than lastWatchedMsgTimeMs add "+entryTopicArray[i].title+" size="+messageList.size());
                  if(entryTopicArray[i].createTimeMs > newestMessageMS)
                    newestMessageMS = entryTopicArray[i].createTimeMs;
                  messageList.add(entryTopicArray[i]);
                }
              }
            }
          }

          // do not wake messageList processing (in jsobject) if our activity is in pause mode
          // we do this mainly, because (it seems) we cannot render webpages in sleep mode
          // another reason is that rendering webpages all day in sleep mode would consume too much energy
          if(!activityPaused) { // set by start() and pause()
            messageList.notify();
          }
        }
      }
    }
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    // activated by bindService() in onCreate()
    @Override
    public void onServiceConnected(ComponentName className, IBinder binder) {
      if(Config.LOGD) Log.i(LOGTAG, "onServiceConnected() localBinder.getService() ...");
      TickerServiceAbstract.LocalBinder localBinder = (TickerServiceAbstract.LocalBinder)binder;
      mService = localBinder.getService();
      if(mService==null) {
        Log.e(LOGTAG, "onServiceConnected() no interface to service, mService==null #################");
        Toast.makeText(mContext, "Failed to access service", Toast.LENGTH_LONG).show();
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      // as a result of unbindService()
      if(Config.LOGD) Log.i(LOGTAG, "onServiceDisconnected()");
      mService = null;
    }
  };

  private boolean findMsgIdInList(long id, LinkedList<EntryTopic> list) {
    int listSize = list.size();
    for(int i=0; i<listSize; i++) {
      if(list.get(i).id==id)
        return true;
    }
    return false;
  }

  // called from 1. onResume() -> onStart()    on activity start + device wake 
  //             2. broadcastReceiver          on live data
  synchronized private int pullUpdateFromServer() {
    int ret=0;
    pullUpdateCounter++;
    //if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer pullUpdateCounter="+pullUpdateCounter);
    if(pullUpdateCounter<=1) {      // to prevent ConcurrentModificationException
      // get latest n messages from service
      if(mService==null) {
        if(Config.LOGD) Log.e(LOGTAG, "pullUpdateFromServer mService==null");
      } else {
        LinkedList<EntryTopic> tmpMessageList = new LinkedList<EntryTopic>();
        // retrieve the list of all messages (from the livecas service) which are newer than timestamp
        List<EntryTopic> list = mService.getMessageListLatestAfterMS(newestMessageMS,maxRequestElements);
        if(list==null) {
          Log.e(LOGTAG, "pullUpdateFromServer abort on list==null");
        } else {
          int listSize = list.size();
          if(listSize<1) {
            if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer abort on list.size="+listSize);
          } else {
            int newItems=0;
            //if(Config.LOGD) Log.i(LOGTAG, String.format("pullUpdateFromServer list.size=%d tmpMessageList.size()=%d",listSize,tmpMessageList.size()));
            // add all new entries to the tail of the tmpMessageList, top of the newlist goes last, for keeping order
            for(int i=listSize-1; i>=0; i--) { // todo: java.util.ConcurrentModificationException
              // only add this entry, if it does not exist in tmpMessageList yet
              EntryTopic newEntryTopic = list.get(i); // todo: java.util.ConcurrentModificationException !!!!!!!!!!!!!!!!!!!!!!!!!1
              //if(!tmpMessageList.contains(newEntryTopic)) { // todo: is this a reliable check?
              if(!findMsgIdInList(newEntryTopic.id,tmpMessageList)) {  // todo: does this work for livecast msgs?
                tmpMessageList.add(newEntryTopic);
                newItems++;
                //if(Config.LOGD) Log.v(LOGTAG, "pullUpdateFromServer title="+newEntryTopic.title+" channel="+newEntryTopic.channelName); //+" descr="+newEntryTopic.description);

                // is this the timestamp of the newest message so far?
                if(newEntryTopic.createTimeMs>newestMessageMS)
                  newestMessageMS = newEntryTopic.createTimeMs;
              } else {
                if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer duplicate entry="+newEntryTopic.title);    // possible to receive this
              }
            }

            // ok, so did we receive any NEW messages?
            int tmpMessageListSize = tmpMessageList.size();
            if(tmpMessageListSize<1) {
              if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer abort on tmpMessageListSize=<1");
            } else if(newItems<1) {
              if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer abort on newItems=<1");
            } else {
              // yes, we got some really new messages
              if(Config.LOGD) Log.i(LOGTAG, "pullUpdateFromServer newItems="+newItems+" tmpMessageList.size()="+tmpMessageList.size());

              while(tmpMessageListSize>messageListMaxSize) {
                tmpMessageList.removeFirst();
                tmpMessageListSize--;
              }

              // calculate the position of the current firstVisibleElement after the new elements are inserted on top

              // update the array from the just modified tmpMessageList
              sendToWebClient(tmpMessageList.toArray(new EntryTopic[0]));
              tmpMessageList.clear();
              ret=tmpMessageListSize;
            }
          }
        }
      }
    }

    --pullUpdateCounter;
    return ret;
  }

  private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { 
    public void onReceive(Context context, Intent intent) {
      // broadcasts are being send by the service (e.g. from TwitterService onStatus())
      final String action = intent.getAction();
      //if(Config.LOGD) Log.i(LOGTAG, "BroadcastReceiver() onReceive() action="+action);
      if(/*!activityPaused &&*/ intentFilter!=null && intentFilter.equals(action)) {
        //if(Config.LOGD) Log.i(LOGTAG, "BroadcastReceiver call pullUpdateFromServer()...");
        pullUpdateFromServer();
      } else {
        if(Config.LOGD) Log.i(LOGTAG, "BroadcastReceiver intentFilter="+intentFilter+" NOT EQUAL action="+action);
      }
    } 
  }; 
}

