package dev.satherov.nexus.api.keybind;

import net.minecraft.resources.Identifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A builder of a key that the mod declares itself.
///
/// Can be started through [Keybinds#key(String, String)].
///
public final class KeybindBuilder extends BindingBuilder<KeybindBuilder> {
    
    ///
    /// The default key of a key that is unbound.
    ///
    private static final int UNBOUND = -1;
    
    ///
    /// The name the key was declared with.
    ///
    private final String name;
    
    ///
    /// The English default of the key's name.
    ///
    private final String english;
    
    ///
    /// The default GLFW key code or mouse button.
    ///
    private int defaultKey = KeybindBuilder.UNBOUND;
    
    ///
    /// If the default key is a mouse button.
    ///
    private boolean mouse;
    
    ///
    /// The modifier, or `null` if the key takes none.
    ///
    private Keybind.@Nullable Modifier modifier;
    
    ///
    /// The context the key can be pressed in.
    ///
    private Keybind.Context context = Keybind.Context.IN_GAME;
    
    ///
    /// The identifier of the category the key is listed in.
    ///
    private Identifier category;
    
    ///
    /// Should only ever be called from [Keybinds#key(String, String)].
    ///
    /// @param keybinds        The keybinds the key is registered in.
    /// @param name            The name of the key.
    /// @param english         The English default of the key's name.
    /// @param defaultCategory The identifier of the category the key is listed in unless [#category(Identifier)] is called.
    ///
    @ApiStatus.Internal
    public KeybindBuilder(Keybinds keybinds, String name, String english, Identifier defaultCategory) {
        super(keybinds, name);
        this.name = name;
        this.english = english;
        this.category = defaultCategory;
    }
    
    ///
    /// Sets the default key to the given GLFW key code.
    ///
    /// Replaces a default mouse button set before.
    ///
    /// @param key The GLFW key code.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public KeybindBuilder defaultKey(int key) {
        this.defaultKey = key;
        this.mouse = false;
        return this;
    }
    
    ///
    /// Sets the default key to the given GLFW mouse button.
    ///
    /// Replaces a default key code set before.
    ///
    /// @param button The GLFW mouse button.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public KeybindBuilder defaultMouseButton(int button) {
        this.defaultKey = button;
        this.mouse = true;
        return this;
    }
    
    ///
    /// Sets the modifier that has to be held together with the key.
    ///
    /// @param modifier The modifier.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public KeybindBuilder modifier(Keybind.Modifier modifier) {
        this.modifier = modifier;
        return this;
    }
    
    ///
    /// Sets the context the key can be pressed in.
    ///
    /// @param context The context.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public KeybindBuilder context(Keybind.Context context) {
        this.context = context;
        return this;
    }
    
    ///
    /// Sets the category the key is listed in.
    ///
    /// @param category The identifier of a category the mod declared, of a vanilla category, or of a category of another mod.
    ///
    /// @return The builder for chaining.
    ///
    /// @see Keybinds#category(String, String)
    ///
    @CanIgnoreReturnValue
    public KeybindBuilder category(Identifier category) {
        this.category = category;
        return this;
    }
    
    ///
    /// Creates the definition from the name, the English default, the default key, the modifier, the context, and the category.
    ///
    @Override
    protected Keybind.Definition createDefinition() {
        return new Keybind.Definition(this.name, this.english, this.defaultKey, this.mouse, this.modifier, this.context, this.category);
    }
}
