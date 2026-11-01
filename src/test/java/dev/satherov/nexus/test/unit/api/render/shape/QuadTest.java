package dev.satherov.nexus.test.unit.api.render.shape;

import dev.satherov.nexus.api.render.shape.Quad;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

///
/// Checks that the quad of a box side covers it and shows its sprite unmirrored from outside.
///
public class QuadTest {
    
    private static final AABB BOX = new AABB(1.0D, 2.0D, 3.0D, 2.0D, 4.0D, 6.0D);
    private static final Offset<Double> CLOSE = Offset.offset(1.0E-9D);
    
    private static void assertClose(Vec3 actual, Vec3 expected) {
        Assertions.assertThat(actual.distanceTo(expected)).isCloseTo(0.0D, QuadTest.CLOSE);
    }
    
    @ParameterizedTest
    @EnumSource(Direction.class)
    public void coversTheSideOfTheBox(Direction side) {
        Quad quad = Quad.of(QuadTest.BOX, side);
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        List<Vec3> corners = List.of(quad.origin(), cornerU, quad.origin().add(quad.edgeV()), cornerU.add(quad.edgeV()));
        
        double extreme = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? QuadTest.BOX.max(side.getAxis()) : QuadTest.BOX.min(side.getAxis());
        List<Vec3> expected = new ArrayList<>();
        for (double x : new double[]{ QuadTest.BOX.minX, QuadTest.BOX.maxX }) {
            for (double y : new double[]{ QuadTest.BOX.minY, QuadTest.BOX.maxY }) {
                for (double z : new double[]{ QuadTest.BOX.minZ, QuadTest.BOX.maxZ }) {
                    Vec3 corner = new Vec3(x, y, z);
                    if (corner.get(side.getAxis()) == extreme) {
                        expected.add(corner);
                    }
                }
            }
        }
        
        Assertions.assertThat(corners)
                .usingElementComparator((actual, other) -> actual.distanceTo(other) < 1.0E-9D ? 0 : 1)
                .containsExactlyInAnyOrderElementsOf(expected);
    }
    
    @ParameterizedTest
    @EnumSource(Direction.class)
    public void showsTheSpriteUnmirroredFromOutside(Direction side) {
        Quad quad = Quad.of(QuadTest.BOX, side);
        QuadTest.assertClose(quad.edgeU().cross(quad.edgeV()).normalize(), side.getUnitVec3().reverse());
    }
    
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = { "NORTH", "SOUTH", "WEST", "EAST" })
    public void runsTheSpriteDownwardsOnTheSides(Direction side) {
        Quad quad = Quad.of(QuadTest.BOX, side);
        QuadTest.assertClose(quad.edgeV().normalize(), new Vec3(0.0D, -1.0D, 0.0D));
    }
}
