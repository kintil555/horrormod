package com.horrormod.mixin;

import com.horrormod.client.FarlandChaosRenderer;
import com.horrormod.world.HorrorDimensions;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void overrideFog(Camera camera, BackgroundRenderer.FogType fogType,
                                     float viewDistance, boolean thickerFog, float tickDelta,
                                     CallbackInfo ci) {
        if (FarlandChaosRenderer.isActive()) {
            long t = System.currentTimeMillis();
            float pulse = (float)(Math.sin(t * 0.008) * 0.3 + 0.7);
            RenderSystem.setShaderFogStart(2.0f);
            RenderSystem.setShaderFogEnd(25.0f * pulse);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            RenderSystem.setShaderFogColor(0.25f * pulse, 0.0f, 0.45f * pulse, 1.0f);
        }
    }
}
