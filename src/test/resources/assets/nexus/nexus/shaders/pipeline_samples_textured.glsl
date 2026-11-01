#nexus base position_tex_color

in vec2 Weights;

out vec2 weights;

layout(std140) uniform SampleTint {
    vec4 TintColor;
};

void nexus_vertex() {
    weights = Weights;
}

vec4 nexus_albedo(vec4 albedo) {
    return mix(albedo, TintColor, weights.y - weights.x);
}
