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
import android.util.DisplayMetrics;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.graphics.Canvas;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RoundRectShape;

public class ShowTouchAreaView extends RelativeLayout {
  private final static String LOGTAG = "ShowTouchAreaView";
  private Context context = null;
  private DisplayMetrics displayMetrics = null;
  private int[] buttonPrev = null, buttonNext = null, buttonOpen = null;
  private TextView textPrev = null, textNext = null, textOpen = null;
  private ShowButtonView showButtonView;
  private volatile boolean buttonsHidden = false;

  private class ShowButtonView extends View {
    private final static String LOGTAG = "ShowButtonView";
    private ShapeDrawable prevDrawable, nextDrawable, openDrawable;

    public ShowButtonView(Context context) {
      super(context);
      openDrawable = new ShapeDrawable(new RoundRectShape(new float[] { 32, 32, 32, 32, 32, 32, 32, 32 },null,null));
      openDrawable.getPaint().setColor(0xf0202040);
      prevDrawable = new ShapeDrawable(new RoundRectShape(new float[] { 32, 32, 32, 32, 32, 32, 32, 32 },null,null));
      prevDrawable.getPaint().setColor(0xf0202040);
      nextDrawable = new ShapeDrawable(new RoundRectShape(new float[] { 32, 32, 32, 32, 32, 32, 32, 32 },null,null));
      nextDrawable.getPaint().setColor(0xf0202040);
    }
    
    protected void setLayout(int widthPixels, int heightPixels) {
      openDrawable.setBounds(buttonOpen[0]+20, buttonOpen[1]+15, buttonOpen[2]-20, buttonOpen[3]-15); 
      prevDrawable.setBounds(buttonPrev[0]+20, buttonPrev[1]+15, buttonPrev[2]-20, buttonPrev[3]-15); 
      nextDrawable.setBounds(buttonNext[0]+20, buttonNext[1]+15, buttonNext[2]-20, buttonNext[3]-15); 
      invalidate();
    }

    protected void onDraw(Canvas canvas) {
      openDrawable.draw(canvas);
      prevDrawable.draw(canvas);
      nextDrawable.draw(canvas);
    }   
  }

  public ShowTouchAreaView(Context context, DisplayMetrics displayMetrics) {
    super(context);
    this.context = context;
    this.displayMetrics = displayMetrics;

    showButtonView = this.new ShowButtonView(context);
    addView(showButtonView);

    textOpen = new TextView(context);
    textOpen.setGravity(0x11); // x/y-center
    textOpen.setText("long press to open");
    textOpen.setTextColor(0xffddddff);
    textOpen.setTextScaleX(1.5f);
    addView(textOpen);

    textPrev = new TextView(context); 
    textPrev.setGravity(0x11); // x/y-center
    textPrev.setText("prev"); // (long press to skip five messages back)");
    textPrev.setTextColor(0xffddddff);
    textPrev.setTextScaleX(1.5f);
    addView(textPrev);

    textNext = new TextView(context);
    textNext.setGravity(0x11); // x/y-center
    textNext.setText("next"); // (long press to activate auto forward mode)");
    textNext.setTextColor(0xffddddff);
    textNext.setTextScaleX(1.5f);
    addView(textNext);
    
    buttonsHidden = false;
  }

  protected void showButtons(boolean showFlag) {
    if(showFlag) {
      if(buttonsHidden) {
        showButtonView.setVisibility(View.VISIBLE);
        textOpen.setVisibility(View.VISIBLE);
        textPrev.setTextColor(0xffddddff);
        textNext.setTextColor(0xffddddff);
        buttonsHidden = false;
      }
    } else {
      if(!buttonsHidden) {
        showButtonView.setVisibility(View.INVISIBLE);
        textOpen.setVisibility(View.INVISIBLE);
        textPrev.setTextColor(0xff333366);
        textNext.setTextColor(0xff333366);
        buttonsHidden = true;
      }
    }
  }

