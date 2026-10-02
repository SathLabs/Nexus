package dev.satherov.nexus.api.registry.client;

import dev.satherov.nexus.api.registry.menu.MenuBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

///
/// A factory of the screen of a menu, called on the client each time the menu opens.
///
/// @param <MENU>   The type of the menu.
/// @param <SCREEN> The type of the screen.
///
/// @see MenuBuilder#screen(ClientOnly)
///
@FunctionalInterface
public interface ScreenFactory<MENU extends AbstractContainerMenu, SCREEN> {

    ///
    /// Creates the screen of the given menu.
    ///
    /// @param menu      The menu.
    /// @param inventory The inventory of the player.
    /// @param title     The title of the menu.
    ///
    /// @return The created screen.
    ///
    SCREEN create(MENU menu, Inventory inventory, Component title);
}
