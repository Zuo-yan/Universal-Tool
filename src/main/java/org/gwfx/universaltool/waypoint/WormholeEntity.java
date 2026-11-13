package org.gwfx.universaltool.waypoint;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Spatial wormhole aperture spawned ahead of the player for immersive teleportation.
 * Renders a parallax destination vista inside a swirling cosmic perimeter ring.
 * Stepping into an entrance wormhole opens the walkable starlit corridor; stepping into the exit completes the jump.
 */
public class WormholeEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_CLOSING =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_PHOTO_ID =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DEST_DIM =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_CORRIDOR_EXIT =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VISTA_ID =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.STRING);

    private static final int MAX_LIFETIME_TICKS = 240;
    private static final int CLOSE_ANIM_TICKS = 10;

    @Nullable
    private UUID ownerUuid;
    @Nullable
    private Waypoint target;
    private boolean fromScroll;
    private int lifeTicks;
    private int closeTicks;

    public WormholeEntity(EntityType<? extends WormholeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void configure(ServerPlayer owner, Waypoint target, float facingYaw, boolean fromScroll) {
        setYRot(facingYaw);
        configure(owner.getUUID(), target, fromScroll, false);
    }

    public void configure(UUID ownerUuid, Waypoint target, boolean fromScroll) {
        configure(ownerUuid, target, fromScroll, false);
    }

    public void configure(UUID ownerUuid, Waypoint target, boolean fromScroll, boolean corridorExit) {
        this.ownerUuid = ownerUuid;
        this.target = target;
        this.fromScroll = fromScroll;
        this.entityData.set(DATA_PHOTO_ID, target.photoId() != null ? target.photoId() : "");
        this.entityData.set(DATA_DEST_DIM, target.dimension().toString());
        this.entityData.set(DATA_CORRIDOR_EXIT, corridorExit);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CLOSING, false);
        builder.define(DATA_PHOTO_ID, "");
        builder.define(DATA_DEST_DIM, "minecraft:overworld");
        builder.define(DATA_CORRIDOR_EXIT, false);
        builder.define(DATA_VISTA_ID, "");
    }

    public boolean isClosing() {
        return this.entityData.get(DATA_CLOSING);
    }

    public String getPhotoId() {
        return this.entityData.get(DATA_PHOTO_ID);
    }

    public String getVistaId() {
        return this.entityData.get(DATA_VISTA_ID);
    }

    public void setVistaId(String vistaId) {
        this.entityData.set(DATA_VISTA_ID, vistaId != null ? vistaId : "");
    }

    public String getDestDimension() {
        return this.entityData.get(DATA_DEST_DIM);
    }

    public boolean isCorridorExit() {
        return this.entityData.get(DATA_CORRIDOR_EXIT);
    }

    public float getOpenProgress(float partialTick) {
        if (isClosing()) {
            return Math.max(0.0F, 1.0F - (closeTicks + partialTick) / CLOSE_ANIM_TICKS);
        }
        return Math.min(1.0F, (tickCount + partialTick) / 8.0F);
    }

    public void beginClosing() {
        if (!isClosing()) {
            this.entityData.set(DATA_CLOSING, true);
            this.closeTicks = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isClosing()) {
            closeTicks++;
            if (level().isClientSide) {
                spawnRimParticles();
            } else if (closeTicks >= CLOSE_ANIM_TICKS) {
                discard();
            }
            return;
        }

        lifeTicks++;
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                spawnRimParticles();
            }
            return;
        }

        if (lifeTicks == 1 && level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY() + 1.0D, getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.35F);
        }

        if (tickCount >= 6) {
            List<ServerPlayer> players = level().getEntitiesOfClass(
                    ServerPlayer.class,
                    getBoundingBox().inflate(0.55D),
                    p -> !p.isSpectator() && (!isCorridorExit() || ownerUuid == null || p.getUUID().equals(ownerUuid))
            );
            if (!players.isEmpty()) {
                ServerPlayer entered = players.get(0);
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            getX(), getY() + 1.4D, getZ(), 18, 0.45D, 0.75D, 0.45D, 0.06D);
                    serverLevel.playSound(null, getX(), getY() + 1.0D, getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.55F);
                }
                if (isCorridorExit()) {
                    WormholeCorridorManager.completeTransit(entered);
                } else if (target != null) {
                    WormholeCorridorManager.enterCorridor(entered, target, fromScroll, getVistaId());
                }
                discard();
                return;
            }
        }

        if (!isCorridorExit() && lifeTicks >= MAX_LIFETIME_TICKS) {
            beginClosing();
        }
    }

    private void spawnRimParticles() {
        double angle = (tickCount * 0.45D) % (Math.PI * 2.0D);
        double radYaw = Math.toRadians(getYRot());
        double rightX = Math.cos(radYaw);
        double rightZ = Math.sin(radYaw);
        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            double lx = Math.cos(a) * 0.72D;
            double ly = 1.05D + Math.sin(a) * 1.05D;
            level().addParticle(
                    ParticleTypes.PORTAL,
                    getX() + rightX * lx,
                    getY() + ly,
                    getZ() + rightZ * lx,
                    0.0D, 0.02D, 0.0D
            );
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        fromScroll = tag.getBoolean("FromScroll");
        lifeTicks = tag.getInt("LifeTicks");
        entityData.set(DATA_CORRIDOR_EXIT, tag.getBoolean("CorridorExit"));
        entityData.set(DATA_PHOTO_ID, tag.getString("PhotoId"));
        entityData.set(DATA_VISTA_ID, tag.getString("VistaId"));
        if (tag.contains("DestDim")) {
            entityData.set(DATA_DEST_DIM, tag.getString("DestDim"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putBoolean("FromScroll", fromScroll);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putBoolean("CorridorExit", isCorridorExit());
        tag.putString("PhotoId", getPhotoId());
        tag.putString("VistaId", getVistaId());
        tag.putString("DestDim", getDestDimension());
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
