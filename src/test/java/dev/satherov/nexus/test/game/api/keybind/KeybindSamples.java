package dev.satherov.nexus.test.game.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;
import dev.satherov.nexus.gametest.api.client.TextBoxScreen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

///
/// The client tests that check declared keys build their mappings and fire in game and in screens.
///
public class KeybindSamples {

    private static final int SETTLE_TICKS = 5;
    private static final AtomicInteger PRESSES = new AtomicInteger();
    private static final AtomicInteger SHIFTED_PRESSES = new AtomicInteger();
    private static final AtomicInteger GAME_PRESSES = new AtomicInteger();
    private static final AtomicInteger SCREEN_PRESSES = new AtomicInteger();
    private static final AtomicInteger TYPED_PRESSES = new AtomicInteger();
    private static final AtomicInteger CLICKS = new AtomicInteger();

    private static @Nullable Identifier category;
    private static @Nullable Keybind declared;
    private static @Nullable Keybind pressed;
    private static @Nullable Keybind unbound;
    private static @Nullable Keybind mouse;
    private static @Nullable Keybind shifted;
    private static @Nullable Keybind inGame;
    private static @Nullable Keybind inScreen;
    private static @Nullable Keybind typed;
    private static @Nullable Keybind rebound;

    @ClientTest
    public static void mappingHasTheDeclaredKeyModifierAndContext(Client client) {
        Keybind declared = Objects.requireNonNull(KeybindSamples.declared);
        KeyMapping mapping = KeybindSamples.mapping(declared);
        Assertions.assertThat(declared.getName()).isEqualTo("key.nexus.keybind_samples_declared");
        Assertions.assertThat(mapping.getDefaultKey()).isEqualTo(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_KP_7));
        Assertions.assertThat(mapping.getDefaultKeyModifier()).isEqualTo(KeyModifier.CONTROL);
        Assertions.assertThat(mapping.getKeyConflictContext()).isEqualTo(KeyConflictContext.GUI);
        Assertions.assertThat(mapping.getCategory()).isEqualTo(new KeyMapping.Category(Objects.requireNonNull(KeybindSamples.category)));
    }

    @ClientTest
    public static void pressRunsTheClientActionOnce(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        KeybindSamples.PRESSES.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_KP_1);
        client.until("the client action to run", () -> KeybindSamples.PRESSES.get() > 0);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.PRESSES.get()).as("runs of the client action").isEqualTo(1);
        Assertions.assertThat(Objects.requireNonNull(KeybindSamples.pressed).isDown()).as("the key after its release").isFalse();
        client.leaveWorld();
    }

    @ClientTest
    public static void unboundAndMouseKeysRegister(Client client) {
        Assertions.assertThat(KeybindSamples.mapping(KeybindSamples.unbound).isUnbound()).as("the unbound mapping is unbound").isTrue();
        Assertions.assertThat(KeybindSamples.mapping(KeybindSamples.mouse).getDefaultKey()).isEqualTo(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
    }

    @ClientTest
    public static void shiftKeyFiresOnlyWithShiftHeld(Client client) {
        Keybind shifted = Objects.requireNonNull(KeybindSamples.shifted);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        KeybindSamples.SHIFTED_PRESSES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_KP_2);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(shifted.isDown()).as("the key held without shift").isFalse();
        keyboard.release(GLFW.GLFW_KEY_KP_2);
        Assertions.assertThat(KeybindSamples.SHIFTED_PRESSES.get()).as("runs without shift").isZero();

        keyboard.hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        keyboard.press(GLFW.GLFW_KEY_KP_2);
        client.until("the client action to run with shift held", () -> KeybindSamples.SHIFTED_PRESSES.get() > 0);
        keyboard.release(GLFW.GLFW_KEY_LEFT_SHIFT);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.SHIFTED_PRESSES.get()).as("runs with shift held").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void gameKeyDoesNothingInAScreen(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        KeybindSamples.GAME_PRESSES.set(0);
        client.keyboard().hold(GLFW.GLFW_KEY_KP_3);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(Objects.requireNonNull(KeybindSamples.inGame).isDown()).as("the key held in a screen").isFalse();
        client.keyboard().release(GLFW.GLFW_KEY_KP_3);
        Assertions.assertThat(KeybindSamples.GAME_PRESSES.get()).as("runs of the client action in a screen").isZero();
        client.minecraft().setScreen(null);
        client.leaveWorld();
    }

    @ClientTest
    public static void screenKeyFiresInAScreenAndGoesUpWhenItCloses(Client client) {
        Keybind inScreen = Objects.requireNonNull(KeybindSamples.inScreen);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        KeybindSamples.SCREEN_PRESSES.set(0);
        keyboard.press(GLFW.GLFW_KEY_KP_4);
        client.until("the client action to run in the screen", () -> KeybindSamples.SCREEN_PRESSES.get() > 0);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.SCREEN_PRESSES.get()).as("runs of the client action in the screen").isEqualTo(1);
        Assertions.assertThat(inScreen.isDown()).as("the key after its release").isFalse();

        keyboard.hold(GLFW.GLFW_KEY_KP_4);
        client.until("the key to read down in the screen", inScreen::isDown);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(inScreen.isDown()).as("the key held in the screen").isTrue();

        client.minecraft().setScreen(null);
        client.until("the key to go up once the screen closed", () -> !inScreen.isDown());
        Assertions.assertThat(keyboard.isHeld(GLFW.GLFW_KEY_KP_4)).as("the key is still held").isTrue();
        keyboard.release(GLFW.GLFW_KEY_KP_4);
        client.leaveWorld();
    }

    @ClientTest
    public static void typingTheLetterIntoATextBoxFiresNothing(Client client) {
        TextBoxScreen screen = new TextBoxScreen();
        client.minecraft().setScreen(screen);
        KeybindSamples.TYPED_PRESSES.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_J);
        client.keyboard().type("j");
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(screen.getBox().getValue()).isEqualTo("j");
        Assertions.assertThat(Objects.requireNonNull(KeybindSamples.typed).isDown()).as("the key typed into the text box").isFalse();
        Assertions.assertThat(KeybindSamples.TYPED_PRESSES.get()).as("runs of the client action while typing").isZero();
    }

    @ClientTest
    public static void clickTheScreenHandledFiresNothing(Client client) {
        client.minecraft().setScreen(new KeybindSamples.ClickTakingScreen());
        KeybindSamples.CLICKS.set(0);
        client.mouse().click(GLFW.GLFW_MOUSE_BUTTON_4);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.CLICKS.get()).as("runs of the client action on a handled click").isZero();

        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        client.mouse().click(GLFW.GLFW_MOUSE_BUTTON_4);
        client.until("the client action to run on a click the screen did not handle", () -> KeybindSamples.CLICKS.get() > 0);
    }

    @ClientTest
    public static void boundKeyFollowsARebind(Client client) {
        Keybind rebound = Objects.requireNonNull(KeybindSamples.rebound);
        KeyMapping mapping = KeybindSamples.mapping(rebound);
        InputConstants.Key target = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_KP_6);
        String before = rebound.getBoundKey().getString();
        mapping.setKey(target);
        KeyMapping.resetMapping();
        try {
            Assertions.assertThat(rebound.getBoundKey().getString())
                    .isNotEqualTo(before)
                    .isEqualTo(target.getDisplayName().getString());
        } finally {
            mapping.setKey(mapping.getDefaultKey());
            KeyMapping.resetMapping();
        }
    }

    @ClientTest
    public static void registerAfterTheMappingsWereRegisteredThrows(Client client) {
        Keybinds keybinds = Nexus.getMod().getKeybinds();
        Assertions.assertThatIllegalStateException().isThrownBy(() -> keybinds.key("keybind_samples_late", "Late").register());
    }

    private static KeyMapping mapping(@Nullable Keybind keybind) {
        String name = Objects.requireNonNull(keybind).getName();
        return Objects.requireNonNull(KeyMapping.get(name), name);
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("keybind_samples", "Keybind Samples");
            KeybindSamples.category = category;
            KeybindSamples.declared = keybinds.key("keybind_samples_declared", "Declared")
                    .defaultKey(GLFW.GLFW_KEY_KP_7)
                    .modifier(Keybind.Modifier.CONTROL)
                    .context(Keybind.Context.IN_SCREEN)
                    .category(category)
                    .register();
            KeybindSamples.pressed = keybinds.key("keybind_samples_pressed", "Pressed")
                    .defaultKey(GLFW.GLFW_KEY_KP_1)
                    .onClient(() -> KeybindSamples.PRESSES::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.unbound = keybinds.key("keybind_samples_unbound", "Unbound")
                    .category(category)
                    .register();
            KeybindSamples.mouse = keybinds.key("keybind_samples_mouse", "Mouse")
                    .defaultMouseButton(GLFW.GLFW_MOUSE_BUTTON_4)
                    .context(Keybind.Context.IN_SCREEN)
                    .onClient(() -> KeybindSamples.CLICKS::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.shifted = keybinds.key("keybind_samples_shifted", "Shifted")
                    .defaultKey(GLFW.GLFW_KEY_KP_2)
                    .modifier(Keybind.Modifier.SHIFT)
                    .onClient(() -> KeybindSamples.SHIFTED_PRESSES::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.inGame = keybinds.key("keybind_samples_in_game", "In Game")
                    .defaultKey(GLFW.GLFW_KEY_KP_3)
                    .onClient(() -> KeybindSamples.GAME_PRESSES::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.inScreen = keybinds.key("keybind_samples_in_screen", "In Screen")
                    .defaultKey(GLFW.GLFW_KEY_KP_4)
                    .context(Keybind.Context.IN_SCREEN)
                    .onClient(() -> KeybindSamples.SCREEN_PRESSES::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.typed = keybinds.key("keybind_samples_typed", "Typed")
                    .defaultKey(GLFW.GLFW_KEY_J)
                    .context(Keybind.Context.IN_SCREEN)
                    .onClient(() -> KeybindSamples.TYPED_PRESSES::incrementAndGet)
                    .category(category)
                    .register();
            KeybindSamples.rebound = keybinds.key("keybind_samples_rebound", "Rebound")
                    .defaultKey(GLFW.GLFW_KEY_KP_5)
                    .category(category)
                    .register();
        }
    }

    private static class PlainScreen extends Screen {

        private PlainScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final class ClickTakingScreen extends KeybindSamples.PlainScreen {

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return true;
        }
    }
}
