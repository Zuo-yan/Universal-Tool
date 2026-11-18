package org.gwfx.universaltool.client;

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
 * 3D item renderer for {@code universal_tool:all_terrain_wheelchair} across GUI, ground, item frame, and hand views.
 */
public final class WheelchairItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static volatile WheelchairItemRenderer instance;

    public WheelchairItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    /**
     * Lazily constructs the shared item renderer instance after Minecraft render dispatchers are initialized.
     */
    public static WheelchairItemRenderer getInstance() {
        WheelchairItemRenderer cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (WheelchairItemRenderer.class) {
            if (instance == null) {
                Minecraft mc = Minecraft.getInstance();
                instance = new WheelchairItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
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
        WheelchairModelData model = WheelchairModelData.getOrLoad();
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        // Match builtin/entity display transforms: ground wheel contact sits at block-space (0.5, 0.0, 0.5).
        poseStack.translate(0.5F, 0.0F, 0.5F);
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(
                bufferSource,
                RenderType.entityCutoutNoCull(WheelchairModelData.TEXTURE),
                true,
                stack.hasFoil());
        model.render(poseStack, consumer, packedLight, packedOverlay,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        poseStack.popPose();
    }

    /**
     * Client item extension hook registered via {@code RegisterClientExtensionsEvent}.
     */
    public static final class WheelchairItemExtensions implements IClientItemExtensions {
        public static final WheelchairItemExtensions INSTANCE = new WheelchairItemExtensions();

        public WheelchairItemExtensions() {
        }

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return WheelchairItemRenderer.getInstance();
        }
    }
}
