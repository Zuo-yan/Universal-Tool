package org.gwfx.universaltool.waypoint;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.gwfx.universaltool.init.ModParticles;

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
    private static final EntityDataAccessor<Boolean> DATA_DEST_EXIT =
            SynchedEntityData.defineId(WormholeEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int MAX_LIFETIME_TICKS = 240;
    private static final int CLOSE_ANIM_TICKS = 10;

    @Nullable
    private UUID ownerUuid;
    @Nullable
    private Waypoint target;
    private boolean fromScroll;
    private int lifeTicks;
    private int closeTicks;
    private int scheduledCloseTick = -1;
    @Nullable
    private ResourceKey<Level> linkedDestDim;
    @Nullable
    private UUID linkedDestUuid;
    @Nullable
    private BlockPos linkedDestPos;

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
        this.entityData.set(DATA_DEST_EXIT, false);
    }

    public void configureDestinationExit(UUID ownerUuid, float facingYaw) {
        setYRot(facingYaw);
        this.ownerUuid = ownerUuid;
        this.target = null;
        this.fromScroll = false;
        this.scheduledCloseTick = -1;
        this.entityData.set(DATA_PHOTO_ID, "");
        this.entityData.set(DATA_CORRIDOR_EXIT, false);
        this.entityData.set(DATA_DEST_EXIT, true);
    }

    public void linkDestinationExit(ResourceKey<Level> destDim, UUID exitUuid, BlockPos exitPos) {
        this.linkedDestDim = destDim;
        this.linkedDestUuid = exitUuid;
        this.linkedDestPos = exitPos;
    }

    public void scheduleCloseAfter(int ticksFromNow) {
        this.scheduledCloseTick = this.lifeTicks + Math.max(1, ticksFromNow);
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
        builder.define(DATA_DEST_EXIT, false);
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

    public boolean isDestinationExit() {
        return this.entityData.get(DATA_DEST_EXIT);
    }

    public float getOpenProgress(float partialTick) {
        if (isClosing()) {
            return Math.max(0.0F, 1.0F - (closeTicks + partialTick) / CLOSE_ANIM_TICKS);
        }
        float openTicks = isDestinationExit() ? 5.0F : 8.0F;
        return Math.min(1.0F, (tickCount + partialTick) / openTicks);
    }

    public void beginClosing() {
        if (!isClosing()) {
            this.entityData.set(DATA_CLOSING, true);
            this.closeTicks = 0;
            if (!isCorridorExit() && !isDestinationExit()) {
                closeLinkedDestinationExit();
            }
        }
    }

    private void closeLinkedDestinationExit() {
        if (linkedDestDim == null || linkedDestUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel destLevel = serverLevel.getServer().getLevel(linkedDestDim);
        if (destLevel == null) {
            return;
        }
        if (linkedDestPos != null) {
            destLevel.getChunkAt(linkedDestPos);
        }
        Entity linked = destLevel.getEntity(linkedDestUuid);
        if (linked instanceof WormholeEntity destExit && destExit.isDestinationExit()) {
            destExit.beginClosing();
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
            if (isDestinationExit()) {
                serverLevel.playSound(null, getX(), getY() + 1.0D, getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.55F, 1.45F);
            } else {
                serverLevel.playSound(null, getX(), getY() + 1.0D, getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.35F);
            }
        }

        if (isDestinationExit()) {
            if (scheduledCloseTick >= 0) {
                if (lifeTicks >= scheduledCloseTick) {
                    beginClosing();
                }
            } else if (lifeTicks > MAX_LIFETIME_TICKS + 20 && !WormholeCorridorManager.hasActiveSession(ownerUuid)) {
                beginClosing();
            }
            return;
        }

        if (tickCount >= 3 && target != null) {
            List<ItemEntity> items = level().getEntitiesOfClass(
                    ItemEntity.class,
                    getBoundingBox().inflate(0.65D),
                    item -> item.isAlive() && !item.getItem().isEmpty()
            );
            for (ItemEntity item : items) {
                if (TeleportManager.teleportItem(item, target) && level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ModParticles.WORMHOLE_SPARK.get(),
                            getX(), getY() + 1.2D, getZ(), 12, 0.3D, 0.45D, 0.3D, 0.04D);
                    serverLevel.playSound(null, getX(), getY() + 1.0D, getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.7F);
                }
            }
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
                    serverLevel.sendParticles(ModParticles.WORMHOLE_SPARK.get(),
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
        float open = getOpenProgress(0.0F);
        if (open <= 0.05F) {
            return;
        }
        boolean corridorExit = isCorridorExit();
        boolean destExit = isDestinationExit();
        double rimRadius = (corridorExit ? 2.38D : (destExit ? 1.46D : 1.38D)) * open;
        double centerY = corridorExit ? 1.65D : 1.48D;

        double dir = destExit ? -0.52D : 0.45D;
        double angle = ((tickCount * dir) % (Math.PI * 2.0D));
        double radYaw = Math.toRadians(getYRot());
        double rightX = Math.cos(radYaw);
        double rightZ = Math.sin(radYaw);
        double normalX = -Math.sin(radYaw);
        double normalZ = Math.cos(radYaw);

        int count = destExit ? 3 : 2;
        var particle = destExit ? ModParticles.WORMHOLE_EXIT_SPARK.get() : ModParticles.WORMHOLE_SPARK.get();
        for (int i = 0; i < count; i++) {
            double a = angle + i * (Math.PI * 2.0D / count);
            double cosA = Math.cos(a);
            double sinA = Math.sin(a);
            double lx = cosA * rimRadius;
            double ly = centerY + sinA * rimRadius;
            double sideOffset = ((i & 1) == 0 ? 0.07D : -0.07D);

            // Orbit tangentially along the circular rim with outward flare (exit flares wider in reverse swirl)
            double tangent = destExit ? -0.036D : 0.032D;
            double outward = destExit ? 0.022D : 0.008D;
            double vx = rightX * (-sinA * tangent + cosA * outward);
            double vy = cosA * tangent + sinA * outward;
            double vz = rightZ * (-sinA * tangent + cosA * outward);

            level().addParticle(
                    particle,
                    getX() + rightX * lx + normalX * sideOffset,
                    getY() + ly,
                    getZ() + rightZ * lx + normalZ * sideOffset,
                    vx, vy, vz
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
        scheduledCloseTick = tag.contains("ScheduledCloseTick") ? tag.getInt("ScheduledCloseTick") : -1;
        entityData.set(DATA_CORRIDOR_EXIT, tag.getBoolean("CorridorExit"));
        entityData.set(DATA_DEST_EXIT, tag.getBoolean("DestExit"));
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
        tag.putInt("ScheduledCloseTick", scheduledCloseTick);
        tag.putBoolean("CorridorExit", isCorridorExit());
        tag.putBoolean("DestExit", isDestinationExit());
        tag.putString("PhotoId", getPhotoId());
        tag.putString("VistaId", getVistaId());
        tag.putString("DestDim", getDestDimension());
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
