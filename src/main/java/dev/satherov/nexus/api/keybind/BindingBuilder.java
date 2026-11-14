package dev.satherov.nexus.api.keybind;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import net.minecraft.server.level.ServerPlayer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

///
/// A builder of one key of a mod.
///
/// @param <SELF> The type of this builder.
///
public abstract sealed class BindingBuilder<SELF extends BindingBuilder<SELF>> permits KeybindBuilder, MappingBuilder {
    
    ///
    /// The keybinds the key is registered in.
    ///
    private final Keybinds keybinds;
    
    ///
    /// The name the key was declared with, or the mapping name of a key on an existing mapping.
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
    /// The server action over the player and the carried values, or `null` if none is set.
    ///
    private @Nullable BiConsumer<ServerPlayer, List<Object>> serverAction;
    
    ///
    /// If the key sends its going down and up to the server.
    ///
    private boolean syncHeld;
    
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
    /// Runs the given action on the server thread each time the key fires and the firing reaches the server.
    ///
    /// Nexus checks nothing about the player.
    ///
    /// @param action The server action, called with the player whose key fired.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If a server action is already set.
    ///
    /// @see #activation(Activation)
    ///
    @CanIgnoreReturnValue
    public SELF onServer(Consumer<ServerPlayer> action) {
        if (this.serverAction != null) {
            throw new IllegalStateException("Key '" + this.name + "' of mod '" + this.keybinds.getMod().getModId() + "' already has a server action");
        }
        
        this.serverAction = (player, _) -> action.accept(player);
        return this.self();
    }
    
    ///
    /// Sends the key going down and up to the server, so the server can read the key of each player.
    ///
    /// @return The builder for chaining.
    ///
    /// @see Keybind#isDown(ServerPlayer)
    /// @see Keybind#getHeldTicks(ServerPlayer)
    /// @see Keybind#getProgress(ServerPlayer)
    ///
    @CanIgnoreReturnValue
    public SELF syncHeld() {
        this.syncHeld = true;
        return this.self();
    }
    
    ///
    /// Registers the key.
    ///
    /// A key the mod declares itself gets the translation key `key.<modid>.<name>`.
    /// A key without a server action that does not sync its held state sends nothing to the server.
    ///
    /// @return The registered key.
    ///
    /// @throws IllegalStateException - If the mod already registered a key of that name.
    /// - If the key mappings were already registered.
    /// - If the payload handlers event already ran.
    /// - If the key sends to the server on the same payload as another key of the mod.
    ///
    public Keybind register() {
        return this.keybinds.register(this.name, this.createDefinition(), this.activation, this.clientAction, this.serverAction, this.syncHeld);
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
