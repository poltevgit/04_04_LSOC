package graphics;

import graphics.Mesh;
import graphics.ModelLoader;
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

    public enum CameraMode {
        FIRST_PERSON,
        THIRD_PERSON
    }

    private CameraMode currentCameraMode = CameraMode.THIRD_PERSON;

    private Vector3f playerPosition = new Vector3f(0.0f, 1.7f, 0.0f);
    private float playerYaw = -90.0f;
    private float playerPitch = 0.0f;

    private Vector3f cameraPos = new Vector3f(0.0f, 0.0f, 0.0f);
    private Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float lastX = 640, lastY = 360;
    private boolean firstMouse = true;
    private boolean mouseCaptured = true;

    private float deltaTime = 0.0f;
    private float lastFrame = 0.0f;

    private int terrainVAO, cubeVAO;
    private List<Building> buildings = new ArrayList<>();
    private List<Vector3f> buildingPositions = new ArrayList<>();
    private List<Vector3f> buildingScales = new ArrayList<>();

    private boolean shouldClose = false;
    private boolean[] keyProcessed = new boolean[512];

    private float weaponScale = 0.015f;
    private boolean weaponLoaded = false;

    private float playerHeight = 1.7f;
    private float playerRadius = 0.4f;

    private boolean editorMode = false;
    private int selectedBuilding = -1;
    private GizmoMode currentGizmoMode = GizmoMode.TRANSLATE;
    private boolean isDragging = false;
    private Vector3f gizmoStartPos = new Vector3f();
    private Vector2f gizmoStartMouse = new Vector2f();

    private enum GizmoMode { TRANSLATE, ROTATE, SCALE }

    private float editorCameraDistance = 20.0f;
    private float editorCameraYaw = 45.0f;
    private float editorCameraPitch = -30.0f;

    public World3D(long window) {
        this.window = window;
    }

    public void enableEditorMode() {
        editorMode = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        mouseCaptured = false;
    }

    public void init() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);

        createShaderProgram();
        setupTerrainGeometry();
        setupCubeGeometry();
        generateRandomCity();

        try {
            playerWeapon = ModelLoader.loadModel("models/FBX/SM_Rifle.fbx");
            weaponLoaded = true;
            System.out.println("Модель винтовки успешно загружена!");
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке модели: " + e.getMessage());
            weaponLoaded = false;
        }

        if (!editorMode) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
        setupCallbacks();
    }

    private void generateRandomCity() {
        Random rand = new Random();
        int citySize = 60;

        for (int i = 0; i < citySize; i++) {
            float x = (rand.nextFloat() - 0.5f) * 300.0f;
            float z = (rand.nextFloat() - 0.5f) * 300.0f;
            float width = 8.0f + rand.nextFloat() * 10.0f;
            float height = 15.0f + rand.nextFloat() * 60.0f;
            float depth = 8.0f + rand.nextFloat() * 10.0f;

            Vector3f pos = new Vector3f(x, height / 2.0f, z);
            Vector3f scale = new Vector3f(width, height, depth);

            buildingPositions.add(pos);
            buildingScales.add(scale);
            buildings.add(new Building(pos, scale));
        }
    }

    private void setupTerrainGeometry() {
        float size = 500.0f;
        float[] vertices = {
                -size, 0, -size,    0, 1, 0,
                size, 0, -size,    0, 1, 0,
                size, 0,  size,    0, 1, 0,
                -size, 0, -size,    0, 1, 0,
                size, 0,  size,    0, 1, 0,
                -size, 0,  size,    0, 1, 0
        };

        terrainVAO = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(terrainVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
    }

    private void setupCubeGeometry() {
        float[] vertices = {
                // Front face
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,

                // Back face
                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,

                // Left face
                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f,  0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,

                // Right face
                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
                0.5f,  0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,

                // Bottom face
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,

                // Top face
                -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
                -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f
        };

        cubeVAO = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(cubeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
    }

    private void updateCamera() {
        Vector3f playerFront = new Vector3f(
                (float)(Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch))),
                (float)Math.sin(Math.toRadians(playerPitch)),
                (float)(Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)))
        ).normalize();

        if (editorMode) {
            float camX = playerPosition.x + (float)(editorCameraDistance * Math.cos(Math.toRadians(editorCameraYaw)) * Math.cos(Math.toRadians(editorCameraPitch)));
            float camY = playerPosition.y + (float)(editorCameraDistance * Math.sin(Math.toRadians(editorCameraPitch)));
            float camZ = playerPosition.z + (float)(editorCameraDistance * Math.sin(Math.toRadians(editorCameraYaw)) * Math.cos(Math.toRadians(editorCameraPitch)));
            cameraPos.set(camX, Math.max(camY, 2.0f), camZ);
            cameraFront.set(playerPosition).sub(cameraPos).normalize();
            cameraUp.set(0.0f, 1.0f, 0.0f);
        } else if (currentCameraMode == CameraMode.FIRST_PERSON) {
            cameraPos.set(playerPosition);
            cameraPos.y += 1.7f;
            cameraFront.set(playerFront);
            cameraUp.set(0.0f, 1.0f, 0.0f);
        } else {
            float distance = 8.0f;
            Vector3f offset = new Vector3f(playerFront).mul(-distance);
            cameraPos.set(playerPosition);
            cameraPos.y += 2.0f;
            cameraPos.add(offset);
            cameraFront.set(playerFront);
            cameraUp.set(0.0f, 1.0f, 0.0f);
        }
    }

    public boolean render() {
        float currentFrame = (float) glfwGetTime();
        deltaTime = currentFrame - lastFrame;
        lastFrame = currentFrame;

        processInput();
        updateCamera();

        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        glViewport(0, 0, width[0], height[0]);

        glClearColor(0.1f, 0.2f, 0.3f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60.0f), (float) width[0] / height[0], 0.1f, 1000.0f);
        Matrix4f view = new Matrix4f().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);

        glUniformMatrix4fv(uniProjection, false, projection.get(new float[16]));
        glUniformMatrix4fv(uniView, false, view.get(new float[16]));
        glUniform3f(uniLightPos, 100.0f, 200.0f, 50.0f);

        // Terrain
        glBindVertexArray(terrainVAO);
        Matrix4f terrainModel = new Matrix4f();
        glUniformMatrix4fv(uniModel, false, terrainModel.get(new float[16]));
        glUniform3f(uniColor, 0.2f, 0.5f, 0.2f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Buildings
        glBindVertexArray(cubeVAO);
        for (int i = 0; i < buildingPositions.size(); i++) {
            Matrix4f model = new Matrix4f()
                    .translate(buildingPositions.get(i))
                    .scale(buildingScales.get(i));
            glUniformMatrix4fv(uniModel, false, model.get(new float[16]));
            if (i % 2 == 0) glUniform3f(uniColor, 0.5f, 0.5f, 0.55f);
            else glUniform3f(uniColor, 0.45f, 0.45f, 0.5f);
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        drawPlayerModel();

        if (currentCameraMode == CameraMode.FIRST_PERSON && weaponLoaded && playerWeapon != null) {
            drawFirstPersonWeapon(width[0], height[0], projection, view);
        }

        glfwSwapBuffers(window);
        return !glfwWindowShouldClose(window) && !shouldClose;
    }

    public boolean renderEditor() {
        float currentFrame = (float) glfwGetTime();
        deltaTime = currentFrame - lastFrame;
        lastFrame = currentFrame;

        processEditorInput();
        updateCamera();

        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        glViewport(0, 0, width[0], height[0]);

        glClearColor(0.15f, 0.18f, 0.22f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60.0f), (float) width[0] / height[0], 0.1f, 1000.0f);
        Matrix4f view = new Matrix4f().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);

        glUniformMatrix4fv(uniProjection, false, projection.get(new float[16]));
        glUniformMatrix4fv(uniView, false, view.get(new float[16]));
        glUniform3f(uniLightPos, 100.0f, 200.0f, 50.0f);

        // Terrain
        glBindVertexArray(terrainVAO);
        Matrix4f terrainModel = new Matrix4f();
        glUniformMatrix4fv(uniModel, false, terrainModel.get(new float[16]));
        glUniform3f(uniColor, 0.2f, 0.5f, 0.2f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Buildings
        glBindVertexArray(cubeVAO);
        for (int i = 0; i < buildingPositions.size(); i++) {
            Matrix4f model = new Matrix4f()
                    .translate(buildingPositions.get(i))
                    .scale(buildingScales.get(i));
            glUniformMatrix4fv(uniModel, false, model.get(new float[16]));

            if (i == selectedBuilding) {
                glUniform3f(uniColor, 1.0f, 0.8f, 0.2f);
            } else {
                glUniform3f(uniColor, 0.5f, 0.5f, 0.55f);
            }
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        if (selectedBuilding >= 0) {
            drawSelectedOutline(buildingPositions.get(selectedBuilding), buildingScales.get(selectedBuilding));
        }

        glfwSwapBuffers(window);
        return !glfwWindowShouldClose(window) && !shouldClose;
    }

    private void drawSelectedOutline(Vector3f position, Vector3f scale) {
        // Рисуем подсветку выбранного объекта (увеличенный масштаб с прозрачностью)
        glBindVertexArray(cubeVAO);
        Matrix4f outlineModel = new Matrix4f()
                .translate(position)
                .scale(scale.x * 1.05f, scale.y * 1.05f, scale.z * 1.05f);
        glUniformMatrix4fv(uniModel, false, outlineModel.get(new float[16]));
        glUniform3f(uniColor, 1.0f, 0.8f, 0.2f);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    private void processEditorInput() {
        float speed = 50.0f * deltaTime;

        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            speed *= 3.0f;
        }

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            editorCameraPitch = Math.min(editorCameraPitch + 1.0f, 89.0f);
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            editorCameraPitch = Math.max(editorCameraPitch - 1.0f, -89.0f);
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            editorCameraYaw -= 2.0f;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            editorCameraYaw += 2.0f;
        }
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
            editorCameraDistance = Math.max(editorCameraDistance - speed, 5.0f);
        }
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
            editorCameraDistance = Math.min(editorCameraDistance + speed, 200.0f);
        }

        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS && !keyProcessed[GLFW_KEY_1]) {
            currentGizmoMode = GizmoMode.TRANSLATE;
            System.out.println("Режим: Перемещение");
            keyProcessed[GLFW_KEY_1] = true;
        }
        if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS && !keyProcessed[GLFW_KEY_2]) {
            currentGizmoMode = GizmoMode.ROTATE;
            System.out.println("Режим: Вращение");
            keyProcessed[GLFW_KEY_2] = true;
        }
        if (glfwGetKey(window, GLFW_KEY_3) == GLFW_PRESS && !keyProcessed[GLFW_KEY_3]) {
            currentGizmoMode = GizmoMode.SCALE;
            System.out.println("Режим: Масштабирование");
            keyProcessed[GLFW_KEY_3] = true;
        }

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
            float x = (rand.nextFloat() - 0.5f) * 100.0f;
            float z = (rand.nextFloat() - 0.5f) * 100.0f;
            float width = 8.0f + rand.nextFloat() * 10.0f;
            float height = 15.0f + rand.nextFloat() * 40.0f;
            float depth = 8.0f + rand.nextFloat() * 10.0f;

            Vector3f pos = new Vector3f(x, height / 2.0f, z);
            Vector3f scale = new Vector3f(width, height, depth);

            buildingPositions.add(pos);
            buildingScales.add(scale);
            buildings.add(new Building(pos, scale));
            selectedBuilding = buildingPositions.size() - 1;
            System.out.println("Создан новый объект");
            keyProcessed[GLFW_KEY_N] = true;
        }

        for (int i = 0; i < 512; i++) {
            if (glfwGetKey(window, i) == GLFW_RELEASE) {
                keyProcessed[i] = false;
            }
        }
    }

    private void drawFirstPersonWeapon(int screenWidth, int screenHeight, Matrix4f originalProjection, Matrix4f originalView) {
        float[] origProj = originalProjection.get(new float[16]);
        float[] origView = originalView.get(new float[16]);

        glDisable(GL_DEPTH_TEST);

        Matrix4f weaponProjection = new Matrix4f().perspective((float) Math.toRadians(75.0f), (float) screenWidth / screenHeight, 0.01f, 10.0f);
        glUniformMatrix4fv(uniProjection, false, weaponProjection.get(new float[16]));

        Matrix4f weaponView = new Matrix4f().lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1), cameraUp);
        glUniformMatrix4fv(uniView, false, weaponView.get(new float[16]));

        Matrix4f weaponModel = new Matrix4f();
        weaponModel.translate(0.5f, -0.5f, -0.8f);
        weaponModel.rotateX((float) Math.toRadians(-5.0f));
        weaponModel.rotateY((float) Math.toRadians(180.0f));
        weaponModel.rotateZ((float) Math.toRadians(0.0f));
        weaponModel.scale(weaponScale);

        glUniformMatrix4fv(uniModel, false, weaponModel.get(new float[16]));
        glUniform3f(uniColor, 0.6f, 0.6f, 0.65f);

        playerWeapon.render();

        glUniformMatrix4fv(uniProjection, false, origProj);
        glUniformMatrix4fv(uniView, false, origView);
        glEnable(GL_DEPTH_TEST);
    }

    private void drawPlayerModel() {
        glBindVertexArray(cubeVAO);

        if (currentCameraMode == CameraMode.FIRST_PERSON) {
            return;
        }

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

        if (weaponLoaded && playerWeapon != null) {
            Vector3f weaponOffset = new Vector3f(
                    (float)Math.cos(Math.toRadians(playerYaw)) * 0.6f,
                    0,
                    (float)Math.sin(Math.toRadians(playerYaw)) * 0.6f
            );

            Matrix4f weaponModel = new Matrix4f()
                    .translate(playerPosition.x + weaponOffset.x, playerPosition.y + 1.1f, playerPosition.z + weaponOffset.z)
                    .rotateX((float) Math.toRadians(10))
                    .rotateY((float) Math.toRadians(playerYaw + 90))
                    .rotateZ((float) Math.toRadians(-5))
                    .scale(weaponScale * 1.5f);
            glUniformMatrix4fv(uniModel, false, weaponModel.get(new float[16]));
            glUniform3f(uniColor, 0.4f, 0.4f, 0.45f);
            playerWeapon.render();
        }
    }

    private void processInput() {
        float speed = 25.0f * deltaTime;

        Vector3f playerFront = new Vector3f(
                (float)(Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch))),
                0,
                (float)(Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)))
        ).normalize();

        Vector3f playerRight = new Vector3f(playerFront).cross(cameraUp).normalize();

        Vector3f moveDirection = new Vector3f();

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            moveDirection.add(playerFront);
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            moveDirection.sub(playerFront);
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            moveDirection.sub(playerRight);
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            moveDirection.add(playerRight);
        }

        if (moveDirection.length() > 0) {
            moveDirection.normalize();

            Vector3f newPosition = new Vector3f(playerPosition);
            newPosition.add(new Vector3f(moveDirection).mul(speed));

            newPosition.x = Math.max(-240, Math.min(240, newPosition.x));
            newPosition.z = Math.max(-240, Math.min(240, newPosition.z));

            if (!checkCollision(newPosition)) {
                playerPosition.set(newPosition);
            } else {
                Vector3f slideX = new Vector3f(newPosition.x, playerPosition.y, playerPosition.z);
                Vector3f slideZ = new Vector3f(playerPosition.x, playerPosition.y, newPosition.z);

                boolean collidedX = checkCollision(slideX);
                boolean collidedZ = checkCollision(slideZ);

                if (!collidedX) {
                    playerPosition.x = slideX.x;
                    playerPosition.z = slideX.z;
                } else if (!collidedZ) {
                    playerPosition.x = slideZ.x;
                    playerPosition.z = slideZ.z;
                }
            }
        }

        playerPosition.y = 1.7f;

        if (glfwGetKey(window, GLFW_KEY_C) == GLFW_PRESS && !keyProcessed[GLFW_KEY_C]) {
            if (currentCameraMode == CameraMode.FIRST_PERSON) {
                currentCameraMode = CameraMode.THIRD_PERSON;
                System.out.println("Вид от третьего лица");
            } else {
                currentCameraMode = CameraMode.FIRST_PERSON;
                System.out.println("Вид от первого лица");
            }
            keyProcessed[GLFW_KEY_C] = true;
        }

        if (glfwGetKey(window, GLFW_KEY_KP_ADD) == GLFW_PRESS && !keyProcessed[GLFW_KEY_KP_ADD]) {
            weaponScale += 0.001f;
            System.out.println("Масштаб оружия: " + weaponScale);
            keyProcessed[GLFW_KEY_KP_ADD] = true;
        }
        if (glfwGetKey(window, GLFW_KEY_KP_SUBTRACT) == GLFW_PRESS && !keyProcessed[GLFW_KEY_KP_SUBTRACT]) {
            weaponScale -= 0.001f;
            if (weaponScale < 0.001f) weaponScale = 0.001f;
            System.out.println("Масштаб оружия: " + weaponScale);
            keyProcessed[GLFW_KEY_KP_SUBTRACT] = true;
        }

        for (int i = 0; i < 512; i++) {
            if (glfwGetKey(window, i) == GLFW_RELEASE) {
                keyProcessed[i] = false;
            }
        }
    }

    private boolean checkCollision(Vector3f position) {
        float playerMinX = position.x - playerRadius;
        float playerMaxX = position.x + playerRadius;
        float playerMinY = position.y - playerHeight + 0.1f;
        float playerMaxY = position.y;
        float playerMinZ = position.z - playerRadius;
        float playerMaxZ = position.z + playerRadius;

        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.intersects(playerMinX, playerMaxX, playerMinY, playerMaxY, playerMinZ, playerMaxZ)) {
                return true;
            }
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

            if (firstMouse) {
                lastX = (float) x;
                lastY = (float) y;
                firstMouse = false;
            }

            float xOffset = (float) x - lastX;
            float yOffset = lastY - (float) y;
            lastX = (float) x;
            lastY = (float) y;

            float sensitivity = 0.15f;
            xOffset *= sensitivity;
            yOffset *= sensitivity;

            playerYaw += xOffset;
            playerPitch += yOffset;

            if (playerPitch > 89.0f) playerPitch = 89.0f;
            if (playerPitch < -89.0f) playerPitch = -89.0f;
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
                editorCameraDistance = (float)Math.max(5.0f, Math.min(200.0f, editorCameraDistance - yoffset * 2.0f));
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
            isDragging = false;
        }
    }

    private void handleObjectDrag(float mouseX, float mouseY) {
        if (!isDragging || selectedBuilding < 0) return;

        float sensitivity = 0.05f;
        Vector2f delta = new Vector2f(mouseX - gizmoStartMouse.x, mouseY - gizmoStartMouse.y);

        Vector3f pos = buildingPositions.get(selectedBuilding);

        // Перемещение по X и Z
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
        rayEye.z = -1.0f;
        rayEye.w = 0.0f;

        Vector4f rayWorld = new Vector4f(rayEye).mul(invView);
        Vector3f rayDir = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();

        return new Ray(cameraPos, rayDir);
    }

    private void createShaderProgram() {
        String vertexSource =
                "#version 330 core\n" +
                        "layout (location = 0) in vec3 aPos;\n" +
                        "layout (location = 1) in vec3 aNormal;\n" +
                        "out vec3 FragPos;\n" +
                        "out vec3 Normal;\n" +
                        "uniform mat4 model; uniform mat4 view; uniform mat4 projection;\n" +
                        "void main() {\n" +
                        "    FragPos = vec3(model * vec4(aPos, 1.0));\n" +
                        "    Normal = mat3(transpose(inverse(model))) * aNormal;\n" +
                        "    gl_Position = projection * view * vec4(FragPos, 1.0);\n" +
                        "}";

        String fragmentSource =
                "#version 330 core\n" +
                        "out vec4 FragColor;\n" +
                        "in vec3 Normal;\n" +
                        "in vec3 FragPos;\n" +
                        "uniform vec3 objectColor;\n" +
                        "uniform vec3 lightPos;\n" +
                        "void main() {\n" +
                        "    float ambientStrength = 0.3;\n" +
                        "    vec3 ambient = ambientStrength * vec3(1.0, 1.0, 1.0);\n" +
                        "    vec3 norm = normalize(Normal);\n" +
                        "    vec3 lightDir = normalize(lightPos - FragPos);\n" +
                        "    float diff = max(dot(norm, lightDir), 0.0);\n" +
                        "    vec3 diffuse = diff * vec3(1.0, 1.0, 0.9);\n" +
                        "    vec4 result = vec4((ambient + diffuse) * objectColor, 1.0);\n" +
                        "    FragColor = result;\n" +
                        "}";

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);

        int[] success = new int[1];
        glGetShaderiv(vertexShader, GL_COMPILE_STATUS, success);
        if (success[0] == 0) {
            String log = glGetShaderInfoLog(vertexShader);
            System.err.println("Vertex shader compilation failed: " + log);
        }

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);

        glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, success);
        if (success[0] == 0) {
            String log = glGetShaderInfoLog(fragmentShader);
            System.err.println("Fragment shader compilation failed: " + log);
        }

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);

        glGetProgramiv(shaderProgram, GL_LINK_STATUS, success);
        if (success[0] == 0) {
            String log = glGetProgramInfoLog(shaderProgram);
            System.err.println("Shader program linking failed: " + log);
        }

        uniModel = glGetUniformLocation(shaderProgram, "model");
        uniView = glGetUniformLocation(shaderProgram, "view");
        uniProjection = glGetUniformLocation(shaderProgram, "projection");
        uniColor = glGetUniformLocation(shaderProgram, "objectColor");
        uniLightPos = glGetUniformLocation(shaderProgram, "lightPos");

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    public void cleanup() {
        if (shaderProgram != 0) {
            glDeleteProgram(shaderProgram);
            shaderProgram = 0;
        }
        if (terrainVAO != 0) {
            glDeleteVertexArrays(terrainVAO);
            terrainVAO = 0;
        }
        if (cubeVAO != 0) {
            glDeleteVertexArrays(cubeVAO);
            cubeVAO = 0;
        }
        System.out.println("World3D cleaned up");
    }

    public boolean isShouldClose() {
        return shouldClose;
    }
}