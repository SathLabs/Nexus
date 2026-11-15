///
/// The typed stages of the values a key sends to the server when it fires.
/// - [Carry1]: The stage of one carried value, started through [BindingBuilder#carry(NexusCodec, ClientOnly)].
/// - [Carry2]: The stage of two carried values, started through [Carry1#carry(NexusCodec, ClientOnly)].
/// - [KeyAction2]: The server action over two carried values.
///
/// Each stage up to [Carry7] starts the stage of one more value.
/// The stages of two to eight values take the [KeyAction2] to [KeyAction8] of their count as the server action.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.keybind.carry;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.BindingBuilder;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
