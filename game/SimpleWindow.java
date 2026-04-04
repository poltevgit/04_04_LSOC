package game;

import graphics.MenuTexture;
import graphics.World3D;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20; // Для отключения шейдеров
import org.lwjgl.system.MemoryStack;

import org.lwjgl.assimp.Assimp;
import java.nio.IntBuffer;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class SimpleWindow {

    private long window; // Указатель на дескриптор окна GLFW
    private GameState currentState = GameState.MENU; // Текущее состояние (Меню, 3D мир и т.д.)
    private World3D world3D; // Объект 3D мира

    // Текстуры для элементов интерфейса
    private MenuTexture bgTex, playTex, newGameTex, downloadTex, fsBgTex;

    // --- НАСТРОЙКИ ЭКРАНА ---
    private final int BASE_WIDTH = 1280; // Логическая ширина для расчета координат
    private final int BASE_HEIGHT = 720; // Логическая высота для расчета координат
    private final int INIT_WINDOW_WIDTH = 800; // Начальная ширина окна
    private final int INIT_WINDOW_HEIGHT = 600; // Начальная высота окна

    // --- КООРДИНАТЫ КНОПОК ---
    private final float PLAY_W = 240, PLAY_H = 120; // Размеры кнопки Play
    private final float PLAY_Y = -100; // Позиция кнопки Play по вертикали

    private final float BTN_W = 220, BTN_H = 70; // Размеры стандартных кнопок
    private final float BTN_X = 0; // Центрирование по X
    private final float NG_Y = -80; // Y-координата кнопки New Game
    private final float DL_Y = -170; // Y-координата кнопки Download

    // Запуск приложения
    public void run() {
        init(); // Инициализация GLFW и OpenGL
        mainLoop(); // Основной цикл отрисовки
        cleanup(); // Очистка ресурсов при выходе
    }

    private void init() {
        // Настройка вывода ошибок GLFW в консоль
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        // Настройка параметров окна
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Скрываем до завершения создания
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // Запрещаем изменять размер вручную

        // Настройка версии OpenGL (3.3 Compatibility для поддержки старых функций)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        // Создание самого окна
        window = glfwCreateWindow(INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT, "Last Shot or Chance", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        centerWindow(); // Центрируем окно на мониторе
        glfwMakeContextCurrent(window); // Делаем контекст окна текущим для потока
        glfwSwapInterval(1); // Включаем вертикальную синхронизацию (V-Sync)
        GL.createCapabilities(); // Связываем LWJGL с функциями OpenGL

        // Базовые настройки графики: текстуры и прозрачность
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glfwShowWindow(window); // Показываем окно

        setupInput(); // Настраиваем управление
        loadTextures(); // Загружаем картинки
    }

    // Метод для центрирования окна на экране пользователя
    private void centerWindow() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pW = stack.mallocInt(1), pH = stack.mallocInt(1);
            glfwGetWindowSize(window, pW, pH);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(window, (vidMode.width() - pW.get(0)) / 2, (vidMode.height() - pH.get(0)) / 2);
            }
        }
    }

    // Регистрация слушателей клавиатуры и мыши
    private void setupInput() {
        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE) handleEscape();
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) handleClick();
        });
    }

    /**
     * Сброс всех 3D настроек OpenGL для корректного отображения 2D меню.
     */
    private void resetGLState() {
        // Выключаем шейдеры, если они были активны в 3D
        GL20.glUseProgram(0);

        // Сбрасываем матрицы трансформации
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Отключаем функции 3D, мешающие 2D отрисовке (глубина, свет)
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);

        // Включаем текстуры и прозрачность
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Устанавливаем белый цвет (чтобы текстуры не были темными)
        glColor4f(1, 1, 1, 1);
    }

    // Логика кнопки Escape в зависимости от состояния игры
    private void handleEscape() {
        if (currentState == GameState.MENU) {
            glfwSetWindowShouldClose(window, true); // Закрыть игру
        } else if (currentState == GameState.WORLD_3D) {
            // Выход из 3D мира в полноэкранное меню
            if (world3D != null) {
                world3D.cleanup();
                world3D = null;
            }
            resetGLState();
            currentState = GameState.FULLSCREEN_MENU;
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL); // Возвращаем курсор
        } else if (currentState == GameState.FULLSCREEN_MENU) {
            toWindowedMenu(); // Возврат в оконный режим
        }
    }

    // Главный цикл, работающий до закрытия окна
    private void mainLoop() {
        while (!glfwWindowShouldClose(window)) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // Черный фон очистки
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Очистка буферов

            // Выбор отрисовки в зависимости от текущего состояния
            if (currentState == GameState.MENU) renderMenu();
            else if (currentState == GameState.FULLSCREEN_MENU) renderFullscreenMenu();
            else if (currentState == GameState.WORLD_3D) renderGameWorld();

            glfwPollEvents(); // Проверка событий (нажатия клавиш, движение мыши)
        }
    }

    // Загрузка файлов изображений
    private void loadTextures() {
        bgTex = load("img/background.png");
        playTex = load("img/play_button.png");
        newGameTex = load("img/newgame_button.png");
        downloadTex = load("img/download_button.png");
        fsBgTex = load("img/fullscreen_bg.png");
    }

    // Вспомогательный метод для загрузки текстуры с обработкой ошибок
    private MenuTexture load(String path) {
        try { return new MenuTexture(path); } catch (Exception e) { return null; }
    }

    // Настройка ортографической проекции для 2D UI
    private void setup2DProjection() {
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        glViewport(0, 0, w[0], h[0]);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // Устанавливаем систему координат от -BASE/2 до BASE/2 (центр в 0,0)
        glOrtho(-BASE_WIDTH / 2.0, BASE_WIDTH / 2.0, -BASE_HEIGHT / 2.0, BASE_HEIGHT / 2.0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    // Отрисовка главного меню в окне
    private void renderMenu() {
        resetGLState();
        setup2DProjection();
        drawQuad(bgTex, -BASE_WIDTH/2f, BASE_WIDTH/2f, BASE_HEIGHT/2f, -BASE_HEIGHT/2f);
        drawQuad(playTex, -PLAY_W/2f, PLAY_W/2f, PLAY_Y + PLAY_H/2f, PLAY_Y - PLAY_H/2f);
        glfwSwapBuffers(window); // Вывод кадра на экран
    }

    // Отрисовка меню в полноэкранном режиме
    private void renderFullscreenMenu() {
        resetGLState();
        setup2DProjection();
        drawQuad(fsBgTex, -BASE_WIDTH/2f, BASE_WIDTH/2f, BASE_HEIGHT/2f, -BASE_HEIGHT/2f);
        drawQuad(newGameTex, BTN_X - BTN_W/2f, BTN_X + BTN_W/2f, NG_Y + BTN_H/2f, NG_Y - BTN_H/2f);
        drawQuad(downloadTex, BTN_X - BTN_W/2f, BTN_X + BTN_W/2f, DL_Y + BTN_H/2f, DL_Y - BTN_H/2f);
        glfwSwapBuffers(window);
    }

    // Управление отрисовкой 3D мира
    private void renderGameWorld() {
        if (world3D != null) {
            // Если мир просит закрыться или произошла ошибка
            if (!world3D.render() || world3D.isShouldClose()) {
                world3D.cleanup();
                world3D = null;
                resetGLState();
                currentState = GameState.FULLSCREEN_MENU;
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }
        }
    }

    // Вспомогательный метод для рисования прямоугольника (кнопки/фона) с текстурой
    private void drawQuad(MenuTexture tex, float left, float right, float top, float bottom) {
        if (tex != null) {
            tex.bind(); // Привязываем текстуру
            glBegin(GL_QUADS); // Рисуем 4 вершины
            glTexCoord2f(0, 1); glVertex2f(left, bottom);
            glTexCoord2f(1, 1); glVertex2f(right, bottom);
            glTexCoord2f(1, 0); glVertex2f(right, top);
            glTexCoord2f(0, 0); glVertex2f(left, top);
            glEnd();
        }
    }

    // Логика нажатия мышкой (детектор попадания в кнопки)
    private void handleClick() {
        if (currentState == GameState.WORLD_3D) return; // В игре клики обрабатываются иначе
        double[] x = new double[1], y = new double[1];
        glfwGetCursorPos(window, x, y);
        int[] winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, winW, winH);

        // Переводим экранные координаты мыши в координаты нашей 2D проекции
        float fx = ((float) x[0] - (winW[0] / 2.0f)) * ((float) BASE_WIDTH / winW[0]);
        float fy = ((winH[0] / 2.0f) - (float) y[0]) * ((float) BASE_HEIGHT / winH[0]);

        if (currentState == GameState.MENU) {
            if (isInside(fx, fy, -PLAY_W/2, PLAY_W/2, PLAY_Y + PLAY_H/2, PLAY_Y - PLAY_H/2)) switchToFullscreen();
        } else if (currentState == GameState.FULLSCREEN_MENU) {
            if (isInside(fx, fy, BTN_X - BTN_W/2, BTN_X + BTN_W/2, NG_Y + BTN_H/2, NG_Y - BTN_H/2)) start3DGame();
        }
    }

    // Проверка: находится ли точка (x, y) внутри прямоугольника
    private boolean isInside(float x, float y, float l, float r, float t, float b) {
        return x >= l && x <= r && y <= t && y >= b;
    }

    // Переход в полноэкранный режим
    private void switchToFullscreen() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode != null) {
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            resetGLState();
            currentState = GameState.FULLSCREEN_MENU;
        }
    }

    // Возврат в оконный режим
    private void toWindowedMenu() {
        glfwSetWindowMonitor(window, NULL, 100, 100, INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT, GLFW_DONT_CARE);
        centerWindow();
        resetGLState();
        loadTextures(); // Перезагружаем текстуры на случай потери контекста
        currentState = GameState.MENU;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    // Инициализация и запуск 3D режима
    private void start3DGame() {
        world3D = new World3D(window);
        world3D.init();
        currentState = GameState.WORLD_3D;
    }

    // Освобождение памяти
    private void cleanup() {
        if (world3D != null) world3D.cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}