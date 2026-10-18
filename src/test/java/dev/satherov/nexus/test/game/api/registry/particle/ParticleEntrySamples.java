package dev.satherov.nexus.test.game.api.registry.particle;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.particle.ParticleEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks the particles that particle entries send to the players near them and the options the entries match.
///
public class ParticleEntrySamples {
    
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> plain;
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> unlimited;
    
    @ServerTest
    public static void spawnSendsTheParticlesToThePlayersNearThePosition(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> near = new ArrayList<>();
        List<Packet<?>> far = new ArrayList<>();
        List<ServerPlayer> players = List.of(
                ParticleEntrySamples.createPlayer(helper, pos, near),
                ParticleEntrySamples.createPlayer(helper, pos.add(100.0D, 0.0D, 0.0D), far)
        );
        
        level.players().addAll(players);
        try {
            plain.spawn(level, plain.value(), pos, 3);
        } finally {
            level.players().removeAll(players);
        }
        
        ClientboundLevelParticlesPacket sent = near.stream()
                .filter(ClientboundLevelParticlesPacket.class::isInstance)
                .map(ClientboundLevelParticlesPacket.class::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("The player near the position wasn't sent the particles"));
        
        helper.assertValueEqual(sent.getParticle(), plain.value(), "options of the particles");
        helper.assertValueEqual(sent.getCount(), 3, "count of the particles");
        helper.assertValueEqual(new Vec3(sent.getX(), sent.getY(), sent.getZ()), pos, "position of the particles");
        helper.assertTrue(far.stream().noneMatch(ClientboundLevelParticlesPacket.class::isInstance), "The player far from the position was sent the particles");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsTheParticlesOfATypeThatOverridesTheLimiterFarther(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> unlimited = ParticleEntrySamples.unlimited();
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = ParticleEntrySamples.createPlayer(helper, pos.add(100.0D, 0.0D, 0.0D), sent);
        level.players().add(player);
        try {
            unlimited.spawn(level, unlimited.value(), pos, 3);
        } finally {
            level.players().remove(player);
        }
        
        helper.assertTrue(sent.stream().anyMatch(ClientboundLevelParticlesPacket.class::isInstance), "The player 100 blocks away wasn't sent the particles of a type that overrides the limiter");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesOptionsOfTheTypeOnly(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        helper.assertTrue(plain.is(plain.value()), "The type did not match its own options");
        helper.assertFalse(plain.is(ParticleTypes.FLAME), "The type matched the options of flames");
        helper.succeed();
    }
    
    private static ParticleEntry<SimpleParticleType, SimpleParticleType> plain() {
        return Objects.requireNonNull(ParticleEntrySamples.plain);
    }
    
    private static ParticleEntry<SimpleParticleType, SimpleParticleType> unlimited() {
        return Objects.requireNonNull(ParticleEntrySamples.unlimited);
    }
    
    private static ServerPlayer createPlayer(GameTestHelper helper, Vec3 pos, List<Packet<?>> sent) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "particle_samples"), ClientInformation.createDefault());
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            
            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
                sent.add(packet);
            }
        };
        
        player.setPos(pos);
        return player;
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            ParticleEntrySamples.plain = registrations.particle("particle_entry_samples_plain").register();
            ParticleEntrySamples.unlimited = registrations.particle("particle_entry_samples_unlimited", () -> new SimpleParticleType(true)).register();
        }
    }
}
