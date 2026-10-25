package dev.satherov.nexus.api.render.hud;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

///
/// Where a HUD layer sits among the others.
///
/// @param relation The side of the anchor that the layer draws on.
/// @param anchor   The identifier of the layer it sits next to, or `null` for every layer.
///
public record HudSlot(Relation relation, @Nullable Identifier anchor) {
    
    ///
    /// Creates a slot directly above the layer with the given identifier.
    ///
    /// The anchor must be registered before the layer in this slot.
    ///
    /// @param anchor The identifier of the layer to sit above.
    ///
    /// @return The slot above the anchor.
    ///
    public static HudSlot above(Identifier anchor) {
        return new HudSlot(Relation.ABOVE, anchor);
    }
    
    ///
    /// Creates a slot directly below the layer with the given identifier.
    ///
    /// The anchor must be registered before the layer in this slot.
    ///
    /// @param anchor The identifier of the layer to sit below.
    ///
    /// @return The slot below the anchor.
    ///
    public static HudSlot below(Identifier anchor) {
        return new HudSlot(Relation.BELOW, anchor);
    }
    
    ///
    /// Creates a slot above every layer registered so far.
    ///
    /// @return The slot above every layer.
    ///
    public static HudSlot top() {
        return new HudSlot(Relation.ABOVE, null);
    }
    
    ///
    /// Creates a slot below every layer registered so far.
    ///
    /// @return The slot below every layer.
    ///
    public static HudSlot bottom() {
        return new HudSlot(Relation.BELOW, null);
    }
    
    ///
    /// A position above or below the anchor.
    ///
    public enum Relation {
        ///
        /// Drawn after the anchor, so over it.
        ///
        ABOVE,
        
        ///
        /// Drawn before the anchor, so under it.
        ///
        BELOW
    }
}
