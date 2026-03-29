package com.horrormod.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tangga dari ladder, naik 12 blok vertikal.
 * Oak log sebagai tiang, ladder menempel di depannya.
 * Menembus bedrock atap dengan membuka lubang di posisi ladder.
 */
public class StaircaseStructure {

    public static void spawn(ServerWorld world, BlockPos playerPos) {
        int bx = playerPos.getX();
        int bz = playerPos.getZ() - 5;
        int baseY = 2;
        int height = 12;

        // Tiang oak log
        for (int i = 0; i <= height; i++)
            world.setBlockState(new BlockPos(bx, baseY + i, bz + 1), Blocks.OAK_LOG.getDefaultState());

        // Ladder menghadap selatan (player bisa naik)
        BlockState ladderState = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH);
        for (int i = 0; i <= height; i++)
            world.setBlockState(new BlockPos(bx, baseY + i, bz), ladderState);

        // Buka lubang di plank atap (y=3) dan bedrock atap (y=4) supaya tidak terblokir
        world.setBlockState(new BlockPos(bx, 3, bz),     Blocks.AIR.getDefaultState());
        world.setBlockState(new BlockPos(bx, 4, bz),     Blocks.AIR.getDefaultState());
        world.setBlockState(new BlockPos(bx, 3, bz + 1), Blocks.AIR.getDefaultState());
        world.setBlockState(new BlockPos(bx, 4, bz + 1), Blocks.AIR.getDefaultState());

        // Platform 3x3 di puncak
        int topY = baseY + height + 1;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz2 = -1; dz2 <= 1; dz2++)
                world.setBlockState(new BlockPos(bx + dx, topY, bz + dz2), Blocks.OAK_PLANKS.getDefaultState());

        // Glowstone penanda
        world.setBlockState(new BlockPos(bx, topY + 1, bz), Blocks.GLOWSTONE.getDefaultState());
    }
}
