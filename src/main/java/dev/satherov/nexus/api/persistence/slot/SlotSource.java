package dev.satherov.nexus.api.persistence.slot;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;

import java.util.Map;
import java.util.function.Function;

///
/// A source of the groups of slots that a holder has on its body, such as the inventory of a player.
///
/// Inside a [SlotLayout], a group under the empty name is called `<source_id>` and every other group `<source_id>/<group>`.
///
/// @param <HOLDER> The type of the holders of the slots.
///
/// @see RegisterSlotSourcesEvent
///
public interface SlotSource<HOLDER> {

    ///
    /// Creates a new source of one group under the empty name.
    ///
    /// @param holder The class of the holders.
    /// @param access The function that gets the slots of a holder.
    ///
    /// @return The source.
    ///
    static <HOLDER> SlotSource<HOLDER> of(Class<HOLDER> holder, Function<HOLDER, SlotAccess> access) {
        return new SlotSource<>() {

            ///
            /// Will always return the given class.
            ///
            @Override
            public Class<HOLDER> holder() {
                return holder;
            }

            ///
            /// Gets the slots of the given holder under the empty name.
            ///
            @Override
            public Map<String, SlotAccess> groups(HOLDER holder) {
                return Map.of("", access.apply(holder));
            }
        };
    }

    ///
    /// The class of the holders of the slots.
    ///
    /// @return The class of the holders.
    ///
    Class<HOLDER> holder();

    ///
    /// Gets the groups of slots of the given holder.
    ///
    /// @param holder The holder of the slots.
    ///
    /// @return The slots of every group, by the name of the group.
    ///
    Map<String, SlotAccess> groups(HOLDER holder);
}
