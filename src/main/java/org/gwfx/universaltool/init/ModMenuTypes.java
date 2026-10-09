package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.menu.PolymerizerMenu;
import org.gwfx.universaltool.phoenix.PhoenixPodMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, UniversalToolMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PolymerizerMenu>> UNIVERSAL_POLYMERIZER_MENU =
            MENU_TYPES.register("universal_polymerizer_menu",
                    () -> IMenuTypeExtension.create(PolymerizerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PhoenixPodMenu>> PHOENIX_POD_MENU =
            MENU_TYPES.register("phoenix_pod_menu",
                    () -> IMenuTypeExtension.create(PhoenixPodMenu::new));
}
