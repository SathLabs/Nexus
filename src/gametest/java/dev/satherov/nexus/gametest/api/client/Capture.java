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
/// One rendered frame, read back from the framebuffer.
///
@Accessors(fluent = true)
public final class Capture {

    ///
    /// The id of the test the frame was captured in, which the path of a golden is built from.
    ///
    private final Identifier test;

    ///
    /// Width in framebuffer pixels.
    ///
    @Getter
    private final int width;

    ///
    /// Height in framebuffer pixels.
    ///
    @Getter
    private final int height;

    ///
    /// The colors of the frame in ARGB, row by row from the top left one.
    ///
    private final int[] pixels;

    ///
    /// Copies the image's pixels out, so the frame outlives the native memory it was read into.
    ///
    private Capture(Identifier test, NativeImage image) {
        this.test = test;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = image.getPixels();
    }

    ///
    /// Reads the frame the target holds back from the GPU.
    ///
    /// @param test   The id of the test the frame is captured in.
    /// @param target The render target the client draws its frames into.
    ///
    @ApiStatus.Internal
    public static Capture from(Identifier test, RenderTarget target) {
        MutableObject<NativeImage> frame = new MutableObject<>();
        Screenshot.takeScreenshot(target, frame::setValue);

        // Vanilla hands the image over on a fenced task, so the fence waits the copy out and the flush below runs that task.
        try (GpuFence fence = RenderSystem.getDevice().createCommandEncoder().createFence()) {
            fence.awaitCompletion(Long.MAX_VALUE);
        }

        RenderSystem.executePendingTasks();

        NativeImage image = frame.get();
        if (image == null) throw new IllegalStateException("the frame was not read back from the framebuffer");
        try (image) {
            return new Capture(test, image);
        }
    }

    ///
    /// The ARGB color at the position.
    ///
    /// @param x The column of the pixel, from the left.
    /// @param y The row of the pixel, from the top.
    ///
    /// @throws IndexOutOfBoundsException If the position is outside the frame.
    ///
    public int pixel(int x, int y) {
        Objects.checkIndex(x, this.width);
        Objects.checkIndex(y, this.height);
        return this.pixels[x + y * this.width];
    }

    ///
    /// Writes the frame as PNG.
    ///
    /// @param path The file to write.
    ///
    public void write(Path path) {
        try (NativeImage image = new NativeImage(this.width, this.height, false)) {
            for (int i = 0; i < this.pixels.length; i++) {
                image.setPixel(i % this.width, i / this.width, this.pixels[i]);
            }

            image.writeToFile(path);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not write the frame to '" + path + "'", failure);
        }
    }

    ///
    /// Fails the test if the frame differs from the golden at `<modid>/goldens/<class>/<name>.png` in the mod's resources,
    /// `<class>` being the test's class in snake case; with `-Precord` writes it under `-Pgoldens` instead and passes.
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
    /// Writes the frame as the golden the run records, creating the directories it sits in.
    ///
    private void record(String path, @Nullable Path goldens) {
        if (goldens == null) {
            throw new AssertionError("the run records goldens without '" + RunOptions.GOLDENS + "'");
        }

        Path golden = goldens.resolve(path);
        try {
            Files.createDirectories(golden.getParent());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not create the directory of the golden '" + path + "'", failure);
        }

        this.write(golden);
    }

    ///
    /// The golden at the path, read from the resources every mod of the run has on the class path.
    ///
    private Capture golden(String path) {
        try (InputStream stream = Capture.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("there is no golden '" + path + "'");
            }

            try (NativeImage image = NativeImage.read(stream)) {
                return new Capture(this.test, image);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read the golden '" + path + "'", failure);
        }
    }

    ///
    /// Fails the test at the first pixel the golden has a different color at.
    ///
    private void assertMatches(Capture golden, String path) {
        if (golden.width != this.width || golden.height != this.height) {
            throw new AssertionError(String.format(Locale.ROOT, "the frame is %dx%d and the golden '%s' is %dx%d", this.width, this.height, path, golden.width, golden.height));
        }

        for (int i = 0; i < this.pixels.length; i++) {
            if (this.pixels[i] != golden.pixels[i]) {
                throw new AssertionError(String.format(
                        Locale.ROOT,
                        "the frame differs from the golden '%s' at %d, %d: %08X instead of %08X",
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
