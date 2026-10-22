///
/// The handles that declared payloads are sent through.
///
/// A payload declared in both directions has a [Payload] as its handle.
/// - [ClientboundPayload]: The handle of a payload declared as sent to the client.
/// - [ServerboundPayload]: The handle of a payload declared as sent to the server.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.network.payload;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
