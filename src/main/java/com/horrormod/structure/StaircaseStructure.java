package com.horrormod.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tangga dari ladder, naik 10 blok vertikal.
 * Struktur: tiang oak log sebagai "dinding" + ladder menempel di sisinya.
 * Di bawah base: pastikan bedrock ada. Di atas puncak: pastikan bedrock ada.
 * Tidak nembus plank/bedrock dimensi karena kita handle y dengan benar.
 *
 * Layout (tampak samping):
 *  y+10 → platform oak planks 3x3 + glowstone
 *  y+1 s/d y+10 → oak log (dinding) + ladder menempel
 *  y=0  → base (bedrock lantai dimensi, sudah ada)
 *
 * Player spawn di y=2, berdiri di plank y=1, bedrock y=0.
 * Tangga spawn di depan player (z-5), naik dari y=2 sampai y=12.
 * Puncak di y=13 → di sini ada platform yang menembus bedrock atap (y=4)
 * ke atas, tapi karena dimensi void di atas y=4 tidak ada apa-apa.
 */
public class StaircaseStructure {

    public static void spawn(ServerWorld world, BlockPos playerPos) {
        // Tangga spawn 5 blok di depan player (ke utara/z-)
        int bx = playerPos.getX();
        int bz = playerPos.getZ() - 5;
        // Base ladder mulai dari lantai plank (y=2, berdiri di atasnya)
        // Naik 12 blok (dari y=2 sampai y=13) menembus atap plank (y=3) dan bedrock (y=4)
        int baseY = 2;
        int height = 12;

        // Oak log sebagai tiang/dinding di belakang ladder (z+1)
        for (int i = 0; i <= height; i++) {
            BlockPos logPos = new BlockPos(bx, baseY + i, bz + 1);
            world.setBlockState(logPos, Blocks.OAK_LOG.getDefaultState());
        }

        // Ladder menempel di sisi selatan oak log (menghadap ke selatan = player bisa naik)
        BlockState ladderState = Blocks.LADDER.getDefaultState()
                .with(LadderBlock.FACING, Direction.SOUTH);
        for (int i = 0; i <= height; i++) {
            BlockPos ladderPos = new BlockPos(bx, baseY + i, bz + 1);
            // Ladder di depan log (z arah player)
            world.setBlockState(new BlockPos(bx, baseY + i, bz), ladderState);
        }

        // Bersihkan blok yang menghalangi di dalam jalur naik (y=3 plank atap & y=4 bedrock atap)
        // Buka 1 blok lubang di plank atap dan bedrock atap tepat di posisi ladder
        world.setBlockState(new BlockPos(bx, 3, bz), Blocks.AIR.getDefaultState()); // plank atap
        world.setBlockState(new BlockPos(bx, 4, bz), Blocks.AIR.getDefaultState()); // bedrock atap
        // Juga buka di posisi log biar tidak terhalang
        world.setBlockState(new BlockPos(bx, 3, bz + 1), Blocks.AIR.getDefaultState());
        world.setBlockState(new BlockPos(bx, 4, bz + 1), Blocks.AIR.getDefaultState());

        // Platform 3x3 di puncak (y = baseY + height + 1)
        int topY = baseY + height + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz2 = -1; dz2 <= 1; dz2++) {
                world.setBlockState(new BlockPos(bx + dx, topY, bz + dz2),
                        Blocks.OAK_PLANKS.getDefaultState());
            }
        }

        // Glowstone penanda di puncak
        world.setBlockState(new BlockPos(bx, topY + 1, bz),
                Blocks.GLOWSTONE.getDefaultState());
    }
}
