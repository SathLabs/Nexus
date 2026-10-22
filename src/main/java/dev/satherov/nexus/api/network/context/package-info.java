///
/// The contexts that payload handlers receive, one for each receiving side.
///
/// The members that both sides share are on [HandlerContext].
/// - [ClientHandlerContext]: The context of every handler on the client.
/// - [ServerHandlerContext]: The context of every handler on the server.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.network.context;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
