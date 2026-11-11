package dev.satherov.nexus.api.persistence.saved;

import dev.satherov.nexus.api.network.ClientRecipients;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.context.ClientHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.internal.persistence.saved.SavedScope;
import dev.satherov.nexus.internal.persistence.saved.SavedValueData;
import dev.satherov.nexus.internal.persistence.saved.client.SyncedValue;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A handle of one value that a mod saves once per server or once per level.
///
/// Each value should be immutable and is only ever replaced whole.
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
    /// The supplier of the initial value.
    ///
    private final Supplier<VALUE> initial;
    
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
    /// @param type    The type of the saved data that holds the value.
    /// @param initial The supplier of the initial value.
    /// @param sync    The builder of the payload the value is synced over, or `null` if it isn't synced.
    ///
    SavedValue(SavedScope<SCOPE> scope, SavedDataType<SavedValueData<VALUE>> type, Supplier<VALUE> initial, @Nullable PayloadBuilder<VALUE> sync) {
        this.scope = scope;
        this.type = type;
        this.initial = initial;
        this.payload = sync != null ? sync.toClient(this::openSynced) : null;
        if (this.payload != null) {
            NeoForge.EVENT_BUS.addListener(scope.joinEvent(), this::onJoin);
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
    public VALUE get(SCOPE scope) {
        return this.getData(scope).getValue();
    }
    
    ///
    /// Replaces the value of the given scope with the given value and marks it to be saved.
    ///
    /// If the value is synced, it will be sent to every player in the scope.
    ///
    /// @param scope The server or level that holds the value.
    /// @param value The new value.
    ///
    public void set(SCOPE scope, VALUE value) {
        this.getData(scope).replace(value);
        this.send(value, this.scope.recipients().apply(scope));
    }
    
    ///
    /// The saved data that holds the value of the given scope, created with the initial value if the scope doesn't have one.
    ///
    private SavedValueData<VALUE> getData(SCOPE scope) {
        return this.scope.storage().apply(scope).computeIfAbsent(this.type);
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
    /// If the value is synced, the result will be sent to every player in the scope.
    ///
    /// @param scope    The server or level that holds the value.
    /// @param operator The operator, called with the current value of the scope.
    ///
    /// @return The new value.
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
    /// Creates the value on the client, once inside the payload handlers event.
    ///
    private BiConsumer<VALUE, ClientHandlerContext> openSynced() {
        SyncedValue<VALUE> synced = new SyncedValue<>(this.initial);
        this.synced = synced;
        return synced;
    }
    
    ///
    /// Sends the value of the scope a player joined to that player.
    ///
    private void onJoin(EntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            this.send(this.get(this.scope.playerScope().apply(player)), ClientRecipients.player(player));
        }
    }
}
