#version 120

uniform sampler2D gtexture;
uniform sampler2D lightmap;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(gtexture, texcoord) * glcolor * texture2D(lightmap, lmcoord);
    if (color.a < 0.1) {
        discard;
    }

    gl_FragData[0] = color;
}
