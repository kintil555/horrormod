package com.horrormod;

import net.fabricmc.api.ClientModInitializer;

public class HorrorModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HorrorMod.LOGGER.info("Horror Mod Client initialized.");
    }
}
