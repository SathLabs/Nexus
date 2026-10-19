package dev.satherov.nexus.api.network.context;

import lombok.Getter;

import dev.satherov.nexus.api.network.PayloadBuilder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;

///
/// A context that a payload handler receives along with its payload.
///
public class HandlerContext {

    ///
    /// The neoforge context of the payload.
    ///
    /// Meant to be used for what this type does not cover, such as finishing a configuration task.
    ///
    @Getter
    private final IPayloadContext platformContext;

    ///
    /// Wraps the given neoforge context of one received payload.
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
    /// The player of the connection.
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
    @CanIgnoreReturnValue
    public CompletableFuture<Void> enqueue(Runnable task) {
        return this.platformContext.enqueueWork(task);
    }
}
