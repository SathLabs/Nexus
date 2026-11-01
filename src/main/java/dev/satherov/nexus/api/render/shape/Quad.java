package dev.satherov.nexus.api.render.shape;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

///
/// A parallelogram spanned from one corner by two edges.
///
/// @param origin The corner both edges start from.
/// @param edgeU  The edge along which the `u` of a sprite runs.
/// @param edgeV  The edge along which the `v` of a sprite runs.
///
public record Quad(Vec3 origin, Vec3 edgeU, Vec3 edgeV) {
    
    ///
    /// Creates the face of the given box on the given side.
    ///
    /// A sprite on the face will be upright when seen from outside the box.
    ///
    /// @param box  The box to take the face of.
    /// @param side The side of the box the face is on.
    ///
    /// @return The face.
    ///
    public static Quad of(AABB box, Direction side) {
        Vec3 east = new Vec3(box.getXsize(), 0.0D, 0.0D);
        Vec3 south = new Vec3(0.0D, 0.0D, box.getZsize());
        Vec3 down = new Vec3(0.0D, -box.getYsize(), 0.0D);
        return switch (side) {
            case DOWN -> new Quad(new Vec3(box.minX, box.minY, box.maxZ), east, south.reverse());
            case UP -> new Quad(new Vec3(box.minX, box.maxY, box.minZ), east, south);
            case NORTH -> new Quad(new Vec3(box.maxX, box.maxY, box.minZ), east.reverse(), down);
            case SOUTH -> new Quad(new Vec3(box.minX, box.maxY, box.maxZ), east, down);
            case WEST -> new Quad(new Vec3(box.minX, box.maxY, box.minZ), south, down);
            case EAST -> new Quad(new Vec3(box.maxX, box.maxY, box.maxZ), south.reverse(), down);
        };
    }
}
