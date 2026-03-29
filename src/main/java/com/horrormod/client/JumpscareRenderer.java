package com.horrormod.client;

import com.horrormod.HorrorMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * Renders a fullscreen jumpscare texture for a short duration.
 * Triggered from client-side packet handling or tick event.
 */
public class JumpscareRenderer {

    // 7 gambar jumpscare (skip Momo)
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

    // State
    private static boolean active      = false;
    private static long    startMillis = 0;
    private static final long DURATION_MS = 500; // 0.5 detik
    private static Identifier currentTexture = null;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    /** Panggil ini untuk trigger jumpscare (pilih gambar random). */
    public static void trigger() {
        if (active) return; // Jangan trigger kalau masih tampil
        active         = true;
        startMillis    = System.currentTimeMillis();
        currentTexture = TEXTURES[RANDOM.nextInt(TEXTURES.length)];
    }

    /** Trigger dengan gambar spesifik (index 0-6). */
    public static void trigger(int index) {
        if (active) return;
        active         = true;
        startMillis    = System.currentTimeMillis();
        currentTexture = TEXTURES[Math.max(0, Math.min(index, TEXTURES.length - 1))];
    }

    private static void render(DrawContext ctx) {
        if (!active || currentTexture == null) return;

        long elapsed = System.currentTimeMillis() - startMillis;
        if (elapsed >= DURATION_MS) {
            active = false;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        // Fullscreen, stretch ke layar penuh
        ctx.drawTexture(currentTexture, 0, 0, 0, 0, w, h, w, h);
    }
}
