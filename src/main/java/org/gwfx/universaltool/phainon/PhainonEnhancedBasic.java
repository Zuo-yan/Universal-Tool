package org.gwfx.universaltool.phainon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonEnhancedBasic {
    public static final int WINDUP_TICK = 3;
    public static final int SLASH_TICK = 6;
    public static final int EXTEND_TICK = 7;
    public static final int RETREAT_TICK = 9;
    public static final int POSE_TICKS = 16;
    public static final int SHARDS_TICK = 14;
    public static final int EFFECT_TICKS = 36;

    private PhainonEnhancedBasic() {}

    public static boolean isInForm(Player player) {
        var sword = player.getMainHandItem();
        return player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && sword.is(ModItems.QINCHEN_SWORD.get())
                && ModDataComponents.getPhainonWeaponState(sword).transformed();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer caster) {
            PhainonUltimateHandler.cueEnhancedBasic(caster, event.getTarget());
        }
    }
}
