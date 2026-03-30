package com.horrormod.client;

import com.horrormod.HorrorMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.file.Path;
import java.util.Random;

public class JumpscareRenderer {

    private static final Identifier[] TEXTURES = {
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_1.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_2.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_3.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_4.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_5.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_6.png"),
        new Identifier(HorrorMod.MOD_ID, "textures/jumpscare/jumpscare_7.png"),
    };

    private static final Random RANDOM  = new Random();
    private static boolean active       = false;
    private static long    startMillis  = 0;
    private static final long DURATION_MS = 500;
    private static Identifier currentTexture = null;

    // Toggle state - persisted ke file
    private static boolean enabled = true;
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("horrormod_jumpscare.txt");

    public static void register() {
        loadConfig();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    public static boolean isEnabled() { return enabled; }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        saveConfig();
        return enabled;
    }

    public static void trigger() {
        if (!enabled || active) return;
        active = true;
        startMillis = System.currentTimeMillis();
        currentTexture = TEXTURES[RANDOM.nextInt(TEXTURES.length)];
    }

    public static void trigger(int index) {
        if (!enabled || active) return;
        active = true;
        startMillis = System.currentTimeMillis();
        currentTexture = TEXTURES[Math.max(0, Math.min(index, TEXTURES.length - 1))];
    }

    private static void render(DrawContext ctx) {
        if (!active || currentTexture == null) return;
        if (System.currentTimeMillis() - startMillis >= DURATION_MS) {
            active = false;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        ctx.drawTexture(currentTexture, 0, 0, 0, 0, w, h, w, h);
    }

    private static void loadConfig() {
        try {
            if (CONFIG_PATH.toFile().exists()) {
                BufferedReader r = new BufferedReader(new FileReader(CONFIG_PATH.toFile()));
                String line = r.readLine();
                r.close();
                enabled = !"false".equalsIgnoreCase(line);
            }
        } catch (Exception e) { enabled = true; }
    }

    private static void saveConfig() {
        try {
            BufferedWriter w = new BufferedWriter(new FileWriter(CONFIG_PATH.toFile()));
            w.write(enabled ? "true" : "false");
            w.close();
        } catch (Exception ignored) {}
    }
}
