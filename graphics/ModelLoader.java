package graphics;

import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.assimp.Assimp.*;

public class ModelLoader {
    public static Mesh loadModel(String path) {
        AIScene scene = aiImportFile(path,
                aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_FixInfacingNormals);

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Assimp error: " + aiGetErrorString());
        }

        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));

        // Extract vertices
        float[] vertices = new float[aiMesh.mNumVertices() * 3];
        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            AIVector3D v = aiMesh.mVertices().get(i);
            vertices[i * 3] = v.x();
            vertices[i * 3 + 1] = v.y();
            vertices[i * 3 + 2] = v.z();
        }

        // Extract normals
        float[] normals = new float[aiMesh.mNumVertices() * 3];
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            AIVector3D n = aiNormals.get(i);
            normals[i * 3] = n.x();
            normals[i * 3 + 1] = n.y();
            normals[i * 3 + 2] = n.z();
        }

        // Extract indices
        int[] indices = new int[aiMesh.mNumFaces() * 3];
        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            IntBuffer pIndices = aiMesh.mFaces().get(i).mIndices();
            indices[i * 3] = pIndices.get(0);
            indices[i * 3 + 1] = pIndices.get(1);
            indices[i * 3 + 2] = pIndices.get(2);
        }

        return new Mesh(vertices, normals, indices);
    }
}