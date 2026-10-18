package dev.satherov.nexus.api.registry.menu;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;

import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

///
/// An entry of one menu type that a [MenuBuilder] registered.
///
/// @param <MENU> The type of the menu.
///
public final class MenuEntry<MENU extends AbstractContainerMenu> extends RegistryEntry<MenuType<?>, MenuType<MENU>> implements Translatable {
    
    ///
    /// The translation of the title.
    ///
    private final Translation translation;
    
    ///
    /// Should only ever be called from [MenuBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the menu type.
    /// @param builder     The builder that registers the menu type.
    /// @param translation The translation of the title.
    ///
    @ApiStatus.Internal
    public MenuEntry(ResourceKey<MenuType<?>> key, MenuBuilder<MENU, ?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// Opens the menu that the given constructor creates for the given player under the title of this entry.
    ///
    /// The factory of the menu type will receive `null` instead of the extra data on the client.
    ///
    /// @param player The player to open the menu for.
    /// @param menu   The constructor of the menu on the server.
    ///
    public void open(ServerPlayer player, MenuConstructor menu) {
        this.open(player, menu, Consumers.nop());
    }
    
    ///
    /// Opens the menu that the given constructor creates for the given player under the title of this entry
    /// and writes the extra data that the factory of the menu type reads on the client.
    ///
    /// If the writer doesn't write anything, the factory will receive `null` instead of the byte buffer.
    ///
    /// @param player The player to open the menu for.
    /// @param menu   The constructor of the menu on the server.
    /// @param extra  The writer of the extra data, called once with the buffer that the factory reads.
    ///
    public void open(ServerPlayer player, MenuConstructor menu, Consumer<RegistryFriendlyByteBuf> extra) {
        player.openMenu(new SimpleMenuProvider(menu, this.component()), extra);
    }
    
    ///
    /// The `container.<modid>.<name>` translation.
    ///
    /// @return The translation of the title.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
