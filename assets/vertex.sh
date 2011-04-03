uniform mat4 uMVPMatrix; // camera/frustumM  (ModelViewProjectionMatrix) (same for all vertices)
attribute vec4 vPosition; // x,y,z-values of the texture
attribute vec2 aTextureCoord; // u,v-values of the texture
varying vec2 vTextureCoord;
varying float alpha;
//varying float optDist;

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

  float optDist = 15.0;
  float camDist = min(gl_Position.z,63.0);
  if(gl_Position.z <= optDist) {
    // texture we look at is nearer or equal from optDist
    alpha = min(23.0, 23.0 - abs(camDist - optDist)) / 23.0;
  } else {
    // texture we look at is further away from optDist
    alpha = min(40.0, 40.0 - abs(camDist - optDist)) / 40.0;
    if(alpha<0.25) alpha=0.25;
  }
}

