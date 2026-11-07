// Gravitational lensing of the backdrop (ST-088): one quad per black hole, a_corner in -1..1 scaled to the quad half-size.
// GLSL ES 1.00 (WebGL 1 compatible). Vertex shaders always have highp in WebGL.
#ifdef GL_ES
precision highp float;
#endif

attribute vec2 a_corner;

uniform mat4 u_projTrans;
uniform vec2 u_center;   // hole centre (arena frame, or its apparent position in pilot view)
uniform float u_quadHalf;

varying vec2 v_rho;      // offset from the hole centre in world units

void main() {
    v_rho = a_corner * u_quadHalf;
    gl_Position = u_projTrans * vec4(u_center + v_rho, 0.0, 1.0);
}
