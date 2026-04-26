package graphics;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.assimp.*;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;

public class ModelLoader {

    // ========== КЛАССЫ ДЛЯ АНИМАЦИИ ==========

    public static class AnimatedModel {
        public Mesh mesh;
        public Skeleton skeleton;
        public List<AnimationClip> animations;
        public Matrix4f globalInverseTransform = new Matrix4f();
    }

    public static class Skeleton {
        public List<Bone> bones = new ArrayList<>();
        public Map<String, Integer> boneMap = new HashMap<>();
        public Bone rootBone;
        public int boneCount;
    }

    public static class Bone {
        public String name;
        public int id;
        public Matrix4f offsetMatrix = new Matrix4f();
        public Matrix4f localTransform = new Matrix4f();
        public Bone parent;
        public List<Bone> children = new ArrayList<>();
    }

    public static class AnimationClip {
        public String name;
        public float duration;
        public float ticksPerSecond;
        public Map<String, BoneAnimation> channels = new HashMap<>();
    }

    public static class BoneAnimation {
        public List<VectorKey> positions = new ArrayList<>();
        public List<QuatKey> rotations = new ArrayList<>();
        public List<VectorKey> scales = new ArrayList<>();
    }

    public static class VectorKey {
        public float time;
        public Vector3f value;
    }

    public static class QuatKey {
        public float time;
        public Quaternionf value;
    }

    // ========== МЕТОДЫ ЗАГРУЗКИ ==========

