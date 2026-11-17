package dev.satherov.nexus.api.event.persistence.slot;

import dev.satherov.nexus.api.persistence.slot.SlotLayoutBuilder;
import dev.satherov.nexus.api.persistence.slot.SlotSource;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

///
/// An event to register the sources of the slots that a holder has on its body.
///
/// Fired once on every mod bus during common setup, on the main thread.
///
/// Nexus registers these sources for every [Player] before it fires the event:
/// - `minecraft:inventory` with 36 slots, hotbar first.
/// - `minecraft:armor` with 4 slots, feet first.
/// - `minecraft:offhand` with 1 slot.
///
/// A source should never cover a slot that another source covers.
///
/// @see SlotLayoutBuilder#registered(Identifier...)
///
public final class RegisterSlotSourcesEvent extends Event implements IModBusEvent {

    ///
    /// The registered sources by their identifier, which this event adds to.
    ///
    private final Map<Identifier, SlotSource<?>> sources;

    ///
    /// The identifiers whose sources were registered through [#tryRegister(Identifier, SlotSource)] and can still be replaced.
    ///
    private final Set<Identifier> replaceable = new HashSet<>();

    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param sources The registered sources by their identifier, which this event adds to.
    ///
    @ApiStatus.Internal
    public RegisterSlotSourcesEvent(Map<Identifier, SlotSource<?>> sources) {
        this.sources = sources;
    }

    ///
    /// Registers the given source under the given identifier.
    ///
    /// If a source is registered under the given identifier through [#tryRegister(Identifier, SlotSource)], the given source will replace it.
    ///
    /// @param id     The identifier of the source.
    /// @param source The source.
    ///
    /// @throws IllegalStateException If a source is already registered under the given identifier through this method.
    ///
    /// @see #tryRegister(Identifier, SlotSource)
    ///
    public void register(Identifier id, SlotSource<?> source) {
        if (this.sources.containsKey(id) && !this.replaceable.contains(id)) {
            throw new IllegalStateException("The slot source '" + id + "' is registered twice");
        }

        this.replaceable.remove(id);
        this.sources.put(id, source);
    }

    ///
    /// Registers the given source under the given identifier if no source is registered under it yet.
    ///
    /// Meant to be used for a source in the namespace of another mod, which that mod may register itself.
    /// A source registered under the same identifier through [#register(Identifier, SlotSource)] wins over the given source,
    /// whichever listener runs first.
    ///
    /// @param id     The identifier of the source.
    /// @param source The source.
    ///
    /// @return `true` if the source was registered, or `false` if another source is already registered under the given identifier.
    ///
    @CanIgnoreReturnValue
    public boolean tryRegister(Identifier id, SlotSource<?> source) {
        if (this.sources.putIfAbsent(id, source) != null) {
            return false;
        }

        this.replaceable.add(id);
        return true;
    }
}
