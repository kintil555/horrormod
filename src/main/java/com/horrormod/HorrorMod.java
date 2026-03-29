package com.horrormod;

import com.horrormod.entity.HorrorEntities;
import com.horrormod.event.HorrorEventHandler;
import com.horrormod.world.HorrorDimensions;
import com.horrormod.world.HorrorSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HorrorMod implements ModInitializer {
    public static final String MOD_ID = "horrormod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Horror Mod initializing...");
        HorrorEntities.register();
        HorrorDimensions.register();
        HorrorSounds.register();
        HorrorEventHandler.register();
        LOGGER.info("Horror Mod initialized.");
    }
}
