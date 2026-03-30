package com.horrormod.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Client-side chaos effect visual saat end crystal farland hancur.
 * - Screen shake: via GameRendererMixin
 * - Langit ungu: via SkyRendererMixin
 * - Awan hilang: via WorldRendererMixin
 * - Fog ungu: via BackgroundRendererMixin
 * - Glitch bars tipis: di sini (HUD)
 */
public class FarlandChaosRenderer {

    private static boolean active   = false;
    private static long    startMs  = 0;
    private static final Random RAND = new Random();

    public static void startChaos() { active = true; startMs = System.currentTimeMillis(); }
    public static void stopChaos()  { active = false; }
    public static boolean isActive(){ return active; }

    public static void register() {
        HudRenderCallback.EVENT.register(FarlandChaosRenderer::renderHud);
    }

    private static void renderHud(DrawContext ctx, float delta) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        long t = System.currentTimeMillis() - startMs;

        // Glitch bars horizontal tipis (1-3px) warna ungu/hitam
        int bars = 3 + RAND.nextInt(5);
        for (int i = 0; i < bars; i++) {
            int barY   = RAND.nextInt(h);
            int barH   = 1 + RAND.nextInt(3);
            int alpha  = 80 + RAND.nextInt(120);
            boolean bright = RAND.nextBoolean();
            int color = bright
                ? ((alpha << 24) | 0x9900FF)   // ungu terang
                : ((alpha << 24) | 0x110022);   // hitam ungu
            ctx.fill(0, barY, w, barY + barH, color);
        }

        // Vignette ungu tipis di tepi (tidak full overlay)
        int vAlpha = 60 + (int)(Math.sin(t * 0.003) * 30);
        int vColor = (vAlpha << 24) | 0x330066;
        int vW = w / 8;
        ctx.fill(0,     0, vW,     h, vColor);
        ctx.fill(w-vW,  0, w,      h, vColor);
        ctx.fill(0,     0, w,     vW, vColor);
        ctx.fill(0, h-vW, w,      h,  vColor);
    }

    /** Dipanggil dari HorrorFogRenderer di farland (non-chaos) */
    public static void applyChaosFog() {
        // Sudah dihandle oleh BackgroundRendererMixin
    }
}
