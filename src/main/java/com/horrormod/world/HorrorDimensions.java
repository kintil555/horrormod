package com.horrormod.world;

import com.horrormod.HorrorMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class HorrorDimensions {

    public static final RegistryKey<World> VOID_FOREST_KEY = RegistryKey.of(
            RegistryKeys.WORLD, new Identifier(HorrorMod.MOD_ID, "void_forest"));

    public static final RegistryKey<World> PLANKS_DIMENSION_KEY = RegistryKey.of(
            RegistryKeys.WORLD, new Identifier(HorrorMod.MOD_ID, "planks_dimension"));

    public static final RegistryKey<World> FARLAND_KEY = RegistryKey.of(
            RegistryKeys.WORLD, new Identifier(HorrorMod.MOD_ID, "farland"));

    public static void register() {
        HorrorMod.LOGGER.info("Registering Horror Dimensions...");
    }
}
