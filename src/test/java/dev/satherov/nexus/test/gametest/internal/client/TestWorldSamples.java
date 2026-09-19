package dev.satherov.nexus.test.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

///
/// The client test that joins a world of its own and sees the block the server puts at its spawn.
///
public class TestWorldSamples {

    @ClientTest
    public static void placedBlockArrives(Client client) {
        client.joinWorld();

        ServerLevel serverLevel = client.serverLevel();
        ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level, "the client level");
        BlockPos spawn = serverLevel.getRespawnData().pos();

        serverLevel.setBlockAndUpdate(spawn, Blocks.STONE.defaultBlockState());
        client.until("the block at the spawn", () -> clientLevel.getBlockState(spawn).is(Blocks.STONE));
        client.leaveWorld();
    }
}
