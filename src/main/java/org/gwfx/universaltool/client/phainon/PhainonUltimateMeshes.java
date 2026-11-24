package org.gwfx.universaltool.client.phainon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.gwfx.universaltool.UniversalToolMod;

import javax.annotation.Nullable;

/**
 * 终结技「永劫燔世，其将背负」的 6 套 Blockbench 3D 特效模型与 7 张配套贴图缓存。
 * <p>随资源包热重载（F3+T）自动重新加载预烘焙三角网格。</p>
 */
public record PhainonUltimateMeshes(
        PhainonVfxModelData.BakedMesh seraphWing,
        PhainonVfxModelData.BakedMesh wingGold,
        PhainonVfxModelData.BakedMesh wingObsidian,
        PhainonVfxModelData.BakedMesh solarCocoon,
        PhainonVfxModelData.BakedMesh cocoonRings,
        PhainonVfxModelData.BakedMesh skyDome
) {
    static final ResourceLocation SERAPH_WING_TEX = asset("textures/misc/phainon_ult_seraph_wing.png");
    static final ResourceLocation WING_GOLD_TEX = asset("textures/misc/phainon_ult_wing_gold.png");
    static final ResourceLocation WING_OBSIDIAN_BODY_TEX = asset("textures/misc/phainon_ult_wing_obsidian_body.png");
    static final ResourceLocation WING_OBSIDIAN_GLOW_TEX = asset("textures/misc/phainon_ult_wing_obsidian_glow.png");
    static final ResourceLocation SOLAR_COCOON_TEX = asset("textures/misc/phainon_ult_solar_cocoon.png");
    static final ResourceLocation TITAN_RUNES_TEX = asset("textures/misc/phainon_ult_titan_runes.png");
    static final ResourceLocation SKY_VEIL_TEX = asset("textures/misc/phainon_ult_sky_veil.png");
    static final ResourceLocation SUNBURST_TEX = asset("textures/misc/phainon_sunburst_sigil.png");
    static final ResourceLocation SLASH_ARC_TEX = asset("textures/misc/phainon_slash_arc.png");

    private static final ResourceLocation SERAPH_WING_OBJ = asset("models/vfx/phainon_ult_seraph_wing.obj");
    private static final ResourceLocation WING_GOLD_OBJ = asset("models/vfx/phainon_ult_wing_gold.obj");
    private static final ResourceLocation WING_OBSIDIAN_OBJ = asset("models/vfx/phainon_ult_wing_obsidian.obj");
    private static final ResourceLocation SOLAR_COCOON_OBJ = asset("models/vfx/phainon_ult_solar_cocoon.obj");
    private static final ResourceLocation COCOON_RINGS_OBJ = asset("models/vfx/phainon_ult_cocoon_rings.obj");
    private static final ResourceLocation SKY_DOME_OBJ = asset("models/vfx/phainon_ult_sky_dome.obj");

    private static volatile PhainonUltimateMeshes current;

    /**
     * 重新加载并预烘焙全部终结技 3D OBJ 网格。
     */
    public static void reload(ResourceManager manager) {
        var seraph = PhainonVfxModelData.loadObjMesh(manager, SERAPH_WING_OBJ);
        var gold = PhainonVfxModelData.loadObjMesh(manager, WING_GOLD_OBJ);
        var obsidian = PhainonVfxModelData.loadObjMesh(manager, WING_OBSIDIAN_OBJ);
        var cocoon = PhainonVfxModelData.loadObjMesh(manager, SOLAR_COCOON_OBJ);
        var rings = PhainonVfxModelData.loadObjMesh(manager, COCOON_RINGS_OBJ);
        var dome = PhainonVfxModelData.loadObjMesh(manager, SKY_DOME_OBJ);
        current = (seraph == null || gold == null || obsidian == null
                || cocoon == null || rings == null || dome == null)
                ? null
                : new PhainonUltimateMeshes(seraph, gold, obsidian, cocoon, rings, dome);
    }

    @Nullable
    static PhainonUltimateMeshes current() {
        return current;
    }

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, path);
    }
}
