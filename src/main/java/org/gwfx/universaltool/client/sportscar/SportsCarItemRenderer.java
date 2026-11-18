package org.gwfx.universaltool.client.sportscar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 海湾 GT 跑车 3D 物品渲染器，负责物品栏、地面掉落物、展示框及第一/第三人称手持立体渲染。
 */
public final class SportsCarItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static volatile SportsCarItemRenderer instance;

    public SportsCarItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    /**
     * 在客户端渲染调度器初始化完成后延迟获取共享物品渲染器实例。
     */
    public static SportsCarItemRenderer getInstance() {
        SportsCarItemRenderer cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (SportsCarItemRenderer.class) {
            if (instance == null) {
                Minecraft mc = Minecraft.getInstance();
                instance = new SportsCarItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
            }
            return instance;
        }
    }

    @Override
    public void renderByItem(ItemStack stack,
                             ItemDisplayContext displayContext,
                             PoseStack poseStack,
                             MultiBufferSource bufferSource,
                             int packedLight,
                             int packedOverlay) {
        SportsCarModelData model = SportsCarModelData.getOrLoad();
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        // 对齐 builtin/entity 显示变换中心：车底正中落在方块空间 (0.5, 0.0, 0.5)
        poseStack.translate(0.5F, 0.0F, 0.5F);

        VertexConsumer mainConsumer = ItemRenderer.getFoilBufferDirect(
                bufferSource,
                RenderType.entityCutoutNoCull(model.mainTexture()),
                true,
                stack.hasFoil());
        model.renderOpaqueParts(
                poseStack,
                mainConsumer,
                packedLight,
                packedOverlay,
                0.0F,
                0.0F,
                0.0F,
                packedLight,
                0xFFFFFFFF,
                packedLight,
                0xFFFFFFFF
        );

        VertexConsumer glassConsumer = ItemRenderer.getFoilBufferDirect(
                bufferSource,
                RenderType.entityTranslucent(model.glassTexture()),
                true,
                stack.hasFoil());
        model.renderTranslucentGlass(
                poseStack,
                glassConsumer,
                packedLight,
                packedOverlay,
                0.0F
        );

        poseStack.popPose();
    }

    /**
     * 通过 {@code RegisterClientExtensionsEvent} 注册到跑车物品上的客户端渲染扩展钩子。
     */
    public static final class SportsCarItemExtensions implements IClientItemExtensions {
        public static final SportsCarItemExtensions INSTANCE = new SportsCarItemExtensions();

        private SportsCarItemExtensions() {
        }

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return SportsCarItemRenderer.getInstance();
        }
    }
}
