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
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector; 
import android.os.SystemClock;
import android.os.Process;
import android.net.Uri;
import android.widget.Toast;
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

public class GlTickerView extends GLSurfaceView implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener 
{
  protected final static String LOGTAG = "GlTickerView";

  public volatile static float msgColumnWidthGL = 10f/6f; // =1.66666666f (of 2.0 which is the width of the portrait mode screen)
  public final static float msgGapWidthGL = 0f;           // when modified: touch activity before compile
  public static final int MAX_BITMAPS = 43; // being used in ServiceClient.java

  protected final static float flingOverlap = 0.60f;
//  protected final static float physicsFactor = 800f; // todo: explain this magic value
  protected final static float onFlingMinScreenDistX = 80f; // shorter flings are not accepted
  protected final static float onFlingMinScreenVelocityX = 80f; // slower flings are not accepted
  protected final static float screenToGLVelocityDivider = 10000f; // the smaller this divider value, the faster the fling
//  protected final static float traerDefaultDrag = 19f; //23f;
//  protected volatile static float traerDrag = traerDefaultDrag;
//  protected final static float traerGravity = 0f;
//  protected final static float particleMass = 350f;
//  protected final static float springStrengthDefault = 8f;
//  protected volatile static float springStrength = springStrengthDefault;
//  protected final static float springDampingDefault = 250f; //320f;
//  protected volatile static float springDamping = springDampingDefault;
//  protected final static float springRestLength = 0.01f;
//  protected final static float springActivateSpeed = 0.06f; //0.16f;    // nur wenn abs(currentMoveXDirection) < springActivateSpeed, wird spring aktiviert
  protected static float screenOverlap = msgColumnWidthGL/7;
  protected final static float bgColDefaut = 0f; // 0f = black, 1f = white
  protected static boolean hasFocus = false;

//  // traer physics
//  public static traer.physics.ParticleSystem particleSystem = null;
//  public static traer.physics.Particle cameraParticle = null;
//  public static traer.physics.Particle[] springParticleArray = new traer.physics.Particle[MAX_BITMAPS+1];
//  public static traer.physics.Spring[] springArray = new traer.physics.Spring[MAX_BITMAPS+1];

  protected static Bitmap[] bitmapArray = new Bitmap[MAX_BITMAPS];
  protected static EntryTopic[] entryTopicArray = new EntryTopic[MAX_BITMAPS];
  protected static int[] bitmapHeightArray = new int[MAX_BITMAPS];
  public volatile static int bitmapCount = 0;
  protected volatile static float sceneXStart = 0f;
  protected volatile static float sceneXWidth = 0f;    // cameraMovementWidth

  protected static Renderer renderer = null;
  protected static Context context = null;

  protected volatile static boolean mustForceGlDraw = false; // our way to get around our GL-powersave and for drawing, will call setLookAtM() and increase mMVPMatrixNeedsUpdate
  protected volatile static int mMVPMatrixNeedsUpdate = 0; // no GL rendering if this is <=0, this way we are able to saving power 

  protected static final float projectionFactor  = 4f;  // making the projection matrix in frustumM smaller by this factor, makes all objects within appear bigger
                                                        // so we must move them further away to have them appear in the original size
                                                        // doing this allows us to move the cam into these objects, which results in the desired zoom effect

  protected static float pageZdist = 40f; // the distance of textures on the z-axis

  protected static float defaultCamToTextureDistance = pageZdist/(4f/5f*4f)*3f;  
  //  for pageZdist=200: defaultCamToTextureDistance = pageZdist/16*3f
  //  for pageZdist=100: defaultCamToTextureDistance = pageZdist/8*3f
  //  for pageZdist=50:  defaultCamToTextureDistance = pageZdist/4*3f   
  //  for pageZdist=40:  defaultCamToTextureDistance = pageZdist/(4f/5f*4f)*3f   
  // note: defaultCamToTextureDistance value is overrided by init() in GlTicker2

//  protected static float pageZdist = 24f; // the distance of textures on the z-axis - must therefor adjust z-pos of renderer.mRectangleVertData0 in init2()
//  protected static float defaultCamToTextureDistance = 8f; // inspired from webgl implementation

  protected static float zNearDefault = 5.0f;   // overrided by init() in GlTicker2
  protected static float zNear = zNearDefault;
  protected static float zFarDefault = 6.0f * pageZdist;
  protected static float zFar = zFarDefault;

  protected static float eyeZDefault = MAX_BITMAPS*pageZdist + defaultCamToTextureDistance; // z-position of the 1st texture (this is always the oldest message)

  public volatile static float eyeX = sceneXStart;
  public volatile static float eyeY = 0f;
  public volatile static float eyeZ = eyeZDefault;

  public volatile static float targetEyeX=eyeX, targetEyeY=eyeY, targetEyeZ=eyeZ;
  public volatile static float centerX=0f, centerY=0f, centerZ=0f;
  public volatile static float upX=0f, upY=1f, upZ=0f;
  
  protected volatile static int currentEntryPos = -1;
 
  public volatile static long physicsMovementUpdatedWanted = 0l; // if true: set eyeX using particleSystem.tick() instead of targetEyeX

  protected volatile static boolean physicsMovementAutoStopWanted = true;    
  protected volatile static boolean physicsSpringsWanted = true;    

  protected volatile static float currentMoveXDirection = 0f;
  protected volatile static float lastMoveXDirection = 0f;
  protected volatile static float currentMoveYDirection = 0f;
  protected volatile static float lastMoveYDirection = 0f;
  protected volatile static float currentMoveZDirection = 0f;
  protected volatile static float lastMoveZDirection = 0f;

  protected volatile static float springOnDirection = 0f;
  protected volatile static int   springOnNumber = -1;
  protected volatile static boolean dontDraw = false;

  protected GestureDetector gestureDetector;
  protected ScaleGestureDetector scaleGestureDetector;
  
  protected CurrentPositionView currentPositionView = null;
  protected static volatile int onDrawDelayCountdown = 0;

  protected volatile static boolean initBitmapLoopActive = false;
  protected volatile static long onResumeMS = 0l;

  protected volatile static EntryTopic onPressEntryTopic = null;
  
  public volatile static boolean autoForward = false;

  public static float zAnimStepDefault = 0.115f;
  public static float zAnimStepSlow = 0.060f;
  public static float zAnimStep = zAnimStepDefault;

  // statistics
  protected volatile static int frameCount=0;
  protected volatile static int frameCountDirty=0;
  protected volatile static int frameCountNonPhysic=0;


  public GlTickerView(Context setContext) {
    super(setContext);
    context = setContext; 
    if(Config.LOGD) Log.i(LOGTAG, "constructor() pageZdist="+pageZdist+" defaultCamToTextureDistance="+defaultCamToTextureDistance+" eyeZDefault="+eyeZDefault);
  }

  public GlTickerView(Context setContext, boolean translucent, int depth, int stencil, int width, int height) {
    super(setContext);
    context = setContext; 
    gestureDetector = new GestureDetector(context,this);
    scaleGestureDetector = new ScaleGestureDetector(context,this);
    init(translucent, depth, stencil, width, height);
    if(Config.LOGD) Log.i(LOGTAG, "constructor(Context,boolean,int,int)");
  }

  @Override 
  public void onPause() {
    // just before device sleep
    super.onPause();
    autoForward=false;
    //if(Config.LOGD) Log.i(LOGTAG, String.format("onPause eyeZ=%f targetEyeZ=%f",eyeZ,targetEyeZ));
  }

  @Override 
  public void onResume() {
    super.onResume();
    onResumeMS = SystemClock.uptimeMillis(); // to prevent quick back-key action
    //if(Config.LOGD) Log.i(LOGTAG, String.format("onResume eyeZ=%f targetEyeZ=%f",eyeZ,targetEyeZ));
  }

  public void onStop() {
    //GlTickerJNILib.stop();
    // -> glticker.cpp: stop()

    //if(Config.LOGD) Log.i(LOGTAG, String.format("onStop frameCount=%d eyeZ=%f", frameCount,eyeZ));
  }



  // public methods

  public void onWindowFocusChanged(boolean hasFocus) {
    if(Config.LOGD) Log.i(LOGTAG, "onWindowFocusChanged");
    this.hasFocus = hasFocus;
  }

  public void setCurrentPositionView(CurrentPositionView currentPositionView) {
    this.currentPositionView = currentPositionView;
  }

  public int currentEntryPosition(boolean display) {
    currentEntryPos = (int)(eyeX/(msgColumnWidthGL+msgGapWidthGL)); // global val
    if(currentEntryPos<0) currentEntryPos=0;
    else if(currentEntryPos>=bitmapCount) currentEntryPos=bitmapCount-1;
    return currentEntryPos;
  }

  public EntryTopic currentlyShownEntry() {
    int pos = currentEntryPosition(false);
    if(pos>=0 && pos<bitmapCount)
      return entryTopicArray[currentEntryPosition(false)];
    return null;
  }


