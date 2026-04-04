package graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.nio.FloatBuffer;
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
        FIRST_PERSON,   // 1 лицо
        THIRD_PERSON    // 3 лицо
    }

    private CameraMode currentCameraMode = CameraMode.THIRD_PERSON;

    // Позиция игрока
    private Vector3f playerPosition = new Vector3f(0.0f, 0.0f, 0.0f);
    private float playerYaw = -90.0f;
    private float playerPitch = 0.0f;

    // Параметры камеры
    private Vector3f cameraPos = new Vector3f(0.0f, 0.0f, 0.0f);
    private Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float lastX = 640, lastY = 360;
    private boolean firstMouse = true;
    private boolean mouseCaptured = true;

    private float deltaTime = 0.0f;
    private float lastFrame = 0.0f;

    private int terrainVAO, cubeVAO;
    private List<Vector3f> buildingPositions = new ArrayList<>();
    private List<Vector3f> buildingScales = new ArrayList<>();

    private boolean shouldClose = false;
    private boolean[] keyProcessed = new boolean[256];

    private float weaponScale = 0.015f;
    private boolean weaponLoaded = false;

    public World3D(long window) {
        this.window = window;
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

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
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
            buildingPositions.add(new Vector3f(x, height / 2.0f, z));
            buildingScales.add(new Vector3f(width, height, width));
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
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,

                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,

                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f,  0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f, -0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
                -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,

                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
                0.5f,  0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
                0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,

                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,

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
        // Направление взгляда игрока
        Vector3f playerFront = new Vector3f(
                (float)(Math.cos(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch))),
                (float)Math.sin(Math.toRadians(playerPitch)),
                (float)(Math.sin(Math.toRadians(playerYaw)) * Math.cos(Math.toRadians(playerPitch)))
        ).normalize();

        if (currentCameraMode == CameraMode.FIRST_PERSON) {
            // Камера в глазах игрока
            cameraPos.set(playerPosition);
            cameraPos.y += 1.7f;
            cameraFront.set(playerFront);
            cameraUp.set(0.0f, 1.0f, 0.0f);
        } else {
            // Камера сзади игрока (3 лицо)
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

        // Рисуем траву
        glBindVertexArray(terrainVAO);
        Matrix4f terrainModel = new Matrix4f();
        glUniformMatrix4fv(uniModel, false, terrainModel.get(new float[16]));
        glUniform3f(uniColor, 0.2f, 0.5f, 0.2f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Рисуем здания
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

        // Рисуем модель игрока (только в 3 лице)
        drawPlayerModel();

        // Рисуем оружие (только в 1 лице)
        if (currentCameraMode == CameraMode.FIRST_PERSON && weaponLoaded && playerWeapon != null) {
            drawFirstPersonWeapon(width[0], height[0], projection, view);
        }

        glfwSwapBuffers(window);
        return !glfwWindowShouldClose(window) && !shouldClose;
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
        weaponModel.translate(0.6f, -0.7f, -0.9f);
        weaponModel.rotateZ((float) Math.toRadians(0.0f));
        weaponModel.rotateY((float) Math.toRadians(0.0f));
        weaponModel.rotateX((float) Math.toRadians(180.0f));
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

        // В 1 лице не рисуем тело
        if (currentCameraMode == CameraMode.FIRST_PERSON) {
            return;
        }

        // Тело игрока
        Matrix4f bodyModel = new Matrix4f()
                .translate(playerPosition.x, playerPosition.y + 0.9f, playerPosition.z)
                .rotateY((float) Math.toRadians(playerYaw + 90))
                .scale(0.8f, 1.6f, 0.6f);

        glUniformMatrix4fv(uniModel, false, bodyModel.get(new float[16]));
        glUniform3f(uniColor, 0.2f, 0.6f, 0.9f);
        glDrawArrays(GL_TRIANGLES, 0, 36);

        // Голова
        Matrix4f headModel = new Matrix4f()
                .translate(playerPosition.x, playerPosition.y + 1.8f, playerPosition.z)
                .scale(0.6f);
        glUniformMatrix4fv(uniModel, false, headModel.get(new float[16]));
        glUniform3f(uniColor, 0.9f, 0.7f, 0.5f);
        glDrawArrays(GL_TRIANGLES, 0, 36);

        // Оружие в руках (для 3 лица)
        if (weaponLoaded && playerWeapon != null) {
            Matrix4f weaponModel = new Matrix4f()
                    .translate(playerPosition.x + 0.6f, playerPosition.y + 1.1f, playerPosition.z + 0.4f)
                    .rotateX((float) Math.toRadians(20))
                    .rotateY((float) Math.toRadians(playerYaw + 90))
                    .rotateZ((float) Math.toRadians(-10))
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
            playerPosition.add(moveDirection.mul(speed));
        }

        // Границы мира
        playerPosition.x = Math.max(-240, Math.min(240, playerPosition.x));
        playerPosition.z = Math.max(-240, Math.min(240, playerPosition.z));

        // Переключение между 1 и 3 лицом по клавише C
        if (glfwGetKey(window, GLFW_KEY_C) == GLFW_PRESS && !keyProcessed['C']) {
            if (currentCameraMode == CameraMode.FIRST_PERSON) {
                currentCameraMode = CameraMode.THIRD_PERSON;
                System.out.println("Вид от третьего лица");
            } else {
                currentCameraMode = CameraMode.FIRST_PERSON;
                System.out.println("Вид от первого лица");
            }
            keyProcessed['C'] = true;
        }

        // Настройка масштаба оружия (+/- на цифровой клавиатуре)
        if (glfwGetKey(window, GLFW_KEY_KP_ADD) == GLFW_PRESS && !keyProcessed['+']) {
            weaponScale += 0.001f;
            System.out.println("Масштаб оружия: " + weaponScale);
            keyProcessed['+'] = true;
        }
        if (glfwGetKey(window, GLFW_KEY_KP_SUBTRACT) == GLFW_PRESS && !keyProcessed['-']) {
            weaponScale -= 0.001f;
            if (weaponScale < 0.001f) weaponScale = 0.001f;
            System.out.println("Масштаб оружия: " + weaponScale);
            keyProcessed['-'] = true;
        }

        // Сброс флагов клавиш
        for (int i = 0; i < 256; i++) {
            if (glfwGetKey(window, i) == GLFW_RELEASE) {
                keyProcessed[i] = false;
            }
        }
    }

    private void setupCallbacks() {
        glfwSetCursorPosCallback(window, (win, x, y) -> {
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

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);

        uniModel = glGetUniformLocation(shaderProgram, "model");
        uniView = glGetUniformLocation(shaderProgram, "view");
        uniProjection = glGetUniformLocation(shaderProgram, "projection");
        uniColor = glGetUniformLocation(shaderProgram, "objectColor");
        uniLightPos = glGetUniformLocation(shaderProgram, "lightPos");

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(terrainVAO);
        glDeleteVertexArrays(cubeVAO);
    }

    public boolean isShouldClose() { return shouldClose; }
}