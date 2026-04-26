package graphics;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import graphics.ModelLoader.*;

import java.util.*;

public class AnimationPlayer {

    // ========== ПОЛЯ ==========

    private final AnimatedModel model;
    private final Skeleton skeleton;
    private AnimationClip currentAnimation;

    private float currentTime = 0;
    private boolean isPlaying = false;
    private boolean loop = true;

    // Матрицы костей для шейдера
    private final Matrix4f[] boneMatrices;
    private final float[] boneMatricesArray;

    // Кэш для вычислений
    private final Matrix4f[] nodeTransforms;

    public AnimationPlayer(AnimatedModel model) {
        this.model = model;
        this.skeleton = model.skeleton;
        this.boneMatrices = new Matrix4f[skeleton.boneCount];
        this.boneMatricesArray = new float[skeleton.boneCount * 16];
        this.nodeTransforms = new Matrix4f[skeleton.boneCount];

        for (int i = 0; i < skeleton.boneCount; i++) {
            boneMatrices[i] = new Matrix4f();
            nodeTransforms[i] = new Matrix4f();
        }

        // Берем первую анимацию если есть
        if (model.animations != null && !model.animations.isEmpty()) {
            this.currentAnimation = model.animations.get(0);
        }
    }

    public void play() {
        isPlaying = true;
        currentTime = 0;
    }

    public void play(String animName) {
        if (model.animations != null) {
            for (AnimationClip anim : model.animations) {
                if (anim.name.equalsIgnoreCase(animName) ||
                        anim.name.toLowerCase().contains(animName.toLowerCase())) {
                    currentAnimation = anim;
                    break;
                }
            }
        }
        play();
    }

    public void pause() {
        isPlaying = false;
    }

    public void stop() {
        isPlaying = false;
        currentTime = 0;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void update(float deltaTime) {
        if (!isPlaying || currentAnimation == null || skeleton.rootBone == null) {
            return;
        }

        currentTime += deltaTime * currentAnimation.ticksPerSecond;

        if (currentTime > currentAnimation.duration) {
            if (loop) {
                currentTime = currentTime % currentAnimation.duration;
            } else {
                currentTime = currentAnimation.duration;
                isPlaying = false;
            }
        }

        // Вычисляем матрицы для всех узлов
        calculateNodeTransforms(skeleton.rootBone, new Matrix4f(), currentTime);

        // Вычисляем финальные матрицы костей
        for (int i = 0; i < skeleton.boneCount; i++) {
            Bone bone = skeleton.bones.get(i);
            Matrix4f globalTransform = nodeTransforms[i];

            // finalTransform = globalInverse * globalTransform * offsetMatrix
            boneMatrices[i].set(model.globalInverseTransform)
                    .mul(globalTransform)
                    .mul(bone.offsetMatrix);
        }

        // Заполняем массив для шейдера
        int idx = 0;
        for (int i = 0; i < skeleton.boneCount; i++) {
            boneMatrices[i].get(boneMatricesArray, idx);
            idx += 16;
        }
    }

    private void calculateNodeTransforms(Bone bone, Matrix4f parentTransform, float time) {
        String nodeName = bone.name;
        Matrix4f localTransform = new Matrix4f(bone.localTransform);

        // Применяем анимацию если есть канал для этого узла
        BoneAnimation channel = currentAnimation.channels.get(nodeName);
        if (channel != null) {
            Vector3f position = interpolatePosition(channel.positions, time);
            Quaternionf rotation = interpolateRotation(channel.rotations, time);
            Vector3f scale = interpolateScale(channel.scales, time);

            localTransform = new Matrix4f()
                    .translation(position)
                    .rotate(rotation)
                    .scale(scale);
        }

        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);
        nodeTransforms[bone.id] = globalTransform;

        // Рекурсивно для детей
        for (Bone child : bone.children) {
            calculateNodeTransforms(child, globalTransform, time);
        }
    }

    // ИСПРАВЛЕНО: используем ModelLoader.VectorKey
    private Vector3f interpolatePosition(List<ModelLoader.VectorKey> keys, float time) {
        if (keys == null || keys.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }
        if (keys.size() == 1) {
            return new Vector3f(keys.get(0).value);
        }

        // Находим ключи для интерполяции
        ModelLoader.VectorKey prev = keys.get(0);
        ModelLoader.VectorKey next = keys.get(keys.size() - 1);

        for (int i = 0; i < keys.size() - 1; i++) {
            if (time >= keys.get(i).time && time < keys.get(i + 1).time) {
                prev = keys.get(i);
                next = keys.get(i + 1);
                break;
            }
        }

        float t = (time - prev.time) / (next.time - prev.time);
        t = Math.max(0, Math.min(1, t));

        return new Vector3f(prev.value).lerp(next.value, t);
    }

    // ИСПРАВЛЕНО: используем ModelLoader.QuatKey
    private Quaternionf interpolateRotation(List<ModelLoader.QuatKey> keys, float time) {
        if (keys == null || keys.isEmpty()) {
            return new Quaternionf();
        }
        if (keys.size() == 1) {
            return new Quaternionf(keys.get(0).value);
        }

        ModelLoader.QuatKey prev = keys.get(0);
        ModelLoader.QuatKey next = keys.get(keys.size() - 1);

        for (int i = 0; i < keys.size() - 1; i++) {
            if (time >= keys.get(i).time && time < keys.get(i + 1).time) {
                prev = keys.get(i);
                next = keys.get(i + 1);
                break;
            }
        }

        float t = (time - prev.time) / (next.time - prev.time);
        t = Math.max(0, Math.min(1, t));

        return new Quaternionf(prev.value).slerp(next.value, t);
    }

    // ИСПРАВЛЕНО: используем ModelLoader.VectorKey
    private Vector3f interpolateScale(List<ModelLoader.VectorKey> keys, float time) {
        if (keys == null || keys.isEmpty()) {
            return new Vector3f(1, 1, 1);
        }
        if (keys.size() == 1) {
            return new Vector3f(keys.get(0).value);
        }

        // Используем тот же метод что и для позиции
        return interpolatePosition(keys, time);
    }

    public float[] getBoneMatrices() {
        return boneMatricesArray;
    }

    public Matrix4f[] getBoneMatricesMatrix() {
        return boneMatrices;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public float getCurrentTime() {
        return currentTime;
    }

    public float getDuration() {
        return currentAnimation != null ? currentAnimation.duration : 0;
    }

    public String getCurrentAnimationName() {
        return currentAnimation != null ? currentAnimation.name : "None";
    }

    public List<String> getAnimationNames() {
        List<String> names = new ArrayList<>();
        if (model.animations != null) {
            for (AnimationClip anim : model.animations) {
                names.add(anim.name);
            }
        }
        return names;
    }

    public void setAnimation(int index) {
        if (model.animations != null && index >= 0 && index < model.animations.size()) {
            currentAnimation = model.animations.get(index);
            currentTime = 0;
        }
    }

    public void setAnimation(String name) {
        if (model.animations != null) {
            for (AnimationClip anim : model.animations) {
                if (anim.name.equals(name)) {
                    currentAnimation = anim;
                    currentTime = 0;
                    return;
                }
            }
        }
    }
}