package models;

import org.joml.Vector3f;
import utils.Ray;

public class Building {
    private Vector3f position;
    private Vector3f size;

    public Building(Vector3f position, Vector3f size) {
        this.position = new Vector3f(position);
        this.size = new Vector3f(size);
    }

    public boolean intersects(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        float buildingMinX = position.x - size.x / 2;
        float buildingMaxX = position.x + size.x / 2;
        float buildingMinY = position.y - size.y / 2;
        float buildingMaxY = position.y + size.y / 2;
        float buildingMinZ = position.z - size.z / 2;
        float buildingMaxZ = position.z + size.z / 2;

        return !(maxX < buildingMinX || minX > buildingMaxX ||
                maxY < buildingMinY || minY > buildingMaxY ||
                maxZ < buildingMinZ || minZ > buildingMaxZ);
    }

    // ИСПРАВЛЕНИЕ 2: Реализация пересечения луча с AABB
    public float intersectRay(Ray ray) {
        Vector3f invDir = new Vector3f(
                1.0f / ray.direction.x,
                1.0f / ray.direction.y,
                1.0f / ray.direction.z
        );

        float minX = position.x - size.x / 2;
        float maxX = position.x + size.x / 2;
        float minY = position.y - size.y / 2;
        float maxY = position.y + size.y / 2;
        float minZ = position.z - size.z / 2;
        float maxZ = position.z + size.z / 2;

        float t1 = (minX - ray.origin.x) * invDir.x;
        float t2 = (maxX - ray.origin.x) * invDir.x;
        float t3 = (minY - ray.origin.y) * invDir.y;
        float t4 = (maxY - ray.origin.y) * invDir.y;
        float t5 = (minZ - ray.origin.z) * invDir.z;
        float t6 = (maxZ - ray.origin.z) * invDir.z;

        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0 || tmin > tmax) {
            return -1;
        }
        return tmin > 0 ? tmin : tmax;
    }

    public void setPosition(Vector3f position) {
        this.position = new Vector3f(position);
    }

    public void setSize(Vector3f size) {
        this.size = new Vector3f(size);
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getSize() { return size; }
}