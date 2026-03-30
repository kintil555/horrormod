package com.horrormod.event;

import com.horrormod.HorrorMod;
import com.horrormod.entity.GrinningEntity;
import com.horrormod.entity.HorrorEntities;
import com.horrormod.network.HorrorPackets;
import com.horrormod.structure.CrossStructure;
import com.horrormod.structure.StaircaseStructure;
import com.horrormod.world.FarlandGenerator;
import com.horrormod.world.HorrorDimensions;
import com.horrormod.world.HorrorSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.*;

public class HorrorEventHandler {

    // ---- Void Forest ----
    private static final int FOREST_CENTER_X      = 142;
    private static final int FOREST_CENTER_Z      = 142;
    private static final int FOREST_HALF          = 142;
    private static final int GROUND_Y             = 1;
    private static final int PLANKS_WALK_DISTANCE = 70;

    // ---- Finale timing ----
    private static final int LIGHTNING_DELAY_TICKS    = 200;   // 10 detik setelah keluar planks
    private static final int DUSK_DELAY_TICKS         = 800;   // 40 detik setelah petir
    private static final int GRINNING_DELAY_TICKS     = 80;    // 4 detik setelah petang
    private static final int LIGHTNING_DURATION_TICKS = 120;   // 6 detik petir brutal
    private static final int LIGHTNING_RADIUS         = 60;
    private static final int POSSESSION_DURATION_TICKS = 400;  // 20 detik
    private static final int FARLAND_DELAY_TICKS      = 3600;  // 3 menit setelah serang grinning

    // ---- Farland ----
    private static final int FARLAND_SPAWN_Y  = 6;   // y spawn di bawah permukaan
    private static final int FARLAND_SURFACE_Y = 100; // permukaan atas

    // Semua player di server berbagi state farland (event global)
    private static boolean   farlandCrystalDestroyed = false;
    private static int       farlandChaosStartTick   = -1;
    private static boolean   farlandPortalsSpawned   = false;
    private static BlockPos  farlandCenter           = new BlockPos(0, FARLAND_SPAWN_Y, 0);

