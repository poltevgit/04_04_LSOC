package graphics;

import models.Building;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import utils.Ray;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class World3D {
    private long window;
    private int shaderProgram;
    private Mesh playerWeapon;
    private int uniModel, uniView, uniProjection, uniColor, uniLightPos;
    private int uniBoneMatrices, uniUseTexture, uniTexture;

    public enum CameraMode { FIRST_PERSON, THIRD_PERSON }
    private CameraMode currentCameraMode = CameraMode.THIRD_PERSON;

    private Vector3f playerPosition = new Vector3f(0.0f, 0.0f, 0.0f);
    private float playerYaw = -90.0f;
    private float playerPitch = 0.0f;

    private Vector3f cameraPos = new Vector3f(0.0f, 10.0f, 20.0f);
    private Vector3f cameraFront = new Vector3f(0.0f, -0.3f, -1.0f).normalize();
    private Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float lastX = 400, lastY = 300;
    private boolean firstMouse = true;
    private boolean mouseCaptured = true;

    private float deltaTime = 0.0f;
    private float lastFrame = 0.0f;

    private int terrainVAO, cubeVAO;
    private int gridVertexCount = 0;
    private List<Building> buildings = new ArrayList<>();
    private List<Vector3f> buildingPositions = new ArrayList<>();
    private List<Vector3f> buildingScales = new ArrayList<>();

    private boolean shouldClose = false;
    private boolean[] keyProcessed = new boolean[512];

    private float weaponScale = 0.5f;
    private boolean weaponLoaded = false;

    private float playerHeight = 1.7f;
    private float playerRadius = 0.4f;

    private boolean editorMode = false;
    private int selectedBuilding = -1;
    private enum GizmoMode { TRANSLATE, ROTATE, SCALE }
    private GizmoMode currentGizmoMode = GizmoMode.TRANSLATE;
    private boolean isDragging = false;
    private Vector2f gizmoStartMouse = new Vector2f();
    private float editorCameraDistance = 20.0f;
    private float editorCameraYaw = 45.0f;
    private float editorCameraPitch = -30.0f;

    // ========== АНИМАЦИЯ ПЕРСОНАЖА ==========
    private ModelLoader.AnimatedModel animIdle;
    private ModelLoader.AnimatedModel animWalk;
    private ModelLoader.AnimatedModel animWalkBack;
    private ModelLoader.AnimatedModel animStrafeLeft;
    private ModelLoader.AnimatedModel animStrafeRight;

    private AnimationPlayer currentAnimPlayer;
    private ModelLoader.AnimatedModel currentAnimModel;
    private Texture playerTexture;

    private boolean playerModelLoaded = false;
    private float playerModelScale = 0.02f;
    private boolean isMoving = false;

    public World3D(long window) {
        this.window = window;
    }

    public void enableEditorMode() {
        editorMode = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        mouseCaptured = false;
        System.out.println("Режим редактора активирован");
    }

    public void init() {
        System.out.println("=== World3D INIT START ===");

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        createShaderProgram();
        setupTerrainGeometry();
        setupCubeGeometry();
        generateRandomCity();

        // Загрузка оружия
        try {
            playerWeapon = ModelLoader.loadModel("models/FBX/SM_Rifle.fbx");
            weaponLoaded = true;
            System.out.println("Модель винтовки загружена!");
        } catch (Exception e) {
            System.err.println("Ошибка загрузки винтовки: " + e.getMessage());
            weaponLoaded = false;
        }

        // Загрузка анимированного персонажа
        loadAnimatedCharacter();

        if (!editorMode) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
        setupCallbacks();

        System.out.println("=== World3D INIT COMPLETE ===");
    }

    private void loadAnimatedCharacter() {
        try {
            // Пробуем разные пути
            String[] paths = {
                    "models/character/idle.fbx",
                    "models/FBX/SKM_Character.fbx",
                    "SKM_Character.fbx"
            };

            for (String path : paths) {
                try {
                    animIdle = ModelLoader.loadAnimated(path);
                    if (animIdle != null) {
                        System.out.println("Загружена модель: " + path);
                        break;
                    }
                } catch (Exception ignored) {}
            }

            // Загружаем остальные анимации
            try { animWalk = ModelLoader.loadAnimated("models/character/walking.fbx"); } catch (Exception e) {}
            try { animWalkBack = ModelLoader.loadAnimated("models/character/walking_backwards.fbx"); } catch (Exception e) {}
            try { animStrafeLeft = ModelLoader.AnimatedModel("models/character/left_strafe.fbx"); } catch (Exception e) {}
            try { animStrafeRight = ModelLoader.loadAnimated("models/character/right_strafe.fbx"); } catch (Exception e) {}

            // Фолбэк на idle если анимации не загрузились
            if (animWalk == null) animWalk = animIdle;
            if (animWalkBack == null) animWalkBack = animIdle;
            if (animStrafeLeft == null) animStrafeLeft = animIdle;
            if (animStrafeRight == null) animStrafeRight = animIdle;

            // Загрузка текстуры
            try {
                playerTexture = new Texture("models/character/character.png");
            } catch (Exception e) {
                System.out.println("Текстура персонажа не найдена, используем цвет");
            }

            if (animIdle != null) {
                currentAnimModel = animIdle;
                currentAnimPlayer = new AnimationPlayer(animIdle);
                currentAnimPlayer.play();
                playerModelLoaded = true;
                System.out.println("Персонаж загружен! Костей: " + animIdle.skeleton.boneCount);
            } else {
                System.err.println("Не удалось загрузить модель персонажа");
                playerModelLoaded = false;
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки персонажа: " + e.getMessage());
            e.printStackTrace();
            playerModelLoaded = false;
        }
    }

    private void generateRandomCity() {
        Random rand = new Random();
        for (int i = 0; i < 30; i++) {
            float x = (rand.nextFloat() - 0.5f) * 200.0f;
            float z = (rand.nextFloat() - 0.5f) * 200.0f;
            float width = 6.0f + rand.nextFloat() * 10.0f;
            float height = 10.0f + rand.nextFloat() * 30.0f;
            float depth = 6.0f + rand.nextFloat() * 10.0f;
            Vector3f pos = new Vector3f(x, height / 2.0f, z);
            Vector3f scale = new Vector3f(width, height, depth);
            buildingPositions.add(pos);
            buildingScales.add(scale);
            buildings.add(new Building(pos, scale));
        }
        System.out.println("Зданий сгенерировано: " + buildings.size());
    }

    private void setupTerrainGeometry() {
        int gridSize = 20;
        float spacing = 20.0f;
        List<Float> vertexList = new ArrayList<>();

        float halfSize = gridSize * spacing / 2;

        for (int i = 0; i <= gridSize; i++) {
            float pos = -halfSize + i * spacing;

            // Линия по X
            vertexList.add(pos); vertexList.add(0f); vertexList.add(-halfSize);
            vertexList.add(0f); vertexList.add(1f); vertexList.add(0f);
            vertexList.add(0f); vertexList.add(0f);

            vertexList.add(pos); vertexList.add(0f); vertexList.add(halfSize);
            vertexList.add(0f); vertexList.add(1f); vertexList.add(0f);
            vertexList.add(1f); vertexList.add(1f);

            // Линия по Z
            vertexList.add(-halfSize); vertexList.add(0f); vertexList.add(pos);
            vertexList.add(0f); vertexList.add(1f); vertexList.add(0f);
            vertexList.add(0f); vertexList.add(0f);

            vertexList.add(halfSize); vertexList.add(0f); vertexList.add(pos);
            vertexList.add(0f); vertexList.add(1f); vertexList.add(0f);
            vertexList.add(1f); vertexList.add(1f);
        }

        gridVertexCount = (gridSize + 1) * 4; // 2 линии * 2 вершины на каждую линию

        float[] vertices = new float[vertexList.size()];
        for (int i = 0; i < vertexList.size(); i++) {
            vertices[i] = vertexList.get(i);
        }

        terrainVAO = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(terrainVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * 4, 6 * 4);
        glEnableVertexAttribArray(2);
    }

    private void setupCubeGeometry() {
        float[] vertices = {
                // Front face
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  1.0f, 0.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  1.0f, 1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  1.0f, 1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  0.0f, 1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,  0.0f, 0.0f,

                // Back face
                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  0.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  1.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  1.0f, 1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  0.0f, 1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,  0.0f, 0.0f,

                // Left face
                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,  1.0f, 0.0f,
                -0.5f,  0.5f, -0.5f, -1.0f,  0.0f,  0.0f,  1.0f, 1.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,  0.0f, 1.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,  0.0f, 1.0f,
                -0.5f, -0.5f,  0.5f, -1.0f,  0.0f,  0.0f,  0.0f, 0.0f,
                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,  1.0f, 0.0f,

                // Right face
                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,  1.0f, 0.0f,
                0.5f,  0.5f, -0.5f,  1.0f,  0.0f,  0.0f,  1.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,  0.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,  0.0f, 1.0f,
                0.5f, -0.5f,  0.5f,  1.0f,  0.0f,  0.0f,  0.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,  1.0f, 0.0f,

                // Bottom face
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,  0.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,  1.0f, 1.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,  1.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,  1.0f, 0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,  0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,  0.0f, 1.0f,

                // Top face
                -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,  0.0f, 1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,  1.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,  1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,  1.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,  0.0f, 0.0f,
                -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,  0.0f, 1.0f
        };

        cubeVAO = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(cubeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * 4, 6 * 4);
        glEnableVertexAttribArray(2);
    }

    private void updateAnimation(float deltaTime) {
        if (!playerModelLoaded || currentAnimPlayer == null) return;

        ModelLoader.AnimatedModel targetAnim = animIdle;
        boolean w = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
        boolean s = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
        boolean a = glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
        boolean d = glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;

        if (w && !s) targetAnim = animWalk;
        else if (s && !w) targetAnim = animWalkBack;
        else if (a && !d) targetAnim = animStrafeLeft;
        else if (d && !a) targetAnim = animStrafeRight;

        if (targetAnim != null && targetAnim != currentAnimModel) {
            currentAnimModel = targetAnim;
            currentAnimPlayer = new AnimationPlayer(targetAnim);
            currentAnimPlayer.play();
        }

        currentAnimPlayer.update(deltaTime);
    }

    private void updateCamera() {
        Vector3f playerFront = new Vector3f(
                (float)(Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch))),
                (float)Math.sin(Math.toRadians(playerPitch)),
                (float)(Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)))
        ).normalize();

        if (editorMode) {
            float camX = playerPosition.x + (float)(editorCameraDistance * Math.cos(Math.toRadians(editorCameraYaw)) * Math.cos(Math.toRadians(editorCameraPitch)));
            float camY = playerPosition.y + 1.7f + (float)(editorCameraDistance * Math.sin(Math.toRadians(editorCameraPitch)));
            float camZ = playerPosition.z + (float)(editorCameraDistance * Math.sin(Math.toRadians(editorCameraYaw)) * Math.cos(Math.toRadians(editorCameraPitch)));
            cameraPos.set(camX, Math.max(camY, 2.0f), camZ);
            cameraFront.set(new Vector3f(playerPosition.x, playerPosition.y + 1.7f, playerPosition.z).sub(cameraPos).normalize());
        } else if (currentCameraMode == CameraMode.FIRST_PERSON) {
            cameraPos.set(playerPosition.x, playerPosition.y + 1.7f, playerPosition.z);
            cameraFront.set(playerFront);
        } else {
            float distance = 10.0f;
            float height = 5.0f;
            Vector3f offset = new Vector3f(playerFront).mul(-distance);
            cameraPos.set(playerPosition.x, playerPosition.y + height, playerPosition.z);
            cameraPos.add(offset);
            cameraFront.set(new Vector3f(playerPosition).add(new Vector3f(0, 1.7f, 0)).sub(cameraPos).normalize());
        }
        cameraUp.set(0.0f, 1.0f, 0.0f);
    }

    public boolean render() {
        float currentFrame = (float) glfwGetTime();
        deltaTime = currentFrame - lastFrame;
        lastFrame = currentFrame;

        processInput();

        if (editorMode) {
            processEditorInput();
        }

        updateAnimation(deltaTime);
        updateCamera();

        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        glViewport(0, 0, width[0], height[0]);

        glClearColor(0.3f, 0.5f, 0.8f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60.0f), (float) width[0] / height[0], 0.1f, 1000.0f);
        Matrix4f view = new Matrix4f().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);

        glUniformMatrix4fv(uniProjection, false, projection.get(new float[16]));
        glUniformMatrix4fv(uniView, false, view.get(new float[16]));
        glUniform3f(uniLightPos, 50.0f, 100.0f, 50.0f);
        glUniform1i(uniUseTexture, 0);

        // СЕТКА
        glBindVertexArray(terrainVAO);
        Matrix4f terrainModel = new Matrix4f();
        glUniformMatrix4fv(uniModel, false, terrainModel.get(new float[16]));
        glUniform3f(uniColor, 0.0f, 0.8f, 0.0f);
        glLineWidth(2.0f);
        glDrawArrays(GL_LINES, 0, gridVertexCount);

        // ЗДАНИЯ
        glBindVertexArray(cubeVAO);
        for (int i = 0; i < buildingPositions.size(); i++) {
            Matrix4f model = new Matrix4f().translate(buildingPositions.get(i)).scale(buildingScales.get(i));
            glUniformMatrix4fv(uniModel, false, model.get(new float[16]));

            if (editorMode && i == selectedBuilding) {
                glUniform3f(uniColor, 1.0f, 0.8f, 0.2f);
            } else {
                if (i % 3 == 0) glUniform3f(uniColor, 0.6f, 0.5f, 0.5f);
                else if (i % 3 == 1) glUniform3f(uniColor, 0.5f, 0.6f, 0.5f);
                else glUniform3f(uniColor, 0.5f, 0.5f, 0.6f);
            }
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        // ПЕРСОНАЖ
        drawPlayerModel();

        // ОРУЖИЕ ОТ ПЕРВОГО ЛИЦА
        if (currentCameraMode == CameraMode.FIRST_PERSON && weaponLoaded) {
            drawFirstPersonWeapon(width[0], height[0]);
        }

        glfwSwapBuffers(window);

        return !glfwWindowShouldClose(window) && !shouldClose;
    }

    private void drawPlayerModel() {
        if (currentCameraMode == CameraMode.FIRST_PERSON) return;

        if (!playerModelLoaded || currentAnimPlayer == null) {
            drawFallbackPlayerModel();
            return;
        }

        float[] boneMatrices = currentAnimPlayer.getBoneMatrices();

        Matrix4f modelMatrix = new Matrix4f()
                .translate(playerPosition.x, playerPosition.y, playerPosition.z)
                .rotateY((float) Math.toRadians(playerYaw + 90))
                .scale(playerModelScale);

        glUniformMatrix4fv(uniModel, false, modelMatrix.get(new float[16]));

        glUniform1i(uniUseTexture, playerTexture != null ? 1 : 0);
        if (playerTexture != null) {
            playerTexture.bind(0);
            glUniform1i(uniTexture, 0);
        }

        glUniformMatrix4fv(uniBoneMatrices, false, boneMatrices);

        currentAnimModel.mesh.render();

        glUniform1i(uniUseTexture, 0);
    }

    private void drawFallbackPlayerModel() {
        glBindVertexArray(cubeVAO);

        Matrix4f bodyModel = new Matrix4f()
                .translate(playerPosition.x, playerPosition.y + 0.9f, playerPosition.z)
                .rotateY((float) Math.toRadians(playerYaw + 90))
                .scale(0.8f, 1.6f, 0.6f);
        glUniformMatrix4fv(uniModel, false, bodyModel.get(new float[16]));
        glUniform3f(uniColor, 0.2f, 0.6f, 0.9f);
        glDrawArrays(GL_TRIANGLES, 0, 36);

        Matrix4f headModel = new Matrix4f()
                .translate(playerPosition.x, playerPosition.y + 1.8f, playerPosition.z)
                .scale(0.6f);
        glUniformMatrix4fv(uniModel, false, headModel.get(new float[16]));
        glUniform3f(uniColor, 0.9f, 0.7f, 0.5f);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    private void drawFirstPersonWeapon(int screenWidth, int screenHeight) {
        glDisable(GL_DEPTH_TEST);

        Matrix4f weaponProjection = new Matrix4f().perspective((float) Math.toRadians(70.0f), (float) screenWidth / screenHeight, 0.01f, 10.0f);
        glUniformMatrix4fv(uniProjection, false, weaponProjection.get(new float[16]));

        Matrix4f weaponView = new Matrix4f().lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1), cameraUp);
        glUniformMatrix4fv(uniView, false, weaponView.get(new float[16]));

        Matrix4f weaponModel = new Matrix4f()
                .translate(0.4f, -0.3f, -0.6f)
                .rotateY((float) Math.toRadians(180.0f))
                .scale(weaponScale);
        glUniformMatrix4fv(uniModel, false, weaponModel.get(new float[16]));
        glUniform3f(uniColor, 1.0f, 1.0f, 1.0f);

        playerWeapon.render();
        glEnable(GL_DEPTH_TEST);
    }

    private void processInput() {
        float speed = 15.0f * deltaTime;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) speed *= 1.8f;

        Vector3f playerFront = new Vector3f(
                (float)(Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch))),
                0,
                (float)(Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)))
        ).normalize();
        Vector3f playerRight = new Vector3f(playerFront).cross(cameraUp).normalize();
        Vector3f moveDirection = new Vector3f();

        isMoving = false;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) { moveDirection.add(playerFront); isMoving = true; }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) { moveDirection.sub(playerFront); isMoving = true; }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) { moveDirection.sub(playerRight); isMoving = true; }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) { moveDirection.add(playerRight); isMoving = true; }

        if (moveDirection.length() > 0) {
            moveDirection.normalize();
            Vector3f newPosition = new Vector3f(playerPosition).add(moveDirection.mul(speed));
            playerPosition.set(newPosition);
        }

        if (glfwGetKey(window, GLFW_KEY_C) == GLFW_PRESS && !keyProcessed[GLFW_KEY_C]) {
            currentCameraMode = currentCameraMode == CameraMode.FIRST_PERSON ? CameraMode.THIRD_PERSON : CameraMode.FIRST_PERSON;
            keyProcessed[GLFW_KEY_C] = true;
            System.out.println("Камера: " + currentCameraMode);
        }

        for (int i = 0; i < 512; i++) {
            if (glfwGetKey(window, i) == GLFW_RELEASE) keyProcessed[i] = false;
        }
    }

    private void processEditorInput() {
        float speed = 50.0f * deltaTime;
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) speed *= 3.0f;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) editorCameraPitch = Math.min(editorCameraPitch + 1.0f, 89.0f);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) editorCameraPitch = Math.max(editorCameraPitch - 1.0f, -89.0f);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) editorCameraYaw -= 2.0f;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) editorCameraYaw += 2.0f;
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) editorCameraDistance = Math.max(editorCameraDistance - speed, 5.0f);
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) editorCameraDistance = Math.min(editorCameraDistance + speed, 200.0f);

        if (glfwGetKey(window, GLFW_KEY_DELETE) == GLFW_PRESS && !keyProcessed[GLFW_KEY_DELETE]) {
            if (selectedBuilding >= 0) {
                buildings.remove(selectedBuilding);
                buildingPositions.remove(selectedBuilding);
                buildingScales.remove(selectedBuilding);
                selectedBuilding = -1;
                System.out.println("Объект удален");
            }
            keyProcessed[GLFW_KEY_DELETE] = true;
        }

        if (glfwGetKey(window, GLFW_KEY_N) == GLFW_PRESS && !keyProcessed[GLFW_KEY_N]) {
            Random rand = new Random();
            float x = playerPosition.x + (rand.nextFloat() - 0.5f) * 20.0f;
            float z = playerPosition.z + (rand.nextFloat() - 0.5f) * 20.0f;
            float width = 5.0f + rand.nextFloat() * 8.0f;
            float height = 8.0f + rand.nextFloat() * 20.0f;
            float depth = 5.0f + rand.nextFloat() * 8.0f;
            Vector3f pos = new Vector3f(x, height / 2.0f, z);
            Vector3f scale = new Vector3f(width, height, depth);
            buildingPositions.add(pos);
            buildingScales.add(scale);
            buildings.add(new Building(pos, scale));
            selectedBuilding = buildingPositions.size() - 1;
            System.out.println("Создано здание");
            keyProcessed[GLFW_KEY_N] = true;
        }

        for (int i = 0; i < 512; i++) {
            if (glfwGetKey(window, i) == GLFW_RELEASE) keyProcessed[i] = false;
        }
    }

    private boolean checkCollision(Vector3f position) {
        float minX = position.x - playerRadius, maxX = position.x + playerRadius;
        float minY = position.y, maxY = position.y + playerHeight;
        float minZ = position.z - playerRadius, maxZ = position.z + playerRadius;
        for (Building b : buildings) {
            if (b.intersects(minX, maxX, minY, maxY, minZ, maxZ)) return true;
        }
        return false;
    }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (editorMode) {
                if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS && selectedBuilding >= 0) {
                    handleObjectDrag((float)x, (float)y);
                }
                return;
            }
            if (!mouseCaptured) return;
            if (firstMouse) { lastX = (float) x; lastY = (float) y; firstMouse = false; }
            float xOffset = ((float) x - lastX) * 0.15f;
            float yOffset = (lastY - (float) y) * 0.15f;
            lastX = (float) x; lastY = (float) y;
            playerYaw += xOffset;
            playerPitch = Math.max(-89.0f, Math.min(89.0f, playerPitch + yOffset));
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (editorMode && button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    handleEditorClick();
                } else if (action == GLFW_RELEASE) {
                    isDragging = false;
                }
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (editorMode) {
                editorCameraDistance = (float) Math.max(5.0f, Math.min(200.0f, editorCameraDistance - yoffset * 2.0f));
            }
        });
    }

    private void handleEditorClick() {
        double[] mx = new double[1], my = new double[1];
        glfwGetCursorPos(window, mx, my);
        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        Ray ray = castRay((float)mx[0], (float)my[1], width[0], height[0]);

        float closestDist = Float.MAX_VALUE;
        int closestBuilding = -1;
        for (int i = 0; i < buildings.size(); i++) {
            float dist = buildings.get(i).intersectRay(ray);
            if (dist > 0 && dist < closestDist) {
                closestDist = dist;
                closestBuilding = i;
            }
        }
        if (closestBuilding >= 0) {
            selectedBuilding = closestBuilding;
            isDragging = true;
            gizmoStartMouse.set((float)mx[0], (float)my[0]);
            System.out.println("Выбран объект: " + closestBuilding);
        } else {
            selectedBuilding = -1;
        }
    }

    private void handleObjectDrag(float mouseX, float mouseY) {
        if (!isDragging || selectedBuilding < 0) return;
        float sensitivity = 0.05f;
        Vector2f delta = new Vector2f(mouseX - gizmoStartMouse.x, mouseY - gizmoStartMouse.y);
        Vector3f pos = buildingPositions.get(selectedBuilding);
        pos.x += delta.x * sensitivity;
        pos.z -= delta.y * sensitivity;
        buildings.get(selectedBuilding).setPosition(pos);
        gizmoStartMouse.set(mouseX, mouseY);
    }

    private Ray castRay(float mouseX, float mouseY, int screenWidth, int screenHeight) {
        float ndcX = (2.0f * mouseX) / screenWidth - 1.0f;
        float ndcY = 1.0f - (2.0f * mouseY) / screenHeight;
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60.0f), (float) screenWidth / screenHeight, 0.1f, 1000.0f);
        Matrix4f view = new Matrix4f().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);
        Matrix4f invProj = new Matrix4f(projection).invert();
        Matrix4f invView = new Matrix4f(view).invert();
        Vector4f rayClip = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
        Vector4f rayEye = new Vector4f(rayClip).mul(invProj);
        rayEye.z = -1.0f; rayEye.w = 0.0f;
        Vector4f rayWorld = new Vector4f(rayEye).mul(invView);
        Vector3f rayDir = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();
        return new Ray(cameraPos, rayDir);
    }

    private void createShaderProgram() {
        String vertexSource =
                "#version 330 core\n" +
                        "layout (location = 0) in vec3 aPos;\n" +
                        "layout (location = 1) in vec3 aNormal;\n" +
                        "layout (location = 2) in vec2 aTexCoord;\n" +
                        "layout (location = 3) in ivec4 aBoneIds;\n" +
                        "layout (location = 4) in vec4 aBoneWeights;\n" +
                        "out vec3 FragPos;\n" +
                        "out vec3 Normal;\n" +
                        "out vec2 TexCoord;\n" +
                        "uniform mat4 model;\n" +
                        "uniform mat4 view;\n" +
                        "uniform mat4 projection;\n" +
                        "uniform mat4 boneMatrices[64];\n" +
                        "void main() {\n" +
                        "    mat4 boneTransform = mat4(0.0);\n" +
                        "    for (int i = 0; i < 4; i++) {\n" +
                        "        if (aBoneIds[i] >= 0) {\n" +
                        "            boneTransform += aBoneWeights[i] * boneMatrices[aBoneIds[i]];\n" +
                        "        }\n" +
                        "    }\n" +
                        "    vec4 localPos = boneTransform * vec4(aPos, 1.0);\n" +
                        "    vec4 worldPos = model * localPos;\n" +
                        "    FragPos = vec3(worldPos);\n" +
                        "    Normal = mat3(transpose(inverse(model))) * mat3(boneTransform) * aNormal;\n" +
                        "    TexCoord = aTexCoord;\n" +
                        "    gl_Position = projection * view * worldPos;\n" +
                        "}";

        String fragmentSource =
                "#version 330 core\n" +
                        "out vec4 FragColor;\n" +
                        "in vec3 Normal;\n" +
                        "in vec3 FragPos;\n" +
                        "in vec2 TexCoord;\n" +
                        "uniform vec3 objectColor;\n" +
                        "uniform vec3 lightPos;\n" +
                        "uniform sampler2D texture1;\n" +
                        "uniform bool useTexture;\n" +
                        "void main() {\n" +
                        "    float ambientStrength = 0.5;\n" +
                        "    vec3 ambient = ambientStrength * vec3(1.0);\n" +
                        "    vec3 norm = normalize(Normal);\n" +
                        "    vec3 lightDir = normalize(lightPos - FragPos);\n" +
                        "    float diff = max(dot(norm, lightDir), 0.0);\n" +
                        "    vec3 diffuse = diff * vec3(1.0, 0.95, 0.9);\n" +
                        "    vec3 resultColor = objectColor;\n" +
                        "    if (useTexture) {\n" +
                        "        resultColor = texture(texture1, TexCoord).rgb;\n" +
                        "    }\n" +
                        "    FragColor = vec4((ambient + diffuse) * resultColor, 1.0);\n" +
                        "}";

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertexSource);
        glCompileShader(vs);
        int[] success = new int[1];
        glGetShaderiv(vs, GL_COMPILE_STATUS, success);
        if (success[0] == 0) System.err.println("VS error: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragmentSource);
        glCompileShader(fs);
        glGetShaderiv(fs, GL_COMPILE_STATUS, success);
        if (success[0] == 0) System.err.println("FS error: " + glGetShaderInfoLog(fs));

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);
        glGetProgramiv(shaderProgram, GL_LINK_STATUS, success);
        if (success[0] == 0) System.err.println("Link error: " + glGetProgramInfoLog(shaderProgram));

        uniModel = glGetUniformLocation(shaderProgram, "model");
        uniView = glGetUniformLocation(shaderProgram, "view");
        uniProjection = glGetUniformLocation(shaderProgram, "projection");
        uniColor = glGetUniformLocation(shaderProgram, "objectColor");
        uniLightPos = glGetUniformLocation(shaderProgram, "lightPos");
        uniBoneMatrices = glGetUniformLocation(shaderProgram, "boneMatrices");
        uniUseTexture = glGetUniformLocation(shaderProgram, "useTexture");
        uniTexture = glGetUniformLocation(shaderProgram, "texture1");

        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    public boolean renderEditor() {
        return render();
    }

    public void cleanup() {
        if (shaderProgram != 0) glDeleteProgram(shaderProgram);
        if (terrainVAO != 0) glDeleteVertexArrays(terrainVAO);
        if (cubeVAO != 0) glDeleteVertexArrays(cubeVAO);
        if (playerTexture != null) playerTexture.cleanup();
        System.out.println("World3D очищен");
    }

    public boolean isShouldClose() {
        return shouldClose;
    }
}
