package dev.satherov.nexus.internal.keybind.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.keybind.KeyActivatedEvent;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.KeyTick;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.keybind.KeySignal;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

///
/// The client listeners of the keybinds of every mod.
///
/// Builds the key mappings of each mod's keys, tracks each key's tick in game and in screens, fires each key through its activation,
/// and sends the key to the server.
///
/// Should only ever be loaded on the client.
///
@ApiStatus.Internal
@EventBusSubscriber(value = Dist.CLIENT, modid = Nexus.MOD_ID)
public final class ClientKeys {

    ///
    /// The client listeners of the client, created once this class is first loaded.
    ///
    private static final ClientKeys INSTANCE = new ClientKeys();

    ///
    /// If the key mappings were already registered.
    ///
    private static boolean mappingsRegistered;

    ///
    /// The tracked keys of every mod, filled as the key mappings are registered and on the first client tick.
    ///
    private final List<ClientKeys.TrackedKey> keys = new ArrayList<>();

    ///
    /// The keybinds whose keys on existing mappings are not tracked yet, added to from the threads the mods are constructed on.
    ///
    private final Queue<Keybinds> untracked = new ConcurrentLinkedQueue<>();

    ///
    /// The category of each identifier that a mapping was built with, holding the vanilla categories from the start.
    ///
    private final Map<Identifier, KeyMapping.Category> categories = new HashMap<>();

    ///
    /// How many client ticks have ended so far.
    ///
    private int clientTick;

    ///
    /// If the window had focus on the last client tick.
    ///
    private boolean focused;

    ///
    /// If a screen was open on the last client tick.
    ///
    private boolean inScreen;

    ///
    /// The connection of the client on the last client tick, or `null` if it was not connected.
    ///
    private @Nullable ClientPacketListener connection;

    ///
    /// How many left clicks vanilla takes as a right click until they are released, which it does on macOS while control is held.
    ///
    private int simulatedRightClicks;

    ///
    /// The button of the mouse event that is being handled, as vanilla reads it, or `null` before the first mouse event.
    ///
    private InputConstants.@Nullable Key button;

