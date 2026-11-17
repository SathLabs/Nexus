package dev.satherov.nexus.test.game.mixin.render;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.assertj.core.api.Assertions;

import java.util.Objects;

///
/// Checks which elements the buffer builder writes through misc and what a refused element leaves behind.
///
public class BufferBuilderMixinSamples {
    
    private static final int CAPACITY = 256;
    
    @ClientTest
    public static void refusedElementStaysMissing(Client client) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(BufferBuilderMixinSamples.CAPACITY)) {
            BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            Assertions.assertThatIllegalArgumentException()
                    .as("an element with one component too few")
                    .isThrownBy(() -> builder.misc(VertexFormatElement.UV0, Float.floatToRawIntBits(1.0F)));
            Assertions.assertThatIllegalStateException()
                    .as("the vertex after the refused element")
                    .isThrownBy(builder::build)
                    .withMessageContaining("UV0");
        }
    }
    
    @ClientTest
    public static void floatElementIsWritten(Client client) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(BufferBuilderMixinSamples.CAPACITY)) {
            BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(0.0F, 0.0F, 0.0F).misc(VertexFormatElement.UV0, Float.floatToRawIntBits(0.25F), Float.floatToRawIntBits(0.75F));
            try (MeshData mesh = Objects.requireNonNull(builder.build(), "the mesh of the vertex")) {
                Assertions.assertThat(mesh.vertexBuffer().getFloat(3 * Float.BYTES)).as("the first component of the element").isEqualTo(0.25F);
                Assertions.assertThat(mesh.vertexBuffer().getFloat(4 * Float.BYTES)).as("the second component of the element").isEqualTo(0.75F);
            }
        }
    }
    
    @ClientTest
    public static void otherElementTypesAreSkipped(Client client) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(BufferBuilderMixinSamples.CAPACITY)) {
            BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            Assertions.assertThatNoException()
                    .as("a short element without components")
                    .isThrownBy(() -> builder.misc(VertexFormatElement.UV2));
            Assertions.assertThatNoException()
                    .as("a short element with components")
                    .isThrownBy(() -> builder.misc(VertexFormatElement.UV2, 1, 2));
        }
    }
}
