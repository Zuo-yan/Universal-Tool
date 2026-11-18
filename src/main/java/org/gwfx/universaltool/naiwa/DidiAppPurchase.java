package org.gwfx.universaltool.naiwa;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.november.mcphone.api.cost.IAppPriceProvider;
import com.november.mcphone.api.cost.ICost;
import com.november.mcphone.core.PhonePlayerData;
import com.november.mcphone.feature.store.PurchasedApps;
import com.november.mcphone.feature.store.net.SyncPurchasedAppsPacket;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class DidiAppPurchase implements IAppPriceProvider {
    public static final ResourceLocation APP_ID = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "didi_daida");
    public static final int SINGLE_PRICE = 8;
    public static final int SQUAD_PRICE = 20;

    @Override
    public Map<ResourceLocation, ICost> prices() {
        return Map.of(APP_ID, ICost.of(Items.EMERALD, 32));
    }

    static boolean isOwned(ServerPlayer player) {
        return PhonePlayerData.of(player).purchasedApps().has(APP_ID);
    }

    static boolean grant(ServerPlayer player) {
        PhonePlayerData data = PhonePlayerData.of(player);
        PurchasedApps existing = data.purchasedApps();
        if (existing.has(APP_ID) || existing.isFull()) {
            return false;
        }
        PurchasedApps updated = existing.with(APP_ID);
        data.setPurchasedApps(updated);
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new SyncPurchasedAppsPacket(updated));
        }
        return true;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ut")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("didi").then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            if (!grant(target)) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.universal_tool.didi.grant_failed", target.getDisplayName()));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.universal_tool.didi.granted", target.getDisplayName()), true);
                            return 1;
                        })))));
    }

    @SubscribeEvent
    public static void migrateLocalInstall(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (!server.isDedicatedServer() && server.isSingleplayerOwner(player.getGameProfile())
                && !isOwned(player) && hasLocalInstall(server)) {
            grant(player);
        }
    }

    private static boolean hasLocalInstall(MinecraftServer server) {
        // MCphone 1.12 uses the sanitized save folder name for the local installation file.
        String save = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "world_" + save.substring(0, Math.min(save.length(), 64));
        Path state = FMLPaths.GAMEDIR.get().resolve("mcphone/data/installed").resolve(key + ".json");
        if (!Files.isRegularFile(state)) {
            return false;
        }
        try (var reader = Files.newBufferedReader(state)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonElement installed = root.getAsJsonObject().get("installed");
            if (installed != null && installed.isJsonArray()) {
                for (JsonElement value : installed.getAsJsonArray()) {
                    if (value.isJsonPrimitive() && APP_ID.toString().equals(value.getAsString())) {
                        return true;
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            UniversalToolMod.LOGGER.warn("Could not read local MCphone installations for {}", key, e);
        }
        return false;
    }
}
