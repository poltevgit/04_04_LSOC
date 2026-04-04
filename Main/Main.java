package Main;

import game.SimpleWindow;
// Импорты должны быть здесь!
import org.lwjgl.assimp.Assimp;
import static org.lwjgl.assimp.Assimp.aiGetVersionMajor;

public class Main {
    public static void main(String[] args) {

        new SimpleWindow().run();
    }
}