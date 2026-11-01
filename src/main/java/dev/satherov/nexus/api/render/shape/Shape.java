package dev.satherov.nexus.api.render.shape;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

///
/// A piece of geometry that draws itself with a [Brush].
///
/// The built-in shapes draw their lines in the stroke of the paint and their faces in its fill.
/// If one of the two colors is `0`, that part will not be drawn.
///
/// A shape is drawn once when a drawing is created or repainted and once when the targeted block of an outline changes.
///
@FunctionalInterface
public interface Shape {
    
    ///
    /// Creates a line between the given points.
    ///
    /// @param from The start of the line.
    /// @param to   The end of the line.
    ///
    /// @return The line.
    ///
    static Shape line(Vec3 from, Vec3 to) {
        return brush -> {
            int stroke = brush.getPaint().stroke();
            if (stroke != 0) {
                brush.line(from, to, stroke);
            }
        };
    }
    
    ///
    /// Creates the twelve edges and six faces of the given box.
    ///
    /// @param box The box to draw.
    ///
    /// @return The box.
    ///
    static Shape box(AABB box) {
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                for (int corner = 0; corner < 8; corner++) {
                    for (int axis = 1; axis < 8; axis <<= 1) {
                        if ((corner & axis) == 0) {
                            brush.line(Shape.corner(box, corner), Shape.corner(box, corner | axis), paint.stroke());
                        }
                    }
                }
            }
            
