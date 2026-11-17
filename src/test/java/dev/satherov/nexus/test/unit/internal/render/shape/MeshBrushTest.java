package dev.satherov.nexus.test.unit.internal.render.shape;

import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.internal.render.shape.MeshBrush;

import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

///
/// Checks which paint the brush draws a painted part with and which normals it refuses.
///
public class MeshBrushTest {
    
    private static final Paint WHOLE = Paint.stroke(0xFFFF0000).withFill(0x8000FF00);
    private static final Paint PART = new Paint(0xFF0000FF, 6.0F, 0, Paint.Depth.ALWAYS);
    private static final Vec3 SHORT = new Vec3(0.0D, 1.0E-6D, 0.0D);
    
    @Test
    public void drawsAPartWithItsOwnPaintAndThenRestoresTheOther() {
        MeshBrush brush = new MeshBrush(MeshBrushTest.WHOLE);
        List<Paint> seen = new ArrayList<>();
        Shape probe = current -> seen.add(current.getPaint());
        brush.draw(Shape.of(probe, probe.painted(MeshBrushTest.PART), probe), MeshBrushTest.PART.withDepth(Paint.Depth.HIDDEN));
        Assertions.assertThat(seen).containsExactly(MeshBrushTest.PART.withDepth(Paint.Depth.HIDDEN), MeshBrushTest.PART, MeshBrushTest.PART.withDepth(Paint.Depth.HIDDEN));
        Assertions.assertThat(brush.getPaint()).isEqualTo(MeshBrushTest.WHOLE);
    }
    
    @Test
    public void restoresThePaintIfThePartThrows() {
        MeshBrush brush = new MeshBrush(MeshBrushTest.WHOLE);
        List<Paint> seen = new ArrayList<>();
        Shape failing = current -> {
            seen.add(current.getPaint());
            throw new IllegalStateException("part");
        };
        Assertions.assertThatIllegalStateException().isThrownBy(() -> brush.draw(failing, MeshBrushTest.PART));
        Assertions.assertThat(seen).containsExactly(MeshBrushTest.PART);
        Assertions.assertThat(brush.getPaint()).isEqualTo(MeshBrushTest.WHOLE);
    }
    
    @Test
    public void refusesADiscWithoutANormal() {
        MeshBrush brush = new MeshBrush(MeshBrushTest.WHOLE);
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> brush.disc(Vec3.ZERO, Vec3.ZERO, 1.0D, 0xFFFF0000));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> brush.disc(Vec3.ZERO, MeshBrushTest.SHORT, 1.0D, 0xFFFF0000));
    }
    
    @Test
    public void refusesARingWithoutANormal() {
        MeshBrush brush = new MeshBrush(MeshBrushTest.WHOLE);
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> brush.ring(Vec3.ZERO, Vec3.ZERO, 1.0D, 0xFFFF0000));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> brush.ring(Vec3.ZERO, MeshBrushTest.SHORT, 1.0D, 0xFFFF0000));
    }
}
