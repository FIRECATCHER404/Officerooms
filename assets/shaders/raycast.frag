#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;

uniform sampler2D u_mazeTex;
uniform sampler2D u_lightTex;
uniform vec2 u_resolution;
uniform vec3 u_cameraPos;
uniform vec3 u_cameraDir;
uniform float u_fov;
uniform float u_cellSize;
uniform float u_wallHeight;
uniform vec2 u_mazeSize;
uniform float u_time;

const float MAX_DIST = 350.0;
const int MAX_STEPS = 160;
const vec2 LAB_MIN_CELL = vec2(4.0, 4.0);
const vec2 LAB_MAX_CELL = vec2(14.0, 11.0);
const int LAB_PC_COUNT = 6;

float sampleWallCell(vec2 cell);
float sampleLightCell(vec2 cell);
float lightMask(vec2 id);
float openAt(vec2 cell);
vec2 fixtureCell(vec2 region);
vec2 fixtureTileIndex(vec2 region);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 34.56);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash21(i + vec2(0.0, 0.0));
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float quantize(float v, float steps) {
    return floor(v * steps) / max(1.0, steps - 1.0);
}

vec2 lightStrideCells() {
    return vec2(7.0, 6.0);
}

vec2 lightRegion(vec2 worldXZ) {
    vec2 mazePos = worldXZ / u_cellSize;
    return floor(mazePos / lightStrideCells());
}

vec3 lightCenterWorld(vec2 region) {
    vec2 tile = vec2(u_cellSize * 0.5);
    vec2 t = fixtureTileIndex(region);
    vec2 center = (t + 0.5) * tile;
    return vec3(center.x, u_wallHeight - 0.02, center.y);
}

float isMazeCellValid(vec2 cell) {
    return step(0.0, cell.x) * step(0.0, cell.y) * step(cell.x, u_mazeSize.x - 1.0) * step(cell.y, u_mazeSize.y - 1.0);
}

float openAt(vec2 cell) {
    float valid = isMazeCellValid(cell);
    return valid * (1.0 - sampleWallCell(cell));
}

