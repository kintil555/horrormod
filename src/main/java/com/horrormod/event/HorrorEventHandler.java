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

import java.util.*;

public class HorrorEventHandler {

    private static final int FOREST_CENTER_X = 32;
    private static final int FOREST_CENTER_Z = 32;
    private static final int FOREST_HALF     = 32;
    private static final int GROUND_Y        = 1;
    private static final int PLANKS_WALK_DISTANCE = 70;

    // Setiap player punya state sendiri — multiplayer safe
    private static final Map<UUID, PlayerState> playerStates = new HashMap<>();

    // Antrian petir: list (world, pos, tickTarget) agar tidak registrasi listener berulang
    private static final List<LightningTask> lightningQueue = new ArrayList<>();

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

        // Proses antrian petir (satu event listener saja, tidak bertumpuk)
        lightningQueue.removeIf(task -> {
            if (now >= task.tickTarget) {
                LightningEntity bolt = new LightningEntity(
                        net.minecraft.entity.EntityType.LIGHTNING_BOLT, task.world);
                bolt.refreshPositionAfterTeleport(task.pos.getX() + 0.5, task.pos.getY(), task.pos.getZ() + 0.5);
                bolt.setCosmetic(true); // tidak membakar / tidak merusak blok
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
                        state.phase       = Phase.RETURNED_FROM_FOREST;
                        state.returnTick  = now;
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
                    // Cek salib milik player INI saja
                    if (state.crossPos != null && isCrossDestroyed(world, state.crossPos)) {
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

                    enforcePlanksCeiling(world, player);

                    // Jarak dihitung dari titik masuk player ITU SENDIRI
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
    }

    private static void teleportToOverworld(MinecraftServer server, ServerPlayerEntity player,
                                            PlayerState state, int now) {
        ServerWorld ow = server.getWorld(state.overWorldDimKey);
        if (ow == null) ow = server.getOverworld();

        BlockPos dest = state.overWorldPos;
        player.teleport(ow, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5,
                player.getYaw(), player.getPitch());

        // Salib 20 block ke timur — unik per-player karena disimpan di state player
        BlockPos crossPos = findSolidGround(ow, dest.add(20, 0, 0));
        state.crossPos = crossPos;
        CrossStructure.spawn(ow, crossPos);

        // 5 petir dalam 5 detik (tiap 20 tick) — ditambah ke antrian global
        for (int i = 0; i < 5; i++) {
            lightningQueue.add(new LightningTask(ow, crossPos, now + i * 20));
        }

        ow.playSound(null, player.getBlockPos(), HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1f, 0.5f);
    }

    private static void teleportToPlanks(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld planks = server.getWorld(HorrorDimensions.PLANKS_DIMENSION_KEY);
        if (planks == null) { HorrorMod.LOGGER.error("[HorrorMod] planks_dimension not found!"); return; }

        // Offset per-player biar tidak tumpang tindih di multiplayer
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

    // =========================================================
    //  Lightning queue helper
    // =========================================================

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

    private static void prepareForestGround(ServerWorld world) {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
            world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
            world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
        }
        for (int x = 4; x < 64; x += 8) for (int z = 4; z < 64; z += 8)
            placeOakTree(world, new BlockPos(x, 2, z));
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

    private static void preparePlanksArea(ServerWorld world, BlockPos center) {
        int r = 200;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            world.setBlockState(new BlockPos(center.getX()+x, 0, center.getZ()+z), Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(new BlockPos(center.getX()+x, 3, center.getZ()+z), Blocks.OAK_PLANKS.getDefaultState());
        }
    }

    private static void enforcePlanksCeiling(ServerWorld world, ServerPlayerEntity player) {
        int px = (int) player.getX(), pz = (int) player.getZ();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            BlockPos f = new BlockPos(px+dx, 0, pz+dz);
            BlockPos c = new BlockPos(px+dx, 3, pz+dz);
            if (!world.getBlockState(f).isOf(Blocks.OAK_PLANKS)) world.setBlockState(f, Blocks.OAK_PLANKS.getDefaultState());
            if (!world.getBlockState(c).isOf(Blocks.OAK_PLANKS)) world.setBlockState(c, Blocks.OAK_PLANKS.getDefaultState());
        }
    }

    // =========================================================
    //  Utilities
    // =========================================================

    private static boolean isCrossDestroyed(ServerWorld world, BlockPos base) {
        BlockPos[] pts = {base, base.up(1), base.up(2), base.up(3), base.up(2).west(), base.up(2).east()};
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
        public BlockPos          overWorldPos    = BlockPos.ORIGIN;
        public RegistryKey<World> overWorldDimKey = World.OVERWORLD;
        public BlockPos          crossPos        = null;   // salib milik player ini
        public BlockPos          planksEnterPos  = BlockPos.ORIGIN;
        public double            planksStartX    = 0;
        public double            planksStartZ    = 0;
        public int               staircaseTopY   = 0;
        public boolean           staircaseSpawned     = false;
        public boolean           playedForeshadowSound = false;
        public boolean           playedReturnSound     = false;
    }
}
