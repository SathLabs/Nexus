#nexus base position_color

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

vec4 nexus_albedo(vec4 albedo) {
    return FogRenderDistanceEnd > 0.0 ? albedo : vec4(0.0, 0.0, 0.0, 1.0);
}
