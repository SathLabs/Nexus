package dev.satherov.nexus.internal.keybind.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.KeyTick;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.keybind.KeySignal;

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
/// Builds the key mappings of the mod's keys, tracks each key's tick in game and in screens, fires each key through its activation,
/// and sends the key to the server.
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
    /// The tracked keys of the mod, empty until the key mappings are registered.
    ///
    private final List<ClientKeys.TrackedKey> keys = new ArrayList<>();

    ///
    /// If the keys on existing mappings are tracked, which they are from the first client tick on.
    ///
    private boolean existingTracked;

    ///
    /// If a screen was open on the last client tick.
    ///
    private boolean inScreen;

    ///
    /// If the window had focus on the last client tick.
    ///
    private boolean focused;

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
    /// Registers the categories the mod declared and one mapping per key the mod declared itself, and then locks the keybinds.
    ///
    /// @param event The event to register the categories and mappings in.
    ///
    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (Identifier category : this.keybinds.getCategories()) {
            event.registerCategory(new KeyMapping.Category(category));
        }

        for (Keybind keybind : this.keybinds.getAll()) {
            Keybind.Definition definition = keybind.getDefinition();
            if (definition == null) {
                continue;
            }

            KeyMapping mapping = new KeyMapping(
                    keybind.getName(),
                    ClientKeys.toConflictContext(definition.context()),
                    ClientKeys.toKeyModifier(definition.modifier()),
                    definition.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM,
                    definition.defaultKey(),
                    new KeyMapping.Category(definition.category())
            );

            event.register(mapping);
            this.keys.add(new ClientKeys.TrackedKey(keybind, mapping, definition.context()));
        }

        this.keybinds.lock();
    }

    ///
    /// Tracks the keys on existing mappings if this is the first client tick, and then moves every tracked key on by one client tick.
    ///
    /// @param event The event of the client tick that ended.
    ///
    private void onClientTick(ClientTickEvent.Post event) {
        if (!this.existingTracked) {
            this.trackExistingMappings();
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inScreen = minecraft.screen != null;
        boolean focused = minecraft.isWindowActive();
        boolean screenChanged = inScreen != this.inScreen;
        boolean lostFocus = this.focused && !focused;
        for (ClientKeys.TrackedKey key : this.keys) {
            key.tick(inScreen, screenChanged, lostFocus);
        }

        this.inScreen = inScreen;
        this.focused = focused;
    }

    ///
    /// Tracks every key on an existing mapping in the context [Keybind.Context#IN_GAME], and leaves a key inert whose mapping does not exist.
    ///
    private void trackExistingMappings() {
        for (Keybind keybind : this.keybinds.getAll()) {
            if (keybind.getDefinition() != null) {
                continue;
            }

            KeyMapping mapping = KeyMapping.get(keybind.getName());
            if (mapping != null) {
                this.keys.add(new ClientKeys.TrackedKey(keybind, mapping, Keybind.Context.IN_GAME));
            }
        }

        this.existingTracked = true;
    }

    ///
    /// Notes a press on every tracked key whose mapping vanilla set down for the pressed key.
    ///
    /// @param event The event of the key.
    ///
    private void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            this.keys.forEach(ClientKeys.TrackedKey::notePress);
        }
    }

    ///
    /// Notes a press on every tracked key whose mapping vanilla set down for the pressed button.
    ///
    /// @param event The event of the button.
    ///
    private void onMouseButton(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            this.keys.forEach(ClientKeys.TrackedKey::notePress);
        }
    }

    ///
    /// Keeps every [Keybind.Context#EVERYWHERE] key that is down in game down in the screen that opens.
    ///
    /// @param event The event of the opening screen.
    ///
    private void onScreenOpening(ScreenEvent.Opening event) {
        this.keys.forEach(ClientKeys.TrackedKey::carryIntoScreen);
    }

    ///
    /// Presses every tracked key bound to the key in the screen, unless the screen's focused edit box takes key input.
    ///
    /// @param event The event of a key press that the screen did not handle.
    ///
    private void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
        if (ClientKeys.isTyping(event.getScreen())) {
            return;
        }

        for (ClientKeys.TrackedKey key : this.keys) {
            if (key.mapping.matches(event.getKeyEvent()) && key.mapping.isConflictContextAndModifierActive()) {
                key.pressInScreen();
            }
        }
    }

    ///
    /// Releases every tracked key bound to the key in the screen.
    ///
    /// @param event The event of the key release.
    ///
    private void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        for (ClientKeys.TrackedKey key : this.keys) {
            if (key.mapping.matches(event.getKeyEvent())) {
                key.releaseInScreen();
            }
        }
    }

    ///
    /// Presses every tracked key bound to the button in the screen, unless the screen handled the click.
    ///
    /// @param event The event of the click.
    ///
    private void onScreenMouseButtonPressed(ScreenEvent.MouseButtonPressed.Post event) {
        if (event.wasClickHandled()) {
            return;
        }

        for (ClientKeys.TrackedKey key : this.keys) {
            if (key.mapping.matchesMouse(event.getMouseButtonEvent()) && key.mapping.isConflictContextAndModifierActive()) {
                key.pressInScreen();
            }
        }
    }

    ///
    /// Releases every tracked key bound to the button in the screen.
    ///
    /// @param event The event of the button release.
    ///
    private void onScreenMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        for (ClientKeys.TrackedKey key : this.keys) {
            if (key.mapping.matchesMouse(event.getMouseButtonEvent())) {
                key.releaseInScreen();
            }
        }
    }

    ///
    /// A key of the mod with its mapping and the state that Nexus tracks for it between client ticks.
    ///
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class TrackedKey {

        ///
        /// The key that is tracked.
        ///
        private final Keybind keybind;

        ///
        /// The mapping of the key.
        ///
        private final KeyMapping mapping;

        ///
        /// The context the key can be pressed in.
        ///
        private final Keybind.Context context;

        ///
        /// If the key was pressed since the last client tick.
        ///
        private boolean pressed;

        ///
        /// If the key is held down in a screen.
        ///
        private boolean heldInScreen;

        ///
        /// Notes a press if vanilla set the mapping down, which it does only while no screen is open.
        ///
        private void notePress() {
            this.pressed |= this.mapping.isDown();
        }

        ///
        /// Keeps the key down in the screen that opens if it is an [Keybind.Context#EVERYWHERE] key that is down in game.
        ///
        private void carryIntoScreen() {
            this.heldInScreen |= this.context == Keybind.Context.EVERYWHERE && this.mapping.isDown();
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
        /// Moves the key on by one client tick, updates the key with the new tick, sends the key going down or up if it syncs its held state,
        /// and then runs the client action and sends the firing if the key's activation fires on it.
        ///
        /// A key that is down goes up without the player letting go if the window lost focus, or if a screen opened over an
        /// [Keybind.Context#IN_GAME] key or closed under an [Keybind.Context#IN_SCREEN] key.
        ///
        /// @param inScreen      If a screen is open.
        /// @param screenChanged If a screen opened or closed since the last client tick.
        /// @param lostFocus     If the window lost focus since the last client tick.
        ///
        private void tick(boolean inScreen, boolean screenChanged, boolean lostFocus) {
            if (lostFocus) {
                this.mapping.setDown(false);
                this.pressed = false;
                this.heldInScreen = false;
            }

            if (!inScreen) {
                // Vanilla does not set a held key down again once the screen closes, so we do it here until its release comes in.
                if (this.heldInScreen && this.context == Keybind.Context.EVERYWHERE) {
                    this.mapping.setDown(true);
                }

                this.heldInScreen = false;
            }

            boolean down = inScreen ? this.context != Keybind.Context.IN_GAME && (this.pressed || this.heldInScreen) : this.pressed || this.mapping.isDown();
            KeyTick last = this.keybind.getTick();
            boolean wasDown = last.down();
            int idleTicks = last.idleTicks();
            if (!down) {
                idleTicks = wasDown ? 1 : idleTicks + 1;
            } else if (wasDown) {
                idleTicks = 0;
            }

            boolean interrupted = wasDown && !down && (lostFocus || screenChanged);
            KeyTick tick = new KeyTick(down, wasDown, down ? last.heldTicks() + 1 : 0, idleTicks, interrupted);
            this.pressed = false;
            this.keybind.update(tick);

            // If the firing went first, the server action would read the key as up on the tick it goes down.
            if (down != wasDown && this.keybind.isSyncHeld()) {
                this.send(down ? KeySignal.Down.INSTANCE : KeySignal.Up.INSTANCE);
            }

            if (!this.keybind.getActivation().fires(tick)) {
                return;
            }

            ClientOnly<Runnable> action = this.keybind.getClientAction();
            if (action != null) {
                action.get().run();
            }

            if (this.keybind.hasServerAction()) {
                this.send(new KeySignal.Fired(List.of()));
            }
        }

        ///
        /// Sends the given signal if the client is connected to a server that registered the key's payload.
        ///
        /// @param signal The signal to send.
        ///
        private void send(KeySignal signal) {
            ServerboundPayload<KeySignal> payload = this.keybind.getPayload();
            if (payload != null && Minecraft.getInstance().getConnection() != null && payload.canSendToServer()) {
                payload.sendToServer(signal);
            }
        }
    }
}
