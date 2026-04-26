package graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {
    private final int vaoId;
    private final int vertexCount;
    private final boolean hasTextureCoords;
    private final boolean hasBones;

    public Mesh(float[] positions, float[] normals, float[] texCoords,
                int[] indices, int[] boneIds, float[] boneWeights) {
        this.vertexCount = indices.length;
        this.hasTextureCoords = texCoords != null && texCoords.length > 0;
        this.hasBones = boneIds != null && boneIds.length > 0;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Positions (location 0)
        int vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, positions, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        // Normals (location 1)
        int normVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, normVboId);
        glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(1);

        // Texture Coords (location 2)
        if (hasTextureCoords) {
            int texVboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, texVboId);
            glBufferData(GL_ARRAY_BUFFER, texCoords, GL_STATIC_DRAW);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(2);
        }

        // Bone IDs (location 3) — integer!
        if (hasBones) {
            int boneIdVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, boneIdVbo);
            glBufferData(GL_ARRAY_BUFFER, boneIds, GL_STATIC_DRAW);
            glVertexAttribIPointer(3, 4, GL_INT, 0, 0);
            glEnableVertexAttribArray(3);
        }

        // Bone Weights (location 4)
        if (hasBones) {
            int boneWeightVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, boneWeightVbo);
            glBufferData(GL_ARRAY_BUFFER, boneWeights, GL_STATIC_DRAW);
            glVertexAttribPointer(4, 4, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(4);
        }

        // Indices
        int eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    // Без костей
    public Mesh(float[] positions, float[] normals, float[] texCoords, int[] indices) {
        this(positions, normals, texCoords, indices, null, null);
    }

    // Старый конструктор
    public Mesh(float[] positions, float[] normals, int[] indices) {
        this(positions, normals, null, indices, null, null);
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }
}
