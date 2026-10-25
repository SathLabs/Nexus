package dev.satherov.nexus.api.render.outline;

import org.jspecify.annotations.Nullable;

///
/// Decides the outline of the block under the crosshair.
///
/// Can be added through [Outlines#replace(OutlineRule)] or [Outlines#add(OutlineRule)].
///
@FunctionalInterface
public interface OutlineRule {
    
    ///
    /// The outline for the target, or `null` to leave it to vanilla and the other rules.
    ///
    /// Called on the client thread when the target changes, and the result is kept until it changes again.
    ///
    /// @param target The block under the crosshair.
    ///
    /// @return The outline for the target, or `null` to leave it to vanilla and the other rules.
    ///
    @Nullable Outline outline(TargetedBlock target);
}
