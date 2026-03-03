#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_time;

varying vec2 v_uv;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 barrel(vec2 uv, float amt) {
    vec2 d = uv - 0.5;
    float r2 = dot(d, d);
    return uv + d * r2 * amt;
}

void main() {
    vec2 uv = barrel(v_uv, -0.018);

    vec2 pixel = 1.0 / max(u_resolution, vec2(1.0));
    float caAmount = 0.55;

    vec3 col;
    col.r = texture2D(u_texture, uv + vec2(pixel.x * caAmount, 0.0)).r;
    col.g = texture2D(u_texture, uv).g;
    col.b = texture2D(u_texture, uv - vec2(pixel.x * caAmount, 0.0)).b;

    float scan = sin(uv.y * u_resolution.y * 0.72 + u_time * 12.0) * 0.008;
    float grain = (hash(uv * (u_time * 220.0 + 10.0)) - 0.5) * 0.015;

    float vig = smoothstep(0.98, 0.10, distance(v_uv, vec2(0.5)));
    float edgeDarken = smoothstep(0.0, 0.05, v_uv.x) * smoothstep(0.0, 0.05, 1.0 - v_uv.x);
    edgeDarken *= smoothstep(0.0, 0.04, v_uv.y) * smoothstep(0.0, 0.04, 1.0 - v_uv.y);

    float pulse = 0.99 + 0.01 * sin(u_time * 7.0 + uv.y * 18.0);

    col *= pulse;
    col += scan + grain;
    col *= vig * edgeDarken;

    col = mix(col, vec3(dot(col, vec3(0.299, 0.587, 0.114))), 0.02);
    col *= vec3(1.0);

    gl_FragColor = vec4(col, 1.0);
}
