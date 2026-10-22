///
/// Payloads that a mod declares and sends between the server and its clients.
/// - [Network]: The payloads of one mod, reached through [NexusMod#getNetwork()].
/// - [PayloadBuilder]: The declaration of one payload, in one direction or both.
/// - [ClientRecipients]: The recipients of a client-bound payload.
///
/// The handles of the declared payloads are in [dev.satherov.nexus.api.network.payload].
/// The contexts that the payload handlers receive are in [dev.satherov.nexus.api.network.context].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.network;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
