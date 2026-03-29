package com.horrormod.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OakStairsBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tangga dari planks yang naik 8 block ke atas, arah utara (NORTH).
 * Setiap step: satu stair block + platform kecil.
 * Di atas tangga ada platform 3x3 dari oak planks.
 */
public class StaircaseStructure {

    public static void spawn(ServerWorld world, BlockPos base) {
        Block stairBlock = Blocks.OAK_STAIRS;
        Block plankBlock = Blocks.OAK_PLANKS;

        // Buat anak tangga, naik ke utara
        // Setiap step: geser +1 z (utara) dan +1 y
        for (int step = 0; step < 8; step++) {
            int x = base.getX();
            int y = base.getY() + step;
            int z = base.getZ() - step; // mundur ke utara

            BlockPos stairPos = new BlockPos(x, y, z);

            BlockState stairState = stairBlock.getDefaultState()
                    .with(StairsBlock.FACING, Direction.NORTH)
                    .with(StairsBlock.HALF, net.minecraft.block.enums.BlockHalf.BOTTOM);

            world.setBlockState(stairPos, stairState);

            // Dinding kiri kanan tangga (opsional, biar terasa koridor)
            world.setBlockState(new BlockPos(x - 1, y, z), plankBlock.getDefaultState());
            world.setBlockState(new BlockPos(x + 1, y, z), plankBlock.getDefaultState());

            // Tutup langit-langit di atas tangga supaya tetap sempit
            world.setBlockState(new BlockPos(x, y + 2, z), plankBlock.getDefaultState());
            world.setBlockState(new BlockPos(x - 1, y + 2, z), plankBlock.getDefaultState());
            world.setBlockState(new BlockPos(x + 1, y + 2, z), plankBlock.getDefaultState());
        }

        // Platform di puncak tangga (3x3)
        int topY = base.getY() + 8;
        int topZ = base.getZ() - 8;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(base.getX() + dx, topY, topZ + dz),
                        plankBlock.getDefaultState());
            }
        }

        // Satu blok "pintu" kosong di atas platform sebagai petunjuk visual
        // (bisa ditandai dengan glowstone atau beacon kecil)
        world.setBlockState(new BlockPos(base.getX(), topY + 1, topZ),
                Blocks.GLOWSTONE.getDefaultState());
    }
}