  protected void setLayout(int widthPixels, int heightPixels) {

    // calculate the height of the prev and next buttons - independent of the screen orientation
    int portraitWidth=0, portraitHeight=0;
    if(displayMetrics.widthPixels>displayMetrics.heightPixels) {
      portraitWidth = displayMetrics.heightPixels;
      portraitHeight = displayMetrics.widthPixels;
    } else {
      portraitWidth = displayMetrics.widthPixels;
      portraitHeight = displayMetrics.heightPixels;
    }

    float skipButtonHeightCm = 1.5f + (float)(portraitHeight-800)/480f;     // (854-800)/480 = 54/480 = 0.1125 = 1.6125
    int skipButtonWidth = (int)(portraitWidth*0.33f);
    if(Config.LOGD) Log.i(LOGTAG, "setLayout() skipButtonHeightCm="+skipButtonHeightCm+" portraitHeight="+portraitHeight+" portraitWidth="+portraitWidth+" skipButtonWidth="+skipButtonWidth);

    float skipButtonHeightPercent = (skipButtonHeightCm*displayMetrics.ydpi) / (heightPixels*2.54f);  // (1.6125*96)/(854*2.54) = 154.8/2169.16 = 0.07136403
    int lowerButtonTop = heightPixels - (int)(heightPixels*skipButtonHeightPercent);

    if(Config.LOGD) Log.i(LOGTAG, "setLayout() skipButtonHeightPercent="+skipButtonHeightPercent+" displayMetrics.ydpi="+displayMetrics.ydpi+" heightPixels="+heightPixels+" lowerButtonTop="+lowerButtonTop);
    buttonOpen = new int[] {0, (int)(heightPixels*0.06f), widthPixels, lowerButtonTop};
    buttonPrev = new int[] {0, lowerButtonTop, skipButtonWidth, heightPixels-35};
    buttonNext = new int[] {widthPixels-skipButtonWidth, lowerButtonTop, widthPixels, heightPixels-35};

    float fontSize = 19f + (((float)portraitWidth - 480f) / 15f);  // (800-480)/20 = 320/20 = 10.666

    textOpen.setTextSize(fontSize+2f);
    RelativeLayout.LayoutParams layoutOpen = new RelativeLayout.LayoutParams(buttonOpen[2]-buttonOpen[0], buttonOpen[3]-buttonOpen[1]);
    layoutOpen.leftMargin = buttonOpen[0]-3;
    layoutOpen.topMargin = buttonOpen[1]+1;
    updateViewLayout(textOpen, layoutOpen);

    textPrev.setTextSize(fontSize);
    RelativeLayout.LayoutParams layoutPrev = new RelativeLayout.LayoutParams(buttonPrev[2]-buttonPrev[0], buttonPrev[3]-buttonPrev[1]);
    layoutPrev.leftMargin = buttonPrev[0]-3;
    layoutPrev.topMargin = buttonPrev[1]+1;
    updateViewLayout(textPrev, layoutPrev);

    textNext.setTextSize(fontSize);
    RelativeLayout.LayoutParams layoutNext = new RelativeLayout.LayoutParams(buttonNext[2]-buttonNext[0], buttonNext[3]-buttonNext[1]);
    layoutNext.leftMargin = buttonNext[0]-3;
    layoutNext.topMargin = buttonNext[1]+1;
    updateViewLayout(textNext, layoutNext);

    showButtonView.setLayout(widthPixels,heightPixels);
  }

  public boolean isButtonPrev(int x, int y) {
    if(x>buttonPrev[0] && x<buttonPrev[2] && y>buttonPrev[1] && y<buttonPrev[3]) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("isButtonPrev() x=%d y=%d YES",x,y));
      return true;
    }
    return false;
  }

  public boolean isButtonNext(int x, int y) {
    if(x>buttonNext[0] && x<buttonNext[2] && y>buttonNext[1] && y<buttonNext[3]) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("isButtonNext() x=%d y=%d YES",x,y));
      return true;
    }
    return false;
  }

  public boolean isButtonOpen(int x, int y) {
    if(x>buttonOpen[0] && x<buttonOpen[2] && y>buttonOpen[1] && y<buttonOpen[3]) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("isButtonOpen() x=%d y=%d YES",x,y));
      return true;
    }
    return false;
  }
}

