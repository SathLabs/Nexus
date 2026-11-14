package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;
import dev.satherov.nexus.gametest.api.client.TextBoxScreen;

import net.neoforged.neoforge.client.settings.KeyModifier;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import org.assertj.core.api.Assertions;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

///
/// The client tests that check key presses, held modifiers, and typed text reach the game.
///
public class KeyboardSamples {
    
    @ClientTest
    public static void pressOpensInventory(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.keyboard().press(client.minecraft().options.keyInventory.getKey().getValue());
        client.until("the inventory", () -> client.screen() instanceof CreativeModeInventoryScreen);
        client.leaveWorld();
    }
    
    @ClientTest
    public static void holdSetsModifier(Client client) {
        RecordingScreen screen = new RecordingScreen();
        client.minecraft().setScreen(screen);
        client.keyboard().hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        client.keyboard().press(GLFW.GLFW_KEY_A);
        client.keyboard().release(GLFW.GLFW_KEY_LEFT_SHIFT);
        client.keyboard().press(GLFW.GLFW_KEY_A);
        
        Assertions.assertThat(screen.presses)
                .filteredOn(event -> event.key() == GLFW.GLFW_KEY_A)
                .extracting(KeyEvent::hasShiftDown)
                .containsExactly(true, false);
    }
    
    @ClientTest
    public static void holdMakesShiftDown(Client client) {
        Window window = client.minecraft().getWindow();
        client.keyboard().hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)).isTrue();
        client.keyboard().release(GLFW.GLFW_KEY_LEFT_SHIFT);
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)).isFalse();
    }
    
    @ClientTest
    public static void holdMakesLetterDown(Client client) {
        Window window = client.minecraft().getWindow();
        client.keyboard().hold(GLFW.GLFW_KEY_K);
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_K)).isTrue();
        client.keyboard().release(GLFW.GLFW_KEY_K);
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_K)).isFalse();
    }
    
    @ClientTest
    public static void releaseAllMakesKeysUp(Client client) {
        Window window = client.minecraft().getWindow();
        Keyboard keyboard = client.keyboard();
        keyboard.hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        keyboard.hold(GLFW.GLFW_KEY_K);
        keyboard.releaseAll();
        
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)).isFalse();
        Assertions.assertThat(InputConstants.isKeyDown(window, GLFW.GLFW_KEY_K)).isFalse();
    }
    
    @ClientTest
    public static void shiftModifierFollowsHeldShift(Client client) {
        client.keyboard().hold(GLFW.GLFW_KEY_LEFT_SHIFT);
        Assertions.assertThat(KeyModifier.SHIFT.isActive(null)).isTrue();
        client.keyboard().release(GLFW.GLFW_KEY_LEFT_SHIFT);
        Assertions.assertThat(KeyModifier.SHIFT.isActive(null)).isFalse();
    }
    
    @ClientTest
    public static void typeIntoTextField(Client client) {
        TextBoxScreen screen = new TextBoxScreen();
        client.minecraft().setScreen(screen);
        client.keyboard().type("nexus");
        Assertions.assertThat(screen.getBox().getValue()).isEqualTo("nexus");
    }
    
    private static final class RecordingScreen extends Screen {
        
        private final List<KeyEvent> presses = new ArrayList<>();
        
        private RecordingScreen() {
            super(Component.empty());
        }
        
        @Override
        public boolean keyPressed(KeyEvent event) {
            this.presses.add(event);
            return true;
        }
    }
}
