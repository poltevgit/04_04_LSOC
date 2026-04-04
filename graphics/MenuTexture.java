package graphics;

import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class MenuTexture {
    private int textureId;
    private int width, height;

    public MenuTexture(String filePath) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            stbi_set_flip_vertically_on_load(false);
            ByteBuffer image = stbi_load(filePath, w, h, c, 4);

            if (image == null) {
                throw new RuntimeException("Не удалось загрузить: " + filePath);
            }

            width = w.get(); height = h.get();
            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            stbi_image_free(image);
        }
    }

    public void bind() { glBindTexture(GL_TEXTURE_2D, textureId); }
    public void unbind() { glBindTexture(GL_TEXTURE_2D, 0); }
    public int getId() { return textureId; }
}