vec2 fixtureCell(vec2 region) {
    vec2 c = floor((region + 0.5) * lightStrideCells());
    vec2 best = c;
    bool found = openAt(c) > 0.5;

    vec2 t = c + vec2(1.0, 0.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(-1.0, 0.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(0.0, 1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(0.0, -1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }

    t = c + vec2(1.0, 1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(-1.0, 1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(1.0, -1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(-1.0, -1.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }

    t = c + vec2(2.0, 0.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(-2.0, 0.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(0.0, 2.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }
    t = c + vec2(0.0, -2.0); if (!found && openAt(t) > 0.5) { best = t; found = true; }

    return best;
}

float lightEnabled(vec2 region) {
    return openAt(fixtureCell(region));
}

vec2 fixtureTileIndex(vec2 region) {
    vec2 c = fixtureCell(region);
    return floor((c + 0.5) * 2.0);
}

float sampleWallCell(vec2 cell) {
    vec2 uv = (cell + 0.5) / u_mazeSize;
    return texture2D(u_mazeTex, uv).r;
}

float sampleLightCell(vec2 cell) {
    vec2 uv = (cell + 0.5) / u_mazeSize;
    return texture2D(u_lightTex, uv).r;
}

vec2 fixtureTileIndexFromCell(vec2 cell) {
    return floor((cell + 0.5) * 2.0);
}

vec3 lightCenterFromCell(vec2 cell) {
    vec2 tile = vec2(u_cellSize * 0.5);
    vec2 t = fixtureTileIndexFromCell(cell);
    vec2 center = (t + 0.5) * tile;
    return vec3(center.x, u_wallHeight - 0.02, center.y);
}

float fluorescentFlicker(float t) {
    float base = 0.92 + 0.08 * sin(t * 8.0);
    float rareDrop = smoothstep(0.985, 1.0, sin(t * 0.67) * 0.5 + 0.5);
    return base * mix(1.0, 0.78, rareDrop);
}

float lightMask(vec2 id) {
    float n = hash21(id * 1.37 + 2.1);
    return step(0.35, n);
}

float wallOcclusionBetween(vec2 worldA, vec2 worldB) {
    vec2 a = worldA / u_cellSize;
    vec2 b = worldB / u_cellSize;
    vec2 d = b - a;
    float segLen = length(d);
    if (segLen < 1e-4) {
        return 1.0;
    }

    vec2 rd = d / segLen;
    vec2 cell = floor(a);
    vec2 stepDir = sign(rd);
    vec2 deltaDist = abs(vec2(
        rd.x == 0.0 ? 1e6 : 1.0 / rd.x,
        rd.y == 0.0 ? 1e6 : 1.0 / rd.y
    ));

    vec2 sideDist;
    sideDist.x = (rd.x < 0.0) ? (a.x - cell.x) * deltaDist.x : (cell.x + 1.0 - a.x) * deltaDist.x;
    sideDist.y = (rd.y < 0.0) ? (a.y - cell.y) * deltaDist.y : (cell.y + 1.0 - a.y) * deltaDist.y;

    for (int i = 0; i < 96; i++) {
        if (cell.x < 0.0 || cell.y < 0.0 || cell.x >= u_mazeSize.x || cell.y >= u_mazeSize.y) {
            return 0.0;
        }
        if (sampleWallCell(cell) > 0.5) {
            return 0.0;
        }

        bool stepX = sideDist.x < sideDist.y;
        float tNext = stepX ? sideDist.x : sideDist.y;
        if (tNext > segLen) {
            break;
        }

        if (stepX) {
            sideDist.x += deltaDist.x;
            cell.x += stepDir.x;
        } else {
            sideDist.y += deltaDist.y;
            cell.y += stepDir.y;
        }
    }

    return 1.0;
}

float lightFromRegion(vec3 p, vec2 region, float flicker) {
    float enabled = lightEnabled(region);
    vec3 lightPos = lightCenterWorld(region);
    if (sampleWallCell(floor(lightPos.xz / u_cellSize)) > 0.5) {
        return 0.0;
    }

    vec2 toLight = lightPos.xz - p.xz;
    float toLightLen = length(toLight);
    vec2 dir = toLight / max(toLightLen, 1e-4);
    vec2 start = p.xz + dir * 0.08;
    vec2 end = lightPos.xz - dir * 0.08;
    float visible = wallOcclusionBetween(start, end);

    float dist = length(p - lightPos);
    float attenuation = 1.0 / (1.0 + 0.09 * dist + 0.032 * dist * dist);
    attenuation *= smoothstep(16.0, 4.0, dist);
    return enabled * visible * attenuation * flicker;
}

float lightFromCell(vec3 p, vec2 cell, float flicker) {
    if (isMazeCellValid(cell) < 0.5) {
        return 0.0;
    }
    float enabled = sampleLightCell(cell);
    if (enabled < 0.5 || sampleWallCell(cell) > 0.5) {
        return 0.0;
    }

    vec3 lightPos = lightCenterFromCell(cell);
    vec2 toLight = lightPos.xz - p.xz;
    float toLightLen = length(toLight);
    vec2 dir = toLight / max(toLightLen, 1e-4);
    vec2 start = p.xz + dir * 0.08;
    vec2 end = lightPos.xz - dir * 0.08;
    float visible = wallOcclusionBetween(start, end);

    float dist = length(p - lightPos);
    float attenuation = 1.0 / (1.0 + 0.09 * dist + 0.032 * dist * dist);
    attenuation *= smoothstep(16.0, 4.0, dist);
    return enabled * visible * attenuation * flicker;
}

float lightContribution(vec3 p, float flicker) {
    vec2 c = floor(p.xz / u_cellSize);
    float sum = 0.0;
    for (int dz = -4; dz <= 4; dz++) {
        for (int dx = -4; dx <= 4; dx++) {
            sum += lightFromCell(p, c + vec2(float(dx), float(dz)), flicker);
        }
    }
    return sum;
}

bool intersectAabb(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax, out float tNear, out vec3 normal) {
    vec3 inv = 1.0 / max(abs(rd), vec3(1e-6)) * sign(rd);
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tsmaller = min(t0, t1);
    vec3 tbigger = max(t0, t1);

    float tEnter = max(max(tsmaller.x, tsmaller.y), tsmaller.z);
    float tExit = min(min(tbigger.x, tbigger.y), tbigger.z);
    if (tExit < max(tEnter, 0.0)) {
        return false;
    }

    tNear = (tEnter > 0.0) ? tEnter : tExit;
    normal = vec3(0.0);
    if (tEnter == tsmaller.x) normal = vec3(-sign(rd.x), 0.0, 0.0);
    else if (tEnter == tsmaller.y) normal = vec3(0.0, -sign(rd.y), 0.0);
    else normal = vec3(0.0, 0.0, -sign(rd.z));
    return true;
}

void tryLabBox(
    vec3 ro,
    vec3 rd,
    vec3 bmin,
    vec3 bmax,
    float matId,
    inout float tBest,
    inout vec3 nBest,
    inout float mBest
) {
    float t;
    vec3 n;
    if (intersectAabb(ro, rd, bmin, bmax, t, n) && t > 0.0 && t < tBest) {
        tBest = t;
        nBest = n;
        mBest = matId;
    }
}

void intersectComputerLab(vec3 ro, vec3 rd, inout float tBest, inout vec3 nBest, inout float mBest) {
    vec2 labMin = LAB_MIN_CELL * u_cellSize;
    vec2 labMax = (LAB_MAX_CELL + 1.0) * u_cellSize;

    // One long desk against the north wall.
    float deskMinX = labMin.x + u_cellSize * 0.7;
    float deskMaxX = labMax.x - u_cellSize * 0.7;
    float deskMinZ = labMin.y + 0.32;
    float deskMaxZ = deskMinZ + 1.25;

    tryLabBox(ro, rd, vec3(deskMinX, 0.0, deskMinZ), vec3(deskMaxX, 0.74, deskMaxZ), 1.0, tBest, nBest, mBest);
    tryLabBox(ro, rd, vec3(deskMinX, 0.74, deskMinZ), vec3(deskMaxX, 0.82, deskMaxZ), 1.1, tBest, nBest, mBest);

    float span = deskMaxX - deskMinX;
    for (int i = 0; i < LAB_PC_COUNT; i++) {
        float fi = float(i) + 1.0;
        float x = deskMinX + span * (fi / float(LAB_PC_COUNT + 1));

        // Old monitor body.
        tryLabBox(ro, rd, vec3(x - 0.34, 0.94, deskMinZ + 0.22), vec3(x + 0.34, 1.44, deskMinZ + 0.70), 2.0, tBest, nBest, mBest);
        // Dark unplugged screen.
        tryLabBox(ro, rd, vec3(x - 0.28, 1.03, deskMinZ + 0.69), vec3(x + 0.28, 1.34, deskMinZ + 0.72), 2.1, tBest, nBest, mBest);
        // Monitor stand + keyboard.
        tryLabBox(ro, rd, vec3(x - 0.09, 0.84, deskMinZ + 0.40), vec3(x + 0.09, 0.94, deskMinZ + 0.52), 2.0, tBest, nBest, mBest);
        tryLabBox(ro, rd, vec3(x - 0.35, 0.82, deskMinZ + 0.78), vec3(x + 0.35, 0.87, deskMinZ + 1.10), 2.2, tBest, nBest, mBest);
        // Tower on desk side.
        tryLabBox(ro, rd, vec3(x + 0.44, 0.82, deskMinZ + 0.18), vec3(x + 0.74, 1.24, deskMinZ + 0.58), 2.3, tBest, nBest, mBest);

        // Chair in front of each station.
        float chairCenterZ = deskMaxZ + 0.95;
        tryLabBox(ro, rd, vec3(x - 0.34, 0.40, chairCenterZ - 0.30), vec3(x + 0.34, 0.52, chairCenterZ + 0.30), 3.0, tBest, nBest, mBest);
        // Backrest on the far side so chair faces the desk.
        tryLabBox(ro, rd, vec3(x - 0.30, 0.52, chairCenterZ + 0.20), vec3(x + 0.30, 1.05, chairCenterZ + 0.30), 3.0, tBest, nBest, mBest);
        tryLabBox(ro, rd, vec3(x - 0.26, 0.0, chairCenterZ - 0.22), vec3(x - 0.20, 0.40, chairCenterZ - 0.16), 3.0, tBest, nBest, mBest);
        tryLabBox(ro, rd, vec3(x + 0.20, 0.0, chairCenterZ - 0.22), vec3(x + 0.26, 0.40, chairCenterZ - 0.16), 3.0, tBest, nBest, mBest);
        tryLabBox(ro, rd, vec3(x - 0.26, 0.0, chairCenterZ + 0.16), vec3(x - 0.20, 0.40, chairCenterZ + 0.22), 3.0, tBest, nBest, mBest);
        tryLabBox(ro, rd, vec3(x + 0.20, 0.0, chairCenterZ + 0.16), vec3(x + 0.26, 0.40, chairCenterZ + 0.22), 3.0, tBest, nBest, mBest);
    }
}

vec3 labPropMaterial(float matId, vec3 p) {
    vec2 uv = floor(p.xz * 11.0) / 11.0;
    float s = (hash21(uv * 1.7) - 0.5) * 0.025;

    if (matId < 1.5) { // Desk
        return vec3(0.32, 0.33, 0.35) + vec3(s);
    } else if (matId < 2.05) { // Monitor shell
        return vec3(0.58, 0.59, 0.61) + vec3(s * 0.6);
    } else if (matId < 2.15) { // Screen off
        return vec3(0.03, 0.03, 0.035);
    } else if (matId < 2.25) { // Keyboard
        return vec3(0.47, 0.48, 0.50) + vec3(s * 0.5);
    } else if (matId < 2.35) { // Tower
        return vec3(0.44, 0.45, 0.47) + vec3(s * 0.5);
    }
    return vec3(0.24, 0.25, 0.27) + vec3(s * 0.4); // Chairs
}

vec3 wallMaterial(vec3 p, vec3 normal) {
    vec2 uv;
    if (abs(normal.x) > 0.5) {
        uv = p.zy;
    } else {
        uv = p.xy;
    }
    uv = floor(uv * 10.0) / 10.0;

    float n0 = hash21(uv * 1.9);
    float n1 = hash21(uv * 5.7 + 11.0);
    float staticGrain = (n0 - 0.5) * 0.035 + (n1 - 0.5) * 0.015;

    vec3 base = vec3(0.50, 0.50, 0.51);
    return base + vec3(staticGrain);
}

vec3 floorMaterial(vec3 p) {
    vec2 uv = floor(p.xz * 10.0) / 10.0;
    float n0 = hash21(uv * 1.2);
    float n1 = hash21(uv * 3.6 + 7.0);
    float staticGrain = (n0 - 0.5) * 0.03 + (n1 - 0.5) * 0.02;

    vec3 floorColor = vec3(0.24, 0.24, 0.25);
    return floorColor + vec3(staticGrain);
}

vec3 ceilingMaterial(vec3 p, out float emissive) {
    vec2 tile = vec2(u_cellSize * 0.5);
    vec2 scaled = p.xz / tile;
    vec2 idx = floor(scaled);
    vec2 local = fract(scaled) - 0.5;

    vec2 texel = floor(p.xz * 7.0) / 7.0;
    float staticA = (hash21(texel * 2.3) - 0.5) * 0.025;
    vec3 base = vec3(0.47, 0.47, 0.48) + vec3(staticA);

    float gridLine = step(0.47, abs(local.x)) + step(0.47, abs(local.y));
    base = mix(base, vec3(0.30, 0.30, 0.30), clamp(gridLine, 0.0, 1.0));

    vec2 cell = floor(p.xz / u_cellSize);
    vec2 fixtureTile = fixtureTileIndexFromCell(cell);
    float hasPanel = sampleLightCell(cell) * (1.0 - sampleWallCell(cell));
    float onFixtureTile =
        (1.0 - step(0.5, abs(idx.x - fixtureTile.x))) *
        (1.0 - step(0.5, abs(idx.y - fixtureTile.y)));

    // The light panel replaces a single ceiling tile interior.
    vec2 panelHalf = vec2(0.445, 0.445);
    float panel = 1.0 - step(1.0, max(abs(local.x) / panelHalf.x, abs(local.y) / panelHalf.y));
    float frame = 1.0 - step(1.0, max(abs(local.x) / (panelHalf.x + 0.05), abs(local.y) / (panelHalf.y + 0.05)));
    frame = clamp(frame - panel, 0.0, 1.0);

    float panelMask = hasPanel * onFixtureTile;
    emissive = panelMask * panel;
    vec3 panelColor = vec3(1.14, 1.14, 1.14);
    vec3 frameColor = vec3(0.33, 0.33, 0.34);
    vec3 c = mix(base, frameColor, panelMask * frame);
    return mix(c, panelColor, emissive);
}

void main() {
    vec2 ndc = v_uv * 2.0 - 1.0;
    float aspect = u_resolution.x / max(1.0, u_resolution.y);

    vec3 forward = normalize(u_cameraDir);
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(right, forward));
    float tanHalfFov = tan(u_fov * 0.5);

    vec3 rayDir = normalize(
        forward +
        right * ndc.x * tanHalfFov * aspect +
        up * ndc.y * tanHalfFov
    );

    vec2 camGrid = u_cameraPos.xz / u_cellSize;
    vec2 mapPos = floor(camGrid);
    vec2 rayGridDir = rayDir.xz;

    vec2 deltaDist = abs(vec2(
        rayGridDir.x == 0.0 ? 1e6 : 1.0 / rayGridDir.x,
        rayGridDir.y == 0.0 ? 1e6 : 1.0 / rayGridDir.y
    ));

    vec2 stepDir = sign(rayGridDir);
    vec2 sideDist;
    sideDist.x = (rayGridDir.x < 0.0) ? (camGrid.x - mapPos.x) * deltaDist.x : (mapPos.x + 1.0 - camGrid.x) * deltaDist.x;
    sideDist.y = (rayGridDir.y < 0.0) ? (camGrid.y - mapPos.y) * deltaDist.y : (mapPos.y + 1.0 - camGrid.y) * deltaDist.y;

    float tWall = 1e9;
    vec3 wallNormal = vec3(0.0);

    for (int i = 0; i < MAX_STEPS; i++) {
        bool stepX = sideDist.x < sideDist.y;
        if (stepX) {
            sideDist.x += deltaDist.x;
            mapPos.x += stepDir.x;
        } else {
            sideDist.y += deltaDist.y;
            mapPos.y += stepDir.y;
        }

        if (mapPos.x < 0.0 || mapPos.y < 0.0 || mapPos.x >= u_mazeSize.x || mapPos.y >= u_mazeSize.y) {
            break;
        }

        if (sampleWallCell(mapPos) > 0.5) {
            tWall = (stepX ? sideDist.x - deltaDist.x : sideDist.y - deltaDist.y) * u_cellSize;
            wallNormal = stepX ? vec3(-stepDir.x, 0.0, 0.0) : vec3(0.0, 0.0, -stepDir.y);
            break;
        }
    }

    float tFloor = 1e9;
    if (rayDir.y < -0.0001) {
        tFloor = (0.0 - u_cameraPos.y) / rayDir.y;
    }

    float tCeil = 1e9;
    if (rayDir.y > 0.0001) {
        tCeil = (u_wallHeight - u_cameraPos.y) / rayDir.y;
    }

    float tProp = 1e9;
    vec3 propNormal = vec3(0.0);
    float propMat = 0.0;
    intersectComputerLab(u_cameraPos, rayDir, tProp, propNormal, propMat);

    float tSurface = min(tWall, min(tFloor, tCeil));
    bool hitProp = tProp < tSurface;
    float tHit = min(tSurface, tProp);
    bool hitWall = !hitProp && (tWall <= tFloor) && (tWall <= tCeil);
    bool hitFloor = !hitProp && (tFloor < tWall) && (tFloor <= tCeil);

    if (tHit < 0.0 || tHit > MAX_DIST) {
        gl_FragColor = vec4(0.02, 0.02, 0.01, 1.0);
        return;
    }

    vec3 hitPos = u_cameraPos + rayDir * tHit;
    vec3 color;
    float emissive = 0.0;

    if (hitProp) {
        color = labPropMaterial(propMat, hitPos);
    } else if (hitWall) {
        color = wallMaterial(hitPos, wallNormal);
    } else if (hitFloor) {
        color = floorMaterial(hitPos);
    } else {
        color = ceilingMaterial(hitPos, emissive);
    }

    float flicker = fluorescentFlicker(u_time);
    float practicalLight = lightContribution(hitPos, flicker);
    vec3 fluorescent = vec3(0.96, 0.97, 1.0) * practicalLight * 3.2;
    color = color * (fluorescent + vec3(0.018));

    // Keep carpet barely readable for navigation in dark zones.
    if (hitFloor) {
        color = max(color, vec3(0.016, 0.016, 0.017));
    }

    color += vec3(1.7, 1.7, 1.7) * emissive * flicker;

    float fog = clamp(tHit / MAX_DIST, 0.0, 1.0);
    color = mix(color, vec3(0.0), fog * 0.90);

    color = color / (color + vec3(1.0));
    color = pow(color, vec3(0.9));

    gl_FragColor = vec4(color, 1.0);
}
