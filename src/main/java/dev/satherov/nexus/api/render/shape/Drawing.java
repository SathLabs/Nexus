package dev.satherov.nexus.api.render.shape;

import dev.satherov.nexus.internal.render.shape.MeshBrush;
import dev.satherov.nexus.internal.render.shape.Primitives;
import dev.satherov.nexus.internal.render.shape.SceneMesh;
import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

///
/// One shape in a scene. Its coordinates are world coordinates until it follows an entity.
///
/// Every method throws an [IllegalStateException] off the client thread.
///
/// Can be started through [Scene#draw(Object, Shape, Paint)] and [Scene#draw(Shape, Paint)].
///
public final class Drawing {
    
    ///
    /// The scene the drawing is in.
    ///
    private final Scene scene;
    
    ///
    /// The key the drawing is under, or `null` if it has none.
    ///
    final @Nullable Object key;
    
    ///
    /// The shape the drawing draws.
    ///
    private final Shape shape;
    
    ///
    /// The paint the shape is drawn with.
    ///
    private Paint paint;
    
    ///
    /// What the shape emitted with the paint.
    ///
    private Primitives primitives;
    
    ///
    /// The entity the coordinates are relative to, or `null` if the drawing follows none.
    ///
    private @Nullable Entity entity;
    
    ///
    /// How many client ticks are left until the drawing expires, or `-1` if it never does.
    ///
    private int ticksLeft = -1;
    
    ///
    /// `true` until the drawing is removed.
    ///
    private boolean active = true;
    
    ///
    /// Should only ever be called from [Scene#draw(Object, Shape, Paint)] and [Scene#draw(Shape, Paint)].
    ///
    /// @param scene The scene the drawing is in.
    /// @param key   The key the drawing is under, or `null` if it has none.
    /// @param shape The shape the drawing draws.
    /// @param paint The paint the shape is drawn with.
    ///
    Drawing(Scene scene, @Nullable Object key, Shape shape, Paint paint) {
        this.scene = scene;
        this.key = key;
        this.shape = shape;
        this.paint = paint;
        
        this.primitives = Drawing.emit(shape, paint);
    }
    
    ///
    /// Draws the given shape into a new brush of the given paint.
    ///
    private static Primitives emit(Shape shape, Paint paint) {
        MeshBrush brush = new MeshBrush(paint);
        shape.draw(brush);
        return brush.finish();
    }
    
    ///
    /// The paint it is drawn with.
    ///
    /// @return The paint of the drawing.
    ///
    public Paint getPaint() {
        this.scene.checkThread();
        return this.paint;
    }
    
    ///
    /// @return `true` until removed, expired, replaced under its key, dropped with its entity, or dropped on leaving the level.
    ///
    public boolean isActive() {
        this.scene.checkThread();
        return this.active;
    }
    
    ///
    /// Draws the shape again with the paint, keeping key, entity, and expiry.
    ///
    /// Does nothing once inactive.
    ///
    /// @param paint The paint to draw the shape with.
    ///
    /// @return This drawing for chaining.
    ///
    @CanIgnoreReturnValue
    public Drawing repaint(Paint paint) {
        this.scene.checkThread();
        if (!this.active) {
            return this;
        }
        
        this.primitives = Drawing.emit(this.shape, paint);
        this.paint = paint;
        this.scene.changed = true;
        return this;
    }
    
    ///
    /// Makes the coordinates relative to the entity's position, interpolated between ticks.
    ///
    /// The drawing is removed when the entity leaves the client's level.
    ///
    /// Does nothing once inactive.
    ///
    /// @param entity The entity to follow.
    ///
    /// @return This drawing for chaining.
    ///
    @CanIgnoreReturnValue
    public Drawing follow(Entity entity) {
        this.scene.checkThread();
        if (!this.active) {
            return this;
        }
        
        this.entity = entity;
        this.scene.changed = true;
        return this;
    }
    
    ///
    /// Removes the drawing once the given number of client ticks passed, counted from now.
    ///
    /// A second call restarts the count.
    ///
    /// @param ticks The number of client ticks until the drawing is removed.
    ///
    /// @return This drawing for chaining.
    ///
    /// @throws IllegalArgumentException If the ticks are below `1`.
    ///
    @CanIgnoreReturnValue
    public Drawing expire(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        this.scene.checkThread();
        //noinspection ConstantValue Nothing enforces the range at runtime.
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }
        
        this.ticksLeft = ticks;
        return this;
    }
    
    ///
    /// Removes the drawing.
    ///
    /// Does nothing once inactive.
    ///
    public void remove() {
        this.scene.checkThread();
        if (this.active) {
            this.scene.drop(this);
        }
    }
    
    ///
    /// The entity the drawing follows.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @return The followed entity, or `null` if the drawing follows none.
    ///
    @ApiStatus.Internal
    public @Nullable Entity getEntity() {
        this.scene.checkThread();
        return this.entity;
    }
    
    ///
    /// Appends the vertex data of the shape into the given builders by render type, relative to the given origin.
    ///
    /// Should only ever be called from [SceneMesh#rebuild(Collection)].
    ///
    /// @param builders The builders by render type, which should hold one for every shape render type.
    /// @param origin   The position that the written positions are relative to, in the coordinates of the drawing.
    ///
    @ApiStatus.Internal
    public void writeInto(Map<RenderType, BufferBuilder> builders, Vec3 origin) {
        this.scene.checkThread();
        this.primitives.writeInto(builders, origin);
    }
    
    ///
    /// Marks the drawing as removed.
    ///
    void end() {
        this.active = false;
    }
    
    ///
    /// Ages the drawing by one client tick.
    ///
    /// @return `true` if the drawing expired or the entity it follows left the level of the client.
    ///
    boolean age() {
        if (this.ticksLeft > 0) {
            this.ticksLeft--;
        }
        
        Entity followed = this.entity;
        return this.ticksLeft == 0 || (followed != null && (followed.isRemoved() || !ClientTicks.isCurrentLevel(followed.level())));
    }
    
    ///
    /// Draws the labels of the shape, at the interpolated position of the entity if the drawing follows one.
    ///
    /// @param pose        The pose stack of the frame, whose origin is the camera.
    /// @param camera      The position of the camera.
    /// @param partialTick The partial tick of the frame.
    /// @param buffers     The buffer source the font draws into.
    ///
    void drawLabels(PoseStack pose, Vec3 camera, float partialTick, MultiBufferSource.BufferSource buffers) {
        Entity followed = this.entity;
        this.primitives.drawLabels(pose, followed == null ? camera : camera.subtract(followed.getPosition(partialTick)), buffers);
    }
}
