package com.tio.instaff.inspection;

import com.tio.instaff.InStaff;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry container for In-Staff inspection menus.
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, InStaff.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<InvseeMenu>> INVSEE =
            MENUS.register("invsee", () -> IMenuTypeExtension.create(InvseeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EnderseeMenu>> ENDERSEE =
            MENUS.register("endersee", () -> IMenuTypeExtension.create(EnderseeMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
