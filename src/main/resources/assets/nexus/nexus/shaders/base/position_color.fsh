#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;

out vec4 fragColor;

#nexus declarations

void main() {
    vec4 color = vertexColor;
    #nexus albedo
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
