uniform mat4 uMVPMatrix; // camera/frustumM  (ModelViewProjectionMatrix) (same for all vertices)

attribute vec4 vPosition; // x,y,z-values of the texture
attribute vec2 aTextureCoord; // u,v-values of the texture    // from src/org/timur/glticker/GlTickerView.java
// attribute float optDist;

varying vec2 vTextureCoord;   // to fragment.sh
varying float alpha;          // to fragment.sh

void main() {
  gl_Position = uMVPMatrix * vPosition;
  vTextureCoord = aTextureCoord;
  
  // gl_Position.z is the distance between the cam and the next texture
  // if it is roughly = defaultCamToTextureDistance =50/4*3, then we apply full opacity
  // if the distance is smaller or bigger, we apply less opacity
  // we do so by handing over varying alpha to the fragment shader
  // note: vPosition.z = mRectangleVertData[i][2] 
  // note: if(cam.z - vPosition.z < pageZdist/4*3f) then apply less opacity
  
  // alpha to be 1.0 at gl_Position.z 

  float optDist1 = 11.0;
  float optDist2 = 14.0;

  if(gl_Position.z <= optDist1) {   // the texture we look at... is nearer than optDist1
    alpha = (gl_Position.z+4.0)/(optDist1+4.0);
    if(alpha<0.05) alpha=0.05;

  } else if(gl_Position.z <= optDist2) {   // the texture we look at... is nearer than optDist2
    // the texture we look at... is <= optDist2 (nearer)
    alpha = 1.0;

  } else {   // the texture we look at... is further away than optDist1
    alpha = (28.0 - min(28.0,gl_Position.z-optDist2)) / 28.0;
    if(alpha<0.15) alpha=0.15;
  }
}

