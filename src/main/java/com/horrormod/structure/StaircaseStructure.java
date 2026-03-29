package com.horrormod.structure;

import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tangga dari oak planks naik 8 block ke arah utara.
 * Platform 3x3 + glowstone di puncak sebagai penanda.
 */
public class StaircaseStructure {

    public static void spawn(ServerWorld world, BlockPos base) {
        for (int step = 0; step < 8; step++) {
            int x = base.getX();
            int y = base.getY() + step;
            int z = base.getZ() - step;

            // Anak tangga
            world.setBlockState(new BlockPos(x, y, z),
                    Blocks.OAK_STAIRS.getDefaultState()
                            .with(StairsBlock.FACING, Direction.NORTH)
                            .with(StairsBlock.HALF, BlockHalf.BOTTOM));

            // Dinding kiri kanan
            world.setBlockState(new BlockPos(x - 1, y, z), Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(new BlockPos(x + 1, y, z), Blocks.OAK_PLANKS.getDefaultState());

            // Langit-langit sempit di atas tangga
            world.setBlockState(new BlockPos(x,     y + 2, z), Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(new BlockPos(x - 1, y + 2, z), Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(new BlockPos(x + 1, y + 2, z), Blocks.OAK_PLANKS.getDefaultState());
        }

        // Platform 3x3 di puncak
        int topY = base.getY() + 8;
        int topZ = base.getZ() - 8;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                world.setBlockState(new BlockPos(base.getX() + dx, topY, topZ + dz),
                        Blocks.OAK_PLANKS.getDefaultState());

        // Penanda glowstone di puncak
        world.setBlockState(new BlockPos(base.getX(), topY + 1, topZ),
                Blocks.GLOWSTONE.getDefaultState());
    }
}
