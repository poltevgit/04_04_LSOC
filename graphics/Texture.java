package graphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Texture {
    private int textureId;
    private int width, height;
    private boolean loaded = false;

    public Texture(String path) {
        IntBuffer w = MemoryUtil.memAllocInt(1);
        IntBuffer h = MemoryUtil.memAllocInt(1);
        IntBuffer channels = MemoryUtil.memAllocInt(1);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("textures/" + path);
                if (is == null) {
                    is = getClass().getClassLoader().getResourceAsStream("models/" + path);
                }
            }

            if (is == null) {
                throw new RuntimeException("Texture not found: " + path);
            }

            byte[] imageData = is.readAllBytes();
            ByteBuffer imageBuffer = MemoryUtil.memAlloc(imageData.length);
            imageBuffer.put(imageData).flip();

            ByteBuffer decodedImage = STBImage.stbi_load_from_memory(
                    imageBuffer, w, h, channels, 4);
            MemoryUtil.memFree(imageBuffer);

            if (decodedImage == null) {
                throw new RuntimeException("Failed to decode: " + STBImage.stbi_failure_reason());
            }

            width = w.get(0);
            height = h.get(0);

            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, decodedImage);

            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

            STBImage.stbi_image_free(decodedImage);
            loaded = true;

            System.out.println("Texture loaded: " + path + " (" + width + "x" + height + ")");

        } catch (Exception e) {
            System.err.println("Error loading texture: " + path);
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            MemoryUtil.memFree(w);
            MemoryUtil.memFree(h);
            MemoryUtil.memFree(channels);
        }
    }

    public void bind() {
        if (loaded) GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    public void bind(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        bind();
    }

    public void cleanup() {
        if (loaded && textureId != 0) {
            GL11.glDeleteTextures(textureId);
            loaded = false;
        }
    }

    public boolean isLoaded() { return loaded; }
    public int getId() { return textureId; }
}
