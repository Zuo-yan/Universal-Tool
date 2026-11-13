package org.gwfx.universaltool.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks combat timers, 32-block spherical XP sharing, discovery milestones,
 * and player-placed block exclusions for skill progression.
 */
final class SkillCombatTracker {
    static final int MAX_PLACED_BLOCK_HISTORY = 16384;

    private static final Map<UUID, Map<String, Long>> PLACED_BLOCKS = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private SkillCombatTracker() {}

    static void markDirty(ServerPlayer player) {
        DIRTY.add(player.getUUID());
    }

    static void clearDirty(UUID playerId) {
        DIRTY.remove(playerId);
    }

    static boolean consumeDirty(UUID playerId) {
        return DIRTY.remove(playerId);
    }

    static void clearAll() {
        PLACED_BLOCKS.clear();
        DIRTY.clear();
    }

    static void onIncomingHit(ServerPlayer victim, Entity directAttacker, long gameTime) {
        if (directAttacker instanceof LivingEntity attacker) {
            SkillManager.data(victim).markCombat(gameTime);
            victim.getPersistentData().putLong("universal_tool_last_hit", gameTime);
            victim.getPersistentData().putUUID("universal_tool_last_attacker", attacker.getUUID());
        }
    }

    static void onLivingDamage(ServerPlayer player, LivingEntity target, float newDamage) {
        long gameTime = player.serverLevel().getGameTime();
        PlayerSkillData data = SkillManager.data(player);
        data.markCombat(gameTime);
        player.getPersistentData().putLong("universal_tool_combat_game_time", gameTime);

        if (target instanceof Enemy) {
            player.getPersistentData().putUUID("universal_tool_last_enemy", target.getUUID());
        }

        SkillPassiveHandler.trackMomentum(player, target, gameTime);
        SkillPassiveHandler.applyBloodthirst(player, target, newDamage);

        if (target.isAlive() || target == player) {
            return;
        }
        if (!markKillReward(player, target)) {
            return;
        }

        recordKillDiscoveries(player, data, target, gameTime);
        if (player.getY() >= 100) {
            data.record("explore:high_place");
        }

        int xp = target instanceof Enemy ? 20 : target instanceof Animal ? 4 : 6;
        awardCombatXp(player, target, xp);
    }

