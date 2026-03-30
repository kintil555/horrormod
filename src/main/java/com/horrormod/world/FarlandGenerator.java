package com.horrormod.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Generate terrain Farland yang mirip aslinya:
 * - Dinding/pillar raksasa bergelombang seperti farland beta
 * - Terrain overshoot: kolom batu menjulang dari y=0 ke y=100+ tidak beraturan
 * - Lembah sempit di antara kolom
 * - Permukaan sangat kasar dan tidak rata
 * - Ladder naik ke permukaan
 * - End crystal di atas
 */
public class FarlandGenerator {

    private static final int SIZE    = 300;
    private static final int MAX_Y   = 110;
    private static final int SPAWN_Y = 5;

    public static void generate(ServerWorld world, BlockPos center) {
        int cx = center.getX(), cz = center.getZ();

        // Bedrock lantai
        for (int x = -SIZE/2; x < SIZE/2; x++)
            for (int z = -SIZE/2; z < SIZE/2; z++)
                world.setBlockState(new BlockPos(cx+x, 0, cz+z), Blocks.BEDROCK.getDefaultState());

        // Generate terrain farland: noise bertingkat yang overshoot
        for (int x = -SIZE/2; x < SIZE/2; x++) {
            for (int z = -SIZE/2; z < SIZE/2; z++) {
                // Farland asli: noise overflow di salah satu axis → dinding
                // Simulasi: combine noise dengan amplifikasi ekstrem di Z axis
                double noiseX = smoothNoise(x * 0.08, z * 0.02) * 0.7
                              + smoothNoise(x * 0.03, z * 0.005) * 0.3;
                double noiseZ = smoothNoise(x * 0.01, z * 0.12) * 1.5; // overshoot Z
                double combined = (noiseX + noiseZ) * 0.5;

                // Tinggi terrain: antara 5 dan MAX_Y
                int h = (int)(SPAWN_Y + Math.abs(combined) * (MAX_Y - SPAWN_Y));
                h = Math.min(h, MAX_Y);

                // Isi kolom dari bedrock ke h
                for (int y = 1; y <= h; y++) {
                    BlockState mat;
                    if (y == h)           mat = Blocks.GRASS_BLOCK.getDefaultState();
                    else if (y >= h - 3)  mat = Blocks.DIRT.getDefaultState();
                    else if (y >= h - 10) mat = Blocks.STONE.getDefaultState();
                    else                  mat = Blocks.DEEPSLATE.getDefaultState();
                    world.setBlockState(new BlockPos(cx+x, y, cz+z), mat);
                }

                // Air di lembah dalam (y < 10)
                if (h < 8) {
                    for (int y = h+1; y <= 8; y++)
                        world.setBlockState(new BlockPos(cx+x, y, cz+z), Blocks.WATER.getDefaultState());
                }
            }
        }

        // Cari y surface di spawn point untuk ladder
        int spawnSurfY = getSurfaceY(world, cx, cz);

        // Ladder dari y=1 ke surface+2
        placeLadder(world, cx, cz, spawnSurfY);

        // End crystal di permukaan center (pastikan di atas tanah)
        int crystalY = spawnSurfY + 2;
        // Platform obsidian
        for (int dx = -2; dx <= 2; dx++)
            for (int dz2 = -2; dz2 <= 2; dz2++)
                world.setBlockState(new BlockPos(cx+dx, crystalY-1, cz+dz2),
                        Blocks.OBSIDIAN.getDefaultState());
        // Bersihkan ruang di atas obsidian
        for (int dy = 0; dy <= 4; dy++)
            world.setBlockState(new BlockPos(cx, crystalY+dy, cz), Blocks.AIR.getDefaultState());

        net.minecraft.entity.decoration.EndCrystalEntity crystal =
            new net.minecraft.entity.decoration.EndCrystalEntity(world,
                cx + 0.5, crystalY, cz + 0.5);
        crystal.setShowBottom(true);
        world.spawnEntity(crystal);
    }

    private static int getSurfaceY(ServerWorld world, int x, int z) {
        for (int y = 150; y > 1; y--) {
            if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) return y + 1;
        }
        return SPAWN_Y + 2;
    }

    private static void placeLadder(ServerWorld world, int cx, int cz, int surfY) {
        // Bersihkan jalur
        for (int y = 1; y <= surfY + 2; y++) {
            world.setBlockState(new BlockPos(cx, y, cz), Blocks.AIR.getDefaultState());
            world.setBlockState(new BlockPos(cx+1, y, cz), Blocks.AIR.getDefaultState());
        }
        // Oak log tiang
        for (int y = 1; y <= surfY + 2; y++)
            world.setBlockState(new BlockPos(cx+1, y, cz), Blocks.OAK_LOG.getDefaultState());
        // Ladder
        BlockState ladder = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST);
        for (int y = 1; y <= surfY + 2; y++)
            world.setBlockState(new BlockPos(cx, y, cz), ladder);
        // Platform kecil di bawah spawn
        for (int dx = -1; dx <= 2; dx++)
            for (int dz2 = -1; dz2 <= 1; dz2++)
                world.setBlockState(new BlockPos(cx+dx, 1, cz+dz2), Blocks.STONE.getDefaultState());
    }

    // Simple layered noise tanpa library
    private static double smoothNoise(double x, double z) {
        int xi = (int)Math.floor(x), zi = (int)Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx*fx*(3-2*fx); fz = fz*fz*(3-2*fz);
        double n00 = hash(xi,   zi);
        double n10 = hash(xi+1, zi);
        double n01 = hash(xi,   zi+1);
        double n11 = hash(xi+1, zi+1);
        return lerp(lerp(n00, n10, fx), lerp(n01, n11, fx), fz);
    }

    private static double hash(int x, int z) {
        int n = x + z * 57;
        n = (n << 13) ^ n;
        return 1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
    }

    private static double lerp(double a, double b, double t) { return a + (b-a)*t; }
}
