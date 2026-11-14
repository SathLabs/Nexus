package dev.satherov.nexus.internal.persistence.saved.client;

import dev.satherov.nexus.api.network.context.ClientHandlerContext;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// The client side of one synced saved value, which stores the value the server last sent.
///
/// Should only ever be loaded on the client.
///
/// @param <VALUE> The type of the value.
///
@ApiStatus.Internal
public final class SyncedValue<VALUE> implements BiConsumer<VALUE, ClientHandlerContext> {
    
    ///
    /// The supplier of the initial value.
    ///
    private final Supplier<VALUE> initial;
    
    ///
    /// The value the server last sent, or `null` if it didn't send one since the client joined.
    ///
    private @Nullable VALUE received;
    
    ///
    /// Subscribes the listener that drops the received value when the client logs out.
    ///
    /// @param initial The supplier of the initial value.
    ///
    public SyncedValue(Supplier<VALUE> initial) {
        this.initial = initial;
        
        // TODO: Evaluate if we want one event per value or one event draining a list.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, this::onLoggingOut);
    }
    
    ///
    /// The value the server last sent, or the initial value if it didn't send one since the client joined.
    ///
    /// @return The value on the client.
    ///
    public VALUE get() {
        return Objects.requireNonNullElseGet(this.received, this.initial);
    }
    
    ///
    /// Stores the value the server sent.
    ///
    /// @param value   The value the server sent.
    /// @param context The client handler context, not used here.
    ///
    @Override
    public void accept(VALUE value, ClientHandlerContext context) {
        this.received = value;
    }
    
    ///
    /// Drops the received value.
    ///
    /// @param event The logging-out event, not used here.
    ///
    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        this.received = null;
    }
}
