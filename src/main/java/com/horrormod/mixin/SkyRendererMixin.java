package com.horrormod.mixin;

import com.horrormod.client.FarlandChaosRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilderStorage;

@Mixin(WorldRenderer.class)
public class SkyRendererMixin {

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void overrideSky(MatrixStack matrices, org.joml.Matrix4f projectionMatrix,
                              float tickDelta, net.minecraft.client.render.Camera camera,
                              boolean bl, Runnable runnable, CallbackInfo ci) {
        if (!FarlandChaosRenderer.isActive()) return;
        // Cancel sky normal → draw solid purple sky
        ci.cancel();
        long t = System.currentTimeMillis();
        float pulse = (float)(Math.sin(t * 0.005) * 0.15 + 0.85);
        // Set clear color ke ungu
        RenderSystem.clearColor(0.25f * pulse, 0.0f, 0.40f * pulse, 1.0f);
        RenderSystem.clear(0x4000, false);
    }
}
