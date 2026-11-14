package dev.satherov.nexus.internal.keybind;

import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// The parts of one declared key, as its builder collected them.
///
/// @param keybinds     The keybinds the key is registered in.
/// @param name         The name the key was declared with.
/// @param defaultKey   The default GLFW key code or mouse button, or `-1` if the key is unbound.
/// @param mouse        If the default key is a mouse button.
/// @param modifier     The modifier, or `null` if the key takes none.
/// @param context      The context the key can be pressed in.
/// @param category     The identifier of the category the key is listed in.
/// @param activation   The activation the key fires through.
/// @param clientAction The action run on the client when the key fires, or `null` if none is set.
///
@ApiStatus.Internal
public record KeyDefinition(
        Keybinds keybinds,
        String name,
        int defaultKey,
        boolean mouse,
        Keybind.@Nullable Modifier modifier,
        Keybind.Context context,
        Identifier category,
        Activation activation,
        @Nullable ClientOnly<Runnable> clientAction
) { }