    private static void recordKillDiscoveries(ServerPlayer player, PlayerSkillData data,
                                              LivingEntity target, long gameTime) {
        if (target instanceof Enemy) {
            data.record("combat:hostile_kill");
            int hostileKills = player.getPersistentData().getInt("universal_tool_hostile_kills") + 1;
            player.getPersistentData().putInt("universal_tool_hostile_kills", hostileKills);
            if (hostileKills >= 10) {
                data.record("combat:ten_kills");
            }
            player.getPersistentData().putString("universal_tool_last_enemy_type",
                    target.getType().builtInRegistryHolder().getRegisteredName());

            long hit = player.getPersistentData().getLong("universal_tool_last_hit");
            if (hit > 0 && gameTime - hit <= 200
                    && player.getPersistentData().hasUUID("universal_tool_last_attacker")
                    && player.getPersistentData().getUUID("universal_tool_last_attacker").equals(target.getUUID())) {
                data.record("combat:counter_kill");
            }

            if (target.getPersistentData().getBoolean("universal_tool_ranged_enemy")) {
                data.record("combat:ranged_kill");
                long firstRanged = player.getPersistentData().getLong("universal_tool_ranged_window_start");
                if (firstRanged == 0 || gameTime - firstRanged > 200) {
                    player.getPersistentData().putLong("universal_tool_ranged_window_start", gameTime);
                    player.getPersistentData().putInt("universal_tool_ranged_kills", 0);
                }
                int rangedKills = player.getPersistentData().getInt("universal_tool_ranged_kills") + 1;
                player.getPersistentData().putInt("universal_tool_ranged_kills", rangedKills);
                if (rangedKills >= 3) {
                    data.record("combat:three_ranged_kills");
                }
            }

            long firstWindow = player.getPersistentData().getLong("universal_tool_combat_window_start");
            if (firstWindow == 0 || gameTime - firstWindow > 100) {
                player.getPersistentData().putLong("universal_tool_combat_window_start", gameTime);
                player.getPersistentData().putInt("universal_tool_combat_window_kills", 0);
            }
            int kills = player.getPersistentData().getInt("universal_tool_combat_window_kills") + 1;
            player.getPersistentData().putInt("universal_tool_combat_window_kills", kills);
            if (kills >= 3) {
                data.record("combat:multi_kill");
            }
        }

        int enemyCount = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(12.0), entity -> entity instanceof Enemy).size();
        if (enemyCount >= 3) {
            data.record("combat:three_nearby");
        }
    }

    static void awardCombatXp(ServerPlayer killer, LivingEntity target, int totalXp) {
        double maxDistSqr = (double) SkillManager.COMBAT_XP_RADIUS * SkillManager.COMBAT_XP_RADIUS;
        List<ServerPlayer> nearby = killer.serverLevel().getPlayers(candidate ->
                candidate.isAlive() && !candidate.isSpectator() && candidate.distanceToSqr(target) <= maxDistSqr);
        if (nearby.isEmpty()) {
            nearby = List.of(killer);
        }
        int each = Math.max(1, totalXp / nearby.size());
        for (ServerPlayer recipient : nearby) {
            award(recipient, each, false);
        }
    }

    private static boolean markKillReward(ServerPlayer player, LivingEntity target) {
        String key = target.getUUID().toString();
        long lastTick = player.getPersistentData().getLong("universal_tool_rewarded_kill_tick");
        String lastKey = player.getPersistentData().getString("universal_tool_rewarded_kill_key");
        long now = player.serverLevel().getGameTime();
        if (key.equals(lastKey) && now - lastTick <= 1) {
            return false;
        }
        player.getPersistentData().putString("universal_tool_rewarded_kill_key", key);
        player.getPersistentData().putLong("universal_tool_rewarded_kill_tick", now);
        return true;
    }

    static void onChangedDimension(ServerPlayer player, String dimensionId) {
        PlayerSkillData data = SkillManager.data(player);
        if (data.addDiscovery("dimension:" + dimensionId)) {
            data.addExperience(SkillManager.PLAYER_DISCOVERY_EXPERIENCE);
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.discovery",
                    SkillManager.PLAYER_DISCOVERY_EXPERIENCE), true);
        }
    }

    static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 == 0 && consumeDirty(player.getUUID())) {
            SkillManager.sync(player);
        }

        if (player.tickCount % 40 == 0) {
            player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(SkillManager.COMBAT_XP_RADIUS),
                            entity -> entity instanceof Enemy)
                    .forEach(entity -> {
                        String type = entity.getType().getDescriptionId();
                        if (type.contains("skeleton") || type.contains("pillager")
                                || type.contains("witch") || type.contains("blaze")) {
                            entity.getPersistentData().putBoolean("universal_tool_ranged_enemy", true);
                        }
                    });
        }

        PlayerSkillData data = SkillManager.data(player);
        String biome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        if (!biome.isEmpty() && data.addDiscovery("biome:" + biome)) {
            awardDiscovery(player, SkillManager.PLAYER_DISCOVERY_EXPERIENCE);
        }

        var chunk = player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z,
                ChunkStatus.FULL, false);
        if (chunk != null) {
            chunk.getAllStarts().forEach((structure, start) -> {
                if (start.isValid() && start.getBoundingBox().isInside(player.blockPosition())) {
                    String dimension = player.serverLevel().dimension().location().toString();
                    String structureKey = "structure:" + dimension + ":" + start.getChunkPos().toLong();
                    if (data.addDiscovery(structureKey)) {
                        awardDiscovery(player, SkillManager.STRUCTURE_DISCOVERY_EXPERIENCE);
                    }
                }
            });
        }

        if (player.getY() >= 120 && data.record("explore:high_place")) {
            data.unlockEligibleSkills(definition -> player.displayClientMessage(
                    Component.translatable("message.universal_tool.skills.unlocked",
                            Component.translatable(definition.translationKey())), true));
            SkillManager.sync(player);
        }
    }

    static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        if (isPlayerPlaced(player, pos)) {
            forgetPlacement(player, pos);
            return;
        }
        PlayerSkillData data = SkillManager.data(player);
        if (isMatureCrop(state)) {
            award(player, 8, false);
            return;
        }
        if (isOre(state)) {
            data.record("mine:ore");
            award(player, 20, false);
        } else {
            award(player, 1, false);
        }
    }

    static void onBlockPlaced(ServerPlayer player, BlockPos pos) {
        Map<String, Long> placed = PLACED_BLOCKS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        placed.put(placementKey(player, pos), player.serverLevel().getGameTime());
        if (placed.size() > MAX_PLACED_BLOCK_HISTORY) {
            var it = placed.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    /** Grants experience, reports level-ups and unlocks, and batches non-immediate syncs. */
    static void award(ServerPlayer player, long xp, boolean immediate) {
        PlayerSkillData data = SkillManager.data(player);
        int levels = data.addExperience(xp, definition -> player.displayClientMessage(
                Component.translatable("message.universal_tool.skills.unlocked",
                        Component.translatable(definition.translationKey())), true));
        if (levels > 0 || immediate) {
            SkillManager.sync(player);
        } else {
            markDirty(player);
        }
    }

    private static void awardDiscovery(ServerPlayer player, int xp) {
        player.displayClientMessage(Component.translatable("message.universal_tool.skills.discovery", xp)
                .withStyle(ChatFormatting.GREEN), true);
        award(player, xp, true);
    }

    static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.NETHER_QUARTZ_ORE);
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.hasProperty(BlockStateProperties.AGE_3) && state.getValue(BlockStateProperties.AGE_3) >= 3;
        }
        return false;
    }

    private static boolean isPlayerPlaced(ServerPlayer player, BlockPos pos) {
        Map<String, Long> placed = PLACED_BLOCKS.get(player.getUUID());
        return placed != null && placed.containsKey(placementKey(player, pos));
    }

    private static void forgetPlacement(ServerPlayer player, BlockPos pos) {
        Map<String, Long> placed = PLACED_BLOCKS.get(player.getUUID());
        if (placed != null) {
            placed.remove(placementKey(player, pos));
        }
    }

    private static String placementKey(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().dimension().location() + "|" + pos.asLong();
    }
}
