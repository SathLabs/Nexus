package dev.satherov.nexus.test.unit.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.KeyTick;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the tick a key reads back on each client tick from the changes of its state.
///
public class KeybindTest {

    private static @Nullable Keybind held;
    private static @Nullable Keybind tapped;

    @Test
    public void countsTheHeldTicksAndTheIdleTicksAroundAHold() {
        Keybind held = Objects.requireNonNull(KeybindTest.held);
        held.track(10);
        Assertions.assertThat(held.getTick()).as("the tick the tracking starts on").isEqualTo(new KeyTick(false, false, 0, 0, false));

        held.moveTo(11);
        held.moveTo(12);
        Assertions.assertThat(held.getTick()).as("the second tick up").isEqualTo(new KeyTick(false, false, 0, 2, false));
        Assertions.assertThat(held.update(true, false, 13)).as("the tick the key goes down").isEqualTo(new KeyTick(true, false, 1, 2, false));
        Assertions.assertThat(held.update(true, false, 14)).as("the second tick down").isEqualTo(new KeyTick(true, true, 2, 0, false));
        Assertions.assertThat(held.update(true, false, 15)).as("the third tick down").isEqualTo(new KeyTick(true, true, 3, 0, false));

        Assertions.assertThat(held.update(false, true, 16)).as("the tick the key goes up").isEqualTo(new KeyTick(false, true, 0, 1, true));
        held.moveTo(17);
        Assertions.assertThat(held.getTick()).as("the tick after the key went up").isEqualTo(new KeyTick(false, false, 0, 2, false));
    }

    @Test
    public void carriesTheGapBetweenTwoTapsOntoTheSecondTap() {
        Keybind tapped = Objects.requireNonNull(KeybindTest.tapped);
        tapped.track(0);
        Assertions.assertThat(tapped.update(true, false, 1)).as("the first tap").isEqualTo(new KeyTick(true, false, 1, 0, false));
        Assertions.assertThat(tapped.update(false, false, 2)).as("the release of the first tap").isEqualTo(new KeyTick(false, true, 0, 1, false));

        tapped.moveTo(3);
        tapped.moveTo(4);
        Assertions.assertThat(tapped.update(true, false, 5)).as("the second tap").isEqualTo(new KeyTick(true, false, 1, 3, false));
        Assertions.assertThat(tapped.update(false, false, 6)).as("the release of the second tap").isEqualTo(new KeyTick(false, true, 0, 1, false));
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("keybind_test", "Keybind Test");
            KeybindTest.held = keybinds.key("keybind_test_held", "Held").category(category).register();
            KeybindTest.tapped = keybinds.key("keybind_test_tapped", "Tapped").category(category).register();
        }
    }
}
