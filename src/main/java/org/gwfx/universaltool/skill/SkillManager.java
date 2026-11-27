package org.gwfx.universaltool.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.SkillActionPayload;
import org.gwfx.universaltool.network.SkillNotificationPayload;
import org.gwfx.universaltool.network.SyncSkillDataPayload;

import java.util.List;

/**
 * 技能系统服务端权威门面：全局开关、持久化同步、动作处理与 NeoForge 事件总调度。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class SkillManager {
    public static final int PULL_FREEZE_RADIUS = 10;
    public static final int FREEZE_TICKS = 100;
    public static final int ORE_SIGHT_TICKS = 300;
    public static final int PLAYER_DISCOVERY_EXPERIENCE = 150;
    public static final int STRUCTURE_DISCOVERY_EXPERIENCE = 300;
    public static final int COMBAT_XP_RADIUS = 32;
    public static final int CANCEL_COOLDOWN_TICKS = 20;
    public static final int CHARGE_MIN_TICKS = 6;
    public static final int MOMENTUM_WINDOW_TICKS = 60;
    public static final int MOMENTUM_MAX_STACKS = 5;
    public static final int STASIS_DAMAGE_INTERVAL_TICKS = 10;
    public static final float STASIS_DAMAGE_FRACTION = 0.04F;

    private SkillManager() {}

    public static PlayerSkillData data(ServerPlayer player) {
        return player.getData(SkillAttachments.SKILL_DATA.get());
    }

    public static boolean isEnabled(MinecraftServer server) {
        return SkillWorldSavedData.isEnabled(server);
    }

    public static boolean isEnabled(ServerLevel level) {
        return SkillWorldSavedData.isEnabled(level);
    }

    /** 管理员切换全存档技能系统总开关（关闭即刻休眠并清理运行时效果，保留玩家进度）。 */
    public static void setSystemEnabled(MinecraftServer server, boolean enabled) {
        if (server == null) return;
        SkillWorldSavedData.setEnabled(server, enabled);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!enabled) {
                SkillActiveExecutor.clearTransient(player);
                SkillPassiveHandler.clearAllPassives(player);
                SkillResonanceHandler.clearTransient(player.getUUID());
            } else {
                SkillPassiveHandler.applyPassiveEffects(player);
            }
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        SkillCombatTracker.clearDirty(player.getUUID());
        boolean enabled = isEnabled(player.serverLevel());
        PacketDistributor.sendToPlayer(player,
                new SyncSkillDataPayload(data(player).serializeForSync(System.currentTimeMillis(), enabled)));
    }

    // ===== 仪式感通知推送 =====

    public static void notifyXpGain(ServerPlayer player, int amount) {
        PacketDistributor.sendToPlayer(player,
                new SkillNotificationPayload(SkillNotificationPayload.Kind.XP_GAIN, "", amount));
    }

    public static void notifyLevelUp(ServerPlayer player, int level) {
        PacketDistributor.sendToPlayer(player,
                new SkillNotificationPayload(SkillNotificationPayload.Kind.LEVEL_UP, "", level));
    }

    public static void notifyStarRankUp(ServerPlayer player, StarRank rank, int level) {
        PacketDistributor.sendToPlayer(player,
                new SkillNotificationPayload(SkillNotificationPayload.Kind.STAR_RANK_UP, rank.id(), level));
    }

    public static void notifySkillUnlocked(ServerPlayer player, SkillDefinition skill) {
        PacketDistributor.sendToPlayer(player,
                new SkillNotificationPayload(SkillNotificationPayload.Kind.SKILL_UNLOCKED, skill.id(), 0));
    }

    public static void notifySkillAwakened(ServerPlayer player, SkillDefinition skill) {
        PacketDistributor.sendToPlayer(player,
                new SkillNotificationPayload(SkillNotificationPayload.Kind.SKILL_AWAKENED, skill.id(), 3));
    }

    public static void handleAction(ServerPlayer player, SkillActionPayload.Action action,
                                    String skillId, List<String> loadout) {
        if (!isEnabled(player.serverLevel())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.disabled"), true);
            sync(player);
            return;
        }
        PlayerSkillData data = data(player);
        long gameTime = player.serverLevel().getGameTime();
        if (action != SkillActionPayload.Action.SELECT_ACTIVE && data.inCombat(gameTime)) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.in_combat"), true);
            return;
        }
        if (action != SkillActionPayload.Action.SELECT_ACTIVE && SkillActiveExecutor.isCharging(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.executing"), true);
            return;
        }

        switch (action) {
            case LEARN -> {
                if (data.learnOrUpgrade(skillId)) {
                    applyPassiveEffects(player);
                    if (data.isAwakened(skillId)) {
                        ModSkills.find(skillId).ifPresent(def -> notifySkillAwakened(player, def));
                    }
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.learned"), true);
                    sync(player);
                } else {
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.learn_failed"), true);
                }
            }
            case SELECT_ACTIVE -> {
                if (data.setSelectedActive(parseIndex(skillId))) {
                    cancelCharge(player);
                    sendActionFeedback(player, "message.universal_tool.skills.active_selected",
                            data.selectedDefinition().map(SkillDefinition::translationKey)
                                    .map(Component::translatable).orElse(Component.empty()));
                }
            }
            case SET_ACTIVE -> {
                if (loadout.isEmpty() ? data.addToLoadout(true, skillId) : data.setLoadout(true, loadout)) {
                    applyPassiveEffects(player);
                    sync(player);
                } else {
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);
                }
            }
            case SET_PASSIVE -> {
                if (loadout.isEmpty() ? data.addToLoadout(false, skillId) : data.setLoadout(false, loadout)) {
                    applyPassiveEffects(player);
                    sync(player);
                } else {
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);
                }
            }
            case UNSET_ACTIVE, UNSET_PASSIVE -> {
                boolean active = action == SkillActionPayload.Action.UNSET_ACTIVE;
                int index = parseIndex(skillId);
                if (index >= 0 && data.removeFromLoadout(active, index)) {
                    if (active) {
                        SkillActiveExecutor.clearOnUnsetActive(player);
                    }
                    applyPassiveEffects(player);
                    sync(player);
                } else {
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.loadout_failed"), true);
                }
            }
            case SWITCH_PRESET -> {
                int idx = parseIndex(skillId);
                if (data.switchPreset(idx)) {
                    SkillActiveExecutor.clearOnUnsetActive(player);
                    applyPassiveEffects(player);
                    player.displayClientMessage(Component.translatable("message.universal_tool.skills.preset_switched",
                            data.preset(idx).name()), true);
                    sync(player);
                }
            }
            case RENAME_PRESET -> {
                int idx = parseIndex(skillId);
                String newName = loadout.isEmpty() ? "" : loadout.get(0);
                if (data.renamePreset(idx, newName)) {
                    sync(player);
                }
            }
            case RESET -> {
                boolean safeGround = player.onGround() || player.isCreative()
                        || player.fallDistance < 1.5F || player.getAbilities().flying;
                if (!safeGround) {
                    sendActionFeedback(player, "message.universal_tool.skills.reset_unsafe");
                    return;
                }
                if (skillId != null && !skillId.isEmpty()) {
                    PlayerSkillData.ResetResult res = data.resetSkillCascade(skillId);
                    if (res.refundedPoints() > 0) {
                        for (String cleared : res.clearedIds()) {
                            SkillActiveExecutor.clearSingleSkill(player, cleared);
                        }
                        applyPassiveEffects(player);
                        sendActionFeedback(player, "message.universal_tool.skills.skill_reset_done",
                                Component.translatable("skill.universal_tool." + skillId), res.refundedPoints());
                    } else {
                        sendActionFeedback(player, "message.universal_tool.skills.skill_not_learned");
                    }
                } else {
                    SkillPassiveHandler.clearAllPassives(player);
                    SkillActiveExecutor.clearAllForPlayer(player);
                    data.resetAllocations();
                    sendActionFeedback(player, "message.universal_tool.skills.reset_done");
                }
            }
        }
    }

    private static void sendActionFeedback(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
        sync(player);
    }

    private static int parseIndex(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isFrozen(Entity entity) {
        return SkillActiveExecutor.isFrozen(entity);
    }

    public static void handleCast(ServerPlayer player, boolean releasing) {
        SkillActiveExecutor.handleCast(player, releasing);
    }

    public static void castSelected(ServerPlayer player) {
        SkillActiveExecutor.castSelected(player);
    }

    public static void cancelCharge(ServerPlayer player) {
        SkillActiveExecutor.cancelCharge(player);
    }

    public static double gravityWellRadius(int level) { return SkillActiveExecutor.gravityWellRadius(level); }
    public static int gravityWellDurationSeconds(int level) { return SkillActiveExecutor.gravityWellDurationSeconds(level); }
    public static int gravityWellDurationTicks(int level) { return SkillActiveExecutor.gravityWellDurationTicks(level); }
    public static int freezeDurationSeconds(int level) { return SkillActiveExecutor.freezeDurationSeconds(level); }
    public static int freezeDurationTicks(int level) { return SkillActiveExecutor.freezeDurationTicks(level); }
    public static double oppressiveRadius(int level) { return SkillActiveExecutor.oppressiveRadius(level); }
    public static int oppressiveDurationTicks(int level) { return SkillActiveExecutor.oppressiveDurationTicks(level); }
    public static int airStepDurationTicks(int level) { return SkillActiveExecutor.airStepDurationTicks(level); }

    public static boolean canPull(ServerPlayer source, LivingEntity target) {
        return SkillActiveExecutor.canPull(source, target);
    }

    public static boolean canAffect(ServerPlayer source, LivingEntity target) {
        return SkillActiveExecutor.canAffect(source, target);
    }

    public static double bulwarkArmor(int level) { return SkillPassiveHandler.bulwarkArmor(level); }
    public static double bulwarkToughness(int level) { return SkillPassiveHandler.bulwarkToughness(level); }
    public static float damageClampRatio(int level) { return SkillPassiveHandler.damageClampRatio(level); }

    static void applyPassiveEffects(ServerPlayer player) {
        SkillPassiveHandler.applyPassiveEffects(player);
    }

    public static void deflectProjectile(ServerPlayer player, Projectile projectile) {
        SkillActiveExecutor.deflectProjectile(player, projectile);
    }

    public static void clearRuntimeEffectsForPlayer(ServerPlayer player) {
        SkillPassiveHandler.clearAllPassives(player);
        SkillActiveExecutor.clearAllForPlayer(player);
        SkillResonanceHandler.clearTransient(player.getUUID());
        SkillCombatTracker.clearDirty(player.getUUID());
    }

    private static void clearTransient(ServerPlayer player) {
        SkillActiveExecutor.clearTransient(player);
        SkillPassiveHandler.clearMomentum(player.getUUID());
        SkillResonanceHandler.clearTransient(player.getUUID());
        SkillCombatTracker.clearDirty(player.getUUID());
    }

    // ===== NeoForge 事件订阅 =====

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void clampFinalDamage(LivingDamageEvent.Pre event) {
        SkillPassiveHandler.clampFinalDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillPassiveHandler.tryPhoenixEmberRevive(player, event);
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnabled(player.serverLevel())) {
            SkillPassiveHandler.onLivingHeal(player, event);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearTransient(player);
            data(player).clearCombat();
            if (isEnabled(player.serverLevel())) {
                applyPassiveEffects(player);
            } else {
                SkillPassiveHandler.clearAllPassives(player);
            }
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearTransient(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearTransient(player);
            data(player).clearCombat();
            player.getPersistentData().remove("universal_tool_combat_game_time");
            if (isEnabled(player.serverLevel())) {
                applyPassiveEffects(player);
            } else {
                SkillPassiveHandler.clearAllPassives(player);
            }
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearTransient(player);
        data(player).clearCombat();
        if (isEnabled(player.serverLevel())) {
            SkillCombatTracker.onChangedDimension(player, event.getTo().location().toString());
            applyPassiveEffects(player);
        } else {
            SkillPassiveHandler.clearAllPassives(player);
        }
        sync(player);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim && isEnabled(victim.serverLevel())) {
            SkillCombatTracker.onVictimDamaged(victim, event.getNewDamage(),
                    event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL));
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!isEnabled(player.serverLevel())) return;

        boolean isSkillDmg = event.getSource().getDirectEntity() instanceof LightningSpearProjectile
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
        boolean isProjectileDmg = event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
        SkillCombatTracker.onLivingDamage(player, target, event.getNewDamage(), isSkillDmg, isProjectileDmg);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && isEnabled(victim.serverLevel())) {
            SkillCombatTracker.onIncomingHit(victim, event.getSource().getDirectEntity(),
                    victim.serverLevel().getGameTime());
            if (!SkillActiveExecutor.applyGuard(victim, event)) {
                SkillPassiveHandler.applyIncomingVictimPassives(victim, event);
            }
        }

        Entity damageOwner = event.getSource().getEntity();
        if (damageOwner == null) damageOwner = event.getSource().getDirectEntity();
        if (damageOwner instanceof ServerPlayer attacker && isEnabled(attacker.serverLevel())) {
            SkillPassiveHandler.applyIncomingAttackerPassives(attacker, event);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        SkillActiveExecutor.onProjectileImpact(event);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (isEnabled(event.getServer())) {
            SkillActiveExecutor.tickServer(event.getServer());
            SkillPassiveHandler.tickServer(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isEnabled(player.serverLevel())) {
            SkillPassiveHandler.clearAllPassives(player);
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        SkillActiveExecutor.tickPlayerActives(player, gameTime);
        SkillPassiveHandler.tickPassives(player, gameTime);
        SkillCombatTracker.tickPlayer(player);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isEnabled(player.serverLevel())) {
            SkillCombatTracker.onBlockBreak(player, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnabled(player.serverLevel())) {
            SkillCombatTracker.onBlockPlaced(player, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        SkillModSynergyHandler.onBlockDrops(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        SkillModSynergyHandler.onLivingDrops(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        SkillModSynergyHandler.onLivingExperienceDrop(event);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SkillCombatTracker.clearAll();
        SkillActiveExecutor.clearAll();
        SkillPassiveHandler.clearAll();
        SkillResonanceHandler.clearAll();
    }
}
