package dev.satherov.nexus.api.keybind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.keybind.activation.KeyTick;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

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
    /// The definition the mapping of the key is built from, or `null` if the key has no mapping of its own.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final Keybind.@Nullable Definition definition;
    
    ///
    /// The activation the key fires through.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final Activation activation;
    
    ///
    /// The action run on the client when the key fires, or `null` if none is set.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final @Nullable ClientOnly<Runnable> clientAction;
    
    ///
    /// The key's tick on the last client tick, with the key up until the first one.
    ///
    private KeyTick tick = new KeyTick(false, false, 0, 0, false);
    
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
        return this.tick.down();
    }
    
    ///
    /// The number of client ticks the key has been down on this client, `0` while it is up.
    ///
    /// Will always return `0` on a dedicated server.
    ///
    /// @return The number of client ticks the key has been down.
    ///
    public int getHeldTicks() {
        return this.tick.heldTicks();
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
        return this.activation.progress(this.tick);
    }
    
    ///
    /// Moves the key on to the given tick.
    ///
    /// Should only ever be called on the client, once per client tick.
    ///
    /// @param tick The key's tick on the client tick that ended.
    ///
    @ApiStatus.Internal
    public void update(KeyTick tick) {
        this.tick = tick;
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
    
    ///
    /// The parts of a key that the mod declares itself, as its builder collected them.
    ///
    /// @param name       The name the key was declared with.
    /// @param english    The English default of the key's name.
    /// @param defaultKey The default GLFW key code or mouse button, or `-1` if the key is unbound.
    /// @param mouse      If the default key is a mouse button.
    /// @param modifier   The modifier, or `null` if the key takes none.
    /// @param context    The context the key can be pressed in.
    /// @param category   The identifier of the category the key is listed in.
    ///
    @ApiStatus.Internal
    public record Definition(
            String name,
            String english,
            int defaultKey,
            boolean mouse,
            Keybind.@Nullable Modifier modifier,
            Keybind.Context context,
            Identifier category
    ) { }
}
