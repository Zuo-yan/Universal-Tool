package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.gwfx.universaltool.init.ModBlockEntities;

import javax.annotation.Nullable;
import java.util.UUID;

public class PersonalSpaceGateBlockEntity extends BlockEntity {
    private static final String OWNER_TAG = "OwnerUUID";
    private static final String PORTAL_MODE_TAG = "PortalMode";
    private static final String PORTAL_ENV_TAG = "PortalEnv";
    private static final String OPENED_TIME_TAG = "OpenedGameTime";

    @Nullable
    private UUID owner;
    private int portalMode = -1;
    private String portalEnvironment = "";
    private long openedGameTime;

    public PersonalSpaceGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public int getPortalMode() {
        return portalMode;
    }

    public String getPortalEnvironment() {
        return portalEnvironment;
    }

    public long getOpenedGameTime() {
        return openedGameTime;
    }

    public void bindTo(UUID owner) {
        if (!owner.equals(this.owner)) {
            this.owner = owner;
            markUpdated();
        }
    }

    public void openPortal(int mode, String environment, long gameTime) {
        this.portalMode = mode;
        this.portalEnvironment = environment == null ? "" : environment;
        this.openedGameTime = gameTime;
        markUpdated();
    }

    public void closePortal() {
        this.portalMode = -1;
        this.portalEnvironment = "";
        this.openedGameTime = 0L;
        markUpdated();
    }

    private void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PersonalSpaceGateBlockEntity gate) {
        if (!(level instanceof ServerLevel serverLevel) || !state.getValue(PersonalSpaceGateBlock.OPEN)) {
            return;
        }
        AABB portalTrigger = new AABB(
                pos.getX() + 0.10, pos.getY(), pos.getZ() + 0.10,
                pos.getX() + 0.90, pos.getY() + 1.45, pos.getZ() + 0.90
        );
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() && player.getBoundingBox().intersects(portalTrigger)) {
                PrivateSpaceManager.enterOpenedGate(player, pos);
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(OWNER_TAG, owner);
        }
        tag.putInt(PORTAL_MODE_TAG, portalMode);
        if (!portalEnvironment.isEmpty()) {
            tag.putString(PORTAL_ENV_TAG, portalEnvironment);
        }
        tag.putLong(OPENED_TIME_TAG, openedGameTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        portalMode = tag.contains(PORTAL_MODE_TAG) ? tag.getInt(PORTAL_MODE_TAG) : -1;
        portalEnvironment = tag.getString(PORTAL_ENV_TAG);
        openedGameTime = tag.getLong(OPENED_TIME_TAG);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
