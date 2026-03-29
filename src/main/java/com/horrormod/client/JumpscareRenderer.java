package com.horrormod.client;

import com.horrormod.HorrorMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

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

    private static final Random RANDOM = new Random();
    private static boolean active = false;
    private static long startMillis = 0;
    private static final long DURATION_MS = 500;
    private static Identifier currentTexture = null;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    public static void trigger() {
        if (active) return;
        active = true;
        startMillis = System.currentTimeMillis();
        currentTexture = TEXTURES[RANDOM.nextInt(TEXTURES.length)];
    }

    public static void trigger(int index) {
        if (active) return;
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
}
