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
 * Manages the transparent Spatial Wormhole Transit Corridor ({@code universal_tool:wormhole_transit}).
 * Builds an invisible barrier walkway with zero solid architecture so the player sprints through
 * open cosmic space straight toward the large destination-vista exit wormhole ahead.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class WormholeCorridorManager {
    public static final ResourceLocation TRANSIT_DIM_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "wormhole_transit");
    public static final ResourceKey<Level> TRANSIT_DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, TRANSIT_DIM_ID);

    private static final int FLOOR_Y = 63;
    private static final int WALK_Y = 64;
    private static final int CEIL_Y = 68;
    private static final int Z_START = 0;
    private static final int Z_END = 14;
    private static final double SPAWN_Z = 1.5D;
    private static final double EXIT_Z = 12.5D;
    private static final double COMPLETE_Z = 11.5D;
    private static final int SPEED_EFFECT_TICKS = 220;

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

    public static boolean hasActiveSession(@javax.annotation.Nullable UUID uuid) {
        return uuid != null && SESSIONS.containsKey(uuid);
    }

    public static void enterCorridor(ServerPlayer player, Waypoint target, boolean fromScroll) {
        enterCorridor(player, target, fromScroll, "");
    }

    public static void enterCorridor(ServerPlayer player, Waypoint target, boolean fromScroll, String vistaId) {
        if (hasActiveSession(player.getUUID())) return;
        MinecraftServer server = player.server;
        ServerLevel transitLevel = getOrCreateTransitLevel(server);
        if (transitLevel == null) {
            if (!TeleportManager.executeTeleport(player, target) && fromScroll) refundScroll(player, target);
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
            buildTransparentCorridor(transitLevel, laneX, uuid, target, fromScroll, vistaId);
            player.fallDistance = 0.0F;
            player.teleportTo(transitLevel, laneX + 0.5D, WALK_Y, SPAWN_Z, 0.0F, 0.0F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_EFFECT_TICKS, 1, false, false, false));
            transitLevel.playSound(null, laneX + 0.5D, WALK_Y + 1.0D, SPAWN_Z,
                    SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.45F, 1.3F);
            player.displayClientMessage(Component.translatable("message.universal_tool.wormhole.corridor_enter"), true);
        });
    }

    public static void completeTransit(ServerPlayer player) {
        TransitSession session = SESSIONS.get(player.getUUID());
        if (session != null && finishTransit(player, session)) SESSIONS.remove(player.getUUID());
    }

    private static boolean finishTransit(ServerPlayer player, TransitSession session) {
        ServerLevel corridor = player.serverLevel();
        boolean arrived = TeleportManager.executeTeleport(player, session.target());
        if (!arrived && !TeleportManager.returnToOrigin(player, session.originDim(), session.originPos(),
                session.originYaw(), session.originPitch())) return false;
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        if (corridor.dimension().equals(TRANSIT_DIM_KEY)) discardLaneWormholes(corridor, session.laneX());
        if (!arrived && session.fromScroll()) refundScroll(player, session.target());
        return true;
    }

    private static void refundScroll(ServerPlayer player, Waypoint target) {
        var scroll = org.gwfx.universaltool.item.TeleportScrollItem.createScroll(target);
        if (!player.getInventory().add(scroll)) player.drop(scroll, false);
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

    private static void buildTransparentCorridor(ServerLevel level, int laneX, UUID ownerUuid,
                                                 Waypoint target, boolean fromScroll, String vistaId) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Wipe full legacy envelope (v1.9.12 built z=0..18, dx=-3..3, y=61..68) so nothing remains behind the exit
        for (int z = -2; z <= 20; z++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = laneX + dx;
                for (int y = 60; y <= 70; y++) {
                    level.setBlock(pos.set(x, y, z), air, 2);
                }
            }
        }

        for (int z = Z_START - 1; z <= Z_END + 1; z++) {
            boolean insideZ = (z >= Z_START && z <= Z_END);
            for (int dx = -2; dx <= 2; dx++) {
                int x = laneX + dx;
                boolean isSideWall = (Math.abs(dx) == 2);
                if (insideZ && !isSideWall) {
                    level.setBlock(pos.set(x, FLOOR_Y, z), barrier, 2);
                }
                for (int y = WALK_Y; y <= CEIL_Y; y++) {
                    boolean shell = !insideZ || isSideWall || y == CEIL_Y;
                    if (shell) {
                        level.setBlock(pos.set(x, y, z), barrier, 2);
                    }
                }
            }
        }

        discardLaneWormholes(level, laneX);
        WormholeEntity exitWormhole = ModEntities.WORMHOLE.get().create(level);
        if (exitWormhole != null) {
            exitWormhole.moveTo(laneX + 0.5D, WALK_Y, EXIT_Z, 0.0F, 0.0F);
            exitWormhole.configure(ownerUuid, target, fromScroll, true);
            exitWormhole.setVistaId(vistaId);
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

            if (session.ticksInCorridor() % 100 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_EFFECT_TICKS, 1, false, false, false));
            }

            if (player.getZ() >= COMPLETE_Z && session.ticksInCorridor() % 20 == 0
                    && finishTransit(player, session)) {
                it.remove();
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
            discardLaneWormholes(player.serverLevel(), session.laneX());
            TeleportManager.returnToOrigin(player, session.originDim(), session.originPos(),
                    session.originYaw(), session.originPitch());
        }
    }
}