    // Track waktu selesai fase grinning per-group (untuk teleport bareng ke farland)
    private static int  grinningDoneGlobalTick = -1;
    private static boolean farlandScheduled    = false;

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
                bolt.refreshPositionAfterTeleport(task.pos.getX()+.5, task.pos.getY(), task.pos.getZ()+.5);
                bolt.setCosmetic(task.cosmetic);
                task.world.spawnEntity(bolt);
                return true;
            }
            return false;
        });

        // ---- Global: teleport semua player ke farland 3 menit setelah grinning done ----
        if (grinningDoneGlobalTick > 0 && !farlandScheduled
                && (now - grinningDoneGlobalTick) >= FARLAND_DELAY_TICKS) {
            farlandScheduled = true;
            teleportAllToFarland(server, now);
        }

        // ---- Global: cek end crystal di farland ----
        if (farlandScheduled && !farlandCrystalDestroyed) {
            checkFarlandCrystal(server, now);
        }

        // ---- Global: efek chaos farland ----
        if (farlandCrystalDestroyed && farlandChaosStartTick > 0) {
            int chaosElapsed = now - farlandChaosStartTick;

            // Spawn portal-portal setelah 2 detik chaos
            if (chaosElapsed >= 40 && !farlandPortalsSpawned) {
                farlandPortalsSpawned = true;
                ServerWorld farland = server.getWorld(HorrorDimensions.FARLAND_KEY);
                if (farland != null) spawnReturnPortals(farland, server, now);
            }
        }

        // ---- Per-player state machine ----
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
                    if (state.crossPos != null && !state.crossTriggerPending) {
                        if (isCrossAnyBlockMissing(world, state.crossPos)) {
                            state.crossTriggerPending = true;
                            state.crossTriggerTick    = now;
                            sendJumpscare(player, -1);
                            world.playSound(null, player.getBlockPos(),
                                    HorrorSounds.RADIO_GLITCH, SoundCategory.AMBIENT, 1f, 0.4f);
                        }
                    }
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

                case FOUND_STAIRCASE: {
                    teleportToOverworldFinal(server, player, state, now);
                    state.phase           = Phase.FINALE_LIGHTNING;
                    state.finaleStartTick = now;
                    break;
                }

                case FINALE_LIGHTNING: {
                    if (world.getRegistryKey().equals(HorrorDimensions.PLANKS_DIMENSION_KEY) ||
                        world.getRegistryKey().equals(HorrorDimensions.VOID_FOREST_KEY)) break;

                    int elapsed = now - state.finaleStartTick;
                    if (elapsed >= LIGHTNING_DELAY_TICKS && !state.lightningBrutalStarted) {
                        state.lightningBrutalStarted = true;
                        state.lightningBrutalTick    = now;
                        scheduleBrutalLightning(world, player.getBlockPos(), now);
                    }
                    if (state.lightningBrutalStarted) {
                        int afterLightning = now - state.lightningBrutalTick;
                        if (afterLightning >= DUSK_DELAY_TICKS && !state.duskSet) {
                            state.duskSet = true;
                            ServerWorld ow = server.getOverworld();
                            ow.setTimeOfDay(13000);
                            ow.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                        }
                        if (state.duskSet
                                && (now - state.lightningBrutalTick - DUSK_DELAY_TICKS) >= GRINNING_DELAY_TICKS
                                && !state.grinningSpawned) {
                            state.grinningSpawned = true;
                            boolean multiplayer = server.getPlayerManager().getPlayerList().size() > 1;
                            if (multiplayer) {
                                spawnGrinningCircle(world, player, state, server, now);
                                state.phase = Phase.FINALE_GRINNING_CIRCLE;
                                state.grinningCircleStartTick = now;
                            } else {
                                spawnGrinningSingleplayer(world, player, state);
                                state.phase = Phase.FINALE_SINGLEPLAYER_ATTACK;
                            }
                        }
                    }
                    break;
                }

                case FINALE_GRINNING_CIRCLE: {
                    int elapsed = now - state.grinningCircleStartTick;
                    rotateGrinningAround(world, player, state, elapsed);
                    if (elapsed >= 80 && !state.grinningRushed) {
                        state.grinningRushed = true;
                        rushGrinningToCenter(world, player, state);
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

                case FINALE_POSSESSION: {
                    if (state.possessedPlayerUUID != null) {
                        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(state.possessedPlayerUUID);
                        int elapsed = now - state.possessionStartTick;
                        if (victim != null && elapsed < POSSESSION_DURATION_TICKS) {
                            if (elapsed % 10 == 0) {
                                ServerPlayerEntity target = getNearestOtherPlayer(server, victim);
                                if (target != null) {
                                    victim.attack(target);
                                    victim.addStatusEffect(new StatusEffectInstance(
                                            StatusEffects.SLOWNESS, 25, 2, true, false));
                                }
                            }
                        } else {
                            // Possession selesai — tandai global tick untuk jadwal farland
                            if (grinningDoneGlobalTick < 0) grinningDoneGlobalTick = now;
                            state.possessedPlayerUUID = null;
                            state.phase = Phase.WAITING_FARLAND;
                            player.sendMessage(Text.literal("\u00a78[ \u00a77... \u00a78]"), false);
                        }
                    } else {
                        if (grinningDoneGlobalTick < 0) grinningDoneGlobalTick = now;
                        state.phase = Phase.WAITING_FARLAND;
                    }
                    break;
                }

                case FINALE_SINGLEPLAYER_ATTACK: {
                    sendJumpscare(player, -1);
                    if (grinningDoneGlobalTick < 0) grinningDoneGlobalTick = now;
                    state.phase = Phase.WAITING_FARLAND;
                    break;
                }

                case WAITING_FARLAND:
                    // Tunggu global timer — teleportAllToFarland() akan handle
                    break;

                case IN_FARLAND: {
                    if (!world.getRegistryKey().equals(HorrorDimensions.FARLAND_KEY)) break;
                    // Ambient sound liminal
                    if ((now - state.farlandEnterTick) % 400 == 10)
                        world.playSound(null, player.getBlockPos(),
                                HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 0.5f, 0.5f);
                    break;
                }

                default: break;
            }
        }
        playerStates.keySet().removeAll(toRemove);
    }

    // =========================================================
    //  Farland logic
    // =========================================================

    /** Teleport SEMUA player ke farland sekaligus (multiplayer-safe) */
    private static void teleportAllToFarland(MinecraftServer server, int now) {
        ServerWorld farland = server.getWorld(HorrorDimensions.FARLAND_KEY);
        if (farland == null) {
            HorrorMod.LOGGER.error("[HorrorMod] Farland not found!");
            return;
        }
        // Generate terrain farland satu kali
        FarlandGenerator.generate(farland, farlandCenter);

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            player.teleport(farland,
                farlandCenter.getX() + 0.5, FARLAND_SPAWN_Y + 1, farlandCenter.getZ() + 0.5,
                player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("\u00a78[ \u00a77... \u00a78] \u00a7fKamu tidak tahu di mana ini."), false);

            PlayerState state = playerStates.get(player.getUuid());
            if (state != null) {
                state.phase           = Phase.IN_FARLAND;
                state.farlandEnterTick = now;
            }
        }
    }

    /** Cek apakah end crystal di farland sudah hancur */
    private static void checkFarlandCrystal(MinecraftServer server, int now) {
        ServerWorld farland = server.getWorld(HorrorDimensions.FARLAND_KEY);
        if (farland == null) return;

        // Cek area sekitar center apakah masih ada end crystal
        Box searchBox = new Box(
            farlandCenter.getX() - 20, FARLAND_SURFACE_Y - 5, farlandCenter.getZ() - 20,
            farlandCenter.getX() + 20, FARLAND_SURFACE_Y + 15, farlandCenter.getZ() + 20);
        List<EndCrystalEntity> crystals = farland.getEntitiesByClass(
            EndCrystalEntity.class, searchBox, e -> true);

        if (crystals.isEmpty() && !farlandCrystalDestroyed) {
            farlandCrystalDestroyed = true;
            farlandChaosStartTick   = now;

            // Kirim packet chaos ke semua player di farland
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.getServerWorld().getRegistryKey().equals(HorrorDimensions.FARLAND_KEY)) {
                    sendPacketEmpty(p, HorrorPackets.FARLAND_CHAOS);
                }
            }

            // Set langit/waktu aneh di farland
            farland.setTimeOfDay(18000); // tengah malam penuh
            farland.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);

            HorrorMod.LOGGER.info("[HorrorMod] End crystal destroyed — Farland chaos started!");
        }
    }

    /**
     * Spawn portal-portal kembali ke overworld.
     * Portal pakai nether portal block dalam bentuk frame standar.
     * Multiplayer: satu portal per player, tersebar di sekitar center.
     */
    private static void spawnReturnPortals(ServerWorld farland, MinecraftServer server, int now) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        int count = Math.max(players.size(), 4);

        int surfY = 5;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            int px = farlandCenter.getX() + (int)(Math.cos(angle) * 20);
            int pz = farlandCenter.getZ() + (int)(Math.sin(angle) * 20);

            // Cari permukaan tanah yang sesungguhnya (bukan hardcode y)
            surfY = 5;
            for (int y = 150; y > 1; y--) {
                if (!farland.getBlockState(new BlockPos(px, y, pz)).isAir()) {
                    surfY = y + 1; // tepat di atas tanah
                    break;
                }
            }
            // Bersihkan area portal dulu
            for (int dx = 0; dx <= 3; dx++)
                for (int dy = 0; dy <= 5; dy++)
                    farland.setBlockState(new BlockPos(px+dx, surfY+dy, pz), Blocks.AIR.getDefaultState());

            BlockPos base = new BlockPos(px, surfY, pz);
            buildNetherPortal(farland, base);
        }

        // Broadcast ke semua player: stop chaos setelah portal spawn
        // (biarkan chaos tetap tapi kurangi intensitas = tidak perlu stop)
        // Kirim pesan ke semua player
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal("\u00a7d\u00a7l[ PORTAL TERBUKA ] \u00a7rMasuk portal untuk kembali!"), false);
        }

        // Set portal sebagai teleporter via tick event (cek proximity)
        // Ini dihandle di fase IN_FARLAND via block interaction
        // Tapi karena nether portal vanilla sudah handle teleport sendiri,
        // kita override destinasi lewat DimensionType respawn_anchor_works=false
        // dan di-teleport manual via entity event
        schedulePortalTeleportCheck(farland, server, now, surfY, count);
    }

    /** Build frame nether portal + fill portal block */
    private static void buildNetherPortal(ServerWorld world, BlockPos base) {
        // Frame obsidian (4 wide, 5 tall)
        for (int y = 0; y <= 4; y++) {
            world.setBlockState(base.add(0, y, 0), Blocks.OBSIDIAN.getDefaultState());
            world.setBlockState(base.add(3, y, 0), Blocks.OBSIDIAN.getDefaultState());
        }
        for (int x = 0; x <= 3; x++) {
            world.setBlockState(base.add(x, 0, 0), Blocks.OBSIDIAN.getDefaultState());
            world.setBlockState(base.add(x, 4, 0), Blocks.OBSIDIAN.getDefaultState());
        }
        // Fill portal block di dalam frame (2x3)
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(base.add(x, y, 0),
                    Blocks.NETHER_PORTAL.getDefaultState()
                        .with(net.minecraft.block.NetherPortalBlock.AXIS,
                              net.minecraft.util.math.Direction.Axis.X));
            }
        }
    }

    /** Jadwal tick loop cek player masuk portal → teleport ke overworld */
    private static void schedulePortalTeleportCheck(ServerWorld farland, MinecraftServer server,
                                                     int now, int surfY, int portalCount) {
        // Simpan posisi portal untuk dicek setiap tick
        // Menggunakan ServerTickEvents baru — lebih simpel: cek di fase IN_FARLAND
        // Flag global bahwa portal sudah aktif
        portalCheckActive = true;
        portalSurfY       = surfY;
        portalSpawnedTick = now;
        portalCountGlobal = portalCount;
    }

    private static boolean portalCheckActive = false;
    private static int     portalSurfY       = 100;
    private static int     portalSpawnedTick = -1;
    private static int     portalCountGlobal = 4;

    /** Cek player menyentuh nether portal block di farland → teleport ke overworld */
    public static void checkPortalEntry(MinecraftServer server, ServerPlayerEntity player, int now) {
        if (!portalCheckActive) return;
        if (!player.getServerWorld().getRegistryKey().equals(HorrorDimensions.FARLAND_KEY)) return;

        BlockPos pp = player.getBlockPos();
        // Cek apakah player berdiri di/dekat nether portal block
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = pp.add(dx, dy, dz);
                    if (player.getServerWorld().getBlockState(check).isOf(Blocks.NETHER_PORTAL)) {
                        // Teleport kembali ke overworld!
                        ServerWorld ow = server.getWorld(player.getServerWorld().getRegistryKey()
                                .equals(HorrorDimensions.FARLAND_KEY)
                                ? World.OVERWORLD : World.OVERWORLD);
                        ow = server.getOverworld();
                        PlayerState state = playerStates.get(player.getUuid());
                        BlockPos dest = (state != null) ? state.overWorldPos : new BlockPos(0, 64, 0);
                        player.teleport(ow, dest.getX()+.5, dest.getY()+1, dest.getZ()+.5,
                                player.getYaw(), player.getPitch());
                        // Stop chaos di client player ini
                        sendPacketEmpty(player, HorrorPackets.FARLAND_CHAOS_STOP);
                        // Restore daylight
                        ow.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, server);
                        if (state != null) state.phase = Phase.DONE;
                        return;
                    }
                }
            }
        }
    }

    // =========================================================
    //  Teleport helpers
    // =========================================================

    private static void teleportToVoidForest(MinecraftServer server, ServerPlayerEntity player, PlayerState state) {
        ServerWorld forest = server.getWorld(HorrorDimensions.VOID_FOREST_KEY);
        if (forest == null) { HorrorMod.LOGGER.error("[HorrorMod] void_forest not found!"); return; }
        prepareForestGround(forest);
        player.teleport(forest, FOREST_CENTER_X+.5, GROUND_Y+1, FOREST_CENTER_Z+.5,
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
        player.teleport(ow, dest.getX()+.5, dest.getY()+1, dest.getZ()+.5,
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
        player.teleport(planks, spawn.getX()+.5, 2.0, spawn.getZ()+.5,
                player.getYaw(), player.getPitch());
        planks.playSound(null, player.getBlockPos(), HorrorSounds.VOID_AMBIANCE, SoundCategory.AMBIENT, 1f, 0.8f);
        player.sendMessage(Text.literal("\u00a78[ \u00a77... \u00a78]"), false);
    }

    private static void teleportToOverworldFinal(MinecraftServer server, ServerPlayerEntity player,
                                                   PlayerState state, int now) {
        ServerWorld ow = server.getWorld(state.overWorldDimKey);
        if (ow == null) ow = server.getOverworld();
        BlockPos origin = state.overWorldPos;
        player.teleport(ow, origin.getX()+.5, origin.getY()+1, origin.getZ()+.5,
                player.getYaw(), player.getPitch());
        spawnManyCrosses(ow, origin, now);
        ow.playSound(null, player.getBlockPos(), HorrorSounds.HORROR_AMBIENT, SoundCategory.AMBIENT, 1f, 0.6f);
        ow.playSound(null, player.getBlockPos(), HorrorSounds.RADIO_GLITCH,   SoundCategory.AMBIENT, 1f, 0.4f);
        player.sendMessage(Text.literal("\u00a74\u00a7l[ \u00a7c\u00a7l... \u00a74\u00a7l]"), false);
    }

    // =========================================================
    //  Lightning
    // =========================================================

    private static void scheduleBrutalLightning(ServerWorld world, BlockPos center, int now) {
        for (int t = 0; t < LIGHTNING_DURATION_TICKS; t++) {
            double angle  = RANDOM.nextDouble() * 2 * Math.PI;
            double radius = RANDOM.nextDouble() * LIGHTNING_RADIUS;
            int bx = (int)(center.getX() + Math.cos(angle) * radius);
            int bz = (int)(center.getZ() + Math.sin(angle) * radius);
            BlockPos pos = findSolidGround(world, new BlockPos(bx, center.getY(), bz));
            lightningQueue.add(new LightningTask(world, pos, now + t, false));
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

    private static void spawnGrinningCircle(ServerWorld world, ServerPlayerEntity player,
                                             PlayerState state, MinecraftServer server, int now) {
        int count = 8;
        state.grinningEntities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double gx = player.getX() + Math.cos(angle) * 7;
            double gz = player.getZ() + Math.sin(angle) * 7;
            BlockPos gPos = findSolidGround(world, new BlockPos((int)gx, (int)player.getY(), (int)gz));
            GrinningEntity g = HorrorEntities.GRINNING.create(world);
            if (g == null) continue;
            g.refreshPositionAndAngles(gPos.getX()+.5, gPos.getY(), gPos.getZ()+.5,
                    (float)(Math.toDegrees(angle)+180), 0);
            g.setAiDisabled(true);
            world.spawnEntity(g);
            state.grinningEntities.add(g.getUuid());
        }
    }

    private static void rotateGrinningAround(ServerWorld world, ServerPlayerEntity player,
                                              PlayerState state, int elapsed) {
        if (state.grinningEntities == null) return;
        int count = state.grinningEntities.size();
        double baseAngle = elapsed * 0.05;
        for (int i = 0; i < count; i++) {
            net.minecraft.entity.Entity e = world.getEntity(state.grinningEntities.get(i));
            if (!(e instanceof GrinningEntity g)) continue;
            double angle = baseAngle + (2 * Math.PI / count) * i;
            g.teleport(player.getX() + Math.cos(angle)*7, player.getY(),
                       player.getZ() + Math.sin(angle)*7);
            g.setYaw((float)(Math.toDegrees(angle)+180));
        }
    }

    private static void rushGrinningToCenter(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        if (state.grinningEntities == null) return;
        for (UUID gUUID : state.grinningEntities) {
            net.minecraft.entity.Entity e = world.getEntity(gUUID);
            if (!(e instanceof GrinningEntity g)) continue;
            g.setAiDisabled(false);
            g.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 5, true, false));
        }
    }

    private static void spawnGrinningSingleplayer(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        GrinningEntity g = HorrorEntities.GRINNING.create(world);
        if (g == null) return;
        BlockPos pos = player.getBlockPos().add(3, 0, 0);
        g.refreshPositionAndAngles(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        g.setTarget(player);
        world.spawnEntity(g);
        state.grinningEntities = new ArrayList<>();
        state.grinningEntities.add(g.getUuid());
    }

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
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++) {
                world.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK.getDefaultState());
                world.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.getDefaultState());
            }
        for (int x = 4; x < size; x += 8)
            for (int z = 4; z < size; z += 8)
                placeOakTree(world, new BlockPos(x, 2, z));
    }

    private static void placeOakTree(ServerWorld world, BlockPos base) {
        for (int i = 0; i < 4; i++) world.setBlockState(base.up(i), Blocks.OAK_LOG.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx)==2 && Math.abs(dz)==2) continue;
            world.setBlockState(base.add(dx,3,dz), Blocks.OAK_LEAVES.getDefaultState());
            if (Math.abs(dx)<=1 && Math.abs(dz)<=1)
                world.setBlockState(base.add(dx,4,dz), Blocks.OAK_LEAVES.getDefaultState());
        }
        world.setBlockState(base.up(5), Blocks.OAK_LEAVES.getDefaultState());
    }

    private static void preparePlanksArea(ServerWorld world, BlockPos center) {
        int r = 200;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            int bx = center.getX()+x, bz = center.getZ()+z;
            world.setBlockState(new BlockPos(bx,0,bz), Blocks.BEDROCK.getDefaultState());
            world.setBlockState(new BlockPos(bx,1,bz), Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(new BlockPos(bx,4,bz), Blocks.BEDROCK.getDefaultState());
            world.setBlockState(new BlockPos(bx,3,bz), Blocks.OAK_PLANKS.getDefaultState());
        }
    }

    private static void enforcePlanksBedrock(ServerWorld world, ServerPlayerEntity player, PlayerState state) {
        int px = (int)player.getX(), pz = (int)player.getZ();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int bx = px+dx, bz2 = pz+dz;
            if (!world.getBlockState(new BlockPos(bx,0,bz2)).isOf(Blocks.BEDROCK))
                world.setBlockState(new BlockPos(bx,0,bz2), Blocks.BEDROCK.getDefaultState());
            if (!state.staircaseSpawned)
                if (!world.getBlockState(new BlockPos(bx,4,bz2)).isOf(Blocks.BEDROCK))
                    world.setBlockState(new BlockPos(bx,4,bz2), Blocks.BEDROCK.getDefaultState());
        }
    }

    // =========================================================
    //  Utilities
    // =========================================================

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
        for (int y = 150; y > -64; y--) {
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

    private static void sendPacketEmpty(ServerPlayerEntity player, net.minecraft.util.Identifier id) {
        ServerPlayNetworking.send(player, id, PacketByteBufs.empty());
    }

    // =========================================================
    //  Inner classes
    // =========================================================

    private static class LightningTask {
        final ServerWorld world; final BlockPos pos; final int tickTarget; final boolean cosmetic;
        LightningTask(ServerWorld w, BlockPos p, int t, boolean c) { world=w; pos=p; tickTarget=t; cosmetic=c; }
    }

    public enum Phase {
        WAITING_IN_OVERWORLD, IN_VOID_FOREST, RETURNED_FROM_FOREST,
        IN_PLANKS_DIMENSION, FOUND_STAIRCASE,
        FINALE_LIGHTNING, FINALE_GRINNING_CIRCLE, FINALE_POSSESSION,
        FINALE_SINGLEPLAYER_ATTACK, WAITING_FARLAND, IN_FARLAND, DONE
    }

    public static class PlayerState {
        public Phase  phase             = Phase.WAITING_IN_OVERWORLD;
        public int    joinTick=0, forestEnterTick=0, planksEnterTick=0, returnTick=0;
        public int    finaleStartTick=0, lightningBrutalTick=0;
        public int    grinningCircleStartTick=0, possessionStartTick=0;
        public int    crossTriggerTick=0, farlandEnterTick=0;
        public BlockPos           overWorldPos    = BlockPos.ORIGIN;
        public RegistryKey<World> overWorldDimKey = World.OVERWORLD;
        public BlockPos           crossPos        = null;
        public BlockPos           planksEnterPos  = BlockPos.ORIGIN;
        public double planksStartX=0, planksStartZ=0;
        public int    staircaseTopY=0;
        public UUID   possessedPlayerUUID = null;
        public List<UUID> grinningEntities = null;
        public double grinningInitialAngle = 0;
        public boolean staircaseSpawned=false, playedForeshadowSound=false;
        public boolean playedReturnSound=false, crossTriggerPending=false;
        public boolean lightningBrutalStarted=false, duskSet=false;
        public boolean grinningSpawned=false, grinningRushed=false;
    }
}
