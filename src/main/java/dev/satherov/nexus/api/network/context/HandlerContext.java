package dev.satherov.nexus.api.network.context;

import dev.satherov.nexus.api.network.PayloadBuilder;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.channel.ChannelHandlerContext;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

///
/// The context that a payload handler receives together with the payload itself.
///
public class HandlerContext {

    ///
    /// The neoforge context of the payload.
    ///
    private final IPayloadContext platformContext;

    ///
    /// Should only ever be called from the handler adapters of [PayloadBuilder].
    ///
    /// @param platform The neoforge context of the payload.
    ///
    @ApiStatus.Internal
    public HandlerContext(IPayloadContext platform) {
        this.platformContext = platform;
    }

    ///
    /// @return The packet listener of the connection.
    ///
    public ICommonPacketListener getListener() {
        return this.platformContext.listener();
    }

    ///
    /// @return The connection the payload was received on.
    ///
    public Connection getConnection() {
        return this.platformContext.connection();
    }

    ///
    /// @return The netty channel handler context of the connection.
    ///
    public ChannelHandlerContext getChannelHandlerContext() {
        return this.platformContext.channelHandlerContext();
    }

    ///
    /// @return The protocol of the connection.
    ///
    public ConnectionProtocol getProtocol() {
        return this.platformContext.protocol();
    }

    ///
    /// @return The packet flow of the payload.
    ///
    public PacketFlow getFlow() {
        return this.platformContext.flow();
    }

    ///
    /// @return The player of the connection.
    ///
    /// @throws IllegalStateException If the connection is in the configuration phase, where no player exists yet.
    ///
    public Player getPlayer() {
        if (this.platformContext.protocol().isConfiguration()) {
            throw new IllegalStateException("There is no player during the configuration phase");
        }

        return this.platformContext.player();
    }

    ///
    /// Sends the given payload back over the connection.
    ///
    /// @param payload The payload to send.
    ///
    public void reply(CustomPacketPayload payload) {
        this.platformContext.reply(payload);
    }

    ///
    /// Handles the given packet as if it had been received on the connection.
    ///
    /// @param packet The packet to handle.
    ///
    public void handle(Packet<?> packet) {
        this.platformContext.handle(packet);
    }

    ///
    /// Handles the given payload as if it had been received on the connection.
    ///
    /// @param payload The payload to handle.
    ///
    public void handle(CustomPacketPayload payload) {
        this.platformContext.handle(payload);
    }

    ///
    /// Finishes the current configuration task of the server.
    ///
    /// @param type The type of the configuration task.
    ///
    /// @throws IllegalStateException If the server did not receive the payload during the configuration phase.
    ///
    public void finishCurrentTask(ConfigurationTask.Type type) {
        if (this.platformContext.flow() != PacketFlow.SERVERBOUND || !this.platformContext.protocol().isConfiguration()) {
            throw new IllegalStateException("There is no configuration task outside the configuration phase of the server");
        }

        this.platformContext.finishCurrentTask(type);
    }

    ///
    /// Closes the connection with the given reason.
    ///
    /// @param reason The reason shown to the player.
    ///
    public void disconnect(Component reason) {
        this.platformContext.disconnect(reason);
    }

    ///
    /// Runs the given task on the main thread of this side.
    ///
    /// Only needed by handlers on the network thread.
    /// If the handler already runs on the main thread, the task will run immediately.
    ///
    /// @param task The task to run on the main thread.
    ///
    /// @return The future that completes once the task ran.
    ///
    /// @see #enqueue(Supplier)
    ///
    @CanIgnoreReturnValue
    public CompletableFuture<Void> enqueue(Runnable task) {
        return this.platformContext.enqueueWork(task);
    }

    ///
    /// Runs the given task on the main thread of this side.
    ///
    /// Only needed by handlers on the network thread.
    /// If the handler already runs on the main thread, the task will run immediately.
    ///
    /// @param task The task to run on the main thread.
    ///
    /// @return The future that completes with the result of the task once it ran.
    ///
    /// @see #enqueue(Runnable)
    ///
    public <RESULT> CompletableFuture<RESULT> enqueue(Supplier<RESULT> task) {
        return this.platformContext.enqueueWork(task);
    }
}
