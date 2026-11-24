package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侵晨战技 VFX 专属渲染层：
 * <ul>
 *   <li>{@link #additiveGlow(ResourceLocation)}：采用 {@code LIGHTNING_TRANSPARENCY}（SRC_ALPHA, ONE）
 *       实现真正叠加发光效果，关闭背面剔除与深度写入但保留深度测试，贴合高速 UV 流动与多层次能量叠亮。</li>
 *   <li>{@link #debrisSolid(ResourceLocation)}：不透明实体切除层，用于渲染飞溅玄武岩碎石与发光波形成强烈明暗反差。</li>
 * </ul>
 */
public final class PhainonVfxRenderTypes extends RenderType {

    private static final Map<ResourceLocation, RenderType> ADDITIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> SOLID_TRIANGLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_TRIANGLE_CACHE = new ConcurrentHashMap<>();

    private PhainonVfxRenderTypes(String name,
                                  VertexFormat format,
                                  VertexFormat.Mode mode,
                                  int bufferSize,
                                  boolean affectsCrumbling,
                                  boolean sortOnUpload,
                                  Runnable setupState,
                                  Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * 获取指定贴图对应的 Additive 叠加发光三角面渲染层（按贴图缓存）。
     */
    public static RenderType additiveGlow(ResourceLocation texture) {
        return ADDITIVE_CACHE.computeIfAbsent(texture, tex -> RenderType.create(
                "phainon_vfx_additive_" + tex.getPath(),
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, true, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        ));
    }

    /**
     * 获取实心飞溅碎石渲染层。
     */
    public static RenderType debrisSolid(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    static RenderType solidTriangles(ResourceLocation texture) {
        return SOLID_TRIANGLE_CACHE.computeIfAbsent(texture, tex -> RenderType.create(
                "phainon_solid_triangles_" + tex.getPath(), DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES, 1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));
    }

    /**
     * 获取普通 Alpha 半透明三角面渲染层（关闭深度写入与背面剔除，用于暗化苍穹穹顶与黑曜石右翼实体剪影）。
     */
    static RenderType translucentTriangles(ResourceLocation texture) {
        return TRANSLUCENT_TRIANGLE_CACHE.computeIfAbsent(texture, tex -> RenderType.create(
                "phainon_translucent_triangles_" + tex.getPath(),
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));
    }
}
