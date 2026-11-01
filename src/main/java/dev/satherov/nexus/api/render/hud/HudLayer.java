package dev.satherov.nexus.api.render.hud;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.Render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

///
/// One layer of the HUD.
///
/// Called on the client thread every frame the HUD is shown.
///
/// Can be registered through [Render#hudLayer(String, HudSlot, ClientOnly)].
///
@FunctionalInterface
public interface HudLayer {
    
    ///
    /// Extracts what the layer draws this frame.
    ///
    /// @param graphics The GUI graphics extractor of the frame.
    /// @param delta    The delta tracker of the frame.
    ///
    void extract(GuiGraphicsExtractor graphics, DeltaTracker delta);
}
