package dev.satherov.nexus.mixin.render;

import net.neoforged.neoforge.client.extensions.IVertexConsumerExtension;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

///
/// Implements [IVertexConsumerExtension#misc(VertexFormatElement, int...)] for [BufferBuilder].
///
/// NeoForge's default of the method does nothing, and vanilla's buffer builder doesn't override it.
///
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexConsumer {

    ///
    /// The vertex format of the buffer builder.
    ///
    @Shadow
    @Final
    private VertexFormat format;

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
    /// Does nothing if the format of the buffer builder doesn't have the element, if the element is neither `FLOAT` nor `INT`, or if no components are given.
    /// If this throws, the element will still be missing from the vertex.
    ///
    /// @param element The element to write.
    /// @param rawData The components of the element, with a `FLOAT` component as its raw bits.
    ///
    /// @return This buffer builder.
    ///
    /// @throws IllegalArgumentException If the number of components does not match the element.
    ///
    @Override
    public VertexConsumer misc(VertexFormatElement element, int... rawData) {
        if (rawData.length == 0 || !this.format.contains(element)) {
            return this;
        }

        if (element.type() != VertexFormatElement.Type.FLOAT && element.type() != VertexFormatElement.Type.INT) {
            return this;
        }

        if (rawData.length != element.count()) {
            throw new IllegalArgumentException("Could not write the vertex element '" + element + "', it has '" + element.count() + "' components but got '" + rawData.length + "'");
        }

        long pointer = this.beginElement(element);
        if (pointer == -1L) {
            return this;
        }

        for (int i = 0; i < rawData.length; i++) {
            MemoryUtil.memPutInt(pointer + (long) i * Integer.BYTES, rawData[i]);
        }

        return this;
    }
}