  public int addBitmap(Bitmap bitmap, EntryTopic entryTopic) {
/*
    //if(Config.LOGD) Log.i(LOGTAG, "addBitmap bitmapCount="+bitmapCount);
    if(renderer==null) {
      return -1;
    }

    if(bitmapCount>=MAX_BITMAPS) {
      //if(Config.LOGD) Log.e(LOGTAG, "addBitmap CANNOT ACCEPT MORE BITMAPS #################");
      //return -2;
      if(Config.LOGD) Log.e(LOGTAG, "addBitmap bitmapCount="+bitmapCount+" >= MAX_BITMAPS="+MAX_BITMAPS+" #################");

      // shift all bitmaps+textures one down deleting the oldest one
      dontDraw = true;
      if(renderer.shiftBack(0,bitmapCount-1)>=0) {  // todo: actually shiftAllBack() => every texture will jump one UP on the z-axis
        if(Config.LOGD) Log.e(LOGTAG, "addBitmap moving the camera one left........ eyeX="+eyeX);
        // moving the camera one left (except it is in pos 0 already)
        // todo: issues depending on rendering-model
        if(eyeX>=(msgColumnWidthGL+msgGapWidthGL)) {
          eyeX -= (msgColumnWidthGL+msgGapWidthGL);
          targetEyeX -= (msgColumnWidthGL+msgGapWidthGL);
          cameraParticle.position().set(eyeX, cameraParticle.position().y(), cameraParticle.position().z());
        } else {
          //Toast.makeText(context, "removing oldest messages...", Toast.LENGTH_SHORT).show(); 
        }
      }
      dontDraw = false;

      if(Config.LOGD) Log.e(LOGTAG, "addBitmap ALL TEXTURES SHIFTED ONE DOWN, CAM "+eyeX+" SWITCHED ONE LEFT");
    }

    bitmapArray[bitmapCount] = bitmap;
    bitmapHeightArray[bitmapCount] = bitmap.getHeight();
    entryTopicArray[bitmapCount] = entryTopic;

    bitmapCount++;
    sceneXWidth = (bitmapCount-1) * (msgColumnWidthGL+msgGapWidthGL);
    if(Config.LOGD) Log.i(LOGTAG, "addBitmap bitmapCount="+bitmapCount+" sceneXWidth="+sceneXWidth+" frameCount="+frameCount+"/"+frameCountDirty);

    // todo: later we should use a queue (instead of an array) and check here if that queue is full
    //       in which case we would remove bitmaps from the begining
*/
    return bitmapCount;
  }

  // protected methods

  protected static void alterDrag(float alterDrag) {
//    traerDrag = traerDefaultDrag + alterDrag*8;
//    if(particleSystem!=null)
//      particleSystem.setDrag(traerDrag);
//    springStrength = springStrengthDefault + alterDrag*4;
//    springDamping = springDampingDefault + alterDrag*30;
//    //if(Config.LOGD) Log.i(LOGTAG, "alterDrag="+alterDrag+" traerDrag="+traerDrag+" springStrength="+springStrength+" springDamping="+springDamping);
  }

  protected static void stopSpring(String info) {
//    if(springOnNumber>=0) {
//      //if(Config.LOGD) Log.i(LOGTAG, String.format("springArray[%d].turnOff() %s",springOnNumber,info));
//      springArray[springOnNumber].turnOff();
//      springOnDirection = 0f;
//      springOnNumber = -1;
//    }
//    physicsMovementUpdatedWanted = 0l;
//    physicsMovementAutoStopWanted = true;
//    physicsSpringsWanted = true;
  }

  protected void init(boolean translucent, int depth, int stencil, int width, int height) {
    if(Config.LOGD) Log.i(LOGTAG, "init()");
    renderer = null;
    bitmapCount = 0;
    sceneXWidth = 0f;

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
    // todo: to try catch for: IllegalArgumentException("No configs match configSpec")
    setEGLConfigChooser( translucent ?
                         new EglConfigChooser(8, 8, 8, 8, depth, stencil) :
                         new EglConfigChooser(5, 6, 5, 0, depth, stencil) );

    // todo: need to check gl error here
    //checkEglError("After setEGLConfigChooser", egl);

    init2();
    
    // todo: can we start reading x/y-sensors (accelerometer) here, to influence targetEyeX/targetEyeY
  }

  protected void init2() {
    if(Config.LOGD) Log.i(LOGTAG, "init2()");
    // load shaders from assets folder and start our GL20-renderer
    try {
      String fragmentShader = getStringFromInputStream(getContext().getAssets().open("fragment.sh"));
      String vertexShader = getStringFromInputStream(getContext().getAssets().open("vertex.sh"));

      // Set the renderer responsible for frame rendering
      if(fragmentShader!=null && vertexShader!=null) {
        renderer = new Renderer(fragmentShader,vertexShader);
        setRenderer(renderer);
        //setRenderMode(RENDERMODE_WHEN_DIRTY); // doing our own dirty handling
      }
    } catch(java.io.IOException ioex) {
      ioex.printStackTrace();
    }
  }

  protected String getStringFromInputStream(InputStream is) throws IOException {
    if(is != null) {
      Writer writer = new StringWriter();
      char[] buffer = new char[1024];
      try {
        Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        int n;
        while ((n = reader.read(buffer)) != -1)
          writer.write(buffer, 0, n);
      } finally {
        is.close();
      }
      return writer.toString();
    }   
    return null;
  }

  protected static class ContextFactory implements GLSurfaceView.EGLContextFactory {
    private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
      //if(Config.LOGD) Log.i(LOGTAG, "creating OpenGL ES 2.0 context");
      checkEglError("Before eglCreateContext", egl);
      int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
      EGLContext context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
      checkEglError("After eglCreateContext", egl);
      return context;
    }

    public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
      egl.eglDestroyContext(display, context);
    }
  }

  protected static void checkEglError(String prompt, EGL10 egl) {
    int error;
    while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
      Log.e(LOGTAG, String.format("%s: EGL error: 0x%x", prompt, error));
    }
  }

  public static boolean openInBrowser(String link) {
    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
    try {
      context.startActivity(browserIntent);
      return true;
    } catch(android.content.ActivityNotFoundException acrnfex) {
      Log.e(LOGTAG, "openInBrowser() ActivityNotFoundException for link="+link);
      Toast.makeText(context, "no activity found for URI "+link, Toast.LENGTH_SHORT).show(); 
    }
    return false;
  }

  public boolean openCurrentMsgInBrowser(EntryTopic entryTopic) {
    // open 'current entry' in browser
    //EntryTopic entryTopic = currentlyShownEntry(); // -> currentEntryPosition() will set currentEntryPos
    if(Config.LOGD) Log.i(LOGTAG, "openCurrentMsgInBrowser() entryTopic="+entryTopic);
    if(entryTopic!=null) {
      String currentLink = entryTopic.link;
      if(Config.LOGD) Log.i(LOGTAG, "openCurrentMsgInBrowser() link="+currentLink);
      if(currentLink!=null) {
        openInBrowser(currentLink);
      } else {
        Toast.makeText(context, "no link", Toast.LENGTH_SHORT).show(); 
      }
    }
    return false;
  }

