// Gravitational lensing of the backdrop (ST-088, research E18): thin lens, source = rho * (1 - w * R_E^2 / |rho|^2).
// Mirrors render/LensingPass.kt lensSourceOffset. GLSL ES 1.00 (WebGL 1 compatible).
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
    gl_FragColor = vec4(texture2D(u_tex, uv).rgb, 1.0);
}
