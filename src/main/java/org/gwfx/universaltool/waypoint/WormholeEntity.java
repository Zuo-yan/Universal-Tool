package org.gwfx.universaltool.waypoint;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 空间虫洞实体：由时空法典/传送卷轴在玩家前方打开，玩家走入其中即可折跃至目标锚点。
 */
public class WormholeEntity extends Entity {
    public static final int OPEN_ANIM_TICKS = 10;
    public static final int MAX_LIFETIME_TICKS = 300; // 15 秒
    private static final EntityDataAccessor<Boolean> DATA_CLOSING =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private Waypoint target;
    @Nullable
    private UUID ownerUuid;
    private boolean fromScroll;
    private int closingTicks = 0;

    public WormholeEntity(EntityType<? extends WormholeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void configure(ServerPlayer owner, Waypoint target, float facingYaw, boolean fromScroll) {
        this.ownerUuid = owner.getUUID();
        this.target = target;
        this.fromScroll = fromScroll;
        this.setYRot(facingYaw);
        this.yRotO = facingYaw;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isClosing() {
        return entityData.get(DATA_CLOSING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CLOSING, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnAmbientParticles();
            return;
        }

        if (isClosing()) {
            closingTicks++;
            if (closingTicks >= 8) {
                discard();
            }
            return;
        }

        if (tickCount >= MAX_LIFETIME_TICKS || target == null) {
            beginClosing();
            return;
        }

        if (tickCount >= 6) {
            List<ServerPlayer> players = level().getEntitiesOfClass(
                    ServerPlayer.class,
                    getBoundingBox().inflate(0.22D)
            );
            for (ServerPlayer entered : players) {
                if (!entered.isSpectator()) {
                    TeleportManager.executeTeleport(entered, target, fromScroll);
                    beginClosing();
                    break;
                }
            }
        }
    }

    public void beginClosing() {
        if (!isClosing()) {
            entityData.set(DATA_CLOSING, true);
            closingTicks = 0;
        }
    }

    private void spawnAmbientParticles() {
        if (tickCount % 2 != 0) {
            return;
        }
        float rad = getYRot() * Mth.DEG_TO_RAD;
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double r = 0.45D + random.nextDouble() * 0.25D;
        double px = getX() + rightX * Math.cos(angle) * r;
        double py = getY() + 1.12D + Math.sin(angle) * r * 1.45D;
        double pz = getZ() + rightZ * Math.cos(angle) * r;
        level().addParticle(
                random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                px, py, pz,
                (getX() - px) * 0.05D, (getY() + 1.12D - py) * 0.05D, (getZ() - pz) * 0.05D
        );
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        fromScroll = tag.getBoolean("FromScroll");
        if (tag.contains("Target")) {
            target = Waypoint.CODEC.parse(NbtOps.INSTANCE, tag.get("Target")).result().orElse(null);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putBoolean("FromScroll", fromScroll);
        if (target != null) {
            Waypoint.CODEC.encodeStart(NbtOps.INSTANCE, target).result()
                    .ifPresent(encoded -> tag.put("Target", encoded));
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }
}
