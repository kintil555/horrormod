package com.horrormod.structure;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Salib (cross) yang terbuat dari dark oak wood.
 * Bentuk:
 *    [O]       y+4  (top)
 *    [O]       y+3
 * [O][O][O]    y+2  (horizontal arm)
 *    [O]       y+1
 *    [O]       y+0  (base)
 */
public class CrossStructure {

    private static final Block CROSS_BLOCK = Blocks.DARK_OAK_LOG;

    public static void spawn(ServerWorld world, BlockPos base) {
        // Tiang vertikal (y=0 sampai y=4)
        for (int i = 0; i <= 4; i++) {
            world.setBlockState(base.up(i), CROSS_BLOCK.getDefaultState());
        }
        // Palang horizontal di y+2 (kiri dan kanan)
        world.setBlockState(base.up(2).west(), CROSS_BLOCK.getDefaultState());
        world.setBlockState(base.up(2).east(), CROSS_BLOCK.getDefaultState());
    }

    /**
     * Mengecek apakah blok-blok utama salib masih ada di posisi tersebut.
     */
    public static boolean exists(ServerWorld world, BlockPos base) {
        // Cek minimal: tiang tengah bawah, tengah atas, dan satu palang
        return world.getBlockState(base).isOf(CROSS_BLOCK)
                && world.getBlockState(base.up(2)).isOf(CROSS_BLOCK)
                && (world.getBlockState(base.up(2).west()).isOf(CROSS_BLOCK)
                 || world.getBlockState(base.up(2).east()).isOf(CROSS_BLOCK));
    }
}
