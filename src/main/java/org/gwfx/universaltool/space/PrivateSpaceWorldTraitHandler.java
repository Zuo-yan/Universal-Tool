package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.List;
import java.util.Optional;

/**
 * Runtime enforcement of {@link PrivateSpaceWorldTraits}:
 * natural-spawn filtering & multipliers (without blocking spawners/eggs/breeding),
 * independent weather sync, anti-griefing protection, and gentle environmental perks.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PrivateSpaceWorldTraitHandler {
    private static final int EFFECT_REFRESH_INTERVAL = 20;
    private static final int NIGHT_VISION_DURATION_TICKS = 260; // Avoids vanilla's <=200t flash warning
    private static final int STANDARD_PERK_DURATION_TICKS = 80;
    private static final int EXTRA_SPAWN_ATTEMPTS_PER_PLAYER = 3;

    private PrivateSpaceWorldTraitHandler() {}

    // --------------------------------------------------------- natural mob spawns

    public static boolean isNaturalSpawn(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (PrivateSpaceManager.isStarryPocket(level)) {
            event.setCanceled(true);
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(level);
        if (env.isEmpty()) {
            return;
        }
        PrivateSpaceWorldTraits.SpawnPolicy policy = env.get().traits().spawns();
        if (policy.mode() == PrivateSpaceWorldTraits.SpawnMode.NO_NATURAL) {
            event.setCanceled(true);
            return;
        }
        List<MobSpawnSettings.SpawnerData> custom = policy.spawnerDataFor(event.getMobCategory());
        if (custom.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        for (MobSpawnSettings.SpawnerData existing : List.copyOf(event.getSpawnerDataList())) {
            event.removeSpawnerData(existing);
        }
        for (MobSpawnSettings.SpawnerData data : custom) {
            event.addSpawnerData(data);
        }
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!isNaturalSpawn(event.getSpawnType()) || !(event.getLevel().getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (PrivateSpaceManager.isStarryPocket(level)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(level);
        if (env.isEmpty()) {
            return;
        }
        boolean allowed = evaluateNaturalSpawnAllowed(env.get(), event.getLevel(), event.getEntityType(), event.getPos());
        event.setResult(allowed
                ? MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED
                : MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!isNaturalSpawn(event.getSpawnType()) || !(event.getLevel().getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (PrivateSpaceManager.isStarryPocket(level)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(level);
        if (env.isEmpty()) {
            return;
        }
        Mob mob = event.getEntity();
        boolean allowed = evaluateNaturalSpawnAllowed(env.get(), event.getLevel(), mob.getType(), mob.blockPosition())
                && mob.checkSpawnObstruction(event.getLevel());
        event.setResult(allowed
                ? MobSpawnEvent.PositionCheck.Result.SUCCEED
                : MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    /**
     * Authoritative placement check for natural spawns inside a pocket realm.
     * Kept separate from event plumbing so tests can verify rule decisions cleanly.
     */
    static boolean evaluateNaturalSpawnAllowed(PrivateSpaceEnvironment env, ServerLevelAccessor level,
                                               EntityType<?> entityType, BlockPos pos) {
        PrivateSpaceWorldTraits.SpawnPolicy policy = env.traits().spawns();
        if (!policy.allowsNatural(entityType)) {
            return false;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL && entityType.getCategory() == MobCategory.MONSTER) {
            return false;
        }
        if (entityType == EntityType.GLOW_SQUID) {
            return !level.getFluidState(pos).isEmpty();
        }
        if (level.getBlockState(pos).isSuffocating(level, pos)
                || level.getBlockState(pos.above()).isSuffocating(level, pos.above())) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        boolean airborne = entityType == EntityType.BEE || entityType == EntityType.PARROT
                || entityType == EntityType.ALLAY || entityType == EntityType.BAT;
        if (!airborne && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        if (entityType.getCategory() == MobCategory.MONSTER) {
            int maxBlockLight = (entityType == EntityType.BLAZE || entityType == EntityType.MAGMA_CUBE) ? 11 : 7;
            if (level.getBrightness(LightLayer.BLOCK, pos) > maxBlockLight) {
                return false;
            }
        }
        return true;
    }

    // --------------------------------------------------------- tick: boost & weather

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            if (PrivateSpaceManager.isStarryPocket(level)) {
                if (tick % 40 == 0) {
                    if (level.getLevelData() instanceof PersonalDimensionLevelData personalData) {
                        personalData.setWeatherRule(PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR);
                    }
                    PrivateSpaceManager.applyWeatherState(level, PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR);
                }
                continue;
            }
            Optional<PrivateSpaceEnvironment> resolved = PrivateSpaceManager.resolveEnvironment(level);
            if (resolved.isEmpty()) {
                continue;
            }
            PrivateSpaceEnvironment env = resolved.get();
            PrivateSpaceWorldTraits traits = env.traits();
            if (tick % 40 == 0) {
                if (level.getLevelData() instanceof PersonalDimensionLevelData personalData) {
                    personalData.setWeatherRule(traits.weather());
                }
                PrivateSpaceManager.applyWeatherState(level, traits.weather());
            }

            double hostileMult = traits.spawns().hostileRateMultiplier();
            if (hostileMult > 1.0 && server.isSpawningMonsters()
                    && level.getDifficulty() != Difficulty.PEACEFUL
                    && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                    && !level.players().isEmpty()) {
                boostHostileSpawns(level, hostileMult);
            }
        }
    }

    private static void boostHostileSpawns(ServerLevel level, double multiplier) {
        int activeHostiles = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.getClassification(true) == MobCategory.MONSTER
                    && !mob.isPersistenceRequired() && !mob.requiresCustomPersistence()) {
                activeHostiles++;
            }
        }
        int playerCount = Math.max(1, level.players().size());
        int boostedCap = (int) Math.round(MobCategory.MONSTER.getMaxInstancesPerChunk() * playerCount * multiplier);
        if (activeHostiles >= boostedCap) {
            return;
        }

        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < EXTRA_SPAWN_ATTEMPTS_PER_PLAYER; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = 24.0 + random.nextDouble() * 48.0;
                int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
                if (!level.isLoaded(new BlockPos(x, player.getBlockY(), z))) {
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (surfaceY <= level.getMinBuildHeight() + 1) {
                    continue;
                }
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, new BlockPos(x, surfaceY, z));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PrivateSpaceManager.isStarryPocket(player.serverLevel())) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F));
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(player.serverLevel());
        if (env.isEmpty()) {
            return;
        }
        PrivateSpaceWorldTraits.WeatherRule weather = env.get().traits().weather();
        if (weather == PrivateSpaceWorldTraits.WeatherRule.ALWAYS_CLEAR) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F));
        } else if (weather == PrivateSpaceWorldTraits.WeatherRule.SNOW_FLURRY) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.65F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F));
        }
    }

    // --------------------------------------------------------- safety & perks

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() != null
                && event.getEntity().level() instanceof ServerLevel level
                && (PrivateSpaceManager.isStarryPocket(level) || PrivateSpaceManager.resolveEnvironment(level).isPresent())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || (!PrivateSpaceManager.isStarryPocket(level) && PrivateSpaceManager.resolveEnvironment(level).isEmpty())) {
            return;
        }
        Entity direct = event.getExplosion().getDirectSourceEntity();
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        if (direct instanceof Mob || indirect instanceof Mob) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(player.serverLevel());
        if (env.isEmpty()) {
            return;
        }
        PrivateSpaceWorldTraits.EnvironmentalPerks perks = env.get().traits().perks();
        if (perks.freezeImmune() && player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
        }
        if (perks.nourishIntervalTicks() > 0
                && player.tickCount % perks.nourishIntervalTicks() == 0
                && player.getFoodData().needsFood()) {
            player.getFoodData().eat(1, 0.5F);
        }
        if (perks.potionEffect() != null && player.tickCount % EFFECT_REFRESH_INTERVAL == 0) {
            int duration = perks.potionEffect().equals(MobEffects.NIGHT_VISION)
                    ? NIGHT_VISION_DURATION_TICKS
                    : STANDARD_PERK_DURATION_TICKS;
            player.addEffect(new MobEffectInstance(perks.potionEffect(), duration,
                    perks.amplifier(), true, false, true));
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<PrivateSpaceEnvironment> env = PrivateSpaceManager.resolveEnvironment(player.serverLevel());
        if (env.isEmpty()) {
            return;
        }
        float multiplier = env.get().traits().perks().fallDamageMultiplier();
        if (multiplier != 1.0F) {
            event.setDamageMultiplier(event.getDamageMultiplier() * multiplier);
        }
    }
}
