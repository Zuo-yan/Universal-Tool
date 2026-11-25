package org.gwfx.universaltool.phainon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Cast entry point and timing of the awakened meteor skill. */
public final class PhainonMeteor {
    public static final int RELEASE = 51;
    public static final int GIANT = 80;
    public static final int IMPACT = 121;
    public static final int DURATION = 152;
    private static final double RANGE = 96;

    private PhainonMeteor() {}

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack sword = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator() || player.getCooldowns().isOnCooldown(sword.getItem())) {
            return InteractionResultHolder.fail(sword);
        }
        if (player instanceof ServerPlayer caster) {
            if (!PhainonUltimateHandler.canUseAwakenedSkill(caster, hand)) return InteractionResultHolder.fail(sword);
            PhainonMeteorDamage.start(caster, hand, impactPoint(caster));
            player.getCooldowns().addCooldown(sword.getItem(), DURATION);
        }
        return InteractionResultHolder.consume(sword);
    }

    private static Vec3 impactPoint(ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        var block = caster.pick(RANGE, 1, false);
        Vec3 end = block.getLocation();
        var entity = ProjectileUtil.getEntityHitResult(caster, eye, end,
                caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(),
                eye.distanceToSqr(end));
        if (entity != null) return entity.getEntity().position();
        if (block.getType() != HitResult.Type.MISS) return end;

        Vec3 forward = PhainonEnhancedBasicMotion.forward(caster.getYRot());
        Vec3 ahead = caster.position().add(forward.scale(48));
        var floor = caster.level().clip(new ClipContext(ahead.add(0, 5, 0), ahead.add(0, -16, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return floor.getType() == HitResult.Type.MISS ? ahead : floor.getLocation();
    }
}
