package org.gwfx.universaltool.phoenix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModItems;

import javax.annotation.Nullable;
import java.util.UUID;

public class PhoenixPodBlockEntity extends BlockEntity implements MenuProvider, Container {

    public static final int SLOT_DNA = 0;
    public static final int SLOT_NUTRIENT = 1;
    public static final int TOTAL_SLOTS = 2;
    public static final int MAX_PROGRESS = 1200; // 60 秒

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private int progress = 0;
    private PodState state = PodState.EMPTY;
    private UUID ownerUUID = null;
    private String ownerName = "";

    // 门平滑升降动画 (0.0F 完全关闭, 1.0F 完全升起)
    private float doorProgress = 0.0F;
    private int doorPhaseTimer = 0;
    private int doorPhase = 0; // 0:静止, 1:开启升起中(40 ticks), 2:保持开启(60 ticks), 3:下降关闭中(40 ticks)

    private ListTag snapshotTag = null;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
                case 2 -> state.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) progress = value;
            if (index == 2 && value >= 0 && value < PodState.values().length) {
                state = PodState.values()[value];
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public PhoenixPodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOENIX_POD_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, PhoenixPodBlockEntity be) {
        // 门升降状态机
        if (be.doorPhase > 0) {
            be.doorPhaseTimer++;
            if (be.doorPhase == 1) {
                // 开启阶段：40 ticks (2.0 秒) 缓缓升起
                be.doorProgress = Math.min(1.0F, be.doorPhaseTimer / 40.0F);
                if (level.isClientSide && be.doorPhaseTimer % 2 == 0) {
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            pos.getX() + 0.1 + level.random.nextDouble() * 0.8,
                            pos.getY() + 0.2 + level.random.nextDouble() * 1.6,
                            pos.getZ() + 0.1 + level.random.nextDouble() * 0.8,
                            (level.random.nextDouble() - 0.5) * 0.03, 0.06, (level.random.nextDouble() - 0.5) * 0.03);
                }
                if (be.doorPhaseTimer >= 40) {
                    be.doorPhase = 2; // 进入保持开启
                    be.doorPhaseTimer = 0;
                    be.doorProgress = 1.0F;
                }
            } else if (be.doorPhase == 2) {
                // 保持开启 60 ticks (3.0 秒)
                be.doorProgress = 1.0F;
                if (be.doorPhaseTimer >= 60) {
                    be.doorPhase = 3; // 进入降下关闭
                    be.doorPhaseTimer = 0;
                    if (level != null) {
                        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 0.8F);
                    }
                }
            } else if (be.doorPhase == 3) {
                // 下降关闭 40 ticks (2.0 秒)
                be.doorProgress = Math.max(0.0F, 1.0F - be.doorPhaseTimer / 40.0F);
                if (be.doorPhaseTimer >= 40) {
                    be.doorPhase = 0; // 完成关闭复位
                    be.doorPhaseTimer = 0;
                    be.doorProgress = 0.0F;
                }
            }
        }

        if (be.state == PodState.CULTIVATING) {
            if (!level.isClientSide) {
                be.progress++;
                if (be.progress >= MAX_PROGRESS) {
                    be.completeCultivation();
                } else if (be.progress % 40 == 0) {
                    level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.2F);
                }
                be.setChanged();
            } else {
                if (level.random.nextFloat() < 0.4F) {
                    level.addParticle(ParticleTypes.BUBBLE,
                            pos.getX() + 0.3 + level.random.nextDouble() * 0.4,
                            pos.getY() + 0.4 + level.random.nextDouble() * 1.2,
                            pos.getZ() + 0.3 + level.random.nextDouble() * 0.4,
                            0, 0.05, 0);
                }
            }
        } else if (be.state == PodState.READY && level.isClientSide) {
            if (level.random.nextFloat() < 0.15F) {
                level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP,
                        pos.getX() + 0.35 + level.random.nextDouble() * 0.3,
                        pos.getY() + 0.3,
                        pos.getZ() + 0.35 + level.random.nextDouble() * 0.3,
                        0, 0.04, 0);
            }
        }
    }

    public void triggerDoorSequence() {
        this.doorPhase = 1;
        this.doorPhaseTimer = 0;
        this.doorProgress = 0.0F;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.7F);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.8F);
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 1.1F);
        }
        sendUpdatePacket();
    }

    public float getDoorProgress() {
        return this.doorProgress;
    }

    public ListTag getSnapshotTag() {
        return this.snapshotTag;
    }

    public boolean startCultivating(Player player) {
        if (level == null || level.isClientSide || state != PodState.EMPTY) {
            return false;
        }

        ItemStack dnaStack = items.get(SLOT_DNA);
        ItemStack nutrientStack = items.get(SLOT_NUTRIENT);

        if (dnaStack.isEmpty() || nutrientStack.isEmpty()) {
            return false;
        }

        UUID dnaUUID = DnaSampleItem.getOwnerUUID(dnaStack);
        if (dnaUUID == null) {
            return false;
        }

        if (!isValidNutrient(nutrientStack)) {
            return false;
        }

        if (NutrientSolutionItem.hasSnapshot(nutrientStack)) {
            this.snapshotTag = NutrientSolutionItem.getSnapshotTag(nutrientStack);
        } else {
            this.snapshotTag = null;
        }

        this.ownerUUID = dnaUUID;
        this.ownerName = DnaSampleItem.getOwnerName(dnaStack);
        dnaStack.shrink(1);
        nutrientStack.shrink(1);

        this.state = PodState.CULTIVATING;
        this.progress = 0;
        this.doorProgress = 0.0F;
        this.doorPhase = 0;
        this.doorPhaseTimer = 0;
        updateBlockState(PodState.CULTIVATING);

        level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.5F);

        setChanged();
        sendUpdatePacket();
        return true;
    }

    private boolean isValidNutrient(ItemStack stack) {
        if (stack.is(ModItems.NUTRIENT_SOLUTION.get())) return true;
        return stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.GLISTERING_MELON_SLICE)
                || stack.is(Items.POTION);
    }

    private void completeCultivation() {
        this.state = PodState.READY;
        this.progress = MAX_PROGRESS;
        this.doorProgress = 0.0F;
        this.doorPhase = 0;
        this.doorPhaseTimer = 0;
        updateBlockState(PodState.READY);

        if (level instanceof ServerLevel serverLevel && ownerUUID != null) {
            PhoenixNetworkSavedData.get(serverLevel.getServer()).registerPod(serverLevel.dimension(), worldPosition, ownerUUID);
        }

        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.2F);

        setChanged();
        sendUpdatePacket();
    }

    public void consumeClone() {
        if (level == null) return;

        if (level instanceof ServerLevel serverLevel) {
            PhoenixNetworkSavedData.get(serverLevel.getServer()).unregisterPod(serverLevel.dimension(), worldPosition);
        }

        this.state = PodState.EMPTY;
        this.progress = 0;
        this.ownerUUID = null;
        this.ownerName = "";
        this.snapshotTag = null;
        updateBlockState(PodState.EMPTY);

        setChanged();
        sendUpdatePacket();
    }

    private void updateBlockState(PodState newState) {
        if (level != null && !level.isClientSide) {
            BlockState current = level.getBlockState(worldPosition);
            if (current.hasProperty(PhoenixPodBlock.STATE)) {
                level.setBlock(worldPosition, current.setValue(PhoenixPodBlock.STATE, newState), 3);
            }
            BlockPos above = worldPosition.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.hasProperty(PhoenixPodBlock.STATE)) {
                level.setBlock(above, aboveState.setValue(PhoenixPodBlock.STATE, newState), 3);
            }
        }
    }

    public void sendUpdatePacket() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isReady() {
        return this.state == PodState.READY && this.ownerUUID != null;
    }

    public PodState getPodState() {
        return this.state;
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public int getProgress() {
        return this.progress;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("Progress");
        doorProgress = tag.getFloat("DoorProgress");
        doorPhase = tag.getInt("DoorPhase");
        doorPhaseTimer = tag.getInt("DoorPhaseTimer");
        if (tag.contains("State")) {
            state = PodState.valueOf(tag.getString("State"));
        } else {
            state = PodState.EMPTY;
        }
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        } else {
            ownerUUID = null;
        }
        ownerName = tag.getString("OwnerName");

        if (tag.contains("SnapshotTag", Tag.TAG_LIST)) {
            snapshotTag = tag.getList("SnapshotTag", Tag.TAG_COMPOUND);
        } else {
            snapshotTag = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putFloat("DoorProgress", doorProgress);
        tag.putInt("DoorPhase", doorPhase);
        tag.putInt("DoorPhaseTimer", doorPhaseTimer);
        tag.putString("State", state.name());
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putString("OwnerName", ownerName);

        if (snapshotTag != null) {
            tag.put("SnapshotTag", snapshotTag);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.universal_tool.phoenix.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PhoenixPodMenu(containerId, playerInventory, this, this.dataAccess);
    }

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty() && items.get(1).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack res = ContainerHelper.removeItem(items, slot, amount);
        if (!res.isEmpty()) setChanged();
        return res;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
