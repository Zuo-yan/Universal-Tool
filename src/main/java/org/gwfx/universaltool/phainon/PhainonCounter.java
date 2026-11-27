package org.gwfx.universaltool.phainon;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModSounds;

/**
 * 白厄觉醒潜行右键技能「灾厄·弑魂焚诏」服务端防反与反击结算。
 * <p>机制：
 * <ul>
 *     <li>架势期间（含起手至防反窗口结束，以及触发反击后的斩落蓄力阶段）获得 80% 免伤（受击伤害 × 0.2F）；</li>
 *     <li>累计记录架势期间承受的减免前原始伤害，触发反击斩落时按每 1 点受击原始伤害额外 +3 点反击伤害（上限 +360）显著提升；</li>
 *     <li>最终反击伤害同步乘以「侵晨」击杀成长倍率 {@link PhainonWeaponState#damageMultiplier()}。</li>
 * </ul>
 * </p>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonCounter {
    public static final int READY = 36, WINDOW = 110, IMPACT = 46, RECOVERY = 78;

    /** 架势期间受击伤害倍率（0.2F = 80% 免伤） */
    public static final float GUARD_DAMAGE_MULTIPLIER = 0.2F;
    /** 反击斩落基础伤害 */
    public static final float BASE_COUNTER_DAMAGE = 90.0F;
    /** 每承受 1 点减免前原始伤害转化的额外反击伤害 */
    public static final float ABSORBED_DAMAGE_RATIO = 3.0F;
    /** 受击增伤上限，防止异常超高伤导致数值溢出 */
    public static final float MAX_ABSORBED_BONUS_DAMAGE = 360.0F;

    private record Cast(InteractionHand hand, long start, @Nullable Vec3 point, float absorbedDamage) {
        Cast withAddedAbsorbed(float rawDamage) {
            float maxRaw = MAX_ABSORBED_BONUS_DAMAGE / ABSORBED_DAMAGE_RATIO;
            float next = Math.min(maxRaw, absorbedDamage + Math.max(0.0F, rawDamage));
            return new Cast(hand, start, point, next);
        }

        Cast triggerRiposte(long now, Vec3 ripostePoint, float totalAbsorbed) {
            float maxRaw = MAX_ABSORBED_BONUS_DAMAGE / ABSORBED_DAMAGE_RATIO;
            return new Cast(hand, now, ripostePoint, Math.min(maxRaw, Math.max(0.0F, totalAbsorbed)));
        }
    }

    private static final Map<UUID, Cast> active = new HashMap<>();

    private PhainonCounter() {}

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { active.clear(); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) clear(p);
    }

    static boolean active(ServerPlayer p) { return active.containsKey(p.getUUID()); }

    /**
     * 计算最终反击伤害 = (基础 90 + min(累计受击原始伤害 * 3, 360)) * 击杀成长倍率。
     */
    public static float computeCounterDamage(float absorbedRawDamage, PhainonWeaponState weaponState) {
        float bonus = Math.min(Math.max(0.0F, absorbedRawDamage) * ABSORBED_DAMAGE_RATIO, MAX_ABSORBED_BONUS_DAMAGE);
        return weaponState.scaleSkillDamage(BASE_COUNTER_DAMAGE + bonus);
    }

    /**
     * 判断玩家当前是否处于有效防反免伤窗口内（防止架势结束后残留免伤）。
     */
    private static boolean isDamageReductionActive(ServerPlayer p, @Nullable Cast cast) {
        if (cast == null || !p.isAlive() || !PhainonUltimateHandler.canUseAwakenedSkill(p, cast.hand)) {
            return false;
        }
        long age = p.level().getGameTime() - cast.start;
        if (age < 0) {
            return false;
        }
        return cast.point == null ? age < WINDOW : age <= IMPACT;
    }

    public static InteractionResultHolder<ItemStack> use(Player p, InteractionHand hand) {
        ItemStack sword = p.getItemInHand(hand);
        if (p instanceof ServerPlayer caster) {
            if (!PhainonUltimateHandler.canUseAwakenedSkill(caster, hand) || active(caster)
                    || p.getCooldowns().isOnCooldown(sword.getItem())) return InteractionResultHolder.fail(sword);
            active.put(p.getUUID(), new Cast(hand, p.level().getGameTime(), null, 0.0F));
            p.getCooldowns().addCooldown(sword.getItem(), WINDOW);
            PhainonSkillEffectPayload.broadcast(caster, PhainonSkillEffectPayload.Action.GUARD, hand, WINDOW);
            // 播放觉醒弹反起手语音：「烙印纷争之名！」
            caster.serverLevel().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    ModSounds.PHAINON_COUNTER_GUARD_VOICE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.consume(sword);
    }

    static void clear(ServerPlayer p) {
        Cast cast = active.remove(p.getUUID());
        if (cast != null) PhainonSkillEffectPayload.broadcast(p, PhainonSkillEffectPayload.Action.GUARD_END, cast.hand, 0);
    }

    /**
     * 架势期间提供 80% 免伤，并累计记录承受的减免前原始伤害量用于强化反击。
     */
    @SubscribeEvent
    public static void reduceGuardDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive()
                || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Cast cast = active.get(p.getUUID());
        if (!isDamageReductionActive(p, cast)) {
            return;
        }
        float currentDamage = e.getNewDamage();
        float rawDamage = Math.max(e.getOriginalDamage(), currentDamage);
        if (currentDamage <= 0.0F && rawDamage <= 0.0F) {
            return;
        }
        if (currentDamage > 0.0F) {
            e.setNewDamage(currentDamage * GUARD_DAMAGE_MULTIPLIER);
        }
        long age = p.level().getGameTime() - cast.start;
        if (cast.point == null || age < IMPACT) {
            active.put(p.getUUID(), cast.withAddedAbsorbed(rawDamage));
        }
    }

    @SubscribeEvent
    public static void hit(LivingDamageEvent.Post e) {
        float incomingRaw = Math.max(e.getOriginalDamage(), e.getNewDamage());
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || incomingRaw <= 0.0F
                || !(e.getSource().getEntity() instanceof LivingEntity attacker)
                || !PhainonNormalSkillHandler.canHitTarget(p, attacker) || p.isAlliedTo(attacker)) return;
        Cast cast = active.get(p.getUUID());
        if (cast == null || cast.point != null || !PhainonUltimateHandler.canUseAwakenedSkill(p, cast.hand)) return;
        long now = p.level().getGameTime(), age = now - cast.start;
        if (age < READY || age >= WINDOW) return;
        Vec3 delta = attacker.position().subtract(p.position());
        Vec3 point = p.position().add(delta.length() > 18 ? delta.normalize().scale(18) : delta);
        // reduceGuardDamage 已在 Pre 阶段累加本次受击原始伤害；若未被 Pre 触发则兜底记录本次伤害
        float absorbed = cast.absorbedDamage > 0.0F ? cast.absorbedDamage : incomingRaw;
        active.put(p.getUUID(), cast.triggerRiposte(now, point, absorbed));
        p.getCooldowns().addCooldown(p.getItemInHand(cast.hand).getItem(), RECOVERY);
        PhainonSkillEffectPayload.counter(p, cast.hand, point);
        // 播放觉醒弹反反击语音：「铭记这道痛楚！」
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                ModSounds.PHAINON_COUNTER_STRIKE_VOICE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1, .72F);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Cast cast = active.get(p.getUUID());
        if (cast == null) return;
        long age = p.level().getGameTime() - cast.start;
        if (!PhainonUltimateHandler.canUseAwakenedSkill(p, cast.hand) || age >= (cast.point == null ? WINDOW : RECOVERY)) {
            clear(p);
            return;
        }
        if (cast.point == null) return;
        var level = p.serverLevel();
        Vec3 point = cast.point;
        if (age == IMPACT) {
            PhainonWeaponState weaponState = ModDataComponents.getPhainonWeaponState(p.getItemInHand(cast.hand));
            float counterDamage = computeCounterDamage(cast.absorbedDamage, weaponState);
            for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(4),
                    t -> PhainonNormalSkillHandler.canHitTarget(p, t) && !p.isAlliedTo(t))) {
                if (target.getBoundingBox().distanceToSqr(point) <= 16) {
                    target.hurt(p.damageSources().playerAttack(p), counterDamage);
                }
            }
            level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.3F, .75F);
        }
        if (age >= IMPACT && age < IMPACT + 24 && age % 2 == 0) {
            double r = .25 + (age - IMPACT) * .16;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + .06 * age;
                double x = point.x + r * Math.cos(a), z = point.z + r * Math.sin(a);
                level.sendParticles(ParticleTypes.FLAME, x, point.y + .18, z, 3, .12, .35, .12, .065);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()),
                        x, point.y + .5, z, 2, .17, .6, .17, .18);
            }
        }
    }
}
