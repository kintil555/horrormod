package com.horrormod.client;

import com.horrormod.HorrorMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Client-side chaos effect saat end crystal di farland dihancurkan:
 * - Layar bergetar (shake camera)
 * - Overlay ungu pulsing
 * - Glitch bars hitam-ungu
 * - (Sky jadi ungu via fog color override)
 */
public class FarlandChaosRenderer {

    private static final Identifier PURPLE_OVERLAY =
        new Identifier(HorrorMod.MOD_ID, "textures/effect/purple_overlay.png");

    private static boolean active = false;
    private static long    startMs = 0;
    private static final Random RAND = new Random();

    public static void startChaos() {
        active  = true;
        startMs = System.currentTimeMillis();
    }

    public static void stopChaos() {
        active = false;
    }

    public static boolean isActive() { return active; }

    public static void register() {
        HudRenderCallback.EVENT.register(FarlandChaosRenderer::renderHud);
    }

    private static void renderHud(DrawContext ctx, float tickDelta) {
        if (!active) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long elapsed = System.currentTimeMillis() - startMs;

        // Pulse alpha (bergerak-gerak)
        float pulse = (float)(Math.sin(elapsed * 0.01) * 0.5 + 0.5);
        int alpha   = (int)(pulse * 120) + 40; // 40-160

        // Purple overlay pulsing
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x6600AA);

        // Glitch bars horizontal acak
        int barCount = 6 + RAND.nextInt(6);
        for (int i = 0; i < barCount; i++) {
            int barY  = RAND.nextInt(h);
            int barH  = 2 + RAND.nextInt(8);
            int barAlpha = 100 + RAND.nextInt(155);
            int color = RAND.nextBoolean()
                ? ((barAlpha << 24) | 0x220044) // hitam ungu
                : ((barAlpha << 24) | 0xAA00FF); // ungu terang
            ctx.fill(0, barY, w, barY + barH, color);
        }

        // Vignette tepi gelap-ungu
        int vAlpha = 180;
        int vColor = (vAlpha << 24) | 0x110022;
        int vSize  = w / 5;
        ctx.fill(0, 0, vSize, h, vColor);
        ctx.fill(w - vSize, 0, w, h, vColor);
        ctx.fill(0, 0, w, vSize, vColor);
        ctx.fill(0, h - vSize, w, h, vColor);
    }

    /**
     * Dipanggil dari HorrorFogRenderer saat chaos aktif
     * untuk override fog ke ungu.
     */
    public static void applyChaosFog() {
        if (!active) return;
        long elapsed = System.currentTimeMillis() - startMs;
        float pulse  = (float)(Math.sin(elapsed * 0.008) * 0.3 + 0.7);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(2.0f);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(20.0f * pulse);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogShape(
            net.minecraft.client.render.FogShape.SPHERE);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogColor(
            0.35f * pulse, 0.0f, 0.55f * pulse, 1.0f); // ungu gelap
    }
}
