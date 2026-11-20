package dev.satherov.nexus.api.render.outline;

import org.jspecify.annotations.Nullable;

///
/// A rule that decides the outline of the block under the crosshair.
///
/// Can be added through [Outlines#replace(OutlineRule)] or [Outlines#add(OutlineRule)].
///
@FunctionalInterface
public interface OutlineRule {
    
    ///
    /// Gets the outline of the given target, or `null` to leave it to vanilla and the other rules.
    ///
    /// Called on the client thread when the target changes, and the outline is kept until it changes again.
    /// Every rule is asked again once a rule is added or the resources reload.
    ///
    /// @param target The targeted block.
    ///
    /// @return The outline of the target, or `null` to leave it to vanilla and the other rules.
    ///
    @Nullable Outline outline(TargetedBlock target);
}
