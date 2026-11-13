package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 个人空间星门方块实体：管理星门展开/收拢动画进度、开启状态与走入传送检测。
 */
public class PersonalSpaceGateBlockEntity extends BlockEntity {
    public static final int MODE_CLOSED = 0;
    public static final int MODE_REALM = 1;
    public static final int MODE_PERSONAL = 2;
    public static final int TOTAL_DEPLOY_TICKS = 36;

    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private int portalMode = MODE_CLOSED;
    private String targetEnvironmentId = "";
    private int entryCooldownTicks = 0;

    private int clientAnimTicks = 0;
    private int clientPrevAnimTicks = 0;

    public PersonalSpaceGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(), pos, state);
        if (state.hasProperty(PersonalSpaceGateBlock.OPEN) && state.getValue(PersonalSpaceGateBlock.OPEN)) {
            this.clientAnimTicks = TOTAL_DEPLOY_TICKS;
            this.clientPrevAnimTicks = TOTAL_DEPLOY_TICKS;
        }
    }

    public void bindTo(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.ownerName = player.getName().getString();
        setChanged();
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getPortalMode() {
        return portalMode;
    }

    public String getTargetEnvironmentId() {
        return targetEnvironmentId;
    }

    public String getPortalEnvironment() {
        return targetEnvironmentId;
    }

    public float getDeployProgress(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, clientPrevAnimTicks, clientAnimTicks) / (float) TOTAL_DEPLOY_TICKS, 0.0F, 1.0F);
    }

    public int getClientAnimTicks() {
        return clientAnimTicks;
    }

    public void openPortal(int mode, String environmentId, long gameTime) {
        openPortal(mode, environmentId);
    }

    public void openPortal(int mode, String environmentId) {
        this.portalMode = mode;
        this.targetEnvironmentId = environmentId == null ? "" : environmentId;
        this.entryCooldownTicks = 24;
        setChanged();
        syncToClient();
    }

    public void closePortal() {
        this.portalMode = MODE_CLOSED;
        this.targetEnvironmentId = "";
        this.entryCooldownTicks = 0;
        setChanged();
        syncToClient();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PersonalSpaceGateBlockEntity gate) {
        gate.clientPrevAnimTicks = gate.clientAnimTicks;
        boolean open = state.hasProperty(PersonalSpaceGateBlock.OPEN) && state.getValue(PersonalSpaceGateBlock.OPEN);
        if (open) {
            if (gate.clientAnimTicks < TOTAL_DEPLOY_TICKS) {
                gate.clientAnimTicks++;
            }
        } else if (gate.clientAnimTicks > 0) {
            gate.clientAnimTicks = Math.max(0, gate.clientAnimTicks - 2);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PersonalSpaceGateBlockEntity gate) {
        if (!state.getValue(PersonalSpaceGateBlock.OPEN)) {
            return;
        }
        if (gate.entryCooldownTicks > 0) {
            gate.entryCooldownTicks--;
            return;
        }
        AABB portalTrigger = new AABB(
                pos.getX() + 0.05D, pos.getY(), pos.getZ() + 0.05D,
                pos.getX() + 0.95D, pos.getY() + 2.30D, pos.getZ() + 0.95D
        );
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, portalTrigger);
        if (!players.isEmpty()) {
            PrivateSpaceManager.enterOpenedGate(players.get(0), pos);
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUuid", ownerUuid);
        }
        tag.putString("OwnerName", ownerName);
        tag.putInt("PortalMode", portalMode);
        tag.putString("TargetEnv", targetEnvironmentId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUuid = tag.hasUUID("OwnerUuid") ? tag.getUUID("OwnerUuid") : null;
        ownerName = tag.getString("OwnerName");
        portalMode = tag.getInt("PortalMode");
        targetEnvironmentId = tag.getString("TargetEnv");
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
