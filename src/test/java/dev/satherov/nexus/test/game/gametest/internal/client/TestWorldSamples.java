package dev.satherov.nexus.test.game.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

import org.assertj.core.api.Assertions;

import java.nio.file.Path;
import java.util.Objects;

///
/// The client tests that join and leave a test world.
///
public class TestWorldSamples {
    
    @ClientTest
    public static void joinWorld(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player);
        
        Assertions.assertThat(player.isCreative()).isTrue();
        Assertions.assertThat(player.getAbilities().flying).isTrue();
        Assertions.assertThat(player.blockPosition()).isEqualTo(level.getRespawnData().pos());
        Assertions.assertThat(level.getGameRules().get(GameRules.ADVANCE_TIME)).isFalse();
        Assertions.assertThat(level.getGameRules().get(GameRules.ADVANCE_WEATHER)).isFalse();
        Assertions.assertThat(level.clockManager().isAtTimeMarker(level.registryAccess().getOrThrow(WorldClocks.OVERWORLD), ClockTimeMarkers.NOON)).isTrue();
        client.leaveWorld();
    }
    
    @ClientTest
    public static void leaveWorld(Client client) {
        client.joinWorld();
        Path save = client.serverLevel().getServer().getWorldPath(LevelResource.ROOT).normalize();
        Assertions.assertThat(save).isDirectory();
        
        client.leaveWorld();
        Assertions.assertThat(client.screen()).isInstanceOf(TitleScreen.class);
        Assertions.assertThat(save).doesNotExist();
    }
}
