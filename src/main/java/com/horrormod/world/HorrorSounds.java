package com.horrormod.world;

import com.horrormod.HorrorMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class HorrorSounds {

    public static final Identifier HORROR_AMBIENT_ID = new Identifier(HorrorMod.MOD_ID, "horror_ambient");
    public static final Identifier VOID_AMBIANCE_ID = new Identifier(HorrorMod.MOD_ID, "void_ambiance");
    public static final Identifier RADIO_GLITCH_ID = new Identifier(HorrorMod.MOD_ID, "radio_glitch");

    public static SoundEvent HORROR_AMBIENT;
    public static SoundEvent VOID_AMBIANCE;
    public static SoundEvent RADIO_GLITCH;

    public static void register() {
        HORROR_AMBIENT = Registry.register(Registries.SOUND_EVENT, HORROR_AMBIENT_ID,
                SoundEvent.of(HORROR_AMBIENT_ID));
        VOID_AMBIANCE = Registry.register(Registries.SOUND_EVENT, VOID_AMBIANCE_ID,
                SoundEvent.of(VOID_AMBIANCE_ID));
        RADIO_GLITCH = Registry.register(Registries.SOUND_EVENT, RADIO_GLITCH_ID,
                SoundEvent.of(RADIO_GLITCH_ID));

        HorrorMod.LOGGER.info("Registered Horror Sounds.");
    }
}
