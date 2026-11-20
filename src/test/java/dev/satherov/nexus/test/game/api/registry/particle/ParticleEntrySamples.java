package dev.satherov.nexus.test.game.api.registry.particle;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.particle.ParticleEntry;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
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

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks the particles that particle entries send to the players near them, the options the entries match, and the providers they register on the client.
///
public class ParticleEntrySamples {
    
    private static final List<ParticleType<?>> CREATED = new ArrayList<>();
    
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> plain;
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> unlimited;
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> sprites;
    private static @Nullable ParticleEntry<SimpleParticleType, SimpleParticleType> provider;
    private static boolean clientCodeRan;
    
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
        
        ClientboundLevelParticlesPacket sent = ParticleEntrySamples.particles(helper, near, "The player near the position wasn't sent the particles");
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
    public static void spawnSendsTheParticlesWithTheGivenSpreadAndSpeed(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> near = new ArrayList<>();
        ServerPlayer player = ParticleEntrySamples.createPlayer(helper, pos, near);
        level.players().add(player);
        try {
            plain.spawn(level, plain.value(), pos, 3, new Vec3(0.5D, 0.25D, 0.125D), 2.0D);
        } finally {
            level.players().remove(player);
        }
        
        ClientboundLevelParticlesPacket sent = ParticleEntrySamples.particles(helper, near, "The player near the position wasn't sent the particles");
        helper.assertValueEqual(sent.getXDist(), 0.5F, "spread of the particles on the x axis");
        helper.assertValueEqual(sent.getYDist(), 0.25F, "spread of the particles on the y axis");
        helper.assertValueEqual(sent.getZDist(), 0.125F, "spread of the particles on the z axis");
        helper.assertValueEqual(sent.getMaxSpeed(), 2.0F, "speed of the particles");
        helper.assertFalse(sent.isOverrideLimiter(), "The particles of the default visibility override the limiter");
        helper.assertFalse(sent.alwaysShow(), "The particles of the default visibility always show");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsTheParticlesThatAlwaysShowToThePlayersNearThePosition(GameTestHelper helper) {
        List<Packet<?>> near = new ArrayList<>();
        List<Packet<?>> far = new ArrayList<>();
        ParticleEntrySamples.spawnNearAndFar(helper, ParticleEntry.Visibility.ALWAYS_SHOW, near, far);
        
        ClientboundLevelParticlesPacket sent = ParticleEntrySamples.particles(helper, near, "The player 20 blocks away wasn't sent the particles that always show");
        helper.assertTrue(sent.alwaysShow(), "The particles that always show don't always show");
        helper.assertFalse(sent.isOverrideLimiter(), "The particles that always show override the limiter");
        helper.assertTrue(far.stream().noneMatch(ClientboundLevelParticlesPacket.class::isInstance), "The player 100 blocks away was sent the particles that always show");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsTheParticlesThatOverrideTheLimiterFarther(GameTestHelper helper) {
        List<Packet<?>> near = new ArrayList<>();
        List<Packet<?>> far = new ArrayList<>();
        ParticleEntrySamples.spawnNearAndFar(helper, ParticleEntry.Visibility.OVERRIDE_LIMITER, near, far);
        
        ClientboundLevelParticlesPacket sent = ParticleEntrySamples.particles(helper, far, "The player 100 blocks away wasn't sent the particles that override the limiter");
        helper.assertTrue(sent.isOverrideLimiter(), "The particles that override the limiter don't override it");
        helper.assertFalse(sent.alwaysShow(), "The particles that override the limiter always show");
        helper.assertTrue(near.stream().anyMatch(ClientboundLevelParticlesPacket.class::isInstance), "The player 20 blocks away wasn't sent the particles that override the limiter");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsTheParticlesToTheGivenPlayerOnly(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> given = new ArrayList<>();
        List<Packet<?>> other = new ArrayList<>();
        ServerPlayer player = ParticleEntrySamples.createPlayer(helper, pos, given);
        List<ServerPlayer> players = List.of(player, ParticleEntrySamples.createPlayer(helper, pos, other));
        level.players().addAll(players);
        boolean reached;
        try {
            reached = plain.spawn(player, plain.value(), pos, 3);
        } finally {
            level.players().removeAll(players);
        }
        
        helper.assertTrue(reached, "The given player near the position was out of range");
        helper.assertValueEqual(ParticleEntrySamples.particles(helper, given, "The given player wasn't sent the particles").getCount(), 3, "count of the particles");
        helper.assertTrue(other.stream().noneMatch(ClientboundLevelParticlesPacket.class::isInstance), "Another player near the position was sent the particles");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsNothingToAGivenPlayerOutOfRange(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = ParticleEntrySamples.createPlayer(helper, pos.add(100.0D, 0.0D, 0.0D), sent);
        
        helper.assertFalse(plain.spawn(player, plain.value(), pos, 3, new Vec3(0.5D, 0.25D, 0.125D), 2.0D), "The player 100 blocks away was in range");
        helper.assertTrue(sent.stream().noneMatch(ClientboundLevelParticlesPacket.class::isInstance), "The player 100 blocks away was sent the particles");
        helper.succeed();
    }
    
    @ServerTest
    public static void spawnSendsTheParticlesThatOverrideTheLimiterToAGivenFarPlayer(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = ParticleEntrySamples.createPlayer(helper, pos.add(100.0D, 0.0D, 0.0D), sent);
        
        helper.assertTrue(plain.spawn(player, plain.value(), ParticleEntry.Visibility.OVERRIDE_LIMITER, pos, 3, Vec3.ZERO, 0.0D), "The player 100 blocks away was out of range");
        helper.assertTrue(ParticleEntrySamples.particles(helper, sent, "The player 100 blocks away wasn't sent the particles").isOverrideLimiter(), "The particles that override the limiter don't override it");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesOptionsOfTheTypeOnly(GameTestHelper helper) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        helper.assertTrue(plain.is(plain.value()), "The type did not match its own options");
        helper.assertFalse(plain.is(ParticleTypes.FLAME), "The type matched the options of flames");
        helper.succeed();
    }
    
    @ServerTest
    public static void neverRunsTheClientCodeOnADedicatedServer(GameTestHelper helper) {
        helper.assertFalse(ParticleEntrySamples.clientCodeRan, "The client code of a particle type ran on a dedicated server");
        helper.succeed();
    }
    
    @ClientTest
    public static void createsTheParticlesWithTheRegisteredProviders(Client client) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> sprites = Objects.requireNonNull(ParticleEntrySamples.sprites);
        ParticleEntry<SimpleParticleType, SimpleParticleType> provider = Objects.requireNonNull(ParticleEntrySamples.provider);
        ParticleEngine engine = client.minecraft().particleEngine;
        engine.createParticle(sprites.value(), 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        engine.createParticle(provider.value(), 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        Assertions.assertThat(ParticleEntrySamples.CREATED).as("the types the providers created particles of").containsExactly(sprites.value(), provider.value());
    }
    
    private static ParticleEntry<SimpleParticleType, SimpleParticleType> plain() {
        return Objects.requireNonNull(ParticleEntrySamples.plain);
    }
    
    private static ParticleEntry<SimpleParticleType, SimpleParticleType> unlimited() {
        return Objects.requireNonNull(ParticleEntrySamples.unlimited);
    }
    
    private static void spawnNearAndFar(GameTestHelper helper, ParticleEntry.Visibility visibility, List<Packet<?>> near, List<Packet<?>> far) {
        ParticleEntry<SimpleParticleType, SimpleParticleType> plain = ParticleEntrySamples.plain();
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        List<ServerPlayer> players = List.of(
                ParticleEntrySamples.createPlayer(helper, pos.add(20.0D, 0.0D, 0.0D), near),
                ParticleEntrySamples.createPlayer(helper, pos.add(100.0D, 0.0D, 0.0D), far)
        );
        
        level.players().addAll(players);
        try {
            plain.spawn(level, plain.value(), visibility, pos, 3, Vec3.ZERO, 0.0D);
        } finally {
            level.players().removeAll(players);
        }
    }
    
    private static ClientboundLevelParticlesPacket particles(GameTestHelper helper, List<Packet<?>> sent, String missing) {
        return sent.stream()
                .filter(ClientboundLevelParticlesPacket.class::isInstance)
                .map(ClientboundLevelParticlesPacket.class::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException(missing));
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
    
    private static final class SampleClient {
        
        private static ParticleProvider<SimpleParticleType> provider() {
            return (options, _, _, _, _, _, _, _, _) -> {
                ParticleEntrySamples.CREATED.add(options.getType());
                return null;
            };
        }
        
        private static ParticleProvider<SimpleParticleType> spriteProvider(SpriteSet sprites) {
            return SampleClient.provider();
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            ParticleEntrySamples.plain = registrations.particle("particle_entry_samples_plain").register();
            ParticleEntrySamples.unlimited = registrations.particle("particle_entry_samples_unlimited", () -> new SimpleParticleType(true)).register();
            ParticleEntrySamples.sprites = registrations.particle("particle_entry_samples_sprites")
                    .sprites(() -> SampleClient::spriteProvider)
                    .register();
            
            ParticleEntrySamples.provider = registrations.particle("particle_entry_samples_provider")
                    .provider(() -> SampleClient::provider)
                    .client(() -> (_, _) -> ParticleEntrySamples.clientCodeRan = true)
                    .register();
        }
    }
}
