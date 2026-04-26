package game;

import graphics.MenuTexture;
import graphics.World3D;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13C.GL_MULTISAMPLE;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class SimpleWindow {

    private long window;
    private GameState currentState = GameState.MENU;
    private World3D world3D;

    private MenuTexture bgTex, playTex, newGameTex, downloadTex, fsBgTex;
    private long arrowCursor;
    private long handCursor;

    private final int INIT_WINDOW_WIDTH = 800;
    private final int INIT_WINDOW_HEIGHT = 600;
    private final float PLAY_W = 240, PLAY_H = 120;
    private final float PLAY_Y = -100;
    private final float BTN_W = 220, BTN_H = 70;
    private final float BTN_X = 0;
    private final float NG_Y = -80;
    private final float DL_Y = -170;
    private boolean playHovered = false;
    private boolean newGameHovered = false;
    private boolean downloadHovered = false;
    private float playButtonScale = 1.0f;

    public void run() {
        init();
        mainLoop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        window = glfwCreateWindow(INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT, "Last Shot or Chance", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        centerWindow();
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        arrowCursor = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        handCursor = glfwCreateStandardCursor(GLFW_HAND_CURSOR);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_MULTISAMPLE);

        glfwShowWindow(window);

        setupInput();
        setupCursorCallback();
        loadTextures();

        System.out.println("=== ИГРА ЗАПУЩЕНА ===");
        System.out.println("OpenGL: " + glGetString(GL_VERSION));
        System.out.println("GPU: " + glGetString(GL_RENDERER));
    }

    private void centerWindow() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pW = stack.mallocInt(1);
            IntBuffer pH = stack.mallocInt(1);
            glfwGetWindowSize(window, pW, pH);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(window,
                        (vidMode.width() - pW.get(0)) / 2,
                        (vidMode.height() - pH.get(0)) / 2);
            }
        }
    }

    private void setupInput() {
        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (action == GLFW_RELEASE) {
                if (key == GLFW_KEY_ESCAPE) handleEscape();
                else if (key == GLFW_KEY_F11) toggleFullscreen();
            }
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) handleClick();
        });
    }

    private void setupCursorCallback() {
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (currentState == GameState.WORLD_3D || currentState == GameState.EDITOR) return;

            int[] winW = new int[1], winH = new int[1];
            glfwGetFramebufferSize(window, winW, winH);

            float fx = ((float) xpos - (winW[0] / 2.0f));
            float fy = ((winH[0] / 2.0f) - (float) ypos);

            if (currentState == GameState.MENU) {
                playHovered = isInside(fx, fy, -PLAY_W/2, PLAY_W/2, PLAY_Y + PLAY_H/2, PLAY_Y - PLAY_H/2);
            } else if (currentState == GameState.FULLSCREEN_MENU) {
                newGameHovered = isInside(fx, fy, BTN_X - BTN_W/2, BTN_X + BTN_W/2, NG_Y + BTN_H/2, NG_Y - BTN_H/2);
                downloadHovered = isInside(fx, fy, BTN_X - BTN_W/2, BTN_X + BTN_W/2, DL_Y + BTN_H/2, DL_Y - BTN_H/2);
            }

            boolean anyHovered = playHovered || newGameHovered || downloadHovered;
            glfwSetCursor(window, anyHovered ? handCursor : arrowCursor);
        });
    }

    // === НОВЫЙ МЕТОД: Полный сброс OpenGL состояния ===
    private void resetGLState() {
        // Отключаем fixed pipeline
        GL20.glUseProgram(0);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(1, 1, 1, 1);
    }

    // === НОВЫЙ МЕТОД: Настройка для 2D меню ===
    private void setup2DState() {
        resetGLState();

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glEnable(GL_TEXTURE_2D);
    }

    private void handleEscape() {
        if (currentState == GameState.MENU) {
            glfwSetWindowShouldClose(window, true);
        } else if (currentState == GameState.WORLD_3D || currentState == GameState.EDITOR) {
            if (world3D != null) {
                world3D.cleanup();
                world3D = null;
            }
            // ВАЖНО: Сначала сбрасываем GL состояние
            resetGLState();
            currentState = GameState.FULLSCREEN_MENU;
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            System.out.println("↩️ Возврат в меню");
        } else if (currentState == GameState.FULLSCREEN_MENU) {
            toWindowedMenu();
        }
    }

    private void toggleFullscreen() {
        if (currentState == GameState.MENU) {
            switchToFullscreen();
        } else if (currentState == GameState.FULLSCREEN_MENU) {
            toWindowedMenu();
        }
    }

    private void mainLoop() {
        while (!glfwWindowShouldClose(window)) {
            updateAnimation();

            if (currentState == GameState.MENU) {
                glfwMakeContextCurrent(window);
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                renderMenu();
                glfwSwapBuffers(window);
            } else if (currentState == GameState.FULLSCREEN_MENU) {
                glfwMakeContextCurrent(window);
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                renderFullscreenMenu();
                glfwSwapBuffers(window);
            } else if (currentState == GameState.WORLD_3D) {
                if (world3D != null) {
                    // World3D сам управляет буферами
                    if (!world3D.render()) {
                        handleEscape();
                    }
                }
            } else if (currentState == GameState.EDITOR) {
                if (world3D != null) {
                    if (!world3D.renderEditor()) {
                        handleEscape();
                    }
                }
            }
            glfwPollEvents();
        }
    }

    private void updateAnimation() {
        long currentTime = System.nanoTime();
        if (playHovered) {
            playButtonScale = 1.0f + (float) Math.sin(currentTime / 200_000_000.0) * 0.05f;
        } else {
            playButtonScale = 1.0f;
        }
    }

    private void loadTextures() {
        System.out.println("Загрузка текстур...");
        bgTex = load("img/background.png");
        playTex = load("img/play_button.png");
        newGameTex = load("img/newgame_button.png");
        downloadTex = load("img/download_button.png");
        fsBgTex = load("img/fullscreen_bg.png");
        System.out.println("Загрузка текстур завершена");
    }

    private MenuTexture load(String path) {
        try {
            return new MenuTexture(path);
        } catch (Exception e) {
            System.err.println("Ошибка загрузки: " + path);
            return null;
        }
    }

    private void setup2DProjection() {
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        glViewport(0, 0, w[0], h[0]);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-w[0]/2.0, w[0]/2.0, -h[0]/2.0, h[0]/2.0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void renderMenu() {
        setup2DState();
        int[] winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, winW, winH);
        setup2DProjection();

        drawQuad(bgTex, -winW[0]/2f, winW[0]/2f, winH[0]/2f, -winH[0]/2f);

        float scaledW = PLAY_W * playButtonScale;
        float scaledH = PLAY_H * playButtonScale;
        float alpha = playHovered ? 1.0f : 0.9f;
        glColor4f(1, 1, 1, alpha);
        drawQuad(playTex, -scaledW/2f, scaledW/2f, PLAY_Y + scaledH/2f, PLAY_Y - scaledH/2f);
        glColor4f(1, 1, 1, 1);
    }

    private void renderFullscreenMenu() {
        setup2DState();
        int[] winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, winW, winH);
        setup2DProjection();

        drawQuad(fsBgTex, -winW[0]/2f, winW[0]/2f, winH[0]/2f, -winH[0]/2f);
        drawButton(newGameTex, BTN_X, NG_Y, BTN_W, BTN_H, newGameHovered);
        drawButton(downloadTex, BTN_X, DL_Y, BTN_W, BTN_H, downloadHovered);
    }

    private void drawButton(MenuTexture tex, float x, float y, float w, float h, boolean hovered) {
        if (tex == null) return;
        float scale = hovered ? 1.05f : 1.0f;
        float alpha = hovered ? 1.0f : 0.85f;
        float scaledW = w * scale;
        float scaledH = h * scale;
        glColor4f(1, 1, 1, alpha);
        drawQuad(tex, x - scaledW/2f, x + scaledW/2f, y + scaledH/2f, y - scaledH/2f);
        glColor4f(1, 1, 1, 1);
    }

    private void drawQuad(MenuTexture tex, float left, float right, float top, float bottom) {
        if (tex != null && tex.isLoaded()) {
            tex.bind();
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(left, bottom);
            glTexCoord2f(1, 1); glVertex2f(right, bottom);
            glTexCoord2f(1, 0); glVertex2f(right, top);
            glTexCoord2f(0, 0); glVertex2f(left, top);
            glEnd();
        } else if (tex == null) {
            glDisable(GL_TEXTURE_2D);
            glColor3f(0.3f, 0.3f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(left, bottom);
            glVertex2f(right, bottom);
            glVertex2f(right, top);
            glVertex2f(left, top);
            glEnd();
            glEnable(GL_TEXTURE_2D);
            glColor3f(1, 1, 1);
        }
    }

    private void handleClick() {
        if (currentState == GameState.WORLD_3D || currentState == GameState.EDITOR) return;

        double[] x = new double[1], y = new double[1];
        glfwGetCursorPos(window, x, y);
        int[] winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, winW, winH);

        float fx = ((float) x[0] - (winW[0] / 2.0f));
        float fy = ((winH[0] / 2.0f) - (float) y[0]);

        if (currentState == GameState.MENU) {
            if (isInside(fx, fy, -PLAY_W/2, PLAY_W/2, PLAY_Y + PLAY_H/2, PLAY_Y - PLAY_H/2)) {
                System.out.println("▶️ Нажата кнопка Play");
                switchToFullscreen();
            }
        } else if (currentState == GameState.FULLSCREEN_MENU) {
            if (isInside(fx, fy, BTN_X - BTN_W/2, BTN_X + BTN_W/2, NG_Y + BTN_H/2, NG_Y - BTN_H/2)) {
                System.out.println("🎮 Запуск 3D игры");
                start3DGame();
            } else if (isInside(fx, fy, BTN_X - BTN_W/2, BTN_X + BTN_W/2, DL_Y + BTN_H/2, DL_Y - BTN_H/2)) {
                System.out.println("🛠️ Запуск редактора");
                startEditor();
            }
        }
    }

    private boolean isInside(float x, float y, float l, float r, float t, float b) {
        return x >= l && x <= r && y <= t && y >= b;
    }

    private void switchToFullscreen() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode != null) {
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            setup2DState();
            currentState = GameState.FULLSCREEN_MENU;
            System.out.println("🖥️ Переход в полноэкранный режим");
        }
    }

    private void toWindowedMenu() {
        glfwSetWindowMonitor(window, NULL, 100, 100, INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT, GLFW_DONT_CARE);
        centerWindow();
        setup2DState();
        loadTextures();
        currentState = GameState.MENU;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        System.out.println("🪟 Возврат в оконный режим");
    }

    private void start3DGame() {
        // ВАЖНО: Сбросить GL состояние перед запуском 3D
        resetGLState();

        world3D = new World3D(window);
        world3D.init();
        currentState = GameState.WORLD_3D;
        System.out.println("🎮 3D игра запущена");
    }

    private void startEditor() {
        resetGLState();

        world3D = new World3D(window);
        world3D.init();
        world3D.enableEditorMode();
        currentState = GameState.EDITOR;
        System.out.println("🛠️ Редактор запущен");
    }

    private void cleanup() {
        System.out.println("Очистка ресурсов...");
        if (world3D != null) world3D.cleanup();
        if (bgTex != null) bgTex.cleanup();
        if (playTex != null) playTex.cleanup();
        if (newGameTex != null) newGameTex.cleanup();
        if (downloadTex != null) downloadTex.cleanup();
        if (fsBgTex != null) fsBgTex.cleanup();
        if (arrowCursor != 0) glfwDestroyCursor(arrowCursor);
        if (handCursor != 0) glfwDestroyCursor(handCursor);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        System.out.println("Игра завершена");
    }
}
