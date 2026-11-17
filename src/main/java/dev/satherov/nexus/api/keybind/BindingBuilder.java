package dev.satherov.nexus.api.keybind;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.keybind.carry.Carry1;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import net.minecraft.server.level.ServerPlayer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    /// The codecs of the carried values, in the order the values are read.
    ///
    private List<NexusCodec<?, ?>> codecs = List.of();
    
    ///
    /// The readers of the carried values on the client, one per codec.
    ///
    private List<ClientOnly<? extends Supplier<?>>> readers = List.of();
    
    ///
    /// The server action over the player and the carried values, or `null` if none is set.
    ///
    private @Nullable BiConsumer<ServerPlayer, List<Object>> serverAction;
    
    ///
    /// If the key sends its going down and up to the server.
    ///
    private boolean syncHeld;
    
    ///
    /// The version neoforge negotiates for the payload of the key.
    ///
    /// Defaults to `1`.
    ///
    private String version = "1";
    
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
    /// Nexus only checks that the player is still connected.
    ///
    /// @param action The server action, called with the player whose key fired.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If a server action is already set, here or through a carry stage.
    ///
    /// @see #activation(Activation)
    ///
    @CanIgnoreReturnValue
    public SELF onServer(Consumer<ServerPlayer> action) {
        return this.finishCarry(List.of(), List.of(), (player, _) -> action.accept(player));
    }
    
    ///
    /// Starts the values that the key carries to the server, read on the client each time the key fires.
    ///
    /// The reader runs on the client thread after the client action, and only while the client is connected to a server that has the key.
    /// If it returns `null`, nothing is sent to the server for that firing.
    /// If it returns a mutable value such as an item stack, then the value must be a copy or a later change to it may reach the server.
    ///
    /// The carried values are set on the builder together with the server action of the last stage,
    /// and a chain without a server action sets neither.
    ///
    /// @param codec  The codec the value is sent with.
    /// @param reader The reader of the value on the client.
    /// @param <A>    The type of the value.
    ///
    /// @return The first carry stage.
    ///
    /// @see Carry1#onServer(BiConsumer)
    ///
    public <A> Carry1<SELF, A> carry(NexusCodec<A, ?> codec, ClientOnly<Supplier<@Nullable A>> reader) {
        return new Carry1<>(this.self(), codec, reader);
    }
    
    ///
    /// Sets the carried values and the server action over them.
    ///
    /// Should only ever be called from [#onServer(Consumer)] and the carry stages.
    ///
    /// @param codecs  The codecs of the carried values, in the order the values are read.
    /// @param readers The readers of the carried values on the client, one per codec.
    /// @param action  The server action, called with the player whose key fired and the decoded values in the order of their codecs.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If a server action is already set.
    ///
    @ApiStatus.Internal
    public SELF finishCarry(List<NexusCodec<?, ?>> codecs, List<ClientOnly<? extends Supplier<?>>> readers, BiConsumer<ServerPlayer, List<Object>> action) {
        if (this.serverAction != null) {
            throw new IllegalStateException("Key '" + this.name + "' of mod '" + this.keybinds.getMod().getModId() + "' already has a server action");
        }
        
        this.codecs = List.copyOf(codecs);
        this.readers = List.copyOf(readers);
        this.serverAction = action;
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
    /// Sets the version of the payload the key sends to the server on.
    ///
    /// If the carried values change between two releases of the mod, then the version must change too or a firing will disconnect the player.
    /// If the client and the server both have the key with different versions, neoforge will refuse the player at join, even though the payload is optional.
    ///
    /// Defaults to `1`.
    ///
    /// @param version The version neoforge negotiates for the payload of the key.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalArgumentException If the version is blank.
    ///
    @CanIgnoreReturnValue
    public SELF version(String version) {
        if (version.isBlank()) {
            throw new IllegalArgumentException("Key '" + this.name + "' of mod '" + this.keybinds.getMod().getModId() + "' has a blank version");
        }
        
        this.version = version;
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
    /// - If the key is in a category of the mod's namespace that the mod did not declare.
    /// - If the key sends to the server on the same payload as another key of the mod.
    ///
    public Keybind register() {
        return this.keybinds.register(this.name, this.createDefinition(), this.activation, this.clientAction, this.codecs, this.readers, this.serverAction, this.syncHeld, this.version);
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
