// Gravitational lensing of the backdrop (ST-088, research E18): thin lens, source = rho * (1 - w * R_E^2 / |rho|^2).
// Mirrors render/LensingPass.kt lensSourceOffset and lensGain. GLSL ES 1.00 (WebGL 1 compatible).
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

varying vec2 v_rho;

uniform sampler2D u_tex;   // the backdrop, screen sized
uniform vec2 u_uvCenter;   // texture coordinate of the hole centre
uniform vec2 u_uvAxisX;    // texture coordinate change per world unit along x
uniform vec2 u_uvAxisY;    // ... and along y
uniform float u_rE;        // Einstein radius
uniform float u_shadow;    // shadow radius, drawn black
uniform float u_fadeInner; // the deflection fades out between max(|x|, |y|) = u_fadeInner and u_fadeOuter (the quad edge)
uniform float u_fadeOuter;
uniform float u_clear;     // backdrop clear grey (SCREEN_CLEAR_GRAY): empty space is not brightened
uniform float u_gainMax;   // cap of the point-source magnification gain (LENS_GAIN_MAX)

void main() {
    float r2 = dot(v_rho, v_rho);
    if (r2 < u_shadow * u_shadow) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float edge = max(abs(v_rho.x), abs(v_rho.y));
    float w = 1.0 - smoothstep(u_fadeInner, u_fadeOuter, edge);
    vec2 src = v_rho * (1.0 - w * u_rE * u_rE / r2);
    vec2 uv = u_uvCenter + src.x * u_uvAxisX + src.y * u_uvAxisY;
    float q = u_rE * u_rE / r2;
    float d = abs(1.0 - q * q);
    float mu = d * u_gainMax < 1.0 ? u_gainMax : 1.0 / d;
    float gain = 1.0 + w * max(min(mu, u_gainMax) - 1.0, 0.0);
    vec3 col = texture2D(u_tex, uv).rgb;
    gl_FragColor = vec4(vec3(u_clear) + gain * max(col - vec3(u_clear), 0.0), 1.0);
}
