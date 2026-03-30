package com.horrormod.mixin;

import com.horrormod.client.JumpscareRenderer;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class PauseScreenMixin {

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void addJumpscareToggle(CallbackInfo ci) {
        GameMenuScreen self = (GameMenuScreen)(Object)this;

        boolean enabled = JumpscareRenderer.isEnabled();
        String label = enabled ? "\u00a7cDisturbing Images: ON" : "\u00a7aDisturbing Images: OFF";

        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), button -> {
            boolean nowEnabled = JumpscareRenderer.toggleEnabled();
            button.setMessage(Text.literal(
                nowEnabled ? "\u00a7cDisturbing Images: ON" : "\u00a7aDisturbing Images: OFF"));
        })
        .dimensions(self.width / 2 - 102, self.height / 4 + 120 + 24, 204, 20)
        .build();

        self.addDrawableChild(btn);
    }
}
