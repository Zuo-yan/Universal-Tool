package org.gwfx.universaltool.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillManager;

/**
 * 模组技能模块管理指令：
 * <ul>
 *   <li>{@code /ut skill on|off|status} — 全存档统一开启/关闭/查询技能模块</li>
 *   <li>{@code /ut unlockall} — 解锁全部 32 技能学习资格</li>
 *   <li>{@code /ut max} — 直接满级（Lv.50 星穹主宰）并学满全部 32 技能</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class SkillCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerBranches(dispatcher, Commands.literal("ut"));
    }

    private static void registerBranches(CommandDispatcher<CommandSourceStack> dispatcher,
                                         LiteralArgumentBuilder<CommandSourceStack> root) {
        dispatcher.register(root
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("skill")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("on")
                                .executes(ctx -> executeSetEnabled(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> executeSetEnabled(ctx.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource()))))
                .then(Commands.literal("unlockall")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeUnlockAll(ctx.getSource())))
                .then(Commands.literal("max")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> executeMax(ctx.getSource()))));
    }

    private static int executeSetEnabled(CommandSourceStack source, boolean enabled) {
        MinecraftServer server = source.getServer();
        SkillManager.setSystemEnabled(server, enabled);
        String key = enabled
                ? "command.universal_tool.skills.enabled_success"
                : "command.universal_tool.skills.disabled_success";
        source.sendSuccess(() -> Component.translatable(key), true);
        return 1;
    }

    private static int executeStatus(CommandSourceStack source) {
        boolean enabled = SkillManager.isEnabled(source.getServer());
        String key = enabled
                ? "command.universal_tool.skills.status_on"
                : "command.universal_tool.skills.status_off";
        source.sendSuccess(() -> Component.translatable(key), false);
        return enabled ? 1 : 0;
    }

    private static int executeUnlockAll(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.universal_tool.skills.player_only"));
            return 0;
        }

        PlayerSkillData data = SkillManager.data(player);
        int unlockedCount = data.unlockAllSkills(skill ->
                SkillManager.notifySkillUnlocked(player, skill));
        SkillManager.sync(player);

        source.sendSuccess(() -> Component.translatable("command.universal_tool.skills.unlock_all_success",
                unlockedCount), true);
        return unlockedCount;
    }

    private static int executeMax(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.universal_tool.skills.player_only"));
            return 0;
        }
        PlayerSkillData data = SkillManager.data(player);
        int changed = data.maximizeAllSkills();
        SkillManager.sync(player);
        source.sendSuccess(() -> Component.translatable("command.universal_tool.skills.max_success",
                data.level(), data.activeSlotCount(), data.passiveSlotCount()), true);
        return changed;
    }

    private SkillCommands() {
    }
}
