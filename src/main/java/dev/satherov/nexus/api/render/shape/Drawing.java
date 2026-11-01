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
/// A shape drawn in a scene with a paint.
///
/// The coordinates of the shape are world coordinates until the drawing follows an entity.
///
/// Every method throws an [IllegalStateException] off the client thread.
///
/// Can be created through [Scene#draw(Object, Shape, Paint)] and [Scene#draw(Shape, Paint)].
///
public final class Drawing {
    
    ///
    /// The key of the drawing instance, or `null` if there is none.
    ///
    final @Nullable Object key;
    
    ///
    /// The scene the drawing is in.
    ///
    private final Scene scene;
    
    ///
    /// The shape the drawing draws.
    ///
    private final Shape shape;
    
    ///
    /// The paint the shape is drawn with.
    ///
    private Paint paint;
    
    ///
    /// The primitives the shape drew with the paint.
    ///
    private Primitives primitives;
    
    ///
    /// The entity the drawing follows, or `null` if there is none.
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
    /// @param key   The key to draw the shape under, or `null` to draw it under no key.
    /// @param shape The shape to draw.
    /// @param paint The paint to draw the shape with.
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
    /// @param shape The shape to draw.
    /// @param paint The paint to draw the shape with.
    ///
    /// @return The primitives the shape drew.
    ///
    private static Primitives emit(Shape shape, Paint paint) {
        MeshBrush brush = new MeshBrush(paint);
        shape.draw(brush);
        return brush.finish();
    }
    
    ///
    /// The paint the shape is drawn with.
    ///
    /// @return The paint of the drawing.
    ///
    public Paint getPaint() {
        this.scene.checkThread();
        return this.paint;
    }
    
    ///
    /// @return `true` until the drawing is removed.
    ///
    public boolean isActive() {
        this.scene.checkThread();
        return this.active;
    }
    
    ///
    /// Draws the shape again with the given paint.
    ///
    /// Does nothing if the drawing was already removed.
    ///
    /// @param paint The paint to draw the shape with.
    ///
    /// @return The drawing for chaining.
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
    /// Makes the coordinates of the shape relative to the position of the given entity.
    ///
    /// The drawing will be removed once the entity leaves the level of the client.
    ///
    /// Does nothing if the drawing was already removed.
    ///
    /// @param entity The entity to follow.
    ///
    /// @return The drawing for chaining.
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
    /// Removes the drawing after the given number of client ticks.
    ///
    /// A second call restarts the count.
    ///
    /// @param ticks The number of client ticks until the drawing is removed.
    ///
    /// @return The drawing for chaining.
    ///
    /// @throws IllegalArgumentException If the given number of ticks is below `1`.
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
    /// Does nothing if the drawing was already removed.
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
    /// @return The entity the drawing follows, or `null` if there is none.
    ///
    @ApiStatus.Internal
    public @Nullable Entity getEntity() {
        this.scene.checkThread();
        return this.entity;
    }
    
    ///
    /// Appends the vertex data of the shape to the given buffer builders, relative to the given origin.
    ///
    /// Should only ever be called from [SceneMesh#rebuild(Collection)].
    ///
    /// @param builders The buffer builders by render type, which should hold one for every render type a shape draws with.
    /// @param origin   The position the written vertices are relative to, in the coordinates of the drawing.
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
    /// Draws the labels of the shape.
    ///
    /// If the drawing follows an entity, the labels will be drawn relative to its interpolated position.
    ///
    /// @param pose        The pose stack of the frame, whose origin is the camera.
    /// @param camera      The position of the camera.
    /// @param partialTick The partial tick of the frame.
    /// @param buffers     The buffer source the labels are drawn into.
    ///
    void drawLabels(PoseStack pose, Vec3 camera, float partialTick, MultiBufferSource.BufferSource buffers) {
        Entity followed = this.entity;
        this.primitives.drawLabels(pose, followed == null ? camera : camera.subtract(followed.getPosition(partialTick)), buffers);
    }
}
