package org.gwfx.universaltool.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.item.SpatialPageItem;
import org.gwfx.universaltool.item.TeleportCodexItem;
import org.gwfx.universaltool.item.TeleportScrollItem;
import org.gwfx.universaltool.item.UniversalToolItem;
import org.gwfx.universaltool.phoenix.DnaSampleItem;
import org.gwfx.universaltool.phoenix.DnaSyringeItem;
import org.gwfx.universaltool.phoenix.NutrientSolutionItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UniversalToolMod.MODID);

    // 基础万能工具
    public static final DeferredItem<UniversalToolItem> WOODEN_UNIVERSAL_TOOL = ITEMS.register("wooden_universal_tool",
            () -> new UniversalToolItem(Tiers.WOOD, UniversalToolItem.properties(Tiers.WOOD, 88, 6.0F, -3.1F)));

    public static final DeferredItem<UniversalToolItem> STONE_UNIVERSAL_TOOL = ITEMS.register("stone_universal_tool",
            () -> new UniversalToolItem(Tiers.STONE, UniversalToolItem.properties(Tiers.STONE, 196, 7.0F, -2.9F)));

    public static final DeferredItem<UniversalToolItem> IRON_UNIVERSAL_TOOL = ITEMS.register("iron_universal_tool",
            () -> new UniversalToolItem(Tiers.IRON, UniversalToolItem.properties(Tiers.IRON, 375, 8.0F, -2.7F)));

    public static final DeferredItem<UniversalToolItem> GOLDEN_UNIVERSAL_TOOL = ITEMS.register("golden_universal_tool",
            () -> new UniversalToolItem(Tiers.GOLD, UniversalToolItem.properties(Tiers.GOLD, 48, 9.0F, -0.8F)));

    public static final DeferredItem<UniversalToolItem> DIAMOND_UNIVERSAL_TOOL = ITEMS.register("diamond_universal_tool",
            () -> new UniversalToolItem(Tiers.DIAMOND, UniversalToolItem.properties(Tiers.DIAMOND, 2341, 10.0F, -2.3F)));

    public static final DeferredItem<UniversalToolItem> NETHERITE_UNIVERSAL_TOOL = ITEMS.register("netherite_universal_tool",
            () -> new UniversalToolItem(Tiers.NETHERITE, UniversalToolItem.properties(Tiers.NETHERITE, 3046, 11.0F, -2.1F).fireResistant()));

    // 空间传送道具系列
    public static final DeferredItem<TeleportCodexItem> TELEPORT_CODEX = ITEMS.register("teleport_codex",
            () -> new TeleportCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<SpatialPageItem> SPATIAL_PAGE = ITEMS.register("spatial_page",
            () -> new SpatialPageItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<TeleportScrollItem> TELEPORT_SCROLL = ITEMS.register("teleport_scroll",
            () -> new TeleportScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    // 万能聚合台方块物品
    public static final DeferredItem<BlockItem> UNIVERSAL_POLYMERIZER = ITEMS.register("universal_polymerizer",
            () -> new BlockItem(ModBlocks.UNIVERSAL_POLYMERIZER.get(), new Item.Properties()));

    // 凤凰计划 (Operation Phoenix)
    public static final DeferredItem<DnaSyringeItem> DNA_SYRINGE = ITEMS.register("dna_syringe",
            () -> new DnaSyringeItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<DnaSampleItem> DNA_SAMPLE = ITEMS.register("dna_sample",
            () -> new DnaSampleItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<NutrientSolutionItem> NUTRIENT_SOLUTION = ITEMS.register("nutrient_solution",
            () -> new NutrientSolutionItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> OPERATION_PHOENIX_POD = ITEMS.register("operation_phoenix_pod",
            () -> new BlockItem(ModBlocks.OPERATION_PHOENIX_POD.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<BlockItem> PERSONAL_SPACE_GATE = ITEMS.register("personal_space_gate",
            () -> new BlockItem(ModBlocks.PERSONAL_SPACE_GATE.get(), new Item.Properties().rarity(Rarity.RARE)));
}
