package org.gwfx.universaltool.skill;

import net.minecraft.core.particles.ParticleTypes;
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
 * 3D 雷霆之矛投射物：沿视线飞出，造成带施法者归属的魔法伤害与视觉落雷；Lv.3 觉醒支持连锁雷电溅射。
 */
public final class LightningSpearProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final int MAX_LIFETIME = 60;
    private float chargeRatio = 1.0F;
    private float damage = 1.0F;
    private int skillLevel = 1;

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

    public void setSkillLevel(int skillLevel) {
        this.skillLevel = Math.max(1, skillLevel);
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
                spawnVisualBolt(serverLevel, target.position(), owner);
                // Lv.3 星辉觉醒：向周围 6 格最多 3 个敌人释放连锁雷电溅射
                if (skillLevel >= 3 && owner instanceof ServerPlayer sp) {
                    int chains = 0;
                    for (LivingEntity extra : serverLevel.getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(6.0),
                            e -> e != target && SkillManager.canAffect(sp, e))) {
                        extra.hurt(damageSources().indirectMagic(this, sp), damage * 0.45F);
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                extra.getX(), extra.getY() + 0.8, extra.getZ(), 12, 0.25, 0.35, 0.25, 0.08);
                        if (++chains >= 3) break;
                    }
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
                LivingEntity owner = getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
                spawnVisualBolt(serverLevel, hit.getLocation(), owner);
            }
            discard();
        }
    }

    private static void spawnVisualBolt(ServerLevel serverLevel, net.minecraft.world.phys.Vec3 pos, LivingEntity owner) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt != null) {
            bolt.moveTo(pos);
            bolt.setVisualOnly(true);
            if (owner instanceof ServerPlayer sp) {
                bolt.setCause(sp);
            }
            serverLevel.addFreshEntity(bolt);
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
