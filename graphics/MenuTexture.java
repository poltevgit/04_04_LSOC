package graphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class MenuTexture {
    private int textureId;
    private int width, height;
    private boolean loaded = false;

    public MenuTexture(String path) {
        // Не используем MemoryStack для больших данных
        IntBuffer w = MemoryUtil.memAllocInt(1);
        IntBuffer h = MemoryUtil.memAllocInt(1);
        IntBuffer channels = MemoryUtil.memAllocInt(1);

        try {
            // Пробуем разные способы загрузки
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);

            // Если не нашли, пробуем без префикса img/
            if (is == null && path.startsWith("img/")) {
                String altPath = path.substring(4); // убираем "img/"
                is = getClass().getClassLoader().getResourceAsStream(altPath);
                if (is != null) {
                    System.out.println("Загружена текстура по альтернативному пути: " + altPath);
                }
            }

            if (is == null) {
                System.err.println("Не удалось загрузить текстуру: " + path);
                textureId = 0;
                return;
            }

            // Читаем все байты из потока
            byte[] imageData = is.readAllBytes();

            // Используем прямую аллокацию памяти вместо MemoryStack
            ByteBuffer imageBuffer = MemoryUtil.memAlloc(imageData.length);
            imageBuffer.put(imageData).flip();

            // Декодируем изображение
            ByteBuffer decodedImage = STBImage.stbi_load_from_memory(
                    imageBuffer, w, h, channels, 4);

            // Освобождаем буфер с исходными данными
            MemoryUtil.memFree(imageBuffer);

            if (decodedImage == null) {
                System.err.println("Не удалось декодировать изображение: " + path);
                System.err.println("Причина: " + STBImage.stbi_failure_reason());
                textureId = 0;
                return;
            }

            width = w.get(0);
            height = h.get(0);

            // Создаем текстуру OpenGL
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // Настройка параметров текстуры
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            // Загружаем данные в текстуру
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, decodedImage);

            // Освобождаем память
            STBImage.stbi_image_free(decodedImage);

            loaded = true;
            System.out.println("✅ Текстура загружена: " + path + " (" + width + "x" + height + ")");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при загрузке текстуры: " + path);
            e.printStackTrace();
            textureId = 0;
            loaded = false;
        } finally {
            // Освобождаем выделенную память
            MemoryUtil.memFree(w);
            MemoryUtil.memFree(h);
            MemoryUtil.memFree(channels);
        }
    }

    public void bind() {
        if (loaded && textureId != 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        }
    }

    public void cleanup() {
        if (loaded && textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
            loaded = false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTextureId() {
        return textureId;
    }
}