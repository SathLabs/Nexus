package dev.satherov.nexus.api.persistence.saved;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.network.ClientRecipients;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.context.ClientHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.persistence.saved.SavedScope;
import dev.satherov.nexus.internal.persistence.saved.SavedValueData;
import dev.satherov.nexus.internal.persistence.saved.client.SyncedValue;

import net.neoforged.neoforge.network.connection.ConnectionType;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import io.netty.buffer.Unpooled;

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A handle of one value that a mod saves once per server or once per level.
///
/// Each value should be immutable and is only ever replaced whole.
///
/// If the saved value of a scope could not be read, its file will be left as it was.
///
/// Every method other than [#getOnClient()] should only ever be called on the server.
///
/// Can be created through [SavedValueBuilder#register()].
///
/// @param <SCOPE> The type of the scope that holds one value, which is the server or a level.
/// @param <VALUE> The type of the value.
///
public final class SavedValue<SCOPE, VALUE> {
    
    ///
    /// The scope that holds one value.
    ///
    private final SavedScope<SCOPE> scope;
    
    ///
    /// The type of the saved data that holds the value in the data storage of a scope.
    ///
    private final SavedDataType<SavedValueData<VALUE>> type;
    
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
    private final @Nullable NexusCodec<VALUE, ? super Access.Registries> codec;
    
    ///
    /// The payload the value is synced over, or `null` if it isn't synced.
    ///
    private final @Nullable ClientboundPayload<VALUE> payload;
    
    ///
    /// The value on the client, or `null` on a dedicated server or if the value isn't synced.
    ///
    private @Nullable SyncedValue<VALUE> synced;
    
    ///
    /// Should only ever be called from [SavedValueBuilder#register()].
    ///
    /// @param scope   The scope that holds one value.
    /// @param id      The identifier of the value, which is also the path of its file.
    /// @param format  The format the value is saved in.
    /// @param initial The supplier of the initial value.
    /// @param codec   The codec the value is synced with, or `null` if it isn't synced.
    /// @param sync    The builder of the payload the value is synced over, or `null` if it isn't synced.
    ///
    SavedValue(
            SavedScope<SCOPE> scope,
            Identifier id,
            Format<VALUE> format,
            Supplier<VALUE> initial,
            @Nullable NexusCodec<VALUE, ? super Access.Registries> codec,
            @Nullable PayloadBuilder<VALUE> sync
    ) {
        this.scope = scope;
        this.type = SavedValueData.type(id, format);
        this.format = format;
        this.initial = initial;
        this.codec = codec;
        ClientboundPayload<VALUE> payload = sync != null ? sync.toClient(this::openSynced) : null;
        this.payload = payload;
        if (payload != null) {
            scope.sync(id, player -> this.toJoinPacket(payload, player));
        }
    }

    ///
    /// The value of the given scope.
    ///
    /// If the scope doesn't have a saved value, the initial value will be saved and returned.
    ///
    /// @param scope The server or level that holds the value.
    ///
    /// @return The value of the scope.
    ///
    /// @throws NexusCodecException If the scope has a saved value that could not be read,
    /// or if it has none and the initial value could not be encoded with the format.
    ///
    public VALUE get(SCOPE scope) {
        return this.getData(scope).getValue();
    }
    
    ///
    /// Replaces the value of the given scope with the given value and marks it to be saved.
    ///
    /// The value is encoded right away, so a value that the format or the codec it is synced with can't encode is refused here,
    /// rather than when the scope is saved or the value is sent.
    /// If the value is synced, it will be sent to every player in the scope.
    ///
    /// @param scope The server or level that holds the value.
    /// @param value The new value.
    ///
    /// @throws NexusCodecException If the scope has a saved value that could not be read,
    /// or if the value could not be encoded with the format or with the codec it is synced with, in which case the scope keeps its value.
    ///
    public void set(SCOPE scope, VALUE value) {
        SavedValueData<VALUE> data = this.getData(scope);
        RegistryAccess registries = this.scope.registries().apply(scope);
        this.requireSyncable(value, registries);
        data.replace(value, this.format.write(value, registries));
        this.send(value, this.scope.recipients().apply(scope));
    }
    
