package dev.satherov.nexus.mixin.tick;

import dev.satherov.nexus.internal.tick.client.ClientSession;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.client.multiplayer.ClientLevel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

///
/// Tells the client session when the load of a client level starts and ends.
///
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    
    ///
    /// Posts the load event that neoforge posts at the end of the constructor through [ClientSession#load(ClientLevel, java.util.function.Supplier)].
    ///
    /// Optional, so a redirect of the post by another mod only makes [dev.satherov.nexus.api.tick.Scheduler#in(net.minecraft.world.level.Level)] refuse the level during its load.
    ///
    /// @param bus      The event bus.
    /// @param event    The load event.
    /// @param original The wrapped post.
    ///
    /// @return The posted load event.
    ///
    @WrapOperation(
            method = "<init>",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"
            )
    )
    private Event nexus$markLoad(IEventBus bus, Event event, Operation<Event> original) {
        return ClientSession.load((ClientLevel) (Object) this, () -> original.call(bus, event));
    }
}