    ///
    /// Adds the vanilla categories and the listeners on the game bus for the client tick and the key and mouse events.
    ///
    /// The releases are read at the highest priority, even if another listener cancelled them.
    ///
    private ClientKeys() {
        for (KeyMapping.Category category : List.of(
                KeyMapping.Category.MOVEMENT,
                KeyMapping.Category.MISC,
                KeyMapping.Category.MULTIPLAYER,
                KeyMapping.Category.GAMEPLAY,
                KeyMapping.Category.INVENTORY,
                KeyMapping.Category.CREATIVE,
                KeyMapping.Category.SPECTATOR,
                KeyMapping.Category.DEBUG
        )) {
            this.categories.put(category.id(), category);
        }

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onClientTick);
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, this::onKey);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseButton.Post.class, this::onMouseButtonPressed);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, InputEvent.MouseButton.Pre.class, this::onMouseButton);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, ScreenEvent.KeyReleased.Pre.class, this::onScreenKeyReleased);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Post.class, this::onScreenMouseButtonPressed);
    }

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
    /// If the given screen takes the keys as text, which a sign screen and a focused text box or text area do.
    ///
    /// @param screen The screen that a key was pressed in.
    ///
    /// @return `true` if the screen takes the keys as text.
    ///
    private static boolean isTyping(Screen screen) {
        return screen instanceof AbstractSignEditScreen || ClientKeys.takesText(screen.getFocused());
    }

    ///
    /// If the given focused widget takes the keys as text, following the focus down through every container to the widget that has it.
    ///
    /// @param focused The focused widget, or `null` if none is focused.
    ///
    /// @return `true` if the widget takes the keys as text.
    ///
    private static boolean takesText(@Nullable GuiEventListener focused) {
        return switch (focused) {
            case EditBox box -> box.canConsumeInput();
            case MultiLineEditBox _ -> true;
            case ContainerEventHandler container -> ClientKeys.takesText(container.getFocused());
            case null, default -> false;
        };
    }

    ///
    /// If the given mapping can be pressed right now, with its conflict context active and its modifier held.
    ///
    /// A mapping without a modifier that is bound to a modifier key itself will be active while other modifiers are held.
    ///
    /// @param mapping The mapping to check.
    ///
    /// @return `true` if the mapping can be pressed.
    ///
    private static boolean isActive(KeyMapping mapping) {
        IKeyConflictContext context = mapping.getKeyConflictContext();
        KeyModifier modifier = mapping.getKeyModifier();
        return context.isActive() && (modifier.isActive(context) || (modifier == KeyModifier.NONE && KeyModifier.isKeyCodeModifier(mapping.getKey())));
    }

    ///
    /// If the screen handled the given click.
    ///
    /// A container screen reports every click as handled, so there only the left and right button and the buttons bound to a hotbar slot,
    /// to swapping the offhand or to picking a block count as handled.
    ///
    /// @param event The event of the click.
    ///
    /// @return `true` if the screen handled the click.
    ///
    private static boolean wasClickHandled(ScreenEvent.MouseButtonPressed.Post event) {
        if (!event.wasClickHandled()) {
            return false;
        }

        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return true;
        }

        MouseButtonEvent click = event.getMouseButtonEvent();
        Options options = Minecraft.getInstance().options;
        return click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT ||
                click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ||
                options.keyPickItem.matchesMouse(click) ||
                options.keySwapOffhand.matchesMouse(click) ||
                Arrays.stream(options.keyHotbarSlots).anyMatch(slot -> slot.matchesMouse(click));
    }

    ///
    /// If the given key is held on the keyboard right now.
    ///
    /// A mouse button never counts, since vanilla only puts the mappings of held keyboard keys back down.
    ///
    /// @param key The key to check.
    ///
    /// @return `true` if the key is a keyboard key that is held.
    ///
    private static boolean isHeld(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM && !key.equals(InputConstants.UNKNOWN) && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    ///
    /// If the client is connected to a server that registered the given payload.
    ///
    /// @param payload The payload of a key.
    ///
    /// @return `true` if the payload can be sent.
    ///
    private static boolean isReachable(ServerboundPayload<KeySignal> payload) {
        return Minecraft.getInstance().getConnection() != null && payload.canSendToServer();
    }

    ///
    /// Adds the client listeners of the given keybinds.
    ///
    /// Listens on the mod's bus for [RegisterKeyMappingsEvent], and tracks the mod's keys on existing mappings on the next client tick.
    ///
    /// If the key mappings were already registered, the keybinds will be locked instead.
    ///
    /// @param keybinds The keybinds of one mod.
    ///
    public static void attach(Keybinds keybinds) {
        if (ClientKeys.mappingsRegistered) {
            keybinds.lock();
            return;
        }

        keybinds.getMod().getEventBus().addListener(RegisterKeyMappingsEvent.class, event -> ClientKeys.INSTANCE.registerMappings(keybinds, event));
        ClientKeys.INSTANCE.untracked.add(keybinds);
    }

    ///
    /// Marks the key mappings as registered.
    ///
    /// Runs on the event bus of Nexus, which gets the event before every mod that depends on it.
    ///
    /// @param event The event the key mappings are registered in.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeys.mappingsRegistered = true;
    }

    ///
    /// Registers the categories the given keybinds declared and one mapping per key they declared themselves, and then locks the keybinds.
    ///
    /// @param keybinds The keybinds of one mod.
    /// @param event    The event to register the categories and mappings in.
    ///
    private void registerMappings(Keybinds keybinds, RegisterKeyMappingsEvent event) {
        for (Identifier category : keybinds.getCategories()) {
            event.registerCategory(this.toCategory(category));
        }

        for (Keybind keybind : keybinds.getAll()) {
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
                    this.toCategory(definition.category())
            );

            event.register(mapping);
            this.track(keybind, mapping, definition.context());
        }

        keybinds.lock();
    }

    ///
    /// The category of the given identifier, created on its first use.
    ///
    /// @param id The identifier of the category.
    ///
    /// @return The category that every mapping in it is built with.
    ///
    private KeyMapping.Category toCategory(Identifier id) {
        // The controls screen starts a new header wherever the category instance changes, so every key of one category shares one.
        return this.categories.computeIfAbsent(id, KeyMapping.Category::new);
    }

    ///
    /// Tracks the keys on existing mappings of every keybinds that are not tracked yet, then moves every tracked key on by one client tick,
    /// and then sends and fires every key that was moved on.
    ///
    /// @param event The event of the client tick that ended.
    ///
    private void onClientTick(ClientTickEvent.Post event) {
        // The keys are tracked before the count goes up, so a key tracked here has been up for one tick once this tick ends.
        for (Keybinds keybinds = this.untracked.poll(); keybinds != null; keybinds = this.untracked.poll()) {
            this.trackExistingMappings(keybinds);
        }

        this.clientTick++;
        Minecraft minecraft = Minecraft.getInstance();
        boolean inScreen = minecraft.screen != null;
        boolean focused = minecraft.isWindowActive();
        boolean lostFocus = this.focused && !focused;
        boolean screenClosed = this.inScreen && !inScreen;
        ClientPacketListener connection = minecraft.getConnection();
        boolean joined = connection != null && connection != this.connection;
        for (ClientKeys.TrackedKey key : this.keys) {
            key.tick(this.clientTick, inScreen, screenClosed, lostFocus);
        }

        // Every key is moved on before any key fires, so a client action that opens or closes a screen cannot change how the keys after it read this tick.
        for (ClientKeys.TrackedKey key : this.keys) {
            key.dispatch(joined);
        }

        this.focused = focused;
        this.inScreen = inScreen;
        this.connection = connection;
    }

    ///
    /// Tracks every key of the given keybinds on an existing mapping in the context of the mapping,
    /// and leaves a key inert whose mapping does not exist.
    ///
    /// @param keybinds The keybinds of one mod.
    ///
    private void trackExistingMappings(Keybinds keybinds) {
        for (Keybind keybind : keybinds.getAll()) {
            if (keybind.getDefinition() != null) {
                continue;
            }

            KeyMapping mapping = KeyMapping.get(keybind.getName());
            if (mapping != null) {
                this.track(keybind, mapping, this.toContext(mapping));
            }
        }
    }

    ///
    /// The context a key on the given existing mapping is tracked in.
    ///
    /// A mapping that a mod declared through Nexus keeps the context of that mod's key.
    /// Any other mapping is tracked in [Keybind.Context#IN_SCREEN] if it is in neoforge's GUI conflict context, and in [Keybind.Context#IN_GAME] if it is not.
    ///
    /// @param mapping The existing mapping.
    ///
    /// @return The context to track the key in.
    ///
    private Keybind.Context toContext(KeyMapping mapping) {
        for (ClientKeys.TrackedKey key : this.keys) {
            if (key.mapping == mapping && key.keybind.getDefinition() != null) {
                return key.context;
            }
        }

        return mapping.getKeyConflictContext() == KeyConflictContext.GUI ? Keybind.Context.IN_SCREEN : Keybind.Context.IN_GAME;
    }

    ///
    /// Tracks the given key on the given mapping from the current client tick on.
    ///
    /// @param keybind The key to track.
    /// @param mapping The mapping of the key.
    /// @param context The context the key can be pressed in.
    ///
    private void track(Keybind keybind, KeyMapping mapping, Keybind.Context context) {
        keybind.track(this.clientTick);
        this.keys.add(new ClientKeys.TrackedKey(keybind, mapping, context));
    }

    ///
    /// Presses every tracked key on the pressed key, and lets go of every tracked key that the released key held down.
    ///
    /// A repeat of a held key does nothing.
    ///
    /// @param event The event of a key that no screen took.
    ///
    private void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            Screen screen = Minecraft.getInstance().screen;
            this.press(InputConstants.getKey(event.getKeyEvent()), screen != null && !ClientKeys.isTyping(screen));
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            this.release(InputConstants.getKey(event.getKeyEvent()));
        }
    }

    ///
    /// Reads the button of the given event as vanilla reads it, and lets go of every tracked key that the button held down if it was released,
    /// in game and in screens.
    ///
    /// @param event The event of the button, read even if another listener cancelled it.
    ///
    private void onMouseButton(InputEvent.MouseButton.Pre event) {
        InputConstants.Key button = InputConstants.Type.MOUSE.getOrCreate(this.toVanillaButton(event.getButton(), event.getModifiers(), event.getAction() == GLFW.GLFW_PRESS));
        this.button = button;
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            this.release(button);
        }
    }

    ///
    /// Presses every tracked key on the pressed button whose mapping vanilla set down, which it does only while no screen is open.
    ///
    /// @param event The event of the button.
    ///
    private void onMouseButtonPressed(InputEvent.MouseButton.Post event) {
        InputConstants.Key button = this.button;
        if (event.getAction() == GLFW.GLFW_PRESS && button != null) {
            this.press(button, false);
        }
    }

    ///
    /// The button that vanilla reads for the given button of a mouse event.
    ///
    /// On macOS vanilla reads a left click with control held as the right button, and its release as well.
    ///
    /// @param button    The button of the event.
    /// @param modifiers The modifiers held during the event.
    /// @param pressed   If the button was pressed.
    ///
    /// @return The button vanilla reads.
    ///
    private int toVanillaButton(int button, int modifiers, boolean pressed) {
        if (!InputQuirks.SIMULATE_RIGHT_CLICK_WITH_LONG_LEFT_CLICK || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return button;
        }

        if (pressed && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            this.simulatedRightClicks++;
            return GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }

        if (!pressed && this.simulatedRightClicks > 0) {
            this.simulatedRightClicks--;
            return GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }

        return button;
    }

    ///
    /// Lets go of every tracked key that the released key held down in the screen.
    ///
    /// @param event The event of the key release, read even if another listener cancelled it.
    ///
    private void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        this.release(InputConstants.getKey(event.getKeyEvent()));
    }

    ///
    /// Presses every tracked key on the button in the screen, unless the screen handled the click.
    ///
    /// @param event The event of the click.
    ///
    private void onScreenMouseButtonPressed(ScreenEvent.MouseButtonPressed.Post event) {
        if (!ClientKeys.wasClickHandled(event)) {
            this.press(InputConstants.Type.MOUSE.getOrCreate(event.getMouseButtonEvent().button()), true);
        }
    }

    ///
    /// Presses every tracked key on the given key.
    ///
    /// @param key      The key that was pressed.
    /// @param inScreen If a screen took the press, and not as text.
    ///
    private void press(InputConstants.Key key, boolean inScreen) {
        for (ClientKeys.TrackedKey tracked : this.keys) {
            tracked.press(key, inScreen);
        }
    }

    ///
    /// Lets go of every tracked key that the given key held down.
    ///
    /// @param key The key that was released.
    ///
    private void release(InputConstants.Key key) {
        for (ClientKeys.TrackedKey tracked : this.keys) {
            tracked.release(key);
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
        /// The key that pressed the key, or `null` if it was let go since.
        ///
        private InputConstants.@Nullable Key heldBy;

        ///
        /// The key's tick on the client tick it was last moved on to, or `null` if it stayed up or was already sent and fired.
        ///
        private @Nullable KeyTick moved;

        ///
        /// If the key's mapping held the key down on the last client tick.
        ///
        private boolean heldByMapping;

        ///
        /// If the key's mapping came back down as a screen closed with no press of its key, and the key has not gone down since.
        ///
        private boolean restored;

        ///
        /// If the key went down on the client tick it was last moved on to only because its mapping came back down as a screen closed.
        ///
        private boolean silent;

        ///
        /// Presses the key if it is on the given key and either vanilla set its mapping down or the screen that took the press allows it.
        ///
        /// @param key      The key that was pressed.
        /// @param inScreen If a screen took the press, and not as text.
        ///
        private void press(InputConstants.Key key, boolean inScreen) {
            boolean allowed = this.mapping.isDown() || (inScreen && this.context != Keybind.Context.IN_GAME && ClientKeys.isActive(this.mapping));
            if (key.equals(this.mapping.getKey()) && allowed) {
                this.pressed = true;
                this.heldBy = key;
            }
        }

        ///
        /// Lets go of the key if the given key pressed it or is the modifier of its mapping.
        ///
        /// @param key The key that was released.
        ///
        private void release(InputConstants.Key key) {
            if (key.equals(this.heldBy) || this.mapping.getKeyModifier().matches(key)) {
                this.heldBy = null;
            }
        }

        ///
        /// Moves the key on to the given client tick.
        ///
        /// A key goes down on a press, and stays down while the key that pressed it is held and its context allows it.
        /// A key that reads its mapping is also down while the mapping reads down and its context allows it.
        ///
        /// A key that is down goes up without the player letting go if the window lost focus, or if a screen opened over an
        /// [Keybind.Context#IN_GAME] key or closed under an [Keybind.Context#IN_SCREEN] key.
        ///
        /// @param clientTick   The client tick that ended.
        /// @param inScreen     If a screen is open.
        /// @param screenClosed If a screen was open on the last client tick and none is open now.
        /// @param lostFocus    If the window lost focus since the last client tick.
        ///
        private void tick(int clientTick, boolean inScreen, boolean screenClosed, boolean lostFocus) {
            boolean wasDown = this.keybind.isDown();
            boolean allowed = inScreen ? this.context != Keybind.Context.IN_GAME : this.context != Keybind.Context.IN_SCREEN;
            boolean wasHeldByMapping = this.heldByMapping;
            boolean mappingDown = allowed && this.readsMapping() && this.mapping.isDown();
            // Vanilla turns a toggle back on once a screen closed, which is no press, and that holds until the key went down or the mapping went up.
            this.restored = mappingDown && !wasDown && !this.pressed && (this.restored || screenClosed);
            // Vanilla puts the mapping of a held key back down once a screen closed or the window took focus again, which is no press.
            this.heldByMapping = mappingDown && (wasDown || !ClientKeys.isHeld(this.mapping.getKey()));
            if (!wasDown && !this.pressed && !this.heldByMapping) {
                this.keybind.moveTo(clientTick);
                return;
            }

            boolean held = this.heldBy != null;
            if (lostFocus) {
                this.pressed = false;
                this.heldBy = null;
            }

            // A press counts even if a screen opened or closed since, but a key that only works in screens drops its press once the screen closed.
            boolean pressed = this.pressed && (inScreen || this.context != Keybind.Context.IN_SCREEN);
            boolean down = pressed || this.heldByMapping || (wasDown && allowed && this.heldBy != null && this.mapping.getKeyConflictContext().isActive());
            this.pressed = false;
            if (!down && !wasDown) {
                this.keybind.moveTo(clientTick);
                return;
            }

            // A key that its mapping held down counts as interrupted if a screen took it away, but not if the mapping went up.
            boolean interrupted = wasDown && !down && (held || lostFocus || (wasHeldByMapping && !allowed));
            this.silent = this.restored && down && !wasDown;
            this.moved = this.keybind.update(down, interrupted, clientTick);
        }

        ///
        /// If the key reads the state of its mapping as well as the presses of its key, which a key on an existing mapping without a modifier does.
        ///
        /// @return `true` if the key reads the state of its mapping.
        ///
        private boolean readsMapping() {
            // NeoForge can leave a mapping with a modifier down after its key was released without the modifier.
            return this.keybind.getDefinition() == null && this.mapping.getKeyModifier() == KeyModifier.NONE;
        }

        ///
        /// Sends the key going down if it syncs its held state, then fires the key if its activation fires on the tick it was moved on to,
        /// and then sends the key going up if it syncs its held state.
        ///
        /// A key that is down is sent as down again if the client joined a server since the last client tick.
        /// A key that its mapping put back down as a screen closed does not fire on the tick it goes down.
        ///
        /// @param joined If the client joined a server since the last client tick.
        ///
        private void dispatch(boolean joined) {
            KeyTick tick = this.moved;
            if (tick == null) {
                return;
            }

            this.moved = null;
            boolean synced = this.keybind.isSyncHeld();
            // If the firing went first, the server action would read the key as up on the tick it goes down.
            if (synced && tick.down() && (joined || !tick.wasDown())) {
                this.send(KeySignal.Down.INSTANCE);
            }

            if (!this.silent && this.keybind.getActivation().fires(tick)) {
                this.fire();
            }

            // If the release went first, the server action would read the key as up on the tick it goes up.
            if (synced && !tick.down()) {
                this.send(KeySignal.Up.INSTANCE);
            }
        }

        ///
        /// Posts a [KeyActivatedEvent] and then, unless it is cancelled, runs the client action, then the readers of the carried values in order,
        /// and then sends the firing with the values if the key has a server action.
        ///
        /// Sends nothing if a reader returns `null`, and the readers after it do not run.
        /// If the client is not connected to a server that registered the key's payload, no reader will run.
        ///
        private void fire() {
            if (NeoForge.EVENT_BUS.post(new KeyActivatedEvent(this.keybind)).isCanceled()) {
                return;
            }

            ClientOnly<Runnable> action = this.keybind.getClientAction();
            if (action != null) {
                action.get().run();
            }

            ServerboundPayload<KeySignal> payload = this.keybind.getPayload();
            if (!this.keybind.hasServerAction() || payload == null || !ClientKeys.isReachable(payload)) {
                return;
            }

            List<Object> values = new ArrayList<>();
            for (ClientOnly<? extends Supplier<?>> reader : this.keybind.getAllReaders()) {
                Object value = reader.get().get();
                if (value == null) {
                    return;
                }

                values.add(value);
            }

            payload.sendToServer(new KeySignal.Fired(values));
        }

        ///
        /// Sends the given signal if the client is connected to a server that registered the key's payload.
        ///
        /// @param signal The signal to send.
        ///
        private void send(KeySignal signal) {
            ServerboundPayload<KeySignal> payload = this.keybind.getPayload();
            if (payload != null && ClientKeys.isReachable(payload)) {
                payload.sendToServer(signal);
            }
        }
    }
}
