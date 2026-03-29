package com.horrormod.structure;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Salib cobblestone berdiri vertikal + iron block di tengah sebagai umpan.
 * Struktur:
 *    [C]       y+5
 *    [C]       y+4
 * [C][I][C]    y+3  (palang, I = iron block sebagai umpan)
 *    [C]       y+2
 *    [C]       y+1
 *    [C]       y+0
 */
public class CrossStructure {

    private static final Block CROSS_BLOCK = Blocks.COBBLESTONE;
    private static final Block LURE_BLOCK  = Blocks.IRON_BLOCK; // umpan di tengah

    public static void spawn(ServerWorld world, BlockPos base) {
        // Tiang vertikal y=0..y=5
        for (int i = 0; i <= 5; i++)
            world.setBlockState(base.up(i), CROSS_BLOCK.getDefaultState());
        // Iron block di tengah palang (y+3) menggantikan cobblestone
        world.setBlockState(base.up(3), LURE_BLOCK.getDefaultState());
        // Palang kiri-kanan y+3
        world.setBlockState(base.up(3).west(),        CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).west().west(), CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).east(),        CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(3).east().east(), CROSS_BLOCK.getDefaultState());
    }

    public static boolean exists(ServerWorld world, BlockPos base) {
        return (world.getBlockState(base).isOf(CROSS_BLOCK))
                && (world.getBlockState(base.up(3)).isOf(LURE_BLOCK)
                 || world.getBlockState(base.up(3)).isOf(CROSS_BLOCK))
                && (world.getBlockState(base.up(3).west()).isOf(CROSS_BLOCK)
                 || world.getBlockState(base.up(3).east()).isOf(CROSS_BLOCK));
    }
}
