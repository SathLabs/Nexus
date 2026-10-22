///
/// Client tests and the client they control.
///
/// The fake [Keyboard] and [Mouse] of the client are reached through [Client#keyboard()] and [Client#mouse()].
/// The last rendered frame is captured into a [Capture] with [Client#capture()].
/// - [ClientTest]: The annotation on a client test method.
/// - [Client]: The client a test method is called with.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.gametest.api.client;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
