package dev.satherov.nexus.api.event.persistence.slot;

import dev.satherov.nexus.api.persistence.slot.SlotLayoutBuilder;
import dev.satherov.nexus.api.persistence.slot.SlotSource;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

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
/// @see SlotLayoutBuilder#registered(Identifier...)
///
public final class RegisterSlotSourcesEvent extends Event implements IModBusEvent {

    ///
    /// The registered sources by their identifier, which this event adds to.
    ///
    private final Map<Identifier, SlotSource<?>> sources;

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
    /// @param id     The identifier of the source.
    /// @param source The source.
    ///
    /// @throws IllegalStateException If a source is already registered under the given identifier.
    ///
    public void register(Identifier id, SlotSource<?> source) {
        if (this.sources.putIfAbsent(id, source) != null) {
            throw new IllegalStateException("The slot source '" + id + "' is registered twice");
        }
    }
}
