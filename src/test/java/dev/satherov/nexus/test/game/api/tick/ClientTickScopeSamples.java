package dev.satherov.nexus.test.game.api.tick;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.TickScope;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.assertj.core.api.Assertions;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

///
/// Checks the client scope and the client level scopes, the client tickers they bind, and the end of both scopes on logout.
///
public class ClientTickScopeSamples {
    
    @ClientTest
    public static void clientLevelTaskRuns(Client client) {
        client.joinWorld();
        ClientLevel level = Objects.requireNonNull(client.minecraft().level);
        TickScope scope = Nexus.getMod().getScheduler().in(level);
        AtomicBoolean ran = new AtomicBoolean();
        scope.delay(1, () -> ran.set(true));
        Assertions.assertThat(Nexus.getMod().getScheduler().in(level)).as("the second scope of the client level").isSameAs(scope);
        client.until("the task of the client level scope to run", ran::get);
    }
    
    @ClientTest
    public static void clientScopeTaskRuns(Client client) {
        client.joinWorld();
        TickScope scope = Nexus.getMod().getScheduler().client();
        AtomicBoolean ran = new AtomicBoolean();
        scope.delay(1, () -> ran.set(true));
        Assertions.assertThat(Nexus.getMod().getScheduler().client()).as("the second client scope").isSameAs(scope);
        client.until("the task of the client scope to run", ran::get);
    }
    
    @ClientTest
    public static void clientTickerBinds(Client client) {
        client.joinWorld();
        ServerLevel serverLevel = client.serverLevel();
        ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level);
        BlockPos pos = serverLevel.getRespawnData().pos().above(3);
        serverLevel.getServer().execute(() -> serverLevel.setBlockAndUpdate(pos, ClientTickingBlock.entry().getDefaultState()));
        client.until("the block entity to load on the client", () -> ClientTickingBlock.recorder().find(clientLevel, pos).isPresent());
        
        ClientTickingBlock.Recorder recorder = ClientTickingBlock.recorder().find(clientLevel, pos).orElseThrow();
        client.until("the client ticker to tick", () -> !recorder.getElapsed().isEmpty());
        Assertions.assertThat(recorder.getElapsed()).as("elapsed passes of the ticks").containsExactly(1);
    }
    
    @ClientTest
    public static void scopesEndOnLogout(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        client.joinWorld();
        ClientLevel level = Objects.requireNonNull(client.minecraft().level);
        TickScope clientScope = scheduler.client();
        TickScope levelScope = scheduler.in(level);
        client.leaveWorld();
        Assertions.assertThatIllegalStateException().as("the client scope after logout").isThrownBy(() -> clientScope.delay(1, () -> { }));
        Assertions.assertThatIllegalStateException().as("the client level scope after logout").isThrownBy(() -> levelScope.delay(1, () -> { }));
        Assertions.assertThatIllegalStateException().as("the client scope requested after logout").isThrownBy(scheduler::client);
        Assertions.assertThatIllegalStateException().as("the scope of the unloaded client level").isThrownBy(() -> scheduler.in(level));
        
        client.joinWorld();
        TickScope rejoined = scheduler.client();
        AtomicBoolean ran = new AtomicBoolean();
        rejoined.delay(1, () -> ran.set(true));
        Assertions.assertThat(rejoined).as("the client scope after the rejoin").isNotSameAs(clientScope);
        client.until("the task of the client scope after the rejoin to run", ran::get);
    }
}
