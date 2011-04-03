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

import android.util.Log;
import android.util.Config;
import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.graphics.Canvas;

public class CurrentPositionView extends RelativeLayout {
  protected final static String LOGTAG = "CurrentPositionView";
  private TextView leftElementsView;
  private TextView rightElementsView;
  private int lastLeft=-1, lastRight=-1;
  private String appName = null;
  private volatile boolean busy=false;
  private int lastNewer = 0;

  public CurrentPositionView(Context context, String appName, int setMaxElements, int portraitWidth, int portraitHeight) {
    super(context);
    this.appName = appName;

    float fontSize = 14f;
    if(portraitWidth>480f)
      fontSize += (((float)portraitWidth - 480f) / 30f);  // (800-480)/30 = 320/30 = 10.666
    if(Config.LOGD) Log.i(LOGTAG, "fontSize="+fontSize+" portraitHeight="+portraitHeight+" portraitWidth="+portraitWidth);

    RelativeLayout.LayoutParams alignLeft = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    alignLeft.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    leftElementsView = new TextView(context);
    leftElementsView.setTextColor(0xc0ccccff); // HC blue
    leftElementsView.setTextScaleX(2.0f);
    leftElementsView.setTextSize(fontSize);
    addView(leftElementsView,alignLeft);
    leftElementsView.setText("  "+appName);

    RelativeLayout.LayoutParams alignRight = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    alignRight.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    rightElementsView = new TextView(context);
    rightElementsView.setTextColor(0xc0ccccff); // HC blue
    rightElementsView.setTextScaleX(2.0f);
    rightElementsView.setTextSize(fontSize);
    addView(rightElementsView,alignRight);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
  }

  protected void setNewerCount(int newer) {
    if(newer<0) 
      newer = lastNewer;
    else
      lastNewer = newer;

    if(busy) {
      rightElementsView.setTextColor(0xc0ff6666); // red
      leftElementsView.setTextColor(0xc0ff6666); // red
    } 
    else { 
      if(GlTickerView.autoForward) {
        rightElementsView.setTextColor(0xc0aaffaa); // green
        leftElementsView.setTextColor(0xc0aaffaa); // green
      } else {
        rightElementsView.setTextColor(0xc0ccccff); // HC blue
        leftElementsView.setTextColor(0xc0ccccff); // HC blue
      }
    }

    if(newer>0)
      rightElementsView.setText(String.format("%d  ",newer));
    else
      rightElementsView.setText("Latest  ");
  }

  protected void setBusy(boolean busy) {
    this.busy = busy;
    if(busy) {
      rightElementsView.setTextColor(0xc0ff6666); // red
      leftElementsView.setTextColor(0xc0ff6666); // red
    } else {
      if(GlTickerView.autoForward) {
        rightElementsView.setTextColor(0xc0aaffaa); // green
        leftElementsView.setTextColor(0xc0aaffaa); // green
      } else {
        rightElementsView.setTextColor(0xc0ccccff); // HC blue
        leftElementsView.setTextColor(0xc0ccccff); // HC blue
      }
    }
  }

  protected void onDraw(Canvas canvas) {
    leftElementsView.draw(canvas);
    rightElementsView.draw(canvas);
  }
}

