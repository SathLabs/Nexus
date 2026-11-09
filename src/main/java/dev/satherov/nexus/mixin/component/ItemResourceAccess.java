package dev.satherov.nexus.mixin.component;

import net.neoforged.neoforge.transfer.item.ItemResource;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

///
/// Accessor for the inner stack of [ItemResource].
///
/// A component filter reads the patched component map of the inner stack through it without a copy.
/// The accessor is a mixin, since an access transformer can't reach [ItemResource].
///
@Mixin(ItemResource.class)
public interface ItemResourceAccess {
    
    ///
    /// The item stack that the resource wraps.
    ///
    /// Should never be modified.
    ///
    /// @return The item stack.
    ///
    @Accessor("innerStack")
    ItemStack getInnerStack();
}
