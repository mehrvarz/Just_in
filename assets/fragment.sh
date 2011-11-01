precision mediump float;
uniform sampler2D sTexture;

varying vec2 vTextureCoord;   // from vertex.sh
varying float alpha;          // from vertex.sh

//uniform float time;
//uniform vec2 resolution;

void main() {
  //gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);

//gl_FragColor = texture2D(sTexture, vTextureCoord);

//gl_FragColor = vec4(alpha, alpha, alpha, alpha) * texture2D(sTexture, vTextureCoord);
  gl_FragColor = vec4(1.0, 1.0, 1.0, alpha) * texture2D(sTexture, vTextureCoord);

//gl_FragColor = vec4(alpha, alpha, alpha, alpha) * vec4(texture2D(sTexture, vTextureCoord).xyz,texture2D(sTexture, vTextureCoord).a);
//gl_FragColor = vec4(1.0, 1.0, 1.0, 0) * vec4(texture2D(sTexture, vTextureCoord).xyz,texture2D(sTexture, vTextureCoord).a);


  //vec2 position = - 1.0 + 2.0 * gl_FragCoord.xy / resolution.xy;
  //float red = abs( sin( position.x * position.y + time / 5.0 ) );
  //float green = abs( sin( position.x * position.y + time / 4.0 ) );
  //float blue = abs( sin( position.x * position.y + time / 3.0 ) );
  //gl_FragColor = vec4( red, green, blue, 1.0 );
}

