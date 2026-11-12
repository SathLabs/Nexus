package dev.satherov.nexus.api.persistence.saved;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.persistence.saved.SavedScope;
import dev.satherov.nexus.internal.persistence.saved.SavedValueData;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

///
/// A builder that declares one saved value on the persistence of a mod.
///
/// Can be started through [Persistence#serverValue(String, Format, Supplier)] or [Persistence#levelValue(String, Format, Supplier)].
///
/// @param <SCOPE> The type of the scope that holds one value, which is the server or a level.
/// @param <VALUE> The type of the value.
///
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SavedValueBuilder<SCOPE, VALUE> {
    
    ///
    /// The persistence the value is declared on.
    ///
    private final Persistence persistence;
    
    ///
    /// The scope that holds one value.
    ///
    private final SavedScope<SCOPE> scope;
    
    ///
    /// The name of the value.
    ///
    private final String name;
    
    ///
    /// The format the value is saved in.
    ///
    private final Format<VALUE> format;
    
    ///
    /// The supplier of the initial value.
    ///
    private final Supplier<VALUE> initial;
    
    ///
    /// The codec the value is synced with, or `null` if it isn't synced.
    ///
    private @Nullable NexusCodec<VALUE, ? super Access.Registries> codec;
    
    ///
    /// Starts a new builder of a value that is saved once per server.
    ///
    /// Should only ever be called from [Persistence#serverValue(String, Format, Supplier)].
    ///
    /// @param persistence The persistence the value is declared on.
    /// @param name        The name of the value.
    /// @param format      The format the value is saved in.
    /// @param initial     The supplier of the initial value.
    ///
    /// @return The builder of the value.
    ///
    @ApiStatus.Internal
    public static <VALUE> SavedValueBuilder<MinecraftServer, VALUE> server(Persistence persistence, String name, Format<VALUE> format, Supplier<VALUE> initial) {
        return new SavedValueBuilder<>(persistence, SavedScope.SERVER, name, format, initial);
    }
    
    ///
    /// Starts a new builder of a value that is saved once per level.
    ///
    /// Should only ever be called from [Persistence#levelValue(String, Format, Supplier)].
    ///
    /// @param persistence The persistence the value is declared on.
    /// @param name        The name of the value.
    /// @param format      The format the value is saved in.
    /// @param initial     The supplier of the initial value.
    ///
    /// @return The builder of the value.
    ///
    @ApiStatus.Internal
    public static <VALUE> SavedValueBuilder<ServerLevel, VALUE> level(Persistence persistence, String name, Format<VALUE> format, Supplier<VALUE> initial) {
        return new SavedValueBuilder<>(persistence, SavedScope.LEVEL, name, format, initial);
    }
    
    ///
    /// Syncs the value to the players in its scope with the given codec.
    ///
    /// Whenever the value is set or updated, the whole value will be sent to every player in the scope.
    /// A player will get the value of the server on login, and the value of a level whenever it enters that level.
    ///
    /// The payload will be declared on the network of the mod as `<modid>:saved/<name>`.
    /// It is optional, so a player whose connection lacks the payload will not be sent the value.
    ///
    /// @param codec The codec the value is sent with.
    ///
    /// @return The builder for chaining.
    ///
    /// @see SavedValue#getOnClient()
    ///
    @CanIgnoreReturnValue
    public SavedValueBuilder<SCOPE, VALUE> sync(NexusCodec<VALUE, ? super Access.Registries> codec) {
        this.codec = codec;
        return this;
    }
    
    ///
    /// Declares the value and creates its handle.
    ///
    /// @return The handle of the value.
    ///
    /// @throws IllegalStateException If a server already started, if the mod already declared a server or level value with the same name,
    /// or if the value is synced and the payload handler event already ran.
    ///
    public SavedValue<SCOPE, VALUE> register() {
        Identifier id = this.persistence.declare(this.name);
        PayloadBuilder<VALUE> sync = this.codec != null ? this.persistence.getMod().getNetwork().play("saved/" + this.name, this.codec).optional() : null;
        return new SavedValue<>(this.scope, SavedValueData.type(id, this.format, this.initial), this.initial, sync);
    }
}
