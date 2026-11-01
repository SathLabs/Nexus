package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.TestWorld;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Reports every ping the client handled to [TestWorld].
///
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    
    ///
    /// Passes the identifier of the handled ping to [TestWorld#onPingHandled(int)].
    ///
    /// Only the render thread reaches the tail, since vanilla throws on the netty thread to hand the packet over.
    ///
    /// @param packet   The handled ping.
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "handlePing",
            at = @At("TAIL")
    )
    private void nexus_gametest$onPingHandled(ClientboundPingPacket packet, CallbackInfo callback) {
        TestWorld.onPingHandled(packet.getId());
    }
}
