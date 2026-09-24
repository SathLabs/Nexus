package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

///
/// The client tests that check the size, the pixels, and the goldens of a captured frame.
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
