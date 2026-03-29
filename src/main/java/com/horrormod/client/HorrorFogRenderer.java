package com.horrormod.client;

import com.horrormod.world.HorrorDimensions;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Custom fog renderer untuk dimensi horror.
 * Sodium tidak bisa di-override via biome JSON, jadi kita pakai
 * WorldRenderEvents.BEFORE_ENTITIES untuk inject fog tiap frame.
 *
 * Void Forest  : fog gelap kehijauan, jarak sangat pendek (8 block)
 * Planks Dim   : fog kuning kayu gelap, jarak sangat pendek (6 block)
 */
public class HorrorFogRenderer {

    public static void register() {
        WorldRenderEvents.BEFORE_ENTITIES.register(HorrorFogRenderer::applyFog);
    }

    private static void applyFog(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        RegistryKey<World> dim = mc.world.getRegistryKey();

        if (dim.equals(HorrorDimensions.VOID_FOREST_KEY)) {
            // Fog gelap kehijauan ala Minecraft Alpha — sangat tebal
            RenderSystem.setShaderFogStart(2.0f);
            RenderSystem.setShaderFogEnd(14.0f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.08f, 0.11f, 0.06f, 1.0f); // hijau gelap
        } else if (dim.equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) {
            // Fog kuning kayu sangat tebal
            RenderSystem.setShaderFogStart(1.0f);
            RenderSystem.setShaderFogEnd(10.0f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.22f, 0.17f, 0.08f, 1.0f); // coklat kayu gelap
        }
    }
}
