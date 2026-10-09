package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.init.ModBlockEntities;

import javax.annotation.Nullable;
import java.util.UUID;

public class PersonalSpaceGateBlockEntity extends BlockEntity {
    private static final String OWNER_TAG = "OwnerUUID";

    @Nullable
    private UUID owner;

    public PersonalSpaceGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void bindTo(UUID owner) {
        if (!owner.equals(this.owner)) {
            this.owner = owner;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(OWNER_TAG, owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
    }
}
