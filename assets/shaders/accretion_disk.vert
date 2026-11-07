// Accretion disk particles (ST-087): every particle orbits on the GPU, phi = phi0 + sqrt(gm / r^3) * time.
// Pilot view (ST-089, u_pilot = 1): each particle gets its own light delay on its circular orbit, the aberration boost and a
// Doppler factor toward the pilot, so the side swinging toward the pilot is bluer and brighter. Mirrors the Kotlin reference
// `diskParticleApparent` (render/AccretionDisk.kt); keep them in sync. With u_pilot = 0 the output equals ST-087.
// GLSL ES 1.00 (WebGL 1 compatible). Vertex shaders always have highp in WebGL, which keeps phi precise for a long worldTime.
#ifdef GL_ES
precision highp float;
#endif

attribute vec2 a_corner;
attribute float a_r;
attribute float a_phi0;
attribute float a_size;
attribute float a_temp;

uniform mat4 u_projTrans;
uniform vec2 u_center;
uniform float u_gm;
uniform float u_rs;
uniform float u_time;
uniform vec4 u_lutMap; // x = ln(Tmin), y = 1 / ln(Tmax / Tmin), z, w: LUT coordinate = t * z + w

uniform float u_pilot;      // 1 in pilot view
uniform float u_tau;        // light age of the (retarded) centre, s
uniform vec2 u_holeVel;     // hole arena velocity at emission, u/s
uniform vec2 u_observer;    // pilot position
uniform vec2 u_beta;        // pilot velocity / c
uniform float u_c;
uniform float u_delay;      // 1: per-particle light delay
uniform float u_aberration; // 1: boost into the pilot frame
uniform vec3 u_beam;        // searchlight: x = exponent, y = min, z = max (spriteBrightness)
uniform float u_betaMax;    // BETA_MAX_MASSIVE

varying vec2 v_corner;
varying float v_lutU;
varying float v_brightness;

const int RETARD_ITERATIONS = 6; // DISK_RETARD_ITERATIONS
const float BETA_EPS = 1e-6;

// apparentOffset (physics/PilotFrame.kt): boosts the offset r from the pilot into the pilot frame.
vec2 apparentOffset(vec2 r, vec2 beta) {
    float bl = length(beta);
    if (bl < BETA_EPS) return r;
    float b = min(bl, u_betaMax);
    vec2 h = beta / bl;
    float rPar = dot(r, h);
    vec2 perp = r - rPar * h;
    float gamma = 1.0 / sqrt(1.0 - b * b);
    return gamma * (rPar + b * length(r)) * h + perp;
}

float gammaOf(vec2 beta) {
    return 1.0 / sqrt(1.0 - min(dot(beta, beta), u_betaMax * u_betaMax));
}

void main() {
    float omega = sqrt(u_gm / (a_r * a_r * a_r));
    float phi = a_phi0 + omega * u_time;
    float g2 = max(1.0 - 1.5 * u_rs / a_r, 0.0); // g^2, so g^4 is its square
    float brightness = g2 * g2;
    float temp = a_temp;
    vec2 centre = u_center + a_r * vec2(cos(phi), sin(phi));
    if (u_pilot > 0.5) {
        if (u_delay > 0.5) {
            float s = 0.0;
            for (int i = 0; i < RETARD_ITERATIONS; i++) {
                float a = phi - omega * s;
                s = length(u_center + a_r * vec2(cos(a), sin(a)) - u_observer) / u_c - u_tau;
            }
            phi -= omega * s;
        }
        vec2 tangent = vec2(-sin(phi), cos(phi));
        vec2 p = u_center + a_r * vec2(cos(phi), sin(phi));
        vec2 betaSrc = (u_holeVel + omega * a_r * tangent) / u_c;
        vec2 toObs = u_observer - p;
        float len = length(toObs);
        float d = 1.0;
        if (len > 0.0) {
            vec2 n = toObs / len;
            float den = gammaOf(betaSrc) * (1.0 - dot(betaSrc, n));
            if (den > 1e-6) d = gammaOf(u_beta) * (1.0 - dot(u_beta, n)) / den;
        }
        temp *= d;
        brightness *= clamp(pow(max(d, 1e-6), u_beam.x), u_beam.y, u_beam.z);
        centre = u_aberration > 0.5 ? u_observer + apparentOffset(p - u_observer, u_beta) : p;
    }
    vec2 pos = centre + a_corner * a_size;
    v_brightness = brightness;
    float t = clamp((log(max(temp, 1.0)) - u_lutMap.x) * u_lutMap.y, 0.0, 1.0);
    v_lutU = t * u_lutMap.z + u_lutMap.w;
    v_corner = a_corner;
    gl_Position = u_projTrans * vec4(pos, 0.0, 1.0);
}
