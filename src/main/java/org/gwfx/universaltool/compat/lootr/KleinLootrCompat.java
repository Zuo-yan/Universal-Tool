package org.gwfx.universaltool.compat.lootr;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.api.data.inventory.ILootrInventory;
import org.jetbrains.annotations.Nullable;

public final class KleinLootrCompat {
    public static boolean isLootrContainer(@Nullable BlockEntity entity) {
        return entity != null && LootrAPI.resolveBlockEntity(entity) != null;
    }

    @Nullable
    public static IItemHandler findPersonalStorage(BlockEntity entity, ServerPlayer player) {
        ILootrBlockEntity provider = LootrAPI.resolveBlockEntity(entity);
        if (provider == null || !provider.canPlayerOpen(player) || LootrAPI.isDecayed(provider)) return null;
        // The UUID lookup only reads existing personal loot. Lootr's player overload can resolve to a team.
        ILootrInventory inventory = LootrAPI.getData(provider).getInventory(player.getUUID());
        return inventory != null && inventory.stillValid(player) ? new InvWrapper(inventory) : null;
    }

    private KleinLootrCompat() {}
}
