package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

///
/// The client tests that check the size, the regions, the pixels, and the goldens of a captured frame.
///
public class CaptureSamples {
    
    @ClientTest
    public static void sizeOfRenderTarget(Client client) {
        Capture capture = client.capture();
        RenderTarget target = client.minecraft().getMainRenderTarget();
        Assertions.assertThat(capture.width()).isEqualTo(target.width);
        Assertions.assertThat(capture.height()).isEqualTo(target.height);
    }
    
    @ClientTest
    public static void pixelMatchesWrite(Client client) throws IOException {
        Capture capture = client.capture();
        Path file = Files.createTempFile("capture", ".png");
        
        try {
            capture.write(file);
            
            try (NativeImage image = NativeImage.read(Files.readAllBytes(file))) {
                Assertions.assertThat(image.getWidth()).isEqualTo(capture.width());
                Assertions.assertThat(image.getHeight()).isEqualTo(capture.height());
                
                for (int y = 0; y < capture.height(); y++) {
                    for (int x = 0; x < capture.width(); x++) {
                        Assertions.assertThat(capture.pixel(x, y)).as("the pixel at %d, %d", x, y).isEqualTo(image.getPixel(x, y));
                    }
                }
            }
        } finally {
            Files.delete(file);
        }
    }
    
    @ClientTest
    public static void pixelOutsideFrame(Client client) {
        Capture capture = client.capture();
        Assertions.assertThatIndexOutOfBoundsException().isThrownBy(() -> capture.pixel(-1, 0));
        Assertions.assertThatIndexOutOfBoundsException().isThrownBy(() -> capture.pixel(capture.width(), 0));
        Assertions.assertThatIndexOutOfBoundsException().isThrownBy(() -> capture.pixel(0, -1));
        Assertions.assertThatIndexOutOfBoundsException().isThrownBy(() -> capture.pixel(0, capture.height()));
    }
    
    @ClientTest
    public static void regionInsideFrame(Client client) {
        Capture capture = client.capture();
        int width = 7;
        int height = 5;
        int x = capture.width() - width;
        int y = capture.height() - height;
        Capture region = capture.region(x, y, width, height);
        
        Assertions.assertThat(region.width()).isEqualTo(width);
        Assertions.assertThat(region.height()).isEqualTo(height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                Assertions.assertThat(region.pixel(column, row)).as("the pixel at %d, %d", column, row).isEqualTo(capture.pixel(x + column, y + row));
            }
        }
        
        Capture whole = capture.region(0, 0, capture.width(), capture.height());
        Assertions.assertThat(whole.width()).isEqualTo(capture.width());
        Assertions.assertThat(whole.height()).isEqualTo(capture.height());
    }
    
    @ClientTest
    public static void regionOutsideFrame(Client client) {
        Capture capture = client.capture();
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> capture.region(-1, 0, 1, 1));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> capture.region(0, -1, 1, 1));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> capture.region(capture.width() - 1, 0, 2, 1));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> capture.region(0, capture.height() - 1, 1, 2));
    }
    
    @ClientTest
    public static void pixelAssertion(Client client) {
        Capture capture = client.capture();
        int x = capture.width() / 2;
        int y = capture.height() / 2;
        int argb = capture.pixel(x, y);
        int other = argb ^ 1;
        
        capture.assertPixel(x, y, argb);
        Assertions.assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> capture.assertPixel(x, y, other))
                .withMessageContaining(String.format(Locale.ROOT, "%08X", argb))
                .withMessageContaining(String.format(Locale.ROOT, "%08X", other));
    }
    
    @ClientTest
    public static void goldenMismatchWritesFiles(Client client) throws IOException {
        if (RunOptions.fromProperties().record()) {
            return;
        }
        
        client.minecraft().setScreen(new GenericMessageScreen(Component.literal("Not the title screen")));
        client.tick();
        Capture capture = client.capture();
        
        Path failures = RunOptions.fromProperties().report().toAbsolutePath().getParent().resolve("failures/nexus/capture_samples/golden_mismatch_writes_files");
        Path frame = failures.resolve("title_screen.png");
        Path diff = failures.resolve("title_screen.diff.png");
        
        try {
            Assertions.assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> capture.assertGolden("title_screen"))
                    .withMessageContaining(frame.toString())
                    .withMessageContaining(diff.toString());
            Assertions.assertThat(frame).isRegularFile();
            Assertions.assertThat(diff).isRegularFile();
        } finally {
            Files.deleteIfExists(frame);
            Files.deleteIfExists(diff);
            Files.deleteIfExists(failures);
        }
    }
    
    @ClientTest
    public static void missingGolden(Client client) {
        if (RunOptions.fromProperties().record()) {
            return;
        }
        
        Capture capture = client.capture();
        Assertions.assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> capture.assertGolden("missing"));
    }
    
    @ClientTest
    public static void titleScreen(Client client) {
        client.capture().assertGolden("title_screen");
    }
}
