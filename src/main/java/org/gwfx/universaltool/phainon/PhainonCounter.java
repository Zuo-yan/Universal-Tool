package org.gwfx.universaltool.phainon;

import java.util.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonCounter {
    public static final int READY = 36, WINDOW = 110, IMPACT = 46, RECOVERY = 78;
    private record Cast(InteractionHand hand, long start, Vec3 point) {}
    private static final Map<UUID, Cast> active = new HashMap<>();

    private PhainonCounter() {}
    @SubscribeEvent
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { active.clear(); }

    static boolean active(ServerPlayer p) { return active.containsKey(p.getUUID()); }

    public static InteractionResultHolder<ItemStack> use(Player p, InteractionHand hand) {
        ItemStack sword = p.getItemInHand(hand);
        if (p instanceof ServerPlayer caster) {
            if (!PhainonUltimateHandler.canUseAwakenedSkill(caster, hand) || active(caster)
                    || p.getCooldowns().isOnCooldown(sword.getItem())) return InteractionResultHolder.fail(sword);
            active.put(p.getUUID(), new Cast(hand, p.level().getGameTime(), null));
            p.getCooldowns().addCooldown(sword.getItem(), WINDOW);
            PhainonSkillEffectPayload.broadcast(caster, PhainonSkillEffectPayload.Action.GUARD, hand, WINDOW);
        }
        return InteractionResultHolder.consume(sword);
    }

    static void clear(ServerPlayer p) {
        Cast cast = active.remove(p.getUUID());
        if (cast != null) PhainonSkillEffectPayload.broadcast(p, PhainonSkillEffectPayload.Action.GUARD_END, cast.hand, 0);
    }

    @SubscribeEvent
    public static void hit(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || e.getNewDamage() <= 0
                || !(e.getSource().getEntity() instanceof LivingEntity attacker)
                || !PhainonNormalSkillHandler.canHitTarget(p, attacker) || p.isAlliedTo(attacker)) return;
        Cast cast = active.get(p.getUUID());
        if (cast == null || cast.point != null || !PhainonUltimateHandler.canUseAwakenedSkill(p, cast.hand)) return;
        long now = p.level().getGameTime(), age = now - cast.start;
        if (age < READY || age >= WINDOW) return;
        Vec3 delta = attacker.position().subtract(p.position());
        Vec3 point = p.position().add(delta.length() > 18 ? delta.normalize().scale(18) : delta);
        active.put(p.getUUID(), new Cast(cast.hand, now, point));
        p.getCooldowns().addCooldown(p.getItemInHand(cast.hand).getItem(), RECOVERY);
        PhainonSkillEffectPayload.counter(p, cast.hand, point);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,1,.72F);
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
            for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(4),
                    t -> PhainonNormalSkillHandler.canHitTarget(p,t) && !p.isAlliedTo(t))) {
                if (target.getBoundingBox().distanceToSqr(point) <= 16) target.hurt(p.damageSources().playerAttack(p), 90);
            }
            level.playSound(null,point.x,point.y,point.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,1.3F,.75F);
        }
        if (age >= IMPACT && age < IMPACT + 24 && age % 2 == 0) {
            double r = .25 + (age - IMPACT) * .16;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + .06 * age;
                double x = point.x + r * Math.cos(a), z = point.z + r * Math.sin(a);
                level.sendParticles(ParticleTypes.FLAME,x,point.y+.18,z,3,.12,.35,.12,.065);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.BLACKSTONE.defaultBlockState()),
                        x,point.y+.5,z,2,.17,.6,.17,.18);
            }
        }
    }
}
