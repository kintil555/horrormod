package com.horrormod.event;

import com.horrormod.HorrorMod;
import com.horrormod.entity.GrinningEntity;
import com.horrormod.entity.HorrorEntities;
import com.horrormod.network.HorrorPackets;
import com.horrormod.structure.CrossStructure;
import com.horrormod.structure.StaircaseStructure;
import com.horrormod.world.HorrorDimensions;
import com.horrormod.world.HorrorSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.*;

public class HorrorEventHandler {

    private static final int FOREST_CENTER_X     = 142;
    private static final int FOREST_CENTER_Z     = 142;
    private static final int FOREST_HALF         = 142;
    private static final int GROUND_Y            = 1;
    private static final int PLANKS_WALK_DISTANCE = 70;

    // Setelah keluar planks: delay sebelum efek finale
    private static final int LIGHTNING_DELAY_TICKS = 200;  // 10 detik
    private static final int DUSK_DELAY_TICKS      = 800;  // 40 detik setelah petir
    private static final int GRINNING_DELAY_TICKS  = 80;   // 4 detik setelah petang

    // Durasi petir brutal: 6 detik = 120 tick, radius 60 blok, tiap 1 tick 1 petir
    private static final int LIGHTNING_DURATION_TICKS = 120;
    private static final int LIGHTNING_RADIUS         = 60;

    // Durasi kontrol player oleh grinning: 20 detik = 400 tick
    private static final int POSSESSION_DURATION_TICKS = 400;

    private static final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private static final List<LightningTask>    lightningQueue = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerState state = new PlayerState();
            state.joinTick        = server.getTicks();
            state.overWorldPos    = player.getBlockPos();
            state.overWorldDimKey = player.getServerWorld().getRegistryKey();
            playerStates.put(player.getUuid(), state);
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

        // Proses antrian petir
        lightningQueue.removeIf(task -> {
            if (now >= task.tickTarget) {
                LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, task.world);
                bolt.refreshPositionAfterTeleport(task.pos.getX() + 0.5, task.pos.getY(), task.pos.getZ() + 0.5);
                bolt.setCosmetic(task.cosmetic);
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

                // ---- FASE 1: tunggu 2 menit di overworld ----
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

                // ---- FASE 2: void forest ----
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

                // ---- FASE 3: kembali ke overworld, tunggu salib dihancurkan ----
                case RETURNED_FROM_FOREST: {
                    if (world.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY) ||
                        world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) break;

                    if (!state.playedReturnSound) {
                        state.playedReturnSound = true;
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.5f, 0.9f);
                    }

                    // Cek: 1 blok salib hancur = langsung trigger, 3 detik kemudian teleport
                    if (state.crossPos != null && !state.crossTriggerPending) {
                        if (isCrossAnyBlockMissing(world, state.crossPos)) {
                            state.crossTriggerPending = true;
                            state.crossTriggerTick    = now;
                            sendJumpscare(player, -1);
                            world.playSound(null, player.getBlockPos(),
                                    HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1f, 0.4f);
                        }
                    }
                    // 3 detik kemudian teleport ke planks
                    if (state.crossTriggerPending && (now - state.crossTriggerTick) >= 60) {
                        teleportToPlanks(server, player, state);
                        state.phase           = Phase.IN_PLANKS_DIMENSION;
                        state.planksEnterPos  = player.getBlockPos();
                        state.planksEnterTick = now;
                        state.planksStartX    = player.getX();
                        state.planksStartZ    = player.getZ();
                    }
                    break;
                }

                // ---- FASE 4: planks dimension ----
                case IN_PLANKS_DIMENSION: {
                    if (!world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY)) break;

