#nexus base position_color

in float Strength;

out float strength;

layout(std140) uniform SampleTint {
    vec4 TintColor;
};

void nexus_vertex() {
    strength = Strength;
}

vec4 nexus_albedo(vec4 albedo) {
    return mix(albedo, TintColor, strength);
}
