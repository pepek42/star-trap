// Accretion disk particles (ST-087): every particle orbits on the GPU, phi = phi0 + sqrt(gm / r^3) * time.
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

varying vec2 v_corner;
varying float v_lutU;
varying float v_brightness;

void main() {
    float phi = a_phi0 + sqrt(u_gm / (a_r * a_r * a_r)) * u_time;
    vec2 pos = u_center + a_r * vec2(cos(phi), sin(phi)) + a_corner * a_size;
    float g2 = max(1.0 - 1.5 * u_rs / a_r, 0.0); // g^2, so g^4 is its square
    v_brightness = g2 * g2;
    float t = clamp((log(max(a_temp, 1.0)) - u_lutMap.x) * u_lutMap.y, 0.0, 1.0);
    v_lutU = t * u_lutMap.z + u_lutMap.w;
    v_corner = a_corner;
    gl_Position = u_projTrans * vec4(pos, 0.0, 1.0);
}
