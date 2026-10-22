///
/// The builder and the entry of attachment types.
/// - [AttachmentBuilder]: The builder, started through [Registrations#attachment(String, Supplier)].
/// - [AttachmentEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.attachment;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;
