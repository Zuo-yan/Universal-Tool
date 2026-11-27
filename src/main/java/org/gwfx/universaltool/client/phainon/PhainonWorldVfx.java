package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonWorldVfx {
    private static BooleanSupplier shaderPackActive = irisShaderPackStatus();

    private PhainonWorldVfx() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!PhainonSkillEffectRenderer.hasWorldEffects() && !PhainonUltimateEffectRenderer.hasWorldEffects()
                && !PhainonEnhancedBasicEffects.hasWorldEffects() && !PhainonCounterEffects.hasWorldEffects()
                && !PhainonMeteorEffects.hasWorldEffects()) {
            return;
        }

        // Iris composites its world targets before NeoForge dispatches AFTER_LEVEL.
        if (shaderPackActive.getAsBoolean()) {
            if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                renderAfterComposite(event);
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderEffects(event);
        }
    }

    private static void renderAfterComposite(RenderLevelStageEvent event) {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            renderEffects(event);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderEffects(RenderLevelStageEvent event) {
        PhainonSkillEffectRenderer.renderWorld(event);
        PhainonUltimateEffectRenderer.renderWorld(event);
        PhainonEnhancedBasicEffects.renderWorld(event);
        PhainonCounterEffects.renderWorld(event);
        PhainonMeteorEffects.renderWorld(event);
    }

    private static BooleanSupplier irisShaderPackStatus() {
        if (!ModList.get().isLoaded("iris")) {
            return () -> false;
        }
        try {
            // Iris remains optional, including on clients built without its API on the classpath.
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method active = api.getMethod("isShaderPackInUse");
            return () -> {
                try {
                    return (boolean) active.invoke(instance);
                } catch (ReflectiveOperationException e) {
                    UniversalToolMod.LOGGER.warn("Could not read Iris shader status", e);
                    shaderPackActive = () -> false;
                    return false;
                }
            };
        } catch (ReflectiveOperationException | LinkageError e) {
            UniversalToolMod.LOGGER.warn("Iris shader status API is unavailable", e);
            return () -> false;
        }
    }
}
