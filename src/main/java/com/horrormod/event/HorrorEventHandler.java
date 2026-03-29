package com.horrormod.event;

import com.horrormod.HorrorMod;
import com.horrormod.structure.CrossStructure;
import com.horrormod.structure.StaircaseStructure;
import com.horrormod.world.HorrorDimensions;
import com.horrormod.world.HorrorSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LightningEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.horrormod.network.HorrorPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import java.util.*;

public class HorrorEventHandler {

    // ---- Void Forest: 285x285, center di 142,142 ----
    private static final int FOREST_CENTER_X = 142;
    private static final int FOREST_CENTER_Z = 142;
    private static final int FOREST_HALF     = 142; // 285/2 dibulatkan
    private static final int GROUND_Y        = 1;
    private static final int PLANKS_WALK_DISTANCE = 70;

    private static final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private static final List<LightningTask> lightningQueue  = new ArrayList<>();

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerState state = new PlayerState();
            state.joinTick        = server.getTicks();
            state.overWorldPos    = player.getBlockPos();
            state.overWorldDimKey = player.getServerWorld().getRegistryKey();
            playerStates.put(player.getUuid(), state);
            HorrorMod.LOGGER.info("[HorrorMod] {} joined — horror sequence started.", player.getName().getString());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            playerStates.remove(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(HorrorEventHandler::onServerTick);
    }

    // =========================================================
    //  Main tick loop
    // =========================================================

    private static void onServerTick(MinecraftServer server) {
        int now = server.getTicks();

        lightningQueue.removeIf(task -> {
            if (now >= task.tickTarget) {
                LightningEntity bolt = new LightningEntity(
                        net.minecraft.entity.EntityType.LIGHTNING_BOLT, task.world);
                bolt.refreshPositionAfterTeleport(task.pos.getX() + 0.5, task.pos.getY(), task.pos.getZ() + 0.5);
                bolt.setCosmetic(true);
                task.world.spawnEntity(bolt);
                return true;
            }
            return false;
        });

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, PlayerState> entry : playerStates.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerState state = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) { toRemove.add(uuid); continue; }

            ServerWorld world = player.getServerWorld();

            switch (state.phase) {

                case WAITING_IN_OVERWORLD: {
                    int elapsed = now - state.joinTick;
                    if (!state.playedForeshadowSound && elapsed >= 600) {
                        state.playedForeshadowSound = true;
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1.0f, 0.8f);
                    }
                    if (elapsed >= 2400) {
                        state.overWorldPos    = player.getBlockPos();
                        state.overWorldDimKey = world.getRegistryKey();
                        teleportToVoidForest(server, player, state);
                        state.phase           = Phase.IN_VOID_FOREST;
                        state.forestEnterTick = now;
                    }
                    break;
                }

