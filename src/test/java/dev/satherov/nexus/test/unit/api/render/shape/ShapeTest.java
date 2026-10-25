package dev.satherov.nexus.test.unit.api.render.shape;

import dev.satherov.nexus.api.render.shape.Brush;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Quad;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.api.render.shape.Sprite;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// Checks the lines, triangles, quads, and text that the built-in shapes draw into a brush.
///
public class ShapeTest {
    
    private static final int STROKE = 0xFFFF0000;
    private static final int FILL = 0x8000FF00;
    private static final Paint BOTH = Paint.stroke(ShapeTest.STROKE).withFill(ShapeTest.FILL);
    private static final AABB BOX = new AABB(1.0D, 2.0D, 3.0D, 2.0D, 4.0D, 6.0D);
    private static final Quad QUAD = new Quad(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 2.0D, 0.0D), new Vec3(0.0D, 0.0D, 3.0D));
    private static final Offset<Double> CLOSE = Offset.offset(1.0E-9D);
    private static final Percentage NEAR = Percentage.withPercentage(3.0D);
    
    private static RecordingBrush draw(Shape shape, Paint paint) {
        RecordingBrush brush = new RecordingBrush(paint);
        shape.draw(brush);
        return brush;
    }
    
    private static Vec3 key(Vec3 point) {
        return new Vec3(Math.round(point.x * 1.0E6D) / 1.0E6D, Math.round(point.y * 1.0E6D) / 1.0E6D, Math.round(point.z * 1.0E6D) / 1.0E6D);
    }
    
    private static Set<Set<Vec3>> edges(List<Line> lines) {
        return lines.stream().map(line -> Set.of(ShapeTest.key(line.from()), ShapeTest.key(line.to()))).collect(Collectors.toSet());
    }
    
    private static Set<Set<Vec3>> edges(AABB box) {
        List<Vec3> corners = ShapeTest.corners(box);
        Set<Set<Vec3>> edges = new HashSet<>();
        for (Vec3 from : corners) {
            for (Vec3 to : corners) {
                long differing = Arrays.stream(Direction.Axis.values()).filter(axis -> from.get(axis) != to.get(axis)).count();
                if (differing == 1) {
                    edges.add(Set.of(ShapeTest.key(from), ShapeTest.key(to)));
                }
            }
        }
        
        return edges;
    }
    
    private static Set<Set<Vec3>> edges(Quad quad) {
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        Vec3 cornerV = quad.origin().add(quad.edgeV());
        Vec3 opposite = cornerU.add(quad.edgeV());
        return ShapeTest.edges(List.of(
                new Line(quad.origin(), cornerU, 0),
                new Line(cornerU, opposite, 0),
                new Line(opposite, cornerV, 0),
                new Line(cornerV, quad.origin(), 0)
        ));
    }
    
    private static List<Vec3> corners(AABB box) {
        List<Vec3> corners = new ArrayList<>();
        for (double x : new double[]{ box.minX, box.maxX }) {
            for (double y : new double[]{ box.minY, box.maxY }) {
                for (double z : new double[]{ box.minZ, box.maxZ }) {
                    corners.add(new Vec3(x, y, z));
                }
            }
        }
        
        return corners;
    }
    
    private static List<Vec3> corners(AABB box, Direction side) {
        double extreme = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(side.getAxis()) : box.min(side.getAxis());
        return ShapeTest.corners(box).stream().filter(corner -> corner.get(side.getAxis()) == extreme).toList();
    }
    
    private static List<Vec3> corners(Quad quad) {
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        return List.of(quad.origin(), cornerU, quad.origin().add(quad.edgeV()), cornerU.add(quad.edgeV()));
    }
    
    private static Set<Vec3> keys(List<Vec3> points) {
        return points.stream().map(ShapeTest::key).collect(Collectors.toSet());
    }
    
    private static List<Vec3> ends(List<Line> lines) {
        return lines.stream().flatMap(line -> Stream.of(line.from(), line.to())).toList();
    }
    
    private static double length(List<Line> lines) {
        return lines.stream().mapToDouble(Line::length).sum();
    }
    
    private static boolean isOnRim(Vec3 point, Vec3 center, Vec3 axis, double radius) {
        Vec3 offset = point.subtract(center);
        return Math.abs(offset.length() - radius) < 1.0E-9D && Math.abs(offset.dot(axis.normalize())) < 1.0E-9D;
    }
    
    private static boolean isInCylinder(Vec3 point, Vec3 base, Vec3 top, double radius) {
        Vec3 axis = top.subtract(base).normalize();
        Vec3 offset = point.subtract(base);
        double along = offset.dot(axis);
        double across = offset.subtract(axis.scale(along)).length();
        return across < radius + 1.0E-9D && along > -1.0E-9D && along < top.subtract(base).length() + 1.0E-9D;
    }
    
    @Test
    public void drawsALineInTheStroke() {
        Vec3 from = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 to = new Vec3(4.0D, 5.0D, 6.0D);
        Assertions.assertThat(ShapeTest.draw(Shape.line(from, to), ShapeTest.BOTH).calls).containsExactly(new Line(from, to, ShapeTest.STROKE));
    }
    
    @Test
    public void leavesOutALineWithoutAStroke() {
        Assertions.assertThat(ShapeTest.draw(Shape.line(Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D)), Paint.fill(ShapeTest.FILL)).calls).isEmpty();
    }
    
    @Test
    public void drawsTheTwelveEdgesOfABox() {
        List<Line> lines = ShapeTest.draw(Shape.box(ShapeTest.BOX), ShapeTest.BOTH).getAll(Line.class);
        Assertions.assertThat(lines).hasSize(12).allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.edges(lines)).isEqualTo(ShapeTest.edges(ShapeTest.BOX));
    }
    
    @Test
    public void drawsTheSixFacesOfABox() {
        List<Face> faces = ShapeTest.draw(Shape.box(ShapeTest.BOX), ShapeTest.BOTH).getAll(Face.class);
        Assertions.assertThat(faces).hasSize(6).allMatch(face -> face.argb() == ShapeTest.FILL && face.sprite() == null);
        Assertions.assertThat(faces)
                .map(face -> ShapeTest.keys(face.corners()))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Direction.values()).map(side -> ShapeTest.keys(ShapeTest.corners(ShapeTest.BOX, side))).toList());
    }
    
    @Test
    public void leavesOutTheFacesOfABoxWithoutAFill() {
        Assertions.assertThat(ShapeTest.draw(Shape.box(ShapeTest.BOX), Paint.stroke(ShapeTest.STROKE)).calls).hasSize(12).allMatch(Line.class::isInstance);
    }
    
    @Test
    public void leavesOutTheEdgesOfABoxWithoutAStroke() {
        Assertions.assertThat(ShapeTest.draw(Shape.box(ShapeTest.BOX), Paint.fill(ShapeTest.FILL)).calls).hasSize(6).allMatch(Face.class::isInstance);
    }
    
    @Test
    public void drawsTheEdgesAndFaceOfAQuad() {
        RecordingBrush brush = ShapeTest.draw(Shape.face(ShapeTest.QUAD), ShapeTest.BOTH);
        List<Line> lines = brush.getAll(Line.class);
        Assertions.assertThat(lines).hasSize(4).allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.edges(lines)).isEqualTo(ShapeTest.edges(ShapeTest.QUAD));
        Assertions.assertThat(brush.getAll(Face.class)).containsExactly(new Face(ShapeTest.QUAD, null, ShapeTest.FILL));
    }
    
    @Test
    public void drawsTheEdgesAndSpriteOfAQuad() {
        Sprite sprite = Sprite.blocks(Identifier.withDefaultNamespace("block/stone"));
        RecordingBrush brush = ShapeTest.draw(Shape.face(ShapeTest.QUAD, sprite), ShapeTest.BOTH);
        List<Line> lines = brush.getAll(Line.class);
        Assertions.assertThat(lines).hasSize(4).allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.edges(lines)).isEqualTo(ShapeTest.edges(ShapeTest.QUAD));
        Assertions.assertThat(brush.getAll(Face.class)).containsExactly(new Face(ShapeTest.QUAD, sprite, ShapeTest.FILL));
    }
    
    @Test
    public void drawsAClosedRingAroundTheCenterOfACircle() {
        Vec3 center = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 normal = new Vec3(1.0D, 1.0D, 0.0D);
        List<Line> lines = ShapeTest.draw(Shape.circle(center, normal, 2.0D), ShapeTest.BOTH).getAll(Line.class);
        Assertions.assertThat(lines).isNotEmpty().allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.ends(lines)).allSatisfy(point -> Assertions.assertThat(ShapeTest.isOnRim(point, center, normal, 2.0D)).isTrue());
        Assertions.assertThat(ShapeTest.length(lines)).isCloseTo(2.0D * Math.PI * 2.0D, ShapeTest.NEAR);
        
        Map<Vec3, Long> uses = ShapeTest.ends(lines).stream().collect(Collectors.groupingBy(ShapeTest::key, Collectors.counting()));
        Assertions.assertThat(uses.values()).containsOnly(2L);
    }
    
    @Test
    public void fillsTheDiscOfACircle() {
        Vec3 center = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 normal = new Vec3(1.0D, 1.0D, 0.0D);
        List<Triangle> triangles = ShapeTest.draw(Shape.circle(center, normal, 2.0D), ShapeTest.BOTH).getAll(Triangle.class);
        Assertions.assertThat(triangles).isNotEmpty().allMatch(triangle -> triangle.argb() == ShapeTest.FILL);
        Assertions.assertThat(triangles.stream().flatMap(triangle -> triangle.corners().stream())).allSatisfy(point -> {
            Assertions.assertThat(point.distanceTo(center)).isLessThan(2.0D + 1.0E-9D);
            Assertions.assertThat(point.subtract(center).dot(normal.normalize())).isCloseTo(0.0D, ShapeTest.CLOSE);
        });
        Assertions.assertThat(triangles.stream().mapToDouble(Triangle::area).sum()).isCloseTo(Math.PI * 4.0D, ShapeTest.NEAR);
    }
    
    @Test
    public void drawsThreeGreatCirclesOfASphere() {
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D);
        List<Line> lines = ShapeTest.draw(Shape.sphere(center, 1.5D), ShapeTest.BOTH).getAll(Line.class);
        Assertions.assertThat(lines).isNotEmpty().allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.ends(lines)).allSatisfy(point -> Assertions.assertThat(point.distanceTo(center)).isCloseTo(1.5D, ShapeTest.CLOSE));
        Assertions.assertThat(ShapeTest.length(lines)).isCloseTo(3.0D * 2.0D * Math.PI * 1.5D, ShapeTest.NEAR);
    }
    
    @Test
    public void fillsTheSurfaceOfASphere() {
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D);
        List<Triangle> triangles = ShapeTest.draw(Shape.sphere(center, 1.5D), ShapeTest.BOTH).getAll(Triangle.class);
        Assertions.assertThat(triangles).isNotEmpty().allMatch(triangle -> triangle.argb() == ShapeTest.FILL);
        Assertions.assertThat(triangles.stream().flatMap(triangle -> triangle.corners().stream()))
                .allSatisfy(point -> Assertions.assertThat(point.distanceTo(center)).isCloseTo(1.5D, ShapeTest.CLOSE));
        Assertions.assertThat(triangles.stream().mapToDouble(Triangle::area).sum()).isCloseTo(4.0D * Math.PI * 1.5D * 1.5D, ShapeTest.NEAR);
    }
    
    @Test
    public void drawsTheRimsAndFourLongEdgesOfACylinder() {
        Vec3 base = new Vec3(1.0D, 1.0D, 1.0D);
        Vec3 top = new Vec3(3.0D, 4.0D, 7.0D);
        Vec3 axis = top.subtract(base);
        List<Line> lines = ShapeTest.draw(Shape.cylinder(base, top, 0.5D), ShapeTest.BOTH).getAll(Line.class);
        Assertions.assertThat(lines).isNotEmpty().allMatch(line -> line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.ends(lines))
                .allSatisfy(point -> Assertions.assertThat(ShapeTest.isOnRim(point, base, axis, 0.5D) || ShapeTest.isOnRim(point, top, axis, 0.5D)).isTrue());
        
        Predicate<Line> isLongEdge = line -> ShapeTest.isOnRim(line.from(), base, axis, 0.5D) != ShapeTest.isOnRim(line.to(), base, axis, 0.5D);
        Assertions.assertThat(lines.stream().filter(isLongEdge).toList()).hasSize(4).allSatisfy(line -> Assertions.assertThat(line.length()).isCloseTo(7.0D, ShapeTest.CLOSE));
        Assertions.assertThat(ShapeTest.length(lines.stream().filter(isLongEdge.negate()).toList())).isCloseTo(2.0D * 2.0D * Math.PI * 0.5D, ShapeTest.NEAR);
    }
    
    @Test
    public void fillsTheMantleAndCapsOfACylinder() {
        Vec3 base = new Vec3(1.0D, 1.0D, 1.0D);
        Vec3 top = new Vec3(3.0D, 4.0D, 7.0D);
        RecordingBrush brush = ShapeTest.draw(Shape.cylinder(base, top, 0.5D), ShapeTest.BOTH);
        List<Face> faces = brush.getAll(Face.class);
        List<Triangle> triangles = brush.getAll(Triangle.class);
        Assertions.assertThat(faces).allMatch(face -> face.argb() == ShapeTest.FILL && face.sprite() == null);
        Assertions.assertThat(triangles).allMatch(triangle -> triangle.argb() == ShapeTest.FILL);
        List<Vec3> corners = Stream.concat(faces.stream().map(Face::corners), triangles.stream().map(Triangle::corners)).flatMap(List::stream).toList();
        Assertions.assertThat(corners).isNotEmpty().allSatisfy(point -> Assertions.assertThat(ShapeTest.isInCylinder(point, base, top, 0.5D)).isTrue());
        
        double area = faces.stream().mapToDouble(Face::area).sum() + triangles.stream().mapToDouble(Triangle::area).sum();
        Assertions.assertThat(area).isCloseTo(2.0D * Math.PI * 0.5D * 7.0D + 2.0D * Math.PI * 0.5D * 0.5D, ShapeTest.NEAR);
    }
    
    @Test
    public void drawsTheEdgesOfAVoxelShapeAtThePosition() {
        RecordingBrush brush = ShapeTest.draw(Shape.voxels(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), new Vec3(10.0D, 64.0D, -3.0D)), ShapeTest.BOTH);
        Assertions.assertThat(brush.calls).hasSize(12).allMatch(call -> call instanceof Line line && line.argb() == ShapeTest.STROKE);
        Assertions.assertThat(ShapeTest.edges(brush.getAll(Line.class))).isEqualTo(ShapeTest.edges(new AABB(10.0D, 64.0D, -3.0D, 11.0D, 64.5D, -2.0D)));
    }
    
    @Test
    public void leavesOutAVoxelShapeWithoutAStroke() {
        Assertions.assertThat(ShapeTest.draw(Shape.voxels(Shapes.block(), Vec3.ZERO), Paint.fill(ShapeTest.FILL)).calls).isEmpty();
    }
    
    @Test
    public void drawsALabelAtNameTagSizeInTheStrokeOverTheFill() {
        Vec3 at = new Vec3(0.5D, 65.0D, 0.5D);
        Component text = Component.literal("label");
        Assertions.assertThat(ShapeTest.draw(Shape.label(at, text), ShapeTest.BOTH).calls).containsExactly(new Text(at, text, 1.0F, ShapeTest.STROKE, ShapeTest.FILL));
    }
    
    @Test
    public void drawsThePartsOfAShapeInOrder() {
        Vec3 first = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 second = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 third = new Vec3(0.0D, 0.0D, 1.0D);
        Component text = Component.literal("part");
        Shape shape = Shape.of(Shape.line(first, second), Shape.label(third, text), Shape.line(second, third));
        Assertions.assertThat(ShapeTest.draw(shape, ShapeTest.BOTH).calls).containsExactly(
                new Line(first, second, ShapeTest.STROKE),
                new Text(third, text, 1.0F, ShapeTest.STROKE, ShapeTest.FILL),
                new Line(second, third, ShapeTest.STROKE)
        );
    }
    
    @Test
    public void drawsAPaintedShapeInItsOwnColors() {
        Vec3 from = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 to = new Vec3(4.0D, 5.0D, 6.0D);
        Paint own = new Paint(0xFF0000FF, 6.0F, 0, Paint.Depth.ALWAYS);
        Assertions.assertThat(ShapeTest.draw(Shape.line(from, to).painted(own), ShapeTest.BOTH).calls).containsExactly(new Line(from, to, 0xFF0000FF));
    }
    
    @Test
    public void drawsAPaintedShapeWithItsOwnPaintAndThenRestoresTheOther() {
        Paint own = new Paint(0xFF0000FF, 6.0F, 0, Paint.Depth.ALWAYS);
        List<Paint> seen = new ArrayList<>();
        Shape probe = current -> seen.add(current.getPaint());
        RecordingBrush brush = ShapeTest.draw(Shape.of(probe.painted(own), probe), ShapeTest.BOTH);
        Assertions.assertThat(seen).containsExactly(own, ShapeTest.BOTH);
        Assertions.assertThat(brush.getPaint()).isEqualTo(ShapeTest.BOTH);
    }
    
    private static final class RecordingBrush implements Brush {
        
        private final List<Object> calls = new ArrayList<>();
        private Paint paint;
        
        private RecordingBrush(Paint paint) {
            this.paint = paint;
        }
        
        @Override
        public Paint getPaint() {
            return this.paint;
        }
        
        @Override
        public void line(Vec3 from, Vec3 to, int argb) {
            this.calls.add(new Line(from, to, argb));
        }
        
        @Override
        public void triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
            this.calls.add(new Triangle(a, b, c, argb));
        }
        
        @Override
        public void quad(Quad quad, int argb) {
            this.calls.add(new Face(quad, null, argb));
        }
        
        @Override
        public void quad(Quad quad, Sprite sprite, int argb) {
            this.calls.add(new Face(quad, sprite, argb));
        }
        
        @Override
        public void text(Vec3 at, Component text, float scale, int argb, int backdrop) {
            this.calls.add(new Text(at, text, scale, argb, backdrop));
        }
        
        @Override
        public void draw(Shape part, Paint paint) {
            Paint previous = this.paint;
            this.paint = paint;
            part.draw(this);
            this.paint = previous;
        }
        
        private <CALL> List<CALL> getAll(Class<CALL> type) {
            return this.calls.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
    
    private record Line(Vec3 from, Vec3 to, int argb) {
        
        private double length() {
            return this.from.distanceTo(this.to);
        }
    }
    
    private record Triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
        
        private List<Vec3> corners() {
            return List.of(this.a, this.b, this.c);
        }
        
        private double area() {
            return this.b.subtract(this.a).cross(this.c.subtract(this.a)).length() / 2.0D;
        }
    }
    
    private record Face(Quad quad, @Nullable Sprite sprite, int argb) {
        
        private List<Vec3> corners() {
            return ShapeTest.corners(this.quad);
        }
        
        private double area() {
            return this.quad.edgeU().cross(this.quad.edgeV()).length();
        }
    }
    
    private record Text(Vec3 at, Component text, float scale, int argb, int backdrop) { }
}
