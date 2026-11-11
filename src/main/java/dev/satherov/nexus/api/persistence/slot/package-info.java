///
/// Layouts of the slots that a holder has on its body, which move their item stacks into snapshots and back.
///
/// A [SlotLayout] puts its own groups together with the groups of the sources registered through [RegisterSlotSourcesEvent].
/// Every group is read and filled through a [SlotAccess].
/// - [SlotLayoutBuilder]: The builder of a layout, started through [Persistence#slots(Class)].
/// - [SlotSource]: The groups of slots of one class of holders, registered under an identifier.
/// - [SlotSnapshot]: The copied item stacks of one holder, saved with [SlotSnapshot#FORMAT].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.persistence.slot;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.Persistence;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
