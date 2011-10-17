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
import android.content.Intent;
import android.util.Config;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector; 
import android.view.ScaleGestureDetector; 
import android.os.SystemClock;
import android.app.Activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer; 

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.io.StringWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

class GlTicker2View extends GlTickerView implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener 
{
  protected final static String LOGTAG = "GlTicker2View";

  public GlTicker2View(Context setContext) {
    super(setContext);
    context = setContext; 
    if(Config.LOGD) Log.i(LOGTAG, "constructor(Context)");
  }

  public GlTicker2View(Context setContext, boolean translucent, int depth, int stencil, int width, int height) {
    super(setContext);
    context = setContext; 
    gestureDetector = new GestureDetector(context,this);
    scaleGestureDetector = new ScaleGestureDetector(context,this);
    init(translucent, depth, stencil, width, height);
    if(Config.LOGD) Log.i(LOGTAG, "constructor(Context,boolean,int,int)");
  }

  @Override
  protected void init(boolean translucent, int depth, int stencil, int width, int height) {
    if(Config.LOGD) Log.i(LOGTAG, "init()");
    renderer = null;
    bitmapCount = 0;
    pageZdist = 24f; // the distance of textures on the z-axis - must therefor adjust z-pos of renderer.mRectangleVertData0 in init2()
    defaultCamToTextureDistance = 21.2f; // inspired from webgl implementation

    eyeZDefault = MAX_BITMAPS*pageZdist + defaultCamToTextureDistance;
    targetEyeZ = eyeZ = eyeZDefault;

    int portraitWidth = width;
    int portraitHeight = height;
    if(portraitWidth>portraitHeight) {
      portraitWidth = height;
      portraitHeight = width;
    }

    zNearDefault = 2.8f; // ideal for 480px width
    if(portraitWidth>480) {
      zNearDefault += ((float)(portraitWidth-480)/600);   // (800-480)/600 = 320/600 = 0.5333
    }
    if(Config.LOGD) Log.i(LOGTAG, "init() portraitWidth="+portraitWidth+" portraitHeight="+portraitHeight+" zNearDefault="+zNearDefault);
    zNear = zNearDefault;

    zFarDefault  = 40f*pageZdist;
    zFar  = zFarDefault;

    // By default, GLSurfaceView() creates a RGB_565 opaque surface.
    // If we want a translucent one, we should change the surface's
    // format here, using PixelFormat.TRANSLUCENT for GL Surfaces
    // is interpreted as any 32-bit surface with alpha by SurfaceFlinger.
    if(translucent) {
      this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    // Setup the context factory for 2.0 rendering.
    setEGLContextFactory(new ContextFactory());

    // We need to choose an EGLConfig that matches the format of
    // our surface exactly. This is going to be done in our
    // custom config chooser. See ConfigChooser class definition
    setEGLConfigChooser( translucent ?
                         new EglConfigChooser(8, 8, 8, 8, depth, stencil) :
                         new EglConfigChooser(5, 6, 5, 0, depth, stencil) );
    init2();
  }

  @Override
  protected void init2() {
    if(Config.LOGD) Log.i(LOGTAG, "init2()");
    // load shaders from assets folder and start our GL20-renderer
    try {
      String fragmentShader = getStringFromInputStream(getContext().getAssets().open("fragment.sh"));
      String vertexShader = getStringFromInputStream(getContext().getAssets().open("vertex.sh"));

      // Set the renderer responsible for frame rendering
      if(fragmentShader!=null && vertexShader!=null) {
        renderer = new Renderer2(fragmentShader,vertexShader);

        // since we modified pageZdist in init(), we must also adjust z-positions of default rectangle
        for(int i=0; i<4; i++)
          renderer.mRectangleVertData0[i*5+2] = MAX_BITMAPS*pageZdist;

        //setEGLConfigChooser(false); // todo: ???
        setRenderer(renderer);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
      }
    } catch(java.io.IOException ioex) {
      ioex.printStackTrace();
    }
  }

  int lastNewerCount = -1;

  @Override
  public int currentEntryPosition(boolean display) {
    currentEntryPos = MAX_BITMAPS - (int)((eyeZ-pageZdist/2)/pageZdist);
    if(display && currentPositionView!=null) {
      int showBitmapCount = bitmapCount - renderer.verticesInitNeededCount;
      int newerCount = showBitmapCount - (currentEntryPos+1);
      if(newerCount<0) newerCount=0;
      if(newerCount!=lastNewerCount) {
        //if(Config.LOGD) Log.i(LOGTAG, String.format("currentEntryPosition() currentEntryPos=%d newerCount=%d showBitmapCount=%d bitmapCount=%d verticesInitNeededCount=%d", 
        //                                                                    currentEntryPos,newerCount,showBitmapCount,bitmapCount,renderer.verticesInitNeededCount));
        final int newerCountFinal = newerCount;
        ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
          public void run() { currentPositionView.setNewerCount(newerCountFinal); }
        });
        lastNewerCount = newerCount;
      }
    }

