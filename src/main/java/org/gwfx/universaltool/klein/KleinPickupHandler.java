package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.gwfx.universaltool.UniversalToolMod;

/** Routes matching normal player pickups into the server-authoritative warehouse. */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class KleinPickupHandler {
    @SubscribeEvent public static void beforePickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (tryDeposit(player, event.getItemEntity())) {
            if (event.getItemEntity().isRemoved() || event.getItemEntity().getItem().isEmpty()) {
                event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        }
    }

    /** Returns true when at least one item was accepted by the warehouse. */
    public static boolean tryDeposit(ServerPlayer player, ItemEntity entity) {
        if (entity.hasPickUpDelay()) return false;
        if (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) return false;

        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        int conduitLv = org.gwfx.universaltool.skill.SkillModSynergyHandler.hyperConduitLevel(player);
        boolean allowAuto = saved.autoDeposit(player.getUUID()) || conduitLv >= 2;
        if (!allowAuto || KleinOpenPayload.findBottle(player.getInventory()) < 0) return false;

        ItemStack stack = entity.getItem().copy();
        if (!KleinWarehouse.canStore(stack)) return false;
        if (conduitLv < 2 && !saved.warehouse(player.getUUID()).containsType(stack)) return false;
        ItemStack extracted = stack.copy();
        BigInteger acceptedAmount = saved.warehouse(player.getUUID()).insert(
                extracted, BigInteger.valueOf(extracted.getCount()));
        int accepted = acceptedAmount.intValue();
        if (accepted <= 0) return false;
        extracted.shrink(accepted);
        if (extracted.isEmpty()) {
            entity.discard();
        } else {
            entity.getItem().setCount(extracted.getCount());
        }
        player.take(entity, accepted);
        saved.changed();
        saved.syncViewers(player.getUUID());
        org.gwfx.universaltool.skill.SkillModSynergyHandler.onKleinBottlePickup(player);
        return true;
    }

    private KleinPickupHandler() {}
}
