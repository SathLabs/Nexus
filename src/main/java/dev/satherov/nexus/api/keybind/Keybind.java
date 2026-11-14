package dev.satherov.nexus.api.keybind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.keybind.activation.KeyTick;
import dev.satherov.nexus.internal.keybind.KeyDefinition;
import dev.satherov.nexus.internal.keybind.client.ClientKeys;

import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;

///
/// A key that a mod registered.
///
/// Can be created through [BindingBuilder#register()].
///
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Keybind {
    
    ///
    /// The name of the mapping, which is also the translation key of its label.
    ///
    /// @return The name `key.<modid>.<name>`.
    ///
    @Getter
    private final String name;
    
    ///
    /// The definition the mapping of the key is built from.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final KeyDefinition definition;
    
    ///
    /// The text of the key that is bound to the mapping right now, resolved by the client that shows it.
    ///
    /// @return The text of the bound key.
    ///
    public Component getBoundKey() {
        return Component.keybind(this.name);
    }
    
    ///
    /// If the key is down on this client, as Nexus tracks it.
    ///
    /// A key in the context [Context#IN_SCREEN] or [Context#EVERYWHERE] is tracked while a screen is open too.
    ///
    /// Will always return `false` on a dedicated server.
    ///
    /// @return `true` if the key is down.
    ///
    public boolean isDown() {
        return FMLEnvironment.getDist().isClient() && ClientKeys.getLastTick(this).down();
    }
    
    ///
    /// The number of client ticks the key has been down on this client, `0` while it is up.
    ///
    /// Will always return `0` on a dedicated server.
    ///
    /// @return The number of client ticks the key has been down.
    ///
    public int getHeldTicks() {
        if (!FMLEnvironment.getDist().isClient()) {
            return 0;
        }
        
        return ClientKeys.getLastTick(this).heldTicks();
    }
    
    ///
    /// How far the key is toward firing on this client, from `0` to `1`, as its activation reports it for the last client tick.
    ///
    /// Will always return `0` on a dedicated server.
    ///
    /// @return The progress of the key toward firing.
    ///
    /// @see Activation#progress(KeyTick)
    ///
    public float getProgress() {
        if (!FMLEnvironment.getDist().isClient()) {
            return 0.0F;
        }
        
        return this.definition.activation().progress(ClientKeys.getLastTick(this));
    }
    
    ///
    /// A modifier key that has to be held together with a key.
    ///
    public enum Modifier {
        ///
        /// The control key.
        ///
        CONTROL,
        
        ///
        /// The shift key.
        ///
        SHIFT,
        
        ///
        /// The alt key.
        ///
        ALT
    }
    
    ///
    /// The context that a key can be pressed in.
    ///
    public enum Context {
        ///
        /// In game, while no screen is open.
        ///
        IN_GAME,
        
        ///
        /// While a screen is open.
        ///
        IN_SCREEN,
        
        ///
        /// At any time, in game or in a screen.
        ///
        EVERYWHERE
    }
}
