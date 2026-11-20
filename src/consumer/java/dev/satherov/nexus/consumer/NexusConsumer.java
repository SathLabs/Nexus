package dev.satherov.nexus.consumer;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.persistence.slot.SlotAccess;
import dev.satherov.nexus.api.persistence.slot.SlotSource;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;

///
/// A second mod built on Nexus, through its api only, for the gametests of cases that take two mods.
///
/// The gametests read what it registered through Nexus and never call into it, since the mod order decides which of
/// the two mods runs a listener first.
///
@Mod(NexusConsumer.MOD_ID)
public class NexusConsumer {

    ///
    /// The mod id of the consumer.
    ///
    public static final String MOD_ID = "nexus_consumer";

    ///
    /// A slot source of the consumer's own, which the gametests try to register as well.
    ///
    public static final Identifier SATCHEL = Identifier.fromNamespaceAndPath(NexusConsumer.MOD_ID, "satchel");

    ///
    /// A slot source in the namespace of Nexus, which the gametests register and the consumer tries to register.
    ///
    public static final Identifier BORROWED = Identifier.fromNamespaceAndPath("nexus", "consumer_borrowed");

    ///
    /// The number of slots the consumer gives each of its sources.
    ///
    public static final int SIZE = 5;

    ///
    /// Creates the consumer's handle and listens for the slot sources.
    ///
    /// @param bus       The consumer's own event bus.
    /// @param container The consumer's container.
    ///
    public NexusConsumer(IEventBus bus, ModContainer container) {
        NexusMod.of(container);
        bus.addListener(NexusConsumer::onRegisterSlotSources);
    }

    ///
    /// Registers the satchel as its owner and tries to register the borrowed source as a guest.
    ///
    /// @param event The event.
    ///
    private static void onRegisterSlotSources(RegisterSlotSourcesEvent event) {
        event.register(NexusConsumer.SATCHEL, NexusConsumer.source());
        event.tryRegister(NexusConsumer.BORROWED, NexusConsumer.source());
    }

    ///
    /// A source that gives the first slots of a container.
    ///
    /// @return The source.
    ///
    private static SlotSource<SimpleContainer> source() {
        return SlotSource.of(SimpleContainer.class, container -> SlotAccess.of(container, 0, NexusConsumer.SIZE));
    }
}
