package com.horrormod.network;

import com.horrormod.HorrorMod;
import net.minecraft.util.Identifier;

public class HorrorPackets {
    /** S2C: Trigger jumpscare. Payload: 1 byte index (-1=random, 0-6=spesifik) */
    public static final Identifier JUMPSCARE = new Identifier(HorrorMod.MOD_ID, "jumpscare");
    /** S2C: Trigger screen shake + purple sky + error texture effect */
    public static final Identifier FARLAND_CHAOS = new Identifier(HorrorMod.MOD_ID, "farland_chaos");
    /** S2C: Stop farland chaos effect */
    public static final Identifier FARLAND_CHAOS_STOP = new Identifier(HorrorMod.MOD_ID, "farland_chaos_stop");
}
