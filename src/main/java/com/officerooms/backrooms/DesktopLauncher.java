package com.officerooms.backrooms;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Backrooms: Office Maze");
        config.setWindowedMode(1600, 900);
        config.useVsync(true);
        config.setForegroundFPS(144);

        new Lwjgl3Application(new BackroomsGame(), config);
    }
}