                    if ((now - state.planksEnterTick) % 580 == 10)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.8f, 0.7f);
                    if ((now - state.planksEnterTick) % 300 == 20)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 0.6f, 1.2f);
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

                // ---- FASE 5: naik tangga, kembali ke overworld ----
                case FOUND_STAIRCASE: {
                    teleportToOverworldFinal(server, player, state, now);
                    state.phase          = Phase.FINALE_LIGHTNING;
                    state.finaleStartTick = now;
                    break;
                }

                // ---- FINALE: petir brutal 6 detik ----
                case FINALE_LIGHTNING: {
                    if (world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY) ||
                        world.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY)) break;

                    int elapsed = now - state.finaleStartTick;

                    // 10 detik setelah keluar planks → petir brutal mulai
                    if (elapsed >= LIGHTNING_DELAY_TICKS && !state.lightningBrutalStarted) {
                        state.lightningBrutalStarted = true;
                        state.lightningBrutalTick    = now;
                        scheduleBrutalLightning(world, player.getBlockPos(), now);
                    }

                    // 40 detik setelah petir → world jadi petang (time 13000)
                    if (state.lightningBrutalStarted) {
                        int afterLightning = now - state.lightningBrutalTick;
                        if (afterLightning >= DUSK_DELAY_TICKS && !state.duskSet) {
                            state.duskSet = true;
                            ServerWorld ow = server.getOverworld();
                            ow.setTimeOfDay(13000); // petang
                            // Lock time supaya tetap petang
                            ow.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                        }

                        // 4 detik setelah petang → spawn grinning
                        if (state.duskSet && (now - state.lightningBrutalTick - DUSK_DELAY_TICKS) >= GRINNING_DELAY_TICKS
                                && !state.grinningSpawned) {
                            state.grinningSpawned = true;
                            boolean multiplayer = server.getPlayerManager().getPlayerList().size() > 1;
                            if (multiplayer) {
                                spawnGrinningCircle(world, player, state, server, now);
                                state.phase = Phase.FINALE_GRINNING_CIRCLE;
                                state.grinningCircleStartTick = now;
                            } else {
                                // Singleplayer: grinning langsung serang + jumpscare
                                spawnGrinningSingleplayer(world, player, state);
                                state.phase = Phase.FINALE_SINGLEPLAYER_ATTACK;
                            }
                        }
                    }
                    break;
                }

                // ---- FINALE MULTIPLAYER: grinning mengitari 4 detik lalu menyerang ----
                case FINALE_GRINNING_CIRCLE: {
                    int elapsed = now - state.grinningCircleStartTick;

                    // Rotate grinning mengelilingi player
                    rotateGrinningAround(world, player, state, elapsed);

                    // 4 detik (80 tick) → semua grinning bergerak cepat ke tengah
                    if (elapsed >= 80 && !state.grinningRushed) {
                        state.grinningRushed = true;
                        rushGrinningToCenter(world, player, state);
                        // Pilih 1 player random untuk dikendalikan
                        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                        if (players.size() > 1) {
                            ServerPlayerEntity victim = players.get(RANDOM.nextInt(players.size()));
                            state.possessedPlayerUUID = victim.getUuid();
                            state.possessionStartTick = now;
                            victim.sendMessage(Text.literal("\u00a74\u00a7l[!] \u00a7cSomething has taken control..."), false);
                        }
                        state.phase = Phase.FINALE_POSSESSION;
                    }
                    break;
                }

                // ---- FINALE: satu player dikendalikan 20 detik ----
                case FINALE_POSSESSION: {
                    if (state.possessedPlayerUUID != null) {
                        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(state.possessedPlayerUUID);
                        int elapsed = now - state.possessionStartTick;

                        if (victim != null && elapsed < POSSESSION_DURATION_TICKS) {
                            // Paksa victim menyerang player terdekat yang bukan dirinya
                            if (elapsed % 10 == 0) {
                                ServerPlayerEntity target = getNearestOtherPlayer(server, victim);
                                if (target != null) {
                                    victim.attack(target);
                                    // Slowness pada victim biar tidak lari
                                    victim.addStatusEffect(new StatusEffectInstance(
                                            StatusEffects.SLOWNESS, 25, 2, true, false));
                                }
                            }
                        } else {
                            // Selesai
                            state.possessedPlayerUUID = null;
                            state.phase = Phase.DONE;
                        }
                    } else {
                        state.phase = Phase.DONE;
                    }
                    break;
                }

                // ---- FINALE SINGLEPLAYER: grinning serang + jumpscare ----
                case FINALE_SINGLEPLAYER_ATTACK: {
                    // Grinning sudah di-spawn dengan AI attack, phase ini langsung jumpscare
                    sendJumpscare(player, -1);
                    state.phase = Phase.DONE;
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
        sendJumpscare(player, -1);
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
            lightningQueue.add(new LightningTask(ow, crossPos, now + i * 20, true));

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

    // =========================================================
    //  Lightning
    // =========================================================

    /**
     * Petir brutal: radius 60 blok, tiap tick 1 petir random, selama 6 detik (120 tick).
     * Petir ini DEAL DAMAGE (cosmetic=false).
     */
    private static void scheduleBrutalLightning(ServerWorld world, BlockPos center, int now) {
        for (int t = 0; t < LIGHTNING_DURATION_TICKS; t++) {
            double angle  = RANDOM.nextDouble() * 2 * Math.PI;
            double radius = RANDOM.nextDouble() * LIGHTNING_RADIUS;
            int bx = (int)(center.getX() + Math.cos(angle) * radius);
            int bz = (int)(center.getZ() + Math.sin(angle) * radius);
            BlockPos pos = findSolidGround(world, new BlockPos(bx, center.getY(), bz));
            lightningQueue.add(new LightningTask(world, pos, now + t, false)); // cosmetic=false = berdamage
        }
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
                lightningQueue.add(new LightningTask(world, pos, now + i * 20, true));
        }
    }

    // =========================================================
    //  Grinning helpers
    // =========================================================

    /** Spawn grinning melingkar radius 7 blok mengelilingi player (multiplayer) */
    private static void spawnGrinningCircle(ServerWorld world, ServerPlayerEntity player,
                                             PlayerState state, MinecraftServer server, int now) {
        int count = 8;
        state.grinningEntities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle  = (2 * Math.PI / count) * i;
            double gx     = player.getX() + Math.cos(angle) * 7;
            double gz     = player.getZ() + Math.sin(angle) * 7;
            BlockPos gPos = findSolidGround(world, new BlockPos((int)gx, (int)player.getY(), (int)gz));

            GrinningEntity g = HorrorEntities.GRINNING.create(world);
            if (g == null) continue;
            g.refreshPositionAndAngles(gPos.getX() + 0.5, gPos.getY(), gPos.getZ() + 0.5,
                    (float)(Math.toDegrees(angle) + 180), 0);
            g.setAiDisabled(true); // disable AI saat berputar
            world.spawnEntity(g);
            state.grinningEntities.add(g.getUuid());
        }
        state.grinningInitialAngle = 0;
    }

    /** Rotate grinning mengelilingi player setiap tick */
    private static void rotateGrinningAround(ServerWorld world, ServerPlayerEntity player,
                                              PlayerState state, int elapsed) {
        if (state.grinningEntities == null) return;
        int count = state.grinningEntities.size();
        double baseAngle = elapsed * 0.05; // kecepatan putar

        for (int i = 0; i < count; i++) {
            UUID gUUID = state.grinningEntities.get(i);
            net.minecraft.entity.Entity e = ((ServerWorld)player.getWorld()).getEntity(gUUID);
            if (!(e instanceof GrinningEntity g)) continue;
            double angle = baseAngle + (2 * Math.PI / count) * i;
            double gx    = player.getX() + Math.cos(angle) * 7;
            double gz    = player.getZ() + Math.sin(angle) * 7;
            g.teleport(gx, player.getY(), gz);
            g.setYaw((float)(Math.toDegrees(angle) + 180));
        }
    }

    /** Semua grinning gerak cepat ke player (enable AI, speed boost) */
    private static void rushGrinningToCenter(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        if (state.grinningEntities == null) return;
        for (UUID gUUID : state.grinningEntities) {
            net.minecraft.entity.Entity e = ((ServerWorld)player.getWorld()).getEntity(gUUID);
            if (!(e instanceof GrinningEntity g)) continue;
            g.setAiDisabled(false); // aktifkan AI → langsung kejar player
            // Speed boost brutal
            g.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 5, true, false));
        }
    }

    /** Spawn grinning langsung menyerang di singleplayer */
    private static void spawnGrinningSingleplayer(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        GrinningEntity g = HorrorEntities.GRINNING.create(world);
        if (g == null) return;
        BlockPos pos = player.getBlockPos().add(3, 0, 0);
        g.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        g.setTarget(player);
        world.spawnEntity(g);
        state.grinningEntities = new ArrayList<>();
        state.grinningEntities.add(g.getUuid());
    }

    /** Dapat player lain terdekat (bukan diri sendiri) */
    private static ServerPlayerEntity getNearestOtherPlayer(MinecraftServer server, ServerPlayerEntity self) {
        ServerPlayerEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getUuid().equals(self.getUuid())) continue;
            double d = p.squaredDistanceTo(self);
            if (d < minDist) { minDist = d; nearest = p; }
        }
        return nearest;
    }

    // =========================================================
    //  World builders
    // =========================================================

    private static void prepareForestGround(ServerWorld world) {
        int size = 285;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
        for (int x = 4; x < size; x += 8)
            for (int z = 4; z < size; z += 8)
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
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int bx = center.getX() + x, bz = center.getZ() + z;
                world.setBlockState(new BlockPos(bx, 0, bz), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(bx, 1, bz), Blocks.OAK_PLANKS.getDefaultState());
                world.setBlockState(new BlockPos(bx, 4, bz), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(bx, 3, bz), Blocks.OAK_PLANKS.getDefaultState());
            }
        }
    }

    private static void enforcePlanksBedrock(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        int px = (int) player.getX(), pz = (int) player.getZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int bx = px + dx, bz2 = pz + dz;
                if (!world.getBlockState(new BlockPos(bx, 0, bz2)).isOf(Blocks.BEDROCK))
                    world.setBlockState(new BlockPos(bx, 0, bz2), Blocks.BEDROCK.getDefaultState());
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

    /** True jika SATU BLOK SAJA dari salib sudah hilang */
    private static boolean isCrossAnyBlockMissing(ServerWorld world, BlockPos base) {
        BlockPos[] pts = {
            base, base.up(1), base.up(2), base.up(3), base.up(4), base.up(5),
            base.up(3).west(), base.up(3).west().west(),
            base.up(3).east(), base.up(3).east().east()
        };
        for (BlockPos p : pts) if (world.getBlockState(p).isAir()) return true;
        return false;
    }

    private static BlockPos findSolidGround(ServerWorld world, BlockPos pos) {
        for (int y = 100; y > -64; y--) {
            BlockPos c = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(c).isAir() && world.getBlockState(c.up()).isAir()) return c.up();
        }
        return pos;
    }

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
        final boolean cosmetic;
        LightningTask(ServerWorld w, BlockPos p, int t, boolean cosmetic) {
            world=w; pos=p; tickTarget=t; this.cosmetic=cosmetic;
        }
    }

    public enum Phase {
        WAITING_IN_OVERWORLD, IN_VOID_FOREST, RETURNED_FROM_FOREST,
        IN_PLANKS_DIMENSION, FOUND_STAIRCASE,
        FINALE_LIGHTNING, FINALE_GRINNING_CIRCLE, FINALE_POSSESSION,
        FINALE_SINGLEPLAYER_ATTACK, DONE
    }

    public static class PlayerState {
        public Phase  phase             = Phase.WAITING_IN_OVERWORLD;
        public int    joinTick          = 0;
        public int    forestEnterTick   = 0;
        public int    planksEnterTick   = 0;
        public int    returnTick        = 0;
        public int    finaleStartTick   = 0;
        public int    lightningBrutalTick = 0;
        public int    grinningCircleStartTick = 0;
        public int    possessionStartTick = 0;
        public int    crossTriggerTick  = 0;

        public BlockPos           overWorldPos    = BlockPos.ORIGIN;
        public RegistryKey<World> overWorldDimKey = World.OVERWORLD;
        public BlockPos           crossPos        = null;
        public BlockPos           planksEnterPos  = BlockPos.ORIGIN;
        public double             planksStartX    = 0;
        public double             planksStartZ    = 0;
        public int                staircaseTopY   = 0;
        public UUID               possessedPlayerUUID = null;

        public List<UUID> grinningEntities = null;
        public double     grinningInitialAngle = 0;

        public boolean staircaseSpawned       = false;
        public boolean playedForeshadowSound  = false;
        public boolean playedReturnSound      = false;
        public boolean crossTriggerPending    = false;
        public boolean lightningBrutalStarted = false;
        public boolean duskSet                = false;
        public boolean grinningSpawned        = false;
        public boolean grinningRushed         = false;
    }
}
