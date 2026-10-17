#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D HeadlightShadow;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AlphaCutoff;
uniform float LightIntensity;
uniform int LightCount;
uniform vec3 LightOrigin0;
uniform vec3 LightForward0;
uniform vec3 LightRight0;
uniform vec3 LightUp0;
uniform vec3 LightOrigin1;
uniform vec3 LightForward1;
uniform vec3 LightRight1;
uniform vec3 LightUp1;
uniform vec3 LightOrigin2;
uniform vec3 LightForward2;
uniform vec3 LightRight2;
uniform vec3 LightUp2;
uniform vec3 LightOrigin3;
uniform vec3 LightForward3;
uniform vec3 LightRight3;
uniform vec3 LightUp3;

uniform int CabinCount;
uniform int WaterPass;
uniform mat4 CabinTransform0;
uniform mat4 CabinTransform1;
uniform mat4 CabinTransform2;
uniform mat4 CabinTransform3;
uniform vec4 WaterSprite0;
uniform vec4 WaterSprite1;
uniform vec4 WaterSprite2;

in float vertexDistance;
in vec4 vertexColor;
in vec4 unlitColor;
in vec3 lightPosition;
in vec2 texCoord0;
out vec4 fragColor;

float shadowDepth(ivec2 pixel, int index) {
    pixel = clamp(pixel, ivec2(0), ivec2(31));
    vec2 packedDepth = texelFetch(HeadlightShadow, pixel + ivec2(0, index * 32), 0).rg;
    return dot(packedDepth, vec2(255.0, 65280.0)) * (32.0 / 65535.0);
}

float visible(ivec2 pixel, int index, vec3 forward, vec3 right, vec3 up,
              vec3 normal, float plane, float along) {
    vec2 projected = ((vec2(pixel) + 0.5) / 32.0 - 0.5) * 1.6;
    float denominator = dot(forward + right * projected.x + up * projected.y, normal);
    float receiver = abs(denominator) > 0.0001 ? plane / denominator : along;
    if (receiver <= 0.0) receiver = along;
    // Receiver-plane comparison avoids a stippled self-shadow on oblique block faces.
    return step(receiver - 0.04, shadowDepth(pixel, index));
}

float beam(vec3 origin, vec3 forward, vec3 right, vec3 up, vec3 normal, int index) {
    vec3 delta = lightPosition - origin;
    float along = dot(delta, forward);
    if (along <= 0.02 || along >= 24.0) return 0.0;
    vec2 projected = vec2(dot(delta, right), dot(delta, up)) / along;
    float cone = 1.0 - smoothstep(0.44, 0.68, length(projected));
    if (cone <= 0.0) return 0.0;
    vec2 pixel = (projected / 1.6 + 0.5) * 32.0 - 0.5;
    ivec2 base = ivec2(floor(pixel));
    vec2 fraction = fract(pixel);
    float plane = dot(delta, normal);
    // Bilinear percentage-closer filtering: interpolate visibility, never shadow distances.
    // This softens the shadow edge without treating an entire block as one light sample.
    float a = visible(base, index, forward, right, up, normal, plane, along);
    float b = visible(base + ivec2(1, 0), index, forward, right, up, normal, plane, along);
    float c = visible(base + ivec2(0, 1), index, forward, right, up, normal, plane, along);
    float d = visible(base + ivec2(1, 1), index, forward, right, up, normal, plane, along);
    float visibility = mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y);
    float attenuation = 1.0 - smoothstep(5.0, 26.0, along);
    float endFade = 1.0 - smoothstep(21.0, 24.0, along);
    return cone * visibility * attenuation * endFade;
}

bool waterUV(vec4 rect) {
    return all(greaterThanEqual(texCoord0, rect.xy)) && all(lessThanEqual(texCoord0, rect.zw));
}

bool insideCabin(mat4 transform) {
    vec3 p = (transform * vec4(lightPosition, 1.0)).xyz;
    // Approved offset hatch trunk, in original pre-scale model units.
    // Evaluate before undoing the aft compaction; this trunk is newly authored.
    if (p.x > 4.0/3.0 && p.x < 26.0/3.0 && p.y > 57.0/3.0 && p.y < 88.0/3.0
            && p.z > -13.0/3.0 && p.z < 10.0/3.0) return true;
    // Undo the authored aft-only shortening before the canonical pressure-hull test.
    // Cockpit/front canopy stay at their original dimensions (aft_compaction in bindings).
    if (p.z > -19.5) p.z = -19.5 + (p.z + 19.5) / 0.5;
    if (p.z < -38.5 || p.z > 32.4) return false;
    float radius = p.z < -20.8 ? mix(8.45, 11.65, clamp((p.z + 38.5) / 17.7, 0.0, 1.0))
                             : mix(11.65, 10.2, clamp((p.z + 20.8) / 1.6, 0.0, 1.0));
    vec2 crossSection = abs(vec2(p.x, p.y - 15.0));
    bool pressureHull = max(crossSection.x, crossSection.y) < radius
            && crossSection.x + crossSection.y < radius * 1.41421356;
    bool sidePort = abs(p.x) < 13.0 && p.y > 13.6 && p.y < 18.4 && p.z > -3.8 && p.z < 8.6;
    return pressureHull || sidePort;
}

void main() {
    vec3 normal = normalize(cross(dFdx(lightPosition), dFdy(lightPosition)));
    if (WaterPass != 0 && (waterUV(WaterSprite0) || waterUV(WaterSprite1) || waterUV(WaterSprite2))) {
        if (CabinCount > 0 && insideCabin(CabinTransform0)) discard;
        if (CabinCount > 1 && insideCabin(CabinTransform1)) discard;
        if (CabinCount > 2 && insideCabin(CabinTransform2)) discard;
        if (CabinCount > 3 && insideCabin(CabinTransform3)) discard;
    }
    vec4 albedo = texture(Sampler0, texCoord0);
    vec4 color = albedo * vertexColor * ColorModulator;
    if (color.a < AlphaCutoff) discard;
    float illumination = 0.0;
    if (LightCount > 0) illumination = max(illumination, beam(LightOrigin0, LightForward0, LightRight0, LightUp0, normal, 0));
    if (LightCount > 1) illumination = max(illumination, beam(LightOrigin1, LightForward1, LightRight1, LightUp1, normal, 1));
    if (LightCount > 2) illumination = max(illumination, beam(LightOrigin2, LightForward2, LightRight2, LightUp2, normal, 2));
    if (LightCount > 3) illumination = max(illumination, beam(LightOrigin3, LightForward3, LightRight3, LightUp3, normal, 3));
    // Preserve texture, tint, AO, alpha, existing brighter lights, and vanilla water fog.
    color.rgb = max(color.rgb, albedo.rgb * unlitColor.rgb * ColorModulator.rgb * illumination * LightIntensity);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
