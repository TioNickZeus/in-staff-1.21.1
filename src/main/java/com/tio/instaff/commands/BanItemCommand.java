package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tio.instaff.protection.BanItemManager;
import com.tio.instaff.protection.BanItemMode;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Restricted item management command (/banitem).
 * Subcommands: add <itemId> [mode], remove <itemId>, list, check [itemId].
 * Requires OP permission level 2.
 */
public final class BanItemCommand {

    private BanItemCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("banitem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ITEM.keySet(), builder))
                                .executes(ctx -> executeAdd(ctx.getSource(), ResourceLocationArgument.getId(ctx, "item"), BanItemMode.TOTAL))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(BanItemMode.values()).map(Enum::name), builder))
                                        .executes(ctx -> executeAdd(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "item"),
                                                BanItemMode.fromString(StringArgumentType.getString(ctx, "mode")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        BanItemManager.getInstance().getAllBannedItems().keySet(), builder))
                                .executes(ctx -> {
                                    String itemId = ResourceLocationArgument.getId(ctx, "item").toString();
                                    if (!BanItemManager.getInstance().unbanItem(itemId)) {
                                        ctx.getSource().sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.banitem.not_banned", itemId));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.banitem.removed", itemId), true);
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Map<String, BanItemMode> banned = BanItemManager.getInstance().getAllBannedItems();
                            String formatted = banned.isEmpty()
                                    ? LocalizationHelper.getRawTranslation("instaff.common.none")
                                    : banned.entrySet().stream()
                                            .map(e -> e.getKey() + " (" + e.getValue().name() + ")")
                                            .collect(Collectors.joining(", "));

                            ctx.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.banitem.list", banned.size(), formatted), false);
                            return 1;
                        }))
                .then(Commands.literal("check")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (source.getEntity() instanceof ServerPlayer player) {
                                ItemStack stack = player.getMainHandItem();
                                if (stack.isEmpty()) {
                                    source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.banitem.hold_item"));
                                    return 0;
                                }
                                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                                return executeCheck(source, key.toString());
                            } else {
                                source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.banitem.console_specify"));
                                return 0;
                            }
                        })
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ITEM.keySet(), builder))
                                .executes(ctx -> executeCheck(ctx.getSource(), ResourceLocationArgument.getId(ctx, "item").toString())))));
    }

    private static int executeAdd(CommandSourceStack source, ResourceLocation itemKey, BanItemMode mode) {
        if (!BuiltInRegistries.ITEM.containsKey(itemKey)) {
            source.sendFailure(LocalizationHelper.getPrefixedMessage("instaff.command.banitem.unknown_item", itemKey.toString()));
            return 0;
        }

        String itemId = itemKey.toString();
        BanItemManager.getInstance().banItem(itemId, mode);
        source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.banitem.added", itemId, mode.name()), true);
        return 1;
    }

    private static int executeCheck(CommandSourceStack source, String itemId) {
        BanItemMode mode = BanItemManager.getInstance().getMode(itemId);
        if (mode != null) {
            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.banitem.check", itemId, mode.name()), false);
        } else {
            source.sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.banitem.not_banned", itemId), false);
        }
        return 1;
    }
}
