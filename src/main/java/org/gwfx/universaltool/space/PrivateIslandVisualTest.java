package org.gwfx.universaltool.space;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PersonalSpaceScreen;
import org.gwfx.universaltool.init.*;
import java.util.UUID;

/** Opt-in visual acceptance run against an isolated copy, never the user's live save. */
@EventBusSubscriber(modid=UniversalToolMod.MODID, value=Dist.CLIENT)
public final class PrivateIslandVisualTest {
    private static int phase;
    private static int ticks;
    private static int sweepIndex;
    private static boolean sweepClicked;
    private static volatile boolean ready;
    private static volatile int visualGroundY;
    private static volatile FiniteIslandChunkGenerator visualGenerator;
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("universal_tool","visual_island");

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("universal_tool.islandVisual")) return;
        var client=Minecraft.getInstance();client.options.pauseOnLostFocus=false;
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().stop();
        if (ready && client.player!=null && (phase==2 || phase==4 || phase==44)) {
            client.mouseHandler.releaseMouse();
            client.setScreen(null);
            client.player.getInventory().selected=3;
            client.player.input.leftImpulse=0;client.player.input.forwardImpulse=0;
            client.player.input.jumping=false;client.player.setDeltaMovement(0,0,0);
            if(phase==2) {
                client.player.setPos(0.5,visualGroundY+1,-2);
                client.player.setYRot(0);client.player.yRotO=0;
                client.player.setXRot(20);client.player.xRotO=20;
            } else {
                client.player.setPos(0,245,0);
                if(phase==4 && ticks==120) {
                    client.levelRenderer.allChanged();
                    UniversalToolMod.LOGGER.info("VISUAL_TEST rebuilt client meshes after aerial camera jump");
                }
                client.player.setYRot(0);client.player.yRotO=0;
                client.player.setXRot(90);client.player.xRotO=90;
            }
        }
        if (phase==0 && !(client.screen instanceof TitleScreen) && client.screen!=null && ++ticks>80) {
            UniversalToolMod.LOGGER.info("VISUAL_TEST replacing onboarding screen {}",client.screen.getClass().getSimpleName());
            client.setScreen(new TitleScreen());
        }
        if(phase==0 && client.screen instanceof TitleScreen) {
            phase=1;client.createWorldOpenFlows().openWorld("island-visual",()-> {
                UniversalToolMod.LOGGER.error("VISUAL_TEST unable to open isolated save");client.stop();
            });
        } else if(phase==1 && client.level!=null && client.player!=null && client.getSingleplayerServer()!=null) {
            phase=2;ticks=0;client.options.renderDistance().set(20);client.options.broadcastOptions();
            var server=client.getSingleplayerServer();var playerId=client.player.getUUID();
            server.execute(()-> {
                var testPlayer=server.getPlayerList().getPlayer(playerId);
                var safeOverworld=server.overworld().getSharedSpawnPos();
                testPlayer.teleportTo(server.overworld(),safeOverworld.getX()+0.5,safeOverworld.getY(),safeOverworld.getZ()+0.5,0,0);
                // Round-trip an actual pre-existing base from the copied save, retaining its generator and blocks.
                for(var record:PersonalSpaceSavedData.get(server).allSpaces()) {
                    if(!PersonalSpaceSavedData.SpaceRecord.LEGACY_VERSION.equals(record.generationVersion())) continue;
                    var old=PrivateSpaceManager.getOrLoadSpace(server,record);
                    if(old==null) throw new IllegalStateException("Legacy fixture failed to load");
                    var saved=PersonalSpaceSavedData.get(server).getResumePosition(record.owner());
                    BlockPos sample=saved.filter(p->p.dimension().location().equals(record.dimension()))
                            .map(p->BlockPos.containing(p.x(),p.y(),p.z())).orElse(old.getSharedSpawnPos());
                    long before=checksum(old,sample);
                    PersonalDimensionRegistry.from(server).unloadDynamicDimension(record.dimension());
                    var after=PrivateSpaceManager.getOrLoadSpace(server,record);
                    if(checksum(after,sample)!=before)throw new IllegalStateException("Legacy blocks changed after restore");
                    UniversalToolMod.LOGGER.info("VISUAL_TEST legacy base {} preserved; generator={}",record.dimension(),after.getChunkSource().getGenerator().getClass().getSimpleName());
                }
                var biome=server.overworld().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.CHERRY_GROVE);
                var generator=new FiniteIslandChunkGenerator(new FixedBiomeSource(biome),PrivateSpaceEnvironment.CHERRY_GROVE,123456L,512);
                visualGenerator=generator;
                var record=new PersonalSpaceSavedData.SpaceRecord(UUID.nameUUIDFromBytes("visual-island".getBytes()),ID,
                        PrivateSpaceEnvironment.CHERRY_GROVE,"s","h",FiniteIslandChunkGenerator.VERSION,512,123456L);
                var existing=PersonalSpaceSavedData.get(server).getSpace(record.owner());
                PersonalSpaceSavedData.get(server).addSpace(record);
                var level=PrivateSpaceManager.getOrLoadSpace(server,record);
                PrivateSpaceManager.configureLevel(level,PrivateSpaceEnvironment.CHERRY_GROVE,existing.isEmpty());
                if(Boolean.getBoolean("universal_tool.islandRestartOnly")) {
                    if(!level.getSharedSpawnPos().equals(new BlockPos(0,80,0)) || !level.getBlockState(new BlockPos(400,90,0)).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK))
                        throw new IllegalStateException("Full restart failed to restore spawn or exterior build");
                    UniversalToolMod.LOGGER.info("VISUAL_TEST full restart restored finite seed, spawn and exterior build");
                }
                level.setBlockAndUpdate(new BlockPos(400,90,0),net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());
                var spawn=level.getSharedSpawnPos();visualGroundY=spawn.getY();
                int inspected=0;
                for(int x=-239;x<=241;x+=16)for(int z=-239;z<=241;z+=16) {
                    if(!generator.containsTerrain(x,z))continue;
                    if(!level.getBlockState(new BlockPos(x,generator.surfaceY(x,z),z)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))
                        throw new IllegalStateException("Interior terrain hole at "+x+","+z);
                    inspected++;
                }
                UniversalToolMod.LOGGER.info("VISUAL_TEST full-footprint terrain integrity: {} columns passed",inspected);
                Direction[] directions={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
                for(int i=0;i<4;i++)level.setBlockAndUpdate(spawn.offset(i*2-3,0,3),ModBlocks.PERSONAL_SPACE_GATE.get()
                        .defaultBlockState().setValue(PersonalSpaceGateBlock.FACING,directions[i]));
                server.overworld().setDayTime(6000);
                var player=server.getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
                player.getInventory().selected=3;
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.PERSONAL_SPACE_GATE.get()));
                PersonalDimensionRegistry.from(server).whenClientReady(player,level,()-> {
                    player.teleportTo(level,0.5,spawn.getY()+1,-2,0,20);
                    ready=true;
                });
            });
        } else if(phase==2 && ready && client.player!=null && ++ticks>160) {
            if(Boolean.getBoolean("universal_tool.islandRestartOnly")) { client.stop(); phase=99; return; }
            capture("gateway_world.png");phase=3;ticks=0;client.setScreen(new InventoryScreen(client.player));
        } else if(phase==3 && ++ticks>50) {
            capture("gateway_inventory.png");phase=4;ticks=0;client.setScreen(null);client.options.fov().set(110);client.options.hideGui=true;
            var server=client.getSingleplayerServer();var id=client.player.getUUID();
            server.execute(()-> {var player=server.getPlayerList().getPlayer(id);player.teleportTo(player.serverLevel(),0,245,0,0,90);});
        } else if(phase==4 && ++ticks>300) {
            int missing=0,total=0;
            for(int x=-239;x<=241;x+=16)for(int z=-239;z<=241;z+=16) {
                if(!visualGenerator.containsTerrain(x,z))continue;total++;
                if(!client.level.getBlockState(new BlockPos(x,visualGenerator.surfaceY(x,z),z)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))missing++;
            }
            UniversalToolMod.LOGGER.info("VISUAL_TEST client terrain columns {} total, {} missing; {}",total,missing,client.levelRenderer.getSectionStatistics());
            capture("island_aerial.png");phase=44;ticks=0;client.smartCull=false;
        } else if(phase==44 && ++ticks>100) {
            capture("island_aerial_no_cull.png");phase=5;ticks=0;client.options.hideGui=false;
            client.setScreen(new PersonalSpaceScreen(BlockPos.ZERO,PersonalSpaceScreen.CREATE));
        } else if(phase==5 && ++ticks>50) {
            capture("island_creation_ui.png");
            sweepIndex=0;sweepClicked=false;ticks=0;phase=6;
        } else if(phase==6) {
            // Walk every landscape through the real selector, one click each.
            var values=PrivateSpaceEnvironment.values();
            if(!sweepClicked) {
                selectChip(values[sweepIndex]);sweepClicked=true;ticks=0;
            } else if(++ticks>20) {
                capture("island_ui_"+values[sweepIndex].id()+".png");
                if(++sweepIndex>=values.length) {
                    phase=7;ticks=0;
                    client.setScreen(new PersonalSpaceScreen(BlockPos.ZERO,PersonalSpaceScreen.VISIT));
                } else sweepClicked=false;
            }
        } else if(phase==7 && ++ticks>50) {
            capture("island_ui_visit.png");ticks=0;phase=8;
            client.setScreen(new PersonalSpaceScreen(BlockPos.ZERO,PersonalSpaceScreen.CHANGE_PASSWORD));
        } else if(phase==8 && ++ticks>50) {
            capture("island_ui_change_password.png");ticks=0;phase=9;
            client.setScreen(new PersonalSpaceScreen(BlockPos.ZERO,PersonalSpaceScreen.CREATE));
        } else if(phase==9 && ++ticks>50) {
            // A rejected submission must report inline and leave the screen open.
            var screen=client.screen;
            clickButton("PanelButton",0,"confirm with an empty password");
            if(client.screen!=screen) throw new IllegalStateException("Validation failure must not close the screen");
            ticks=0;phase=10;
        } else if(phase==10 && ++ticks>20) {
            capture("island_ui_validation_error.png");
            UniversalToolMod.LOGGER.info("VISUAL_TEST all captures completed");client.stop();phase=11;
        }
    }
    /** Clicks an environment chip the way a player would, proving the selector answers one click per landscape. */
    private static void selectChip(PrivateSpaceEnvironment environment) {
        clickButton("EnvironmentChip",environment.ordinal(),"select "+environment.id());
    }
    private static void clickButton(String simpleName,int index,String action) {
        var screen=Minecraft.getInstance().screen;
        if(screen==null) throw new IllegalStateException("No screen is open to "+action);
        var matches=new java.util.ArrayList<net.minecraft.client.gui.layouts.LayoutElement>();
        for(var child:screen.children())
            if(child.getClass().getSimpleName().equals(simpleName)
                    && child instanceof net.minecraft.client.gui.layouts.LayoutElement element) matches.add(element);
        if(matches.size()<=index)
            throw new IllegalStateException("Expected a "+simpleName+" to "+action+", found "+matches.size());
        var target=matches.get(index);
        if(!screen.mouseClicked(target.getX()+target.getWidth()/2.0,target.getY()+target.getHeight()/2.0,0))
            throw new IllegalStateException("Click was not handled: "+action);
        UniversalToolMod.LOGGER.info("VISUAL_TEST {} by clicking {}",action,simpleName);
    }
    private static void capture(String file) {
        var client=Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory,file,client.getMainRenderTarget(),message->
                UniversalToolMod.LOGGER.info("VISUAL_TEST screenshot {}: {}",file,message.getString()));
    }
    private static long checksum(net.minecraft.server.level.ServerLevel level, BlockPos sample) {
        var chunk=level.getChunk(sample.getX()>>4,sample.getZ()>>4);long hash=1;
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++)
            hash=31*hash+chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX()+dx,y,chunk.getPos().getMinBlockZ()+dz)).hashCode();
        return hash;
    }
}
