// Boundary field shimmer: faint honeycomb that brightens with depth into the field,
// plus ripples that expand at the speed of light from boundary impacts.
// GLSL ES 1.00 (WebGL 1 compatible).
#ifdef GL_ES
precision mediump float;
#endif

#define MAX_IMPACTS 16

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_world;

uniform vec2 u_mapSize;
uniform float u_skin;
uniform float u_time;
uniform vec3 u_baseColor;
uniform float u_baseAlpha;
uniform float u_c;
uniform vec4 u_impacts[MAX_IMPACTS]; // x, y, startTime, strength
uniform int u_impactCount;

const float CELL_SIZE = 1.2;
const float RING_WIDTH = 0.6;
const float RING_DECAY = 0.6;
const float SPOT_RADIUS_SQ = 1.44;
const float SPOT_LIFE = 0.2;
const float Q_SOFT_MIN = 0.15;
const vec2 HEX_STEP = vec2(1.0, 1.7320508);

// Hexagonal distance: 0 at the cell centre, 0.5 on the cell edge.
float hexDist(vec2 p) {
    p = abs(p);
    return max(dot(p, HEX_STEP * 0.5), p.x);
}

// Edge mask of a hexagonal grid built from two offset rectangular grids.
float hexEdge(vec2 world) {
    vec2 p = world / CELL_SIZE;
    vec4 centers = floor(vec4(p, p - vec2(0.5, 1.0)) / HEX_STEP.xyxy) + 0.5;
    vec4 offsets = vec4(p - centers.xy * HEX_STEP, p - (centers.zw + 0.5) * HEX_STEP);
    vec2 g = dot(offsets.xy, offsets.xy) < dot(offsets.zw, offsets.zw) ? offsets.xy : offsets.zw;
    return smoothstep(0.38, 0.47, hexDist(g));
}

void main() {
    float dx = min(v_world.x, u_mapSize.x - v_world.x);
    float dy = min(v_world.y, u_mapSize.y - v_world.y);
    float d = max(min(dx, dy), 0.0);
    float q = clamp((u_skin - d) / u_skin, 0.0, 1.0);
    float hex = hexEdge(v_world) * (0.6 + 0.4 * sin(u_time * 2.0 + v_world.x * 0.3));
    float base = u_baseAlpha * q * q * hex;

    float ripple = 0.0;
    float flash = 0.0;
    for (int i = 0; i < MAX_IMPACTS; i++) {
        if (i >= u_impactCount) break;
        vec4 imp = u_impacts[i];
        float rho = distance(v_world, imp.xy);
        float age = u_time - imp.z;
        float front = (rho - u_c * age) / RING_WIDTH;
        float ring = imp.w * exp(-front * front) * exp(-age / RING_DECAY);
        float spot = imp.w * exp(-rho * rho / SPOT_RADIUS_SQ) * max(0.0, 1.0 - age / SPOT_LIFE);
        ripple += ring + spot;
        flash += imp.w * (ring + spot);
    }

    float qSoft = max(q, Q_SOFT_MIN);
    float intensity = base + ripple * qSoft;
    vec3 color = mix(u_baseColor, vec3(1.0), clamp(flash, 0.0, 1.0));
    gl_FragColor = vec4(color, clamp(intensity, 0.0, 1.0));
}
