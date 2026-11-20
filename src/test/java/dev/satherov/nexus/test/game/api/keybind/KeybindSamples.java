package dev.satherov.nexus.test.game.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;
import dev.satherov.nexus.gametest.api.client.TextBoxScreen;
import dev.satherov.nexus.gametest.mixin.KeyboardHandlerAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;

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
    private static final String TOGGLE_MAPPING = "key.keybind_samples_toggle";
    private static final String DRIVEN_MAPPING = "key.keybind_samples_driven";
    private static final String RESTORED_MAPPING = "key.keybind_samples_restored";
    private static final AtomicInteger PRESSES = new AtomicInteger();
    private static final AtomicInteger SHIFTED_PRESSES = new AtomicInteger();
    private static final AtomicInteger GAME_PRESSES = new AtomicInteger();
    private static final AtomicInteger SCREEN_PRESSES = new AtomicInteger();
    private static final AtomicInteger TYPED_PRESSES = new AtomicInteger();
    private static final AtomicInteger CLICKS = new AtomicInteger();
    private static final AtomicInteger TOGGLE_RELEASES = new AtomicInteger();
    private static final AtomicInteger DRIVEN_PRESSES = new AtomicInteger();
    private static final AtomicInteger RESTORED_PRESSES = new AtomicInteger();
    private static final AtomicInteger RESTORED_SERVER_RUNS = new AtomicInteger();

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
    private static @Nullable Keybind everywhere;
    private static @Nullable Keybind modifier;
    private static @Nullable Keybind vanillaCategorized;
    private static @Nullable Keybind toggled;
    private static @Nullable Keybind driven;
    private static @Nullable Keybind restored;
    private static volatile @Nullable Throwable lateRefusal;

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
    public static void keysOfOneCategoryShareItsInstance(Client client) {
        KeyMapping.Category category = KeybindSamples.mapping(KeybindSamples.declared).getCategory();
        Assertions.assertThat(KeybindSamples.mapping(KeybindSamples.pressed).getCategory()).as("the category of a second key in it").isSameAs(category);
        Assertions.assertThat(KeybindSamples.mapping(KeybindSamples.vanillaCategorized).getCategory()).as("the category of a key in a vanilla category").isSameAs(KeyMapping.Category.MISC);
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
    public static void shiftAloneFiresNothingAfterTheKeyWasHeldWhileAScreenClosed(Client client) {
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        KeybindSamples.SHIFTED_PRESSES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_KP_2);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        client.minecraft().setScreen(null);
        // The window may not have focus, so the held keys are put back down here the way a focused window does it.
        KeyMapping.setAll();
        keyboard.release(GLFW.GLFW_KEY_KP_2);

        keyboard.hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        keyboard.release(GLFW.GLFW_KEY_LEFT_SHIFT);
        Assertions.assertThat(KeybindSamples.SHIFTED_PRESSES.get()).as("runs on shift alone").isZero();
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
    public static void screenKeyPressedAsItsScreenClosesFiresNothing(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        client.ticks(KeybindSamples.SETTLE_TICKS);
        KeybindSamples.SCREEN_PRESSES.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_KP_4);
        client.minecraft().setScreen(null);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.SCREEN_PRESSES.get()).as("runs of the client action after the screen closed").isZero();
        Assertions.assertThat(Objects.requireNonNull(KeybindSamples.inScreen).isDown()).as("the key after the screen closed").isFalse();
        client.leaveWorld();
    }

    @ClientTest
    public static void repeatOfAKeyHeldBeforeItsScreenOpenedFiresNothing(Client client) {
        Keybind inScreen = Objects.requireNonNull(KeybindSamples.inScreen);
        Keyboard keyboard = client.keyboard();
        Minecraft minecraft = client.minecraft();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        KeybindSamples.SCREEN_PRESSES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_KP_4);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        minecraft.setScreen(new KeybindSamples.PlainScreen());
        ((KeyboardHandlerAccess) minecraft.keyboardHandler).invokeKeyPress(minecraft.getWindow().handle(), GLFW.GLFW_REPEAT, new KeyEvent(GLFW.GLFW_KEY_KP_4, 0, 0));
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(inScreen.isDown()).as("the key repeated in the screen").isFalse();
        Assertions.assertThat(KeybindSamples.SCREEN_PRESSES.get()).as("runs of the client action on the repeat").isZero();
        keyboard.release(GLFW.GLFW_KEY_KP_4);
        minecraft.setScreen(null);
        client.leaveWorld();
    }

    @ClientTest
    public static void screenKeyGoesUpOnAReleaseAnotherListenerCancelled(Client client) {
        Keybind inScreen = Objects.requireNonNull(KeybindSamples.inScreen);
        Keyboard keyboard = client.keyboard();
        KeybindSamples.PlainScreen screen = new KeybindSamples.PlainScreen();
        client.minecraft().setScreen(screen);
        keyboard.hold(GLFW.GLFW_KEY_KP_4);
        client.until("the key to read down in the screen", inScreen::isDown);
        // The release arrives already cancelled, the way a listener that ran before Nexus would leave it.
        ScreenEvent.KeyReleased.Pre release = new ScreenEvent.KeyReleased.Pre(screen, new KeyEvent(GLFW.GLFW_KEY_KP_4, 0, 0));
        release.setCanceled(true);
        NeoForge.EVENT_BUS.post(release);
        client.until("the key to go up on its cancelled release", () -> !inScreen.isDown());
        keyboard.release(GLFW.GLFW_KEY_KP_4);
        client.minecraft().setScreen(null);
    }

    @ClientTest
    public static void screenKeyOnAModifierKeyReadsDownInAScreen(Client client) {
        Keybind modifier = Objects.requireNonNull(KeybindSamples.modifier);
        Keyboard keyboard = client.keyboard();
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        keyboard.hold(GLFW.GLFW_KEY_RIGHT_CONTROL);
        client.until("the key to read down in the screen", modifier::isDown);
        keyboard.release(GLFW.GLFW_KEY_RIGHT_CONTROL);
        client.until("the key to go up on its release", () -> !modifier.isDown());
        client.minecraft().setScreen(null);
    }

    @ClientTest
    public static void everywhereKeyReleasedRightAfterItsScreenClosedGoesUp(Client client) {
        Keybind everywhere = Objects.requireNonNull(KeybindSamples.everywhere);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        keyboard.hold(GLFW.GLFW_KEY_F9);
        client.until("the key to read down", everywhere::isDown);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(everywhere.isDown()).as("the key held in the screen").isTrue();

        client.minecraft().setScreen(null);
        keyboard.release(GLFW.GLFW_KEY_F9);
        client.until("the key to go up on its release", () -> !everywhere.isDown());
        client.leaveWorld();
    }

    @ClientTest
    public static void keyReboundWhileHeldGoesUpOnTheReleaseOfItsOldKey(Client client) {
        Keybind everywhere = Objects.requireNonNull(KeybindSamples.everywhere);
        KeyMapping mapping = KeybindSamples.mapping(everywhere);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        keyboard.hold(GLFW.GLFW_KEY_F9);
        client.until("the key to read down", everywhere::isDown);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F12));
        KeyMapping.resetMapping();
        try {
            keyboard.release(GLFW.GLFW_KEY_F9);
            client.ticks(KeybindSamples.SETTLE_TICKS);
            client.minecraft().setScreen(null);
            client.ticks(KeybindSamples.SETTLE_TICKS);
            Assertions.assertThat(everywhere.isDown()).as("the key after the release of the key it was bound to").isFalse();
        } finally {
            mapping.setKey(mapping.getDefaultKey());
            KeyMapping.resetMapping();
        }

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
    public static void typingTheLetterIntoATextAreaFiresNothing(Client client) {
        KeybindSamples.NotesScreen screen = new KeybindSamples.NotesScreen();
        client.minecraft().setScreen(screen);
        KeybindSamples.TYPED_PRESSES.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_J);
        client.keyboard().type("j");
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(screen.notes.getValue()).isEqualTo("j");
        Assertions.assertThat(KeybindSamples.TYPED_PRESSES.get()).as("runs of the client action while typing").isZero();
    }

    @ClientTest
    public static void typingTheLetterOnASignFiresNothing(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player);
        SignBlockEntity sign = new SignBlockEntity(player.blockPosition(), Blocks.OAK_SIGN.defaultBlockState());
        sign.setLevel(player.level());
        client.minecraft().setScreen(new SignEditScreen(sign, true, false));
        KeybindSamples.TYPED_PRESSES.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_J);
        client.keyboard().type("j");
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(client.screen()).as("the screen after typing").isInstanceOf(SignEditScreen.class);
        Assertions.assertThat(KeybindSamples.TYPED_PRESSES.get()).as("runs of the client action while writing on the sign").isZero();
        client.minecraft().setScreen(null);
        client.leaveWorld();
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
    public static void mouseKeyFiresOnAClickInAContainerScreen(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new InventoryScreen(Objects.requireNonNull(client.minecraft().player)));
        KeybindSamples.CLICKS.set(0);
        client.mouse().click(GLFW.GLFW_MOUSE_BUTTON_4);
        client.until("the client action to run on a click in the container screen", () -> KeybindSamples.CLICKS.get() > 0);
        client.minecraft().setScreen(null);
        client.leaveWorld();
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
    public static void existingKeyOnAToggleFollowsTheToggle(Client client) {
        Keybind toggled = Objects.requireNonNull(KeybindSamples.toggled);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        KeybindSamples.TOGGLE_RELEASES.set(0);
        keyboard.press(GLFW.GLFW_KEY_PAGE_UP);
        client.until("the key to read down once the toggle is on", toggled::isDown);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(toggled.isDown()).as("the key after the tap that turned the toggle on").isTrue();
        Assertions.assertThat(KeybindSamples.TOGGLE_RELEASES.get()).as("runs of the release while the toggle is on").isZero();

        keyboard.press(GLFW.GLFW_KEY_PAGE_UP);
        client.until("the release to run once the toggle is off", () -> KeybindSamples.TOGGLE_RELEASES.get() > 0);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(toggled.isDown()).as("the key after the tap that turned the toggle off").isFalse();
        Assertions.assertThat(KeybindSamples.TOGGLE_RELEASES.get()).as("runs of the release").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void existingKeyFollowsAMappingThatCodeSetDown(Client client) {
        Keybind driven = Objects.requireNonNull(KeybindSamples.driven);
        KeyMapping mapping = KeybindSamples.mapping(driven);
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        // A mapping that reads down on the first tick after a screen closed counts as a toggle that vanilla turned back on.
        client.ticks(KeybindSamples.SETTLE_TICKS);
        KeybindSamples.DRIVEN_PRESSES.set(0);
        mapping.setDown(true);
        try {
            client.until("the key to read down", driven::isDown);
            client.ticks(KeybindSamples.SETTLE_TICKS);
            Assertions.assertThat(driven.isDown()).as("the key while its mapping is down").isTrue();
            Assertions.assertThat(KeybindSamples.DRIVEN_PRESSES.get()).as("runs of the client action").isEqualTo(1);
        } finally {
            mapping.setDown(false);
        }

        client.until("the key to go up once its mapping went up", () -> !driven.isDown());
        client.leaveWorld();
    }

    @ClientTest
    public static void existingKeyHeldWhileAScreenClosesDoesNotFire(Client client) {
        Keybind driven = Objects.requireNonNull(KeybindSamples.driven);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        KeybindSamples.DRIVEN_PRESSES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_PAGE_DOWN);
        client.ticks(KeybindSamples.SETTLE_TICKS);
        client.minecraft().setScreen(null);
        // The window may not have focus, so the held keys are put back down here the way a focused window does it.
        KeyMapping.setAll();
        client.ticks(KeybindSamples.SETTLE_TICKS);
        Assertions.assertThat(KeybindSamples.mapping(driven).isDown()).as("the mapping after the screen closed").isTrue();
        Assertions.assertThat(driven.isDown()).as("the key held while the screen closed").isFalse();
        Assertions.assertThat(KeybindSamples.DRIVEN_PRESSES.get()).as("runs of the client action").isZero();
        keyboard.release(GLFW.GLFW_KEY_PAGE_DOWN);
        client.leaveWorld();
    }

    @ClientTest
    public static void existingKeyOnARestoredToggleDoesNotFireAgainAsTheScreenCloses(Client client) {
        Keybind restored = Objects.requireNonNull(KeybindSamples.restored);
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        ServerPlayer player = client.serverLevel().players().getFirst();
        KeybindSamples.RESTORED_PRESSES.set(0);
        KeybindSamples.RESTORED_SERVER_RUNS.set(0);
        keyboard.press(GLFW.GLFW_KEY_KP_DIVIDE);
        client.until("the server to read the key down once the toggle is on", () -> restored.isDown(player));
        client.minecraft().setScreen(new KeybindSamples.PlainScreen());
        client.until("the server to read the key up under the screen", () -> !restored.isDown(player));
        client.minecraft().setScreen(null);
        client.until("the key to read down once vanilla turned the toggle back on", restored::isDown);
        Assertions.assertThat(KeybindSamples.mapping(restored).isDown()).as("the mapping after the screen closed").isTrue();
        Assertions.assertThat(restored.getHeldTicks()).as("the held ticks on the tick the toggle came back").isEqualTo(1);

        client.until("the server to read the key down again", () -> restored.isDown(player));
        int settled = player.level().getServer().getTickCount() + KeybindSamples.SETTLE_TICKS;
        client.until("the server to handle what the client sent", () -> player.level().getServer().getTickCount() >= settled);
        Assertions.assertThat(restored.isDown()).as("the key while the toggle is back on").isTrue();
        Assertions.assertThat(KeybindSamples.RESTORED_PRESSES.get()).as("runs of the client action").isEqualTo(1);
        Assertions.assertThat(KeybindSamples.RESTORED_SERVER_RUNS.get()).as("runs of the server action").isEqualTo(1);
        keyboard.press(GLFW.GLFW_KEY_KP_DIVIDE);
        client.until("the key to go up once the toggle is off", () -> !restored.isDown());
        client.leaveWorld();
    }

    @ClientTest
    public static void registerAfterTheMappingsWereRegisteredThrows(Client client) {
        Assertions.assertThat(KeybindSamples.lateRefusal)
                .as("the refusal of a key registered between the key mappings and the payload handlers event")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after the key mappings were registered");
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
            KeybindSamples.everywhere = keybinds.key("keybind_samples_everywhere", "Everywhere")
                    .defaultKey(GLFW.GLFW_KEY_F9)
                    .context(Keybind.Context.EVERYWHERE)
                    .category(category)
                    .register();
            KeybindSamples.modifier = keybinds.key("keybind_samples_modifier", "Modifier")
                    .defaultKey(GLFW.GLFW_KEY_RIGHT_CONTROL)
                    .context(Keybind.Context.IN_SCREEN)
                    .category(category)
                    .register();
            KeybindSamples.vanillaCategorized = keybinds.key("keybind_samples_vanilla_categorized", "Vanilla Categorized")
                    .category(Identifier.withDefaultNamespace("misc"))
                    .register();
            KeybindSamples.toggled = keybinds.existing(KeybindSamples.TOGGLE_MAPPING)
                    .activation(Activation.release())
                    .onClient(() -> KeybindSamples.TOGGLE_RELEASES::incrementAndGet)
                    .register();
            KeybindSamples.driven = keybinds.existing(KeybindSamples.DRIVEN_MAPPING)
                    .onClient(() -> KeybindSamples.DRIVEN_PRESSES::incrementAndGet)
                    .register();
            KeybindSamples.restored = keybinds.existing(KeybindSamples.RESTORED_MAPPING)
                    .syncHeld()
                    .onClient(() -> KeybindSamples.RESTORED_PRESSES::incrementAndGet)
                    .onServer(_ -> KeybindSamples.RESTORED_SERVER_RUNS.incrementAndGet())
                    .register();
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID, value = Dist.CLIENT)
    public static final class ClientSide {

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            Keybinds keybinds = new Keybinds(Nexus.getMod());
            KeybindSamples.lateRefusal = Assertions.catchThrowable(() -> keybinds.key("keybind_samples_late", "Late").register());
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(new ToggleKeyMapping(KeybindSamples.TOGGLE_MAPPING, GLFW.GLFW_KEY_PAGE_UP, KeyMapping.Category.MISC, () -> true, false));
            event.register(new KeyMapping(KeybindSamples.DRIVEN_MAPPING, GLFW.GLFW_KEY_PAGE_DOWN, KeyMapping.Category.MISC));
            event.register(new ToggleKeyMapping(KeybindSamples.RESTORED_MAPPING, GLFW.GLFW_KEY_KP_DIVIDE, KeyMapping.Category.MISC, () -> true, true));
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

    private static final class NotesScreen extends KeybindSamples.PlainScreen {

        private static final int WIDTH = 200;
        private static final int HEIGHT = 100;

        private final MultiLineEditBox notes = MultiLineEditBox.builder().build(this.font, KeybindSamples.NotesScreen.WIDTH, KeybindSamples.NotesScreen.HEIGHT, Component.empty());

        @Override
        protected void init() {
            this.addRenderableWidget(this.notes);
            this.setInitialFocus(this.notes);
        }
    }
}