                case IN_VOID_FOREST: {
                    if (!world.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY)) break;
                    if ((now - state.forestEnterTick) % 620 == 10)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 0.7f, 1.0f);

                    double px = player.getX(), pz = player.getZ();
                    boolean boundary = px < (FOREST_CENTER_X - FOREST_HALF)
                            || px > (FOREST_CENTER_X + FOREST_HALF)
                            || pz < (FOREST_CENTER_Z - FOREST_HALF)
                            || pz > (FOREST_CENTER_Z + FOREST_HALF);
                    if (boundary || player.getY() < 0) {
                        teleportToOverworld(server, player, state, now);
                        state.phase      = Phase.RETURNED_FROM_FOREST;
                        state.returnTick = now;
                    }
                    break;
                }

                case RETURNED_FROM_FOREST: {
                    if (world.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY) ||
                        world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) break;

                    if (!state.playedReturnSound) {
                        state.playedReturnSound = true;
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.5f, 0.9f);
                    }
                    if (state.crossPos != null && isCrossDestroyed(world, state.crossPos)) {
                        sendJumpscare(player, -1); // jumpscare saat salib hancur
                        teleportToPlanks(server, player, state);
                        state.phase           = Phase.IN_PLANKS_DIMENSION;
                        state.planksEnterPos  = player.getBlockPos();
                        state.planksEnterTick = now;
                        state.planksStartX    = player.getX();
                        state.planksStartZ    = player.getZ();
                    }
                    break;
                }

                case IN_PLANKS_DIMENSION: {
                    if (!world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) break;

                    if ((now - state.planksEnterTick) % 580 == 10)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.8f, 0.7f);
                    if ((now - state.planksEnterTick) % 300 == 20)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 0.6f, 1.2f);

                    // Random jumpscare tiap ~30 detik di planks dimension
                    if ((now - state.planksEnterTick) % 600 == 50)
                        sendJumpscare(player, -1);

                    enforcePlanksBedrock(world, player, state);

                    double dist = Math.sqrt(
                        Math.pow(player.getX() - state.planksStartX, 2) +
                        Math.pow(player.getZ() - state.planksStartZ, 2));

                    if (dist >= PLANKS_WALK_DISTANCE && !state.staircaseSpawned) {
                        state.staircaseSpawned = true;
                        BlockPos base = player.getBlockPos();
                        StaircaseStructure.spawn(world, base);
                        state.staircaseTopY = base.getY() + 8;
                        player.sendMessage(Text.literal("\u00a78[ \u00a7c! \u00a78] \u00a77Kamu melihat sesuatu..."), false);
                    }
                    if (state.staircaseSpawned && player.getY() >= state.staircaseTopY) {
                        state.phase = Phase.FOUND_STAIRCASE;
                    }
                    break;
                }

                case FOUND_STAIRCASE: {
                    teleportToOverworldFinal(server, player, state, now);
                    state.phase = Phase.FINALE;
                    break;
                }

                default: break;
            }
        }
        playerStates.keySet().removeAll(toRemove);
    }

    // =========================================================
    //  Teleport helpers
    // =========================================================

    private static void teleportToVoidForest(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld forest = server.getWorld(HorrorDimensions.VOID_FOREST_KEY);
        if (forest == null) { HorrorMod.LOGGER.error("[HorrorMod] void_forest not found!"); return; }
        prepareForestGround(forest);
        player.teleport(forest, FOREST_CENTER_X + 0.5, GROUND_Y + 1, FOREST_CENTER_Z + 0.5,
                player.getYaw(), player.getPitch());
        forest.playSound(null, player.getBlockPos(), HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 1f, 1f);
        player.sendMessage(Text.literal("\u00a78..."), false);
        sendJumpscare(player, -1); // random jumpscare saat masuk void forest
    }

    private static void teleportToOverworld(MinecraftServer server, ServerPlayerEntity player,
                                            PlayerState state, int now) {
        ServerWorld ow = server.getWorld(state.overWorldDimKey);
        if (ow == null) ow = server.getOverworld();
        BlockPos dest = state.overWorldPos;
        player.teleport(ow, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5,
                player.getYaw(), player.getPitch());

        BlockPos crossPos = findSolidGround(ow, dest.add(20, 0, 0));
        state.crossPos = crossPos;
        CrossStructure.spawn(ow, crossPos);

        for (int i = 0; i < 5; i++)
            lightningQueue.add(new LightningTask(ow, crossPos, now + i * 20));

        ow.playSound(null, player.getBlockPos(), HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1f, 0.5f);
    }

    private static void teleportToPlanks(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld planks = server.getWorld(HorrorDimensions.PLANKS_DIMENSION_KEY);
        if (planks == null) { HorrorMod.LOGGER.error("[HorrorMod] planks_dimension not found!"); return; }

        int offsetX = Math.abs(player.getUuid().hashCode() % 4096) * 300;
        BlockPos spawn = new BlockPos(offsetX, 1, 0);
        preparePlanksArea(planks, spawn);
        player.teleport(planks, spawn.getX() + 0.5, 2.0, spawn.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        planks.playSound(null, player.getBlockPos(), HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 1f, 0.8f);
        player.sendMessage(Text.literal("\u00a78[ \u00a77... \u00a78]"), false);
    }

    private static void teleportToOverworldFinal(MinecraftServer server, ServerPlayerEntity player,
                                                  PlayerState state, int now) {
        ServerWorld ow = server.getWorld(state.overWorldDimKey);
        if (ow == null) ow = server.getOverworld();
        BlockPos origin = state.overWorldPos;
        player.teleport(ow, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        spawnManyCrosses(ow, origin, now);
        ow.playSound(null, player.getBlockPos(), HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 1f, 0.6f);
        ow.playSound(null, player.getBlockPos(), HorrorSounds.RADIO_GLITCH,   SoundCategory.AMBIENT, 1f, 0.4f);
        player.sendMessage(Text.literal("\u00a74\u00a7l[ \u00a7c\u00a7l... \u00a74\u00a7l]"), false);
    }

    private static void spawnManyCrosses(ServerWorld world, BlockPos origin, int now) {
        int[][] offsets = {
            {10,5},{-10,5},{5,-10},{-5,-10},
            {20,0},{-20,0},{0,20},{0,-20},
            {15,15},{-15,15},{15,-15},{-15,-15}
        };
        for (int[] off : offsets) {
            BlockPos pos = findSolidGround(world, origin.add(off[0], 0, off[1]));
            CrossStructure.spawn(world, pos);
            for (int i = 0; i < 5; i++)
                lightningQueue.add(new LightningTask(world, pos, now + i * 20));
        }
    }

    // =========================================================
    //  World builders
    // =========================================================

    /**
     * Void Forest: 285x285 (0..284), tengah di 142,142.
     * Lantai: bedrock y=0, grass y=1.
     * Di luar area → void (chunk tidak di-generate = drop ke void).
     * Pohon tiap 8 blok dalam area.
     */
    private static void prepareForestGround(ServerWorld world) {
        int size = 285;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
        // Pohon tiap 8 block, mulai offset 4 biar ada space dari tepi
        for (int x = 4; x < size; x += 8) {
            for (int z = 4; z < size; z += 8) {
                placeOakTree(world, new BlockPos(x, 2, z));
            }
        }
    }

    private static void placeOakTree(ServerWorld world, BlockPos base) {
        for (int i = 0; i < 4; i++) world.setBlockState(base.up(i), Blocks.OAK_LOG.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            world.setBlockState(base.add(dx, 3, dz), Blocks.OAK_LEAVES.getDefaultState());
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)
                world.setBlockState(base.add(dx, 4, dz), Blocks.OAK_LEAVES.getDefaultState());
        }
        world.setBlockState(base.up(5), Blocks.OAK_LEAVES.getDefaultState());
    }

    /**
     * Planks dimension:
     * y=0 → BEDROCK (tidak bisa dihancurkan)
     * y=1 → OAK_PLANKS (dekorasi di atas bedrock)
     * y=4 → BEDROCK atap (tidak bisa dihancurkan)
     * y=3 → OAK_PLANKS atap dekorasi
     * Ruang bermain: y=2 (berdiri di y=2)
     */
    private static void preparePlanksArea(ServerWorld world, BlockPos center) {
        int r = 200;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int bx = center.getX() + x;
                int bz = center.getZ() + z;
                // Lantai
                world.setBlockState(new BlockPos(bx, 0, bz), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(bx, 1, bz), Blocks.OAK_PLANKS.getDefaultState());
                // Atap
                world.setBlockState(new BlockPos(bx, 4, bz), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(bx, 3, bz), Blocks.OAK_PLANKS.getDefaultState());
            }
        }
    }

    /**
     * Hanya jaga bedrock lantai (y=0) dan bedrock atap (y=4) di sekitar player.
     * Plank (y=1 dan y=3) BEBAS dihancurkan player — tidak di-restore.
     * Bedrock di-restore kalau hilang (tidak mungkin di survival, tapi jaga-jaga).
     */
    private static void enforcePlanksBedrock(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        int px = (int) player.getX(), pz = (int) player.getZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int bx = px + dx, bz2 = pz + dz;
                // Hanya restore bedrock lantai y=0 — jangan restore atap y=4
                // karena tangga perlu buka lubang di bedrock atap
                if (!world.getBlockState(new BlockPos(bx, 0, bz2)).isOf(Blocks.BEDROCK))
                    world.setBlockState(new BlockPos(bx, 0, bz2), Blocks.BEDROCK.getDefaultState());
                // Restore bedrock atap y=4 HANYA jika tangga belum spawn
                // Setelah tangga spawn, biarkan lubang tetap terbuka
                if (!state.staircaseSpawned) {
                    if (!world.getBlockState(new BlockPos(bx, 4, bz2)).isOf(Blocks.BEDROCK))
                        world.setBlockState(new BlockPos(bx, 4, bz2), Blocks.BEDROCK.getDefaultState());
                }
            }
        }
    }

    // =========================================================
    //  Utilities
    // =========================================================

    private static boolean isCrossDestroyed(ServerWorld world, BlockPos base) {
        // Salib cobblestone, tiang y0-y5, palang di y3
        BlockPos[] pts = {base, base.up(1), base.up(2), base.up(3), base.up(3).west(), base.up(3).east()};
        int gone = 0;
        for (BlockPos p : pts) if (world.getBlockState(p).isAir()) gone++;
        return gone >= 3;
    }

    private static BlockPos findSolidGround(ServerWorld world, BlockPos pos) {
        for (int y = 100; y > -64; y--) {
            BlockPos c = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(c).isAir() && world.getBlockState(c.up()).isAir()) return c.up();
        }
        return pos;
    }

    // =========================================================
    //  Jumpscare helper
    // =========================================================

    /**
     * Kirim packet jumpscare ke player tertentu.
     * @param index -1 = random, 0-6 = gambar spesifik
     */
    private static void sendJumpscare(ServerPlayerEntity player, int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(index);
        ServerPlayNetworking.send(player, HorrorPackets.JUMPSCARE, buf);
    }

    // =========================================================
    //  Inner classes
    // =========================================================

    private static class LightningTask {
        final ServerWorld world;
        final BlockPos pos;
        final int tickTarget;
        LightningTask(ServerWorld w, BlockPos p, int t) { world=w; pos=p; tickTarget=t; }
    }

    public enum Phase {
        WAITING_IN_OVERWORLD, IN_VOID_FOREST, RETURNED_FROM_FOREST,
        IN_PLANKS_DIMENSION, FOUND_STAIRCASE, FINALE
    }

    public static class PlayerState {
        public Phase phase             = Phase.WAITING_IN_OVERWORLD;
        public int   joinTick          = 0;
        public int   forestEnterTick   = 0;
        public int   planksEnterTick   = 0;
        public int   returnTick        = 0;
        public BlockPos           overWorldPos    = BlockPos.ORIGIN;
        public RegistryKey<World> overWorldDimKey = World.OVERWORLD;
        public BlockPos           crossPos        = null;
        public BlockPos           planksEnterPos  = BlockPos.ORIGIN;
        public double             planksStartX    = 0;
        public double             planksStartZ    = 0;
        public int                staircaseTopY   = 0;
        public boolean            staircaseSpawned      = false;
        public boolean            playedForeshadowSound = false;
        public boolean            playedReturnSound     = false;
    }
}
