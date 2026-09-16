package com.tio.instaff.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.tio.instaff.InStaff;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Event subscriber for registering all In-Staff administrative and moderation commands.
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class ModCommands {

    private ModCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        StaffCommand.register(dispatcher);
        PunishCommands.register(dispatcher);
        MaintenanceCommand.register(dispatcher);
        WhitelistCommand.register(dispatcher);
        InvseeCommand.register(dispatcher);
        BanItemCommand.register(dispatcher);
        HistoryCommand.register(dispatcher);
        PlaytimeCommand.register(dispatcher);

        InStaff.LOGGER.info("Registered all In-Staff commands.");
    }
}
