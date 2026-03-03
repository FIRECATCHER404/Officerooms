package com.officerooms.backrooms;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BackroomsGame extends ApplicationAdapter {
    private static final int MAZE_WIDTH = 41;
    private static final int MAZE_HEIGHT = 41;
    private static final float CELL_SIZE = 4.0f;
    private static final float WALL_HEIGHT = 3.8f;
    private static final float MOVE_SPEED = 7.5f;
    private static final float MOUSE_SENSITIVITY = 0.12f;
    private static final float PLAYER_RADIUS = 0.35f;
    private static final float PLAYER_EYE_HEIGHT = 1.65f;
    private static final int LAB_MIN_X = 4;
    private static final int LAB_MAX_X = 14;
    private static final int LAB_MIN_Z = 4;
    private static final int LAB_MAX_Z = 11;
    private static final int LAB_PC_COUNT = 6;

    private final Random seedGenerator = new Random();
    private final Random runRandom = new Random();
    private final Vector2 lookAngles = new Vector2(0f, 0f);

    private int[][] maze;
    private int[][] hallwayLights;
    private long currentSeed;
    private PerspectiveCamera camera;

    private ShaderProgram raycastShader;
    private ShaderProgram postShader;
    private Mesh screenQuad;
    private Texture mazeTexture;
    private Texture lightTexture;
    private FrameBuffer sceneBuffer;

    private SpriteBatch hudBatch;
    private BitmapFont hudFont;
    private boolean mouseCaptured;
    private boolean showHud = false;

    @Override
    public void create() {
        Gdx.input.setCursorCatched(false);
        mouseCaptured = false;

        camera = new PerspectiveCamera(72f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.05f;
        camera.far = 450f;

        hudBatch = new SpriteBatch();
        hudFont = new BitmapFont();
        hudFont.getData().setScale(1.15f);
        hudFont.setColor(0.96f, 0.94f, 0.78f, 1f);

        createShaderPipeline();
        startNewRun();
    }

    private void createShaderPipeline() {
        ShaderProgram.pedantic = false;
        FileHandle vert = Gdx.files.internal("assets/shaders/raycast.vert");
        FileHandle frag = Gdx.files.internal("assets/shaders/raycast.frag");
        raycastShader = new ShaderProgram(vert, frag);
        if (!raycastShader.isCompiled()) {
            throw new IllegalStateException("Raycast shader compile failed: " + raycastShader.getLog());
        }
        FileHandle postVert = Gdx.files.internal("assets/shaders/post.vert");
        FileHandle postFrag = Gdx.files.internal("assets/shaders/post.frag");
        postShader = new ShaderProgram(postVert, postFrag);
        if (!postShader.isCompiled()) {
            throw new IllegalStateException("Post shader compile failed: " + postShader.getLog());
        }

        screenQuad = new Mesh(
            true,
            4,
            6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        );
        screenQuad.setVertices(new float[] {
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
             1f,  1f, 1f, 0f,
            -1f,  1f, 0f, 0f
        });
        screenQuad.setIndices(new short[] {0, 1, 2, 2, 3, 0});
        resizeSceneBuffer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void startNewRun() {
        currentSeed = seedGenerator.nextLong();
        runRandom.setSeed(currentSeed);

        maze = generateMaze(MAZE_WIDTH, MAZE_HEIGHT, runRandom);
        uploadMazeTexture();
        hallwayLights = generateHallwayLights(maze, MAZE_WIDTH, MAZE_HEIGHT);
        uploadLightTexture();

        int[] spawnCell = findSpawnCell(runRandom);
        Vector3 spawn = cellCenter(spawnCell[0], spawnCell[1]);
        camera.position.set(spawn);
        setSpawnLookDirection(spawnCell[0], spawnCell[1]);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private void uploadMazeTexture() {
        if (mazeTexture != null) {
            mazeTexture.dispose();
        }

        Pixmap pixmap = new Pixmap(MAZE_WIDTH, MAZE_HEIGHT, Pixmap.Format.RGBA8888);
        for (int z = 0; z < MAZE_HEIGHT; z++) {
            for (int x = 0; x < MAZE_WIDTH; x++) {
                if (maze[z][x] == 1) {
                    pixmap.setColor(Color.WHITE);
                } else {
                    pixmap.setColor(Color.BLACK);
                }
                pixmap.drawPixel(x, z);
            }
        }

        mazeTexture = new Texture(pixmap);
        mazeTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        mazeTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pixmap.dispose();
    }

    private void uploadLightTexture() {
        if (lightTexture != null) {
            lightTexture.dispose();
        }

        Pixmap pixmap = new Pixmap(MAZE_WIDTH, MAZE_HEIGHT, Pixmap.Format.RGBA8888);
        for (int z = 0; z < MAZE_HEIGHT; z++) {
            for (int x = 0; x < MAZE_WIDTH; x++) {
                if (hallwayLights[z][x] == 1) {
                    pixmap.setColor(Color.WHITE);
                } else {
                    pixmap.setColor(Color.BLACK);
                }
                pixmap.drawPixel(x, z);
            }
        }

        lightTexture = new Texture(pixmap);
        lightTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        lightTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pixmap.dispose();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        resizeSceneBuffer(width, height);
    }

    private void resizeSceneBuffer(int width, int height) {
        if (sceneBuffer != null) {
            sceneBuffer.dispose();
        }
        sceneBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);
        updateMouseCapture();
        updateLook();
        updateMovement(delta);

        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            showHud = !showHud;
        }

        sceneBuffer.begin();
        Gdx.gl.glViewport(0, 0, sceneBuffer.getWidth(), sceneBuffer.getHeight());
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.01f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        raycastShader.bind();
        raycastShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        raycastShader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        raycastShader.setUniformf("u_cameraDir", camera.direction.x, camera.direction.y, camera.direction.z);
        raycastShader.setUniformf("u_fov", camera.fieldOfView * MathUtils.degreesToRadians);
        raycastShader.setUniformf("u_cellSize", CELL_SIZE);
        raycastShader.setUniformf("u_wallHeight", WALL_HEIGHT);
        raycastShader.setUniformf("u_mazeSize", MAZE_WIDTH, MAZE_HEIGHT);
        raycastShader.setUniformf("u_time", (float) (System.nanoTime() * 1e-9));
        raycastShader.setUniformi("u_mazeTex", 0);
        raycastShader.setUniformi("u_lightTex", 1);

        mazeTexture.bind(0);
        lightTexture.bind(1);
        screenQuad.render(raycastShader, GL20.GL_TRIANGLES);
        sceneBuffer.end();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        postShader.bind();
        postShader.setUniformi("u_texture", 0);
        postShader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        postShader.setUniformf("u_time", (float) (System.nanoTime() * 1e-9));
        sceneBuffer.getColorBufferTexture().bind(0);
        screenQuad.render(postShader, GL20.GL_TRIANGLES);

        if (showHud) {
            drawHud();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            startNewRun();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    private void drawHud() {
        float top = Gdx.graphics.getHeight() - 14f;
        hudBatch.begin();
        hudFont.draw(hudBatch, "Backrooms Build", 14f, top);
        hudFont.draw(hudBatch, "WASD move | Mouse look | Click capture mouse | Tab release", 14f, top - 26f);
        hudFont.draw(hudBatch, "R new seed/run | H HUD | Esc quit", 14f, top - 52f);
        hudFont.draw(hudBatch, "Seed: " + currentSeed, 14f, top - 78f);
        hudBatch.end();
    }

    private void updateMouseCapture() {
        if (!mouseCaptured && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            mouseCaptured = true;
            Gdx.input.setCursorCatched(true);
        }
        if (mouseCaptured && Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            mouseCaptured = false;
            Gdx.input.setCursorCatched(false);
        }
    }

    private void updateLook() {
        if (!mouseCaptured) {
            return;
        }
        float deltaX = Gdx.input.getDeltaX() * MOUSE_SENSITIVITY;
        float deltaY = -Gdx.input.getDeltaY() * MOUSE_SENSITIVITY;

        lookAngles.x = (lookAngles.x + deltaX) % 360f;
        lookAngles.y = MathUtils.clamp(lookAngles.y + deltaY, -89f, 89f);

        float yawRad = lookAngles.x * MathUtils.degreesToRadians;
        float pitchRad = lookAngles.y * MathUtils.degreesToRadians;

        Vector3 direction = new Vector3();
        direction.x = MathUtils.cos(pitchRad) * MathUtils.sin(yawRad);
        direction.y = MathUtils.sin(pitchRad);
        direction.z = -MathUtils.cos(pitchRad) * MathUtils.cos(yawRad);

        camera.direction.set(direction).nor();
        camera.up.set(Vector3.Y);
    }

    private void updateMovement(float delta) {
        Vector3 forward = new Vector3(camera.direction.x, 0f, camera.direction.z);
        if (forward.len2() > 0f) {
            forward.nor();
        }
        Vector3 right = new Vector3(forward).crs(Vector3.Y).nor();
        Vector3 move = new Vector3();

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            move.add(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            move.sub(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            move.sub(right);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            move.add(right);
        }

        if (move.len2() > 0f) {
            move.nor().scl(MOVE_SPEED * delta);
            applyCollisionAndMove(move);
        }

        camera.position.y = PLAYER_EYE_HEIGHT;
        camera.update();
    }

    private void applyCollisionAndMove(Vector3 deltaMove) {
        float nextX = camera.position.x + deltaMove.x;
        float nextZ = camera.position.z + deltaMove.z;

        if (isWalkable(nextX, camera.position.z)) {
            camera.position.x = nextX;
        }

        if (isWalkable(camera.position.x, nextZ)) {
            camera.position.z = nextZ;
        }
    }

    private boolean isWalkable(float worldX, float worldZ) {
        float[] checks = new float[] {
            -PLAYER_RADIUS, -PLAYER_RADIUS,
             PLAYER_RADIUS, -PLAYER_RADIUS,
            -PLAYER_RADIUS,  PLAYER_RADIUS,
             PLAYER_RADIUS,  PLAYER_RADIUS
        };

        for (int i = 0; i < checks.length; i += 2) {
            float testX = worldX + checks[i];
            float testZ = worldZ + checks[i + 1];
            int cellX = (int) Math.floor(testX / CELL_SIZE);
            int cellZ = (int) Math.floor(testZ / CELL_SIZE);
            if (cellX < 0 || cellZ < 0 || cellX >= MAZE_WIDTH || cellZ >= MAZE_HEIGHT) {
                return false;
            }
            if (maze[cellZ][cellX] == 1) {
                return false;
            }
            if (collidesWithLabProps(testX, testZ)) {
                return false;
            }
        }

        return true;
    }

    private boolean collidesWithLabProps(float x, float z) {
        float labMinX = LAB_MIN_X * CELL_SIZE;
        float labMaxX = (LAB_MAX_X + 1) * CELL_SIZE;
        float labMinZ = LAB_MIN_Z * CELL_SIZE;
        float labMaxZ = (LAB_MAX_Z + 1) * CELL_SIZE;
        if (x < labMinX - 2f || x > labMaxX + 2f || z < labMinZ - 2f || z > labMaxZ + 2f) {
            return false;
        }

        float deskMinX = labMinX + CELL_SIZE * 0.7f;
        float deskMaxX = labMaxX - CELL_SIZE * 0.7f;
        float deskMinZ = labMinZ + 0.32f;
        float deskMaxZ = deskMinZ + 1.25f;
        if (insideRect(x, z, deskMinX - PLAYER_RADIUS, deskMaxX + PLAYER_RADIUS, deskMinZ - PLAYER_RADIUS, deskMaxZ + PLAYER_RADIUS)) {
            return true;
        }

        float span = deskMaxX - deskMinX;
        float chairCenterZ = deskMaxZ + 0.95f;
        for (int i = 0; i < LAB_PC_COUNT; i++) {
            float fi = (float) i + 1f;
            float cx = deskMinX + span * (fi / (LAB_PC_COUNT + 1f));
            if (insideRect(
                x, z,
                cx - 0.38f - PLAYER_RADIUS, cx + 0.38f + PLAYER_RADIUS,
                chairCenterZ - 0.34f - PLAYER_RADIUS, chairCenterZ + 0.34f + PLAYER_RADIUS
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean insideRect(float x, float z, float minX, float maxX, float minZ, float maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private Vector3 cellCenter(int cellX, int cellZ) {
        return new Vector3(
            cellX * CELL_SIZE + CELL_SIZE * 0.5f,
            PLAYER_EYE_HEIGHT,
            cellZ * CELL_SIZE + CELL_SIZE * 0.5f
        );
    }

    private int[] findSpawnCell(Random rng) {
        float labCenterX = (LAB_MIN_X + LAB_MAX_X) * 0.5f;
        float labCenterZ = (LAB_MIN_Z + LAB_MAX_Z) * 0.5f;
        float minNearDist2 = 4f * 4f;   // avoid spawning right on the lab door
        float maxNearDist2 = 13f * 13f; // still near-ish to the lab

        List<int[]> nearLabCandidates = new ArrayList<>();
        List<int[]> strongCandidates = new ArrayList<>();
        List<int[]> fallbackCandidates = new ArrayList<>();

        for (int z = 1; z < MAZE_HEIGHT - 1; z++) {
            for (int x = 1; x < MAZE_WIDTH - 1; x++) {
                if (maze[z][x] != 0) {
                    continue;
                }

                int openNeighbors = 0;
                if (maze[z][x + 1] == 0) openNeighbors++;
                if (maze[z][x - 1] == 0) openNeighbors++;
                if (maze[z + 1][x] == 0) openNeighbors++;
                if (maze[z - 1][x] == 0) openNeighbors++;

                boolean inLab = x >= LAB_MIN_X && x <= LAB_MAX_X && z >= LAB_MIN_Z && z <= LAB_MAX_Z;
                if (!inLab) {
                    fallbackCandidates.add(new int[] {x, z});
                    if (openNeighbors >= 2) {
                        float dx = x - labCenterX;
                        float dz = z - labCenterZ;
                        float dist2 = dx * dx + dz * dz;
                        if (dist2 >= minNearDist2 && dist2 <= maxNearDist2) {
                            nearLabCandidates.add(new int[] {x, z});
                        }
                        strongCandidates.add(new int[] {x, z});
                    }
                }
            }
        }

        if (!nearLabCandidates.isEmpty()) {
            return nearLabCandidates.get(rng.nextInt(nearLabCandidates.size()));
        }
        if (!strongCandidates.isEmpty()) {
            return strongCandidates.get(rng.nextInt(strongCandidates.size()));
        }
        if (!fallbackCandidates.isEmpty()) {
            return fallbackCandidates.get(rng.nextInt(fallbackCandidates.size()));
        }

        // Worst-case fallback.
        return new int[] {1, 1};
    }

    private void setSpawnLookDirection(int cellX, int cellZ) {
        int[][] dirs = new int[][] {
            {0, -1}, // north
            {1, 0},  // east
            {0, 1},  // south
            {-1, 0}  // west
        };

        int[] best = new int[] {0, -1};
        for (int[] d : dirs) {
            int nx = cellX + d[0];
            int nz = cellZ + d[1];
            if (nx >= 0 && nz >= 0 && nx < MAZE_WIDTH && nz < MAZE_HEIGHT && maze[nz][nx] == 0) {
                best = d;
                break;
            }
        }

        camera.direction.set(best[0], 0f, best[1]).nor();
        lookAngles.y = 0f;
        lookAngles.x = MathUtils.atan2(best[0], -best[1]) * MathUtils.radiansToDegrees;
    }

    private int[][] generateMaze(int width, int height, Random rng) {
        int[][] grid = new int[height][width];

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                grid[z][x] = 1;
            }
        }

        int startX = 1;
        int startZ = 1;
        grid[startZ][startX] = 0;

        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {startX, startZ});

        int[][] dirs = new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};

        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            int cx = current[0];
            int cz = current[1];

            shuffleArray(dirs, rng);
            boolean carved = false;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];

                if (nx <= 0 || nz <= 0 || nx >= width - 1 || nz >= height - 1) {
                    continue;
                }

                if (grid[nz][nx] == 1) {
                    grid[cz + d[1] / 2][cx + d[0] / 2] = 0;
                    grid[nz][nx] = 0;
                    stack.push(new int[] {nx, nz});
                    carved = true;
                    break;
                }
            }

            if (!carved) {
                stack.pop();
            }
        }

        for (int z = 2; z < height - 2; z++) {
            for (int x = 2; x < width - 2; x++) {
                if (grid[z][x] == 1 && rng.nextFloat() < 0.08f) {
                    grid[z][x] = 0;
                }
            }
        }

        carveComputerLab(grid, width, height);
        return grid;
    }

    private void carveComputerLab(int[][] grid, int width, int height) {
        int minX = Math.max(2, LAB_MIN_X);
        int maxX = Math.min(width - 3, LAB_MAX_X);
        int minZ = Math.max(2, LAB_MIN_Z);
        int maxZ = Math.min(height - 3, LAB_MAX_Z);

        // Solid perimeter to read like a dedicated room.
        for (int x = minX - 1; x <= maxX + 1; x++) {
            grid[minZ - 1][x] = 1;
            grid[maxZ + 1][x] = 1;
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            grid[z][minX - 1] = 1;
            grid[z][maxX + 1] = 1;
        }

        // Room interior.
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                grid[z][x] = 0;
            }
        }

        // Main door and guaranteed east connector corridor.
        int doorZ = (minZ + maxZ) / 2;
        int eastX = Math.min(width - 2, maxX + 8);
        for (int x = maxX; x <= eastX; x++) {
            grid[doorZ][x] = 0;
        }
        for (int z = Math.max(1, doorZ - 2); z <= Math.min(height - 2, doorZ + 2); z++) {
            grid[z][eastX] = 0;
        }

        // Secondary south exit so the room is never a one-way dead-end.
        int doorX = (minX + maxX) / 2;
        int southZ = Math.min(height - 2, maxZ + 7);
        for (int z = maxZ; z <= southZ; z++) {
            grid[z][doorX] = 0;
        }
        for (int x = Math.max(1, doorX - 2); x <= Math.min(width - 2, doorX + 2); x++) {
            grid[southZ][x] = 0;
        }
    }

    private int[][] generateHallwayLights(int[][] grid, int width, int height) {
        int[][] lights = new int[height][width];

        for (int z = 2; z < height - 2; z++) {
            for (int x = 2; x < width - 2; x++) {
                if (grid[z][x] != 0) {
                    continue;
                }

                boolean inLab = x >= LAB_MIN_X && x <= LAB_MAX_X && z >= LAB_MIN_Z && z <= LAB_MAX_Z;
                if (inLab) {
                    continue;
                }

                boolean openN = grid[z - 1][x] == 0;
                boolean openS = grid[z + 1][x] == 0;
                boolean openE = grid[z][x + 1] == 0;
                boolean openW = grid[z][x - 1] == 0;
                int openCount = (openN ? 1 : 0) + (openS ? 1 : 0) + (openE ? 1 : 0) + (openW ? 1 : 0);

                // Hallways only: narrow corridor cells, not rooms/junctions.
                if (openCount != 2) {
                    continue;
                }

                float centerHash = hashCell(x, z);
                if (centerHash < 0.58f) {
                    continue;
                }

                // Local-maximum pick in nearby hallway cells removes obvious grid repetition.
                boolean bestLocal = true;
                for (int dz = -2; dz <= 2 && bestLocal; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int nx = x + dx;
                        int nz = z + dz;
                        if (grid[nz][nx] != 0) {
                            continue;
                        }

                        boolean nN = grid[nz - 1][nx] == 0;
                        boolean nS = grid[nz + 1][nx] == 0;
                        boolean nE = grid[nz][nx + 1] == 0;
                        boolean nW = grid[nz][nx - 1] == 0;
                        int nOpen = (nN ? 1 : 0) + (nS ? 1 : 0) + (nE ? 1 : 0) + (nW ? 1 : 0);
                        if (nOpen != 2) {
                            continue;
                        }

                        if (hashCell(nx, nz) > centerHash) {
                            bestLocal = false;
                            break;
                        }
                    }
                }

                if (bestLocal) {
                    lights[z][x] = 1;
                }
            }
        }

        // Explicit lab ceiling lights for the computer lab: two lines for better desk coverage.
        int labCenterZ = (LAB_MIN_Z + LAB_MAX_Z) / 2;
        int rowA = Math.max(LAB_MIN_Z + 1, labCenterZ - 1);
        int rowB = Math.min(LAB_MAX_Z - 1, labCenterZ + 1);
        for (int x = LAB_MIN_X + 1; x <= LAB_MAX_X - 1; x += 2) {
            if (grid[rowA][x] == 0) {
                lights[rowA][x] = 1;
            }
            if (grid[rowB][x] == 0) {
                lights[rowB][x] = 1;
            }
        }

        return lights;
    }

    private float hashCell(int x, int z) {
        int h = x * 374761393 ^ z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }

    private void shuffleArray(int[][] array, Random rng) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    @Override
    public void dispose() {
        Gdx.input.setCursorCatched(false);
        if (raycastShader != null) {
            raycastShader.dispose();
        }
        if (screenQuad != null) {
            screenQuad.dispose();
        }
        if (mazeTexture != null) {
            mazeTexture.dispose();
        }
        if (lightTexture != null) {
            lightTexture.dispose();
        }
        if (sceneBuffer != null) {
            sceneBuffer.dispose();
        }
        if (postShader != null) {
            postShader.dispose();
        }
        if (hudBatch != null) {
            hudBatch.dispose();
        }
        if (hudFont != null) {
            hudFont.dispose();
        }
    }
}
