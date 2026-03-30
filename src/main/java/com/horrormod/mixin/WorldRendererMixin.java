package com.horrormod.mixin;

import com.horrormod.client.FarlandChaosRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void cancelClouds(CallbackInfo ci) {
        if (FarlandChaosRenderer.isActive()) {
            ci.cancel(); // Hilangkan awan saat chaos
        }
    }
}