    return currentEntryPos;
  }

  @Override
  public int addBitmap(Bitmap bitmap, EntryTopic entryTopic) {
    // called by WebView.PictureListener() onNewPicture()
    if(renderer==null) {
      Log.e(LOGTAG, "addBitmap renderer==null bitmapCount="+bitmapCount);
      return -1;
    }

    //if(Config.LOGD) Log.i(LOGTAG, "addBitmap bitmapCount="+bitmapCount);
    // here we take entryTopic.createTimeMs and find it's position within entryTopicArray[]
    int insertAfterPosition=0;
    if(bitmapCount>0) {
      while(bitmapCount>insertAfterPosition && entryTopic.createTimeMs > entryTopicArray[insertAfterPosition].createTimeMs)
        insertAfterPosition++;
    }
    //if(Config.LOGD) Log.i(LOGTAG, "addBitmap bitmapCount="+bitmapCount+" insertAfterPosition="+insertAfterPosition);

    int camPos = currentEntryPosition(false); // eyeZ/pageZdist
    dontDraw = true; // prevent onDrawFrame()
    if(bitmapCount>=MAX_BITMAPS) {
      // list is full

      if(insertAfterPosition>0) {  // otherwise: the new entry is older than all existing ones: do nothing
        // delete the outdated bm-file
        String outdatedBmFilename = ""+entryTopicArray[0].createTimeMs+".bm";
        File file = new File(context.getCacheDir(), outdatedBmFilename);
        if(file!=null) {
          //if(Config.LOGD) Log.i(LOGTAG,String.format("addBitmap() DELETING=%s", outdatedBmFilename));
          boolean ret = file.delete();
          //if(Config.LOGD) Log.i(LOGTAG, "addBitmap() delete outdated bm "+outdatedBmFilename+" success="+ret);
        } else {
          Log.e(LOGTAG, "addBitmap() file "+outdatedBmFilename+" cannot be deleted");
        }

        if(camPos<=0) {
          // zoom animate cam one position forward (from pos 0 to 1)
          //if(Config.LOGD) Log.i(LOGTAG, "addBitmap() auto-animate cam from "+currentEntryPosition(false));
          targetEyeZ -= pageZdist;

          dontDraw = false; // allow onDrawFrame() activity
          for(int j=0; j<8; j++) {  // max 8*200ms = 1600ms
            try { Thread.sleep(200); } catch(java.lang.InterruptedException iex) { }
            // note: addBitmap() is called by MyWebView..onNewPicture(), sleeping here delays the call to the next loadUrl()
            if(currentEntryPosition(false)!=camPos) {
              try { Thread.sleep(200); } catch(java.lang.InterruptedException iex) { }
              if(Config.LOGD) Log.i(LOGTAG, "addBitmap() auto-animate cam to "+currentEntryPosition(false)+" waited "+((j+1)*200)+" ms"); //, this="+entryTopic.title);
              break;
            }
          }
          dontDraw = true; // prevent any onDrawFrame() activity
          
          camPos = currentEntryPosition(false); // should now be 1
        }

        // remove the outdated entryTopic from entryTopicArray[] and shift all entries from [1 -> insertAfterPosition-1] to [0 -> insertAfterPosition-2]
        //if(Config.LOGD) Log.i(LOGTAG, "addBitmap shiftBack 0 -> ("+insertAfterPosition+"-1) camPos="+camPos+" autoForward="+autoForward);
        renderer.shiftBack(0,insertAfterPosition-1);    // [i] = [i+1]

        if(camPos<insertAfterPosition && camPos>0) {
          //if(Config.LOGD) Log.i(LOGTAG, "addBitmap() switch camera one back........ camPos="+camPos);
          eyeZ += pageZdist;
          targetEyeZ += pageZdist;
          camPos = currentEntryPosition(false); // should now be 1
        }
      }

      insertAfterPosition--;
    } 
    else //if(bitmapCount<MAX_BITMAPS)
    {
      // list is not full
      if(bitmapCount>0 && insertAfterPosition<bitmapCount) {
        // shift all newer entries (from insertAfterPosition to bitmapCount-1) one forward 

        //if(Config.LOGD) Log.i(LOGTAG, "addBitmap shiftForward "+insertAfterPosition+" -> "+(bitmapCount-1)+" camPos="+camPos);
        renderer.shiftForward(insertAfterPosition,bitmapCount-1);  // shift all textures before the insert position one step forward 

        if(camPos >= insertAfterPosition) { // move the cam forward (towards z=0) as well, if necessary
          //if(Config.LOGD) Log.i(LOGTAG, "addBitmap moving camera one forward........ camPos="+camPos);
          eyeZ -= pageZdist;
          targetEyeZ -= pageZdist;
        }
      }

      bitmapCount++;
    }

    if(insertAfterPosition>=0) {
      // insert bitmap at insertAfterPosition
      synchronized(renderer) {
        if(!renderer.mRectangleVerticesInitNeeded[insertAfterPosition]) {
          renderer.verticesInitNeededCount++; 
          renderer.mRectangleVerticesInitNeeded[insertAfterPosition] = true;
        }
        bitmapArray[insertAfterPosition] = bitmap;
        bitmapHeightArray[insertAfterPosition] = bitmap.getHeight();
        entryTopicArray[insertAfterPosition] = entryTopic;
        //sceneXWidth = (bitmapCount-1) * (msgColumnWidthGL+msgGapWidthGL);
        //if(Config.LOGD) Log.i(LOGTAG, "addBitmap bitmapCount="+bitmapCount+" set InitNeeded on insertAfterPosition="+insertAfterPosition+
        //                              " bitmapArray[insertAfterPosition]="+bitmapArray[insertAfterPosition]+" camPos="+currentEntryPosition());
      }
    }

    dontDraw = false;
    return bitmapCount;
  }

  @Override
  protected void switchPrevPage() {
    //if(Config.LOGD) Log.i(LOGTAG, String.format("switchPrevPage() eyeZ=%f targetEyeZ=%f eyeZDefault=%f ...", eyeZ,targetEyeZ,eyeZDefault));
    if(targetEyeZ<eyeZDefault) {
      int nextIdx = MAX_BITMAPS - (int)((targetEyeZ+pageZdist)/pageZdist);
      //if(Config.LOGD) Log.i(LOGTAG, String.format("switchPrevPage() eyeZ=%f targetEyeZ=%f currentEntryPos=%d nextIdx=%d InitNeeded[nextIdx]=%b", 
      //                                             eyeZ,targetEyeZ,currentEntryPos,nextIdx,renderer.mRectangleVerticesInitNeeded[nextIdx]));
      if(nextIdx>=0 && !renderer.mRectangleVerticesInitNeeded[nextIdx]) {
        targetEyeZ += pageZdist;
        //if(Config.LOGD) Log.i(LOGTAG, String.format("switchPrevPage() eyeZ=%f targetEyeZ=%f eyeZDefault=%f DONE", eyeZ,targetEyeZ,eyeZDefault));
      }
    }
  }

  @Override
  protected void switchNextPage() {
    //if(Config.LOGD) Log.i(LOGTAG, String.format("switchNextPage() eyeZ=%f targetEyeZ=%f eyeZDefault=%f ...", eyeZ,targetEyeZ,eyeZDefault));
    float eyezMin = eyeZDefault-(bitmapCount-1)*pageZdist;
    if(targetEyeZ>eyezMin) {
      int nextIdx = MAX_BITMAPS - (int)((targetEyeZ-pageZdist)/pageZdist);
      //if(Config.LOGD) Log.i(LOGTAG, String.format("switchNextPage() eyeZ=%f targetEyeZ=%f currentEntryPos=%d nextIdx=%d", eyeZ,targetEyeZ,currentEntryPos,nextIdx));
      if(nextIdx<MAX_BITMAPS && !renderer.mRectangleVerticesInitNeeded[nextIdx]) {
        targetEyeZ -= pageZdist;
        //if(Config.LOGD) Log.i(LOGTAG, String.format("switchNextPage() eyeZ=%f targetEyeZ=%f eyeZDefault=%f eyezMin=%f DONE", eyeZ,targetEyeZ,eyeZDefault,eyezMin));
      }
    }
  }

  float beginTargetEyeZ, prevTargetEyeZ, nextTargetEyeZ;

  @Override
  public boolean onScaleBegin(ScaleGestureDetector detector) {
    if(Config.LOGD) Log.i(LOGTAG, "onScaleBegin() getScaleFactor()="+detector.getScaleFactor());

    beginTargetEyeZ = targetEyeZ;
    prevTargetEyeZ = -1f;
    nextTargetEyeZ = -1f;

    if(targetEyeZ<eyeZDefault) {
      int nextIdx = MAX_BITMAPS - (int)((targetEyeZ+pageZdist)/pageZdist);
      //if(Config.LOGD) Log.i(LOGTAG, String.format("switchPrevPage() eyeZ=%f targetEyeZ=%f currentEntryPos=%d nextIdx=%d InitNeeded[nextIdx]=%b", 
      //                                             eyeZ,targetEyeZ,currentEntryPos,nextIdx,renderer.mRectangleVerticesInitNeeded[nextIdx]));
      if(nextIdx>=0 && !renderer.mRectangleVerticesInitNeeded[nextIdx]) {
        prevTargetEyeZ = targetEyeZ + pageZdist;
        //if(Config.LOGD) Log.i(LOGTAG, String.format("switchPrevPage() eyeZ=%f targetEyeZ=%f eyeZDefault=%f DONE", eyeZ,targetEyeZ,eyeZDefault));
      }
    }

    float eyezMin = eyeZDefault-(bitmapCount-1)*pageZdist;
    if(targetEyeZ>eyezMin) {
      int nextIdx = MAX_BITMAPS - (int)((targetEyeZ-pageZdist)/pageZdist);
      //if(Config.LOGD) Log.i(LOGTAG, String.format("switchNextPage() eyeZ=%f targetEyeZ=%f currentEntryPos=%d nextIdx=%d", eyeZ,targetEyeZ,currentEntryPos,nextIdx));
      if(nextIdx<MAX_BITMAPS && !renderer.mRectangleVerticesInitNeeded[nextIdx]) {
        nextTargetEyeZ = targetEyeZ - pageZdist;
      }
    }

    return true;
  }

  @Override
  public boolean onScale(ScaleGestureDetector detector) {
    float scaleFactor = detector.getScaleFactor();
    //if(Config.LOGD) Log.i(LOGTAG, "onScale() getScaleFactor()="+scaleFactor);

    if(scaleFactor<1f) {
      targetEyeZ = beginTargetEyeZ + (1f-scaleFactor)*pageZdist;
    } else
    if(scaleFactor>1f) {
      if(scaleFactor>4f) scaleFactor=4f;
      targetEyeZ = beginTargetEyeZ - scaleFactor*pageZdist/4f;
    } else {
      targetEyeZ = beginTargetEyeZ;
    }

    return false;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector detector) {
    float scaleFactor = detector.getScaleFactor();
    if(Config.LOGD) Log.i(LOGTAG, "onScaleEnd() getScaleFactor()="+scaleFactor);

    if(scaleFactor<0.8f && prevTargetEyeZ>0f) {
      targetEyeZ = prevTargetEyeZ;
    } else
    if(scaleFactor>1.55f && nextTargetEyeZ>0f) {
      targetEyeZ = nextTargetEyeZ;
    } else {
      targetEyeZ = beginTargetEyeZ;
    }
  }

  @Override
  public boolean onFling(MotionEvent motionEvent1, MotionEvent motionEvent2, float gestureVelocityX, float gestureVelocityY) {
    // nothing for now (this is sometimes accidently executed on my nexus one)
    return false;
  }

  @Override
  public boolean onSingleTapUp(MotionEvent motionEvent) {
    // Notified when a tap occurs with the up MotionEvent that triggered it.
    if(Config.LOGD) Log.i(LOGTAG, "onSingleTapUp x="+motionEvent.getX()+" y="+motionEvent.getY());
    if(GlActivityAbstract.showTouchAreaView.isButtonOpen((int)motionEvent.getX(),(int)motionEvent.getY())) {
      targetEyeX = 0;
      targetEyeY = 0;
    }

    return false;
  } 

  @Override
  public boolean onTouchEvent(MotionEvent motionEvent) {
    //if(Config.LOGD) Log.i(LOGTAG, "onTouchEvent motionEvent.getY="+motionEvent.getY());
    boolean ret = false;

    if(gestureDetector!=null) {
      ret = gestureDetector.onTouchEvent(motionEvent);
      //if(!ret)
        if(scaleGestureDetector!=null)
          ret = scaleGestureDetector.onTouchEvent(motionEvent);
    }
    return ret;
  } 

  @Override
  public boolean onDown(MotionEvent motionEvent) {
    // a tap occurs with the down MotionEvent that triggered it.

    onPressEntryTopic = currentlyShownEntry();  // used in onLongPress() -> openCurrentMsgInBrowser(onPressEntryTopic)

    // disable showTouchAreaView
    if(GlActivityAbstract.showTouchAreaView!=null && GlActivityAbstract.showTouchAreaViewActiveSince>0l) {
      //GlActivityAbstract.frameLayout.removeView(GlActivityAbstract.showTouchAreaView);
      GlActivityAbstract.showTouchAreaView.showButtons(false);
      GlActivityAbstract.showTouchAreaViewActiveSince = 0l;
    }

    // isButtonPrev()
    if(GlActivityAbstract.showTouchAreaView.isButtonPrev((int)motionEvent.getX(),(int)motionEvent.getY())) {
      zAnimStep = zAnimStepDefault;
      autoForward = false;
      switchPrevPage();
      GlActivityAbstract.lastCurrentVisibleUpdateMS = SystemClock.uptimeMillis();
      return true;
    }

    // isButtonNext()
    if(GlActivityAbstract.showTouchAreaView.isButtonNext((int)motionEvent.getX(),(int)motionEvent.getY())) {
      zAnimStep = zAnimStepDefault;
      switchNextPage();
      GlActivityAbstract.lastCurrentVisibleUpdateMS = SystemClock.uptimeMillis();
      return true;
    }

    //if(Config.LOGD) Log.i(LOGTAG, "onDown() NO BUTTON motionEvent.getX()="+motionEvent.getX()+" getY()="+motionEvent.getY());
    if(autoForward) {
      autoForward = false;
      if(currentPositionView!=null)
        ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
          public void run() { currentPositionView.setNewerCount(-1); }
        });
    }

    return false;
  }

  @Override
  public void onShowPress(MotionEvent motionEvent) {
    // "The user has performed a down MotionEvent and not performed a move or up yet."
    // mehr als ein kurzer tab, (also ca. ab 0,6 sek wenn man gedrueckt haelt - oder schon nach 0,3 wenn man loslaesst ) 
    // aber noch kein LongPress (der kommt nach ca 1.2 sek)
    // "make a distinction between a possible unintentional touch and an intentional one"

    // disable showTouchAreaView
    if(GlActivityAbstract.showTouchAreaView!=null && GlActivityAbstract.showTouchAreaViewActiveSince>0l) {
      //GlActivityAbstract.frameLayout.removeView(GlActivityAbstract.showTouchAreaView);
      GlActivityAbstract.showTouchAreaView.showButtons(false);
      GlActivityAbstract.showTouchAreaViewActiveSince = 0l;
      return;
    }
  }

  @Override
  public void onLongPress(MotionEvent motionEvent) {
    // isButtonPrev()
    if(GlActivityAbstract.showTouchAreaView.isButtonPrev((int)motionEvent.getX(),(int)motionEvent.getY())) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("onLongPress() motionEvent.getX()=%f getY()=%f y>75%, x<30%",motionEvent.getX(),motionEvent.getY()));
      // go 10 msgs back ...
      targetEyeZ += pageZdist*10;
      if(targetEyeZ>eyeZDefault)
        targetEyeZ=eyeZDefault;
      // ... and deactivate autoForward
      if(autoForward) {
        autoForward = false;
        if(currentPositionView!=null)
          ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
            public void run() { currentPositionView.setNewerCount(-1); }
          });
      }
      return;
    }

    // isButtonNext()
    if(GlActivityAbstract.showTouchAreaView.isButtonNext((int)motionEvent.getX(),(int)motionEvent.getY())) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("onLongPress() motionEvent.getX()=%f getY()=%f y>75%, x>70% - actvate autoForward !!!",motionEvent.getX(),motionEvent.getY()));
      if(!autoForward) {
        autoForward = true;
        if(currentPositionView!=null)
          ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
            public void run() { currentPositionView.setNewerCount(-1); }
          });
      }
      return;
    }

    // isButtonOpen()
    if(GlActivityAbstract.showTouchAreaView.isButtonOpen((int)motionEvent.getX(),(int)motionEvent.getY())) {
      // display a popup menu: browse, send as email, send via SMS, ...
      ((Activity)context).showDialog(1);
    }

    if(autoForward) {
      autoForward = false;
      if(currentPositionView!=null)
        ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
          public void run() { currentPositionView.setNewerCount(-1); }
        });
    }
  }

  @Override
  public boolean onScroll(MotionEvent motionEvent1, MotionEvent motionEvent2, float distanceX, float distanceY) {
    // achtung: onDown wird vorher ausgewertet - nur wenn dort false zurückgegeben wird kommen wir hier hin
    //          deshalb können sich die bereiche überlappen
    if(motionEvent1.getY() < GlActivityAbstract.displayMetrics.heightPixels*0.90f) {
      // onScroll in the top 90% of the screen
      float moveX =  distanceX / 80f;
      float moveY = -distanceY / 80f;  // GL vertical 0 is on the bottom
      targetEyeX += moveX;
      targetEyeY += moveY;
      //if(Config.LOGD) Log.i(LOGTAG, "onScroll() moveX="+moveX+" moveY="+moveY+" targetEyeX="+targetEyeX+" targetEyeY="+targetEyeY+" eyeX="+eyeX+" eyeY="+eyeY);
      return true;
    }
    return false;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event)
  {
    if(autoForward) {
      autoForward = false;
      if(currentPositionView!=null)
        ((GlActivityAbstract)context).runOnUiThread(new Runnable() {
          public void run() { currentPositionView.setNewerCount(-1); }
        });
    }

    long currentMS = SystemClock.uptimeMillis();
    int keyCode = event.getKeyCode();
    switch(keyCode)
    {
//      case KeyEvent.KEYCODE_DPAD_UP:
//      case KeyEvent.KEYCODE_DPAD_LEFT:
//      case 24: // vol up
//        targetEyeZ += 0.01f;
//        if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() KEYCODE_DPAD_LEFT currentMS="+currentMS+" lastKeyEventMS="+lastKeyEventMS+" targetEyeZ="+targetEyeZ);
//        return true; // event was consumed

//      case KeyEvent.KEYCODE_DPAD_DOWN:
//	    case KeyEvent.KEYCODE_DPAD_RIGHT:     // cursor right = page down
//      case 25: // vol down
//        targetEyeZ -= 0.01f; //pageZdist;
//        if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() KEYCODE_DPAD_RIGHT currentMS="+currentMS+" lastKeyEventMS="+lastKeyEventMS+" targetEyeZ="+targetEyeZ);
//        return true; // event was consumed
        
      case KeyEvent.KEYCODE_DPAD_CENTER:
        //openCurrentMsgInBrowser(currentlyShownEntry());
        //dialogCurrentMsg(currentlyShownEntry());
        ((Activity)context).showDialog(1);
        return true; // event was consumed
    }

    if(keyCode==4) { // back key
      if(currentMS - onResumeMS <= 2500l) {
        if(Config.LOGD) Log.i(LOGTAG, String.format("dispatchKeyEvent() ignore back-key quickly %d ms) after resume",currentMS-onResumeMS));
        return true; // event was consumed
      }

      if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() accept back-key");

      // disable showTouchAreaView
      if(GlActivityAbstract.showTouchAreaView!=null && GlActivityAbstract.showTouchAreaViewActiveSince>0l) {
        GlActivityAbstract.showTouchAreaView.showButtons(false);
        GlActivityAbstract.showTouchAreaViewActiveSince = 0l;
        return true;
      }
    } else {
      if(Config.LOGD) Log.i(LOGTAG, String.format("dispatchKeyEvent() unknown keyCode=%d",keyCode));
    }

    return false; 
  }

  protected class Renderer2 extends Renderer implements GLSurfaceView.Renderer {

    public Renderer2(String fragmentShader, String vertexShader) {
      super(fragmentShader, vertexShader);
      if(Config.LOGD) Log.i(LOGTAG, "Renderer2() constructor");
    }

    @Override
    protected void shiftBack(int startIdx, int endIdx) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("Renderer2() shiftBack() startIdx=%d endIdx=%d",startIdx,endIdx));
      int texturesSave = textures[startIdx];
      float[] mRectangleVertDataSave = mRectangleVertData[startIdx];
      boolean mRectangleVerticesInitNeededSave = mRectangleVerticesInitNeeded[startIdx];

      for(int i=startIdx; i<endIdx; i++) {
        textures[i] = textures[i+1];
        mRectangleVertData[i] = mRectangleVertData[i+1];
        mRectangleVertData[i][2]  += pageZdist;  // bottom left z
        mRectangleVertData[i][7]  += pageZdist;  // bottom right z
        mRectangleVertData[i][12] += pageZdist;  // top left z
        mRectangleVertData[i][17] += pageZdist;  // top right z

        mRectangleVertices[i].rewind();
        try {
          mRectangleVertices[i].put(mRectangleVertData[i]);   // todo: java.nio.BufferOverflowException 2011-03-14
          mRectangleVerticesInitNeeded[i] = mRectangleVerticesInitNeeded[i+1];
          entryTopicArray[i] = entryTopicArray[i+1];
          bitmapArray[i] = bitmapArray[i+1];
          bitmapHeightArray[i] = bitmapHeightArray[i+1];
        } catch(Exception ex) { 
          Log.e(LOGTAG, "Renderer2() shiftBack() startIdx="+startIdx+" endIdx="+endIdx+" i="+i+" ex="+ex);
          ex.printStackTrace();
        }
      }

      textures[endIdx] = texturesSave;
      mRectangleVertData[endIdx] = mRectangleVertDataSave;
      mRectangleVerticesInitNeeded[endIdx] = mRectangleVerticesInitNeededSave;
    }

    @Override
    protected void shiftForward(int startIdx, int endIdx) {
      //if(Config.LOGD) Log.i(LOGTAG, String.format("Renderer2() shiftForward() startIdx=%d endIdx=%d",startIdx,endIdx));
      int texturesSave = textures[endIdx+1];
      float[] mRectangleVertDataSave = mRectangleVertData[endIdx+1];
      boolean mRectangleVerticesInitNeededSave = mRectangleVerticesInitNeeded[endIdx+1];

      for(int i=endIdx; i>=startIdx; i--) {
        textures[i+1] = textures[i];
        mRectangleVertData[i+1] = mRectangleVertData[i];

        mRectangleVertData[i+1][2]  -= pageZdist;  // bottom left z
        mRectangleVertData[i+1][7]  -= pageZdist;  // bottom right z
        mRectangleVertData[i+1][12] -= pageZdist;  // top left z
        mRectangleVertData[i+1][17] -= pageZdist;  // top right z

        mRectangleVertices[i+1].rewind();
        try {
          mRectangleVertices[i+1].put(mRectangleVertData[i+1]); // pos = 0
          mRectangleVerticesInitNeeded[i+1] = mRectangleVerticesInitNeeded[i];
          entryTopicArray[i+1] = entryTopicArray[i];
          bitmapArray[i+1] = bitmapArray[i];
          bitmapHeightArray[i+1] = bitmapHeightArray[i];
        } catch(Exception ex) { 
          Log.e(LOGTAG, "Renderer2() shiftForward(startIdx="+startIdx+" endIdx="+endIdx+" i="+i+" ex="+ex);
          ex.printStackTrace();
        }
      }

      textures[startIdx] = texturesSave;
      mRectangleVertData[startIdx] = mRectangleVertDataSave;
      mRectangleVerticesInitNeeded[startIdx] = mRectangleVerticesInitNeededSave;
    }

    @Override
    protected void initBitmap(int i) {
      if(i>=MAX_BITMAPS) {
        Log.e(LOGTAG, "Renderer2() initBitmap i="+i+" >=MAX_BITMAPS");
      } else {
        //if(Config.LOGD) Log.i(LOGTAG, "Renderer2() initBitmap i="+i+" mRectangleVerticesInitNeeded[i]="+mRectangleVerticesInitNeeded[i]+" bitmapArray[i]="+bitmapArray[i]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);

        if(mRectangleVerticesInitNeeded[i]) {
          // start by using the default vertice values
          for(int j=0; j<RECTANGLE_VALUE_COUNT; j++)
            mRectangleVertData[i][j] = mRectangleVertData0[j];

          if(i>0) {
            // set the z-position of the new texture for all four corners
            mRectangleVertData[i][2]  = (MAX_BITMAPS-i)*pageZdist;  // bottom left z
            mRectangleVertData[i][7]  = (MAX_BITMAPS-i)*pageZdist;  // bottom right z
            mRectangleVertData[i][12] = (MAX_BITMAPS-i)*pageZdist;  // top left z
            mRectangleVertData[i][17] = (MAX_BITMAPS-i)*pageZdist;  // top right z
          }

          // set the correct y values on the bottom side (set the texture height)
          int bitmapHeight = bitmapHeightArray[i];
          float textureHeight = Math.min(1f,bitmapHeight/762f);
          mRectangleVertData[i][1]  = -textureHeight;    // bottom left y 
          mRectangleVertData[i][6]  = -textureHeight;    // bottom right y 
          mRectangleVertData[i][11] =  textureHeight;    // top left y  
          mRectangleVertData[i][16] =  textureHeight;    // top right y

          // set the correct x values on the right side (set the texture width)
          int bitmapWidth = GlActivityAbstract.webViewWantedWidth;
          float textureWidth = Math.min(1f,bitmapWidth/480f);
          mRectangleVertData[i][0]  = -textureWidth;    // bottom left x
          mRectangleVertData[i][10] = -textureWidth;    // bottom right x
          mRectangleVertData[i][5]  =  textureWidth;    // top left x
          mRectangleVertData[i][15] =  textureWidth;    // top right x

          //if(Config.LOGD) Log.i(LOGTAG, "Renderer2() initBitmap i="+i+" not initialized textureHeight="+textureHeight+" textureWidth="+textureWidth);
        }

        //if(Config.LOGD) Log.i(LOGTAG, "Renderer2() initBitmap i="+i+" mRectangleVertData[i][10]/[15]="+mRectangleVertData[i][10]+"/"+mRectangleVertData[i][15]);
        mRectangleVertices[i].rewind();
        try {
          mRectangleVertices[i].put(mRectangleVertData[i]); // pos = 0
        } catch(Exception ex) { 
          Log.e(LOGTAG, "initBitmap exception on mRectangleVertices[i].put() i="+i+" ex="+ex);
          ex.printStackTrace();
          if(bitmapArray[i]!=null) {
            bitmapArray[i].recycle();
            bitmapArray[i]=null;
          }
          return;
        }

        mRectangleVertices[i].position(RECTANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, RECTANGLE_VERTICES_DATA_STRIDE_BYTES, mRectangleVertices[i]);

        // note: the writing too a file (further down) can take some time... and while this is happening 
        // a new item might get feed into takeSnapshot() and addBitmap()
        // there, bitmapArray[i] and mRectangleVerticesInitNeeded[i] might get modified
        // this is why we try to become independent of data referenced by [i] ASAP
        Bitmap bitmap = bitmapArray[i];
        String filename = ""+entryTopicArray[i].createTimeMs+".bm";
        synchronized(this) {
          mRectangleVerticesInitNeeded[i] = false; verticesInitNeededCount--;
          bitmapArray[i] = null;
        }
        currentEntryPosition(true);
        // from this point on we are interruptable - and element [i] can be moved and be overwritten

        // type of texture scaling
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        boolean loadedNotGeneratedBitmap = false;

        // load the previously created bitmap
        if(bitmap==null) {
          loadedNotGeneratedBitmap = true;
          if(filename!=null) {
            File file = new File(context.getCacheDir(), filename);
            if(file!=null) {
              try {
                FileInputStream fileInputStream = new FileInputStream(file);
                if(fileInputStream!=null) {
                  bitmap = BitmapFactory.decodeStream(fileInputStream);
                  if(bitmap==null)
                    Log.e(LOGTAG, "initBitmap load filename="+filename+" no bitmap after decodeStream()");
                } else {
                  Log.e(LOGTAG, "initBitmap load filename="+filename+" no fileInputStream");
                }
                //if(Config.LOGD) Log.i(LOGTAG, "initBitmap load filename="+filename+" done");
              } catch(java.io.IOException ioex) {
                Log.e(LOGTAG, "initBitmap ioex load filename="+filename+" failed "+ioex);
                // todo: need alternative for a blank texture
              } catch(java.lang.Exception ex) {
                Log.e(LOGTAG, "initBitmap ex load filename="+filename+" failed "+ex);
                // todo: need alternative for a blank texture
              }
            } else {
              Log.e(LOGTAG, "initBitmap load filename="+filename+" no file");
            }
          } else {
            Log.e(LOGTAG, "initBitmap load bm no filename");
          }
        }

        if(bitmap!=null) {
          GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

          // store the bitmap using a separate thread, with a lower priority
          if(!loadedNotGeneratedBitmap) { // but not if the bitmap was itself just loaded
            // todo: probably better to not start a new thread if an older one is still alive
            (new StoreBitmapThread(context,filename,bitmap)).start();
          } else {
            bitmap.recycle();
          }
        }
      }
    }

    private class StoreBitmapThread extends Thread {
      Context context;
      String filename; 
      Bitmap bitmap;

      StoreBitmapThread(Context context, String filename, Bitmap bitmap) {
        this.context = context;
        this.filename = filename; 
        this.bitmap = bitmap;
      }

      public void run() {
        setPriority(Thread.MIN_PRIORITY);
        try {
          File file = new File(context.getCacheDir(), filename);
          if(file!=null) {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            if(fileOutputStream!=null) {
              bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            } else {
              Log.e(LOGTAG, "StoreBitmapThread store filename="+filename+" failed null");
            }
          } else {
            Log.e(LOGTAG, "StoreBitmapThread filename="+filename+" failed no file");
          }
        }
        catch(java.io.FileNotFoundException fnfex) {
          Log.e(LOGTAG, "StoreBitmapThread filename="+filename+" failed "+fnfex);
        }
        bitmap.recycle();
      }
    }

  }
}

