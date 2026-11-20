package dev.satherov.nexus.mixin.tick;

import dev.satherov.nexus.internal.tick.client.ClientSession;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;

///
/// Holds the client level that a login or a respawn builds from its load on, until the handler has switched the client to it.
///
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    
    ///
    /// Runs `handleLogin` through [ClientSession#switchLevel(Runnable)].
    ///
    /// @param packet   The login packet.
    /// @param original The wrapped handler.
    ///
    @WrapMethod(method = "handleLogin")
    private void nexus$holdLoginLevel(ClientboundLoginPacket packet, Operation<Void> original) {
        ClientSession.switchLevel(() -> original.call(packet));
    }
    
    ///
    /// Runs `handleRespawn` through [ClientSession#switchLevel(Runnable)].
    ///
    /// @param packet   The respawn packet.
    /// @param original The wrapped handler.
    ///
    @WrapMethod(method = "handleRespawn")
    private void nexus$holdRespawnLevel(ClientboundRespawnPacket packet, Operation<Void> original) {
        ClientSession.switchLevel(() -> original.call(packet));
    }
}
