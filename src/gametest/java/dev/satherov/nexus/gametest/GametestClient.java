package dev.satherov.nexus.gametest;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.startup.Entrypoint;
import net.neoforged.fml.startup.FatalErrorReporting;

import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

///
/// The entrypoint of a client test run, which starts FML's client with the headless flag set so no early loading window
/// opens, and then hands over to minecraft's client main.
///
/// FML loads this before the game layer exists, so nothing in here may reference a minecraft, neoforge or mod class.
///
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GametestClient extends Entrypoint {
    
    ///
    /// Deletes `shaderpacks/<name>` in the game directory, copies the shader pack of the given name from the classpath into it,
    /// and then writes `config/iris.properties` with the pack selected, shaders on, and the update message off.
    ///
    /// The pack has to be a directory on the classpath.
    ///
    /// @param name The folder name of the pack under `shaderpacks/`.
    ///
    /// @throws IllegalStateException If there is no pack of that name on the classpath.
    /// @throws IOException           If a file could not be deleted, copied, or written.
    /// @throws URISyntaxException    If the URL of the pack is not a valid URI.
    ///
    private static void installShaderPack(String name) throws IOException, URISyntaxException {
        URL resource = GametestClient.class.getClassLoader().getResource("shaderpacks/" + name);
        if (resource == null) {
            throw new IllegalStateException("There is no shader pack '" + name + "' on the classpath");
        }
        
        Path target = Files.createDirectories(Path.of("shaderpacks")).resolve(name);
        if (Files.exists(target)) {
            try (Stream<Path> stale = Files.walk(target)) {
                for (Path path : stale.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        
        Path source = Path.of(resource.toURI());
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Files.copy(path, target.resolve(source.relativize(path)));
            }
        }
        
        Properties properties = new Properties();
        properties.setProperty("shaderPack", name);
        properties.setProperty("enableShaders", "true");
        properties.setProperty("disableUpdateMessage", "true");
        try (Writer writer = Files.newBufferedWriter(Files.createDirectories(Path.of("config")).resolve("iris.properties"))) {
            properties.store(writer, null);
        }
    }
    
    ///
    /// Starts FML headless for the client distribution and then runs `net.minecraft.client.main.Main` with the same
    /// arguments.
    ///
    /// If `nexus.gametest.shaderpack` is set, the pack of that name will first be copied into the game directory and selected in iris,
    /// or the startup fails if the pack is missing.
    ///
    /// If the startup fails, the failure will be reported on the console and the run will exit with `1`.
    ///
    /// @param args The program arguments of the run.
    ///
    public static void main(String[] args) {
        try {
            String shaderpack = System.getProperty("nexus.gametest.shaderpack");
            if (shaderpack != null) {
                GametestClient.installShaderPack(shaderpack);
            }
            
            try (Entrypoint.StartupResult result = Entrypoint.startup(args, true, Dist.CLIENT, true)) {
                Entrypoint.createMainMethodCallable(result, "net.minecraft.client.main.Main").invokeExact(result.loader().getProgramArgs().getArguments());
            }
        } catch (Throwable failure) {
            FatalErrorReporting.reportFatalErrorOnConsole(failure);
            System.exit(1);
        }
    }
}
