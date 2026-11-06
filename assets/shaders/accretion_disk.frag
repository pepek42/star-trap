// Accretion disk particle: soft round dot coloured from the blackbody LUT, drawn additively.
// GLSL ES 1.00 (WebGL 1 compatible).
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

varying vec2 v_corner;
varying float v_lutU;
varying float v_brightness;

uniform sampler2D u_lut;
uniform float u_alpha;

void main() {
    float d = length(v_corner);
    float soft = 1.0 - smoothstep(0.2, 1.0, d);
    vec3 color = texture2D(u_lut, vec2(v_lutU, 0.5)).rgb * v_brightness;
    gl_FragColor = vec4(color, soft * u_alpha);
}
