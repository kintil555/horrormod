package com.horrormod.mixin;

import com.horrormod.client.FarlandChaosRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    private static final Random SHAKE_RAND = new Random();

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void injectShake(double fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        if (!FarlandChaosRenderer.isActive()) return;
        Matrix4f mat = cir.getReturnValue();
        // Tambah translasi acak = efek shake
        float shakeX = (SHAKE_RAND.nextFloat() - 0.5f) * 0.03f;
        float shakeY = (SHAKE_RAND.nextFloat() - 0.5f) * 0.03f;
        mat.translate(shakeX, shakeY, 0);
        cir.setReturnValue(mat);
    }
}
