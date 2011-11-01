uniform mat4 uMVPMatrix; // camera/frustumM  (ModelViewProjectionMatrix) (same for all vertices)

attribute vec4 vPosition; // x,y,z-values of the texture
attribute vec2 aTextureCoord; // u,v-values of the texture    // from src/org/timur/glticker/GlTickerView.java
// attribute float optDist;

varying vec2 vTextureCoord;   // to fragment.sh
varying float alpha;          // to fragment.sh

void main() {
  gl_Position = uMVPMatrix * vPosition;
  vTextureCoord = aTextureCoord;
  
  // gl_Position.z is the distance between the cam and the next message-texture
  // if it is roughly = defaultCamToTextureDistance - zNear = 21.2 - 2.8, then we apply full opacity
  // if the distance is smaller or bigger, we apply less opacity
  // we do so by handing over varying alpha to the fragment shader
  // note: vPosition.z = mRectangleVertData[i][2] 
  
  float optDist1 = 11.0;
  float optDist2 = 14.0;

  if(gl_Position.z <= optDist1) {   // the texture we look at... is nearer than optDist1
    alpha = (gl_Position.z+4.0)/(optDist1+4.0);
    if(alpha<0.03) alpha=0.03;

  } else if(gl_Position.z <= optDist2) {   // the texture we look at... is nearer than optDist2
    // the texture we look at... is <= optDist2 (nearer)
    alpha = 1.0;

  } else {   // the texture we look at... is further away than optDist1
    alpha = (29.0 - min(29.0,gl_Position.z-optDist2)) / 29.0;
    if(alpha<0.10) alpha=0.10;
  }
}

