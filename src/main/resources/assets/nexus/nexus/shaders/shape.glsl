#nexus base position_color

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

// The values of Kind, in the order of Primitives.ShapeInputs.Kind.
#define KIND_DISC 0
#define KIND_RING 1
#define KIND_SPHERE 2

// The pixels the quad reaches past the shape, on top of half the band of a ring.
#define MARGIN 2.0

// The number of rim points sampled before the two nearest ones are refined.
#define RIM_SAMPLES 24

// The number of refinement steps from each of the two nearest rim samples.
#define RIM_STEPS 4

// The smallest clip w of a point that lies in front of the eye.
#define FRONT 1.0e-4

in vec3 Center;
in vec3 Axis;
in float Radius;
in float Width;
in vec2 Corner;
in int Kind;

out vec3 shapePoint;
flat out vec3 shapeEye;
flat out vec3 shapeCenter;
flat out vec3 shapeAxis;
flat out float shapeRadius;
flat out float shapeWidth;
flat out int shapeKind;
flat out vec4 rimCenter;
flat out vec4 rimFirst;
flat out vec4 rimSecond;

// A unit vector perpendicular to the given unit vector.
vec3 perpendicular(vec3 direction) {
    vec3 other = abs(direction.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    return normalize(cross(direction, other));
}

// Places the corner on a quad that faces the eye and covers the projection of the shape, or on the whole screen if the eye is inside the sphere or next to the circle.
void nexus_vertex() {
    mat4 unprojection = inverse(ProjMat);
    vec4 eye = unprojection * vec4(0.0, 0.0, 1.0, 0.0);
    shapeEye = eye.xyz / eye.w;
    shapeCenter = (ModelViewMat * vec4(Center, 1.0)).xyz;
    shapeRadius = Radius * length(ModelViewMat[0].xyz);
    shapeWidth = Width;
    shapeKind = Kind;

    vec3 toCenter = shapeCenter - shapeEye;
    float range = length(toCenter);
    vec3 forward = range > 0.0 ? toCenter / range : vec3(0.0, 0.0, -1.0);
    vec3 side;
    vec2 extent;
    if (Kind == KIND_SPHERE) {
        shapeAxis = vec3(0.0);
        rimCenter = vec4(0.0);
        rimFirst = vec4(0.0);
        rimSecond = vec4(0.0);
        side = perpendicular(forward);
        // The tangent cone of the sphere crosses the plane through its center at this distance from the center.
        float tangent = range * range - shapeRadius * shapeRadius;
        extent = tangent > 0.0 ? vec2(range * shapeRadius / sqrt(tangent)) : vec2(-1.0);
    } else {
        shapeAxis = normalize(mat3(ModelViewMat) * Axis);
        vec3 first = perpendicular(shapeAxis) * shapeRadius;
        rimCenter = ProjMat * vec4(shapeCenter, 1.0);
        rimFirst = ProjMat * vec4(first, 0.0);
        rimSecond = ProjMat * vec4(cross(shapeAxis, first), 0.0);
        float cosine = dot(shapeAxis, forward);
        vec3 tilt = shapeAxis - cosine * forward;
        float sine = length(tilt);
        side = sine > 1.0e-4 ? tilt / sine : perpendicular(forward);
        // The circle comes no closer to the eye than this along the forward direction, so its projection is bounded by it.
        float nearest = range - shapeRadius * sine;
        extent = nearest > 0.0 ? vec2(abs(cosine), 1.0) * range * shapeRadius / nearest : vec2(-1.0);
    }

    vec3 point;
    if (extent.x < 0.0 || max(extent.x, extent.y) > 100.0 * range) {
        vec4 screen = unprojection * vec4(Corner, 0.0, 1.0);
        point = screen.xyz / screen.w;
    } else {
        // A pixel spans this tangent at the middle of the screen and less towards its edges, so the margin grows with the slope.
        float pixel = 2.0 / (ProjMat[1][1] * ScreenSize.y);
        vec2 slope = extent / range;
        extent += pixel * (0.5 * Width + MARGIN) * range * (1.0 + slope * slope);
        point = shapeCenter + side * Corner.x * extent.x + cross(forward, side) * Corner.y * extent.y;
    }

    shapePoint = point;
    gl_Position = ProjMat * vec4(point, 1.0);
}

#ifdef NEXUS_FRAGMENT
// The clip position of the rim point at the given angle.
vec4 rimAt(float angle) {
    return rimCenter + cos(angle) * rimFirst + sin(angle) * rimSecond;
}

// The window position of the given clip position, in pixels.
vec2 pixelOf(vec4 clip) {
    return (clip.xy / clip.w * 0.5 + 0.5) * ScreenSize;
}

// The distance in pixels from the fragment to the rim point at the given angle, or a billion if the point lies behind the eye.
float rimDistance(float angle) {
    vec4 clip = rimAt(angle);
    return clip.w > FRONT ? distance(pixelOf(clip), gl_FragCoord.xy) : 1.0e9;
}

// Moves the given angle towards the rim point nearest to the fragment, at most one sample spacing per step.
float refine(float angle, float spacing) {
    float current = rimDistance(angle);
    for (int i = 0; i < RIM_STEPS; i++) {
        vec4 clip = rimAt(angle);
        vec4 tangent = cos(angle) * rimSecond - sin(angle) * rimFirst;
        vec2 velocity = (tangent.xy * clip.w - clip.xy * tangent.w) / (clip.w * clip.w) * 0.5 * ScreenSize;
        float speed = dot(velocity, velocity);
        if (!(speed > 0.0)) {
            break;
        }

        float next = angle - clamp(dot(pixelOf(clip) - gl_FragCoord.xy, velocity) / speed, -spacing, spacing);
        float moved = rimDistance(next);
        if (!(moved < current)) {
            break;
        }

        angle = next;
        current = moved;
    }

    return angle;
}

// The angle of the rim point nearest to the fragment on the screen.
float nearestRimAngle() {
    float spacing = 6.28318531 / float(RIM_SAMPLES);
    float distances[RIM_SAMPLES];
    int best = 0;
    for (int i = 0; i < RIM_SAMPLES; i++) {
        distances[i] = rimDistance(float(i) * spacing);
        if (distances[i] < distances[best]) {
            best = i;
        }
    }

    // A thin ellipse brings its other side close to the fragment too, so the best sample apart from the first one is refined as well.
    int other = best;
    for (int i = 0; i < RIM_SAMPLES; i++) {
        int apart = abs(i - best);
        if (min(apart, RIM_SAMPLES - apart) > 1 && (other == best || distances[i] < distances[other])) {
            other = i;
        }
    }

    float first = refine(float(best) * spacing, spacing);
    float second = refine(float(other) * spacing, spacing);
    return rimDistance(second) < rimDistance(first) ? second : first;
}

// The coverage of the fragment by the side of an edge where the given distance to it is negative, faded over one pixel.
float coverage(float edge) {
    return clamp(0.5 - edge / max(fwidth(edge), 1.0e-6), 0.0, 1.0);
}

// The window depth of the given clip position.
float depthOf(vec4 clip) {
    return clamp((gl_DepthRange.diff * clip.z / clip.w + gl_DepthRange.near + gl_DepthRange.far) * 0.5, 0.0, 1.0);
}
#endif

// Intersects the ray of the fragment with the shape, discards a miss, and writes the depth of the hit.
vec4 nexus_albedo(vec4 albedo) {
    vec3 ray = normalize(shapePoint - shapeEye);
    vec4 color = albedo;
    float edge;
    vec4 hit;
    bool behind;
    if (shapeKind == KIND_SPHERE) {
        vec3 toCenter = shapeCenter - shapeEye;
        float along = dot(toCenter, ray);
        float miss = length(toCenter - along * ray);
        float depth = sqrt(max(shapeRadius * shapeRadius - miss * miss, 0.0));
        // If the near hit lies behind the eye, the eye is inside the sphere and sees its far side.
        float reach = along - depth >= 0.0 ? along - depth : along + depth;
        vec3 surface = shapeEye + ray * reach;
        edge = miss - shapeRadius;
        hit = ProjMat * vec4(surface, 1.0);
        behind = reach < 0.0;
        color.rgb *= 0.5 + 0.5 * abs(dot(normalize(surface - shapeCenter), ray));
    } else {
        vec4 rim = rimAt(nearestRimAngle());
        float pixels = rim.w > FRONT ? distance(pixelOf(rim), gl_FragCoord.xy) : 1.0e9;
        if (shapeKind == KIND_RING) {
            edge = pixels - 0.5 * shapeWidth;
            hit = rim;
            behind = rim.w <= FRONT;
        } else {
            float facing = dot(ray, shapeAxis);
            float reach = abs(facing) > 1.0e-6 ? dot(shapeCenter - shapeEye, shapeAxis) / facing : -1.0;
            vec3 plane = shapeEye + ray * reach;
            bool inside = reach > 0.0 && distance(plane, shapeCenter) < shapeRadius;
            edge = inside ? -pixels : pixels;
            hit = inside ? ProjMat * vec4(plane, 1.0) : rim;
            behind = !inside && rim.w <= FRONT;
        }
    }

    float alpha = coverage(edge);
    if (behind || alpha <= 0.0) {
        discard;
    }

    gl_FragDepth = depthOf(hit);
    return vec4(color.rgb, color.a * alpha);
}