//  protected boolean dialogCurrentMsg(/*final EntryTopic entryTopic*/) {
//    //GlActivityAbstract.currentEntryTopic = entryTopic;
//    ((Activity)context).showDialog(1);
//    return false;
//  }



  /////////////////////////////////////////////////////////////////////////////////////////////////////// user interface

  protected volatile boolean longPressForMessageScrollerActive = false;
  protected volatile float longPressForMessageScrollerStartX = 0f;
  protected volatile float longPressMoveLastPos = -1f;
  protected volatile float longPressXposTargetGlPageAligned = -1f;
  protected long lastKeyEventMS=-1l;

  public boolean dispatchKeyEvent(KeyEvent event)
  {
    long currentMS = SystemClock.uptimeMillis();
    int keyCode = event.getKeyCode();
    switch(keyCode)
    {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_LEFT:
        if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() KEYCODE_DPAD_LEFT currentMS="+currentMS+" lastKeyEventMS="+lastKeyEventMS);
        targetEyeZ += 0.1;    // debug: super fine zoom the z-axis (todo: use +=pageZdist)
        return true; // event was consumed

      case KeyEvent.KEYCODE_DPAD_DOWN:
	    case KeyEvent.KEYCODE_DPAD_RIGHT:     // cursor right = page down
        if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() KEYCODE_DPAD_RIGHT currentMS="+currentMS+" lastKeyEventMS="+lastKeyEventMS);
        targetEyeZ -= 0.1; // debug: super fine zoom the z-axis (todo: use -=pageZdist)
        return true; // event was consumed

      case KeyEvent.KEYCODE_DPAD_CENTER:
        openCurrentMsgInBrowser(currentlyShownEntry());
        return true; // event was consumed
    }

    if(keyCode==4) { // back key
      if(currentMS - onResumeMS <= 1500l) {
        if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() ignore back-key quickly after resume");
        return true; // event was consumed
      }
      if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() accept back-key");
    }

    if(Config.LOGD) Log.i(LOGTAG, "dispatchKeyEvent() unknown keyCode="+keyCode);
    return false; 
  }


  //float motionLastX=-1f, motionLastY=-1f;

  @Override
  public boolean onTouchEvent(MotionEvent motionEvent) {
//    if(Config.LOGD) Log.i(LOGTAG, "onTouchEvent longPressForMessageScrollerActive="+longPressForMessageScrollerActive+" motionEvent.getY="+motionEvent.getY()+" GlActivityAbstract.displayMetrics.heightPixels="+GlActivityAbstract.displayMetrics.heightPixels);

//    if(motionEvent.getAction()==MotionEvent.ACTION_UP) {
//      longPressForMessageScrollerActive = false;
//      physicsSpringsWanted = true;
//      physicsMovementAutoStopWanted = true;
//      stopSpring(null); //"onTouchEvent ACTION_MOVE bottom 20% activate");
//      //motionLastX=-1f; motionLastY=-1f;
//    } 
//    else
//    if(motionEvent.getAction()==MotionEvent.ACTION_MOVE) {
//      if(motionEvent.getY() > GlActivityAbstract.displayMetrics.heightPixels*0.80f) {
//        // in the bottom 15% of the screen
//        //motionLastX=-1f; motionLastY=-1f;
//        if(!longPressForMessageScrollerActive) {
//          targetEyeX = eyeX;
//          stopSpring(null /*"onTouchEvent ACTION_MOVE bottom 20% activate"*/);
//          longPressMoveLastPos = -1f;
//          longPressXposTargetGlPageAligned = -1f;
//          longPressForMessageScrollerStartX = motionEvent.getX();
//          if(cameraParticle!=null) {
//            cameraParticle.position().set(eyeX, 0f, 0f);
//            physicsMovementUpdatedWanted = SystemClock.uptimeMillis();
//            particleSystem.tick();
//            physicsMovementAutoStopWanted = false;
//            physicsSpringsWanted = false;
//            longPressForMessageScrollerActive = true;
//          }
//          //if(Config.LOGD) Log.i(LOGTAG, "onTouchEvent ACTION_MOVE bottom 20%: activate MessageScroller (hit "+(motionEvent.getY() - GlActivityAbstract.displayMetrics.heightPixels*0.80f)+" from bottom)");
//          return true;
//        } 

//        float posX = motionEvent.getX();
//        float diffX = posX - longPressForMessageScrollerStartX;
//        float maxScale = GlActivityAbstract.displayMetrics.widthPixels/4; // we use 1/2 of the screen width for the negative scale and 1/2 for the positive scale
//        float scaleStep = maxScale/7; // within 1/4 of the screen with we establish 4 steps
//        float step = (int)(Math.abs(diffX)/scaleStep);
//        if(step>0) {      // finger has moved x-wise some amount away from it's 1st contact point
//          float phyMove;
//          if(diffX>0) {                // finger is moving right
//            phyMove = step*-0.003f;    // move camera to the left
//          }
//          else {                       // finger is moving left
//            phyMove = step* 0.003f;    // move camera to the right
//          }

//          if(phyMove!=0f) {
//            if(Math.abs(cameraParticle.velocity().x()+phyMove)<0.18f) {
//              cameraParticle.velocity().add(phyMove, 0f, 0f);
//              //if(Config.LOGD) Log.i(LOGTAG, "onTouchEvent ACTION_MOVE bottom 20%: press step="+step+" diffX="+diffX+" phyMove="+phyMove+" "+cameraParticle.velocity().x());
//              // todo: must be careful not to jump outside of field
//            }
//          }
//        }
//        return true;
//      } else {
//        //if(Config.LOGD) Log.i(LOGTAG, "onTouchEvent ACTION_MOVE top 80%: not handled...");
//        if(motionEvent.getY() < GlActivityAbstract.displayMetrics.heightPixels*0.80f) {
//          // onTouchEvent in the top 80% of the screen
//        }
//      }
//    }

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
  public boolean onScroll(MotionEvent motionEvent1, MotionEvent motionEvent2, float distanceX, float distanceY) {
    if(motionEvent1.getY() < GlActivityAbstract.displayMetrics.heightPixels*0.80f) {
      // onScroll in the top 80% of the screen
//    if(cameraParticle!=null /* && eyeZ<1.1f*/) {
        //if(eyeX<sceneXStart || eyeX>sceneXWidth)
        //  distanceX /=2;
        float moveX =  distanceX / 800f;
        float moveY = -distanceY / 800f;  // GL vertical 0 is on the bottom
        targetEyeX += moveX;
        if(Math.abs(moveX)>=1.5f && Math.abs(moveX)>Math.abs(moveY)) // if this move is mostly horizontal...
          moveY/=5f; // ...then minimize the effect of the vertical change
        //if(GlActivityAbstract.displayMetrics.widthPixels > GlActivityAbstract.displayMetrics.heightPixels) // vertical scrolling only in landscape mode
          targetEyeY += moveY;
        stopSpring(null); // "onScroll");
        physicsSpringsWanted = false;

        if(Config.LOGD) Log.i(LOGTAG, "onScroll() moveX="+moveX+" moveY="+moveY+" targetEyeX="+targetEyeX+" targetEyeY="+targetEyeY+" eyeX="+eyeX+" eyeY="+eyeY);

//        if(Config.LOGD) Log.i(LOGTAG, "onScroll() targetEyeX="+targetEyeX+" from eyeX="+eyeX+
//                                      " (move distanceX="+distanceX+" "+(distanceX/(physicsFactor/eyeZ))+")"+
//                                      " distanceY="+distanceY+
//                                      " frameCount="+frameCount+" "+frameCountNonPhysic);

//    }
    }
    return false;
  }

  @Override
  public boolean onFling(MotionEvent motionEvent1, MotionEvent motionEvent2, float gestureVelocityX, float gestureVelocityY) {
    if(motionEvent1.getY() < GlActivityAbstract.displayMetrics.heightPixels*0.80f) {
      // this is a fling in the top 80% ofthe screen

      // velocity = pixel/s in screen coordinates
      //If gestureVelocityX < 0: fling in left direction  (stuff on the right comes into view - we move to the right)
      //If gestureVelocityX > 0: fling in right direction  (stuff on the left comes into view - we move to the left)
      //If velocityY > 0: fling down
      //If velocityY < 0: fling up
      final float gestureDistX = Math.abs(motionEvent2.getX()-motionEvent1.getX());

      if(Math.abs(gestureVelocityX)>onFlingMinScreenVelocityX && gestureDistX>onFlingMinScreenDistX) {
        targetEyeX = eyeX;
        stopSpring(null); // "onFling");

        //float cameraVelocityX = -gestureVelocityX / screenToGLVelocityDivider;
        //float cameraVelocityX = -gestureVelocityX / (screenToGLVelocityDivider/((eyeZ+2f)/3f));    // 1 = /1, 5 = 7/3
        float cameraVelocityX = (-gestureVelocityX / screenToGLVelocityDivider) * (float)Math.sqrt(Math.sqrt(eyeZ));

        if(Config.LOGD) Log.i(LOGTAG, "onFling() cameraVelocityX="+cameraVelocityX+" gestureVelocityX="+gestureVelocityX+" eyeZ="+eyeZ+" screenToGLVelocityDivider="+screenToGLVelocityDivider+" eyeX="+eyeX);

        float idealCameraVelocityX = cameraVelocityX; 

//        float estimatedTargetEyeX = (cameraVelocityX / traerDrag) * (physicsFactor/eyeZ) + eyeX;
//        if(estimatedTargetEyeX>=0f && estimatedTargetEyeX<=sceneXWidth) {
//          if(eyeZ<1.1f) {
//            float minDistX, maxDistX, minCameraVelocityX, maxCameraVelocityX;
//            if(cameraVelocityX<0f) {
//              minDistX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL);
//              maxDistX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL) -flingOverlap;
//              minCameraVelocityX = (minDistX * traerDrag) / physicsFactor;
//              maxCameraVelocityX = (maxDistX * traerDrag) / physicsFactor;
//              if(idealCameraVelocityX < maxCameraVelocityX)
//                idealCameraVelocityX = maxCameraVelocityX;
//              if(idealCameraVelocityX > minCameraVelocityX)
//                idealCameraVelocityX = minCameraVelocityX;
//            } else {
//              minDistX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL) +(msgColumnWidthGL+msgGapWidthGL);
//              maxDistX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL) +(msgColumnWidthGL+msgGapWidthGL) +flingOverlap ;
//              minCameraVelocityX = (minDistX * traerDrag) / physicsFactor;
//              maxCameraVelocityX = (maxDistX * traerDrag) / physicsFactor;
//              if(idealCameraVelocityX > maxCameraVelocityX)
//                idealCameraVelocityX = maxCameraVelocityX;
//              if(idealCameraVelocityX < minCameraVelocityX)
//                idealCameraVelocityX = minCameraVelocityX;
//            }

//            //if(Config.LOGD) Log.i(LOGTAG, "onFling() idealCameraVelocityX="+idealCameraVelocityX+" maxDistX="+maxDistX+" maxCameraVelocityX="+maxCameraVelocityX+" estimatedTargetEyeX="+estimatedTargetEyeX);
//          }
//        }


//        if(cameraParticle!=null) {
//          cameraParticle.position().set(eyeX, 0f, 0f);
//          cameraParticle.velocity().set(idealCameraVelocityX, 0f, 0f);
//          physicsMovementUpdatedWanted = SystemClock.uptimeMillis();
//          particleSystem.tick();                                // java.lang.NullPointerException ???
//        }
      }
    }
    return false;
  }

  @Override
  public void onShowPress(MotionEvent motionEvent) {
    // "The user has performed a down MotionEvent and not performed a move or up yet."
    // mehr als ein kurzer tab, (also ca. ab 0,6 sek wenn man gedrueckt haelt - oder schon nach 0,3 wenn man loslaesst ) 
    // aber noch kein LongPress (der kommt nach ca 1.2 sek)
    // "make a distinction between a possible unintentional touch and an intentional one"

    if(Config.LOGD) Log.i(LOGTAG, "onShowPress() "+motionEvent.getX()+" "+motionEvent.getY());
    physicsMovementUpdatedWanted = 0l; //targetEyeX = eyeX; // stop all movement
  }

  @Override
  public void onLongPress(MotionEvent motionEvent) {
//    if(Config.LOGD) Log.i(LOGTAG, "onLongPress() x="+motionEvent.getX()+" y="+motionEvent.getY()+" GlActivityAbstract.displayMetrics.heightPixels="+GlActivityAbstract.displayMetrics.heightPixels);
//    physicsMovementUpdatedWanted = 0l; targetEyeX = eyeX; // stop all movement
//    if(cameraParticle!=null) {
//      if(motionEvent.getY() > GlActivityAbstract.displayMetrics.heightPixels*0.80f) {
//        // long press in the bottom 20% of screen
//      } else {
//        // longpress in the top 80% of screen: hyperlink to url of the visible msg
//        openCurrentMsgInBrowser(onPressEntryTopic);
//      }
//    }
  }

  @Override
  public boolean onDown(MotionEvent motionEvent) {
//    // a tap occurs with the down MotionEvent that triggered it.
//    if(Config.LOGD) Log.i(LOGTAG, "onDown() eyeX="+eyeX+" sceneXWidth="+sceneXWidth);
//    onPressEntryTopic = currentlyShownEntry();
//    physicsMovementUpdatedWanted = 0l; //targetEyeX = eyeX; // stop all movement
//    longPressForMessageScrollerActive = false;  // ???

//    if(cameraParticle!=null /*&& eyeZ<1.1f*/) {
//      if(motionEvent.getY() > GlActivityAbstract.displayMetrics.heightPixels*0.70f) {
//        if(motionEvent.getX() < GlActivityAbstract.displayMetrics.widthPixels*0.30f) {
//          /*if(eyeX>=0f)*/ {
//            //if(Config.LOGD) Log.i(LOGTAG, "onDown() bottom 25% / left 25%: switchPrevPage eyeZ="+eyeZ);
//            switchPrevPage();
//            return true;
//          }
//        } else if(motionEvent.getX() > GlActivityAbstract.displayMetrics.widthPixels*0.75f) {
//          /*if(eyeX<=sceneXWidth)*/ {
//            //if(Config.LOGD) Log.i(LOGTAG, "onDown() bottom 25% / right 25%: switchNextPage eyeZ="+eyeZ);
//            switchNextPage();
//            return true;
//          }
//        }
//      }
//    }

    return false;
  }

  @Override
  public boolean onSingleTapUp(MotionEvent motionEvent) {
    // Notified when a tap occurs with the up MotionEvent that triggered it.
    if(Config.LOGD) Log.i(LOGTAG, "onSingleTapUp() x="+motionEvent.getX()+" y="+motionEvent.getY()); //+" downtime="+motionEvent.getDownTime());
    physicsMovementUpdatedWanted = 0l; targetEyeX = eyeX; // stop all movement
    longPressForMessageScrollerActive = false;  // ???

    return false;
  } 

  protected void switchPrevPage() {
//    targetEyeX = eyeX;
//    stopSpring(null); // "onSingleTapUp");

//    float smallerOverlap = flingOverlap*0.20f; // use less overlap for non-fling page switch
//    float distX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL);
//    while(distX>-msgColumnWidthGL/2)
//      distX -= (msgColumnWidthGL+msgGapWidthGL);
//    distX -= smallerOverlap;
//    float idealCameraVelocityX = (distX * traerDrag) / physicsFactor;
//    //if(Config.LOGD) Log.i(LOGTAG, "switchPrevPage() idealCameraVelocityX="+idealCameraVelocityX+" distX="+distX+" eyeX="+eyeX);
//    cameraParticle.position().set(eyeX, 0f, 0f);
//    cameraParticle.velocity().set(idealCameraVelocityX, 0f, 0f);
//    physicsMovementUpdatedWanted = SystemClock.uptimeMillis();
//    particleSystem.tick();
  }

  protected void switchNextPage() {
//    targetEyeX = eyeX;
//    stopSpring(null); // "onSingleTapUp");

//    float smallerOverlap = flingOverlap*0.20f; // use less overlap for non-fling page switch
//    float distX = -(eyeX)%(msgColumnWidthGL+msgGapWidthGL);
//    while(distX<msgColumnWidthGL/2)
//      distX += (msgColumnWidthGL+msgGapWidthGL);
//    distX += smallerOverlap;
//    float idealCameraVelocityX = (distX * traerDrag) / physicsFactor;
//    //if(Config.LOGD) Log.i(LOGTAG, "switchNextPage() idealCameraVelocityX="+idealCameraVelocityX+" distX="+distX+" eyeX="+eyeX);
//    cameraParticle.position().set(eyeX, 0f, 0f);
//    cameraParticle.velocity().set(idealCameraVelocityX, 0f, 0f);
//    physicsMovementUpdatedWanted = SystemClock.uptimeMillis();
//    particleSystem.tick();
  }


  @Override
  public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
    // "Notified when a single-tap occurs."
    // "this tap is 100% not part of a double tap gesture"
    //if(Config.LOGD) Log.i(LOGTAG, "onSingleTapConfirmed() x="+motionEvent.getX()+" y="+motionEvent.getY()); //+" downtime="+motionEvent.getDownTime());
    return false;   // consumed or not
  }



  // doubleTap support

  @Override
  public boolean onDoubleTap(MotionEvent motionEvent) {
    //if(Config.LOGD) Log.i(LOGTAG, "onDoubleTap() x="+motionEvent.getX()+" y="+motionEvent.getY()); //+" downtime="+motionEvent.getDownTime());
    return false;   // consumed or not
  }

  @Override
  public boolean onDoubleTapEvent(MotionEvent motionEvent) {
    // "Notified when an event within a double-tap gesture occurs, including the down, move, and up events."
    //if(Config.LOGD) Log.i(LOGTAG, "onDoubleTapEvent()");
    return false;   // consumed or not
  }

  // multitouch zooming support
 
  @Override
  public boolean onScaleBegin(ScaleGestureDetector detector) {
    if(Config.LOGD) Log.i(LOGTAG, "onScaleBegin() getScaleFactor()="+detector.getScaleFactor());
    return false;
  }

  @Override
  public boolean onScale(ScaleGestureDetector detector) {
    float scaleFactor = detector.getScaleFactor();
    float oldTargetEyeZ = targetEyeZ;
    float newTargetEyeZ = 0f;
    if(scaleFactor!=0f) {
      newTargetEyeZ = oldTargetEyeZ / scaleFactor; //Math.sqrt(scaleFactor); slower!
      if(newTargetEyeZ<1f) newTargetEyeZ=1f;
      else if(newTargetEyeZ>5f) newTargetEyeZ=5f;

      if(newTargetEyeZ!=oldTargetEyeZ) {
        targetEyeZ = newTargetEyeZ;
        mustForceGlDraw = true;       // todo: should not be necessary

        alterDrag(1f-newTargetEyeZ);
      }
    }
    //if(Config.LOGD) Log.i(LOGTAG, "onScale() getScaleFactor()="+detector.getScaleFactor()+" new targetEyeZ="+targetEyeZ+" old targetEyeZ="+oldTargetEyeZ+" mustForceGlDraw="+mustForceGlDraw);
    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector detector) {
    if(Config.LOGD) Log.i(LOGTAG, "onScaleEnd() getScaleFactor()="+detector.getScaleFactor());
  }



  ////////////////////////////////////////////////////////////////////////////////////////////////// GL Renderer
  protected class Renderer implements GLSurfaceView.Renderer {
    protected String fragmentShader, vertexShader;

    protected static final int RECTANGLE_VERTICES_DATA_ELEMENTS = 5; // 5 floats per coordinate = 3D + U+V
    protected static final int FLOAT_SIZE_BYTES = 4;
    protected static final int RECTANGLE_VERTICES_DATA_STRIDE_BYTES = RECTANGLE_VERTICES_DATA_ELEMENTS * FLOAT_SIZE_BYTES;
    protected static final int RECTANGLE_VERTICES_DATA_POS_OFFSET = 0;
    protected static final int RECTANGLE_VERTICES_DATA_UV_OFFSET = 3;
    protected static final int RECTANGLE_VERTICES = 4;
    protected static final int RECTANGLE_VALUE_COUNT = RECTANGLE_VERTICES * RECTANGLE_VERTICES_DATA_ELEMENTS; // 4 vertices * 5 values per coordinate

    // position in space of the 1st texture (all ther textures will be positioned to the right of it)
    protected float[] mRectangleVertData0 = { 
       // X,  Y,  Z,    U,     V      (UV mapping is the way we map bitmap-pixels to vertices, a process of making a 2D image representation of a 3D model)
        -1f, -1f, MAX_BITMAPS*pageZdist,   0.0f,  1.0f,    // bottom left  (0-4)
         1f, -1f, MAX_BITMAPS*pageZdist,   1.0f,  1.0f,    // bottom right (5-9)
        -1f,  1f, MAX_BITMAPS*pageZdist,   0.0f,  0.0f,    // top left     (10-14)
         1f,  1f, MAX_BITMAPS*pageZdist,   1.0f,  0.0f,    // top right    (15-19)
    };
/*
     0  bottom left x     0
     1  bottom left y     0
     2  bottom left z     0
     3  bottom left u     0
     4  bottom left v     1
     5  bottom right x    2
     6  bottom right y    0
     7  bottom right z    0
     8  bottom right u    1
     9  bottom right v    1
    10  top left x        0
    11  top left y        2
    12  top left z        0
    13  top left u        0
    14  top left v        0
    15  top right x       2
    16  top right y       2
    17  top right z       0
    18  top right u       1
    19  top right v       0

*/

    protected float[][] mRectangleVertData = null;
    protected FloatBuffer[] mRectangleVertices = null;    // values set in initBitmap()
    protected int[] textures = new int[MAX_BITMAPS];
    protected boolean  texturesInitialized = false;
    protected boolean[] mRectangleVerticesInitNeeded = null;
    protected volatile int verticesInitNeededCount=0;

    protected int gvPositionHandle;
    protected int maTextureCoordHandle;
//    protected int moptDistHandle;
    protected int muMVPMatrixHandle;
    protected float[] mVMatrix = new float[16];
    protected float[] mMVPMatrix = new float[16];
    protected float[] mProjMatrix = new float[16];
    protected float[] mMMatrix = new float[16];
    protected float bgColor = bgColDefaut;   
    protected float bgColorAddVal = 0.05f;
    protected float rotateAngle=-1f, lastRotateAngle=-1f;

    public Renderer(String fragmentShader, String vertexShader) {
      if(Config.LOGD) Log.i(LOGTAG, "Renderer");
      this.fragmentShader = fragmentShader;
      this.vertexShader = vertexShader;
      //particleSystem  = null;

      bitmapCount=0;
      // todo: other things that need reset?

      if(mRectangleVertData==null) {
        // allocate gl-buffers only on the very first call to onSurfaceCreated()
        mRectangleVertData = new float[MAX_BITMAPS][RECTANGLE_VALUE_COUNT];
        mRectangleVertices = new FloatBuffer[MAX_BITMAPS];
        mRectangleVerticesInitNeeded = new boolean[MAX_BITMAPS];
        for(int i=0; i<MAX_BITMAPS; i++) {
          mRectangleVertices[i] = ByteBuffer.allocateDirect(RECTANGLE_VALUE_COUNT*FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
          // RECTANGLE_VALUE_COUNT*FLOAT_SIZE_BYTES = 4*5*4 = 80 bytes
        }
      }
    }

// todo: MUST FIX
    protected void shiftBack(int startIdx, int endIdx) {
/*
      if(bitmapCount>0) {
        int texturesSave = textures[0];
        float[] mRectangleVertDataSave = mRectangleVertData[0];

        GLES20.glDeleteTextures(1, textures, 0);

        for(int i=startIdx; i<endIdx; i++) {
          textures[i] = textures[i+1];
          mRectangleVertData[i] = mRectangleVertData[i+1];
          mRectangleVertData[i][0]  -= (msgColumnWidthGL+msgGapWidthGL);  // bottom left x
          mRectangleVertData[i][5]  -= (msgColumnWidthGL+msgGapWidthGL);  // bottom right x
          mRectangleVertData[i][10] -= (msgColumnWidthGL+msgGapWidthGL);  // top left x
          mRectangleVertData[i][15] -= (msgColumnWidthGL+msgGapWidthGL);  // top right x

          mRectangleVertices[i].rewind();
          mRectangleVertices[i].put(mRectangleVertData[i]).position(RECTANGLE_VERTICES_DATA_POS_OFFSET);   // pos = 0

          entryTopicArray[i] = entryTopicArray[i+1];
          bitmapArray[i] = bitmapArray[i+1];
          bitmapHeightArray[i] = bitmapHeightArray[i+1];
        }

        bitmapCount--;
        //lastBitmapCount--;

        textures[bitmapCount] = texturesSave;
        mRectangleVertData[bitmapCount] = mRectangleVertDataSave;

        return bitmapCount;
      }
*/
    }

    protected void shiftForward(int startIdx, int endIdx) {
      for(int i=endIdx; i>startIdx+1; i--) {
/*
        textures[i+1] = textures[i];
        mRectangleVertData[i+1] = mRectangleVertData[i];

        mRectangleVertData[i+1][2]  -= pageZdist;  // bottom left z
        mRectangleVertData[i+1][7]  -= pageZdist;  // bottom right z
        mRectangleVertData[i+1][12] -= pageZdist;  // top left z
        mRectangleVertData[i+1][17] -= pageZdist;  // top right z

        mRectangleVertices[i+1].rewind();
        mRectangleVertices[i+1].put(mRectangleVertData[i+1]).position(RECTANGLE_VERTICES_DATA_POS_OFFSET);   // pos = 0

        entryTopicArray[i+1] = entryTopicArray[i];
        bitmapArray[i+1] = bitmapArray[i];
        bitmapHeightArray[i+1] = bitmapHeightArray[i];
*/
      }
    }

    protected void initBitmap(int i) {
//      if(i>=MAX_BITMAPS) {
//        if(Config.LOGD) Log.e(LOGTAG, "Renderer initBitmap i>=MAX_BITMAPS i="+i+" #################");
//      } else {
//        if(Config.LOGD) Log.i(LOGTAG, "Renderer initBitmap i="+i+" mRectangleVerticesInitNeeded[i]="+mRectangleVerticesInitNeeded[i]);

//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);

//        GLES20.glEnable(GLES20.GL_BLEND);   // todo: not needed?
//        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);  // todo: not needed?
//        //GLES20.glColor4(255, 255, 255, 30);

//        if(mRectangleVerticesInitNeeded[i]) {
//          // start by using the default values
//          for(int j=0; j<RECTANGLE_VALUE_COUNT; j++)
//            mRectangleVertData[i][j] = mRectangleVertData0[j];

//          if(i>0) {
//            // position the new texture a full page to the right of the previous texture
//            mRectangleVertData[i][0]  = mRectangleVertData[i-1][0]  + msgColumnWidthGL+msgGapWidthGL;  // bottom left x
//            mRectangleVertData[i][5]  = mRectangleVertData[i-1][5]  + msgColumnWidthGL+msgGapWidthGL;  // bottom right x
//            mRectangleVertData[i][10] = mRectangleVertData[i-1][10] + msgColumnWidthGL+msgGapWidthGL;  // top left x
//            mRectangleVertData[i][15] = mRectangleVertData[i-1][15] + msgColumnWidthGL+msgGapWidthGL;  // top right x
//          }

//          // set the correct y values on the bottom side (set the texture height)
//          int bitmapHeight = bitmapHeightArray[i];
//          float textureHeight = 2f * Math.min(1f,bitmapHeight/762f);
//          mRectangleVertData[i][1] = mRectangleVertData[i][11] - textureHeight;    // bottom left y  = top left y - height
//          mRectangleVertData[i][6] = mRectangleVertData[i][16] - textureHeight;    // bottom right y = top right y - height

//          // set the correct x values on the right side (set the texture width)
//          int bitmapWidth = GlActivityAbstract.webViewWantedWidth;
//          float textureWidth = 2f * Math.min(1f,bitmapWidth/480f);
//          mRectangleVertData[i][5]  = mRectangleVertData[i][0] + textureWidth;      // bottom right x
//          mRectangleVertData[i][15] = mRectangleVertData[i][10] + textureWidth;    // top right x

//          // move textures down a tiny bit (taller textures less though), so that it does not look too straight
//          float shiftDown = Math.max(0.20f-textureHeight/10f,0f);
//          mRectangleVertData[i][1]  -= shiftDown;  // bottom left y
//          mRectangleVertData[i][6]  -= shiftDown;  // bottom right y
//          mRectangleVertData[i][11] -= shiftDown;  // top left y
//          mRectangleVertData[i][16] -= shiftDown;  // top right y

//          mRectangleVerticesInitNeeded[i] = false;
//        }

//        //if(Config.LOGD) Log.i(LOGTAG, "initBitmap i="+i+" ########## bitmapHeight="+bitmapHeight+" textureHeight="+textureHeight+
//        //                               " bottom leftY="+mRectangleVertData[i][1]+" bottom rightY="+mRectangleVertData[i][6]);

//        mRectangleVertices[i].rewind();
//        mRectangleVertices[i].put(mRectangleVertData[i]); //.position(RECTANGLE_VERTICES_DATA_POS_OFFSET);   // pos = 0
//        // todo: java.nio.BufferOverflowException if mRectangleVertices[i].position() > 0
//        // mRectangleVertices[i] should have RECTANGLE_VALUE_COUNT*FLOAT_SIZE_BYTES = 4*5*4 = 80 bytes as 20 floats
//        // remaining() = the number of elements between the current position and the limit

//        mRectangleVertices[i].position(RECTANGLE_VERTICES_DATA_UV_OFFSET);
//        GLES20.glVertexAttribPointer(maTextureCoordHandle, 2, GLES20.GL_FLOAT, false, RECTANGLE_VERTICES_DATA_STRIDE_BYTES, mRectangleVertices[i]); 

//        // crisp or blury scaling
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//        Bitmap bitmap = bitmapArray[i];
//        if(bitmap!=null)
//        {
//          //GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
//          // calls glTexImage2D() on the current OpenGL context
//          //GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmapArray[i].getWidth(), bitmapArray[i].getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
//          GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.getWidth(), bitmap.getHeight(), 0, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, extract(bitmap)); 

//          bitmap.recycle();
//          bitmapArray[i]=null;
//        }
//      }
    }

    private ByteBuffer extract(Bitmap bmp)
    {
      ByteBuffer bb = ByteBuffer.allocateDirect(bmp.getHeight() * bmp.getWidth() * 4);
      bb.order(ByteOrder.BIG_ENDIAN);
      IntBuffer ib = bb.asIntBuffer();
      // Convert ARGB -> RGBA
      for(int y = bmp.getHeight() - 1; y > -1; y--)
      {
        for(int x = 0; x < bmp.getWidth(); x++)
        {
          int pix = bmp.getPixel(x, bmp.getHeight() - y - 1);
          int alpha = ((pix >> 24) & 0xFF);
          int red = ((pix >> 16) & 0xFF);
          int green = ((pix >> 8) & 0xFF);
          int blue = ((pix) & 0xFF);

          ib.put(red << 24 | green << 16 | blue << 8 | alpha);
        }
      }
      bb.position(0);
      return bb;
    } 

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
      if(Config.LOGD) Log.i(LOGTAG, String.format("onSurfaceCreated eyeZ=%f targetEyeZ=%f",eyeZ,targetEyeZ));
      int mProgram = createProgram(vertexShader, fragmentShader);
      if(mProgram<=0) {
        throw new RuntimeException("Could not create gles program");
      }

      GLES20.glUseProgram(mProgram);
      checkGlError("glUseProgram");

      gvPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");       // to vertex.sh
      checkGlError("glGetAttribLocation vPosition");
      //Log.i(LOGTAG, String.format("gvPositionHandle=%d", gvPositionHandle));
      if(gvPositionHandle == -1) {
        throw new RuntimeException("Could not get attrib location for vPosition");
      }

      maTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");    // to vertex.sh
      checkGlError("glGetAttribLocation aTextureCoord");
      //Log.i(LOGTAG, String.format("maTextureCoordHandle=%d", maTextureCoordHandle));
      if(maTextureCoordHandle == -1) {
        throw new RuntimeException("Could not get attrib location for aTextureCoord");
      }
/*
      moptDistHandle = GLES20.glGetAttribLocation(mProgram, "optDist");    // to vertex.sh
      checkGlError("glGetAttribLocation optDist");
      if(moptDistHandle == -1) {
        throw new RuntimeException("Could not get attrib location for optDist");
      }
*/
      muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
      checkGlError("glGetUniformLocation uMVPMatrix");
      //Log.i(LOGTAG, String.format("muMVPMatrixHandle=%d", muMVPMatrixHandle));
      if(muMVPMatrixHandle == -1) {
        throw new RuntimeException("Could not get attrib location for uMVPMatrix");
      }

//      // traer physics
//      if(particleSystem==null) {
//        particleSystem = new traer.physics.ParticleSystem(traerGravity, traerDrag);

//        cameraParticle = particleSystem.makeParticle( particleMass, 0f, 0f, 0f);
//        cameraParticle.makeFree();

//        // todo: we only need one spring - and when using only one, we will position it as needed
//        for(int i=0; i<=MAX_BITMAPS; i++) {
//          springParticleArray[i] = particleSystem.makeParticle( 0f, i*(msgColumnWidthGL+msgGapWidthGL), 0f, 0f);
//          springParticleArray[i].makeFixed();
//          springArray[i] = particleSystem.makeSpring(cameraParticle, springParticleArray[i], springStrength, springDamping, springRestLength);
//          springArray[i].turnOff();
//        }
//      }

      // Create textures-array on every call to onSurfaceCreated()  ??? apparently NOT!
      if(!texturesInitialized) {  // wow, this seems to help a lot!
        GLES20.glGenTextures(MAX_BITMAPS, textures, 0);
        texturesInitialized=true;
      }

      for(int i=0; i<MAX_BITMAPS; i++)
        mRectangleVerticesInitNeeded[i] = false;

      //      // By default, OpenGL enables features that improve quality but reduce
      //      // performance.
      //      //GLES20.glDisable(GLES20.GL_LIGHTING);   // GLES20.GL_LIGHTING not defined
      //      GLES20.glDisable(GLES20.GL_DITHER);
      GLES20.glDisable(GLES20.GL_DITHER);
      //GLES20.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
      //      //GLES20.glHint(GLES20.GL_PERSPECTIVE_CORRECTION_HINT, GLES20.GL_FASTEST);
      //      //GLES20.glHint(0, GLES20.GL_FASTEST);
      //      //GLES20.glTexEnvx(GLES20.GL_TEXTURE_ENV, GLES20.GL_TEXTURE_ENV_MODE, GLES20.GL_MODULATE);

      //GLES20.glShadeModel(GLES20.GL_SMOOTH);

      GLES20.glEnable(GLES20.GL_DEPTH_TEST);
      //GLES20.glDisable(GLES20.GL_DEPTH_TEST);

      GLES20.glEnable(GLES20.GL_CULL_FACE);
      GLES20.glCullFace(GLES20.GL_BACK);

      physicsMovementUpdatedWanted = 0l;
      physicsMovementAutoStopWanted = true;
      frameCount = 0;
      frameCountDirty = 0;
      frameCountNonPhysic = 0;

      if(bitmapCount>0) {
        // all GL context is lost, so we want to reload textures from disk
        // but in order to get a quick _visual_ switch to our activity, we set onDrawDelayCountdown
        // to delay calls to initBitmap() (-> loading of textures in our onDrawFrame method) for roughly 1s = 30 frames
        // this allows androids own activity-switch-animation to play out
        onDrawDelayCountdown=18;
        verticesInitNeededCount=0;
        for(int i=0; i<bitmapCount; i++) {
          mRectangleVerticesInitNeeded[i]=true;
          verticesInitNeededCount++;
        }
      }

      //mustForceGlDraw=true;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
      //GlTickerJNILib.init(width, height, vertexShader, fragmentShader);
      // -> glticker.cpp: setupGraphics(width, height, vertexShader, fragmentShader)

      if(mustForceGlDraw) {
        Log.i(LOGTAG, "onSurfaceChanged deny double call");
        return;
      }

      if(Config.LOGD) Log.i(LOGTAG, String.format("onSurfaceChanged width=%d height=%d webViewWantedWidth=%d msgColumnWidthGL=%f eyeZ=%f",
                                                  width,height,GlActivityAbstract.webViewWantedWidth,msgColumnWidthGL,eyeZ));
      // galaxy s portrait:  width=480 height=762 webViewWantedWidth=400
      // galaxy s landscape: width=800 height=442 webViewWantedWidth=400

      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);   // render thread priority raised

      // glViewport specifies the affine transformation of x and y from normalized device coordinates to window coordinates.
      // describes the actual flat representation of the 3D objects. If we were using a camera we would call this the photograph.
      GLES20.glViewport(0, 0, width, height);

      //float ratio = (float)width/(float)height;
      //gl.glOrthof(0, width, height, 0, 1f, 50f); //for 0,0 at top-right
      // glScalef(1f, -1f, 1f);

      // Define a projection matrix in terms of six clip planes
      // make textures keep their aspect ratio in landscape view
      // screen coordinates w=480 * h=762 (nexus1) is our default assumption 
      // this will result in GL coordinates to always be: right=0.0->2.0, bottom=2.0->0.0
      float projectionLeft   = -width/480f/2f  / projectionFactor;
      float projectionRight  =  width/480f/2f  / projectionFactor;
      float projectionTop    =  height/762f/2f / projectionFactor;
      float projectionBottom = -height/762f/2f / projectionFactor;
      if(Config.LOGD) Log.i(LOGTAG, String.format("onSurfaceChanged frustumM left=%f right=%f bottom=%f top=%f pageZdist=%f",
                                                   projectionLeft,projectionRight,projectionBottom,projectionTop,pageZdist));
      if(width>height) { // landscape mode
        //float landscapeZoomFactor = (1f + ((width-800)/24f)/100f);
        float landscapeZoomFactor = (1f + ((width-800)/8f)/100f);
        zNear = zNearDefault*landscapeZoomFactor;
        if(Config.LOGD) Log.i(LOGTAG, String.format("onSurfaceChanged landscapeZoomFactor=%f zNear=%f zNearDefault=%f",landscapeZoomFactor,zNear,zNearDefault));
      } else {
        zNear = zNearDefault; // portrait
        if(Config.LOGD) Log.i(LOGTAG, String.format("onSurfaceChanged portrait zNear=%f ",zNear));
      }

      Matrix.frustumM(mProjMatrix, 0, // offset
                      projectionLeft, projectionRight, projectionBottom, projectionTop,
                      zNear, zFar);   // near, far, relative to eyeZ of camera specified by setLookAtM()

      // for eyeZmin = 1.0f, frustum goes from (eyeZ - nearFrustum =) 0.0f to (eyeZ-farFrustum =) -5.0f
      // for eyeZmax = 5.0f, frustum goes from (eyeZ - nearFrustum =) 4.0f to (eyeZ-farFrustum =) -1.0f
      // textures have the best readability if positioned at (eyeZ-nearFrustum), for eyeZmin this is 0.0

      onDrawFrameMs = lastOnDrawFrameMs = 0l;
      mustForceGlDraw = true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDrawFrame(GL10 gl) {
      //GlTickerJNILib.step(); // -> glticker.cpp: renderFrame()

      if(dontDraw)
        return;

      if(onDrawDelayCountdown>0) {
        // postpone rendering of new bitmaps for a couple of frames
        onDrawDelayCountdown--;
      }

      if(onDrawDelayCountdown==0) {
        if(eyeZ==targetEyeZ /*&& eyeX==targetEyeX && eyeY==targetEyeY*/) {
          // only if there are no animations taking place right now
          if(!initBitmapLoopActive) {
            initBitmapLoopActive = true;
            // inside this block files may be written, which may take a little
            // initBitmapLoopActive prevent this block to be called again before this run is finished
            int countInits=0;
            for(int i=0; i<bitmapCount; i++)
              if(mRectangleVerticesInitNeeded[i]) {
                //if(Config.LOGD) Log.i(LOGTAG, "onDrawFrame() initBitmap("+i+")");
                initBitmap(i);
                countInits++;
              }
            if(countInits>0)
              mustForceGlDraw = true;
            initBitmapLoopActive = false;
          }
        }
      }

      onDrawFrame2(gl);
      frameCount++;
    }

    long lastOnDrawFrameMs=0l;
    long onDrawFrameMs=0l;
    long drawTicks=0;

    public void onDrawFrame2(GL10 gl) {
      lastOnDrawFrameMs = onDrawFrameMs;
      onDrawFrameMs = SystemClock.uptimeMillis();
      long msSinceLastOnDrawFrame = onDrawFrameMs-lastOnDrawFrameMs;
      if(lastOnDrawFrameMs==0l) {
        drawTicks=1l;
        if(Config.LOGD) Log.i(LOGTAG, "onDrawFrame2 first call after onSurfaceChanged, mustForceGlDraw="+mustForceGlDraw+" hasFocus="+hasFocus);
      }
      else {
        drawTicks = msSinceLastOnDrawFrame/32l; // assuming 30 fps here
      }

      if(drawTicks>1l) {
        //if(Config.LOGD) Log.i(LOGTAG, String.format("onDrawFrame2 msSinceLastOnDrawFrame=%d drawTicks=%d",msSinceLastOnDrawFrame,drawTicks));
      }

      if(drawTicks<1l)
        drawTicks=1l;

      // handle bgColor
      /*
      bgColor += bgColorAddVal;
      mustForceGlDraw = true;
      if(bgColor > 1.0f) {
        bgColor = 1.0f;
        bgColorAddVal = -bgColorAddVal;
      } else if(bgColor < 0.0f) {
        bgColor = 0.0f;
        bgColorAddVal = -bgColorAddVal;
      }
      */

      float lastEyeX = eyeX;
      lastMoveXDirection = currentMoveXDirection;
      if(physicsMovementUpdatedWanted>0l) {
//        // physics movement
//        particleSystem.tick();
//        targetEyeX = eyeX = cameraParticle.position().x();
      } 
      else
      if(Math.abs(targetEyeX-eyeX)>0.002) {
        eyeX += (targetEyeX-eyeX)*0.015f;
        //if(Config.LOGD) Log.i(LOGTAG,"onDrawFrame2 animate eyeX="+eyeX);
        frameCountNonPhysic++;
      } 
      else 
      if(targetEyeX!=eyeX) {
        eyeX=targetEyeX;
        //targetEyeX = 0f;
        //targetEyeY = 0f;
      }
      currentMoveXDirection = eyeX - lastEyeX;


      float lastEyeY = eyeY;
      if(Math.abs(targetEyeY-eyeY)>0.002) {
        eyeY += (targetEyeY-eyeY)*0.015f;
        //if(Config.LOGD) Log.i(LOGTAG,"onDrawFrame2 animate eyeY="+eyeY);
        mustForceGlDraw = true;
      } else 
      if(targetEyeY!=eyeY) {
        eyeY=targetEyeY;
        //targetEyeX = 0f;
        //targetEyeY = 0f;
      }

      lastMoveYDirection = currentMoveYDirection;
      currentMoveYDirection = eyeY - lastEyeY;




/* */
      float lastEyeZ = eyeZ;
      if(Math.abs(targetEyeZ-eyeZ)>0.001) {
        float move;
        if(targetEyeZ>eyeZ)
          move = (targetEyeZ-eyeZ)*zAnimStep*drawTicks;    // next: going away from the camera
        else
          move = (targetEyeZ-eyeZ)*zAnimStep*drawTicks;    // prev: coming closer towards the camera

        //if(Config.LOGD) Log.i(LOGTAG,"onDrawFrame2() eyeZ="+eyeZ+" targetEyeZ="+targetEyeZ+" move="+move+")");
        if(move>2.5f) 
          move=2.5f;
        else
        if(move<-5f) 
          move=-5f;
        eyeZ += move;

        currentEntryPosition(true);   // todo: too costly?
        //currentEntryPos = MAX_BITMAPS - (int)(eyeZ/pageZdist);

        mustForceGlDraw = true;
      } else
      if(eyeZ!=targetEyeZ) {
        eyeZ=targetEyeZ;
        //System.gc(); // after the animation has finished may be a good time to get some garbage out
      }

      lastMoveZDirection = currentMoveZDirection;
      currentMoveZDirection = eyeZ - lastEyeZ;
/* */


/*
      // traer physics springs

      if(physicsSpringsWanted && springOnNumber<0 && currentMoveXDirection<0)     // springs wanted, no springs active, camera moving left
        for(int i=0; i<=bitmapCount-1; i++)
          if(eyeX<-screenOverlap || 
              (eyeZ<1.05f 
               && eyeX<=springParticleArray[i].position().x()
               && eyeX>springParticleArray[i].position().x()-msgColumnWidthGL/4
               && Math.abs(currentMoveXDirection)<springActivateSpeed     // speed has fallen below trigger (this actually prevents the start spring to pull the camera back too easly)
              ) 
            ) 
          {
//            long msSincePhyStart = SystemClock.uptimeMillis()-physicsMovementUpdatedWanted;
//            if(physicsMovementUpdatedWanted>0l && msSincePhyStart<10l) {
//              if(Config.LOGD) Log.i(LOGTAG,"camera move left IGNORE SPRING ACTIVATION ON EARLY physicsMovementUpdatedWanted "+msSincePhyStart);
//              break;
//            }
            // activate spring when moving to the left (if i=0: going outside left of field, or else if slow enough to stop)
            springArray[i].setStrength(springStrength);
            springArray[i].setDamping(springDamping);
            springArray[i].turnOn();
            springOnDirection = currentMoveXDirection;
            springOnNumber = i;
            //if(Config.LOGD) Log.i(LOGTAG, String.format("camera move left springArray[%d].turnOn() pos=%f currentMoveXDirection=%f lastMoveXDirection=%f eyeX=%f lastEyeX=%f %d ms",
            //                         i,springParticleArray[i].position().x(),currentMoveXDirection,lastMoveXDirection,eyeX,lastEyeX,SystemClock.uptimeMillis()-physicsMovementUpdatedWanted));
            break;
          }

      if(physicsSpringsWanted && springOnNumber<0 && currentMoveXDirection>0)     // springs wanted, no springs active, camera moving right
        for(int i=bitmapCount-1; i>=0; i--)
          if(eyeX>sceneXWidth+screenOverlap || 
              (eyeZ<1.05f 
               && eyeX>=springParticleArray[i].position().x()
               && eyeX<springParticleArray[i].position().x()+msgColumnWidthGL/4 
               && Math.abs(currentMoveXDirection)<springActivateSpeed 
              )
            ) 
          {
//            long msSincePhyStart = SystemClock.uptimeMillis()-physicsMovementUpdatedWanted;
//            if(physicsMovementUpdatedWanted>0l && msSincePhyStart<10l) {
//              if(Config.LOGD) Log.i(LOGTAG,"camera move right IGNORE SPRING ACTIVATION ON EARLY physicsMovementUpdatedWanted "+msSincePhyStart);
//              break;
//            }
            // activate spring when moving to the right (if i=bitmapCount: going outside right of field, or else if slow enough to stop)
            springArray[i].setStrength(springStrength);
            springArray[i].setDamping(springDamping);
            springArray[i].turnOn();
            springOnDirection = currentMoveXDirection;
            springOnNumber = i;
            //if(Config.LOGD) Log.i(LOGTAG, String.format("camera move right springArray[%d].turnOn() pos=%f currentMoveXDirection=%f lastMoveXDirection=%f eyeX=%f lastEyeX=%f %d ms",
            //                         i,springParticleArray[i].position().x(),currentMoveXDirection,lastMoveXDirection,eyeX,lastEyeX,SystemClock.uptimeMillis()-physicsMovementUpdatedWanted));
            break;
          }

      if(springOnNumber>=0 && currentMoveXDirection==0f && lastMoveXDirection!=0f) {
        // a spring is active, but camera movement just came to a halt, so we stop it
        //if(Config.LOGD) Log.i(LOGTAG,"a spring is active, but camera movement just came to a halt, so we stop it");
        stopSpring(null);
        //targetEyeX = eyeX;    // todo ???
      }

      if(springOnNumber>=0 && springOnDirection>0f && currentMoveXDirection<0f) { // turned around (right to left) on spring bounce
        // a spring is active for the camera moving to the right, but the camera is now moving to the left
        try {
          if(eyeX<=springParticleArray[springOnNumber].position().x())
          {
            // the camera reached the active spring after rebounce, so we stop the spring
            if(Config.LOGD) Log.i(LOGTAG,"the camera reached the active spring after rebounce from right, so we stop the spring");  // todo: when this comes it doesn't look good
            targetEyeX = eyeX = springParticleArray[springOnNumber].position().x();
            stopSpring(null);
          }
        }
        catch(java.lang.ArrayIndexOutOfBoundsException aioobex) {
          Log.e(LOGTAG,"ArrayIndexOutOfBoundsException +"+aioobex);
        }
      }

      if(springOnNumber>=0 && springOnDirection<0f && currentMoveXDirection>0f) { // turned around (left to right) on spring bounce
        // a spring is active for the camera moving to the left, but the camera is now moving to the right
        try {
          if(eyeX>=springParticleArray[springOnNumber].position().x())
          {
            // the camera reached the active spring after rebounce, so we stop the spring
            if(Config.LOGD) Log.i(LOGTAG,"the camera reached the active spring after rebounce from left, so we stop the spring");  // todo: when this comes it doesn't look good
            targetEyeX = eyeX = springParticleArray[springOnNumber].position().x();
            stopSpring(null);
          }
        }
        catch(java.lang.ArrayIndexOutOfBoundsException aioobex) {
          Log.e(LOGTAG,"ArrayIndexOutOfBoundsException +"+aioobex);
        }
      }
*/


      


          // sandbox / safty-belt
          boolean eyeXFixed=false;
          float maxX =  2.4f;
          float minX = -2.4f;
          float maxY =  2.0f;
          float minY = -2.0f;

          if(eyeX > maxX) {
            //if(Config.LOGD) Log.i(LOGTAG,"kindergarden eyeX="+eyeX+" too much to the right (>"+maxX+")");
            eyeX = maxX;
            targetEyeX = maxX/2;
            eyeXFixed=true;
          }
          if(eyeX < minX) {
            //if(Config.LOGD) Log.i(LOGTAG,"kindergarden eyeX="+eyeX+" too much to the left (<"+minX+")");
            eyeX = minX;
            targetEyeX = minX/2;
            eyeXFixed=true;
          }
          // this should not be needed... but safety
          if(Float.isNaN(eyeX)) {
            targetEyeX = eyeX = 1f;
            eyeXFixed=true;
            if(Config.LOGD) Log.e(LOGTAG,"kindergarden eyeX NaN fixed");
          }

          boolean eyeYFixed=false;
          if(eyeY > maxY) {
            //if(Config.LOGD) Log.i(LOGTAG,"kindergarden eyeY="+eyeY+" too low");
            //targetEyeY = 
              eyeY = maxY;
            targetEyeY = maxY/2;
            eyeYFixed=true;
          }
          if(eyeY < minY) {
            //if(Config.LOGD) Log.i(LOGTAG,"kindergarden eyeY="+eyeY+" too high");
            //targetEyeY = 
              eyeY = minY;
            targetEyeY = minY/2;
            eyeYFixed=true;
          }

      if(mustForceGlDraw
      || eyeXFixed || eyeYFixed                         // value was outside of the sandbox
      || Math.abs(currentMoveXDirection)>0.00001        // still moving along x
      || Math.abs(currentMoveYDirection)>0.00001        // still moving along x
      || Math.abs(currentMoveZDirection)>0.00001        // still moving along z
      ) {
        Matrix.setLookAtM(mVMatrix, 0,
                          eyeX, eyeY, eyeZ,
                        //centerX, centerY, eyeZ-pageZdist*MAX_BITMAPS, //centerZ,
                          centerX-eyeX*16, centerY-eyeY*16, eyeZ-pageZdist*MAX_BITMAPS, //centerZ,
                          upX, upY, upZ);
        mMVPMatrixNeedsUpdate = 16;
        if(mustForceGlDraw) {
          mustForceGlDraw = false;
          //if(Config.LOGD) Log.i(LOGTAG,"onDrawFrame2() mustForceGlDraw=false, mMVPMatrixNeedsUpdate="+mMVPMatrixNeedsUpdate);
        }
      } 
      else
      // X and Z movements have come to a halt (practically)
      if(currentMoveXDirection!=0f) {
        currentMoveXDirection=0;

        //if(Config.LOGD) Log.i(LOGTAG,"physicsMovement came to a halt at eyeZ="+eyeZ+" currentEntryPosition()="+currentEntryPosition(false));

        if(physicsMovementUpdatedWanted>0l && physicsMovementAutoStopWanted) {
          targetEyeX = eyeX;
          physicsMovementUpdatedWanted=0l;
          stopSpring(null);

          if(eyeZ<1.05f) {
            float rest = eyeX%(msgColumnWidthGL+msgGapWidthGL);
            if(Math.abs(rest)>0.00001) {
/*
              // snap to screen... by settng targetEyeX
              if(rest < (msgColumnWidthGL+msgGapWidthGL)/2f) {
                targetEyeX = eyeX - rest;
              } else {
                targetEyeX = eyeX - rest + (msgColumnWidthGL+msgGapWidthGL);
              }
              if(Config.LOGD) Log.i(LOGTAG, "rest="+rest+" must snap to screen, set targetEyeX="+targetEyeX+" eyeX="+eyeX);
*/
            }
          }
        }
/*
        if(eyeX>0f && eyeX<sceneXWidth) {          // ????
          physicsMovementUpdatedWanted=0l;
          targetEyeX = eyeX;
        }
*/
      }


      // handle rotation
      //float speed= 0.20f;
      //long time = SystemClock.uptimeMillis() % (long)(360f/speed);
      //rotateAngle = speed * (int)time;
      rotateAngle = 0f;
      if(rotateAngle!=lastRotateAngle) {
        // evenif we are not rotating stuff, it is essntial to call setRotateM() once 
        Matrix.setRotateM(mMMatrix, 0, // int rmOffset
                         rotateAngle, // float angle
                         0.0f, 0.0f, 1.0f); // float x, float y, float z
        lastRotateAngle=rotateAngle;
        mMVPMatrixNeedsUpdate = 16;
      }

      // be good to the device: if nothing is supposed to change on screen, we should skip rendering any GL
if(lastOnDrawFrameMs==0l) {
  if(Config.LOGD) Log.i(LOGTAG, "onDrawFrame2 first call after onSurfaceChanged --- hasFocus="+hasFocus+" mMVPMatrixNeedsUpdate="+mMVPMatrixNeedsUpdate);
}
      if(/*(lastOnDrawFrameMs==0l || hasFocus) &&*/ mMVPMatrixNeedsUpdate>0) {
        mMVPMatrixNeedsUpdate--;
        if(mMVPMatrixNeedsUpdate==0) {
          //if(Config.LOGD) Log.i(LOGTAG, String.format("onDrawFrame2() mMVPMatrixNeedsUpdate=0 autoForward=%b",autoForward));
        }

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT); checkGlError("glClear"); // takes time: 11% of onDrawFrame()
        GLES20.glClearColor(bgColor, bgColor, bgColor, 1.0f); checkGlError("glClearColor");

        Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);       // camera / lookAt
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0);  // frustumM
        // "mMVPMatrix" (cam/frustum) (4x4 floating point matrix) will be send to the vertex shader as "uniform mat4 uMVPMatrix"
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        if(bitmapCount>0) {
          // draw all objects with textures
          GLES20.glEnableVertexAttribArray(gvPositionHandle); checkGlError("glEnableVertexAttribArray");    // accessible as 'vPosition' in shader
          GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

          //GLES20.glEnable(GLES20.GL_ALPHA_TEST);
          //GLES20.glAlphaFunc(GLES20.GL_EQUAL,1.0f );
          
          for(int i=bitmapCount-1; i>=0; i--) {  // very important to draw the elements on the z-axis upwards
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);

            GLES20.glEnable(GLES20.GL_BLEND); 
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            // x,y,z-values of "mRectangleVertices[i]" will be send to the vertext shader as "attribute vec4 vPosition"
            mRectangleVertices[i].position(RECTANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(gvPositionHandle, 3, GLES20.GL_FLOAT, false, RECTANGLE_VERTICES_DATA_STRIDE_BYTES, mRectangleVertices[i]); 
            //checkGlError("glVertexAttribPointer maPosition");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RECTANGLE_VERTICES); // takes time: 25% of onDrawFrame2()
            //checkGlError("glDrawArrays");

            // u,v-values of "mRectangleVertices[i]" will be send to the vertext shader as "attribute vec2 aTextureCoord"
            // und dann als "varying vec2 vTextureCoord" in den fragment shader kopiert
            mRectangleVertices[i].position(RECTANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(maTextureCoordHandle, 2, GLES20.GL_FLOAT, false, RECTANGLE_VERTICES_DATA_STRIDE_BYTES, mRectangleVertices[i]); // takes time 22% of onDrawFrame2()
            //checkGlError("glVertexAttribPointer maTextureCoordHandle");
          }

          GLES20.glEnableVertexAttribArray(maTextureCoordHandle);    // accessible as 'aTextureCoord' in vertex shader
          checkGlError("glEnableVertexAttribArray maTextureCoordHandle");

/*
 //mLifetimeLoc = GLES20.glGetAttribLocation(mProgramObject, "a_lifetime");
 
          GLES20.glVertexAttribPointer(maTextureCoordHandle, 1, GLES20.GL_FLOAT, false, RECTANGLE_VERTICES_DATA_STRIDE_BYTES, mRectangleVertices[i]); // takes time 22% of onDrawFrame2()

          GLES20.glEnableVertexAttribArray(moptDistHandle);    // accessible as 'optDist' in vertex shader
          checkGlError("glEnableVertexAttribArray moptDistHandle");
*/
        }

        frameCountDirty++;
      }
    }

    protected int compileShader(int shaderType, String source) {
      int shader = GLES20.glCreateShader(shaderType);
      if(shader != 0) {
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if(compiled[0] == 0) {
          Log.e(LOGTAG, "Could not compile shader " + shaderType + ":");
          Log.e(LOGTAG, GLES20.glGetShaderInfoLog(shader));
          GLES20.glDeleteShader(shader);
          shader = 0;
        }
      }
      return shader;
    }

    protected int createProgram(String vertexSource, String fragmentSource) {
      int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
      if(vertexShader == 0) {
        return -1;
      }

      int pixelShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
      if(pixelShader == 0) {
        return -2;
      }

      int program = GLES20.glCreateProgram();
      if(program != 0) {
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");

        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");

        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if(linkStatus[0] != GLES20.GL_TRUE) {
          Log.e(LOGTAG, "Could not link program: ");
          Log.e(LOGTAG, GLES20.glGetProgramInfoLog(program));
          GLES20.glDeleteProgram(program);
          program = 0;
        }
      }
      return program;
    }

    protected void checkGlError(String op) {
      int error;
      while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
        Log.e(LOGTAG, op + ": glError " + error);
        throw new RuntimeException(op + ": glError " + error);
      }
    }
  }
}


