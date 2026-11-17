package dev.satherov.nexus.api.keybind;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A builder of a key on a mapping that the mod did not declare, such as a mapping of vanilla or of another mod.
///
/// The key, the modifier, the context and the category belong to the mapping's owner.
/// The key is tracked in the context of the mapping:
/// - The context its owner declared, if the owner declared the mapping through Nexus.
/// - [Keybind.Context#IN_SCREEN], if the mapping is in neoforge's GUI conflict context.
/// - [Keybind.Context#IN_GAME] for every other mapping, which includes every mapping of vanilla.
///
/// If the mapping takes no modifier, the key will also be down while the mapping reads down, such as a toggle that is on or a state that another mod set.
/// If such a mapping comes back down as a screen closes with no press of its key, as a toggle that vanilla turns back on does, the key will go down without firing on that tick and its held ticks will count from then.
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
