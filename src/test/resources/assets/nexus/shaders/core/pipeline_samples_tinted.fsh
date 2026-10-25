#version 330

layout(std140) uniform SampleTint {
    vec4 TintColor;
};

out vec4 fragColor;

void main() {
    fragColor = TintColor;
}
