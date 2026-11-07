// Lit sprite (ST-097): colour * (ambient + sum of wrapped-diffuse point lights), lit in the arena frame.
// Mirrors shadeSpriteTexel / shade in render/LitSpriteRenderer.kt and render/Lighting.kt line by line.
// GLSL ES 1.00 (WebGL 1 compatible): constant loop bound, loop-index array access only.
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// MAX_LIGHTS (MAX_LIGHTS_PER_SPRITE), LIGHT_HEIGHT, MAX_OCCLUDERS, STAR_ANGULAR_SIZE and MIN_PENUMBRA (Lighting.kt) are
// #defined by LitSpriteRenderer through ShaderProgram.prependFragmentCode; directional lights use a tenth of LIGHT_HEIGHT.

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform sampler2D u_normals;
uniform vec4 u_colorRegion;  // u, v, u2, v2 of the colour region
uniform vec4 u_normalRegion; // u, v, u2, v2 of the normal-map region (mode 1)
uniform int u_normalMode;    // 0 flat, 1 normal map, 2 analytic sphere
uniform float u_domeBlend;   // share of the sphere normal mixed into the normal map (mode 1), ASTEROID_DOME_BLEND for rocks
uniform vec2 u_center;       // arena position of the sprite centre
uniform vec2 u_rot;          // cos, sin of the arena angle
uniform vec2 u_halfSize;
uniform float u_ambient;
uniform vec3 u_lightPos[MAX_LIGHTS];
uniform vec3 u_lightColor[MAX_LIGHTS]; // colour * intensity
uniform float u_lightSize[MAX_LIGHTS];
uniform float u_lightRange[MAX_LIGHTS];
uniform float u_lightDir[MAX_LIGHTS];  // 1 = directional (x, y is a direction)
uniform int u_lightCount;
uniform vec3 u_occ[MAX_OCCLUDERS];    // shadow casters (ST-098): x, y, radius in the arena frame
uniform int u_occCount;

// Mirrors circleShadowFactor in render/Lighting.kt: 1 = lit, 0 = shadowed by the circle occ (x, y, r) seen from p.
// For a directional light lpos is the direction toward it.
float circleShadow(vec2 p, vec2 lpos, float lightSize, bool directional, vec3 occ) {
    vec2 toL = directional ? lpos : lpos - p;
    float len = length(toL);
    if (len <= 0.0) return 1.0;
    vec2 dir = toL / len;
    vec2 oc = occ.xy - p;
    float t = dot(oc, dir);
    if (t <= 0.0 || (!directional && t >= len)) return 1.0;
    float dPerp = abs(oc.x * dir.y - oc.y * dir.x);
    float w = max(directional ? STAR_ANGULAR_SIZE * t : lightSize * t / len, MIN_PENUMBRA);
    return smoothstep(occ.z - w, occ.z + w, dPerp);
}

void main() {
    vec4 albedo = texture2D(u_texture, v_texCoords);

    vec2 t = (v_texCoords - u_colorRegion.xy) / (u_colorRegion.zw - u_colorRegion.xy);
    vec2 q = (t * 2.0 - 1.0) * vec2(1.0, -1.0);

    vec3 n = vec3(0.0, 0.0, 1.0);
    if (u_normalMode == 1) {
        vec2 nuv = u_normalRegion.xy + t * (u_normalRegion.zw - u_normalRegion.xy);
        vec3 raw = texture2D(u_normals, nuv).rgb * 2.0 - 1.0;
        n = raw / max(length(raw), 0.0001);
        vec3 sphere = vec3(q, sqrt(max(0.0, 1.0 - dot(q, q))));
        vec3 blended = mix(n, sphere, u_domeBlend);
        n = blended / max(length(blended), 0.0001);
    } else if (u_normalMode == 2) {
        n = vec3(q, sqrt(max(0.0, 1.0 - dot(q, q))));
    }
    n.xy = vec2(u_rot.x * n.x - u_rot.y * n.y, u_rot.y * n.x + u_rot.x * n.y);

    vec2 local = q * u_halfSize;
    vec2 p = u_center + vec2(u_rot.x * local.x - u_rot.y * local.y, u_rot.y * local.x + u_rot.x * local.y);

    vec3 shade = vec3(u_ambient);
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= u_lightCount) break;
        vec3 pos = u_lightPos[i];
        vec3 l;
        float f = 1.0;
        float wrap = 0.0;
        if (u_lightDir[i] > 0.5) {
            l = normalize(vec3(pos.xy, LIGHT_HEIGHT / 10.0));
        } else {
            vec2 dxy = pos.xy - p;
            float d = length(dxy);
            l = normalize(vec3(dxy, pos.z));
            float qd = d / u_lightSize[i];
            f = (1.0 / (1.0 + qd * qd)) * (1.0 - smoothstep(0.75 * u_lightRange[i], u_lightRange[i], d));
            wrap = d <= 0.0 ? 1.0 : clamp(u_lightSize[i] / d, 0.0, 1.0);
        }
        float diffuse = max(0.0, (dot(n, l) + wrap) / (1.0 + wrap));
        float shadow = 1.0;
        for (int j = 0; j < MAX_OCCLUDERS; j++) {
            if (j >= u_occCount) break;
            shadow *= circleShadow(p, pos.xy, u_lightSize[i], u_lightDir[i] > 0.5, u_occ[j]);
        }
        shade += u_lightColor[i] * (f * diffuse * shadow);
    }

    gl_FragColor = vec4(albedo.rgb * shade * v_color.rgb, albedo.a * v_color.a);
}
