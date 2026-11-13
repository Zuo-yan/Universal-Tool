package org.gwfx.universaltool.space;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.UniversalToolMod;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Registered only by the isolated runIslandGameTest development configuration. */
@PrefixGameTestTemplate(false)
public final class PrivateSpaceGameTests {
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void flatIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.SUPERFLAT); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=600)
    public static void skyIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.SKY_ISLANDS); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void cherryIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.CHERRY_GROVE); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void amethystIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.AMETHYST_STARFALL); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void sculkIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.SCULK_SANCTUM); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void mushroomIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.MUSHROOM_WONDERLAND); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void obsidianIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.OBSIDIAN_FORGE); }
    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void auroraIsland(GameTestHelper helper) { environment(helper, PrivateSpaceEnvironment.AURORA_GLACIER); }

    /** The biome is part of the profile, so a new landscape needs no change here. */
    private static FiniteIslandChunkGenerator generator(ServerLevel overworld, PrivateSpaceEnvironment environment) {
        var biome = overworld.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(environment.profile().biome());
        return new FiniteIslandChunkGenerator(new FixedBiomeSource(biome), environment, 932174L + environment.ordinal(), 512);
    }
    private static DimensionType type(ServerLevel overworld) {
        DimensionType t=overworld.dimensionType();
        return new DimensionType(t.fixedTime(),t.hasSkyLight(),t.hasCeiling(),t.ultraWarm(),t.natural(),t.coordinateScale(),
                t.bedWorks(),t.respawnAnchorWorks(),t.minY(),t.height(),t.logicalHeight(),t.infiniburn(),t.effectsLocation(),
                t.ambientLight(),t.monsterSettings());
    }
    private static void environment(GameTestHelper helper, PrivateSpaceEnvironment environment) {
        var server=helper.getLevel().getServer(); var overworld=server.overworld();
        var registry=PersonalDimensionRegistry.from(server);
        var id=ResourceLocation.fromNamespaceAndPath("universal_tool","test_island/"+environment.id());
        var generator=generator(overworld,environment);
        var ops=RegistryOps.create(JsonOps.INSTANCE,overworld.registryAccess());
        var encoded=ChunkGenerator.CODEC.encodeStart(ops,generator).getOrThrow();
        var decoded=(FiniteIslandChunkGenerator)ChunkGenerator.CODEC.parse(ops,encoded).getOrThrow();
        helper.assertTrue(decoded.surfaceY(40,50)==generator.surfaceY(40,50),"Codec must preserve terrain seed");
        long start=System.nanoTime();
        var level=registry.createDynamicDimension(id,generator,type(overworld));
        helper.assertTrue(level!=null,"Dimension creation should succeed");
        var worldSpawn=overworld.getSharedSpawnPos();
        PrivateSpaceManager.configureLevel(level,environment,true);
        var spawn=level.getSharedSpawnPos();
        var profile=environment.profile();
        helper.assertTrue(spawn.getX()==0 && spawn.getZ()==0,"Private spawn must be at island center");
        helper.assertTrue(level.getBlockState(spawn.below()).is(profile.surface().top()),
                "Spawn must have dry ground: "+environment.id());
        helper.assertTrue(overworld.getSharedSpawnPos().equals(worldSpawn),"Private spawn must not change overworld spawn");
        helper.assertTrue(level.getWorldBorder().getSize()>1_000_000,"Island must not impose a movement wall");
        if(profile.spawnArea().treeLog()!=null) {
            boolean leaves=false;
            for(BlockPos pos:BlockPos.betweenClosed(-9,spawn.getY(),-9,9,spawn.getY()+8,9))
                leaves|=level.getBlockState(pos).is(profile.spawnArea().treeLeaves());
            helper.assertTrue(leaves,"Landing canopy must be visible near spawn: "+environment.id());
        }
        int[][] empty={{16,0},{-17,0},{0,16},{0,-17},{16,16},{-17,16},{16,-17},{-17,-17}};
        for(int[] point:empty) for(var section:level.getChunk(point[0],point[1]).getSections())
            helper.assertTrue(section.hasOnlyAir(),"Exterior chunk must be entirely air: "+Arrays.toString(point));
        int[][] edgeChunks={{15,0},{-16,0},{0,15},{0,-16},{10,10},{-11,10}};
        for(int[] point:edgeChunks) {
            var chunk=level.getChunk(point[0],point[1]);
            for(int dx=0;dx<16;dx++) for(int dz=0;dz<16;dz++) {
                int x=point[0]*16+dx,z=point[1]*16+dz;
                if(generator.containsTerrain(x,z)) continue;
                for(int y=-64;y<150;y++) helper.assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).isAir(),
                        "Vegetation/terrain must not cross island mask");
            }
        }
        // (40,-40) sits inside the central landmass of every prototype, floating islands included.
        int sx=40,sz=-40,surface=generator.surfaceY(sx,sz);
        helper.assertTrue(generator.containsTerrain(sx,sz),"Sample column must be inside the island");
        helper.assertTrue(level.getBlockState(new BlockPos(sx,surface,sz)).is(profile.surface().top()),
                "Generated height must match height query: "+environment.id());
        helper.assertTrue(generator.getBaseColumn(sx,sz,level,null).getBlock(surface).is(profile.surface().top()),
                "Base column must match generation: "+environment.id());
        if(profile.surface().snowLayer())
            helper.assertTrue(level.getBlockState(new BlockPos(sx,surface+1,sz)).is(Blocks.SNOW),
                    "Frozen surface must carry a snow layer: "+environment.id());
        if(profile.prototype()==PrivateSpaceEnvironment.Prototype.SKY) {
            helper.assertTrue(generator.containsTerrain(0,0),"A sky world still needs its central landmass");
            helper.assertTrue(!generator.containsTerrain(230,230),"Columns between islands must be void");
            helper.assertTrue(level.getBlockState(new BlockPos(230,generator.getSeaLevel(),230)).isAir(),
                    "The gap between sky islands must stay open");
        }
        BlockPos extension=new BlockPos(400,90,0),chestPos=spawn.offset(2,0,3);
        level.setBlockAndUpdate(extension,Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity)level.getBlockEntity(chestPos)).setItem(0,new ItemStack(Items.DIAMOND,3));
        UniversalToolMod.LOGGER.info("ISLAND_TEST {} generated/validated in {} ms",environment.id(),(System.nanoTime()-start)/1_000_000);
        helper.runAfterDelay(2,()-> {
            helper.assertTrue(server.getLevel(level.dimension())==level,"New dimension must enter server tick registry");
            var worldTag=server.getWorldData().createTag(server.registries().compositeAccess(),null);
            helper.assertTrue(!worldTag.getCompound("WorldGenSettings").getCompound("dimensions").contains(id.toString()),
                    "Runtime dimensions must not become permanent vanilla dimension definitions");
            registry.unloadDynamicDimension(id);
            helper.runAfterDelay(2,()-> {
                helper.assertTrue(server.getLevel(level.dimension())==null,"Empty dimension must unload");
                var restored=registry.loadDynamicDimension(id,decoded,type(overworld));
                helper.runAfterDelay(2,()-> {
                    helper.assertTrue(restored.getSharedSpawnPos().equals(spawn),"Independent spawn must survive unload/load");
                    helper.assertTrue(restored.getBlockState(extension).is(Blocks.DIAMOND_BLOCK),"Player builds outside island must persist");
                    var chest=(ChestBlockEntity)restored.getBlockEntity(chestPos);
                    helper.assertTrue(chest!=null && chest.getItem(0).getCount()==3,"Chest contents must persist");
                    registry.unloadDynamicDimension(id);helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace="universal_tool", template="empty")
    public static void savedDataCompatibility(GameTestHelper helper) {
        helper.assertTrue(PrivateSpaceEnvironment.byId("plains") == PrivateSpaceEnvironment.AMETHYST_STARFALL, "Legacy plains id must map to amethyst_starfall");
        helper.assertTrue(PrivateSpaceEnvironment.byId("desert") == PrivateSpaceEnvironment.OBSIDIAN_FORGE, "Legacy desert id must map to obsidian_forge");
        helper.assertTrue(PrivateSpaceEnvironment.byId("snowy_plains") == PrivateSpaceEnvironment.AURORA_GLACIER, "Legacy snowy_plains id must map to aurora_glacier");
        helper.assertTrue(PrivateSpaceEnvironment.byId("birch_forest") == PrivateSpaceEnvironment.MUSHROOM_WONDERLAND, "Legacy birch_forest id must map to mushroom_wonderland");
        helper.assertTrue(PrivateSpaceEnvironment.byId("savanna") == PrivateSpaceEnvironment.SCULK_SANCTUM, "Legacy savanna id must map to sculk_sanctum");

        UUID owner=UUID.randomUUID(); CompoundTag entry=new CompoundTag();
        entry.putUUID("Owner",owner);entry.putString("Dimension","universal_tool:legacy_fixture");
        entry.putString("Environment","plains");entry.putString("PasswordSalt","salt");entry.putString("PasswordHash","hash");
        ListTag list=new ListTag();list.add(entry);CompoundTag root=new CompoundTag();root.put("Spaces",list);
        var registry=helper.getLevel().registryAccess();
        var legacy=PersonalSpaceSavedData.load(root,registry).getSpace(owner).orElseThrow();
        helper.assertTrue(legacy.environment() == PrivateSpaceEnvironment.AMETHYST_STARFALL, "Legacy saved data environment must resolve via compatibility mapping");
        helper.assertTrue(!legacy.isFiniteIsland() && legacy.generationVersion().equals("legacy_v1"),"Missing version must retain legacy generator");
        for (var environment : PrivateSpaceEnvironment.values()) {
            var oldRecord = new PersonalSpaceSavedData.SpaceRecord(owner, legacy.dimension(), environment, "s", "h",
                    PersonalSpaceSavedData.SpaceRecord.LEGACY_VERSION, 128, 0);
            helper.assertTrue(!(PrivateSpaceManager.createGenerator(helper.getLevel(), oldRecord) instanceof FiniteIslandChunkGenerator),
                    "Legacy " + environment.id() + " must still restore its original generator");
        }
        var data=new PersonalSpaceSavedData();
        data.addSpace(new PersonalSpaceSavedData.SpaceRecord(owner,legacy.dimension(),legacy.environment(),"salt","hash",
                FiniteIslandChunkGenerator.VERSION,512,123456L));
        var origin=new PersonalSpaceSavedData.Position(Level.OVERWORLD,10,70,20,30,0);
        var privateOrigin=new PersonalSpaceSavedData.Position(Level.OVERWORLD,-40,68,12,90,5);
        data.setReturnPosition(owner,origin);data.setResumePosition(owner,origin);data.setPrivateReturnPosition(owner,privateOrigin);
        var restored=PersonalSpaceSavedData.load(data.save(new CompoundTag(),registry),registry);
        var record=restored.getSpace(owner).orElseThrow();
        helper.assertTrue(record.terrainSeed()==123456L && record.terrainSize()==512 && record.isFiniteIsland(),"SavedData round trip must preserve terrain metadata");
        helper.assertTrue(restored.getReturnPosition(owner).orElseThrow().equals(origin)
                && restored.getPrivateReturnPosition(owner).orElseThrow().equals(privateOrigin)
                && restored.getResumePosition(owner).orElseThrow().equals(origin),"Return, private-return, and resume positions must round trip");
        helper.succeed();
    }

    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=200)
    public static void returnGateProtectionAndPrivateRespawn(GameTestHelper helper) {
        var server=helper.getLevel().getServer();
        var overworld=server.overworld();
        var registry=PersonalDimensionRegistry.from(server);
        var ownerId=UUID.randomUUID();
        var visitorId=UUID.randomUUID();
        var dimensionId=ResourceLocation.fromNamespaceAndPath("universal_tool","test_return_protection/"+ownerId.toString().replace("-",""));
        var biome=overworld.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var island=new FiniteIslandChunkGenerator(new FixedBiomeSource(biome),PrivateSpaceEnvironment.SUPERFLAT,712345L,512);
        var home=registry.createDynamicDimension(dimensionId,island,type(overworld));
        var owner=new PersonalSpaceSavedData.SpaceRecord(ownerId,dimensionId,PrivateSpaceEnvironment.SUPERFLAT,
                "salt","hash",FiniteIslandChunkGenerator.VERSION,512,712345L);
        var data=PersonalSpaceSavedData.get(server);
        data.addSpace(owner);
        PrivateSpaceManager.configureLevel(home,PrivateSpaceEnvironment.SUPERFLAT,true);
        helper.runAfterDelay(2,()-> {
        var returnPos=home.getSharedSpawnPos().offset(-3,0,0);
        var returnBlock=org.gwfx.universaltool.init.ModBlocks.PERSONAL_SPACE_RETURN.get();
        helper.assertTrue(home.getBlockState(returnPos).is(returnBlock),"Home spawn must have its protected return gate");
        helper.assertTrue(returnBlock.defaultBlockState().getPistonPushReaction()==PushReaction.BLOCK,"Pistons must not move the return gate");

        var profile=new com.mojang.authlib.GameProfile(ownerId,"return-owner-test");
        var player=mockPlayer(server,overworld,profile);
        home.setBlockAndUpdate(returnPos,Blocks.AIR.defaultBlockState());
        PrivateSpaceManager.ensureReturnGate(home);
        helper.assertTrue(home.getBlockState(returnPos).is(returnBlock),"The canonical return gate must repair after removal");
        player.setGameMode(GameType.SURVIVAL);
        var survivalBreak=new BlockEvent.BreakEvent(home,returnPos,home.getBlockState(returnPos),player);
        PrivateSpaceManager.protectReturnGate(survivalBreak);
        helper.assertTrue(survivalBreak.isCanceled(),"Survival mining must be cancelled");
        player.setGameMode(GameType.CREATIVE);
        var creativeBreak=new BlockEvent.BreakEvent(home,returnPos,home.getBlockState(returnPos),player);
        PrivateSpaceManager.protectReturnGate(creativeBreak);
        helper.assertTrue(creativeBreak.isCanceled(),"Creative mining must be cancelled");
        var explosion=new Explosion(home,null,returnPos.getX()+0.5,returnPos.getY()+0.5,returnPos.getZ()+0.5,
                5.0F,false,Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(returnPos);
        var detonation=new ExplosionEvent.Detonate(home,explosion,new ArrayList<>());
        PrivateSpaceManager.protectReturnGateFromExplosion(detonation);
        helper.assertTrue(detonation.getAffectedBlocks().isEmpty(),"Explosions must not remove the return gate");

        BlockPos overworldSpawn=overworld.getSharedSpawnPos();
        var sourceTarget=new PersonalSpaceSavedData.Position(Level.OVERWORLD,overworldSpawn.getX()+0.5,
                overworldSpawn.getY(),overworldSpawn.getZ()+0.5,25,5);
        data.setReturnPosition(ownerId,sourceTarget);
        player.teleportTo(home,home.getSharedSpawnPos().getX()+0.5,home.getSharedSpawnPos().getY(),
                home.getSharedSpawnPos().getZ()+0.5,0,0);
        PrivateSpaceManager.recordPrivateDeath(new LivingDeathEvent(player,player.damageSources().genericKill()));
        player.teleportTo(overworld,sourceTarget.x(),sourceTarget.y(),sourceTarget.z(),sourceTarget.yaw(),sourceTarget.pitch());
        PrivateSpaceManager.respawnFromPrivateSpace(new PlayerEvent.PlayerRespawnEvent(player,false));
        ServerLevel currentHome = server.getLevel(home.dimension());
        helper.assertTrue(currentHome != null
                        && player.serverLevel() == currentHome
                        && player.blockPosition().equals(currentHome.getSharedSpawnPos()),
                "The owner must respawn at their own base spawn; sameDim="
                        +(currentHome != null && player.serverLevel() == currentHome)+
                        ", eqPos="+(currentHome != null && player.blockPosition().equals(currentHome.getSharedSpawnPos()))+
                        ", actual="+player.serverLevel().dimension()+"/"+player.blockPosition()+
                        ", actualClass="+player.blockPosition().getClass().getName()+
                        ", expected="+(currentHome == null ? "null" : currentHome.getSharedSpawnPos()));
        helper.assertTrue(data.getReturnPosition(ownerId).isPresent(),"Owner death must preserve their entry position");

        var visitor=mockPlayer(server,home,new com.mojang.authlib.GameProfile(visitorId,"return-visitor-test"));
        data.setReturnPosition(visitorId,sourceTarget);
        PrivateSpaceManager.recordPrivateDeath(new LivingDeathEvent(visitor,visitor.damageSources().genericKill()));
        visitor.teleportTo(overworld,sourceTarget.x(),sourceTarget.y(),sourceTarget.z(),sourceTarget.yaw(),sourceTarget.pitch());
        PrivateSpaceManager.respawnFromPrivateSpace(new PlayerEvent.PlayerRespawnEvent(visitor,false));
        helper.assertTrue(visitor.serverLevel()==overworld,"A visitor must return to the dimension they entered from");
        helper.assertTrue(data.getReturnPosition(visitorId).isEmpty(),"A completed overworld return must clear its one-time position");
        helper.assertTrue(Math.abs(visitor.getX()-sourceTarget.x())<1.01 && Math.abs(visitor.getZ()-sourceTarget.z())<1.01,
                "A missing overworld return stone must not prevent coordinate-based return");
        var unrecordedVisitor=mockPlayer(server,home,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"unrecorded-visitor-test"));
        BlockPos spawn=overworld.getSharedSpawnPos();
        PrivateSpaceManager.recordPrivateDeath(new LivingDeathEvent(unrecordedVisitor,unrecordedVisitor.damageSources().genericKill()));
        unrecordedVisitor.teleportTo(overworld,sourceTarget.x(),sourceTarget.y(),sourceTarget.z(),0,0);
        PrivateSpaceManager.respawnFromPrivateSpace(new PlayerEvent.PlayerRespawnEvent(unrecordedVisitor,false));
        helper.assertTrue(unrecordedVisitor.serverLevel()==overworld
                        && unrecordedVisitor.distanceToSqr(spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5)<64,
                "A visitor without a saved entry point must fall back to a safe position near overworld spawn");
        player.discard();
        visitor.discard();
        unrecordedVisitor.discard();
        registry.unloadDynamicDimension(dimensionId);
        helper.succeed();
        });
    }

    private static ServerPlayer mockPlayer(MinecraftServer server,ServerLevel level,com.mojang.authlib.GameProfile profile) {
        var player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        return player;
    }

    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=200)
    public static void freeFlightAndVoidRescue(GameTestHelper helper) {
        var server=helper.getLevel().getServer();var registry=PersonalDimensionRegistry.from(server);
        helper.runAfterDelay(2,()-> {
            var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"island-test-player");
            var player=new ServerPlayer(server,helper.getLevel(),profile,ClientInformation.createDefault());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                    connection,player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
            var entrance=helper.absolutePos(new BlockPos(1,1,1));
            helper.getLevel().setBlockAndUpdate(entrance,org.gwfx.universaltool.init.ModBlocks.PERSONAL_SPACE_GATE.get().defaultBlockState());
            ((PersonalSpaceGateBlockEntity)helper.getLevel().getBlockEntity(entrance)).bindTo(player.getUUID());
            player.setPos(entrance.getX()+0.5,entrance.getY(),entrance.getZ()+0.5);
            PrivateSpaceManager.handleAction(player,new org.gwfx.universaltool.network.PersonalSpaceActionPayload(
                    entrance,org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_REALM,"superflat"));
            var level=player.serverLevel();var id=level.dimension().location();
            helper.assertTrue(level!=helper.getLevel(),"Actual realm payload must enter a new dimension");
            var data=PersonalSpaceSavedData.get(server);
            helper.assertTrue(data.getRealm(PrivateSpaceEnvironment.SUPERFLAT).orElseThrow().isFiniteIsland(),
                    "Shared realm must persist the finite generator version");
            player.teleportTo(level,400,100,0,0,0);
            PrivateSpaceManager.rescueFromVoid(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.getX()==400,"Flying beyond footprint must not be clamped");
            player.teleportTo(level,400,-90,0,0,0);player.fallDistance=100;
            PrivateSpaceManager.rescueFromVoid(new PlayerTickEvent.Post(player));
            helper.assertTrue(Math.abs(player.getX())<1 && player.getY()==level.getSharedSpawnPos().getY(),
                    "Void must return player to safe spawn; pos="+player.position()+", spawn="+level.getSharedSpawnPos());
            helper.assertTrue(player.fallDistance==0 && player.getInventory().getItem(0).getCount()==7,"Rescue must preserve inventory and clear fall damage");
            PrivateSpaceManager.returnPlayer(player);
            helper.assertTrue(player.serverLevel()==helper.getLevel() && data.getReturnPosition(player.getUUID()).isEmpty(),
                    "Return door must restore original dimension and clear its saved destination");
            player.teleportTo(helper.getLevel(),400,-90,0,0,0);
            PrivateSpaceManager.rescueFromVoid(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.getY()==-90,"Void rescue must not affect normal worlds");
            player.discard();registry.unloadDynamicDimension(id);helper.succeed();
        });
    }

    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=400)
    public static void worldTraitsAndOres(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var registry = PersonalDimensionRegistry.from(server);

        // Verify DimensionType traits for eternal day, eternal night, safe beds, and piglin safety
        DimensionType flatType = PrivateSpaceManager.buildDimensionType(overworld.dimensionType(), PrivateSpaceEnvironment.SUPERFLAT);
        helper.assertTrue(flatType.fixedTime().equals(OptionalLong.of(6000L)), "Superflat must lock to eternal noon (6000)");
        helper.assertTrue(flatType.bedWorks() && !flatType.ultraWarm(), "Superflat must allow beds and water");

        DimensionType forgeType = PrivateSpaceManager.buildDimensionType(overworld.dimensionType(), PrivateSpaceEnvironment.OBSIDIAN_FORGE);
        helper.assertTrue(forgeType.fixedTime().equals(OptionalLong.of(18000L)), "Obsidian Forge must lock to eternal night (18000)");
        helper.assertTrue(forgeType.bedWorks() && !forgeType.ultraWarm() && forgeType.monsterSettings().piglinSafe(),
                "Obsidian Forge must keep beds/water safe while keeping Piglins safe from zombification");

        // Create Superflat, Amethyst Starfall, Mushroom Wonderland, and Obsidian Forge levels to test ores, weather, & spawns
        var flatId = ResourceLocation.fromNamespaceAndPath("universal_tool", "test_traits/superflat");
        var amethystId = ResourceLocation.fromNamespaceAndPath("universal_tool", "test_traits/amethyst");
        var mushroomId = ResourceLocation.fromNamespaceAndPath("universal_tool", "test_traits/mushroom");
        var forgeId = ResourceLocation.fromNamespaceAndPath("universal_tool", "test_traits/forge");

        var flatGen = generator(overworld, PrivateSpaceEnvironment.SUPERFLAT);
        var amethystGen = generator(overworld, PrivateSpaceEnvironment.AMETHYST_STARFALL);
        var mushroomGen = generator(overworld, PrivateSpaceEnvironment.MUSHROOM_WONDERLAND);
        var forgeGen = generator(overworld, PrivateSpaceEnvironment.OBSIDIAN_FORGE);

        var flatLevel = registry.createDynamicDimension(flatId, flatGen, flatType);
        var amethystLevel = registry.createDynamicDimension(amethystId, amethystGen,
                PrivateSpaceManager.buildDimensionType(overworld.dimensionType(), PrivateSpaceEnvironment.AMETHYST_STARFALL));
        var mushroomLevel = registry.createDynamicDimension(mushroomId, mushroomGen,
                PrivateSpaceManager.buildDimensionType(overworld.dimensionType(), PrivateSpaceEnvironment.MUSHROOM_WONDERLAND));
        var forgeLevel = registry.createDynamicDimension(forgeId, forgeGen, forgeType);

        PrivateSpaceManager.configureLevel(flatLevel, PrivateSpaceEnvironment.SUPERFLAT, true);
        PrivateSpaceManager.configureLevel(amethystLevel, PrivateSpaceEnvironment.AMETHYST_STARFALL, true);
        PrivateSpaceManager.configureLevel(mushroomLevel, PrivateSpaceEnvironment.MUSHROOM_WONDERLAND, true);
        PrivateSpaceManager.configureLevel(forgeLevel, PrivateSpaceEnvironment.OBSIDIAN_FORGE, true);

        helper.assertTrue(!flatLevel.isRaining() && !flatLevel.isThundering(),
                "ALWAYS_CLEAR personal realms must report clear weather");

        // Natural spawn vs spawner checks
        BlockPos flatSurface = flatLevel.getSharedSpawnPos().offset(24, 0, 24);
        flatLevel.getChunk(flatSurface);
        var natSlime = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                net.minecraft.world.entity.EntityType.SLIME, flatLevel,
                net.minecraft.world.entity.MobSpawnType.NATURAL, flatSurface, flatLevel.random, true);
        PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(natSlime);
        helper.assertTrue(!natSlime.getPlacementCheckResult(), "Superflat must block natural Slime spawns");

        var spawnerSlime = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                net.minecraft.world.entity.EntityType.SLIME, flatLevel,
                net.minecraft.world.entity.MobSpawnType.SPAWNER, flatSurface, flatLevel.random, true);
        PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(spawnerSlime);
        helper.assertTrue(spawnerSlime.getPlacementCheckResult(), "Superflat must NOT interfere with mob spawners");

        BlockPos mushPos = new BlockPos(36, mushroomGen.surfaceY(36, 36) + 1, 36);
        mushroomLevel.getChunk(mushPos);
        var natMooshroom = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                net.minecraft.world.entity.EntityType.MOOSHROOM, mushroomLevel,
                net.minecraft.world.entity.MobSpawnType.NATURAL, mushPos, mushroomLevel.random, false);
        PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(natMooshroom);
        helper.assertTrue(natMooshroom.getPlacementCheckResult(), "Mushroom Wonderland must allow natural Mooshroom spawns");

        var natZombie = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                net.minecraft.world.entity.EntityType.ZOMBIE, mushroomLevel,
                net.minecraft.world.entity.MobSpawnType.NATURAL, mushPos, mushroomLevel.random, true);
        PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(natZombie);
        helper.assertTrue(!natZombie.getPlacementCheckResult(), "Mushroom Wonderland must reject natural Zombie spawns");

        var spawnerZombie = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                net.minecraft.world.entity.EntityType.ZOMBIE, mushroomLevel,
                net.minecraft.world.entity.MobSpawnType.SPAWNER, mushPos, mushroomLevel.random, true);
        PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(spawnerZombie);
        helper.assertTrue(spawnerZombie.getPlacementCheckResult(), "Mushroom Wonderland must leave Spawner Zombies unaffected");

        helper.assertTrue(PrivateSpaceEnvironment.OBSIDIAN_FORGE.traits().spawns().hostileRateMultiplier() == 2.0,
                "Obsidian Forge must declare 2.0x hostile spawn rate multiplier");

        // Underground ore verification across 4x4 interior chunks
        int amethystOreCount = countMatchingBlocks(amethystLevel, amethystGen, Set.of(
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE));
        int forgeOreCount = countMatchingBlocks(forgeLevel, forgeGen, Set.of(
                Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS));
        int flatOreCount = countMatchingBlocks(flatLevel, flatGen, Set.of(
                Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE));

        helper.assertTrue(amethystOreCount > 40, "Amethyst Starfall 2.5x ore generation must populate dense Overworld ores; found=" + amethystOreCount);
        helper.assertTrue(forgeOreCount > 15, "Obsidian Forge must populate Nether ores underground; found=" + forgeOreCount);
        helper.assertTrue(flatOreCount == 0, "Superflat must contain zero underground ores; found=" + flatOreCount);

        registry.unloadDynamicDimension(flatId);
        registry.unloadDynamicDimension(amethystId);
        registry.unloadDynamicDimension(mushroomId);
        registry.unloadDynamicDimension(forgeId);
        helper.succeed();
    }

    @GameTest(templateNamespace="universal_tool", template="empty", timeoutTicks=300)
    public static void realmSwitchingAndPrivateStarryPocket(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registry = PersonalDimensionRegistry.from(server);
        var overworld = server.overworld();

        // 1. Starry Void Block must NOT have an item or appear in any Creative tab
        var starryBlock = org.gwfx.universaltool.init.ModBlocks.STARRY_VOID_BLOCK.get();
        helper.assertTrue(starryBlock.asItem() == Items.AIR
                        && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(starryBlock)),
                "StarryVoidBlock must have no BlockItem and must be excluded from creative tabs");
        boolean foundInCreative = org.gwfx.universaltool.init.ModCreativeTabs.UNIVERSAL_TOOL_TAB.get().getDisplayItems()
                .stream().anyMatch(stack -> stack.is(starryBlock.asItem()));
        helper.assertTrue(!foundInCreative, "StarryVoidBlock must never appear in CreativeModeTab");

        helper.runAfterDelay(2, () -> {
            var alice = mockPlayer(server, helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "alice-star"));
            var bob = mockPlayer(server, helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "bob-star"));

            BlockPos gatePos = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos startA = helper.absolutePos(new BlockPos(2, 1, 1));
            BlockPos startB = helper.absolutePos(new BlockPos(0, 1, 2));
            for (BlockPos floor : List.of(gatePos.below(), startA.below(), startB.below())) {
                if (helper.getLevel().getBlockState(floor).isAir()) {
                    helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                }
            }
            helper.getLevel().setBlockAndUpdate(gatePos,
                    org.gwfx.universaltool.init.ModBlocks.PERSONAL_SPACE_GATE.get().defaultBlockState());

            alice.setPos(startA.getX() + 0.5, startA.getY(), startA.getZ() + 0.5);
            bob.setPos(startB.getX() + 0.5, startB.getY(), startB.getZ() + 0.5);

            // 2. Freely switch between shared exploration realms without lock-in
            PrivateSpaceManager.handleAction(alice, new org.gwfx.universaltool.network.PersonalSpaceActionPayload(
                    gatePos, org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_REALM, "superflat"));
            ServerLevel flatRealm = alice.serverLevel();
            helper.assertTrue(flatRealm.dimension().location().getPath().equals("realm/superflat"),
                    "Alice must enter shared Superflat realm");

            BlockPos realmGate = flatRealm.getSharedSpawnPos().offset(2, 0, 0);
            flatRealm.setBlockAndUpdate(realmGate,
                    org.gwfx.universaltool.init.ModBlocks.PERSONAL_SPACE_GATE.get().defaultBlockState());
            alice.setPos(realmGate.getX() + 0.5, realmGate.getY(), realmGate.getZ() + 0.5);
            PrivateSpaceManager.handleAction(alice, new org.gwfx.universaltool.network.PersonalSpaceActionPayload(
                    realmGate, org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_REALM, "cherry_grove"));
            ServerLevel cherryRealm = alice.serverLevel();
            helper.assertTrue(cherryRealm.dimension().location().getPath().equals("realm/cherry_grove"),
                    "Alice must freely switch to Cherry Grove realm without lock-in");

            // Returning from realm restores original Overworld coordinates (preserved across realm switches)
            PrivateSpaceManager.returnPlayer(alice);
            helper.assertTrue(alice.serverLevel() == helper.getLevel()
                            && alice.distanceToSqr(startA.getX() + 0.5, startA.getY(), startA.getZ() + 0.5) < 9.0,
                    "Returning after free realm switching must restore pre-realm Overworld position; level="
                            + alice.serverLevel().dimension() + ", pos=" + alice.position()
                            + ", expected=" + startA
                            + ", floor=" + helper.getLevel().getBlockState(startA.below())
                            + ", feet=" + helper.getLevel().getBlockState(startA)
                            + ", head=" + helper.getLevel().getBlockState(startA.above())
                            + ", savedReturn=" + PersonalSpaceSavedData.get(server).getReturnPosition(alice.getUUID()));

            // 3. Secondary menu -> UUID-keyed password-free 25x25x25 Private Starry Space
            alice.setPos(gatePos.getX() + 0.5, gatePos.getY(), gatePos.getZ() + 1.0);
            PrivateSpaceManager.handleAction(alice, new org.gwfx.universaltool.network.PersonalSpaceActionPayload(
                    gatePos, org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE, ""));
            ServerLevel pocketA = alice.serverLevel();

            bob.setPos(gatePos.getX() + 0.5, gatePos.getY(), gatePos.getZ() - 1.0);
            PrivateSpaceManager.handleAction(bob, new org.gwfx.universaltool.network.PersonalSpaceActionPayload(
                    gatePos, org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE, ""));
            ServerLevel pocketB = bob.serverLevel();

            helper.assertTrue(PrivateSpaceManager.isStarryPocket(pocketA) && PrivateSpaceManager.isStarryPocket(pocketB),
                    "Both players must enter StarryPocket dimensions");
            helper.assertTrue(pocketA != pocketB && !pocketA.dimension().equals(pocketB.dimension()),
                    "Private Starry Space must isolate dimensions by player UUID");

            // Inspect 25x25x25 starry shell + interior air + BlockEntity presence
            helper.assertTrue(pocketA.getBlockState(new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0)).is(starryBlock)
                            && pocketA.getBlockEntity(new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0)) instanceof StarryVoidBlockEntity
                            && pocketA.getBlockState(new BlockPos(12, StarryPocketChunkGenerator.FLOOR_Y, -12)).is(starryBlock),
                    "25x25 floor y=63 must be StarryVoidBlock with StarryVoidBlockEntity");
            helper.assertTrue(pocketA.getBlockState(new BlockPos(0, StarryPocketChunkGenerator.CEILING_Y, 0)).is(Blocks.BARRIER)
                            && pocketA.getBlockState(new BlockPos(StarryPocketChunkGenerator.SHELL_HALF, 70, 0)).is(Blocks.BARRIER)
                            && pocketA.getBlockState(new BlockPos(-StarryPocketChunkGenerator.SHELL_HALF, 70, 0)).is(Blocks.BARRIER)
                            && pocketA.getBlockState(new BlockPos(0, 70, StarryPocketChunkGenerator.SHELL_HALF)).is(Blocks.BARRIER)
                            && pocketA.getBlockState(new BlockPos(0, 70, -StarryPocketChunkGenerator.SHELL_HALF)).is(Blocks.BARRIER),
                    "Perimeter walls and ceiling cap must be invisible BARRIER blocks");
            helper.assertTrue(pocketA.getBlockState(new BlockPos(0, StarryPocketChunkGenerator.INTERIOR_MIN_Y, 0)).isAir()
                            && pocketA.getBlockState(new BlockPos(12, 88, -12)).isAir(),
                    "25x25x25 interior (-12..+12, y=64..88) must be clear air");

            // Survival protection & zero natural mobs
            alice.setGameMode(GameType.SURVIVAL);
            var breakAttempt = new BlockEvent.BreakEvent(pocketA, new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0),
                    starryBlock.defaultBlockState(), alice);
            PrivateSpaceManager.protectReturnGate(breakAttempt);
            helper.assertTrue(breakAttempt.isCanceled(), "StarryVoidBlock shell must be unbreakable in survival");

            var mobCheck = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                    net.minecraft.world.entity.EntityType.ZOMBIE, pocketA,
                    net.minecraft.world.entity.MobSpawnType.NATURAL, new BlockPos(0, 64, 0),
                    pocketA.random, true);
            PrivateSpaceWorldTraitHandler.onSpawnPlacementCheck(mobCheck);
            helper.assertTrue(mobCheck.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL,
                    "Private Starry Space must block natural mob spawns");

            // 4. Shift + Right-Click StarryVoidBlock (even while holding a pickaxe!) returns to exact pre-entry position
            alice.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            alice.setShiftKeyDown(true);
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    new net.minecraft.world.phys.Vec3(0.5, StarryPocketChunkGenerator.FLOOR_Y + 1.0, 0.5),
                    net.minecraft.core.Direction.UP,
                    new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0), false);
            var shiftClick = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    alice, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockPos(0, StarryPocketChunkGenerator.FLOOR_Y, 0), hit);
            PrivateSpaceManager.onStarryVoidShiftClick(shiftClick);
            helper.assertTrue(shiftClick.isCanceled(), "Shift+RightClick on StarryVoidBlock must intercept item use");
            helper.assertTrue(alice.serverLevel() == helper.getLevel()
                            && alice.distanceToSqr(gatePos.getX() + 0.5, gatePos.getY(), gatePos.getZ() + 1.0) < 4.0,
                    "Shift+RightClick StarryVoidBlock must return player to exact pre-entry Overworld location");

            alice.discard();
            bob.discard();
            registry.unloadDynamicDimension(flatRealm.dimension().location());
            registry.unloadDynamicDimension(cherryRealm.dimension().location());
            registry.unloadDynamicDimension(pocketA.dimension().location());
            registry.unloadDynamicDimension(pocketB.dimension().location());
            overworld.setBlockAndUpdate(gatePos, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    private static int countMatchingBlocks(ServerLevel level, FiniteIslandChunkGenerator generator, Set<Block> targets) {
        int found = 0;
        for (int cx = 2; cx <= 5; cx++) {
            for (int cz = 2; cz <= 5; cz++) {
                var chunk = level.getChunk(cx, cz);
                int minX = cx * 16;
                int minZ = cz * 16;
                for (int dx = 0; dx < 16; dx += 2) {
                    for (int dz = 0; dz < 16; dz += 2) {
                        int x = minX + dx;
                        int z = minZ + dz;
                        if (!generator.containsTerrain(x, z)) continue;
                        int top = generator.surfaceY(x, z);
                        int bottom = generator.bottomY(x, z);
                        for (int y = bottom + 1; y < top - 2; y++) {
                            if (targets.contains(chunk.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                                found++;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
