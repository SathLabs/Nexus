package dev.satherov.nexus.gametest.api.client;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;

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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Capture {
    
    ///
    /// The brightness that the matching pixels of a diff image are scaled to.
    ///
    private static final float DIFF_BRIGHTNESS = 0.25F;
    
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
        this(test, image.getWidth(), image.getHeight(), image.getPixels());
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
    /// Fails the test if the pixel at the given position is not exactly the given color.
    ///
    /// The failure message will contain both colors.
    ///
    /// @param x    The horizontal position of the pixel, starting from the left side.
    /// @param y    The vertical position of the pixel, starting from the top side.
    /// @param argb The ARGB color the pixel should have.
    ///
    /// @throws AssertionError            If the pixel has any other color.
    /// @throws IndexOutOfBoundsException If the position is outside the frame.
    ///
    public void assertPixel(int x, int y, int argb) {
        int pixel = this.pixel(x, y);
        if (pixel != argb) {
            throw new AssertionError(String.format(Locale.ROOT, "The pixel at '%d, %d' is '%08X' instead of '%08X'", x, y, pixel, argb));
        }
    }
    
    ///
    /// The given rectangle of this capture as a capture of its own.
    ///
    /// @param x      The horizontal position of the rectangle, starting from the left side.
    /// @param y      The vertical position of the rectangle, starting from the top side.
    /// @param width  The width of the rectangle in framebuffer pixels.
    /// @param height The height of the rectangle in framebuffer pixels.
    ///
    /// @return The pixels inside the rectangle as a capture of the same test.
    ///
    /// @throws IllegalArgumentException If the rectangle does not lie inside this capture.
    ///
    public Capture region(int x, int y, int width, int height) {
        if (x < 0 || y < 0 || width < 1 || height < 1 || width > this.width - x || height > this.height - y) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "The region '%d, %d, %dx%d' does not lie inside the capture of '%dx%d'",
                    x,
                    y,
                    width,
                    height,
                    this.width,
                    this.height
            ));
        }
        
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(this.pixels, x + (y + row) * this.width, pixels, row * width, width);
        }
        
        return new Capture(this.test, width, height, pixels);
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
    /// If the frame differs, the frame and a diff image will be written into the directory of the report,
    /// as `failures/<modid>/<class>/<method>/<name>.png` and `<name>.diff.png`, while the failure message will contain both paths.
    /// - `<method>` is the test method written in snake case.
    ///
    /// The diff image shows every differing pixel in red over a dark gray copy of the frame.
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
        
        Path failures = this.test.resolveAgainst(options.report().toAbsolutePath().getParent().resolve("failures"));
        this.assertMatches(this.golden(path), path, failures, name);
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
    /// @param golden   The golden the captured frame is compared against.
    /// @param path     The path of the golden, used for error messages.
    /// @param failures The directory the frame and its diff are written into if the frame differs.
    /// @param name     The name of the golden, without the `.png`.
    ///
    /// @throws AssertionError If the captured frame differs from the golden, in size or pixel color.
    ///
    private void assertMatches(Capture golden, String path, Path failures, String name) {
        if (golden.width != this.width || golden.height != this.height) {
            throw this.writeFailure(
                    String.format(Locale.ROOT, "The captured frame is %dx%d and the golden '%s', is %dx%d", this.width, this.height, path, golden.width, golden.height),
                    golden,
                    failures,
                    name
            );
        }
        
        for (int i = 0; i < this.pixels.length; i++) {
            if (this.pixels[i] != golden.pixels[i]) {
                throw this.writeFailure(
                        String.format(
                                Locale.ROOT,
                                "The captured frame differs from the golden '%s' at %d, %d: %08X instead of %08X",
                                path,
                                i % this.width,
                                i / this.width,
                                this.pixels[i],
                                golden.pixels[i]
                        ),
                        golden,
                        failures,
                        name
                );
            }
        }
    }
    
    ///
    /// Writes the captured frame and its diff against the given golden into the given directory and then creates the failure.
    ///
    /// @param message  The message of the failure, without the paths of the written files.
    /// @param golden   The golden the captured frame differs from.
    /// @param failures The directory the frame and its diff are written into.
    /// @param name     The name of the golden, without the `.png`.
    ///
    /// @return The failure with the given message and the paths of both files.
    ///
    /// @throws UncheckedIOException If the directory could not be created or a file could not be written.
    ///
    private AssertionError writeFailure(String message, Capture golden, Path failures, String name) {
        try {
            Files.createDirectories(failures);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not create the directory '" + failures + "' for the failure of '" + this.test + "'", failure);
        }
        
        Path frame = failures.resolve(name + ".png");
        Path diff = failures.resolve(name + ".diff.png");
        this.write(frame);
        this.diff(golden).write(diff);
        return new AssertionError(message + ", the frame was written to '" + frame + "' and the diff to '" + diff + "'");
    }
    
    ///
    /// Creates the diff image of the captured frame against the given golden, with the larger width and the larger height of the two.
    ///
    /// Every pixel that differs, or that only one of the two has, is red.
    /// Every other pixel is the pixel of the frame in dark gray.
    ///
    /// @param golden The golden the captured frame is compared against.
    ///
    /// @return The diff image of the captured frame against the golden.
    ///
    private Capture diff(Capture golden) {
        int width = Math.max(this.width, golden.width);
        int height = Math.max(this.height, golden.height);
        int[] pixels = new int[width * height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[x + y * width] = this.matchesAt(golden, x, y) ? ARGB.opaque(ARGB.scaleRGB(ARGB.greyscale(this.pixel(x, y)), Capture.DIFF_BRIGHTNESS)) : CommonColors.RED;
            }
        }
        
        return new Capture(this.test, width, height, pixels);
    }
    
    ///
    /// If both the captured frame and the given golden have a pixel at the given position, and it has the same color in both.
    ///
    /// @param golden The golden the captured frame is compared against.
    /// @param x      The horizontal position of the pixel, starting from the left side.
    /// @param y      The vertical position of the pixel, starting from the top side.
    ///
    /// @return `true` if both have the same pixel at the given position.
    ///
    private boolean matchesAt(Capture golden, int x, int y) {
        return x < this.width && x < golden.width && y < this.height && y < golden.height && this.pixel(x, y) == golden.pixel(x, y);
    }
}
