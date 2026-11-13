package org.gwfx.universaltool.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * 监听下界合金万能工具的交互起始事件（左键挖掘、左键攻击），在动作开始的第一帧切换对应工具/武器形态。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class UniversalToolFormEvents {

    private UniversalToolFormEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!NetheriteToolForm.supportsDynamicMorph(stack)) {
            return;
        }
        NetheriteToolForm form = NetheriteToolForm.detectForBlock(player.level().getBlockState(event.getPos()));
        NetheriteToolForm.activate(stack, player.level(), form);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!NetheriteToolForm.supportsDynamicMorph(stack)) {
            return;
        }
        NetheriteToolForm.activate(stack, player.level(), NetheriteToolForm.SWORD);
    }
}