    ///
    /// The saved data that holds the value of the given scope, created with the initial value if the scope doesn't have a file.
    ///
    /// @param scope The server or level that holds the value.
    ///
    /// @return The saved data of the scope.
    ///
    /// @throws NexusCodecException If the scope has a file that could not be read, or if it has none and the initial value could not be encoded.
    ///
    private SavedValueData<VALUE> getData(SCOPE scope) {
        SavedDataStorage storage = this.scope.storage().apply(scope);
        SavedValueData<VALUE> existing = storage.get(this.type);
        if (existing != null) {
            return existing;
        }

        // If vanilla could not read the file, it returns null, so creating the data here would overwrite the file on the next save.
        Path file = storage.getDataFile(this.type.id());
        if (Files.exists(file)) {
            throw CodecErrors.decodeFailure(this.type.id().toString(), CodecFormat.NBT.name(), new NexusCodecException("The file " + file + " could not be read"));
        }

        VALUE initial = this.initial.get();
        SavedValueData<VALUE> created = SavedValueData.of(initial, this.format.write(initial, this.scope.registries().apply(scope)));
        storage.set(this.type, created);
        return created;
    }
    
    ///
    /// Encodes the given value into a scratch buffer with the codec it is synced with, if the value is synced.
    ///
    /// @param value      The value to encode.
    /// @param registries The registry access of the scope of the value.
    ///
    /// @throws NexusCodecException If the value is synced and could not be encoded with the codec it is synced with.
    ///
    private void requireSyncable(VALUE value, RegistryAccess registries) {
        if (this.codec == null) {
            return;
        }

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            this.codec.encode(CodecFormat.netty(buffer), value);
        } finally {
            buffer.release();
        }
    }
    
    ///
    /// Sends the given value to the given recipients if the value is synced.
    ///
    private void send(VALUE value, ClientRecipients recipients) {
        if (this.payload != null) {
            this.payload.send(value, recipients);
        }
    }
    
    ///
    /// Replaces the value of the given scope with the result of the given operator and marks the result to be saved.
    ///
    /// The result is encoded right away, the same as with [#set(Object, Object)].
    /// If the value is synced, the result will be sent to every player in the scope.
    ///
    /// @param scope    The server or level that holds the value.
    /// @param operator The operator, called with the current value of the scope.
    ///
    /// @return The new value.
    ///
    /// @throws NexusCodecException If the scope has a saved value that could not be read,
    /// or if the result could not be encoded with the format or with the codec it is synced with, in which case the scope keeps its value.
    ///
    @CanIgnoreReturnValue
    public VALUE update(SCOPE scope, UnaryOperator<VALUE> operator) {
        VALUE updated = operator.apply(this.get(scope));
        this.set(scope, updated);
        return updated;
    }
    
    ///
    /// The value that the server last sent for the scope the client is in.
    ///
    /// If the server hasn't sent the value yet or the client left the server, the initial value will be returned.
    ///
    /// @return The value of the scope the client is in.
    ///
    /// @throws IllegalStateException If the value isn't synced.
    ///
    public VALUE getOnClient() {
        if (this.payload == null) {
            throw new IllegalStateException("The saved value '" + this.type.id() + "' is not synced");
        }
        
        return this.synced != null ? this.synced.get() : this.initial.get();
    }
    
    ///
    /// Wraps the value of the scope of the given player into the packet of the given payload.
    ///
    /// @param payload The payload the value is synced over.
    /// @param player  The player that joined the scope.
    ///
    /// @return The packet that holds the value.
    ///
    /// @throws NexusCodecException If the value of the scope could not be read, or if it could not be encoded with the codec it is synced with.
    ///
    private Packet<? super ClientGamePacketListener> toJoinPacket(ClientboundPayload<VALUE> payload, ServerPlayer player) {
        SCOPE scope = this.scope.playerScope().apply(player);
        VALUE value = this.get(scope);
        this.requireSyncable(value, this.scope.registries().apply(scope));
        return payload.toPacket(value);
    }
    
    ///
    /// Creates the value on the client, once inside the payload handlers event.
    ///
    private BiConsumer<VALUE, ClientHandlerContext> openSynced() {
        SyncedValue<VALUE> synced = new SyncedValue<>(this.initial);
        this.synced = synced;
        return synced;
    }
}
