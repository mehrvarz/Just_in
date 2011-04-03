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

public interface ServiceClientInterface
{
  public void init(String serviceClass, String intentFilter, String serviceArg1, String serviceArg2); // on activity onCreate() -> startService() + bindService()

  public boolean isConnected();
  public boolean isConnecting();
  public String getErrMsg();

  public int  start(); // on activity onResume() -> activityPaused=false + pullUpdateFromServer() + registerReceiver(TICKER_NEW_ENTRY_BROADCAST_ACTION)
  // as of now, the serviceClient will directly add stuff to: ((GlActivity)mContext).messageList
  // after messageList has been modified, messageList.notify() will be called (but only between start() and pause())
  // if a block of new items arrive, ((GlActivity)mContext).lastWatchedMsgTimeMs will be used to 
  // 1st add newer msgs to messageList, then add older msgs to messageList
  public void pause(); // on activity onPause() -> activityPaused=true
  
  public void destroy(); // on activity onDestroy() -> unbindService() (service will continue to run), unregisterReceiver(broadcastReceiver)

  public TickerServiceAbstract getServiceObject();
}

