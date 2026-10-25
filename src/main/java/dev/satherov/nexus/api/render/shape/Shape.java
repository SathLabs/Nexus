package dev.satherov.nexus.api.render.shape;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

///
/// Geometry that draws itself into a brush.
/// Edges take the paint's stroke and faces its fill, and a part whose color is `0` is left out.
///
/// Drawn once when a drawing is created or repainted.
///
@FunctionalInterface
public interface Shape {
    
    ///
    /// Creates a line between the given points, stroke only.
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
    /// @return The edges and faces of the box.
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
    /// @return The edges and face of the quad.
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
    /// Creates the four edges of the given quad and its face showing the given sprite, tinted by the fill.
    ///
    /// @param quad   The quad to draw.
    /// @param sprite The sprite to show on the face.
    ///
    /// @return The edges and textured face of the quad.
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
    /// Creates a ring of the given radius around the given center in the plane the normal stands on, with a disc as fill.
    ///
    /// @param center The center of the ring.
    /// @param normal The normal of the plane of the ring.
    /// @param radius The radius of the ring.
    ///
    /// @return The ring and its disc.
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
    /// Creates a sphere with three great circles as stroke and the surface as fill.
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
    /// Both rims and four long edges are the stroke, and the mantle and caps the fill.
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
    /// Creates every edge of the given voxel shape at the given position, stroke only.
    ///
    /// @param shape    The voxel shape to outline.
    /// @param position The position of the voxel shape's origin.
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
    /// Creates text facing the camera, centered on the given point, at name tag size.
    /// The text is drawn in the stroke and a backdrop in the fill.
    ///
    /// Text passes the depth test under [Paint.Depth#VISIBLE] and ignores it under the other two modes.
    ///
    /// @param at   The point to center the text on.
    /// @param text The text to draw.
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
    /// Creates one shape that draws every given part in order.
    ///
    /// @param parts The parts to draw, in the order they should be drawn.
    ///
    /// @return The shape of all parts.
    ///
    static Shape of(Shape... parts) {
        List<Shape> shapes = List.of(parts);
        return brush -> shapes.forEach(shape -> shape.draw(brush));
    }
    
    ///
    /// The corner of the box at the given index, whose bits select the maximum on the x, y, and z axis in that order.
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
    /// The evenly spaced points of a ring of the given radius around the given center in the plane the normal stands on.
    ///
    /// Rings with the same normal start at the same angle.
    ///
    private static Vec3[] ring(Vec3 center, Vec3 normal, double radius) {
        Vec3 axis = normal.normalize();
        Vec3 start = axis.cross(Math.abs(axis.y) < 0.5D ? Vec3.Y_AXIS : Vec3.X_AXIS).normalize().scale(radius);
        Vec3 quarter = axis.cross(start);
        Vec3[] points = new Vec3[32];
        for (int i = 0; i < points.length; i++) {
            double angle = 2.0D * Math.PI * i / points.length;
            points[i] = center.add(start.scale(Math.cos(angle))).add(quarter.scale(Math.sin(angle)));
        }
        
        return points;
    }
    
    ///
    /// Draws the lines between the given points of a ring, closing it.
    ///
    private static void loop(Brush brush, Vec3[] points, int argb) {
        for (int i = 0; i < points.length; i++) {
            brush.line(points[i], points[(i + 1) % points.length], argb);
        }
    }
    
    ///
    /// Draws the disc inside the given points of a ring as triangles around its center.
    ///
    private static void disc(Brush brush, Vec3 center, Vec3[] points, int argb) {
        for (int i = 0; i < points.length; i++) {
            brush.triangle(center, points[i], points[(i + 1) % points.length], argb);
        }
    }
    
    ///
    /// Draws the surface of the sphere as triangles between rings of latitude, leaving out the empty triangles at the poles.
    ///
    private static void surface(Brush brush, Vec3 center, double radius, int argb) {
        Vec3[] upper = Shape.ring(center.add(0.0D, radius, 0.0D), Vec3.Y_AXIS, 0.0D);
        int bands = upper.length / 2;
        for (int band = 1; band <= bands; band++) {
            double angle = Math.PI * band / bands;
            Vec3[] lower = Shape.ring(center.add(0.0D, radius * Math.cos(angle), 0.0D), Vec3.Y_AXIS, radius * Math.sin(angle));
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
    /// Emits the shape into the given brush.
    ///
    /// @param brush The brush to draw with.
    ///
    void draw(Brush brush);
    
    ///
    /// This shape drawn with the given paint instead of its drawing's, colors, width, and depth alike, through [Brush#draw(Shape, Paint)].
    ///
    /// @param paint The paint to draw this shape with.
    ///
    /// @return The shape with its own paint.
    ///
    default Shape painted(Paint paint) {
        return brush -> brush.draw(this, paint);
    }
}
