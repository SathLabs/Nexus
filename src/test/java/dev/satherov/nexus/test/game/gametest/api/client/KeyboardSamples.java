package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

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
    public static void typeIntoTextField(Client client) {
        TextFieldScreen screen = new TextFieldScreen();
        client.minecraft().setScreen(screen);
        client.keyboard().type("nexus");
        Assertions.assertThat(screen.field.getValue()).isEqualTo("nexus");
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
    
    private static final class TextFieldScreen extends Screen {
        
        private final EditBox field = new EditBox(this.font, 200, 20, Component.empty());
        
        private TextFieldScreen() {
            super(Component.empty());
        }
        
        @Override
        protected void init() {
            this.addRenderableWidget(this.field);
            this.setInitialFocus(this.field);
        }
    }
}
