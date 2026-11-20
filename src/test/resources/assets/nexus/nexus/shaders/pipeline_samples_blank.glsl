#nexus base position_color

#moj_import <nexus:pipeline_samples_blank.glsl>

layout(std140) uniform SampleTint {
    vec4 TintColor;
};

vec4 nexus_albedo(vec4 albedo) {
    return TintColor;
}
