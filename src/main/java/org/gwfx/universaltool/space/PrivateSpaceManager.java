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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PersonalSpaceScreen;
import org.gwfx.universaltool.init.ModBlocks;
import org.gwfx.universaltool.network.PersonalSpaceActionPayload;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;
import org.gwfx.universaltool.network.PersonalSpaceScreenPayload;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.SpawnArea;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Surface;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PrivateSpaceManager {
    private static final int WORLD_SIZE = 512;
    private static final int LAND_SEARCH_RADIUS = 48;
    private static final int ACTION_CREATE = 0;
    private static final int ACTION_VISIT = 1;
    private static final int ACTION_CHANGE_PASSWORD = 2;
    private static final java.util.Map<UUID, ResourceKey<Level>> DEATH_DIMENSIONS = new java.util.HashMap<>();
    private static final java.util.Set<UUID> RESPAWN_RETURN_PENDING = new java.util.HashSet<>();

    private PrivateSpaceManager() {}

    public static void onGateUsed(ServerPlayer player, PersonalSpaceGateBlockEntity gate, boolean sneaking) {
        UUID owner = gate.getOwner();
        if (owner == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.unbound_gate"), true);
            return;
        }

        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        Optional<PersonalSpaceSavedData.SpaceRecord> space = data.getSpace(owner);
        if (owner.equals(player.getUUID())) {
            if (space.isEmpty()) {
                openScreen(player, gate.getBlockPos(), PersonalSpaceScreen.CREATE);
            } else if (sneaking) {
                openScreen(player, gate.getBlockPos(), PersonalSpaceScreen.CHANGE_PASSWORD);
            } else {
                enterSpace(player, space.get());
            }
            return;
        }

        if (space.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.not_created"), true);
            return;
        }
        openScreen(player, gate.getBlockPos(), PersonalSpaceScreen.VISIT);
    }

    private static void openScreen(ServerPlayer player, BlockPos gatePos, int mode) {
        PacketDistributor.sendToPlayer(player, new PersonalSpaceScreenPayload(gatePos, mode));
    }

    public static void handleAction(ServerPlayer player, PersonalSpaceActionPayload payload) {
        if (player.distanceToSqr(payload.gatePos().getX() + 0.5, payload.gatePos().getY() + 0.5,
                payload.gatePos().getZ() + 0.5) > 64.0
                || !(player.serverLevel().getBlockEntity(payload.gatePos()) instanceof PersonalSpaceGateBlockEntity gate)
                || gate.getOwner() == null) {
            // The player has walked away or the gate is gone: no answer is owed, and the client's own
            // timeout restores the button.
            return;
        }

        UUID gateOwner = gate.getOwner();
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        Optional<PersonalSpaceSavedData.SpaceRecord> existing = data.getSpace(gateOwner);

        if (payload.mode() == ACTION_CREATE) {
            if (!gateOwner.equals(player.getUUID()) || existing.isPresent()) return;
            PrivateSpaceEnvironment environment = PrivateSpaceEnvironment.byId(payload.environment());
            if (environment == null) return;
            String rejection = validateNewPassword(payload.password(), payload.confirmation());
            if (rejection != null) {
                reject(player, payload, rejection);
                return;
            }
            createSpace(player, payload, environment, payload.password());
            return;
        }

        if (payload.mode() == ACTION_CHANGE_PASSWORD) {
            if (!gateOwner.equals(player.getUUID()) || existing.isEmpty()) return;
            String rejection = validateNewPassword(payload.password(), payload.confirmation());
            if (rejection != null) {
                reject(player, payload, rejection);
                return;
            }
            PasswordHasher.HashedPassword hashed = PasswordHasher.hash(payload.password());
            data.changePassword(gateOwner, hashed.salt(), hashed.hash());
            accept(player, payload);
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.password_changed"), true);
            return;
        }

        if (payload.mode() == ACTION_VISIT && !gateOwner.equals(player.getUUID()) && existing.isPresent()) {
            if (payload.password().length() > PrivateSpaceRules.MAX_PASSWORD_LENGTH) {
                reject(player, payload, "message.universal_tool.personal_space.password_length");
                return;
            }
            PersonalSpaceSavedData.SpaceRecord space = existing.get();
            if (!PasswordHasher.verify(payload.password(), space.passwordSalt(), space.passwordHash())) {
                reject(player, payload, "message.universal_tool.personal_space.wrong_password");
                return;
            }
            accept(player, payload);
            enterSpace(player, space);
        }
    }

    /**
     * @return the translation key to show, or {@code null} when the password is acceptable
     */
    private static String validateNewPassword(String password, String confirmation) {
        if (password.length() < PrivateSpaceRules.MIN_PASSWORD_LENGTH
                || password.length() > PrivateSpaceRules.MAX_PASSWORD_LENGTH) {
            return "message.universal_tool.personal_space.password_length";
        }
        if (!MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), confirmation.getBytes(StandardCharsets.UTF_8))) {
            return "message.universal_tool.personal_space.password_mismatch";
        }
        return null;
    }

    private static void accept(ServerPlayer player, PersonalSpaceActionPayload payload) {
        PacketDistributor.sendToPlayer(player, new PersonalSpaceResultPayload(payload.gatePos(), payload.mode(), true, ""));
    }

    private static void reject(ServerPlayer player, PersonalSpaceActionPayload payload, String messageKey) {
        PacketDistributor.sendToPlayer(player, new PersonalSpaceResultPayload(payload.gatePos(), payload.mode(), false, messageKey));
    }

    private static void createSpace(ServerPlayer player, PersonalSpaceActionPayload payload,
                                    PrivateSpaceEnvironment environment, String password) {
        MinecraftServer server = player.server;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(server);
        ResourceLocation dimensionId = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,
                "personal_space/" + player.getUUID().toString().replace("-", ""));
        PersonalDimensionRegistry registry = PersonalDimensionRegistry.from(server);
        if (!registry.canCreateDimension(dimensionId)) {
            reject(player, payload, "message.universal_tool.personal_space.dimension_exists");
            return;
        }

        ServerLevel overworld = server.overworld();
        long startedAt = System.nanoTime();
        long terrainSeed = overworld.getSeed() ^ player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
        PasswordHasher.HashedPassword hashed = PasswordHasher.hash(password);
        PersonalSpaceSavedData.SpaceRecord record = new PersonalSpaceSavedData.SpaceRecord(player.getUUID(),
                dimensionId, environment, hashed.salt(), hashed.hash(), FiniteIslandChunkGenerator.VERSION, WORLD_SIZE, terrainSeed);
        ChunkGenerator generator;
        try {
            generator = createGenerator(overworld, record);
        } catch (RuntimeException exception) {
            UniversalToolMod.LOGGER.error("Failed to create personal-space generator for {}", environment.id(), exception);
            reject(player, payload, "message.universal_tool.personal_space.create_failed");
            return;
        }

        ServerLevel privateLevel;
        try {
            privateLevel = registry.createDynamicDimension(dimensionId, generator,
                    copyDimensionType(overworld.dimensionType()));
        } catch (RuntimeException exception) {
            UniversalToolMod.LOGGER.error("Failed to create personal space dimension {}", dimensionId, exception);
            reject(player, payload, "message.universal_tool.personal_space.create_failed");
            return;
        }
        if (privateLevel == null) {
            UniversalToolMod.LOGGER.error("Internal dimension registry rejected personal space {} (dimension type or ID is already registered)",
                    dimensionId);
            reject(player, payload, "message.universal_tool.personal_space.create_failed");
            return;
        }

        configureLevel(privateLevel, environment, true);
        UniversalToolMod.LOGGER.info("Created {} personal island {} in {} ms", environment.id(), dimensionId,
                (System.nanoTime() - startedAt) / 1_000_000L);
        data.addSpace(record);
        accept(player, payload);
        enterLoadedSpace(player, record, privateLevel);
        player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.created"), true);
    }

    static ChunkGenerator createGenerator(ServerLevel overworld, PersonalSpaceSavedData.SpaceRecord record) {
        PrivateSpaceEnvironment environment = record.environment();
        if (!record.isFiniteIsland() && !PersonalSpaceSavedData.SpaceRecord.LEGACY_VERSION.equals(record.generationVersion()))
            throw new IllegalStateException("Unsupported private terrain version: " + record.generationVersion());
        var registries = overworld.registryAccess();
        if (!record.isFiniteIsland() && environment == PrivateSpaceEnvironment.SUPERFLAT) {
            WorldPreset flatPreset = registries.lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(WorldPresets.FLAT).value();
            WorldDimensions dimensions = flatPreset.createWorldDimensions();
            return dimensions.dimensions().get(LevelStem.OVERWORLD).generator();
        }

        ResourceLocation biomeId = environment.profile().biome().location();
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, biomeId));
        if (record.isFiniteIsland()) return new FiniteIslandChunkGenerator(new FixedBiomeSource(biome), environment,
                record.terrainSeed(), record.terrainSize());
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
    }

    private static DimensionType copyDimensionType(DimensionType source) {
        return new DimensionType(source.fixedTime(), source.hasSkyLight(), source.hasCeiling(), source.ultraWarm(),
                source.natural(), source.coordinateScale(), source.bedWorks(), source.respawnAnchorWorks(),
                source.minY(), source.height(), source.logicalHeight(), source.infiniburn(), source.effectsLocation(),
                source.ambientLight(), source.monsterSettings());
    }

    static void configureLevel(ServerLevel level, PrivateSpaceEnvironment environment, boolean fresh) {
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(59_999_968);
        level.getWorldBorder().setWarningBlocks(0);

        BlockPos spawn = level.getSharedSpawnPos();
        if (fresh) {
            spawn = prepareSpawnArea(level, environment);
        }

        if (fresh || !spawn.equals(level.getSharedSpawnPos())) level.setDefaultSpawnPos(spawn, 0.0F);
        ensureReturnGate(level);
    }

    static void ensureReturnGate(ServerLevel level) {
        BlockPos pos = level.getSharedSpawnPos().offset(-3, 0, 0);
        if (!level.getBlockState(pos).is(ModBlocks.PERSONAL_SPACE_RETURN.get()))
            level.setBlock(pos, ModBlocks.PERSONAL_SPACE_RETURN.get().defaultBlockState(), 3);
    }

    @SubscribeEvent
    public static void protectReturnGate(BlockEvent.BreakEvent event) {
        if (!event.getState().is(ModBlocks.PERSONAL_SPACE_RETURN.get())) return;
        event.setCanceled(true);
        event.getPlayer().displayClientMessage(Component.translatable(
                "message.universal_tool.personal_space.return_gate_protected"), true);
    }

    @SubscribeEvent
    public static void protectReturnGateFromExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockState(pos)
                .is(ModBlocks.PERSONAL_SPACE_RETURN.get()));
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
                    if (!level.getBlockState(clear).isAir()) level.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
                }
                // Snow is re-applied after the clearing pass so the landing pad reads as frozen ground.
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
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getY() >= player.serverLevel().getMinBuildHeight() - 16
                || !isPersonalSpace(player.server, player.serverLevel().dimension())) return;
        BlockPos spawn = player.serverLevel().getSharedSpawnPos();
        BlockPos safe = findSafePosition(player.serverLevel(), spawn.getX(), spawn.getY(), spawn.getZ());
        player.teleportTo(player.serverLevel(), safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
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
        PersonalSpaceSavedData.SpaceRecord ownSpace = data.getSpace(player.getUUID()).orElse(null);
        ServerLevel destination;
        PersonalSpaceSavedData.Position position;
        if (ownSpace != null && ownSpace.dimension().equals(deathDimension.location())) {
            destination = getOrLoadSpace(player.server, ownSpace);
            if (destination == null) destination = player.server.overworld();
            configureLevel(destination, ownSpace.environment(), false);
            BlockPos spawn = destination.getSharedSpawnPos();
            position = new PersonalSpaceSavedData.Position(destination.dimension(), spawn.getX() + 0.5,
                    spawn.getY(), spawn.getZ() + 0.5, 0, 0);
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
            if (target == player.server.overworld()) data.clearReturnPosition(player.getUUID());
            data.setResumePosition(player.getUUID(), positionOf(player));
            RESPAWN_RETURN_PENDING.remove(player.getUUID());
        };
        if (isPersonalSpace(player.server, target.dimension()))
            PersonalDimensionRegistry.from(player.server).whenClientReady(player, target, finishRespawn);
        else finishRespawn.run();
    }

    private static void enterSpace(ServerPlayer player, PersonalSpaceSavedData.SpaceRecord record) {
        ServerLevel level = getOrLoadSpace(player.server, record);
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.load_failed"), true);
            return;
        }
        configureLevel(level, record.environment(), false);
        enterLoadedSpace(player, record, level);
    }

    private static void enterLoadedSpace(ServerPlayer player, PersonalSpaceSavedData.SpaceRecord record, ServerLevel level) {
        PersonalDimensionRegistry.from(player.server).whenClientReady(player, level, () -> {
            PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
            if (!player.level().dimension().location().equals(record.dimension())) {
                data.setReturnPosition(player.getUUID(), positionOf(player));
            }
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            data.setResumePosition(player.getUUID(), positionOf(player));
        });
    }

    static ServerLevel getOrLoadSpace(MinecraftServer server, PersonalSpaceSavedData.SpaceRecord record) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            PersonalDimensionRegistry.from(server).adoptExistingDimension(level);
            return level;
        }
        try {
            PersonalDimensionRegistry registry = PersonalDimensionRegistry.from(server);
            return registry.loadDynamicDimension(record.dimension(), createGenerator(server.overworld(), record),
                    copyDimensionType(server.overworld().dimensionType()));
        } catch (RuntimeException exception) {
            UniversalToolMod.LOGGER.error("Failed to load personal space {}", record.dimension(), exception);
            return null;
        }
    }

    public static void returnPlayer(ServerPlayer player) {
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        PersonalSpaceSavedData.Position destination = data.getReturnPosition(player.getUUID()).orElse(null);
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
            data.clearReturnPosition(player.getUUID());
            player.teleportTo(target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    returnPosition.yaw(), returnPosition.pitch());
            player.displayClientMessage(Component.translatable("message.universal_tool.personal_space.returned"), true);
        };
        if (isPersonalSpace(player.server, target.dimension()))
            PersonalDimensionRegistry.from(player.server).whenClientReady(player, target, teleport);
        else teleport.run();
    }

    private static ServerLevel getOrLoadDestination(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level != null || dimension.equals(Level.OVERWORLD) || dimension.equals(Level.NETHER) || dimension.equals(Level.END)) {
            return level;
        }
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(server);
        for (PersonalSpaceSavedData.SpaceRecord record : data.allSpaces()) {
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
            if (level != null) PersonalDimensionRegistry.from(server).adoptExistingDimension(level);
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
                Optional<PersonalSpaceSavedData.SpaceRecord> space = data.allSpaces().stream()
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
                } else player.teleportTo(level, resume.x(), resume.y(), resume.z(), resume.yaw(), resume.pitch());
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PersonalSpaceSavedData data = PersonalSpaceSavedData.get(player.server);
        boolean fromSpace = isPersonalSpace(player.server, event.getFrom());
        boolean toSpace = isPersonalSpace(player.server, event.getTo());
        if (fromSpace && !toSpace && !RESPAWN_RETURN_PENDING.contains(player.getUUID())
                && !DEATH_DIMENSIONS.containsKey(player.getUUID())) {
            data.clearReturnPosition(player.getUUID());
        }
        if (fromSpace && !event.getFrom().equals(event.getTo())
                && !DEATH_DIMENSIONS.containsKey(player.getUUID())) {
            ResourceKey<Level> oldDimension = event.getFrom();
            player.server.execute(() -> unloadIfEmpty(player.server, oldDimension));
        }
    }

    @SubscribeEvent
    public static void tickDimensionSynchronisation(ServerTickEvent.Post event) {
        var server = event.getServer();
        var registry = PersonalDimensionRegistry.from(server);
        registry.tickPendingTeleports();
        // Logout events can precede removal from PlayerList. Recheck emptiness after that removal.
        if (server.getTickCount() % 20 == 0) {
            for (var record : PersonalSpaceSavedData.get(server).allSpaces()) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
                ServerLevel loaded = server.getLevel(key);
                if (loaded != null) ensureReturnGate(loaded);
                if (!registry.hasPendingTeleport(record.dimension()))
                    unloadIfEmpty(server, key);
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

    private static void unloadIfEmpty(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !isPersonalSpace(server, dimension)) return;
        if (server.getPlayerList().getPlayers().stream().anyMatch(player -> player.level() == level)) return;
        PersonalDimensionRegistry.from(server).unloadDynamicDimension(dimension.location());
    }
}
