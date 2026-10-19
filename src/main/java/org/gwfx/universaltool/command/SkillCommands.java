package org.gwfx.universaltool.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillManager;

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
                .then(Commands.literal("unlockall")
                        .executes(ctx -> executeUnlockAll(ctx.getSource())))
                .then(Commands.literal("max")
                        .executes(ctx -> executeMax(ctx.getSource()))));
    }

    private static int executeUnlockAll(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.universal_tool.skills.player_only"));
            return 0;
        }

        PlayerSkillData data = SkillManager.data(player);
        int unlockedCount = data.unlockAllSkills(skill -> {
            player.displayClientMessage(Component.translatable("message.universal_tool.skills.unlocked",
                    Component.translatable(skill.translationKey())), false);
        });
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
