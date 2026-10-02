package dev.satherov.nexus.api.registry.menu;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.client.ScreenFactory;
import dev.satherov.nexus.internal.registry.EntryRegister;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

///
/// A builder of one menu type.
///
/// @param <MENU>   The type of the menu.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class MenuBuilder<MENU extends AbstractContainerMenu, PARENT> extends EntryBuilder<MenuType<?>, MenuType<MENU>, MenuEntry<MENU>, PARENT, MenuBuilder<MENU, PARENT>> {

    ///
    /// The English title.
    ///
    private final String english;

    ///
    /// The factory of the menu on the client.
    ///
    private final IContainerFactory<MENU> factory;

    ///
    /// The registration of the screen, called on the client with the mod bus and the entry, or `null` if the menu has
    /// no screen.
    ///
    private @Nullable BiConsumer<IEventBus, MenuEntry<MENU>> screen;

    ///
    /// Starts the menu type titled `container.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// If the server doesn't write any extra data when it opens the menu, the factory will receive `null` instead of it.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English title.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the menu, called on the client with the extra data that the server wrote.
    ///
    public MenuBuilder(Registrations registrations, String name, String english, PARENT parent, IContainerFactory<MENU> factory) {
        super(registrations, Registries.MENU, name, parent);
        this.english = english;
        this.factory = factory;
    }

    ///
    /// Registers the given screen for the menu on the client.
    ///
    /// @param screen The factory of the screen, called on the client each time the menu opens.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public <SCREEN extends Screen & MenuAccess<MENU>> MenuBuilder<MENU, PARENT> screen(ClientOnly<ScreenFactory<MENU, SCREEN>> screen) {
        this.checkUnregistered();
        this.screen = (bus, entry) -> ClientDispatch.screen(bus, entry, screen);
        return this;
    }

    ///
    /// Creates the menu type with the factory.
    ///
    /// @return The created menu type.
    ///
    @Override
    protected MenuType<MENU> create() {
        return IMenuTypeExtension.create(this.factory);
    }

    ///
    /// Creates the entry and declares its title `container.<modid>.<name>`, and adds the screen as a client hook if
    /// [#screen(ClientOnly)] was called.
    ///
    /// @param key The key of the menu type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected MenuEntry<MENU> createEntry(ResourceKey<MenuType<?>> key) {
        MenuEntry<MENU> entry = new MenuEntry<>(key, this, this.define("container", this.english));
        BiConsumer<IEventBus, MenuEntry<MENU>> screen = this.screen;
        if (screen != null) {
            EntryRegister<MenuType<?>> register = (EntryRegister<MenuType<?>>) this.getRegistrations().getRegister(Registries.MENU);
            register.addClientHook(bus -> screen.accept(bus, entry));
        }

        return entry;
    }
}
