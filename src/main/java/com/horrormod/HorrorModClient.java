package com.horrormod;

import com.horrormod.client.FarlandChaosRenderer;
import com.horrormod.client.HorrorFogRenderer;
import com.horrormod.client.JumpscareRenderer;
import com.horrormod.client.entity.GrinningRenderer;
import com.horrormod.entity.HorrorEntities;
import com.horrormod.network.HorrorPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class HorrorModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HorrorMod.LOGGER.info("Horror Mod Client initialized.");

        JumpscareRenderer.register();
        HorrorFogRenderer.register();
        FarlandChaosRenderer.register();
        EntityRendererRegistry.register(HorrorEntities.GRINNING, GrinningRenderer::new);

        // Packet: jumpscare
        ClientPlayNetworking.registerGlobalReceiver(HorrorPackets.JUMPSCARE,
            (client, handler, buf, responseSender) -> {
                int index = buf.readByte();
                client.execute(() -> {
                    if (index < 0) JumpscareRenderer.trigger();
                    else JumpscareRenderer.trigger(index);
                });
            });

        // Packet: farland chaos start
        ClientPlayNetworking.registerGlobalReceiver(HorrorPackets.FARLAND_CHAOS,
            (client, handler, buf, responseSender) ->
                client.execute(FarlandChaosRenderer::startChaos));

        // Packet: farland chaos stop
        ClientPlayNetworking.registerGlobalReceiver(HorrorPackets.FARLAND_CHAOS_STOP,
            (client, handler, buf, responseSender) ->
                client.execute(FarlandChaosRenderer::stopChaos));
    }
}
