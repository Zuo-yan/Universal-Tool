package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonCounter;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;

@EventBusSubscriber(modid=UniversalToolMod.MODID,value=Dist.CLIENT)
public final class PhainonCounterEffects {
    private record Scene(long start, PhainonSkillEffectPayload cue, PhainonCounterStrikeVisuals.Strike path) {
        float age(float partial) { return level.getGameTime()-start+partial; }
        boolean strike() { return cue.action()==PhainonSkillEffectPayload.Action.RIPOSTE; }
        float lift(float t) {
            return strike() ? smooth(t/19) * (1-smooth((t-32)/14)) : smooth((t-12)/24);
        }
    }
    private static ClientLevel level;
    private static final Map<Integer,Scene> scenes=new HashMap<>();
    private static PhainonVfxModelData.BakedMesh sword;
    public static void reload(net.minecraft.server.packs.resources.ResourceManager manager) {
        sword = PhainonVfxModelData.loadObjMesh(manager, ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "models/item/qinchen_sword_awakened.obj"));
    }
    private PhainonCounterEffects() {}
    private static float smooth(float t) { return PhainonEnhancedBasicEffects.smooth(t); }
    private static void sync() {
        if(level!=Minecraft.getInstance().level) { scenes.clear(); level=Minecraft.getInstance().level; }
    }
    static void apply(PhainonSkillEffectPayload cue) {
        sync();
        if(cue.action()==PhainonSkillEffectPayload.Action.GUARD_END) scenes.remove(cue.casterId());
        else if (level != null) {
            var owner = level.getEntity(cue.casterId());
            Vec3 hit = new Vec3(cue.originX(), cue.originY(), cue.originZ());
            Vec3 origin = owner == null ? hit : owner.position();
            Vec3 direction = hit.subtract(origin);
            float yaw = direction.horizontalDistanceSqr() < .001 ? cue.yawDeg()
                    : (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            boolean mainRight = !(owner instanceof Player player) || player.getMainArm() == HumanoidArm.RIGHT;
            int side = cue.mainHand() == mainRight ? -1 : 1;
            var path = new PhainonCounterStrikeVisuals.Strike(origin, hit, yaw, side);
            scenes.put(cue.casterId(), new Scene(level.getGameTime(), cue, path));
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        sync();
        scenes.values().removeIf(s->s.age(0)>=s.cue.totalTicks());
    }
    static boolean hand(PoseStack p, Player player, InteractionHand hand, HumanoidArm arm,float partial,float equip) {
        Scene s=scenes.get(player.getId());
        if(s==null || player.level()!=level || s.cue.mainHand()!=(hand==InteractionHand.MAIN_HAND)) return false;
        float t=s.age(partial), lift=s.lift(t), side=arm==HumanoidArm.RIGHT?1:-1;
        float weight=s.strike()?1-smooth((t-46)/18):smooth(t/6);
        p.translate(side*(.56-.18*weight),-.52-.6*equip+(.12+.27*lift)*weight,-.72);
        p.mulPose(Axis.ZP.rotationDegrees(side*(64-86*lift)*weight));
        p.mulPose(Axis.XP.rotationDegrees((18+36*lift)*weight));
        return true;
    }
    public static void pose(PlayerModel<?> model,Player player) {
        Scene s=scenes.get(player.getId());
        if(s==null || level!=player.level()) return;
        float t=s.age(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        float lift=s.lift(t), w=s.strike()?1-smooth((t-46)/17):smooth(t/7);
        boolean right=(player.getMainArm()==HumanoidArm.RIGHT)==s.cue.mainHand();
        var arm=right?model.rightArm:model.leftArm;
        arm.xRot=Mth.lerp(w,arm.xRot,-1.15F-1.5F*lift);
        arm.zRot=Mth.lerp(w,arm.zRot,(right?-1:1)*(.74F-.51F*lift));
        model.body.xRot+=s.strike()?.24F*smooth((t-32)/14)*w:0;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || scenes.isEmpty() || level!=Minecraft.getInstance().level) return;
        var meshes=PhainonVfxModelData.getOrLoad();
        if(meshes==null) return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var layer=PhainonVfxRenderTypes.additiveGlow(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID,"textures/misc/phainon_sunburst_sigil.png"));

        var p=e.getPoseStack();
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (sword != null) {
            var rock = PhainonVfxRenderTypes.solidTriangles(ResourceLocation.fromNamespaceAndPath(
                    UniversalToolMod.MODID, "textures/item/qinchen_sword_awakened.png"));
            var solid = buffers.getBuffer(rock);
            for (Scene scene : scenes.values()) {
                if (!scene.strike()) continue;
                float age = scene.age(partial);
                PhainonCounterStrikeVisuals.steel(p, solid, sword, scene.path, e.getCamera().getPosition(), age);
            }
            buffers.endBatch(rock);
        }
        var debris = PhainonVfxRenderTypes.solidTriangles(ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/misc/phainon_debris_rock.png"));
        var rubble = buffers.getBuffer(debris);
        Vec3 camera = e.getCamera().getPosition();
        for (Scene scene : scenes.values()) {
            if (scene.strike()) PhainonCounterStrikeVisuals.fracture(p, rubble, meshes, scene.path, camera, scene.age(partial), false);
        }
        buffers.endBatch(debris);
        var buf = buffers.getBuffer(layer);
        for(Scene s:scenes.values()) {
            var cue=s.cue;
            float age = s.age(partial);
            if (s.strike()) {
                PhainonCounterStrikeVisuals.edge(p, buf, meshes, s.path, camera, age);
                PhainonCounterStrikeVisuals.fracture(p, buf, meshes, s.path, camera, age, true);
                continue;
            }
            var owner = level.getEntity(cue.casterId());
            if (owner == null) continue;
            boolean close = owner == e.getCamera().getEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            Vec3 pos = owner.getPosition(partial).subtract(camera);
            float lift = s.lift(age);
            float extend = smooth((age - 22) / 17);
            float intensity = smooth(age / 6) * (close ? .48F : .94F);
            p.pushPose();
            p.translate(pos.x, pos.y, pos.z);
            p.mulPose(Axis.YP.rotationDegrees(-owner.getYRot()));
            p.translate(s.path.side() * (close ? .71 : .42), 1.17 + lift * .62, .48);
            p.mulPose(Axis.ZP.rotationDegrees(s.path.side() * (80 - 116 * lift + 84 * extend)));
            p.pushPose();
            p.mulPose(Axis.XP.rotationDegrees(-90));
            p.scale(.52F + extend, .5F, 1.4F + extend * (close ? 1.5F : 4.9F));
            meshes.slashBeam().renderAdditive(p,buf,1,.79F,.27F,intensity,0,0);
            p.popPose();
            float size = .20F + .24F * smooth(age / 10);
            p.scale(size, size, size);
            p.mulPose(Axis.ZP.rotationDegrees(age * 2));
            meshes.sunburstWheel().renderAdditive(p,buf,1,.92F,.43F,intensity,0,0);
            p.popPose();
        }
        buffers.endBatch(layer);
    }
}
