package org.gwfx.universaltool.waypoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.space.PersonalDimensionRegistry;

/**
 * Manages the dedicated enclosed Wormhole Transit Corridor dimension ({@code universal_tool:wormhole_transit}),
 * completely independent of Private Space. Builds a dark deepslate tunnel with a translucent cyan-glass
 * walkway over glowing void-core blocks, stepped cyan/amethyst energy arches, and an exit wormhole ahead.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class WormholeCorridorManager {
    public static final ResourceLocation TRANSIT_DIM_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "wormhole_transit");
    public static final ResourceKey<Level> TRANSIT_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, TRANSIT_DIM_ID);

    private static final int UNDER_Y = 62;
    private static final int FLOOR_Y = 63;
    private static final int WALK_Y = 64;
    private static final int CEIL_Y = 68;
    private static final int Z_START = 0;
    private static final int Z_END = 18;
    private static final double SPAWN_Z = 1.8D;
    private static final double EXIT_Z = 16.2D;
    private static final double COMPLETE_Z = 15.4D;
    private static final int MAX_CORRIDOR_TICKS = 240;

    private record TransitSession(
            ResourceKey<Level> originDim,
            Vec3 originPos,
            float originYaw,
            float originPitch,
            Waypoint target,
            boolean fromScroll,
            int laneX,
            int ticksInCorridor
    ) {
        TransitSession withTicked() {
            return new TransitSession(originDim, originPos, originYaw, originPitch,
                    target, fromScroll, laneX, ticksInCorridor + 1);
        }
    }

    private static final Map<UUID, TransitSession> SESSIONS = new HashMap<>();

    private WormholeCorridorManager() {}

    public static void enterCorridor(ServerPlayer player, Waypoint target, boolean fromScroll) {
        MinecraftServer server = player.server;
        ServerLevel transitLevel = getOrCreateTransitLevel(server);
        if (transitLevel == null) {
            TeleportManager.executeTeleport(player, target, fromScroll);
            return;
        }

        UUID uuid = player.getUUID();
        int laneX = (Math.abs(uuid.hashCode()) % 512) * 32;
        TransitSession session = new TransitSession(
                player.serverLevel().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                target,
                fromScroll,
                laneX,
                0
        );
        SESSIONS.put(uuid, session);

        PersonalDimensionRegistry.from(server).whenClientReady(player, transitLevel, () -> {
            if (!SESSIONS.containsKey(uuid)) {
                return;
            }
            buildEnclosedCorridor(transitLevel, laneX, uuid, target, fromScroll);
            player.fallDistance = 0.0F;
            player.teleportTo(transitLevel, laneX + 0.5D, WALK_Y, SPAWN_Z, 0.0F, 0.0F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MAX_CORRIDOR_TICKS, 1, false, false, false));
            transitLevel.playSound(null, laneX + 0.5D, WALK_Y + 1.0D, SPAWN_Z,
                    SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.45F, 1.3F);
            player.displayClientMessage(Component.translatable("message.universal_tool.wormhole.corridor_enter"), true);
        });
    }

    public static void completeTransit(ServerPlayer player) {
        TransitSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        if (player.serverLevel().dimension().equals(TRANSIT_DIM_KEY)) {
            discardLaneWormholes(player.serverLevel(), session.laneX());
        }
        TeleportManager.executeTeleport(player, session.target(), session.fromScroll());
    }

    private static ServerLevel getOrCreateTransitLevel(MinecraftServer server) {
        ServerLevel existing = server.getLevel(TRANSIT_DIM_KEY);
        if (existing != null) {
            PersonalDimensionRegistry.from(server).adoptExistingDimension(existing);
            return existing;
        }
        try {
            PersonalDimensionRegistry registry = PersonalDimensionRegistry.from(server);
            Holder<Biome> voidBiome = server.overworld().registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(Biomes.THE_VOID);
            FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                    Optional.empty(), voidBiome, List.of());
            ChunkGenerator generator = new FlatLevelSource(flatSettings);

            DimensionType overworldType = server.overworld().dimensionType();
            DimensionType.MonsterSettings monsters = new DimensionType.MonsterSettings(
                    true, false, overworldType.monsterSettings().monsterSpawnLightTest(), 0);
            DimensionType transitType = new DimensionType(
                    OptionalLong.of(18000L),
                    false,
                    false,
                    false,
                    true,
                    1.0D,
                    true,
                    true,
                    overworldType.minY(),
                    overworldType.height(),
                    overworldType.logicalHeight(),
                    overworldType.infiniburn(),
                    BuiltinDimensionTypes.END_EFFECTS,
                    0.28F,
                    monsters
            );

            ServerLevel loaded = registry.canCreateDimension(TRANSIT_DIM_ID)
                    ? registry.createDynamicDimension(TRANSIT_DIM_ID, generator, transitType)
                    : registry.loadDynamicDimension(TRANSIT_DIM_ID, generator, transitType);
            if (loaded != null) {
                loaded.getWorldBorder().setCenter(0.0D, 0.0D);
                loaded.getWorldBorder().setSize(59_999_968);
                loaded.getWorldBorder().setWarningBlocks(0);
            }
            return loaded;
        } catch (RuntimeException exception) {
            UniversalToolMod.LOGGER.error("Failed to load wormhole transit dimension", exception);
            return null;
        }
    }

    private static void buildEnclosedCorridor(ServerLevel level, int laneX, UUID ownerUuid,
                                              Waypoint target, boolean fromScroll) {
        BlockState wallBrick = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState wallTile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState cryingObs = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState centerGlass = Blocks.CYAN_STAINED_GLASS.defaultBlockState();
        BlockState sideGlass = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState cyanGlow = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState amethystGlow = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        BlockState amethystCrystal = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = Z_START; z <= Z_END; z++) {
            boolean endCap = (z == Z_START || z == Z_END);
            boolean isArch = (z == 5 || z == 10 || z == 15);
            BlockState archMat = (z == 10) ? amethystGlow : cyanGlow;

            for (int dx = -3; dx <= 3; dx++) {
                int x = laneX + dx;
                int adx = Math.abs(dx);

                // Solid obsidian base beneath the glowing glass walkway
                level.setBlock(pos.set(x, UNDER_Y - 1, z), obsidian, 2);

                if (endCap) {
                    for (int y = UNDER_Y; y <= CEIL_Y; y++) {
                        boolean frameGlow = (adx == 1 && y >= WALK_Y && y <= CEIL_Y - 1) || (dx == 0 && y == CEIL_Y - 1);
                        level.setBlock(pos.set(x, y, z), (z == Z_END && frameGlow) ? cyanGlow : wallBrick, 2);
                    }
                    continue;
                }

                // Under-floor glowing core beneath glass walkway (dx = -1..1)
                if (adx <= 1) {
                    BlockState under = (dx == 0 && z % 3 == 0) ? cyanGlow
                            : (adx == 1 && z % 2 == 0) ? cryingObs : obsidian;
                    level.setBlock(pos.set(x, UNDER_Y, z), under, 2);
                    level.setBlock(pos.set(x, FLOOR_Y, z), dx == 0 ? centerGlass : sideGlass, 2);
                } else {
                    level.setBlock(pos.set(x, UNDER_Y, z), obsidian, 2);
                    level.setBlock(pos.set(x, FLOOR_Y, z), (adx == 2 && isArch) ? archMat : wallTile, 2);
                }

                // Enclosed walls (dx = +-3) and ceiling (y = CEIL_Y)
                if (adx == 3) {
                    for (int y = WALK_Y; y <= CEIL_Y; y++) {
                        level.setBlock(pos.set(x, y, z), wallBrick, 2);
                    }
                    continue;
                }

                level.setBlock(pos.set(x, CEIL_Y, z), wallBrick, 2);

                // Interior tunnel (dx = -2..2, y = WALK_Y..CEIL_Y-1)
                for (int y = WALK_Y; y <= CEIL_Y - 1; y++) {
                    if (isArch) {
                        boolean inOctagonalArch =
                                (adx == 2 && y <= WALK_Y + 2)
                                || (adx == 1 && y == WALK_Y + 3)
                                || (dx == 0 && y == WALK_Y + 3);
                        level.setBlock(pos.set(x, y, z), inOctagonalArch ? archMat : air, 2);
                    } else if (adx == 2) {
                        if (y == WALK_Y && z % 3 == 0) {
                            level.setBlock(pos.set(x, y, z), (z % 2 == 0) ? amethystCrystal : wallTile, 2);
                        } else if (y == WALK_Y + 1 && z % 3 == 0 && z % 2 == 1) {
                            level.setBlock(pos.set(x, y, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                        } else if (y >= WALK_Y + 3) {
                            level.setBlock(pos.set(x, y, z), wallTile, 2);
                        } else {
                            level.setBlock(pos.set(x, y, z), air, 2);
                        }
                    } else {
                        level.setBlock(pos.set(x, y, z), air, 2);
                    }
                }
            }
        }

        discardLaneWormholes(level, laneX);
        WormholeEntity exitWormhole = ModEntities.WORMHOLE.get().create(level);
        if (exitWormhole != null) {
            exitWormhole.moveTo(laneX + 0.5D, WALK_Y, EXIT_Z, 0.0F, 0.0F);
            exitWormhole.configure(ownerUuid, target, fromScroll, true);
            level.addFreshEntity(exitWormhole);
        }
    }

    private static void discardLaneWormholes(ServerLevel level, int laneX) {
        AABB laneBox = new AABB(laneX - 4.0D, WALK_Y - 2.0D, Z_START - 2.0D,
                laneX + 5.0D, CEIL_Y + 2.0D, Z_END + 2.0D);
        List<WormholeEntity> existing = level.getEntitiesOfClass(WormholeEntity.class, laneBox);
        for (WormholeEntity wh : existing) {
            wh.discard();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, TransitSession>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TransitSession> entry = it.next();
            UUID uuid = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                it.remove();
                continue;
            }
            TransitSession session = entry.getValue().withTicked();
            entry.setValue(session);

            if (!player.serverLevel().dimension().equals(TRANSIT_DIM_KEY)) {
                if (session.ticksInCorridor() > 20) {
                    it.remove();
                }
                continue;
            }

            if (session.ticksInCorridor() % 4 == 0) {
                emitCorridorParticles(player.serverLevel(), session.laneX(), session.ticksInCorridor());
            }

            if (player.getZ() >= COMPLETE_Z || session.ticksInCorridor() >= MAX_CORRIDOR_TICKS) {
                it.remove();
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                discardLaneWormholes(player.serverLevel(), session.laneX());
                TeleportManager.executeTeleport(player, session.target(), session.fromScroll());
            }
        }
    }

    private static void emitCorridorParticles(ServerLevel level, int laneX, int tick) {
        double phase = (tick * 0.25D) % (Math.PI * 2.0D);
        for (int zArch : new int[]{5, 10, 15}) {
            for (int i = 0; i < 6; i++) {
                double a = phase + i * (Math.PI / 3.0D);
                double px = laneX + 0.5D + Math.cos(a) * 1.1D;
                double py = WALK_Y + 1.35D + Math.sin(a) * 1.1D;
                level.sendParticles(ParticleTypes.END_ROD, px, py, zArch + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TransitSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        if (player.serverLevel().dimension().equals(TRANSIT_DIM_KEY)) {
            ServerLevel origin = player.server.getLevel(session.originDim());
            if (origin != null) {
                player.teleportTo(origin, session.originPos().x, session.originPos().y, session.originPos().z,
                        session.originYaw(), session.originPitch());
            }
        }
    }
}
