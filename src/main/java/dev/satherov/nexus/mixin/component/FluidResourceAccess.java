package dev.satherov.nexus.mixin.component;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

///
/// Accessor for the inner stack of [FluidResource].
///
/// A component filter reads the patched component map of the inner stack through it without a copy.
/// The accessor is a mixin, since an access transformer can't reach [FluidResource].
///
@Mixin(FluidResource.class)
public interface FluidResourceAccess {
    
    ///
    /// The fluid stack that the resource wraps.
    ///
    /// Should never be modified.
    ///
    /// @return The fluid stack.
    ///
    @Accessor("innerStack")
    FluidStack getInnerStack();
}
