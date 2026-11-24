package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗计时、32 格球形经验分享、全部 32 项技能的试炼进度统计以及防刷方块放置记录。
 */
final class SkillCombatTracker {
    static final int MAX_PLACED_BLOCK_HISTORY = 16384;

    private static final Map<UUID, LinkedHashMap<String, Boolean>> PLACED_BLOCKS = new ConcurrentHashMap<>();
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
        if (!SkillManager.isEnabled(victim.serverLevel())) return;
        if (directAttacker instanceof LivingEntity attacker) {
            SkillManager.data(victim).markCombat(gameTime);
            victim.getPersistentData().putLong("universal_tool_last_hit", gameTime);
            victim.getPersistentData().putUUID("universal_tool_last_attacker", attacker.getUUID());
        }
    }

    static void onVictimDamaged(ServerPlayer victim, float finalDamage, boolean isFall) {
        if (!SkillManager.isEnabled(victim.serverLevel()) || finalDamage <= 0) return;
        PlayerSkillData data = SkillManager.data(victim);
        int rounded = Math.max(1, Math.round(finalDamage));
        data.incrementStat("damage_taken", rounded, s -> SkillManager.notifySkillUnlocked(victim, s));
        if (isFall) {
            data.incrementStat("fall_damage_taken", rounded, s -> SkillManager.notifySkillUnlocked(victim, s));
        }
        if (finalDamage >= 8.0F && victim.isAlive()) {
            data.incrementStat("heavy_hits_survived", 1, s -> SkillManager.notifySkillUnlocked(victim, s));
        }
        if (victim.isAlive() && victim.getMaxHealth() > 0 && victim.getHealth() / victim.getMaxHealth() < 0.35F) {
            long lastMarked = victim.getPersistentData().getLong("universal_tool_low_hp_tick");
            long now = victim.serverLevel().getGameTime();
            if (now - lastMarked > 200) {
                victim.getPersistentData().putLong("universal_tool_low_hp_tick", now);
                data.incrementStat("low_health_survives", 1, s -> SkillManager.notifySkillUnlocked(victim, s));
            }
        }
        markDirty(victim);
    }

    static void onLivingDamage(ServerPlayer player, LivingEntity target, float newDamage, boolean isSkillDamage) {
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        long gameTime = player.serverLevel().getGameTime();
        PlayerSkillData data = SkillManager.data(player);
        data.markCombat(gameTime);
        player.getPersistentData().putLong("universal_tool_combat_game_time", gameTime);

        if (newDamage > 0 && target != player) {
            int dmgInt = Math.max(1, Math.round(newDamage));
            data.incrementStat("damage_dealt", dmgInt, s -> SkillManager.notifySkillUnlocked(player, s));
            if (!isSkillDamage && SkillModSynergyHandler.universalToolTierRank(player.getMainHandItem()) > 0) {
                data.incrementStat("universal_tool_hits", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }
        }

        if (target instanceof Enemy) {
            player.getPersistentData().putUUID("universal_tool_last_enemy", target.getUUID());
        }

        SkillPassiveHandler.trackMomentum(player, target, gameTime);
        SkillPassiveHandler.applyBloodthirst(player, target, newDamage);

        if (target.isAlive() || target == player) {
            markDirty(player);
            return;
        }
        if (!markKillReward(player, target)) {
            return;
        }

        recordKillDiscoveries(player, data, target, gameTime, isSkillDamage);
        SkillActiveExecutor.onBerserkKill(player);
        if (target instanceof Enemy) {
            SkillResonanceHandler.onHostileKill(player, target);
        }
        if (player.getY() >= 120) {
            data.incrementStat("high_place_reached", 1, s -> SkillManager.notifySkillUnlocked(player, s));
        }

        int xp = isBoss(target) ? 450 : target instanceof Enemy ? 20 : target instanceof Animal ? 4 : 6;
        awardCombatXp(player, target, xp);
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss
                || entity instanceof Warden || entity instanceof ElderGuardian;
    }

    private static void recordKillDiscoveries(ServerPlayer player, PlayerSkillData data,
                                              LivingEntity target, long gameTime, boolean isSkillDamage) {
        if (target instanceof Enemy || isBoss(target)) {
            data.incrementStat("kills_hostile", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            if (isBoss(target)) {
                data.incrementStat("boss_kills", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }
            if (isSkillDamage || target.getPersistentData().getBoolean("universal_tool_ranged_enemy")) {
                data.incrementStat("ranged_or_skill_kills", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }

            long hit = player.getPersistentData().getLong("universal_tool_last_hit");
            if (hit > 0 && gameTime - hit <= 200
                    && player.getPersistentData().hasUUID("universal_tool_last_attacker")
                    && player.getPersistentData().getUUID("universal_tool_last_attacker").equals(target.getUUID())) {
                data.incrementStat("counter_kills", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }

            long firstWindow = player.getPersistentData().getLong("universal_tool_combat_window_start");
            if (firstWindow == 0 || gameTime - firstWindow > 100) {
                player.getPersistentData().putLong("universal_tool_combat_window_start", gameTime);
                player.getPersistentData().putInt("universal_tool_combat_window_kills", 0);
            }
            int kills = player.getPersistentData().getInt("universal_tool_combat_window_kills") + 1;
            player.getPersistentData().putInt("universal_tool_combat_window_kills", kills);
            if (kills >= 3) {
                player.getPersistentData().putInt("universal_tool_combat_window_kills", 0);
                data.incrementStat("combat_multi_kills", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }
        }

        int enemyCount = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(12.0), entity -> entity instanceof Enemy).size();
        if (enemyCount >= 3) {
            long lastCrowd = player.getPersistentData().getLong("universal_tool_crowd_tick");
            if (gameTime - lastCrowd > 100) {
                player.getPersistentData().putLong("universal_tool_crowd_tick", gameTime);
                data.incrementStat("nearby_crowd_fights", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            }
        }
    }

    /** 32 格球形范围经验分配（不因多人分摊而虚增总经验）。 */
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
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        PlayerSkillData data = SkillManager.data(player);
        if (data.addDiscovery("dimension:" + dimensionId)) {
            data.incrementStat("dimensions_visited", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            int bonus = explorersXpBonus(player, SkillManager.PLAYER_DISCOVERY_EXPERIENCE);
            awardDiscovery(player, bonus);
        }
    }

    static void tickPlayer(ServerPlayer player) {
        if (!SkillManager.isEnabled(player.serverLevel())) return;

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
            data.incrementStat("biomes_discovered", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            awardDiscovery(player, explorersXpBonus(player, SkillManager.PLAYER_DISCOVERY_EXPERIENCE));
        }

        var chunk = player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z,
                ChunkStatus.FULL, false);
        if (chunk != null) {
            chunk.getAllStarts().forEach((structure, start) -> {
                if (start.isValid() && start.getBoundingBox().isInside(player.blockPosition())) {
                    String dimension = player.serverLevel().dimension().location().toString();
                    String structureKey = "structure:" + dimension + ":" + start.getChunkPos().toLong();
                    if (data.addDiscovery(structureKey)) {
                        data.incrementStat("structures_discovered", 1, s -> SkillManager.notifySkillUnlocked(player, s));
                        awardDiscovery(player, explorersXpBonus(player, SkillManager.STRUCTURE_DISCOVERY_EXPERIENCE));
                    }
                }
            });
        }

        if (player.getY() >= 120 && data.stat("high_place_reached") == 0) {
            data.incrementStat("high_place_reached", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            SkillManager.sync(player);
        }
    }

    private static int explorersXpBonus(ServerPlayer player, int baseXp) {
        int lv = SkillPassiveHandler.equippedLevel(player, ModSkills.EXPLORERS_EYE);
        if (lv <= 0) return baseXp;
        float mult = lv == 1 ? 1.20F : lv == 2 ? 1.35F : 1.50F;
        return Math.round(baseXp * mult);
    }

    static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        if (isPlayerPlaced(player, pos)) {
            forgetPlacement(player, pos);
            return;
        }
        recordMinedBlock(player, state);
        if (SkillPassiveHandler.equippedLevel(player, ModSkills.FOCUS_MINING) >= 3
                && player.getRandom().nextFloat() < 0.25F) {
            var held = player.getMainHandItem();
            if (held.isDamageableItem() && held.isDamaged()) {
                held.setDamageValue(Math.max(0, held.getDamageValue() - 1));
            }
        }
        SkillNewActivesHelper.tryChainVeinMine(player, pos, state);
    }

    static void recordMinedBlock(ServerPlayer player, BlockState state) {
        PlayerSkillData data = SkillManager.data(player);
        if (isMatureCrop(state)) {
            data.incrementStat("crops_harvested", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            SkillResonanceHandler.onCraftsmanHarvest(player, player.blockPosition());
            award(player, 8, false);
            return;
        }
        data.incrementStat("blocks_mined", 1, s -> SkillManager.notifySkillUnlocked(player, s));
        if (isOre(state)) {
            data.incrementStat("ores_mined", 1, s -> SkillManager.notifySkillUnlocked(player, s));
            SkillResonanceHandler.onCraftsmanHarvest(player, player.blockPosition());
            award(player, 20, false);
        } else {
            award(player, 1, false);
        }
    }

    /** 永久记录玩家放置方块（采用有界 FIFO 淘汰，不再按 6000 tick 过期，修复历史缺陷 #7）。 */
    static void onBlockPlaced(ServerPlayer player, BlockPos pos) {
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        LinkedHashMap<String, Boolean> placed = PLACED_BLOCKS.computeIfAbsent(player.getUUID(),
                ignored -> new LinkedHashMap<>(256, 0.75F, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > MAX_PLACED_BLOCK_HISTORY;
                    }
                });
        synchronized (placed) {
            placed.put(placementKey(player, pos), Boolean.TRUE);
        }
    }

    static void award(ServerPlayer player, long xp, boolean immediate) {
        if (xp <= 0 || !SkillManager.isEnabled(player.serverLevel())) return;
        PlayerSkillData data = SkillManager.data(player);
        StarRank oldRank = data.starRank();
        int oldLevel = data.level();
        int levels = data.addExperience(xp, skill -> SkillManager.notifySkillUnlocked(player, skill));
        if (levels > 0) {
            StarRank newRank = data.starRank();
            if (newRank != oldRank) {
                SkillManager.notifyStarRankUp(player, newRank, data.level());
            } else {
                SkillManager.notifyLevelUp(player, data.level());
            }
            SkillManager.applyPassiveEffects(player);
        } else {
            SkillManager.notifyXpGain(player, (int) Math.min(Integer.MAX_VALUE, xp));
        }
        if (levels > 0 || immediate || data.level() != oldLevel) {
            SkillManager.sync(player);
        } else {
            markDirty(player);
        }
    }

    private static void awardDiscovery(ServerPlayer player, int xp) {
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

    static boolean isPlayerPlaced(ServerPlayer player, BlockPos pos) {
        LinkedHashMap<String, Boolean> placed = PLACED_BLOCKS.get(player.getUUID());
        if (placed == null) return false;
        synchronized (placed) {
            return placed.containsKey(placementKey(player, pos));
        }
    }

    private static void forgetPlacement(ServerPlayer player, BlockPos pos) {
        LinkedHashMap<String, Boolean> placed = PLACED_BLOCKS.get(player.getUUID());
        if (placed != null) {
            synchronized (placed) {
                placed.remove(placementKey(player, pos));
            }
        }
    }

    private static String placementKey(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().dimension().location() + "|" + pos.asLong();
    }
}