            if (paint.fill() != 0) {
                for (Direction side : Direction.values()) {
                    brush.quad(Quad.of(box, side), paint.fill());
                }
            }
        };
    }
    
    ///
    /// Creates the four edges and the face of the given quad.
    ///
    /// @param quad The quad to draw.
    ///
    /// @return The quad.
    ///
    static Shape face(Quad quad) {
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                Shape.outline(brush, quad, paint.stroke());
            }
            
            if (paint.fill() != 0) {
                brush.quad(quad, paint.fill());
            }
        };
    }
    
    ///
    /// Creates the four edges of the given quad and its face with the given sprite on it, tinted in the fill.
    ///
    /// @param quad   The quad to draw.
    /// @param sprite The sprite to show on the face.
    ///
    /// @return The quad with the sprite.
    ///
    static Shape face(Quad quad, Sprite sprite) {
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                Shape.outline(brush, quad, paint.stroke());
            }
            
            if (paint.fill() != 0) {
                brush.quad(quad, sprite, paint.fill());
            }
        };
    }
    
    ///
    /// Creates the ring and the disc of a circle around the given center, in the plane with the given normal.
    ///
    /// @param center The center of the circle.
    /// @param normal The normal of the plane the circle lies in.
    /// @param radius The radius of the circle.
    ///
    /// @return The circle.
    ///
    static Shape circle(Vec3 center, Vec3 normal, double radius) {
        Vec3[] points = Shape.ring(center, normal, radius);
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                Shape.loop(brush, points, paint.stroke());
            }
            
            if (paint.fill() != 0) {
                Shape.disc(brush, center, points, paint.fill());
            }
        };
    }
    
    ///
    /// Creates the surface of a sphere and its three great circles, one around each axis.
    ///
    /// @param center The center of the sphere.
    /// @param radius The radius of the sphere.
    ///
    /// @return The sphere.
    ///
    static Shape sphere(Vec3 center, double radius) {
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                for (Vec3 normal : List.of(Vec3.X_AXIS, Vec3.Y_AXIS, Vec3.Z_AXIS)) {
                    Shape.loop(brush, Shape.ring(center, normal, radius), paint.stroke());
                }
            }
            
            if (paint.fill() != 0) {
                Shape.surface(brush, center, radius, paint.fill());
            }
        };
    }
    
    ///
    /// Creates a cylinder from the given base center to the given top center.
    ///
    /// Its lines are both rims and four edges along the side, its faces the side and both caps.
    ///
    /// @param base   The center of the base.
    /// @param top    The center of the top.
    /// @param radius The radius of the cylinder.
    ///
    /// @return The cylinder.
    ///
    static Shape cylinder(Vec3 base, Vec3 top, double radius) {
        Vec3 axis = top.subtract(base);
        Vec3[] bottomRim = Shape.ring(base, axis, radius);
        Vec3[] topRim = Shape.ring(top, axis, radius);
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                Shape.loop(brush, bottomRim, paint.stroke());
                Shape.loop(brush, topRim, paint.stroke());
                for (int i = 0; i < bottomRim.length; i += bottomRim.length / 4) {
                    brush.line(bottomRim[i], topRim[i], paint.stroke());
                }
            }
            
            if (paint.fill() != 0) {
                for (int i = 0; i < bottomRim.length; i++) {
                    Vec3 next = bottomRim[(i + 1) % bottomRim.length];
                    brush.quad(new Quad(bottomRim[i], next.subtract(bottomRim[i]), axis), paint.fill());
                }
                
                Shape.disc(brush, base, bottomRim, paint.fill());
                Shape.disc(brush, top, topRim, paint.fill());
            }
        };
    }
    
    ///
    /// Creates every edge of the given voxel shape, moved to the given position.
    ///
    /// @param shape    The voxel shape to outline.
    /// @param position The position the voxel shape is placed at.
    ///
    /// @return The edges of the voxel shape.
    ///
    static Shape voxels(VoxelShape shape, Vec3 position) {
        return brush -> {
            int stroke = brush.getPaint().stroke();
            if (stroke != 0) {
                shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> brush.line(position.add(x1, y1, z1), position.add(x2, y2, z2), stroke));
            }
        };
    }
    
    ///
    /// Creates a label of the given text at name tag size, centered on the given point and facing the camera.
    /// The text is drawn in the stroke and its backdrop in the fill.
    ///
    /// If the stroke is `0`, nothing will be drawn.
    /// If the depth is not [Paint.Depth#VISIBLE], the label will be drawn everywhere.
    ///
    /// @param at   The point to center the label on.
    /// @param text The text of the label.
    ///
    /// @return The label.
    ///
    static Shape label(Vec3 at, Component text) {
        return brush -> {
            Paint paint = brush.getPaint();
            if (paint.stroke() != 0) {
                brush.text(at, text, 1.0F, paint.stroke(), paint.fill());
            }
        };
    }
    
    ///
    /// Creates one shape out of the given parts.
    ///
    /// @param parts The shapes to draw together.
    ///
    /// @return The shape of all parts.
    ///
    static Shape of(Shape... parts) {
        List<Shape> shapes = List.of(parts);
        return brush -> shapes.forEach(shape -> shape.draw(brush));
    }
    
    ///
    /// Gets the corner of the given box with the given index.
    ///
    /// @param box   The box to take the corner of.
    /// @param index The index of the corner, whose bits `1`, `2`, and `4` select the maximum x, y, and z.
    ///
    /// @return The corner.
    ///
    private static Vec3 corner(AABB box, int index) {
        return new Vec3(
                (index & 1) == 0 ? box.minX : box.maxX,
                (index & 2) == 0 ? box.minY : box.maxY,
                (index & 4) == 0 ? box.minZ : box.maxZ
        );
    }
    
    ///
    /// Draws the four edges of the given quad.
    ///
    /// @param brush The brush to draw with.
    /// @param quad  The quad to outline.
    /// @param argb  The ARGB color of the edges.
    ///
    private static void outline(Brush brush, Quad quad, int argb) {
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        Vec3 cornerV = quad.origin().add(quad.edgeV());
        Vec3 opposite = cornerU.add(quad.edgeV());
        brush.line(quad.origin(), cornerU, argb);
        brush.line(cornerU, opposite, argb);
        brush.line(opposite, cornerV, argb);
        brush.line(cornerV, quad.origin(), argb);
    }
    
    ///
    /// Computes the number of segments a ring of the given radius takes to sag at most a 64th of a block below its arc.
    ///
    /// The number is a multiple of `4`, at least `16` and at most `256`.
    ///
    /// @param radius The radius of the ring.
    ///
    /// @return The number of segments.
    ///
    private static int segments(double radius) {
        if (radius <= 1.0D / 64.0D) {
            return 16;
        }
        
        int exact = (int) Math.ceil(Math.PI / Math.acos(1.0D - 1.0D / (64.0D * radius)));
        return Math.clamp((exact + 3) / 4 * 4, 16, 256);
    }
    
    ///
    /// Computes evenly spaced points of a ring of the given radius around the given center, in the plane with the given normal.
    ///
    /// The number of points comes from [#segments(double)].
    ///
    /// @param center The center of the ring.
    /// @param normal The normal of the plane the ring lies in.
    /// @param radius The radius of the ring.
    ///
    /// @return The points of the ring, in order around it.
    ///
    private static Vec3[] ring(Vec3 center, Vec3 normal, double radius) {
        return Shape.ring(center, normal, radius, Shape.segments(radius));
    }
    
    ///
    /// Computes the given number of evenly spaced points of a ring of the given radius around the given center,
    /// in the plane with the given normal.
    ///
    /// Two rings with the same normal start at the same angle.
    ///
    /// @param center   The center of the ring.
    /// @param normal   The normal of the plane the ring lies in.
    /// @param radius   The radius of the ring.
    /// @param segments The number of points.
    ///
    /// @return The points of the ring, in order around it.
    ///
    private static Vec3[] ring(Vec3 center, Vec3 normal, double radius, int segments) {
        Vec3 axis = normal.normalize();
        Vec3 start = axis.cross(Math.abs(axis.y) < 0.5D ? Vec3.Y_AXIS : Vec3.X_AXIS).normalize().scale(radius);
        Vec3 quarter = axis.cross(start);
        Vec3[] points = new Vec3[segments];
        for (int i = 0; i < points.length; i++) {
            double angle = 2.0D * Math.PI * i / points.length;
            points[i] = center.add(start.scale(Math.cos(angle))).add(quarter.scale(Math.sin(angle)));
        }
        
        return points;
    }
    
    ///
    /// Draws a closed loop of lines through the given points.
    ///
    /// @param brush  The brush to draw with.
    /// @param points The points to connect, in the order they should be connected.
    /// @param argb   The ARGB color of the lines.
    ///
    private static void loop(Brush brush, Vec3[] points, int argb) {
        for (int i = 0; i < points.length; i++) {
            brush.line(points[i], points[(i + 1) % points.length], argb);
        }
    }
    
    ///
    /// Draws the disc inside the given ring as triangles around the given center.
    ///
    /// @param brush  The brush to draw with.
    /// @param center The center of the ring.
    /// @param points The points of the ring, in order around it.
    /// @param argb   The ARGB color of the disc.
    ///
    private static void disc(Brush brush, Vec3 center, Vec3[] points, int argb) {
        for (int i = 0; i < points.length; i++) {
            brush.triangle(center, points[i], points[(i + 1) % points.length], argb);
        }
    }
    
    ///
    /// Draws the surface of a sphere of the given radius around the given center as triangles in bands of latitude.
    ///
    /// The bands are half as many as the segments of the equator.
    ///
    /// The triangles at both poles have no area and are left out.
    ///
    /// @param brush  The brush to draw with.
    /// @param center The center of the sphere.
    /// @param radius The radius of the sphere.
    /// @param argb   The ARGB color of the surface.
    ///
    private static void surface(Brush brush, Vec3 center, double radius, int argb) {
        int segments = Shape.segments(radius);
        Vec3[] upper = Shape.ring(center.add(0.0D, radius, 0.0D), Vec3.Y_AXIS, 0.0D, segments);
        int bands = segments / 2;
        for (int band = 1; band <= bands; band++) {
            double angle = Math.PI * band / bands;
            Vec3[] lower = Shape.ring(center.add(0.0D, radius * Math.cos(angle), 0.0D), Vec3.Y_AXIS, radius * Math.sin(angle), segments);
            for (int i = 0; i < lower.length; i++) {
                int next = (i + 1) % lower.length;
                if (band > 1) {
                    brush.triangle(upper[i], upper[next], lower[next], argb);
                }
                
                if (band < bands) {
                    brush.triangle(upper[i], lower[next], lower[i], argb);
                }
            }
            
            upper = lower;
        }
    }
    
    ///
    /// Draws this shape with the given brush.
    ///
    /// @param brush The brush to draw with.
    ///
    void draw(Brush brush);
    
    ///
    /// Creates a shape that draws this one with the given paint instead of the paint of the brush.
    ///
    /// @param paint The paint to draw this shape with.
    ///
    /// @return The painted shape.
    ///
    /// @see Brush#draw(Shape, Paint)
    ///
    default Shape painted(Paint paint) {
        return brush -> brush.draw(this, paint);
    }
}
