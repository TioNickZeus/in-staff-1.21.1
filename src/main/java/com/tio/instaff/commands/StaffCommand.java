package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Hub command for In-Staff (/staff, /instaff).
 * Displays an overview of available administrative tools and moderation commands.
 */
public final class StaffCommand {

    private StaffCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("staff")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.staff.help"), false);
                    return 1;
                });

        dispatcher.register(command);

        // Alias /instaff
        dispatcher.register(Commands.literal("instaff")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> LocalizationHelper.getPrefixedMessage("instaff.command.staff.help"), false);
                    return 1;
                }));
    }
}
