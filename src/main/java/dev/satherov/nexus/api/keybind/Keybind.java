package dev.satherov.nexus.api.keybind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.keybind.activation.KeyTick;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.keybind.HeldKeys;
import dev.satherov.nexus.internal.keybind.KeySignal;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

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
    /// @return The name `key.<modid>.<name>`, or the mapping name passed to [Keybinds#existing(String)].
    ///
    @Getter
    private final String name;
    
    ///
    /// The definition the mapping of the key is built from, or `null` if the key is on an existing mapping.
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
    /// The readers of the carried values on the client, in the order of their codecs.
    ///
    private final @Unmodifiable List<ClientOnly<? extends Supplier<?>>> readers;
    
    ///
    /// If a server action is set for the key.
    ///
    private final boolean hasServerAction;
    
    ///
    /// If the key sends its going down and up to the server.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final boolean syncHeld;
    
    ///
    /// The payload the key sends to the server on, or `null` if the key has neither a server action nor syncs its held state.
    ///
    @Getter(onMethod_ = @ApiStatus.Internal)
    private final @Nullable ServerboundPayload<KeySignal> payload;
    
    ///
    /// The held keys of the mod, read on the server.
    ///
    private final HeldKeys heldKeys;
    
    ///
    /// The key's tick on the client tick its state last changed on, with the key up until the first change.
    ///
    private KeyTick changedTick = new KeyTick(false, false, 0, 0, false);
    
    ///
    /// The client tick the key's state last changed on or its tracking started on, `0` while the key is not tracked.
    ///
    private int changedAt;
    
    ///
    /// The client tick the key was last moved on to or its tracking started on, `0` while the key is not tracked.
    ///
    private int movedAt;
    
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
    /// Will always return `false` on a dedicated server, and for a key on an existing mapping that no mod on this client declares.
    ///
    /// @return `true` if the key is down.
    ///
    public boolean isDown() {
        return this.changedTick.down();
    }
    
    ///
    /// If the given player's key is down on the server.
    ///
    /// @param player The player whose key is read.
    ///
    /// @return `true` if the key is down, or `false` if the player logged out.
    ///
    /// @throws IllegalStateException If the key does not sync its held state.
    ///
    /// @see BindingBuilder#syncHeld()
    ///
    public boolean isDown(ServerPlayer player) {
        return this.getHeldTicks(player) > 0;
    }
    
    ///
    /// The number of client ticks the key has been down on this client, `0` while it is up.
    ///
    /// Will always return `0` on a dedicated server.
    ///
    /// @return The number of client ticks the key has been down.
    ///
    public int getHeldTicks() {
        return this.getTick().heldTicks();
    }
    
    ///
    /// The number of server ticks since the given player's key went down, `1` on the tick it arrives and `0` while it is up.
    ///
    /// @param player The player whose key is read.
    ///
    /// @return The number of server ticks the key has been down, or `0` if the player logged out.
    ///
    /// @throws IllegalStateException If the key does not sync its held state.
    ///
    /// @see BindingBuilder#syncHeld()
    ///
    public int getHeldTicks(ServerPlayer player) {
        if (!this.syncHeld) {
            throw new IllegalStateException("Key '" + this.name + "' does not sync its held state, so the server cannot read it");
        }
        
        return this.heldKeys.getHeldTicks(player, this.name);
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
        return this.activation.progress(this.getTick());
    }
    
    ///
    /// How far the given player's key is toward firing on the server, from `0` to `1`, as its activation reports it for the player's held ticks.
    ///
    /// Will always return `0` while the key is up.
    ///
    /// @param player The player whose key is read.
    ///
    /// @return The progress of the key toward firing.
    ///
    /// @throws IllegalStateException If the key does not sync its held state.
    ///
    /// @see Activation#progress(KeyTick)
    /// @see BindingBuilder#syncHeld()
    ///
    public float getProgress(ServerPlayer player) {
        int heldTicks = this.getHeldTicks(player);
        if (heldTicks == 0) {
            return 0.0F;
        }
        
        return this.activation.progress(new KeyTick(true, heldTicks > 1, heldTicks, 0, false));
    }
    
    ///
    /// Any readers of the carried values on the client, in the order of their codecs.
    ///
    /// Should only ever be resolved on the client.
    ///
    /// @return Any readers of the carried values.
    ///
    @ApiStatus.Internal
    public @Unmodifiable List<ClientOnly<? extends Supplier<?>>> getAllReaders() {
        return this.readers;
    }
    
    ///
    /// If a server action is set for the key.
    ///
    /// @return `true` if a server action is set.
    ///
    @ApiStatus.Internal
    public boolean hasServerAction() {
        return this.hasServerAction;
    }
    
    ///
    /// The key's tick on the client tick it was last moved on to, with the key up until the first one.
    ///
    /// @return The key's tick on the client tick it was last moved on to.
    ///
    @ApiStatus.Internal
    public KeyTick getTick() {
        return this.tickAt(this.movedAt);
    }
    
    ///
    /// Starts tracking the key on the given client tick, with the key up.
    ///
    /// Should only ever be called on the client, once per key.
    ///
    /// @param clientTick The client tick the tracking starts on.
    ///
    @ApiStatus.Internal
    public void track(int clientTick) {
        this.changedAt = clientTick;
        this.movedAt = clientTick;
    }
    
    ///
    /// Moves the key on to the given client tick with its state unchanged.
    ///
    /// Should only ever be called on the client, on every client tick that [#update(boolean, boolean, int)] is not called on.
    ///
    /// @param clientTick The client tick that ended.
    ///
    @ApiStatus.Internal
    public void moveTo(int clientTick) {
        this.movedAt = clientTick;
    }
    
    ///
    /// Moves the key on to the given client tick with the given down state.
    ///
    /// Should only ever be called on the client, on every client tick the key is down and on the tick it goes up.
    ///
    /// @param down        If the key is down on the client tick.
    /// @param interrupted If the key went up without the player letting go, read only on the tick it goes up.
    /// @param clientTick  The client tick that ended.
    ///
    /// @return The key's tick on the client tick.
    ///
    @ApiStatus.Internal
    public KeyTick update(boolean down, boolean interrupted, int clientTick) {
        this.movedAt = clientTick;
        if (down == this.changedTick.down()) {
            return this.tickAt(clientTick);
        }
        
        // On the tick the key goes down, the idle ticks keep the length of the gap that ended on the tick before.
        this.changedTick = down ? new KeyTick(true, false, 1, this.tickAt(clientTick - 1).idleTicks(), false) : new KeyTick(false, true, 0, 1, interrupted);
        this.changedAt = clientTick;
        return this.changedTick;
    }
    
    ///
    /// The key's tick on the given client tick, which is not before the client tick its state last changed on.
    ///
    private KeyTick tickAt(int clientTick) {
        int elapsed = clientTick - this.changedAt;
        if (elapsed == 0) {
            return this.changedTick;
        }
        
        if (this.changedTick.down()) {
            return new KeyTick(true, true, this.changedTick.heldTicks() + elapsed, 0, false);
        }
        
        return new KeyTick(false, false, 0, this.changedTick.idleTicks() + elapsed, false);
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
