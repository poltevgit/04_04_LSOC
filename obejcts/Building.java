package objects;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Building {
    private Vector3f position;
    private Vector3f size;

    public Building(float x, float y, float z, float w, float h, float d) {
        position = new Vector3f(x, y + h/2, z);
        size = new Vector3f(w, h, d);
    }

    public Matrix4f getMatrix() {
        return new Matrix4f().translate(position).scale(size);
    }
}