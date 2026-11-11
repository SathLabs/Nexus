package dev.satherov.nexus.internal.persistence.slot;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.slot.SlotAccess;
import dev.satherov.nexus.api.persistence.slot.SlotSource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

///
/// The table of the slot sources registered through [RegisterSlotSourcesEvent] and the vanilla sources of a player.
///
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public class SlotSources {

    ///
    /// The registered sources by their identifier, or `null` until [RegisterSlotSourcesEvent] was posted.
    ///
    private static volatile @Nullable Map<Identifier, SlotSource<?>> registered;

    ///
    /// Queues the event to be posted on the main thread once common setup ran on every mod.
    ///
    @SubscribeEvent
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SlotSources::collect);
    }

    ///
    /// Registers the vanilla sources, posts the event and then freezes the table.
    ///
    private static void collect() {
        Map<Identifier, SlotSource<?>> sources = new LinkedHashMap<>();
        RegisterSlotSourcesEvent event = new RegisterSlotSourcesEvent(sources);
        event.register(
                Identifier.withDefaultNamespace("inventory"),
                SlotSource.of(Player.class, player -> SlotAccess.of(player.getInventory(), 0, Inventory.INVENTORY_SIZE))
        );
        // The armor sits between the main slots and the offhand, feet first.
        event.register(
                Identifier.withDefaultNamespace("armor"),
                SlotSource.of(Player.class, player -> SlotAccess.of(player.getInventory(), Inventory.INVENTORY_SIZE, Inventory.SLOT_OFFHAND - Inventory.INVENTORY_SIZE))
        );
        event.register(
                Identifier.withDefaultNamespace("offhand"),
                SlotSource.of(Player.class, player -> SlotAccess.of(player.getInventory(), Inventory.SLOT_OFFHAND, 1))
        );
        ModLoader.postEvent(event);
        SlotSources.registered = Collections.unmodifiableMap(sources);
    }

    ///
    /// Gets every registered source by its identifier.
    ///
    /// @return The registered sources.
    ///
    /// @throws IllegalStateException If [RegisterSlotSourcesEvent] wasn't posted yet.
    ///
    public static Map<Identifier, SlotSource<?>> getAll() {
        Map<Identifier, SlotSource<?>> sources = SlotSources.registered;
        if (sources == null) {
            throw new IllegalStateException("The slot sources are not registered yet");
        }

        return sources;
    }
}
