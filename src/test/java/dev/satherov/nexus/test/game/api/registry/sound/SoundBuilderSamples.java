package dev.satherov.nexus.test.game.api.registry.sound;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.sound.SoundEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks the sounds that sound entries play for the players in range.
///
public class SoundBuilderSamples {
    
    private static @Nullable SoundEntry plain;
    private static @Nullable SoundEntry ranged;
    
    @ServerTest
    public static void playsAtTheCenterOfTheBlockForANearbyPlayer(GameTestHelper helper) {
        SoundEntry plain = SoundBuilderSamples.plain();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        List<Packet<?>> sent = SoundBuilderSamples.record(helper, pos.getCenter().add(2.0D, 0.0D, 0.0D), () -> plain.play(helper.getLevel(), pos, SoundSource.BLOCKS, 0.5F, 2.0F));
        ClientboundSoundPacket played = SoundBuilderSamples.find(helper, sent, ClientboundSoundPacket.class);
        helper.assertValueEqual(played.getSound().value(), plain.value(), "played sound");
        helper.assertValueEqual(played.getSource(), SoundSource.BLOCKS, "source of the sound");
        helper.assertValueEqual(played.getY(), pos.getCenter().y(), "height of the sound");
        helper.assertTrue(new Vec3(played.getX(), played.getY(), played.getZ()).distanceTo(pos.getCenter()) < 1.0D, "The sound was played away from the block");
        helper.assertValueEqual(played.getVolume(), 0.5F, "volume of the sound");
        helper.assertValueEqual(played.getPitch(), 2.0F, "pitch of the sound");
        helper.succeed();
    }
    
    @ServerTest
    public static void playsWithVolumeAndPitchOneByDefault(GameTestHelper helper) {
        SoundEntry plain = SoundBuilderSamples.plain();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        List<Packet<?>> sent = SoundBuilderSamples.record(helper, pos.getCenter().add(2.0D, 0.0D, 0.0D), () -> plain.play(helper.getLevel(), pos, SoundSource.AMBIENT));
        ClientboundSoundPacket played = SoundBuilderSamples.find(helper, sent, ClientboundSoundPacket.class);
        helper.assertValueEqual(played.getSound().value(), plain.value(), "played sound");
        helper.assertValueEqual(played.getSource(), SoundSource.AMBIENT, "source of the sound");
        helper.assertValueEqual(played.getVolume(), 1.0F, "volume of the sound");
        helper.assertValueEqual(played.getPitch(), 1.0F, "pitch of the sound");
        helper.succeed();
    }
    
    @ServerTest
    public static void playsAtTheEntityForANearbyPlayer(GameTestHelper helper) {
        SoundEntry plain = SoundBuilderSamples.plain();
        Entity entity = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        List<Packet<?>> sent = SoundBuilderSamples.record(helper, entity.position().add(2.0D, 0.0D, 0.0D), () -> plain.play(entity, SoundSource.NEUTRAL, 0.5F, 2.0F));
        ClientboundSoundEntityPacket played = SoundBuilderSamples.find(helper, sent, ClientboundSoundEntityPacket.class);
        helper.assertValueEqual(played.getSound().value(), plain.value(), "played sound");
        helper.assertValueEqual(played.getSource(), SoundSource.NEUTRAL, "source of the sound");
        helper.assertValueEqual(played.getId(), entity.getId(), "entity the sound follows");
        helper.assertValueEqual(played.getVolume(), 0.5F, "volume of the sound");
        helper.assertValueEqual(played.getPitch(), 2.0F, "pitch of the sound");
        helper.succeed();
    }
    
    @ServerTest
    public static void reachesNoPlayerBeyondAFixedRange(GameTestHelper helper) {
        SoundEntry plain = SoundBuilderSamples.plain();
        SoundEntry ranged = SoundBuilderSamples.ranged();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        List<Packet<?>> sent = SoundBuilderSamples.record(helper, pos.getCenter().add(8.0D, 0.0D, 0.0D), () -> {
            ranged.play(helper.getLevel(), pos, SoundSource.BLOCKS);
            plain.play(helper.getLevel(), pos, SoundSource.BLOCKS);
        });
        
        List<SoundEvent> heard = sent.stream()
                .filter(ClientboundSoundPacket.class::isInstance)
                .map(packet -> ((ClientboundSoundPacket) packet).getSound().value())
                .toList();
        helper.assertValueEqual(heard, List.of(plain.value()), "sounds that reached the player");
        helper.succeed();
    }
    
    private static List<Packet<?>> record(GameTestHelper helper, Vec3 position, Runnable play) {
        ServerLevel level = helper.getLevel();
        PlayerList players = level.getServer().getPlayerList();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "sound_samples"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        players.placeNewPlayer(connection, player, cookie);
        player.setPos(position);
        
        List<Packet<?>> sent = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            
            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
                sent.add(packet);
            }
        };
        try {
            play.run();
        } finally {
            players.remove(player);
        }
        
        return sent;
    }
    
    private static <PACKET extends Packet<?>> PACKET find(GameTestHelper helper, List<Packet<?>> sent, Class<PACKET> type) {
        return sent.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("The player wasn't sent a '" + type.getSimpleName() + "'"));
    }
    
    private static SoundEntry plain() {
        return Objects.requireNonNull(SoundBuilderSamples.plain);
    }
    
    private static SoundEntry ranged() {
        return Objects.requireNonNull(SoundBuilderSamples.ranged);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            SoundBuilderSamples.plain = registrations.sound("sound_builder_samples_plain", "Plain Sample Sound").register();
            SoundBuilderSamples.ranged = registrations.sound("sound_builder_samples_ranged", "Ranged Sample Sound").range(4.0F).register();
        }
    }
}
