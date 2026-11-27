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
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.network.Skill3dEffectPayload;
import org.gwfx.universaltool.network.Skill3dEffectPayload.EffectKind;

/**
 * 3D 雷霆之矛投射物：沿视线飞出并带电弧拖尾，造成带施法者归属的百分比强化魔法伤与 3D 雷爆；
 * Lv.3 觉醒支持最多 5 个敌人之间的真实 3D 折线连锁闪电链。
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
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 2, 0.06, 0.06, 0.06, 0.02);
                SkillVfx.point(sl, position(), SkillVfx.Tint.SPEAR.dust);
            }
            if (tickCount >= MAX_LIFETIME) {
                discard();
            }
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
            float maxPct = skillLevel == 1 ? 0.05F : (skillLevel == 2 ? 0.08F : 0.12F);
            float totalDmg = target instanceof LivingEntity livingTarget
                    ? SkillActiveExecutor.pctDamage(livingTarget, damage, maxPct, 0.0F, 0.0F)
                    : damage;

            if (owner != null) {
                target.hurt(damageSources().indirectMagic(this, owner), totalDmg);
            } else {
                target.hurt(damageSources().magic(), totalDmg);
            }

            if (level() instanceof ServerLevel serverLevel) {
                spawnVisualBolt(serverLevel, target.position(), owner);
                Vec3 impactPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                Skill3dEffectPayload.spawn(serverLevel, EffectKind.LIGHTNING_CHAIN,
                        impactPos, impactPos, 3.2F, 14, 0xA88BFF);

                // Lv.3 星辉觉醒：向周围 6 格最多 5 个敌人拉出 3D 折线闪电链
                if (skillLevel >= 3 && owner instanceof ServerPlayer sp) {
                    int chains = 0;
                    Vec3 prevNode = impactPos;
                    for (LivingEntity extra : serverLevel.getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(6.0),
                            e -> e != target && SkillManager.canAffect(sp, e))) {
                        float chainDmg = SkillActiveExecutor.pctDamage(extra, damage * 0.45F, 0.06F, 0.0F, 0.0F);
                        extra.hurt(damageSources().indirectMagic(this, sp), chainDmg);
                        Vec3 nextNode = extra.position().add(0, extra.getBbHeight() * 0.5, 0);
                        Skill3dEffectPayload.spawn(serverLevel, EffectKind.LIGHTNING_CHAIN,
                                prevNode, nextNode, 2.0F, 12, 0xC69CFF);
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                nextNode.x, nextNode.y, nextNode.z, 10, 0.25, 0.35, 0.25, 0.08);
                        prevNode = nextNode;
                        if (++chains >= 5) break;
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
                Skill3dEffectPayload.spawn(serverLevel, EffectKind.LIGHTNING_CHAIN,
                        hit.getLocation(), hit.getLocation(), 2.6F, 12, 0xA88BFF);
            }
            discard();
        }
    }

    private static void spawnVisualBolt(ServerLevel serverLevel, Vec3 pos, LivingEntity owner) {
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
