package dev.satherov.nexus.api.keybind;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A builder of a key on a mapping that the mod did not declare, such as a mapping of vanilla or of another mod.
///
/// The key, the modifier, the context and the category belong to the mapping's owner.
///
/// Can be started through [Keybinds#existing(String)].
///
public final class MappingBuilder extends BindingBuilder<MappingBuilder> {
    
    ///
    /// Should only ever be called from [Keybinds#existing(String)].
    ///
    /// @param keybinds    The keybinds the key is registered in.
    /// @param mappingName The name of the mapping.
    ///
    @ApiStatus.Internal
    public MappingBuilder(Keybinds keybinds, String mappingName) {
        super(keybinds, mappingName);
    }
    
    ///
    /// Will always return `null`.
    ///
    @Override
    protected Keybind.@Nullable Definition createDefinition() {
        return null;
    }
}
