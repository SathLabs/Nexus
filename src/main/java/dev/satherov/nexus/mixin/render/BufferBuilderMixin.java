package dev.satherov.nexus.mixin.render;

import net.neoforged.neoforge.client.extensions.IVertexConsumerExtension;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

///
/// Lets a buffer builder write the extra vertex inputs of a program through [IVertexConsumerExtension#misc(VertexFormatElement, int...)].
///
/// NeoForge's default of that method does nothing, and vanilla's buffer builder doesn't override it.
///
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexConsumer {
    
    ///
    /// Marks the given element as written in the current vertex.
    ///
    /// @param element The element to write.
    ///
    /// @return The address to write the element at, or `-1` if the format doesn't have the element or it was already written.
    ///
    @Shadow
    protected abstract long beginElement(VertexFormatElement element);
    
    ///
    /// Writes the given raw components of a `FLOAT` or `INT` element into the current vertex, four bytes each.
    ///
    /// Does nothing if the format of the buffer builder doesn't have the element.
    ///
    /// @param element The element to write.
    /// @param rawData The components of the element, with a `FLOAT` component as its raw bits.
    ///
    /// @return This buffer builder.
    ///
    /// @throws IllegalArgumentException If the type of the element is neither `FLOAT` nor `INT` or if the number of components does not match the element.
    ///
    @Override
    public VertexConsumer misc(VertexFormatElement element, int... rawData) {
        long pointer = this.beginElement(element);
        if (pointer == -1L) {
            return this;
        }
        
        if (element.type() != VertexFormatElement.Type.FLOAT && element.type() != VertexFormatElement.Type.INT) {
            throw new IllegalArgumentException("Could not write the vertex element '" + element + "', its type '" + element.type() + "' is neither 'Float' nor 'Int'");
        }
        
        if (rawData.length != element.count()) {
            throw new IllegalArgumentException("Could not write the vertex element '" + element + "', it has '" + element.count() + "' components but got '" + rawData.length + "'");
        }
        
        for (int i = 0; i < rawData.length; i++) {
            MemoryUtil.memPutInt(pointer + (long) i * Integer.BYTES, rawData[i]);
        }
        
        return this;
    }
}
