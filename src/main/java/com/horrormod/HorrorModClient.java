package com.horrormod;

import com.horrormod.client.HorrorFogRenderer;
import com.horrormod.client.JumpscareRenderer;
import com.horrormod.network.HorrorPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class HorrorModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HorrorMod.LOGGER.info("Horror Mod Client initialized.");
        JumpscareRenderer.register();
        HorrorFogRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(HorrorPackets.JUMPSCARE, (client, handler, buf, responseSender) -> {
            int index = buf.readByte();
            client.execute(() -> {
                if (index < 0) JumpscareRenderer.trigger();
                else JumpscareRenderer.trigger(index);
            });
        });
    }
}
