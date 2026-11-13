package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModBlocks;
import org.gwfx.universaltool.network.PersonalSpaceActionPayload;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;
import org.gwfx.universaltool.network.PersonalSpaceScreenPayload;
import org.gwfx.universaltool.space.PersonalSpaceSavedData.SpaceRecord;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.SpawnArea;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Surface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates the dual-mode Star Gate network:
 * <ul>
 *   <li>8 shared, freely switchable exploration realms ({@code universal_tool:realm/<env>})</li>
 *   <li>Password-free, UUID-keyed 25x25x25 Private Starry Spaces ({@code universal_tool:private_pocket/<uuid>})</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PrivateSpaceManager {
    public static final ResourceLocation STARRY_POCKET_EFFECTS =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "starry_pocket");
    private static final int WORLD_SIZE = 512;
    private static final int LAND_SEARCH_RADIUS = 48;
    private static final int PAGE_REALMS = 0;
    private static final Map<UUID, ResourceKey<Level>> DEATH_DIMENSIONS = new HashMap<>();
    private static final Set<UUID> RESPAWN_RETURN_PENDING = new HashSet<>();

    private PrivateSpaceManager() {}

    public static String privatePocketPath(UUID playerUuid) {
        return "private_pocket/" + playerUuid.toString().replace("-", "");
    }

    public static String realmPath(PrivateSpaceEnvironment environment) {
        return "realm/" + environment.id();
    }

    /** Opens the Star Gate terminal for whichever player right-clicked it (no lock-in, no passwords). */
    public static void onGateUsed(ServerPlayer player, BlockPos gatePos) {
        String currentEnv = resolveEnvironment(player.serverLevel())
                .map(PrivateSpaceEnvironment::id)
                .orElse(PrivateSpaceEnvironment.SUPERFLAT.id());
        openScreen(player, gatePos, PAGE_REALMS,
                player.getName().getString(), currentEnv, privatePocketPath(player.getUUID()));
    }

    private static void openScreen(ServerPlayer player, BlockPos gatePos, int page,
                                   String playerName, String environmentId, String privateDimension) {
        PacketDistributor.sendToPlayer(player,
                new PersonalSpaceScreenPayload(gatePos, page, playerName, environmentId, privateDimension));
    }

    public static void handleAction(ServerPlayer player, PersonalSpaceActionPayload payload) {
        if (player.distanceToSqr(payload.gatePos().getX() + 0.5, payload.gatePos().getY() + 0.5,
                payload.gatePos().getZ() + 0.5) > 64.0
                || !player.serverLevel().getBlockState(payload.gatePos()).is(ModBlocks.PERSONAL_SPACE_GATE.get())) {
            return;
        }

        if (payload.mode() == PersonalSpaceActionPayload.ACTION_ENTER_REALM) {
            PrivateSpaceEnvironment environment = PrivateSpaceEnvironment.byId(payload.environment());
            if (environment == null) {
                reject(player, payload, "message.universal_tool.personal_space.load_failed");
                return;
            }
            enterRealm(player, payload, environment);
            return;
        }

        if (payload.mode() == PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE) {
            enterPrivatePocket(player, payload);
        }
    }

    private static void accept(ServerPlayer player, PersonalSpaceActionPayload payload) {
        PacketDistributor.sendToPlayer(player, new PersonalSpaceResultPayload(payload.gatePos(), payload.mode(), true, ""));
    }

    private static void reject(ServerPlayer player, PersonalSpaceActionPayload payload, String messageKey) {
        PacketDistributor.sendToPlayer(player, new PersonalSpaceResultPayload(payload.gatePos(), payload.mode(), false, messageKey));
    }

    private static void enterRealm(ServerPlayer player, PersonalSpaceActionPayload payload, PrivateSpaceEnvironment environment) {
        MinecraftServer server = player.server;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(server);
        boolean fresh = data.getRealm(environment).isEmpty();
        SpaceRecord record = data.getRealm(environment).orElseGet(() -> {
            long terrainSeed = server.overworld().getSeed() ^ environment.id().hashCode();
            SpaceRecord created = new SpaceRecord(
                    PersonalSpaceSavedData.SHARED_REALM_OWNER,
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, realmPath(environment)),
                    environment, "", "", FiniteIslandChunkGenerator.VERSION, WORLD_SIZE, terrainSeed
            );
            data.putRealm(created);
            return created;
        });

        ServerLevel targetLevel = getOrLoadSpace(server, record, fresh);
        if (targetLevel == null) {
            reject(player, payload, "message.universal_tool.personal_space.load_failed");
            return;
        }

        accept(player, payload);
        PersonalDimensionRegistry.from(server).whenClientReady(player, targetLevel, () -> {
            if (!isRealmLevel(player.serverLevel())) {
                // Keep the original Overworld (or pre-realm) return anchor across free realm switches.
                data.setReturnPosition(player.getUUID(), positionOf(player));
            }
            BlockPos spawn = targetLevel.getSharedSpawnPos();
            player.teleportTo(targetLevel, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            data.setResumePosition(player.getUUID(), positionOf(player));
            if (fresh) {
                player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.created"), true);
            }
        });
    }

    private static void enterPrivatePocket(ServerPlayer player, PersonalSpaceActionPayload payload) {
        MinecraftServer server = player.server;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(server);
        UUID playerUuid = player.getUUID();
        boolean fresh = data.getPrivatePocket(playerUuid).isEmpty();
        SpaceRecord record = data.getPrivatePocket(playerUuid).orElseGet(() -> {
            long seed = server.overworld().getSeed() ^ playerUuid.getMostSignificantBits();
            SpaceRecord created = new SpaceRecord(
                    playerUuid,
                    ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, privatePocketPath(playerUuid)),
                    PrivateSpaceEnvironment.SUPERFLAT, "", "", StarryPocketChunkGenerator.VERSION, 25, seed
            );
            data.putPrivatePocket(created);
            return created;
        });

        ServerLevel targetLevel = getOrLoadSpace(server, record, fresh);
        if (targetLevel == null) {
            reject(player, payload, "message.universal_tool.personal_space.load_failed");
            return;
        }

        accept(player, payload);
        PersonalDimensionRegistry.from(server).whenClientReady(player, targetLevel, () -> {
            if (!isStarryPocket(player.serverLevel())) {
                data.setPrivateReturnPosition(playerUuid, positionOf(player));
            }
            player.teleportTo(targetLevel, 0.5, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0.5,
                    player.getYRot(), player.getXRot());
            data.setResumePosition(playerUuid, positionOf(player));
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.entered_private"), true);
        });
    }

    static ChunkGenerator createGenerator(ServerLevel overworld, SpaceRecord record) {
        var registries = overworld.registryAccess();
        if (record.isStarryPocket()) {
            Holder<Biome> cosmicBiome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
            return new StarryPocketChunkGenerator(new FixedBiomeSource(cosmicBiome));
        }

        PrivateSpaceEnvironment environment = record.environment();
        if (!record.isFiniteIsland() && !SpaceRecord.LEGACY_VERSION.equals(record.generationVersion())) {
            throw new IllegalStateException("Unsupported private terrain version: " + record.generationVersion());
        }
        if (!record.isFiniteIsland() && environment == PrivateSpaceEnvironment.SUPERFLAT) {
            WorldPreset flatPreset = registries.lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(WorldPresets.FLAT).value();
            WorldDimensions dimensions = flatPreset.createWorldDimensions();
            return dimensions.dimensions().get(LevelStem.OVERWORLD).generator();
        }

        ResourceLocation biomeId = environment.profile().biome().location();
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, biomeId));
        if (record.isFiniteIsland()) {
            return new FiniteIslandChunkGenerator(new FixedBiomeSource(biome), environment,
                    record.terrainSeed(), record.terrainSize());
        }
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
    }

    public static DimensionType copyDimensionType(DimensionType source) {
        return buildDimensionType(source, PrivateSpaceEnvironment.SUPERFLAT);
    }

    /** Builds a realm-tuned DimensionType (fixed clock, safe beds/water, optional piglin safety). */
    public static DimensionType buildDimensionType(DimensionType source, PrivateSpaceEnvironment environment) {
        PrivateSpaceWorldTraits traits = environment.traits();
        DimensionType.MonsterSettings srcMonsters = source.monsterSettings();
        DimensionType.MonsterSettings monsters = new DimensionType.MonsterSettings(
                traits.piglinSafe(),
                srcMonsters.hasRaids(),
                srcMonsters.monsterSpawnLightTest(),
                srcMonsters.monsterSpawnBlockLightLimit()
        );
        return new DimensionType(
                traits.time().fixedTime(),
                source.hasSkyLight(),
                false,
                false,
                true,
                source.coordinateScale(),
                true,
                true,
                source.minY(),
                source.height(),
                source.logicalHeight(),
                source.infiniburn(),
                source.effectsLocation(),
                Math.max(source.ambientLight(), traits.ambientLight()),
                monsters
        );
    }

    /** Builds the eternal starry sanctuary DimensionType (safe beds, soft ambient light, no hazards). */
    public static DimensionType buildPrivatePocketDimensionType(DimensionType source) {
        DimensionType.MonsterSettings srcMonsters = source.monsterSettings();
        DimensionType.MonsterSettings monsters = new DimensionType.MonsterSettings(
                true,
                false,
                srcMonsters.monsterSpawnLightTest(),
                0
        );
        return new DimensionType(
                OptionalLong.of(18000L),
                true,
                false,
                false,
                true,
                1.0,
                true,
                true,
                source.minY(),
                source.height(),
                source.logicalHeight(),
                source.infiniburn(),
                STARRY_POCKET_EFFECTS,
                Math.max(source.ambientLight(), 0.35F),
                monsters
        );
    }

    public static void configureLevel(ServerLevel level, PrivateSpaceEnvironment environment, boolean fresh) {
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(59_999_968);
        level.getWorldBorder().setWarningBlocks(0);

        if (isStarryPocket(level)) {
            if (level.getLevelData() instanceof PersonalDimensionLevelData personalData) {
                personalData.setWeatherRule(PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR);
            }
            applyWeatherState(level, PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR);
            BlockPos spawn = new BlockPos(0, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0);
            if (!spawn.equals(level.getSharedSpawnPos())) {
                level.setDefaultSpawnPos(spawn, 0.0F);
            }
            ensureStarryChamberShell(level, fresh);
            return;
        }

        if (level.getLevelData() instanceof PersonalDimensionLevelData personalData) {
            personalData.setWeatherRule(environment.traits().weather());
        }
        applyWeatherState(level, environment.traits().weather());

        BlockPos spawn = level.getSharedSpawnPos();
        boolean needsSpawnInit = fresh || (!isSafeSpawn(level, spawn)
                && !level.getBlockState(spawn.offset(-3, 0, 0)).is(ModBlocks.PERSONAL_SPACE_RETURN.get()));
        if (needsSpawnInit) {
            spawn = prepareSpawnArea(level, environment);
        }

        if (needsSpawnInit || !spawn.equals(level.getSharedSpawnPos())) {
            level.setDefaultSpawnPos(spawn, 0.0F);
        }
        ensureReturnGate(level);
    }

    /** Ensures the 25x25 StarryVoidBlock floor and transparent Barrier boundary box exist. */
    static void ensureStarryChamberShell(ServerLevel level, boolean force) {
        BlockPos probeFloor = new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0);
        BlockPos probeWall = new BlockPos(StarryPocketChunkGenerator.SHELL_HALF, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0);
        if (!force && level.getBlockState(probeFloor).is(ModBlocks.STARRY_VOID_BLOCK.get())
                && level.getBlockEntity(probeFloor) instanceof StarryVoidBlockEntity
                && level.getBlockState(probeWall).is(Blocks.BARRIER)) {
            return;
        }
        BlockState floorState = ModBlocks.STARRY_VOID_BLOCK.get().defaultBlockState();
        BlockState barrierState = Blocks.BARRIER.defaultBlockState();
        int innerHalf = StarryPocketChunkGenerator.INNER_HALF;
        int shellHalf = StarryPocketChunkGenerator.SHELL_HALF;
        int floorY = StarryPocketChunkGenerator.FLOOR_Y;
        int ceilingY = StarryPocketChunkGenerator.CEILING_Y;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -shellHalf; x <= shellHalf; x++) {
            for (int z = -shellHalf; z <= shellHalf; z++) {
                boolean onPerimeter = x == -shellHalf || x == shellHalf || z == -shellHalf || z == shellHalf;
                for (int y = floorY; y <= ceilingY; y++) {
                    cursor.set(x, y, z);
                    if (y == floorY && Math.abs(x) <= innerHalf && Math.abs(z) <= innerHalf) {
                        if (!level.getBlockState(cursor).is(ModBlocks.STARRY_VOID_BLOCK.get())) {
                            level.setBlock(cursor, floorState, 2);
                        }
                        if (!(level.getBlockEntity(cursor) instanceof StarryVoidBlockEntity)) {
                            level.setBlockEntity(new StarryVoidBlockEntity(cursor.immutable(), floorState));
                        }
                    } else if (onPerimeter || y == ceilingY) {
                        if (!level.getBlockState(cursor).is(Blocks.BARRIER)) {
                            level.setBlock(cursor, barrierState, 2);
                        }
                    }
                }
            }
        }
    }

    static void applyWeatherState(ServerLevel level, PrivateSpaceWorldTraits.WeatherRule rule) {
        if (rule == PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
        } else if (rule == PrivateSpaceWorldTraits.WeatherRule.SNOW_FLURRY) {
            level.setRainLevel(0.65F);
            level.setThunderLevel(0.0F);
        }
    }

    static void ensureReturnGate(ServerLevel level) {
        if (isStarryPocket(level)) {
            return;
        }
        BlockPos pos = level.getSharedSpawnPos().offset(-3, 0, 0);
        if (!level.getBlockState(pos).is(ModBlocks.PERSONAL_SPACE_RETURN.get())) {
            level.setBlock(pos, ModBlocks.PERSONAL_SPACE_RETURN.get().defaultBlockState(), 3);
        }
    }

    @SubscribeEvent
    public static void onStarryVoidShiftClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(ModBlocks.STARRY_VOID_BLOCK.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
            returnPlayer(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void protectReturnGate(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!state.is(ModBlocks.PERSONAL_SPACE_RETURN.get()) && !state.is(ModBlocks.STARRY_VOID_BLOCK.get())) {
            return;
        }
        if (state.is(ModBlocks.STARRY_VOID_BLOCK.get()) && event.getPlayer().isCreative()) {
            return;
        }
        event.setCanceled(true);
        event.getPlayer().displayClientMessage(Component.translatable(
                "message.universal_tool.personal_space.return_gate_protected"), true);
    }

    @SubscribeEvent
    public static void protectReturnGateFromExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> {
            BlockState state = event.getLevel().getBlockState(pos);
            return state.is(ModBlocks.PERSONAL_SPACE_RETURN.get()) || state.is(ModBlocks.STARRY_VOID_BLOCK.get());
        });
    }

    private static BlockPos prepareSpawnArea(ServerLevel level, PrivateSpaceEnvironment environment) {
        BlockPos land = level.getChunkSource().getGenerator() instanceof FiniteIslandChunkGenerator islandGenerator
                ? new BlockPos(0, islandGenerator.surfaceY(0, 0) + 1, 0) : findDryLand(level);
        boolean island = land == null;
        int centerX = island ? 0 : land.getX();
        int centerZ = island ? 0 : land.getZ();
        int groundY = island
                ? Mth.clamp(level.getSeaLevel() + 3, level.getMinBuildHeight() + 8, level.getMaxBuildHeight() - 16)
                : land.getY();
        int radius = environment.profile().spawnArea().platformRadius();
        Surface surface = environment.profile().surface();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int localY = groundY;
                if (island) {
                    int edge = Math.max(Math.abs(dx), Math.abs(dz));
                    localY -= Math.max(0, edge - 6);
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                int existingSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int fillStart = Math.max(level.getMinBuildHeight() + 1, Math.max(existingSurface - 1, localY - 5));
                int fillerDepth = surface.fillerDepth();
                for (int y = fillStart; y < localY - 1; y++) {
                    level.setBlock(new BlockPos(x, y, z), y < localY - fillerDepth
                            ? Blocks.STONE.defaultBlockState() : surface.filler().defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(x, localY - 1, z), surface.top().defaultBlockState(), 3);
                for (int y = localY; y <= localY + 10; y++) {
                    BlockPos clear = new BlockPos(x, y, z);
                    if (!level.getBlockState(clear).isAir()) {
                        level.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                if (surface.snowLayer() && !level.getBlockState(new BlockPos(x, localY, z)).is(Blocks.SNOW)) {
                    level.setBlock(new BlockPos(x, localY, z), Blocks.SNOW.defaultBlockState(), 3);
                }
            }
        }

        BlockPos spawn = new BlockPos(centerX, groundY, centerZ);
        SpawnArea spawnArea = environment.profile().spawnArea();
        if (spawnArea.treeLog() != null) {
            placeSpawnTree(level, centerX + 5, groundY, centerZ + 1, spawnArea);
            placeSpawnTree(level, centerX - 5, groundY, centerZ - 1, spawnArea);
            placeSpawnTree(level, centerX + 1, groundY, centerZ - 5, spawnArea);
        }
        return spawn;
    }

    private static BlockPos findDryLand(ServerLevel level) {
        for (int radius = 0; radius <= LAND_SEARCH_RADIUS; radius += 4) {
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (groundY <= level.getSeaLevel()) continue;
                    BlockPos ground = new BlockPos(x, groundY - 1, z);
                    if (!level.getBlockState(ground).getFluidState().isEmpty()
                            || level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) continue;
                    return new BlockPos(x, groundY, z);
                }
            }
        }
        return null;
    }

    private static boolean isSafeSpawn(ServerLevel level, BlockPos spawn) {
        BlockPos floor = spawn.below();
        return level.getBlockState(floor).getFluidState().isEmpty()
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && level.getBlockState(spawn).isAir()
                && level.getBlockState(spawn.above()).isAir();
    }

    private static void placeSpawnTree(ServerLevel level, int x, int groundY, int z, SpawnArea spawnArea) {
        BlockState trunk = spawnArea.treeLog().defaultBlockState();
        BlockState leaves = spawnArea.treeLeaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < 5; y++) level.setBlock(new BlockPos(x, groundY + y, z), trunk, 3);
        for (int layer = 2; layer <= 6; layer++) {
            int canopyRadius = layer <= 4 ? 3 : 2;
            for (int dx = -canopyRadius; dx <= canopyRadius; dx++) {
                for (int dz = -canopyRadius; dz <= canopyRadius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > canopyRadius * 2) continue;
                    BlockPos leafPos = new BlockPos(x + dx, groundY + layer, z + dz);
                    if (level.getBlockState(leafPos).isAir()) level.setBlock(leafPos, leaves, 3);
                }
            }
        }
        for (int y = 3; y <= 4; y++) {
            level.setBlock(new BlockPos(x + 2, groundY + y, z), trunk, 3);
            level.setBlock(new BlockPos(x - 2, groundY + y, z + 1), trunk, 3);
        }
    }

    @SubscribeEvent
    public static void rescueFromVoid(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (isStarryPocket(level)) {
            boolean outOfChamber = player.getY() < StarryPocketChunkGenerator.FLOOR_Y
                    || player.getY() > StarryPocketChunkGenerator.CEILING_Y + 2
                    || Math.abs(player.getX()) > StarryPocketChunkGenerator.SHELL_HALF + 2.0
                    || Math.abs(player.getZ()) > StarryPocketChunkGenerator.SHELL_HALF + 2.0;
            if (!outOfChamber) {
                return;
            }
            player.teleportTo(level, 0.5, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0.5,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
            player.setDeltaMovement(0, 0, 0);
            PersonalSpaceSavedData.get(player.server).setResumePosition(player.getUUID(), positionOf(player));
            return;
        }

        if (player.getY() >= level.getMinBuildHeight() - 16
                || !isPersonalSpace(player.server, level.dimension())) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos safe = findSafePosition(level, spawn.getX(), spawn.getY(), spawn.getZ());
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.setDeltaMovement(0, 0, 0);
        PersonalSpaceSavedData.get(player.server).setResumePosition(player.getUUID(), positionOf(player));
    }

    @SubscribeEvent
    public static void recordPrivateDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        if (isPersonalSpace(player.server, dimension)) {
            DEATH_DIMENSIONS.put(player.getUUID(), dimension);
            RESPAWN_RETURN_PENDING.add(player.getUUID());
        } else {
            DEATH_DIMENSIONS.remove(player.getUUID());
            RESPAWN_RETURN_PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void respawnFromPrivateSpace(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceKey<Level> deathDimension = DEATH_DIMENSIONS.remove(player.getUUID());
        if (deathDimension == null) return;

        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        SpaceRecord ownSpace = data.getSpace(player.getUUID()).orElse(null);
        ServerLevel destination;
        PersonalSpaceSavedData.Position position;
        if (ownSpace != null && ownSpace.dimension().equals(deathDimension.location())) {
            destination = getOrLoadSpace(player.server, ownSpace, false);
            if (destination == null) destination = player.server.overworld();
            configureLevel(destination, ownSpace.environment(), false);
            if (ownSpace.isStarryPocket()) {
                position = new PersonalSpaceSavedData.Position(destination.dimension(),
                        0.5, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0.5, 0, 0);
            } else {
                BlockPos spawn = destination.getSharedSpawnPos();
                position = new PersonalSpaceSavedData.Position(destination.dimension(),
                        spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            }
        } else {
            position = data.getReturnPosition(player.getUUID()).orElse(null);
            if (position == null) {
                ServerLevel overworld = player.server.overworld();
                BlockPos spawn = overworld.getSharedSpawnPos();
                position = new PersonalSpaceSavedData.Position(Level.OVERWORLD, spawn.getX() + 0.5,
                        spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            }
            destination = getOrLoadDestination(player.server, position.dimension());
            if (destination == null) {
                destination = player.server.overworld();
                BlockPos spawn = destination.getSharedSpawnPos();
                position = new PersonalSpaceSavedData.Position(Level.OVERWORLD, spawn.getX() + 0.5,
                        spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            }
        }

        RESPAWN_RETURN_PENDING.add(player.getUUID());
        ServerLevel target = destination;
        PersonalSpaceSavedData.Position targetPosition = position;
        Runnable finishRespawn = () -> {
            BlockPos safe = findSafePosition(target, targetPosition.x(), targetPosition.y(), targetPosition.z());
            player.teleportTo(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    targetPosition.yaw(), targetPosition.pitch());
            if (!isPersonalSpace(player.server, target.dimension())) {
                data.clearReturnPosition(player.getUUID());
            } else {
                data.setResumePosition(player.getUUID(), positionOf(player));
            }
            RESPAWN_RETURN_PENDING.remove(player.getUUID());
        };
        if (isPersonalSpace(player.server, target.dimension())) {
            PersonalDimensionRegistry.from(player.server).whenClientReady(player, target, finishRespawn);
        } else {
            finishRespawn.run();
        }
    }

    public static ServerLevel getOrLoadSpace(MinecraftServer server, SpaceRecord record) {
        return getOrLoadSpace(server, record, false);
    }

    private static ServerLevel getOrLoadSpace(MinecraftServer server, SpaceRecord record, boolean fresh) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            PersonalDimensionRegistry.from(server).adoptExistingDimension(level);
            configureLevel(level, record.environment(), fresh);
            return level;
        }
        try {
            PersonalDimensionRegistry registry = PersonalDimensionRegistry.from(server);
            DimensionType dimType = record.isStarryPocket()
                    ? buildPrivatePocketDimensionType(server.overworld().dimensionType())
                    : buildDimensionType(server.overworld().dimensionType(), record.environment());
            ServerLevel loaded = fresh && registry.canCreateDimension(record.dimension())
                    ? registry.createDynamicDimension(record.dimension(), createGenerator(server.overworld(), record), dimType)
                    : registry.loadDynamicDimension(record.dimension(), createGenerator(server.overworld(), record), dimType);
            if (loaded != null) {
                configureLevel(loaded, record.environment(), fresh);
            }
            return loaded;
        } catch (RuntimeException exception) {
            UniversalToolMod.LOGGER.error("Failed to load space {}", record.dimension(), exception);
            return null;
        }
    }

    public static void returnPlayer(ServerPlayer player) {
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        boolean fromPocket = isStarryPocket(player.serverLevel());
        PersonalSpaceSavedData.Position destination = fromPocket
                ? data.getPrivateReturnPosition(player.getUUID())
                        .or(() -> data.getReturnPosition(player.getUUID()))
                        .orElse(null)
                : data.getReturnPosition(player.getUUID()).orElse(null);

        if (destination == null) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            destination = new PersonalSpaceSavedData.Position(Level.OVERWORLD,
                    spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        }

        ServerLevel level = getOrLoadDestination(player.server, destination.dimension());
        if (level == null) {
            level = player.server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            destination = new PersonalSpaceSavedData.Position(Level.OVERWORLD,
                    spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        }

        ServerLevel target = level;
        PersonalSpaceSavedData.Position returnPosition = destination;
        Runnable teleport = () -> {
            BlockPos safe = findSafePosition(target, returnPosition.x(), returnPosition.y(), returnPosition.z());
            if (fromPocket) {
                data.clearPrivateReturnPosition(player.getUUID());
            } else {
                data.clearReturnPosition(player.getUUID());
            }
            if (!isPersonalSpace(player.server, target.dimension())) {
                data.clearResumePosition(player.getUUID());
            }
            player.teleportTo(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    returnPosition.yaw(), returnPosition.pitch());
            if (isPersonalSpace(player.server, target.dimension())) {
                data.setResumePosition(player.getUUID(), positionOf(player));
            }
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.returned"), true);
        };
        if (isPersonalSpace(player.server, target.dimension())) {
            PersonalDimensionRegistry.from(player.server).whenClientReady(player, target, teleport);
        } else {
            teleport.run();
        }
    }

    private static ServerLevel getOrLoadDestination(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level != null || dimension.equals(Level.OVERWORLD) || dimension.equals(Level.NETHER) || dimension.equals(Level.END)) {
            return level;
        }
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(server);
        for (SpaceRecord record : data.allSpaces()) {
            if (record.dimension().equals(dimension.location())) {
                return getOrLoadSpace(server, record);
            }
        }
        return null;
    }

    private static BlockPos findSafePosition(ServerLevel level, double x, double y, double z) {
        int baseX = (int) Math.floor(x);
        int baseY = (int) Math.floor(y);
        int baseZ = (int) Math.floor(z);
        for (int dy = 0; dy <= 4; dy++) {
            for (int radius = 0; radius <= 3; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos feet = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
                            return feet;
                        }
                    }
                }
            }
        }
        return new BlockPos(baseX, Math.min(level.getMaxBuildHeight() - 2, baseY + 1), baseZ);
    }

    private static PersonalSpaceSavedData.Position positionOf(ServerPlayer player) {
        return new PersonalSpaceSavedData.Position(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void recoverLegacyRegistrations(ServerStartedEvent event) {
        var server = event.getServer();
        for (var record : PersonalSpaceSavedData.get(server).allSpaces()) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
            if (level != null) {
                PersonalDimensionRegistry.from(server).adoptExistingDimension(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        data.getResumePosition(player.getUUID()).ifPresent(position -> {
            ServerLevel level = getOrLoadDestination(player.server, position.dimension());
            if (level != null) {
                PersonalSpaceSavedData.Position resume = position;
                Optional<SpaceRecord> space = data.allSpaces().stream()
                        .filter(record -> record.dimension().equals(position.dimension().location())).findFirst();
                if (space.isPresent()) {
                    configureLevel(level, space.get().environment(), false);
                    BlockPos savedFeet = new BlockPos(Mth.floor(resume.x()), Mth.floor(resume.y()), Mth.floor(resume.z()));
                    if (!isSafeSpawn(level, savedFeet)) {
                        BlockPos spawn = level.getSharedSpawnPos();
                        resume = new PersonalSpaceSavedData.Position(resume.dimension(), spawn.getX() + 0.5,
                                spawn.getY(), spawn.getZ() + 0.5, resume.yaw(), resume.pitch());
                        data.setResumePosition(player.getUUID(), resume);
                    }
                }
                var restoredPosition = resume;
                if (space.isPresent()) {
                    PersonalDimensionRegistry.from(player.server).whenClientReady(player, level, () ->
                            player.teleportTo(level, restoredPosition.x(), restoredPosition.y(), restoredPosition.z(),
                                    restoredPosition.yaw(), restoredPosition.pitch()));
                } else {
                    player.teleportTo(level, resume.x(), resume.y(), resume.z(), resume.yaw(), resume.pitch());
                }
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        boolean fromPocket = isStarryPocketDimension(event.getFrom());
        boolean toPocket = isStarryPocketDimension(event.getTo());
        boolean fromSpace = isPersonalSpace(player.server, event.getFrom());
        boolean toSpace = isPersonalSpace(player.server, event.getTo());
        boolean leavingUnexpectedly = !RESPAWN_RETURN_PENDING.contains(player.getUUID())
                && !DEATH_DIMENSIONS.containsKey(player.getUUID());
        if (fromPocket && !toPocket && leavingUnexpectedly) {
            data.clearPrivateReturnPosition(player.getUUID());
        }
        if (fromSpace && !toSpace && leavingUnexpectedly) {
            data.clearReturnPosition(player.getUUID());
        }
        if (fromSpace && !event.getFrom().equals(event.getTo()) && !DEATH_DIMENSIONS.containsKey(player.getUUID())) {
            ResourceKey<Level> oldDimension = event.getFrom();
            player.server.execute(() -> unloadIfEmpty(player.server, oldDimension));
        }
    }

    @SubscribeEvent
    public static void tickDimensionSynchronisation(ServerTickEvent.Post event) {
        var server = event.getServer();
        var registry = PersonalDimensionRegistry.from(server);
        registry.tickPendingTeleports();
        if (server.getTickCount() % 20 == 0) {
            for (var record : PersonalSpaceSavedData.get(server).allSpaces()) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
                ServerLevel loaded = server.getLevel(key);
                if (loaded != null && !record.isStarryPocket()) {
                    ensureReturnGate(loaded);
                }
                if (!registry.hasPendingTeleport(record.dimension())) {
                    unloadIfEmpty(server, key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DEATH_DIMENSIONS.remove(player.getUUID());
        RESPAWN_RETURN_PENDING.remove(player.getUUID());
        PersonalDimensionRegistry.from(player.server).forgetClient(player.getUUID());
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        if (isPersonalSpace(player.server, dimension)) {
            PersonalSpaceSavedData.get(player.server).setResumePosition(player.getUUID(), positionOf(player));
            player.server.execute(() -> unloadIfEmpty(player.server, dimension));
        }
    }

    static boolean isPersonalSpace(MinecraftServer server, ResourceKey<Level> dimension) {
        return PersonalSpaceSavedData.get(server).allSpaces().stream()
                .anyMatch(record -> record.dimension().equals(dimension.location()));
    }

    public static boolean isStarryPocket(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof StarryPocketChunkGenerator
                || isStarryPocketDimension(level.dimension());
    }

    private static boolean isStarryPocketDimension(ResourceKey<Level> dimension) {
        ResourceLocation loc = dimension.location();
        return UniversalToolMod.MODID.equals(loc.getNamespace())
                && loc.getPath().startsWith("private_pocket/");
    }

    private static boolean isRealmLevel(ServerLevel level) {
        ResourceLocation loc = level.dimension().location();
        return level.getChunkSource().getGenerator() instanceof FiniteIslandChunkGenerator
                || (UniversalToolMod.MODID.equals(loc.getNamespace()) && loc.getPath().startsWith("realm/"));
    }

    /** Resolves a loaded exploration realm's landscape, including isolated test/preview dimensions. */
    public static Optional<PrivateSpaceEnvironment> resolveEnvironment(ServerLevel level) {
        if (isStarryPocket(level)) {
            return Optional.empty();
        }
        if (level.getChunkSource().getGenerator() instanceof FiniteIslandChunkGenerator islandGenerator) {
            return Optional.of(islandGenerator.environment());
        }
        ResourceLocation dimId = level.dimension().location();
        return PersonalSpaceSavedData.get(level.getServer()).allSpaces().stream()
                .filter(record -> record.dimension().equals(dimId) && !record.isStarryPocket())
                .map(SpaceRecord::environment)
                .findFirst();
    }

    private static void unloadIfEmpty(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !isPersonalSpace(server, dimension)) return;
        if (server.getPlayerList().getPlayers().stream().anyMatch(player -> player.level() == level)) return;
        PersonalDimensionRegistry.from(server).unloadDynamicDimension(dimension.location());
    }
}
