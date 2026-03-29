package com.horrormod.structure;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Salib (cross) dari cobblestone, berdiri vertikal.
 *    [X]       y+5
 *    [X]       y+4
 * [X][X][X]    y+3  (palang, 2 blok kiri & kanan)
 *    [X]       y+2
 *    [X]       y+1
 *    [X]       y+0
 */
public class CrossStructure {

    private static final Block CROSS_BLOCK = Blocks.COBBLESTONE;

    public static void spawn(ServerWorld world, BlockPos base) {
        for (int i = 0; i <= 5; i++)
            world.setBlockState(base.up(i), CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).west(),        CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).west().west(), CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).east(),        CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).east().east(), CROSS_BLOCK.getDefaultState());
    }

    public static boolean exists(ServerWorld world, BlockPos base) {
        return world.getBlockState(base).isOf(CROSS_BLOCK)
                && world.getBlockState(base.up(3)).isOf(CROSS_BLOCK)
                && (world.getBlockState(base.up(3).west()).isOf(CROSS_BLOCK)
                 || world.getBlockState(base.up(3).east()).isOf(CROSS_BLOCK));
    }
}
