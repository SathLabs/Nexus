package dev.satherov.nexus.gametest.api.client;

import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

///
/// One rendered frame captured from the framebuffer.
///
@Accessors(fluent = true)
public final class Capture {
    
    ///
    /// The identifier of the test that the frame was captured in.
    ///
    /// Also used as the path of a golden image.
    ///
    private final Identifier test;
    
    ///
    /// The width of the frame in framebuffer pixels.
    ///
    @Getter
    private final int width;
    
    ///
    /// The height of the frame in framebuffer pixels.
    ///
    @Getter
    private final int height;
    
    ///
    /// The colors of all pixels in the frame in ARGB.
    ///
    /// Stored row by row, starting in the top left corner.
    ///
    private final int[] pixels;
    
    ///
    /// Copies all pixels from the native image into the current capture.
    ///
    /// @param test  The identifier of the test the frame was captured in.
    /// @param image The native image the frame was read back into.
    ///
    private Capture(Identifier test, NativeImage image) {
        this.test = test;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = image.getPixels();
    }
    
    ///
    /// Creates a captured frame from the given render target.
    ///
    /// @param test   The identifier of the test the frame is captured in.
    /// @param target The render target the client draws its frames into.
    ///
    /// @return The frame the render target holds.
    ///
    /// @throws IllegalStateException If there was no frame captured from the framebuffer.
    ///
    @ApiStatus.Internal
    public static Capture from(Identifier test, RenderTarget target) {
        MutableObject<@Nullable NativeImage> frame = new MutableObject<>();
        Screenshot.takeScreenshot(target, frame::setValue);
        
        // Vanilla hands the image over on a fenced task, so the fence just waits, and the call below runs the task.
        try (GpuFence fence = RenderSystem.getDevice().createCommandEncoder().createFence()) {
            fence.awaitCompletion(Long.MAX_VALUE);
        }
        
        RenderSystem.executePendingTasks();
        
        NativeImage image = frame.get();
        if (image == null) {
            throw new IllegalStateException("The frame was not able to be captured from the framebuffer");
        }
        
        try (image) {
            return new Capture(test, image);
        }
    }
    
    ///
    /// The ARGB color at the position asked for.
    ///
    /// @param x The horizontal position of the pixel, starting from the left side.
    /// @param y The vertical position of the pixel, starting from the top side.
    ///
    /// @return The ARGB color at that position.
    ///
    /// @throws IndexOutOfBoundsException If the position is outside the frame.
    ///
    public int pixel(int x, int y) {
        Objects.checkIndex(x, this.width);
        Objects.checkIndex(y, this.height);
        return this.pixels[x + y * this.width];
    }
    
    ///
    /// Writes the captured frame to the given path as a png file.
    ///
    /// @param path The path to write the frame to.
    ///
    /// @throws UncheckedIOException If the frame could not be written.
    ///
    public void write(Path path) {
        try (NativeImage image = new NativeImage(this.width, this.height, false)) {
            for (int i = 0; i < this.pixels.length; i++) {
                image.setPixel(i % this.width, i / this.width, this.pixels[i]);
            }
            
            image.writeToFile(path);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write the frame of '" + this.test + "' to '" + path + "'", failure);
        }
    }
    
    ///
    /// Fails the test if the current frame differs from the golden saved at `<modid>/goldens/<class>/<name>.png` in the mod's resources.
    ///
    /// - `<modid>` is the id of the mod that owns the test.
    /// - `<class>` is the test class written in snake case.
    ///
    /// If the `-Precord` property is set, the frame will instead be written to the path specified under the `-Pgoldens` property.
    ///
    /// @param name The name of the golden, without the `.png`.
    ///
    public void assertGolden(String name) {
        String path = this.test.getNamespace() + "/goldens/" + StringUtils.substringBefore(this.test.getPath(), "/") + "/" + name + ".png";
        RunOptions options = RunOptions.fromProperties();
        
        if (options.record()) {
            this.record(path, options.goldens());
            return;
        }
        
        this.assertMatches(this.golden(path), path);
    }
    
    ///
    /// Writes the captured frame to the specified path.
    ///
    /// @param path    The path of the golden. Its parent directory will be the directory goldens are stored in.
    /// @param goldens The directory goldens are recorded into, or `null` if none was given.
    ///
    /// @throws AssertionError       If we're trying to record goldens via `-Precord` without a path being set under `-Pgoldens`.
    /// @throws UncheckedIOException If the directory the golden is stored in could not be created.
    ///
    private void record(String path, @Nullable Path goldens) {
        if (goldens == null) {
            throw new AssertionError("The run records goldens without '" + RunOptions.GOLDENS + "'");
        }
        
        Path golden = goldens.resolve(path);
        try {
            Files.createDirectories(golden.getParent());
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not create the directory the golden '" + path + "' are stored in", failure);
        }
        
        this.write(golden);
    }
    
    ///
    /// The golden stored at the given path.
    ///
    /// @param path The path of the golden in the resources.
    ///
    /// @return The golden at the path.
    ///
    /// @throws AssertionError       If there is no golden at the given path.
    /// @throws UncheckedIOException If the golden could not be read.
    ///
    private Capture golden(String path) {
        try (InputStream stream = Capture.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("There is no golden stored at '" + path + "'");
            }
            
            try (NativeImage image = NativeImage.read(stream)) {
                return new Capture(this.test, image);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read the golden from '" + path + "'", failure);
        }
    }
    
    ///
    /// Fails the test with the location of the first pixel that differs from the given golden.
    ///
    /// @param golden The golden the captured frame is compared against.
    /// @param path   The path of the golden, used for error messages.
    ///
    /// @throws AssertionError If the captured frame differs from the golden, in size or pixel color.
    ///
    private void assertMatches(Capture golden, String path) {
        if (golden.width != this.width || golden.height != this.height) {
            throw new AssertionError(String.format(Locale.ROOT, "The captured frame is %dx%d and the golden '%s', is %dx%d", this.width, this.height, path, golden.width, golden.height));
        }
        
        for (int i = 0; i < this.pixels.length; i++) {
            if (this.pixels[i] != golden.pixels[i]) {
                throw new AssertionError(String.format(
                        Locale.ROOT,
                        "The captured frame differs from the golden '%s' at %d, %d: %08X instead of %08X",
                        path,
                        i % this.width,
                        i / this.width,
                        this.pixels[i],
                        golden.pixels[i]
                ));
            }
        }
    }
}
