package utils;

import org.joml.Vector3f;

public class Ray {
    public Vector3f origin;
    public Vector3f direction;

    public Ray(Vector3f origin, Vector3f direction) {
        this.origin = new Vector3f(origin);
        this.direction = new Vector3f(direction);
    }
}