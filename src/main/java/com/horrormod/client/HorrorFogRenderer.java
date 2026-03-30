package com.horrormod.client;

import com.horrormod.world.HorrorDimensions;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.FogShape;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import com.mojang.blaze3d.systems.RenderSystem;

public class HorrorFogRenderer {

    public static void register() {
        WorldRenderEvents.BEFORE_ENTITIES.register(HorrorFogRenderer::applyFog);
    }

    private static void applyFog(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // Kalau chaos aktif, override semua dengan fog ungu
        if (FarlandChaosRenderer.isActive()) {
            FarlandChaosRenderer.applyChaosFog();
            return;
        }

        RegistryKey<World> dim = mc.world.getRegistryKey();

        if (dim.equals(HorrorDimensions.VOID_FOREST_KEY)) {
            RenderSystem.setShaderFogStart(2.0f);
            RenderSystem.setShaderFogEnd(14.0f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.08f, 0.11f, 0.06f, 1.0f);
        } else if (dim.equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) {
            RenderSystem.setShaderFogStart(1.0f);
            RenderSystem.setShaderFogEnd(10.0f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.22f, 0.17f, 0.08f, 1.0f);
        } else if (dim.equals(HorrorDimensions.FARLAND_KEY)) {
            RenderSystem.setShaderFogStart(8.0f);
            RenderSystem.setShaderFogEnd(80.0f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.15f, 0.12f, 0.20f, 1.0f); // ungu abu gelap
        }
    }
}
