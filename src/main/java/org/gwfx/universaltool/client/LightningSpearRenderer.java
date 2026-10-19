package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.LightningSpearProjectile;

/**
 * Renders the 3D Blockbench spear model pointing directly forward along the projectile's trajectory.
 */
public final class LightningSpearRenderer extends EntityRenderer<LightningSpearProjectile> {
    private final ItemRenderer itemRenderer;

    public LightningSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(LightningSpearProjectile entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();

        float entityYaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float entityPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // Projectile rotation:
        // Entity yaw - 90 aligns +X with flight direction.
        // Entity pitch - 90 rotates +Y (spearhead in the model) into +X, pointing spearhead directly forward!
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entityPitch - 90.0F));

        // Scale up model for high visibility:
        poseStack.scale(1.4F, 1.4F, 1.4F);

        // Center the rotation pivot near the middle of the shaft (json [8, 8, 8] -> [-0.5, -0.5, -0.5]):
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                entity.level(),
                entity.getId());

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(LightningSpearProjectile entity) {
        return ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/item/lightning_spear.png");
    }
}
