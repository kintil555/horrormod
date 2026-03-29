package com.horrormod.event;

import com.horrormod.HorrorMod;
import com.horrormod.structure.CrossStructure;
import com.horrormod.structure.StaircaseStructure;
import com.horrormod.world.HorrorDimensions;
import com.horrormod.world.HorrorSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class HorrorEventHandler {

    // Dimensi hutan: 64x64, center di 32,32
    private static final int FOREST_CENTER_X = 32;
    private static final int FOREST_CENTER_Z = 32;
    private static final int FOREST_HALF = 32;
    private static final int GROUND_Y = 1; // grass block di y=1

    // Dimensi planks: player harus jalan 70 block
    private static final int PLANKS_WALK_DISTANCE = 70;

    // State per player (UUID)
    private static final Map<UUID, PlayerState> playerStates = new HashMap<>();

    public static void register() {
        // Saat player join server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
            PlayerState state = new PlayerState();
            state.joinTick = server.getTicks();
            state.overWorldPos = player.getBlockPos();
            state.overWorldDim = player.getServerWorld().getRegistryKey();
            playerStates.put(uuid, state);
            HorrorMod.LOGGER.info("Player {} joined. Horror sequence started.", player.getName().getString());
        });

        // Saat player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerStates.remove(handler.getPlayer().getUuid());
        });

        // Tick utama server
        ServerTickEvents.END_SERVER_TICK.register(HorrorEventHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, PlayerState> entry : playerStates.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerState state = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

            if (player == null) {
                toRemove.add(uuid);
                continue;
            }

            ServerWorld currentWorld = player.getServerWorld();
            int currentTick = server.getTicks();

            // === FASE 1: Di Overworld, tunggu 2 menit (2400 tick) lalu teleport ke hutan ===
            if (state.phase == Phase.WAITING_IN_OVERWORLD) {
                int ticksSinceJoin = currentTick - state.joinTick;

                // Play horror ambient sound setelah 30 detik (600 tick) sebagai foreshadowing
                if (!state.playedForeshadowSound && ticksSinceJoin >= 600) {
                    state.playedForeshadowSound = true;
                    currentWorld.playSound(null, player.getBlockPos(),
                            HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1.0f, 0.8f);
                }

                // 2 menit = 2400 tick
                if (ticksSinceJoin >= 2400) {
                    // Simpan posisi overworld sebelum teleport
                    state.overWorldPos = player.getBlockPos();
                    state.overWorldDim = currentWorld.getRegistryKey();

                    // Teleport ke hutan
                    teleportToVoidForest(server, player, state);
                    state.phase = Phase.IN_VOID_FOREST;
                    state.forestEnterTick = currentTick;
                }
            }

            // === FASE 2: Di dimensi hutan void ===
            else if (state.phase == Phase.IN_VOID_FOREST) {
                if (!currentWorld.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY)) continue;

                // Play ambient terus di dimensi hutan
                if ((currentTick - state.forestEnterTick) % 620 == 0) {
                    currentWorld.playSound(null, player.getBlockPos(),
                            HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 0.7f, 1.0f);
                }

                // Cek apakah player menyentuh batas 64x64
                double px = player.getX();
                double pz = player.getZ();
                boolean hitBoundary = px < (FOREST_CENTER_X - FOREST_HALF)
                        || px > (FOREST_CENTER_X + FOREST_HALF)
                        || pz < (FOREST_CENTER_Z - FOREST_HALF)
                        || pz > (FOREST_CENTER_Z + FOREST_HALF);

                // Atau player jatuh ke void (y < 0)
                boolean hitVoid = player.getY() < 0;

                if (hitBoundary || hitVoid) {
                    // Teleport balik ke overworld
                    teleportToOverworld(server, player, state);
                    state.phase = Phase.RETURNED_FROM_FOREST;
                    state.returnTick = currentTick;

                    // Spawn struktur salib di dekat player di overworld (20 block)
                    ServerWorld overWorld = server.getWorld(state.overWorldDim);
                    if (overWorld != null) {
                        scheduleAction(state, () -> {
                            BlockPos crossPos = state.overWorldPos.add(20, 0, 0);
                            CrossStructure.spawn(overWorld, crossPos);
                            player.sendMessage(Text.literal(""), false);
                        });
                    }
                }
            }

            // === FASE 3: Kembali ke Overworld setelah hutan, tunggu salib dihancurkan ===
            else if (state.phase == Phase.RETURNED_FROM_FOREST) {
                if (currentWorld.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY)) continue;

                // Sound ambiance setelah kembali
                if (!state.playedReturnSound) {
                    state.playedReturnSound = true;
                    currentWorld.playSound(null, player.getBlockPos(),
                            HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.5f, 0.9f);
                }

                // Cek apakah salib sudah dihancurkan
                // Kita cek area sekitar posisi salib (20 block dari overWorldPos)
                BlockPos crossBase = state.overWorldPos.add(20, 0, 0);
                if (isCrossDestroyed(currentWorld, crossBase)) {
                    // Teleport ke dimensi planks
                    teleportToPlanks(server, player, state);
                    state.phase = Phase.IN_PLANKS_DIMENSION;
                    state.planksEnterPos = player.getBlockPos();
                    state.planksEnterTick = currentTick;
                }
            }

            // === FASE 4: Di dimensi planks, jalan 70 block ===
            else if (state.phase == Phase.IN_PLANKS_DIMENSION) {
                if (!currentWorld.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) continue;

                // Sound menyeramkan di planks
                if ((currentTick - state.planksEnterTick) % 580 == 0) {
                    currentWorld.playSound(null, player.getBlockPos(),
                            HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.8f, 0.7f);
                }
                if ((currentTick - state.planksEnterTick) % 300 == 0) {
                    currentWorld.playSound(null, player.getBlockPos(),
                            HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 0.6f, 1.2f);
                }

                // Pastikan langit-langit selalu ada di atas player
                enforcePlanksCeiling(currentWorld, player);

                // Hitung jarak dari titik masuk
                double dist = getHorizontalDistance(player.getPos(),
                        Vec3d.ofCenter(state.planksEnterPos));

                if (dist >= PLANKS_WALK_DISTANCE) {
                    // Spawn tangga di posisi player
                    BlockPos stairBase = player.getBlockPos();
                    StaircaseStructure.spawn(currentWorld, stairBase);
                    state.phase = Phase.FOUND_STAIRCASE;
                    player.sendMessage(Text.literal("\u00a78[ \u00a7c! \u00a78] \u00a77Kamu melihat sesuatu..."), false);
                }
            }

            // === FASE 5: Player naik tangga, cek apakah sudah di atas ===
            else if (state.phase == Phase.FOUND_STAIRCASE) {
                if (!currentWorld.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) continue;

                // Cek apakah player cukup tinggi (naik tangga ~8+ block)
                if (state.planksEnterPos != null && player.getY() > state.planksEnterPos.getY() + 6) {
                    // Teleport ke overworld dengan banyak salib
                    teleportToOverworldFinal(server, player, state);
                    state.phase = Phase.FINALE;
                }
            }

            // Jalankan scheduled actions
            if (state.scheduledAction != null) {
                state.scheduledAction.run();
                state.scheduledAction = null;
            }
        }

        playerStates.keySet().removeAll(toRemove);
    }

    // =========================================================
    //  Teleport helpers
    // =========================================================

    private static void teleportToVoidForest(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld forest = server.getWorld(HorrorDimensions.VOID_FOREST_KEY);
        if (forest == null) {
            HorrorMod.LOGGER.error("void_forest dimension not found!");
            return;
        }
        // Siapkan chunk & tanah
        prepareForestGround(forest);

        player.teleport(forest,
                FOREST_CENTER_X + 0.5,
                GROUND_Y + 1,
                FOREST_CENTER_Z + 0.5,
                player.getYaw(), player.getPitch());

        forest.playSound(null, player.getBlockPos(),
                HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 1.0f, 1.0f);

        player.sendMessage(Text.literal("\u00a78..."), false);
    }

    private static void teleportToOverworld(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld overWorld = server.getWorld(state.overWorldDim);
        if (overWorld == null) overWorld = server.getOverworld();

        BlockPos dest = state.overWorldPos;
        player.teleport(overWorld,
                dest.getX() + 0.5,
                dest.getY() + 0.5,
                dest.getZ() + 0.5,
                player.getYaw(), player.getPitch());

        // Spawn salib ~20 block ke timur dari posisi overworld
        BlockPos crossPos = state.overWorldPos.add(20, 0, 0);
        // Cari Y yang solid
        BlockPos safePos = findSolidGround(overWorld, crossPos);
        CrossStructure.spawn(overWorld, safePos);

        overWorld.playSound(null, player.getBlockPos(),
                HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1.0f, 0.5f);
    }

    private static void teleportToPlanks(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld planks = server.getWorld(HorrorDimensions.PLANKS_DIMENSION_KEY);
        if (planks == null) {
            HorrorMod.LOGGER.error("planks_dimension not found!");
            return;
        }

        // Spawn di posisi 0, 1, 0 dengan langit-langit di y=4
        preparePlanksArea(planks, new BlockPos(0, 0, 0));

        player.teleport(planks, 0.5, 2.0, 0.5, player.getYaw(), player.getPitch());

        planks.playSound(null, player.getBlockPos(),
                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 1.0f, 0.8f);

        player.sendMessage(Text.literal("\u00a78[ \u00a77... \u00a78]"), false);
    }

    private static void teleportToOverworldFinal(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld overWorld = server.getWorld(state.overWorldDim);
        if (overWorld == null) overWorld = server.getOverworld();

        BlockPos origin = state.overWorldPos;
        player.teleport(overWorld,
                origin.getX() + 0.5,
                origin.getY() + 0.5,
                origin.getZ() + 0.5,
                player.getYaw(), player.getPitch());

        // Spawn banyak salib di sekitar player (radius 30)
        spawnManyCrosses(overWorld, origin);

        overWorld.playSound(null, player.getBlockPos(),
                HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 1.0f, 0.6f);
        overWorld.playSound(null, player.getBlockPos(),
                HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1.0f, 0.4f);

        player.sendMessage(Text.literal("\u00a74\u00a7l[ \u00a7c\u00a7l... \u00a74\u00a7l]"), false);
    }

    // =========================================================
    //  World preparation helpers
    // =========================================================

    private static void prepareForestGround(ServerWorld world) {
        // Paksa-load chunk di area 64x64 dan tanam oak tree yang rapih
        int treeSpacing = 8;
        for (int x = 4; x < 64; x += treeSpacing) {
            for (int z = 4; z < 64; z += treeSpacing) {
                BlockPos treeBase = new BlockPos(x, 2, z);
                // Pastikan ground ada
                world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
                // Tanam pohon oak manual (trunk + leaves)
                placeOakTree(world, treeBase);
            }
        }
        // Isi seluruh lantai 64x64
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                if (world.getBlockState(new BlockPos(x, 0, z)).isAir()) {
                    world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
                }
                if (world.getBlockState(new BlockPos(x, 1, z)).isAir()) {
                    world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
                }
            }
        }
    }

    private static void placeOakTree(ServerWorld world, BlockPos base) {
        // Trunk 4 block
        for (int i = 0; i < 4; i++) {
            world.setBlockState(base.up(i), Blocks.OAK_LOG.getDefaultState());
        }
        // Daun di layer 3 dan 4 (3x3 dan 1x1)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                world.setBlockState(base.add(dx, 3, dz), Blocks.OAK_LEAVES.getDefaultState());
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    world.setBlockState(base.add(dx, 4, dz), Blocks.OAK_LEAVES.getDefaultState());
                }
            }
        }
        world.setBlockState(base.up(5), Blocks.OAK_LEAVES.getDefaultState());
    }

    private static void preparePlanksArea(ServerWorld world, BlockPos center) {
        // Buat koridor planks 200x200 dengan langit-langit 3 block tinggi (y=0 floor, y=3 ceiling)
        int radius = 150;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos floor = new BlockPos(center.getX() + x, 0, center.getZ() + z);
                BlockPos ceil = new BlockPos(center.getX() + x, 3, center.getZ() + z);
                world.setBlockState(floor, Blocks.OAK_PLANKS.getDefaultState());
                world.setBlockState(ceil, Blocks.OAK_PLANKS.getDefaultState());
            }
        }
    }

    private static void enforcePlanksCeiling(ServerWorld world, ServerPlayerEntity player) {
        // Pastikan langit-langit selalu ada tepat 2 block di atas player (jarak berjalan)
        int px = (int) player.getX();
        int pz = (int) player.getZ();
        int ceilY = 3; // selalu y=3
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos ceil = new BlockPos(px + dx, ceilY, pz + dz);
                BlockPos floor = new BlockPos(px + dx, 0, pz + dz);
                if (!world.getBlockState(ceil).isOf(Blocks.OAK_PLANKS)) {
                    world.setBlockState(ceil, Blocks.OAK_PLANKS.getDefaultState());
                }
                if (!world.getBlockState(floor).isOf(Blocks.OAK_PLANKS)) {
                    world.setBlockState(floor, Blocks.OAK_PLANKS.getDefaultState());
                }
            }
        }
    }

    // =========================================================
    //  Spawn multiple crosses (finale)
    // =========================================================

    private static void spawnManyCrosses(ServerWorld world, BlockPos origin) {
        int[][] offsets = {
            {10, 5}, {-10, 5}, {5, -10}, {-5, -10},
            {20, 0}, {-20, 0}, {0, 20}, {0, -20},
            {15, 15}, {-15, 15}, {15, -15}, {-15, -15}
        };
        for (int[] off : offsets) {
            BlockPos pos = findSolidGround(world, origin.add(off[0], 0, off[1]));
            CrossStructure.spawn(world, pos);
        }
    }

    // =========================================================
    //  Utility helpers
    // =========================================================

    private static boolean isCrossDestroyed(ServerWorld world, BlockPos crossBase) {
        // Salib terdiri dari: base (0), vertical (1,2,3), horizontal (-1,1 di y+2)
        // Cek apakah semua blok penyusun salib sudah hilang
        BlockPos[] crossBlocks = {
            crossBase,
            crossBase.up(1),
            crossBase.up(2),
            crossBase.up(3),
            crossBase.up(2).west(),
            crossBase.up(2).east()
        };
        int destroyedCount = 0;
        for (BlockPos bp : crossBlocks) {
            if (world.getBlockState(bp).isAir()) destroyedCount++;
        }
        // Anggap hancur jika lebih dari separuh blok sudah hilang
        return destroyedCount >= 3;
    }

    private static BlockPos findSolidGround(ServerWorld world, BlockPos pos) {
        // Cari Y solid dari atas ke bawah
        for (int y = 100; y > -64; y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(check).isAir() &&
                world.getBlockState(check.up()).isAir()) {
                return check.up();
            }
        }
        return pos;
    }

    private static double getHorizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void scheduleAction(PlayerState state, Runnable action) {
        state.scheduledAction = action;
    }

    // =========================================================
    //  Player state
    // =========================================================

    public enum Phase {
        WAITING_IN_OVERWORLD,
        IN_VOID_FOREST,
        RETURNED_FROM_FOREST,
        IN_PLANKS_DIMENSION,
        FOUND_STAIRCASE,
        FINALE
    }

    public static class PlayerState {
        public Phase phase = Phase.WAITING_IN_OVERWORLD;
        public int joinTick = 0;
        public int forestEnterTick = 0;
        public int planksEnterTick = 0;
        public int returnTick = 0;
        public BlockPos overWorldPos = BlockPos.ORIGIN;
        public RegistryKey<World> overWorldDim = World.OVERWORLD;
        public BlockPos planksEnterPos = BlockPos.ORIGIN;
        public boolean playedForeshadowSound = false;
        public boolean playedReturnSound = false;
        public Runnable scheduledAction = null;
    }
}