    // Старый метод для обычных моделей
    public static Mesh loadModel(String path) {
        AIScene scene = aiImportFile(path,
                aiProcess_Triangulate |
                        aiProcess_JoinIdenticalVertices |
                        aiProcess_GenSmoothNormals |
                        aiProcess_FixInfacingNormals |
                        aiProcess_FlipUVs);

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Assimp error: " + aiGetErrorString());
        }

        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));

        float[] vertices = new float[aiMesh.mNumVertices() * 3];
        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            AIVector3D v = aiMesh.mVertices().get(i);
            vertices[i * 3] = v.x();
            vertices[i * 3 + 1] = v.y();
            vertices[i * 3 + 2] = v.z();
        }

        float[] normals = new float[aiMesh.mNumVertices() * 3];
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        if (aiNormals != null) {
            for (int i = 0; i < aiMesh.mNumVertices(); i++) {
                AIVector3D n = aiNormals.get(i);
                normals[i * 3] = n.x();
                normals[i * 3 + 1] = n.y();
                normals[i * 3 + 2] = n.z();
            }
        }

        float[] texCoords = null;
        AIVector3D.Buffer aiTexCoords = aiMesh.mTextureCoords(0);
        if (aiTexCoords != null) {
            texCoords = new float[aiMesh.mNumVertices() * 2];
            for (int i = 0; i < aiMesh.mNumVertices(); i++) {
                AIVector3D tc = aiTexCoords.get(i);
                texCoords[i * 2] = tc.x();
                texCoords[i * 2 + 1] = tc.y();
            }
        }

        int[] indices = new int[aiMesh.mNumFaces() * 3];
        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            IntBuffer pIndices = aiMesh.mFaces().get(i).mIndices();
            indices[i * 3] = pIndices.get(0);
            indices[i * 3 + 1] = pIndices.get(1);
            indices[i * 3 + 2] = pIndices.get(2);
        }

        return new Mesh(vertices, normals, texCoords, indices);
    }

    // ========== НОВЫЙ МЕТОД ДЛЯ АНИМИРОВАННЫХ МОДЕЛЕЙ ==========
    public static AnimatedModel loadAnimatedModel(String path) {
        int flags = aiProcess_Triangulate |
                aiProcess_JoinIdenticalVertices |
                aiProcess_FixInfacingNormals |
                aiProcess_FlipUVs |
                aiProcess_LimitBoneWeights |
                aiProcess_GenSmoothNormals;

        AIScene scene = aiImportFile(path, flags);
        if (scene == null) {
            throw new RuntimeException("Failed to load animated model: " + path + "\nError: " + aiGetErrorString());
        }

        AnimatedModel model = new AnimatedModel();

        // Глобальная обратная трансформация
        model.globalInverseTransform = toMatrix(scene.mRootNode().mTransformation()).invert();

        // Загружаем скелет
        model.skeleton = loadSkeleton(scene);

        // Загружаем меш с костями
        model.mesh = loadAnimatedMesh(scene, model.skeleton);

        // Загружаем анимации
        model.animations = loadAnimations(scene);

        System.out.println("✅ Animated model loaded: " + path);
        System.out.println("   Bones: " + model.skeleton.boneCount);
        System.out.println("   Animations: " + model.animations.size());

        return model;
    }

    private static Skeleton loadSkeleton(AIScene scene) {
        Skeleton skeleton = new Skeleton();

        // Собираем все кости из мешей
        for (int m = 0; m < scene.mNumMeshes(); m++) {
            AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(m));
            for (int i = 0; i < aiMesh.mNumBones(); i++) {
                AIBone aiBone = AIBone.create(aiMesh.mBones().get(i));
                String boneName = aiBone.mName().dataString();

                if (!skeleton.boneMap.containsKey(boneName)) {
                    Bone bone = new Bone();
                    bone.name = boneName;
                    bone.id = skeleton.bones.size();
                    bone.offsetMatrix = toMatrix(aiBone.mOffsetMatrix());
                    skeleton.bones.add(bone);
                    skeleton.boneMap.put(boneName, bone.id);
                }
            }
        }

        // Строим иерархию
        buildHierarchy(scene.mRootNode(), null, skeleton);

        // Находим корневую кость
        for (Bone bone : skeleton.bones) {
            if (bone.parent == null) {
                skeleton.rootBone = bone;
                break;
            }
        }

        skeleton.boneCount = skeleton.bones.size();

        return skeleton;
    }

    private static void buildHierarchy(AINode node, Bone parent, Skeleton skeleton) {
        String nodeName = node.mName().dataString();
        Integer boneId = skeleton.boneMap.get(nodeName);
        Bone currentBone = null;

        if (boneId != null) {
            currentBone = skeleton.bones.get(boneId);
            currentBone.localTransform = toMatrix(node.mTransformation());
            if (parent != null) {
                currentBone.parent = parent;
                parent.children.add(currentBone);
            }
        }

        for (int i = 0; i < node.mNumChildren(); i++) {
            AINode child = AINode.create(node.mChildren().get(i));
            buildHierarchy(child, currentBone != null ? currentBone : parent, skeleton);
        }
    }

    private static Mesh loadAnimatedMesh(AIScene scene, Skeleton skeleton) {
        // Берем первый меш (можно объединить все меши при необходимости)
        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));
        int vertexCount = aiMesh.mNumVertices();

        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] texCoords = new float[vertexCount * 2];
        int[] boneIds = new int[vertexCount * 4];
        float[] boneWeights = new float[vertexCount * 4];

        // Инициализация boneIds значением -1
        for (int i = 0; i < vertexCount * 4; i++) {
            boneIds[i] = 0;
        }

        // Позиции, нормали, UV
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D v = aiMesh.mVertices().get(i);
            positions[i*3] = v.x();
            positions[i*3+1] = v.y();
            positions[i*3+2] = v.z();

            if (aiMesh.mNormals() != null) {
                AIVector3D n = aiMesh.mNormals().get(i);
                normals[i*3] = n.x();
                normals[i*3+1] = n.y();
                normals[i*3+2] = n.z();
            }

            if (aiMesh.mTextureCoords(0) != null) {
                AIVector3D tc = aiMesh.mTextureCoords(0).get(i);
                texCoords[i*2] = tc.x();
                texCoords[i*2+1] = tc.y();
            }
        }

        // Индексы
        int faceCount = aiMesh.mNumFaces();
        int[] indices = new int[faceCount * 3];
        for (int i = 0; i < faceCount; i++) {
            AIFace face = aiMesh.mFaces().get(i);
            IntBuffer idx = face.mIndices();
            indices[i*3] = idx.get(0);
            indices[i*3+1] = idx.get(1);
            indices[i*3+2] = idx.get(2);
        }

        // Кости и веса
        for (int b = 0; b < aiMesh.mNumBones(); b++) {
            AIBone aiBone = AIBone.create(aiMesh.mBones().get(b));
            String boneName = aiBone.mName().dataString();
            Integer boneId = skeleton.boneMap.get(boneName);

            if (boneId != null) {
                for (int w = 0; w < aiBone.mNumWeights(); w++) {
                    AIVertexWeight vw = aiBone.mWeights().get(w);
                    int vId = vw.mVertexId();
                    float weight = vw.mWeight();

                    // Ищем свободный слот (до 4 костей на вершину)
                    for (int slot = 0; slot < 4; slot++) {
                        if (boneWeights[vId * 4 + slot] == 0) {
                            boneIds[vId * 4 + slot] = boneId;
                            boneWeights[vId * 4 + slot] = weight;
                            break;
                        }
                    }
                }
            }
        }

        // Нормализация весов
        for (int i = 0; i < vertexCount; i++) {
            float total = 0;
            for (int j = 0; j < 4; j++) {
                total += boneWeights[i*4 + j];
            }
            if (total > 0) {
                for (int j = 0; j < 4; j++) {
                    boneWeights[i*4 + j] /= total;
                }
            } else {
                // Если нет весов, привязываем к корневой кости
                boneIds[i*4] = 0;
                boneWeights[i*4] = 1.0f;
            }
        }

        return new Mesh(positions, normals, texCoords, indices, boneIds, boneWeights);
    }

    private static List<AnimationClip> loadAnimations(AIScene scene) {
        List<AnimationClip> animations = new ArrayList<>();

        for (int a = 0; a < scene.mNumAnimations(); a++) {
            AIAnimation aiAnim = AIAnimation.create(scene.mAnimations().get(a));
            AnimationClip anim = new AnimationClip();
            anim.name = aiAnim.mName().dataString();
            if (anim.name.isEmpty()) anim.name = "Animation" + a;
            anim.duration = (float) aiAnim.mDuration();
            anim.ticksPerSecond = (float) aiAnim.mTicksPerSecond();
            if (anim.ticksPerSecond == 0) anim.ticksPerSecond = 30.0f;

            for (int c = 0; c < aiAnim.mNumChannels(); c++) {
                AINodeAnim channel = AINodeAnim.create(aiAnim.mChannels().get(c));
                String nodeName = channel.mNodeName().dataString();
                BoneAnimation boneAnim = new BoneAnimation();

                // Позиции
                for (int k = 0; k < channel.mNumPositionKeys(); k++) {
                    AIVectorKey key = channel.mPositionKeys().get(k);
                    VectorKey vk = new VectorKey();
                    vk.time = (float) key.mTime();
                    AIVector3D v = key.mValue();
                    vk.value = new Vector3f(v.x(), v.y(), v.z());
                    boneAnim.positions.add(vk);
                }

                // Повороты
                for (int k = 0; k < channel.mNumRotationKeys(); k++) {
                    AIQuatKey key = channel.mRotationKeys().get(k);
                    QuatKey qk = new QuatKey();
                    qk.time = (float) key.mTime();
                    AIQuaternion q = key.mValue();
                    qk.value = new Quaternionf(q.x(), q.y(), q.z(), q.w());
                    boneAnim.rotations.add(qk);
                }

                // Масштабы
                for (int k = 0; k < channel.mNumScalingKeys(); k++) {
                    AIVectorKey key = channel.mScalingKeys().get(k);
                    VectorKey vk = new VectorKey();
                    vk.time = (float) key.mTime();
                    AIVector3D v = key.mValue();
                    vk.value = new Vector3f(v.x(), v.y(), v.z());
                    boneAnim.scales.add(vk);
                }

                anim.channels.put(nodeName, boneAnim);
            }

            animations.add(anim);
        }

        return animations;
    }

    private static Matrix4f toMatrix(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }
}