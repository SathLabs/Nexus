package dev.satherov.nexus.test.game.consumer;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.slot.SlotAccess;
import dev.satherov.nexus.api.persistence.slot.SlotSnapshot;
import dev.satherov.nexus.api.persistence.slot.SlotSource;
import dev.satherov.nexus.consumer.NexusConsumer;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;

///
/// Checks that the owner of a slot source id keeps it against another mod that tries to register it, in either order
/// of the two mods' listeners.
///
/// The consumer registers [NexusConsumer#SATCHEL] and tries [NexusConsumer#BORROWED], and these samples do the
/// opposite, so one of the two ids sees the owner's listener run first and the other sees it run second.
///
public class SlotSourceOwnerSamples {

    private static final int SIZE = 2;

    private static int captured(Identifier source) {
        SlotSnapshot snapshot = Nexus.getMod().getPersistence().slots(SimpleContainer.class).source(source).build().capture(new SimpleContainer(9));
        return snapshot.size(source.toString());
    }

    @ServerTest
    public static void theOtherModKeepsItsOwnSource(GameTestHelper helper) {
        helper.assertValueEqual(SlotSourceOwnerSamples.captured(NexusConsumer.SATCHEL), NexusConsumer.SIZE, "slots of the consumer's own source");
        helper.succeed();
    }

    @ServerTest
    public static void theOwnerKeepsItsSourceAgainstTheOtherMod(GameTestHelper helper) {
        helper.assertValueEqual(SlotSourceOwnerSamples.captured(NexusConsumer.BORROWED), SlotSourceOwnerSamples.SIZE, "slots of the source the consumer tried");
        helper.succeed();
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onRegisterSlotSources(RegisterSlotSourcesEvent event) {
            SlotSource<SimpleContainer> source = SlotSource.of(SimpleContainer.class, container -> SlotAccess.of(container, 0, SlotSourceOwnerSamples.SIZE));
            event.tryRegister(NexusConsumer.SATCHEL, source);
            event.register(NexusConsumer.BORROWED, source);
        }
    }
}
