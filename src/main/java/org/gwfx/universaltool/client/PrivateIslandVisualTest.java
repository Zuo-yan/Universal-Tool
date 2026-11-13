package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModBlocks;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.space.FiniteIslandChunkGenerator;
import org.gwfx.universaltool.space.PersonalDimensionRegistry;
import org.gwfx.universaltool.space.PersonalSpaceGateBlock;
import org.gwfx.universaltool.space.PersonalSpaceSavedData;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment;
import org.gwfx.universaltool.space.PrivateSpaceManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Opt-in visual acceptance run against an isolated copy, never the user's live save. */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PrivateIslandVisualTest {
    private static int phase;
    private static int ticks;
    private static int sweepIndex;
    private static boolean sweepClicked;
    private static volatile boolean ready;
    private static volatile int visualGroundY;
    private static volatile FiniteIslandChunkGenerator visualGenerator;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("universal_tool", "visual_island");

    // Realm preview capture mode state
    private static int capturePhase;
    private static int captureState;
    private static int captureTicks;
    private static int captureIndex;
    private static final long CAPTURE_RUN_ID = System.currentTimeMillis() / 1000L;
    private static volatile boolean captureReady;
    private static volatile ResourceLocation activePreviewDimId;
    private static volatile double camX, camY, camZ;
    private static volatile float camYaw, camPitch;
    private static volatile int targetLookX, targetLookY, targetLookZ;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("universal_tool.islandVisual")) return;
        var client = Minecraft.getInstance();
        if (isCaptureRealmPreviewsMode()) {
            tickCaptureRealmPreviews(client);
            return;
        }
        client.options.pauseOnLostFocus = false;
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().stop();
        if (ready && client.player != null && (phase == 2 || phase == 4 || phase == 44)) {
            client.mouseHandler.releaseMouse();
            client.setScreen(null);
            client.player.getInventory().selected = 3;
            client.player.input.leftImpulse = 0;
            client.player.input.forwardImpulse = 0;
            client.player.input.jumping = false;
            client.player.setDeltaMovement(0, 0, 0);
            if (phase == 2) {
                client.player.setPos(0.5, visualGroundY + 1, -2);
                client.player.setYRot(0);
                client.player.yRotO = 0;
                client.player.setXRot(20);
                client.player.xRotO = 20;
            } else {
                client.player.setPos(0, 245, 0);
                if (phase == 4 && ticks == 120) {
                    client.levelRenderer.allChanged();
                    UniversalToolMod.LOGGER.info("VISUAL_TEST rebuilt client meshes after aerial camera jump");
                }
                client.player.setYRot(0);
                client.player.yRotO = 0;
                client.player.setXRot(90);
                client.player.xRotO = 90;
            }
        }
        if (phase == 0 && !(client.screen instanceof TitleScreen) && client.screen != null && ++ticks > 80) {
            UniversalToolMod.LOGGER.info("VISUAL_TEST replacing onboarding screen {}", client.screen.getClass().getSimpleName());
            client.setScreen(new TitleScreen());
        }
        if (phase == 0 && client.screen instanceof TitleScreen) {
            phase = 1;
            client.createWorldOpenFlows().openWorld("island-visual", () -> {
                UniversalToolMod.LOGGER.error("VISUAL_TEST unable to open isolated save");
                client.stop();
            });
        } else if (phase == 1 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
            phase = 2;
            ticks = 0;
            client.options.renderDistance().set(20);
            client.options.broadcastOptions();
            var server = client.getSingleplayerServer();
            var playerId = client.player.getUUID();
            server.execute(() -> {
                var testPlayer = server.getPlayerList().getPlayer(playerId);
                var safeOverworld = server.overworld().getSharedSpawnPos();
                testPlayer.teleportTo(server.overworld(), safeOverworld.getX() + 0.5, safeOverworld.getY(), safeOverworld.getZ() + 0.5, 0, 0);
                // Round-trip an actual pre-existing base from the copied save, retaining its generator and blocks.
                for (var record : PersonalSpaceSavedData.get(server).allSpaces()) {
                    if (!PersonalSpaceSavedData.SpaceRecord.LEGACY_VERSION.equals(record.generationVersion())) continue;
                    var old = PrivateSpaceManager.getOrLoadSpace(server, record);
                    if (old == null) throw new IllegalStateException("Legacy fixture failed to load");
                    var saved = PersonalSpaceSavedData.get(server).getResumePosition(record.owner());
                    BlockPos sample = saved.filter(p -> p.dimension().location().equals(record.dimension()))
                            .map(p -> BlockPos.containing(p.x(), p.y(), p.z())).orElse(old.getSharedSpawnPos());
                    long before = checksum(old, sample);
                    PersonalDimensionRegistry.from(server).unloadDynamicDimension(record.dimension());
                    var after = PrivateSpaceManager.getOrLoadSpace(server, record);
                    if (checksum(after, sample) != before) throw new IllegalStateException("Legacy blocks changed after restore");
                    UniversalToolMod.LOGGER.info("VISUAL_TEST legacy base {} preserved; generator={}", record.dimension(), after.getChunkSource().getGenerator().getClass().getSimpleName());
                }
                var biome = server.overworld().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.CHERRY_GROVE);
                var generator = new FiniteIslandChunkGenerator(new FixedBiomeSource(biome), PrivateSpaceEnvironment.CHERRY_GROVE, 123456L, 512);
                visualGenerator = generator;
                var record = new PersonalSpaceSavedData.SpaceRecord(UUID.nameUUIDFromBytes("visual-island".getBytes()), ID,
                        PrivateSpaceEnvironment.CHERRY_GROVE, "s", "h", FiniteIslandChunkGenerator.VERSION, 512, 123456L);
                var existing = PersonalSpaceSavedData.get(server).getSpace(record.owner());
                PersonalSpaceSavedData.get(server).addSpace(record);
                var level = PrivateSpaceManager.getOrLoadSpace(server, record);
                PrivateSpaceManager.configureLevel(level, PrivateSpaceEnvironment.CHERRY_GROVE, existing.isEmpty());
                if (Boolean.getBoolean("universal_tool.islandRestartOnly")) {
                    if (!level.getSharedSpawnPos().equals(new BlockPos(0, 80, 0)) || !level.getBlockState(new BlockPos(400, 90, 0)).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK))
                        throw new IllegalStateException("Full restart failed to restore spawn or exterior build");
                    UniversalToolMod.LOGGER.info("VISUAL_TEST full restart restored finite seed, spawn and exterior build");
                }
                level.setBlockAndUpdate(new BlockPos(400, 90, 0), net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState());
                var spawn = level.getSharedSpawnPos();
                visualGroundY = spawn.getY();
                int inspected = 0;
                for (int x = -239; x <= 241; x += 16)
                    for (int z = -239; z <= 241; z += 16) {
                        if (!generator.containsTerrain(x, z)) continue;
                        if (!level.getBlockState(new BlockPos(x, generator.surfaceY(x, z), z)).is(generator.environment().profile().surface().top()))
                            throw new IllegalStateException("Interior terrain hole at " + x + "," + z);
                        inspected++;
                    }
                UniversalToolMod.LOGGER.info("VISUAL_TEST full-footprint terrain integrity: {} columns passed", inspected);
                Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                for (int i = 0; i < 4; i++)
                    level.setBlockAndUpdate(spawn.offset(i * 2 - 3, 0, 3), ModBlocks.PERSONAL_SPACE_GATE.get()
                            .defaultBlockState().setValue(PersonalSpaceGateBlock.FACING, directions[i]));
                server.overworld().setDayTime(6000);
                var player = server.getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.getInventory().selected = 3;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PERSONAL_SPACE_GATE.get()));
                PersonalDimensionRegistry.from(server).whenClientReady(player, level, () -> {
                    player.teleportTo(level, 0.5, spawn.getY() + 1, -2, 0, 20);
                    ready = true;
                });
            });
        } else if (phase == 2 && ready && client.player != null && ++ticks > 160) {
            if (Boolean.getBoolean("universal_tool.islandRestartOnly")) {
                client.stop();
                phase = 99;
                return;
            }
            capture("gateway_world.png");
            phase = 3;
            ticks = 0;
            client.setScreen(new InventoryScreen(client.player));
        } else if (phase == 3 && ++ticks > 50) {
            capture("gateway_inventory.png");
            phase = 4;
            ticks = 0;
            client.setScreen(null);
            client.options.fov().set(110);
            client.options.hideGui = true;
            var server = client.getSingleplayerServer();
            var id = client.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.teleportTo(player.serverLevel(), 0, 245, 0, 0, 90);
            });
        } else if (phase == 4 && ++ticks > 300) {
            int missing = 0, total = 0;
            for (int x = -239; x <= 241; x += 16)
                for (int z = -239; z <= 241; z += 16) {
                    if (!visualGenerator.containsTerrain(x, z)) continue;
                    total++;
                    if (!client.level.getBlockState(new BlockPos(x, visualGenerator.surfaceY(x, z), z)).is(visualGenerator.environment().profile().surface().top()))
                        missing++;
                }
            UniversalToolMod.LOGGER.info("VISUAL_TEST client terrain columns {} total, {} missing; {}", total, missing, client.levelRenderer.getSectionStatistics());
            capture("island_aerial.png");
            phase = 44;
            ticks = 0;
            client.smartCull = false;
        } else if (phase == 44 && ++ticks > 100) {
            capture("island_aerial_no_cull.png");
            phase = 5;
            ticks = 0;
            client.options.hideGui = false;
            client.setScreen(new PersonalSpaceScreen(BlockPos.ZERO, PersonalSpaceScreen.PAGE_REALMS,
                    client.player.getName().getString(), "superflat", ""));
        } else if (phase == 5 && ++ticks > 50) {
            capture("island_creation_ui.png");
            sweepIndex = 0;
            sweepClicked = false;
            ticks = 0;
            phase = 6;
        } else if (phase == 6) {
            // Walk every landscape through the real selector, one click each.
            var values = PrivateSpaceEnvironment.values();
            if (!sweepClicked) {
                selectChip(values[sweepIndex]);
                sweepClicked = true;
                ticks = 0;
            } else if (++ticks > 20) {
                capture("island_ui_" + values[sweepIndex].id() + ".png");
                if (++sweepIndex >= values.length) {
                    phase = 65;
                    ticks = 0;
                    client.setScreen(null);
                    client.options.hideGui = true;
                    var server = client.getSingleplayerServer();
                    var playerId = client.player.getUUID();
                    server.execute(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player == null) return;
                        var level = player.serverLevel();
                        var spawn = level.getSharedSpawnPos();
                        var gateA = spawn.offset(-1, 0, 3);
                        var gateB = spawn.offset(1, 0, 3);
                        PrivateSpaceManager.openPortalAtGate(level, gateA,
                                org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE, "");
                        PrivateSpaceManager.openPortalAtGate(level, gateB,
                                org.gwfx.universaltool.network.PersonalSpaceActionPayload.ACTION_ENTER_REALM, "obsidian_forge");
                        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 0.3, spawn.getZ() + 0.3, 0.0F, 8.0F);
                    });
                } else sweepClicked = false;
            }
        } else if (phase == 65 && ++ticks > 30) {
            capture("opened_stargate_portal.png");
            phase = 7;
            ticks = 0;
            var server = client.getSingleplayerServer();
            var playerId = client.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) return;
                ResourceLocation pocketId = ResourceLocation.fromNamespaceAndPath(
                        UniversalToolMod.MODID, "visual_pocket_" + CAPTURE_RUN_ID);
                activePreviewDimId = pocketId;
                var biome = server.overworld().registryAccess().lookupOrThrow(Registries.BIOME)
                        .getOrThrow(net.minecraft.world.level.biome.Biomes.THE_VOID);
                var pocketLevel = PersonalDimensionRegistry.from(server).createDynamicDimension(
                        pocketId,
                        new org.gwfx.universaltool.space.StarryPocketChunkGenerator(new FixedBiomeSource(biome)),
                        PrivateSpaceManager.buildPrivatePocketDimensionType(server.overworld().dimensionType()));
                PrivateSpaceManager.configureLevel(pocketLevel, PrivateSpaceEnvironment.SUPERFLAT, true);
                PersonalDimensionRegistry.from(server).whenClientReady(player, pocketLevel, () ->
                        player.teleportTo(pocketLevel, 0.5, 67.5, -8.5, -20.0F, 24.0F));
            });
        } else if (phase == 7 && ++ticks > 120) {
            capture("private_pocket_world.png");
            UniversalToolMod.LOGGER.info("VISUAL_TEST all captures completed");
            client.stop();
            phase = 11;
        }
    }

    /** Clicks an environment chip the way a player would, proving the selector answers one click per landscape. */
    private static void selectChip(PrivateSpaceEnvironment environment) {
        clickButton("EnvironmentChip", environment.ordinal(), "select " + environment.id());
    }

    private static void clickButton(String simpleName, int index, String action) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) throw new IllegalStateException("No screen is open to " + action);
        var matches = new java.util.ArrayList<net.minecraft.client.gui.layouts.LayoutElement>();
        for (var child : screen.children())
            if (child.getClass().getSimpleName().equals(simpleName)
                    && child instanceof net.minecraft.client.gui.layouts.LayoutElement element) matches.add(element);
        if (matches.size() <= index)
            throw new IllegalStateException("Expected a " + simpleName + " to " + action + ", found " + matches.size());
        var target = matches.get(index);
        if (!screen.mouseClicked(target.getX() + target.getWidth() / 2.0, target.getY() + target.getHeight() / 2.0, 0))
            throw new IllegalStateException("Click was not handled: " + action);
        UniversalToolMod.LOGGER.info("VISUAL_TEST {} by clicking {}", action, simpleName);
    }

    private static void capture(String file) {
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, file, client.getMainRenderTarget(), message ->
                UniversalToolMod.LOGGER.info("VISUAL_TEST screenshot {}: {}", file, message.getString()));
    }

    private static long checksum(net.minecraft.server.level.ServerLevel level, BlockPos sample) {
        var chunk = level.getChunk(sample.getX() >> 4, sample.getZ() >> 4);
        long hash = 1;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
                    hash = 31 * hash + chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + dx, y, chunk.getPos().getMinBlockZ() + dz)).hashCode();
        return hash;
    }

    // ------------------------------------------------- 8-realm preview capture

    private static boolean isCaptureRealmPreviewsMode() {
        if (Boolean.getBoolean("universal_tool.captureRealmPreviews")) return true;
        if ("1".equals(System.getenv("UNIVERSAL_TOOL_CAPTURE_REALMS"))) return true;
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return Files.exists(gameDir.resolve("capture_realms.flag"));
    }

    private static void tickCaptureRealmPreviews(Minecraft client) {
        client.options.pauseOnLostFocus = false;
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().stop();

        if (capturePhase == 0 && !(client.screen instanceof TitleScreen) && client.screen != null && ++captureTicks > 80) {
            client.setScreen(new TitleScreen());
        }
        if (capturePhase == 0 && client.screen instanceof TitleScreen) {
            capturePhase = 1;
            captureTicks = 0;
            client.createWorldOpenFlows().openWorld("island-visual", () -> {
                UniversalToolMod.LOGGER.error("CAPTURE_REALMS unable to open isolated save");
                client.stop();
            });
            return;
        }

        if (capturePhase == 1 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
            capturePhase = 2;
            captureState = 0;
            captureIndex = 0;
            captureTicks = 0;
            client.options.renderDistance().set(12);
            client.options.fov().set(78);
            client.options.hideGui = true;
            client.options.broadcastOptions();
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                server.overworld().setDayTime(6000);
                server.overworld().setWeatherParameters(100000, 0, false, false);
            });
            return;
        }

        if (capturePhase != 2 || client.getSingleplayerServer() == null || client.player == null) return;

        PrivateSpaceEnvironment[] envs = PrivateSpaceEnvironment.values();
        boolean capturingPrivatePocket = captureIndex == envs.length;
        if (captureIndex > envs.length) {
            client.stop();
            capturePhase = 99;
            return;
        }

        var server = client.getSingleplayerServer();
        if (capturingPrivatePocket) {
            if (captureState == 0) {
                captureState = 1;
                captureTicks = 0;
                captureReady = false;
                ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath(
                        UniversalToolMod.MODID, "visual_preview_private_pocket_" + CAPTURE_RUN_ID);
                activePreviewDimId = dimId;
                var playerId = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) return;
                    Holder<Biome> biome = server.overworld().registryAccess().lookupOrThrow(Registries.BIOME)
                            .getOrThrow(Biomes.THE_VOID);
                    var level = PersonalDimensionRegistry.from(server).createDynamicDimension(
                            dimId,
                            new org.gwfx.universaltool.space.StarryPocketChunkGenerator(new FixedBiomeSource(biome)),
                            PrivateSpaceManager.buildPrivatePocketDimensionType(server.overworld().dimensionType()));
                    PrivateSpaceManager.configureLevel(level, PrivateSpaceEnvironment.SUPERFLAT, true);
                    camX = 0.5;
                    camY = 67.5;
                    camZ = -8.5;
                    camYaw = -28.0F;
                    camPitch = 14.0F;
                    targetLookX = 0;
                    targetLookY = 64;
                    targetLookZ = 0;
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    PersonalDimensionRegistry.from(server).whenClientReady(player, level, () -> {
                        player.teleportTo(level, camX, camY, camZ, camYaw, camPitch);
                        captureReady = true;
                    });
                });
                return;
            }
            client.mouseHandler.releaseMouse();
            client.setScreen(null);
            if (!captureReady || client.level == null || !client.level.dimension().location().equals(activePreviewDimId)) {
                return;
            }
            client.player.setDeltaMovement(0, 0, 0);
            client.player.setPos(camX, camY, camZ);
            client.player.setYRot(camYaw);
            client.player.yRotO = camYaw;
            client.player.setXRot(camPitch);
            client.player.xRotO = camPitch;
            captureTicks++;
            if (captureTicks == 30) {
                client.levelRenderer.allChanged();
            }
            if (captureTicks >= 100) {
                Path outputPath = resolvePreviewOutputPath(client, "private_pocket");
                boolean saved = PhotoManager.capturePanoramicToFile(outputPath, 544, 136);
                UniversalToolMod.LOGGER.info("CAPTURE_REALMS saved private_pocket -> {} (ok={})", outputPath, saved);
                ResourceLocation dimToUnload = activePreviewDimId;
                var playerId = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        var safe = server.overworld().getSharedSpawnPos();
                        player.teleportTo(server.overworld(), safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 0, 0);
                    }
                    PersonalDimensionRegistry.from(server).unloadDynamicDimension(dimToUnload);
                });
                captureIndex++;
                client.stop();
                capturePhase = 99;
            }
            return;
        }

        PrivateSpaceEnvironment env = envs[captureIndex];

        if (captureState == 0) {
            captureState = 1;
            captureTicks = 0;
            captureReady = false;
            ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath(
                    UniversalToolMod.MODID, "visual_preview_" + env.id() + "_" + CAPTURE_RUN_ID);
            activePreviewDimId = dimId;
            var playerId = client.player.getUUID();
            long seed = findScenicSeed(env);

            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) return;
                ResourceLocation biomeId = env.profile().biome().location();
                Holder<Biome> biome = server.overworld().registryAccess().lookupOrThrow(Registries.BIOME)
                        .getOrThrow(ResourceKey.create(Registries.BIOME, biomeId));
                var generator = new FiniteIslandChunkGenerator(new FixedBiomeSource(biome), env, seed, 512);
                var registry = PersonalDimensionRegistry.from(server);
                var level = registry.createDynamicDimension(dimId, generator,
                        PrivateSpaceManager.buildDimensionType(server.overworld().dimensionType(), env));
                if (level == null) {
                    throw new IllegalStateException("Failed to create preview dimension " + dimId);
                }
                PrivateSpaceManager.configureLevel(level, env, true);
                BlockPos spawn = level.getSharedSpawnPos();

                double cx;
                double cy;
                double cz;
                double tx;
                double ty;
                double tz;
                if (env == PrivateSpaceEnvironment.SKY_ISLANDS) {
                    cx = 32.0;
                    cy = spawn.getY() + 16.0;
                    cz = -22.0;
                    tx = 125.0;
                    ty = spawn.getY() + 1.0;
                    tz = 8.0;
                } else if (env == PrivateSpaceEnvironment.SUPERFLAT) {
                    cx = -9.0;
                    cy = spawn.getY() + 6.5;
                    cz = -16.0;
                    tx = 8.0;
                    ty = spawn.getY() + 1.5;
                    tz = 40.0;
                } else if (env == PrivateSpaceEnvironment.AURORA_GLACIER) {
                    cx = -7.0;
                    cy = spawn.getY() + 12.0;
                    cz = -14.0;
                    tx = 12.0;
                    ty = spawn.getY() + 2.5;
                    tz = 28.0;
                } else {
                    cx = -10.0;
                    cy = spawn.getY() + 12.0;
                    cz = -15.0;
                    tx = 12.0;
                    ty = spawn.getY() + 2.5;
                    tz = 26.0;
                }
                double dx = tx - cx;
                double dy = ty - cy;
                double dz = tz - cz;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));

                camX = cx;
                camY = cy;
                camZ = cz;
                camYaw = yaw;
                camPitch = pitch;
                targetLookX = (int) Math.round(tx);
                targetLookY = (int) Math.round(ty);
                targetLookZ = (int) Math.round(tz);

                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                registry.whenClientReady(player, level, () -> {
                    player.teleportTo(level, cx, cy, cz, yaw, pitch);
                    captureReady = true;
                });
            });
            return;
        }

        // captureState == 1: wait for chunks to arrive & mesh
        client.mouseHandler.releaseMouse();
        client.setScreen(null);
        if (!captureReady || client.level == null
                || !client.level.dimension().location().equals(activePreviewDimId)) {
            return;
        }

        client.player.setDeltaMovement(0, 0, 0);
        client.player.input.leftImpulse = 0;
        client.player.input.forwardImpulse = 0;
        client.player.input.jumping = false;
        client.player.setPos(camX, camY, camZ);
        client.player.setYRot(camYaw);
        client.player.yRotO = camYaw;
        client.player.setXRot(camPitch);
        client.player.xRotO = camPitch;

        captureTicks++;
        if (captureTicks == 30) {
            client.levelRenderer.allChanged();
        }
        boolean targetLoaded = client.level.isLoaded(new BlockPos(targetLookX, targetLookY, targetLookZ));
        if (captureTicks >= 110 && (targetLoaded || captureTicks >= 180)) {
            Path outputPath = resolvePreviewOutputPath(client, env.id());
            boolean saved = PhotoManager.capturePanoramicToFile(outputPath, 544, 136);
            UniversalToolMod.LOGGER.info("CAPTURE_REALMS saved {} -> {} (ok={})", env.id(), outputPath, saved);
            if (!saved) {
                throw new IllegalStateException("Failed to save realm preview for " + env.id());
            }

            ResourceLocation dimToUnload = activePreviewDimId;
            var playerId = client.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    var safe = server.overworld().getSharedSpawnPos();
                    player.teleportTo(server.overworld(), safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 0, 0);
                }
                PersonalDimensionRegistry.from(server).unloadDynamicDimension(dimToUnload);
            });

            captureIndex++;
            captureState = 0;
            captureTicks = 0;
        }
    }

    private static Path resolvePreviewOutputPath(Minecraft client, String id) {
        Path cur = client.gameDirectory.toPath().toAbsolutePath().normalize();
        for (int i = 0; i < 6 && cur != null; i++) {
            Path assetsDir = cur.resolve("src/main/resources/assets/universal_tool");
            if (Files.isDirectory(assetsDir)) {
                return assetsDir.resolve("textures/gui/previews/env_" + id + ".png");
            }
            cur = cur.getParent();
        }
        return client.gameDirectory.toPath().resolve("previews/env_" + id + ".png");
    }

    /** Picks a deterministic seed whose procedural landmarks sit nicely inside the foreground view. */
    private static long findScenicSeed(PrivateSpaceEnvironment env) {
        for (long candidate = 1000L; candidate < 5000L; candidate++) {
            if (env == PrivateSpaceEnvironment.SUPERFLAT) {
                long h1 = mixSeed(candidate ^ (0L * 341873128712L) ^ (48L * 132897987541L));
                long h2 = mixSeed(candidate ^ (16L * 341873128712L) ^ (48L * 132897987541L));
                if ((h1 & 63) == 0 && (h2 & 63) == 0) return candidate;
            } else if (env == PrivateSpaceEnvironment.SKY_ISLANDS) {
                long h1 = mixSeed(candidate ^ (0L * 341873128712L) ^ (32L * 132897987541L));
                long h2 = mixSeed(candidate ^ (16L * 341873128712L) ^ (16L * 132897987541L));
                if ((h1 & 15) == 0 && (h2 & 15) == 0) return candidate;
            } else {
                long h1 = mixSeed(candidate ^ (0L * 341873128712L) ^ (16L * 132897987541L));
                long h2 = mixSeed(candidate ^ (16L * 341873128712L) ^ (32L * 132897987541L));
                long h3 = mixSeed(candidate ^ (-16L * 341873128712L) ^ (32L * 132897987541L));
                if ((h1 & 7) == 0 && (h2 & 7) == 0 && (h3 & 7) == 0) return candidate;
            }
        }
        return 424242L + env.ordinal() * 1337L;
    }

    private static long mixSeed(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
