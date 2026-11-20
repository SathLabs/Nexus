package dev.satherov.nexus.test.game.api.tick;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.Task;
import dev.satherov.nexus.api.tick.TickScope;
import dev.satherov.nexus.api.tick.Ticker;
import dev.satherov.nexus.api.tick.ticker.Next;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.internal.tick.Ticks;
import dev.satherov.nexus.internal.tick.client.ClientSession;
import dev.satherov.nexus.test.game.api.tick.budget.BudgetSamples;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;

import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

///
/// Checks the tick scopes and bound tickers that need a world of the client's own, on the client and on its integrated server.
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
    
    @ClientTest
    public static void clientLevelScopeWorksInItsLoad(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        AtomicBoolean ran = new AtomicBoolean();
        Consumer<LevelEvent.Load> listener = event -> {
            if (event.getLevel() instanceof ClientLevel level) {
                scheduler.in(level).delay(1, () -> ran.set(true));
            }
        };
        
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, listener);
        try {
            client.joinWorld();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        
        client.until("the task scheduled in the load of the client level to run", ran::get);
    }
    
    @ClientTest
    public static void scopesEndOnReconfiguration(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        client.joinWorld();
        TickScope scope = scheduler.client();
        MinecraftServer server = client.serverLevel().getServer();
        UUID id = Objects.requireNonNull(client.minecraft().player).getUUID();
        server.execute(() -> Objects.requireNonNull(server.getPlayerList().getPlayer(id)).connection.switchToConfig());
        client.until("the client scope to end", () -> ClientTickScopeSamples.hasEnded(scope));
        Assertions.assertThatIllegalStateException().as("the client scope requested while the client is configured").isThrownBy(scheduler::client);
        
        client.until("the server to configure the player again", () -> server.submit(() -> ClientTickScopeSamples.startConfiguration(server, id)).join());
        client.until("the client to rejoin the world", () -> client.minecraft().player != null);
        Assertions.assertThat(scheduler.client()).as("the client scope after the rejoin").isNotSameAs(scope);
    }
    
    @ClientTest
    public static void scopesEndOnALoginWithoutALogout(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        client.joinWorld();
        TickScope scope = scheduler.client();
        Minecraft minecraft = client.minecraft();
        // A configuration phase whose start and end are both handled before the next client tick logs in again without a logout.
        ClientSession.onLoggingIn(new ClientPlayerNetworkEvent.LoggingIn(Objects.requireNonNull(minecraft.gameMode), Objects.requireNonNull(minecraft.player), Objects.requireNonNull(minecraft.getConnection()).getConnection()));
        Assertions.assertThat(ClientTickScopeSamples.hasEnded(scope)).as("the client scope after a second login").isTrue();
        
        TickScope next = scheduler.client();
        AtomicBoolean ran = new AtomicBoolean();
        next.delay(1, () -> ran.set(true));
        Assertions.assertThat(next).as("the client scope after the second login").isNotSameAs(scope);
        client.until("the task of the client scope after the second login to run", ran::get);
    }
    
    @ClientTest
    public static void levelBuiltByAModIsNotHeld(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        client.joinWorld();
        Minecraft minecraft = client.minecraft();
        AtomicBoolean refusedInLoad = new AtomicBoolean();
        Consumer<LevelEvent.Load> listener = event -> {
            if (event.getLevel() instanceof ClientLevel level) {
                try {
                    TickScope _ = scheduler.in(level);
                } catch (IllegalStateException _) {
                    refusedInLoad.set(true);
                }
            }
        };
        
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, listener);
        ClientLevel preview;
        try {
            preview = ClientTickScopeSamples.buildLevel(minecraft);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        
        Assertions.assertThat(refusedInLoad).as("the scope of a client level that a mod built, requested in its load").isTrue();
        AtomicInteger ticks = new AtomicInteger();
        Consumer<ClientTickEvent.Pre> counter = _ -> ticks.incrementAndGet();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, counter);
        try {
            client.until("a client tick after the level was built", () -> ticks.get() >= 1);
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
        }
        
        Assertions.assertThatIllegalStateException().as("the scope of a client level that a mod built, requested after a tick").isThrownBy(() -> scheduler.in(preview));
    }
    
    @ClientTest
    public static void levelSwappedInThePacketHandlerIsHeld(Client client) {
        client.joinWorld();
        Minecraft minecraft = client.minecraft();
        AtomicBoolean swappedHeld = new AtomicBoolean();
        AtomicBoolean nestedHeld = new AtomicBoolean(true);
        AtomicBoolean laterHeld = new AtomicBoolean(true);
        AtomicReference<ClientLevel> swapped = new AtomicReference<>();
        Consumer<LevelEvent.Load> listener = event -> {
            if (event.getLevel() instanceof SwappedLevel level) {
                swappedHeld.set(Ticks.isHeld(level));
                ClientLevel _ = ClientTickScopeSamples.buildLevel(minecraft);
            } else if (event.getLevel() instanceof ClientLevel level) {
                (swapped.get() == null ? nestedHeld : laterHeld).set(Ticks.isHeld(level));
            }
        };
        
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, listener);
        try {
            // A mod that swaps the level of a login for a subclass of its own builds it inside the handler, which runs in this window.
            ClientSession.switchLevel(() -> {
                swapped.set(new SwappedLevel(minecraft));
                ClientLevel _ = ClientTickScopeSamples.buildLevel(minecraft);
            });
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        
        Assertions.assertThat(swappedHeld).as("a client level subclass that a packet handler built, in its load").isTrue();
        Assertions.assertThat(nestedHeld).as("a client level built inside the load of that level, in its own load").isFalse();
        Assertions.assertThat(laterHeld).as("a client level built later in the same packet handler, in its own load").isFalse();
        Assertions.assertThat(Ticks.isHeld(swapped.get())).as("the client level subclass after the packet handler, which never switched to it").isFalse();
    }
    
    @ClientTest
    public static void serverScopeLeftByAStopIsReplaced(Client client) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        Ticks ticks = new Ticks();
        AtomicReference<TickScope> stale = new AtomicReference<>();
        // Runs after the stop listener of the ticks, which leaves the scope behind as a stop does when an earlier listener of it throws.
        Consumer<ServerStoppedEvent> leftBehind = event -> stale.set(ticks.getServerScope(event.getServer(), () -> new TickScope(scheduler, LogicalSide.SERVER)));
        client.joinWorld();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerStoppedEvent.class, leftBehind);
        try {
            client.leaveWorld();
            client.until("the stop of the server", () -> stale.get() != null);
        } finally {
            NeoForge.EVENT_BUS.unregister(leftBehind);
        }
        
        client.joinWorld();
        MinecraftServer server = client.serverLevel().getServer();
        AtomicBoolean ran = new AtomicBoolean();
        TickScope scope = server.submit(() -> {
            TickScope next = ticks.getServerScope(server, () -> new TickScope(scheduler, LogicalSide.SERVER));
            next.delay(1, () -> ran.set(true));
            return next;
        }).join();
        Assertions.assertThat(scope).as("the server scope of the next server").isNotSameAs(stale.get());
        client.until("the task of the server scope of the next server to run", ran::get);
    }
    
    @ClientTest
    public static void pausedClientScopeDoesNotPass(Client client) {
        client.joinWorld();
        TickScope scope = Nexus.getMod().getScheduler().client();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicInteger ticks = new AtomicInteger();
        Consumer<ClientTickEvent.Post> counter = _ -> ticks.incrementAndGet();
        client.minecraft().setScreen(new PauseScreen(true));
        client.until("the game to pause", () -> client.minecraft().isPaused());
        scope.delay(1, () -> ran.set(true));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, counter);
        try {
            client.until("three client ticks while the game is paused", () -> ticks.get() >= 3);
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
        }
        
        Assertions.assertThat(ran).as("the task of the paused client scope").isFalse();
        client.minecraft().setScreen(null);
        client.until("the task of the client scope after the pause", ran::get);
    }
    
    @ClientTest
    public static void frozenLevelScopeDoesNotPass(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        MinecraftServer server = level.getServer();
        Scheduler scheduler = Nexus.getMod().getScheduler();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicInteger passes = new AtomicInteger();
        server.execute(() -> {
            server.tickRateManager().setFrozen(true);
            scheduler.in(level).delay(1, () -> ran.set(true));
            Task _ = scheduler.server().every(1, passes::incrementAndGet);
        });
        client.until("three passes of the server scope while the level is frozen", () -> passes.get() >= 3);
        Assertions.assertThat(ran).as("the task of the frozen level scope").isFalse();
        
        server.execute(() -> server.tickRateManager().setFrozen(false));
        client.until("the task of the level scope after the level was unfrozen", ran::get);
    }
    
    @ClientTest
    public static void frozenLevelScopeForgetsCancelledTickers(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        MinecraftServer server = level.getServer();
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BlockPos pos = level.getRespawnData().pos().above(3);
        AtomicInteger held = new AtomicInteger(-1);
        server.execute(() -> {
            TickScope scope = scheduler.in(level);
            server.tickRateManager().setFrozen(true);
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            scope.ticker(Objects.requireNonNull(level.getBlockEntity(pos)), _ -> Next.TICK);
            level.removeBlock(pos, false);
            scheduler.server().delay(2, () -> held.set(scope.getTickerCount()));
        });
        client.until("two passes of the server scope while the level is frozen", () -> held.get() >= 0);
        server.execute(() -> server.tickRateManager().setFrozen(false));
        Assertions.assertThat(held.get()).as("the tickers that the frozen level scope holds after the block was broken").isZero();
    }
    
    @ClientTest
    public static void unloadingTheChunkDropsTheTicker(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = level.getRespawnData().pos().offset(512, 0, 512);
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        AtomicInteger ticks = new AtomicInteger();
        Ticker ticker = server.submit(() -> {
            level.setChunkForced(chunkX, chunkZ, true);
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            return Nexus.getMod().getScheduler().in(level).ticker(Objects.requireNonNull(level.getBlockEntity(pos)), _ -> {
                ticks.incrementAndGet();
                return Next.TICK;
            });
        }).join();
        client.until("the bound ticker to tick in the forced chunk", () -> ticks.get() > 0);
        
        server.execute(() -> level.setChunkForced(chunkX, chunkZ, false));
        client.until("the chunk of the bound ticker to unload", () -> !ticker.isActive());
    }
    
    @ClientTest
    public static void boundTickerEndsWithItsLevel(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        BlockPos pos = level.getRespawnData().pos().above(3);
        Ticker ticker = level.getServer().submit(() -> {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            return Nexus.getMod().getScheduler().in(level).ticker(Objects.requireNonNull(level.getBlockEntity(pos)), _ -> Next.SLEEP);
        }).join();
        client.leaveWorld();
        Assertions.assertThat(ticker.isActive()).as("the bound ticker after its level unloaded").isFalse();
    }
    
    @ClientTest
    public static void boundTickerWaitsOutsideTheWorldBorder(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        BlockPos pos = level.getRespawnData().pos().above(3);
        List<Integer> elapsed = new CopyOnWriteArrayList<>();
        level.getServer().execute(() -> {
            WorldBorder border = level.getWorldBorder();
            double size = border.getSize();
            double centerX = border.getCenterX();
            double centerZ = border.getCenterZ();
            TickScope scope = Nexus.getMod().getScheduler().in(level);
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            scope.ticker(Objects.requireNonNull(level.getBlockEntity(pos)), passes -> {
                elapsed.add(passes);
                return Next.TICK;
            });
            scope.delay(2, () -> {
                border.setSize(1.0D);
                border.setCenter(pos.getX() + 100.0D, pos.getZ());
            });
            scope.delay(7, () -> {
                border.setSize(size);
                border.setCenter(centerX, centerZ);
            });
        });
        client.until("two ticks of the bound ticker", () -> elapsed.size() >= 2);
        Assertions.assertThat(elapsed.subList(0, 2)).as("elapsed passes of the ticks before and after the world border left the block").containsExactly(1, 6);
    }
    
    @ClientTest
    public static void clientJobSpendsTheMinimumOnly(Client client) {
        int steps = BudgetSamples.stepsOfOnePass(new TickScope(Nexus.getMod().getScheduler(), LogicalSide.CLIENT), () -> true);
        Assertions.assertThat(steps).as("steps of 1 ms of a client job in a pass with time left under a minimum of 5 ms").isBetween(2, 5);
    }
    
    private static ClientLevel buildLevel(Minecraft minecraft) {
        ClientLevel current = Objects.requireNonNull(minecraft.level);
        return new ClientLevel(Objects.requireNonNull(minecraft.getConnection()), new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false), Level.OVERWORLD, current.dimensionTypeRegistration(), 2, 2, minecraft.levelRenderer, false, 0L, current.getSeaLevel());
    }
    
    private static boolean hasEnded(TickScope scope) {
        try {
            scope.delay(1, () -> { }).cancel();
        } catch (IllegalStateException _) {
            return true;
        }
        
        return false;
    }
    
    private static boolean startConfiguration(MinecraftServer server, UUID id) {
        for (Connection connection : server.getConnection().getConnections()) {
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener && listener.getOwner().id().equals(id)) {
                listener.startConfiguration();
                return true;
            }
        }
        
        return false;
    }
    
    private static final class SwappedLevel extends ClientLevel {
        
        private SwappedLevel(Minecraft minecraft) {
            ClientLevel current = Objects.requireNonNull(minecraft.level);
            super(Objects.requireNonNull(minecraft.getConnection()), new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false), Level.OVERWORLD, current.dimensionTypeRegistration(), 2, 2, minecraft.levelRenderer, false, 0L, current.getSeaLevel());
        }
    }
}
