package dev.satherov.nexus.api.keybind;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// A builder of one key of a mod.
///
/// @param <SELF> The type of this builder.
///
public abstract sealed class BindingBuilder<SELF extends BindingBuilder<SELF>> permits KeybindBuilder {
    
    ///
    /// The keybinds the key is registered in.
    ///
    private final Keybinds keybinds;
    
    ///
    /// The name the key was declared with.
    ///
    private final String name;
    
    ///
    /// The activation the key fires through.
    ///
    private Activation activation = Activation.press();
    
    ///
    /// The client action, or `null` if none is set.
    ///
    private @Nullable ClientOnly<Runnable> clientAction;
    
    ///
    /// Starts the key of the given name in the given keybinds.
    ///
    /// @param keybinds The keybinds the key is registered in.
    /// @param name     The name of the key.
    ///
    protected BindingBuilder(Keybinds keybinds, String name) {
        this.keybinds = keybinds;
        this.name = name;
    }
    
    ///
    /// Sets when the key fires.
    ///
    /// The key fires through [Activation#press()] unless this is called.
    ///
    /// @param activation The activation the key fires through.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public SELF activation(Activation activation) {
        this.activation = activation;
        return this.self();
    }
    
    ///
    /// Runs the given action on the client thread each time the key fires.
    ///
    /// The action also runs while the client is connected to a server without the mod.
    ///
    /// @param action The client action.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If a client action is already set.
    ///
    /// @see #activation(Activation)
    ///
    @CanIgnoreReturnValue
    public SELF onClient(ClientOnly<Runnable> action) {
        if (this.clientAction != null) {
            throw new IllegalStateException("Key '" + this.name + "' of mod '" + this.keybinds.getMod().getModId() + "' already has a client action");
        }
        
        this.clientAction = action;
        return this.self();
    }
    
    ///
    /// Registers the key and declares its translation key `key.<modid>.<name>`.
    ///
    /// @return The registered key.
    ///
    /// @throws IllegalStateException If the mod already registered a key of that name, or if the key mappings were already registered.
    ///
    public Keybind register() {
        return this.keybinds.register(Objects.requireNonNull(this.createDefinition()), this.activation, this.clientAction);
    }
    
    ///
    /// This builder as its own type.
    ///
    private SELF self() {
        //noinspection unchecked Every builder is the type it declares as its own.
        return (SELF) this;
    }
    
    ///
    /// Creates the definition of the key from what this builder collected.
    ///
    /// @return The created definition, or `null` if the key has no mapping of its own.
    ///
    @ForOverride
    protected abstract Keybind.@Nullable Definition createDefinition();
}
