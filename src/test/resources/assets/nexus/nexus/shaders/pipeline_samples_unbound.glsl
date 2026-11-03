#nexus base position_color

layout(std140) uniform SampleUnbound {
    vec4 UnboundColor;
};

vec4 nexus_albedo(vec4 albedo) {
    return albedo * UnboundColor;
}
