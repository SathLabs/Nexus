#nexus base entity

#define NO_OVERLAY
#define EMISSIVE
#define NO_CARDINAL_LIGHTING

in int Pick;

flat out int pick;

layout(std140) uniform SampleTint {
    vec4 TintColor;
};

void nexus_vertex() {
    pick = Pick;
}

vec4 nexus_albedo(vec4 albedo) {
    return pick == 3 ? TintColor : albedo;
}
