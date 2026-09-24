package dev.satherov.nexus.gametest;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.startup.Entrypoint;
import net.neoforged.fml.startup.FatalErrorReporting;

///
/// The entrypoint of a client test run, which starts FML's client with the headless flag set so no early loading window
/// opens, and then hands over to minecraft's client main.
///
/// FML loads this before the game layer exists, so nothing in here may reference a minecraft, neoforge or mod class.
///
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GametestClient extends Entrypoint {

    ///
    /// Starts FML headless for the client distribution and then runs `net.minecraft.client.main.Main` with the same
    /// arguments.
    ///
    /// If the startup fails, the failure will be reported on the console and the run will exit with `1`.
    ///
    /// @param args The program arguments of the run.
    ///
    public static void main(String[] args) {
        try (Entrypoint.StartupResult result = Entrypoint.startup(args, true, Dist.CLIENT, true)) {
            Entrypoint.createMainMethodCallable(result, "net.minecraft.client.main.Main").invokeExact(result.loader().getProgramArgs().getArguments());
        } catch (Throwable failure) {
            FatalErrorReporting.reportFatalErrorOnConsole(failure);
            System.exit(1);
        }
    }
}
