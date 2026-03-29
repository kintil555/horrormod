package com.horrormod.network;

import com.horrormod.HorrorMod;
import net.minecraft.util.Identifier;

public class HorrorPackets {
    /** S2C: Trigger jumpscare. Payload: 1 byte = index (0-6), -1 = random */
    public static final Identifier JUMPSCARE = new Identifier(HorrorMod.MOD_ID, "jumpscare");
}
