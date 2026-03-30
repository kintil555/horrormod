package com.horrormod.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Generate terrain Farland:
 * - Batu raksasa menjulang (pillar/kolom stone/gravel/dirt tidak teratur)
 * - Permukaan kasar di atas (y=80-120)
 * - Ladder naik ke permukaan
 * - Air biru mengalir di celah-celah (liminal/eerily calm)
 * - End crystal di atas permukaan
 * - Area 400x400 blok
 */
public class FarlandGenerator {

    private static final int SIZE      = 400;
    private static final int CENTER_X  = 0;
    private static final int CENTER_Z  = 0;
    private static final int BASE_Y    = 5;
    private static final int TOP_Y     = 100;

    public static void generate(ServerWorld world, BlockPos spawnPoint) {
        Random rand = new Random(spawnPoint.asLong());
        int cx = spawnPoint.getX();
        int cz = spawnPoint.getZ();

        // Bedrock lantai
        for (int x = -SIZE/2; x < SIZE/2; x++)
            for (int z = -SIZE/2; z < SIZE/2; z++)
                world.setBlockState(new BlockPos(cx+x, BASE_Y-1, cz+z), Blocks.BEDROCK.getDefaultState());

        // Generate kolom-kolom batu raksasa farland
        for (int x = -SIZE/2; x < SIZE/2; x += 3) {
            for (int z = -SIZE/2; z < SIZE/2; z += 3) {
                int height = 40 + rand.nextInt(60); // 40-100 blok tinggi
                Block mat = pickMaterial(rand);
                for (int y = BASE_Y; y < BASE_Y + height; y++) {
                    // Lebar kolom mengecil ke atas (taper)
                    int halfW = Math.max(1, (height - (y - BASE_Y)) / 15 + 1);
                    for (int dx = -halfW; dx <= halfW; dx++) {
                        for (int dz2 = -halfW; dz2 <= halfW; dz2++) {
                            if (rand.nextInt(6) == 0) continue; // lubang acak
                            BlockPos bp = new BlockPos(cx+x+dx, y, cz+z+dz2);
                            if (world.getBlockState(bp).isAir())
                                world.setBlockState(bp, mat.getDefaultState());
                        }
                    }
                }
                // Air di celah (kadang-kadang)
                if (rand.nextInt(12) == 0) {
                    int waterY = BASE_Y + rand.nextInt(30);
                    world.setBlockState(new BlockPos(cx+x, waterY, cz+z), Blocks.WATER.getDefaultState());
                }
            }
        }

        // Permukaan atas: lapisan stone/gravel di y=TOP_Y
        for (int x = -SIZE/2; x < SIZE/2; x++) {
            for (int z = -SIZE/2; z < SIZE/2; z++) {
                int surfY = TOP_Y - 5 + rand.nextInt(10);
                for (int y = surfY; y <= surfY + 3; y++) {
                    BlockPos bp = new BlockPos(cx+x, y, cz+z);
                    if (world.getBlockState(bp).isAir()) {
                        Block mat2 = rand.nextInt(3) == 0 ? Blocks.GRAVEL : Blocks.STONE;
                        world.setBlockState(bp, mat2.getDefaultState());
                    }
                }
            }
        }

        // Ladder naik dari bawah ke permukaan di posisi spawn
        placeLadder(world, cx, cz);

        // End crystal di atas permukaan (center area)
        placeEndCrystal(world, new BlockPos(cx, TOP_Y + 5, cz));
    }

    private static Block pickMaterial(Random rand) {
        return switch (rand.nextInt(5)) {
            case 0  -> Blocks.GRAVEL;
            case 1  -> Blocks.DIRT;
            case 2  -> Blocks.COBBLESTONE;
            case 3  -> Blocks.DEEPSLATE;
            default -> Blocks.STONE;
        };
    }

    /** Ladder naik dari BASE_Y ke TOP_Y+2 di posisi cx, cz */
    private static void placeLadder(ServerWorld world, int cx, int cz) {
        // Oak log sebagai tiang di belakang ladder
        for (int y = BASE_Y; y <= TOP_Y + 3; y++) {
            world.setBlockState(new BlockPos(cx+1, y, cz), Blocks.OAK_LOG.getDefaultState());
        }
        // Ladder menempel di sisi barat log (arah west = player menghadap east untuk naik)
        net.minecraft.block.BlockState ladderState = Blocks.LADDER.getDefaultState()
                .with(net.minecraft.block.LadderBlock.FACING, net.minecraft.util.math.Direction.WEST);
        for (int y = BASE_Y; y <= TOP_Y + 3; y++) {
            world.setBlockState(new BlockPos(cx, y, cz), ladderState);
        }
        // Platform kecil di bawah ladder biar gampang naik
        for (int dx = -1; dx <= 2; dx++)
            for (int dz2 = -1; dz2 <= 1; dz2++)
                world.setBlockState(new BlockPos(cx+dx, BASE_Y-1, cz+dz2), Blocks.STONE.getDefaultState());
    }

    /** Spawn end crystal di atas stone platform */
    private static void placeEndCrystal(ServerWorld world, BlockPos pos) {
        // Platform bedrock untuk end crystal
        for (int dx = -2; dx <= 2; dx++)
            for (int dz2 = -2; dz2 <= 2; dz2++)
                world.setBlockState(pos.add(dx, -1, dz2), Blocks.OBSIDIAN.getDefaultState());

        // End crystal berdiri di atas platform
        net.minecraft.entity.decoration.EndCrystalEntity crystal =
            new net.minecraft.entity.decoration.EndCrystalEntity(world,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        crystal.setShowBottom(true);
        world.spawnEntity(crystal);
    }
}
