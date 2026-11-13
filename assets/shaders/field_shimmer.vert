// Boundary field shimmer: SpriteBatch-compatible vertex shader. The texcoords carry the arena-frame position
// (the vertex position itself may be boosted into the pilot frame, ST-077), so the field is always computed in the arena frame.
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projTrans;

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_world;

void main() {
    v_color = a_color;
    v_texCoords = a_texCoord0;
    v_world = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
