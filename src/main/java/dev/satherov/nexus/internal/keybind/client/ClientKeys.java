package dev.satherov.nexus.internal.keybind.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.keybind.KeyDefinition;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

///
/// The client listeners of one mod's keybinds.
///
/// Builds the key mappings of the mod's keys and tracks whether each key is down, in game and in screens.
///
/// Should only ever be loaded on the client.
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientKeys {

    ///
    /// The keybinds the mappings are built from.
    ///
    private final Keybinds keybinds;

    ///
    /// The mappings of the mod's keys, empty until the key mappings are registered.
    ///
    private final List<ClientKeys.TrackedMapping> mappings = new ArrayList<>();

    ///
    /// The NeoForge key modifier of the given modifier.
    ///
    /// @param modifier The modifier of a declared key, or `null` if it takes none.
    ///
    /// @return The NeoForge key modifier, or [KeyModifier#NONE] if the key takes no modifier.
    ///
    private static KeyModifier toKeyModifier(Keybind.@Nullable Modifier modifier) {
        return switch (modifier) {
            case null -> KeyModifier.NONE;
            case CONTROL -> KeyModifier.CONTROL;
            case SHIFT -> KeyModifier.SHIFT;
            case ALT -> KeyModifier.ALT;
        };
    }

    ///
    /// The NeoForge conflict context of the given context.
    ///
    /// @param context The context of a declared key.
    ///
    /// @return The NeoForge conflict context.
    ///
    private static KeyConflictContext toConflictContext(Keybind.Context context) {
        return switch (context) {
            case IN_GAME -> KeyConflictContext.IN_GAME;
            case IN_SCREEN -> KeyConflictContext.GUI;
            case EVERYWHERE -> KeyConflictContext.UNIVERSAL;
        };
    }

    ///
    /// If the focused widget of the given screen is an edit box that takes key input, as vanilla checks it.
    ///
    /// @param screen The screen that a key was pressed in.
    ///
    /// @return `true` if the focused widget takes key input.
    ///
    private static boolean isTyping(Screen screen) {
        return screen.getFocused() instanceof EditBox box && box.canConsumeInput();
    }

    ///
    /// Creates the client listeners of the given keybinds.
    ///
    /// Listens on the mod's bus for [RegisterKeyMappingsEvent], and on the game bus for the client tick, the screen opening and
    /// the key and mouse events.
    ///
    /// @param keybinds The keybinds of one mod.
    ///
    public static void attach(Keybinds keybinds) {
        ClientKeys keys = new ClientKeys(keybinds);
        keybinds.getMod().getEventBus().addListener(RegisterKeyMappingsEvent.class, keys::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, keys::onClientTick);
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, keys::onKey);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Post.class, keys::onMouseButton);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, keys::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Post.class, keys::onScreenKeyPressed);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyReleased.Pre.class, keys::onScreenKeyReleased);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Post.class, keys::onScreenMouseButtonPressed);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonReleased.Pre.class, keys::onScreenMouseButtonReleased);
    }

    ///
    /// If the given key is down as Nexus tracks it.
    ///
    /// @param keybind The key to check.
    ///
    /// @return `true` if the key was down on the last client tick, or `false` if its mapping was never built.
    ///
    public static boolean isDown(Keybind keybind) {
        return KeyMapping.get(keybind.getName()) instanceof ClientKeys.TrackedMapping mapping && mapping.down;
    }

    ///
    /// Registers the categories the mod declared and one mapping per key of the mod, and then locks the keybinds.
    ///
    /// @param event The event to register the categories and mappings in.
    ///
    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (Identifier category : this.keybinds.getCategories()) {
            event.registerCategory(new KeyMapping.Category(category));
        }

        for (Keybind keybind : this.keybinds.getAll()) {
            ClientKeys.TrackedMapping mapping = new ClientKeys.TrackedMapping(keybind);
            event.register(mapping);
            this.mappings.add(mapping);
        }

        this.keybinds.lock();
    }

    ///
    /// Moves every mapping on by one client tick.
    ///
    /// @param event The event of the client tick that ended.
    ///
    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inScreen = minecraft.screen != null;
        boolean focused = minecraft.isWindowActive();
        for (ClientKeys.TrackedMapping mapping : this.mappings) {
            mapping.tick(inScreen, focused);
        }
    }

    ///
    /// Notes a press on every mapping that vanilla set down for the pressed key.
    ///
    /// @param event The event of the key.
    ///
    private void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            this.mappings.forEach(ClientKeys.TrackedMapping::notePress);
        }
    }

    ///
    /// Notes a press on every mapping that vanilla set down for the pressed button.
    ///
    /// @param event The event of the button.
    ///
    private void onMouseButton(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            this.mappings.forEach(ClientKeys.TrackedMapping::notePress);
        }
    }

    ///
    /// Keeps every [Keybind.Context#EVERYWHERE] mapping that is down in game down in the screen that opens.
    ///
    /// @param event The event of the opening screen.
    ///
    private void onScreenOpening(ScreenEvent.Opening event) {
        this.mappings.forEach(ClientKeys.TrackedMapping::carryIntoScreen);
    }

    ///
    /// Presses every mapping of the key in the screen, unless the screen's focused edit box takes key input.
    ///
    /// @param event The event of a key press that the screen did not handle.
    ///
    private void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
        if (ClientKeys.isTyping(event.getScreen())) {
            return;
        }

        for (ClientKeys.TrackedMapping mapping : this.mappings) {
            if (mapping.matches(event.getKeyEvent()) && mapping.isConflictContextAndModifierActive()) {
                mapping.pressInScreen();
            }
        }
    }

    ///
    /// Releases every mapping of the key in the screen.
    ///
    /// @param event The event of the key release.
    ///
    private void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        for (ClientKeys.TrackedMapping mapping : this.mappings) {
            if (mapping.matches(event.getKeyEvent())) {
                mapping.releaseInScreen();
            }
        }
    }

    ///
    /// Presses every mapping of the button in the screen, unless the screen handled the click.
    ///
    /// @param event The event of the click.
    ///
    private void onScreenMouseButtonPressed(ScreenEvent.MouseButtonPressed.Post event) {
        if (event.wasClickHandled()) {
            return;
        }

        for (ClientKeys.TrackedMapping mapping : this.mappings) {
            if (mapping.matchesMouse(event.getMouseButtonEvent()) && mapping.isConflictContextAndModifierActive()) {
                mapping.pressInScreen();
            }
        }
    }

    ///
    /// Releases every mapping of the button in the screen.
    ///
    /// @param event The event of the button release.
    ///
    private void onScreenMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        for (ClientKeys.TrackedMapping mapping : this.mappings) {
            if (mapping.matchesMouse(event.getMouseButtonEvent())) {
                mapping.releaseInScreen();
            }
        }
    }

    ///
    /// A key mapping built from a declared key, with the down state that Nexus tracks for it.
    ///
    private static final class TrackedMapping extends KeyMapping {

        ///
        /// The declared key the mapping is built from.
        ///
        private final KeyDefinition definition;

        ///
        /// If the key was pressed since the last client tick.
        ///
        private boolean pressed;

        ///
        /// If the key is held down in a screen.
        ///
        private boolean heldInScreen;

        ///
        /// If the key was down on the last client tick.
        ///
        private boolean down;

        ///
        /// If the key was down on the client tick before the last.
        ///
        private boolean wasDown;

        ///
        /// The number of ticks the key has been down, `0` while it is up.
        ///
        private int heldTicks;

        ///
        /// The number of ticks the key has been up, or the length of the gap that ended on the tick it went down.
        ///
        private int idleTicks;

        ///
        /// If the key went up on the last client tick without the player letting go.
        ///
        private boolean interrupted;

        ///
        /// If a screen was open on the last client tick.
        ///
        private boolean inScreen;

        ///
        /// If the window had focus on the last client tick.
        ///
        private boolean focused;

        ///
        /// Should only ever be called from [ClientKeys#onRegisterKeyMappings(RegisterKeyMappingsEvent)].
        ///
        /// @param keybind The declared key to build the mapping from.
        ///
        private TrackedMapping(Keybind keybind) {
            KeyDefinition definition = keybind.getDefinition();
            super(
                    keybind.getName(),
                    ClientKeys.toConflictContext(definition.context()),
                    ClientKeys.toKeyModifier(definition.modifier()),
                    definition.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM,
                    definition.defaultKey(),
                    new KeyMapping.Category(definition.category())
            );
            this.definition = definition;
        }

        ///
        /// Notes a press if vanilla set the mapping down, which it does only while no screen is open.
        ///
        private void notePress() {
            this.pressed |= this.isDown();
        }

        ///
        /// Keeps the key down in the screen that opens if it is an [Keybind.Context#EVERYWHERE] key that is down in game.
        ///
        private void carryIntoScreen() {
            this.heldInScreen |= this.definition.context() == Keybind.Context.EVERYWHERE && this.isDown();
        }

        ///
        /// Presses the key in a screen.
        ///
        private void pressInScreen() {
            this.pressed = true;
            this.heldInScreen = true;
        }

        ///
        /// Releases the key in a screen.
        ///
        private void releaseInScreen() {
            this.heldInScreen = false;
        }

        ///
        /// Moves the key on by one client tick and then runs the client action if the key went down on it.
        ///
        /// A key that is down goes up without the player letting go if the window lost focus, or if a screen opened over an
        /// [Keybind.Context#IN_GAME] key or closed under an [Keybind.Context#IN_SCREEN] key.
        ///
        /// @param inScreen If a screen is open.
        /// @param focused  If the window has focus.
        ///
        private void tick(boolean inScreen, boolean focused) {
            boolean lostFocus = this.focused && !focused;
            if (lostFocus) {
                this.release();
                this.pressed = false;
                this.heldInScreen = false;
            }

            if (!inScreen) {
                // Vanilla does not set a held key down again once the screen closes, so we do it here until its release comes in.
                if (this.heldInScreen && this.definition.context() == Keybind.Context.EVERYWHERE) {
                    this.setDown(true);
                }

                this.heldInScreen = false;
            }

            boolean down = inScreen ? this.definition.context() != Keybind.Context.IN_GAME && (this.pressed || this.heldInScreen) : this.pressed || this.isDown();
            this.interrupted = this.down && !down && (lostFocus || inScreen != this.inScreen);
            this.wasDown = this.down;
            this.down = down;
            this.heldTicks = down ? this.heldTicks + 1 : 0;
            if (!down) {
                this.idleTicks = this.wasDown ? 1 : this.idleTicks + 1;
            } else if (this.wasDown) {
                this.idleTicks = 0;
            }

            this.pressed = false;
            this.inScreen = inScreen;
            this.focused = focused;

            ClientOnly<Runnable> action = this.definition.clientAction();
            if (down && !this.wasDown && action != null) {
                action.get().run();
            }
        }
    }
}
