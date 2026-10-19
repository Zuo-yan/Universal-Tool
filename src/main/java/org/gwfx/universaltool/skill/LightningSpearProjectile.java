package org.gwfx.universaltool.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;

/**
 * 3D spear projectile that flies towards its target, deals impact damage,
 * and calls down a real lightning bolt upon striking an entity or block.
 */
public final class LightningSpearProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final int MAX_LIFETIME = 60;
    private float chargeRatio = 1.0F;
    private float damage = 1.0F;

    public LightningSpearProjectile(EntityType<? extends LightningSpearProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public LightningSpearProjectile(Level world, LivingEntity owner, float damage) {
        super(ModEntities.LIGHTNING_SPEAR_PROJECTILE.get(), owner, world);
        this.damage = damage;
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    public void setChargeRatio(float ratio) {
        chargeRatio = Math.max(0.01F, Math.min(1.0F, ratio));
    }

    public float chargeRatio() {
        return chargeRatio;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= MAX_LIFETIME) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        if (getOwner() instanceof ServerPlayer sp && target instanceof LivingEntity living) {
            return SkillManager.canAffect(sp, living);
        }
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) {
            Entity target = hit.getEntity();
            LivingEntity owner = getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
            if (owner != null) {
                target.hurt(damageSources().indirectMagic(this, owner), damage);
            } else {
                target.hurt(damageSources().magic(), damage);
            }

            if (level() instanceof ServerLevel serverLevel) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(target.position());
                    if (owner instanceof ServerPlayer sp) {
                        bolt.setCause(sp);
                    }
                    serverLevel.addFreshEntity(bolt);
                }
            }
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel serverLevel) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(hit.getLocation());
                    if (getOwner() instanceof ServerPlayer sp) {
                        bolt.setCause(sp);
                    }
                    serverLevel.addFreshEntity(bolt);
                }
            }
            discard();
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.LIGHTNING_SPEAR.get());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }
}
