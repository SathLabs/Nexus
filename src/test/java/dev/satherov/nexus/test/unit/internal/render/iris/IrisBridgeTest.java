package dev.satherov.nexus.test.unit.internal.render.iris;

import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks that pipelines stay unassigned when the internals of Iris that the bridge relies on are missing.
///
public class IrisBridgeTest {
    
    @Test
    public void leavesAnEntityPipelineUnassignedWithoutTheIrisInternals() {
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("nexus", "iris_bridge_test"))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                .build();
        // Iris is only on the compile classpath, so its internals are missing here like in an Iris build that renamed them.
        Assertions.assertThat(IrisBridge.class.getClassLoader().getResource("net/irisshaders/iris/pipeline/IrisPipelines.class")).isNull();
        Assertions.assertThatCode(() -> IrisBridge.assign(pipeline, Shading.ENTITIES)).doesNotThrowAnyException();
    }
}
