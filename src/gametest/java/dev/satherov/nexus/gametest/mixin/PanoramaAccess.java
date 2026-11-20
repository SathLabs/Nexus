package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.renderer.Panorama;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

///
/// Turns the panorama of the title screen to a given angle.
///
@Mixin(Panorama.class)
public interface PanoramaAccess {
    
    ///
    /// Sets the angle the panorama is turned by.
    ///
    /// @param spin The angle in degrees.
    ///
    @Accessor("spin")
    void setSpin(float spin);
}
