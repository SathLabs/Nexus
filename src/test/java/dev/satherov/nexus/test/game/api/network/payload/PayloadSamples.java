package dev.satherov.nexus.test.game.api.network.payload;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.network.Channel;
import dev.satherov.nexus.api.network.Network;
import dev.satherov.nexus.api.network.ClientRecipients;
import dev.satherov.nexus.api.network.context.HandlerContext;
import dev.satherov.nexus.api.network.context.ServerHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.api.network.payload.Payload;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

///
/// The client tests that check payloads reach the side they are sent to, in play and in configuration.
///
public class PayloadSamples {
    
    private static final double RADIUS = 8.0D;
    private static final String ASKED = "asked during configuration";
    private static final Set<String> RECEIVED = ConcurrentHashMap.newKeySet();
    private static final Map<String, ServerPlayer> SENDERS_BY_VALUE = new ConcurrentHashMap<>();
    private static final Set<String> ANSWERS = ConcurrentHashMap.newKeySet();
    
    private static @Nullable ClientboundPayload<String> clientbound;
    private static @Nullable ServerboundPayload<String> serverbound;
    private static @Nullable Payload<String> bidirectional;
    private static @Nullable Payload<String> optional;
    private static @Nullable ClientboundPayload<String> question;
    private static @Nullable ServerboundPayload<String> answer;
    private static @Nullable Throwable playerFailure;
    
    @ClientTest
    public static void sendToServerReachesTheServerAndTheReplyTheClient(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        String value = "serverbound";
        Objects.requireNonNull(PayloadSamples.serverbound).sendToServer(value);
        client.until("the reply to reach the client", () -> PayloadSamples.RECEIVED.contains(value));
        Assertions.assertThat(PayloadSamples.SENDERS_BY_VALUE).containsEntry(value, player);
        client.leaveWorld();
    }
    
    @ClientTest
    public static void bidirectionalReachesBothSides(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        Payload<String> bidirectional = Objects.requireNonNull(PayloadSamples.bidirectional);
        String toServer = "bidirectional to the server";
        String toClient = "bidirectional to the client";
        bidirectional.sendToServer(toServer);
        player.level().getServer().execute(() -> bidirectional.send(toClient, ClientRecipients.player(player)));
        client.until("the value to reach the server", () -> PayloadSamples.SENDERS_BY_VALUE.containsKey(toServer));
        client.until("the value to reach the client", () -> PayloadSamples.RECEIVED.contains(toClient));
        Assertions.assertThat(PayloadSamples.SENDERS_BY_VALUE).containsEntry(toServer, player);
        client.leaveWorld();
    }
    
    @ClientTest
    public static void optionalChannelCanSendBothWays(Client client) {
        client.joinWorld();
        Channel channel = Objects.requireNonNull(PayloadSamples.optional).getChannel();
        Assertions.assertThat(channel.isOptional()).isTrue();
        Assertions.assertThat(channel.canSend(PayloadSamples.getPlayer(client))).isTrue();
        Assertions.assertThat(channel.canSendToServer()).isTrue();
        client.leaveWorld();
    }
    
    @ClientTest
    public static void clientboundPayloadRefusesSendToServer(Client client) {
        client.joinWorld();
        Payload<String> clientbound = (Payload<String>) Objects.requireNonNull(PayloadSamples.clientbound);
        Assertions.assertThatIllegalStateException().isThrownBy(() -> clientbound.sendToServer("clientbound to the server"));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void configurationHandlerAnswersDuringTheJoinWithoutAPlayer(Client client) {
        PayloadSamples.playerFailure = null;
        PayloadSamples.ANSWERS.clear();
        client.joinWorld();
        Assertions.assertThat(PayloadSamples.playerFailure).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(PayloadSamples.ANSWERS).containsExactly(PayloadSamples.ASKED);
        client.leaveWorld();
    }
    
    @ClientTest
    public static void playerReachesTheClient(Client client) {
        client.joinWorld();
        PayloadSamples.assertReaches(client, "player", ClientRecipients.player(PayloadSamples.getPlayer(client)));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void playersReachesTheClient(Client client) {
        client.joinWorld();
        PayloadSamples.assertReaches(client, "players", ClientRecipients.players(List.of(PayloadSamples.getPlayer(client))));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void allReachesTheClient(Client client) {
        client.joinWorld();
        PayloadSamples.assertReaches(client, "all", ClientRecipients.all(client.serverLevel().getServer()));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void dimensionReachesTheClient(Client client) {
        client.joinWorld();
        PayloadSamples.assertReaches(client, "dimension", ClientRecipients.dimension(client.serverLevel()));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void nearReachesTheClientInsideTheRadius(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        Vec3 center = player.position().add(PayloadSamples.RADIUS / 2.0D, 0.0D, 0.0D);
        PayloadSamples.assertReaches(client, "near inside the radius", ClientRecipients.near(player.level(), center, PayloadSamples.RADIUS));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void nearSkipsTheClientOutsideTheRadius(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        Vec3 center = player.position().add(PayloadSamples.RADIUS * 2.0D, 0.0D, 0.0D);
        PayloadSamples.assertSkips(client, "near outside the radius", ClientRecipients.near(player.level(), center, PayloadSamples.RADIUS));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void trackingChunkReachesTheClient(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        PayloadSamples.assertReaches(client, "tracking chunk", ClientRecipients.trackingChunk(player.level(), player.chunkPosition()));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void trackingPositionReachesTheClient(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        BlockPos middle = player.chunkPosition().getMiddleBlockPosition(player.getBlockY());
        PayloadSamples.assertReaches(client, "tracking position", ClientRecipients.trackingPosition(player.level(), middle));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void trackingEntityReachesTheClientTrackingIt(Client client) {
        client.joinWorld();
        ServerPlayer player = PayloadSamples.getPlayer(client);
        ServerLevel level = player.level();
        ArmorStand stand = new ArmorStand(level, player.getX() + 2.0D, player.getY(), player.getZ());
        stand.setNoGravity(true);
        level.getServer().execute(() -> level.addFreshEntity(stand));
        client.until("the client to see the armor stand", () -> Objects.requireNonNull(client.minecraft().level).getEntity(stand.getId()) != null);
        PayloadSamples.assertReaches(client, "tracking entity", ClientRecipients.trackingEntity(stand));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void trackingEntitySkipsThePlayerItself(Client client) {
        client.joinWorld();
        PayloadSamples.assertSkips(client, "tracking the player", ClientRecipients.trackingEntity(PayloadSamples.getPlayer(client)));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void trackingEntityAndSelfReachesThePlayerItself(Client client) {
        client.joinWorld();
        PayloadSamples.assertReaches(client, "tracking the player and itself", ClientRecipients.trackingEntityAndSelf(PayloadSamples.getPlayer(client)));
        client.leaveWorld();
    }
    
    private static void assertReaches(Client client, String value, ClientRecipients recipients) {
        ClientboundPayload<String> clientbound = Objects.requireNonNull(PayloadSamples.clientbound);
        client.serverLevel().getServer().execute(() -> clientbound.send(value, recipients));
        client.until("'" + value + "' to reach the client", () -> PayloadSamples.RECEIVED.contains(value));
    }
    
    private static void assertSkips(Client client, String value, ClientRecipients recipients) {
        ClientboundPayload<String> clientbound = Objects.requireNonNull(PayloadSamples.clientbound);
        ServerPlayer player = PayloadSamples.getPlayer(client);
        String next = "after " + value;
        client.serverLevel().getServer().execute(() -> {
            clientbound.send(value, recipients);
            clientbound.send(next, ClientRecipients.player(player));
        });
        client.until("'" + next + "' to reach the client", () -> PayloadSamples.RECEIVED.contains(next));
        Assertions.assertThat(PayloadSamples.RECEIVED).doesNotContain(value);
    }
    
    private static ServerPlayer getPlayer(Client client) {
        return client.serverLevel().players().getFirst();
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Network network = Nexus.getMod().getNetwork();
            PayloadSamples.clientbound = network.play("payload_samples_clientbound", ByteBufCodecs.STRING_UTF8).toClient(() -> PayloadSamples.Registered::onClientValue);
            PayloadSamples.serverbound = network.play("payload_samples_serverbound", ByteBufCodecs.STRING_UTF8).toServer(PayloadSamples.Registered::reply);
            PayloadSamples.bidirectional = network.play("payload_samples_bidirectional", ByteBufCodecs.STRING_UTF8).bidirectional(
                    () -> PayloadSamples.Registered::onClientValue,
                    PayloadSamples.Registered::record
            );
            PayloadSamples.optional = network.channel("payload_samples")
                    .optional()
                    .play("optional", ByteBufCodecs.STRING_UTF8)
                    .bidirectional(() -> PayloadSamples.Registered::onClientValue, PayloadSamples.Registered::record);
            PayloadSamples.question = network.configuration("payload_samples_question", ByteBufCodecs.STRING_UTF8).toClient(() -> PayloadSamples.Registered::onQuestion);
            PayloadSamples.answer = network.configuration("payload_samples_answer", ByteBufCodecs.STRING_UTF8).toServer(PayloadSamples.Registered::onAnswer);
        }
        
        @SubscribeEvent
        public static void onRegisterConfigurationTasks(RegisterConfigurationTasksEvent event) {
            event.register(new PayloadSamples.QuestionTask(event.getListener()));
        }
        
        private static void onClientValue(String value, HandlerContext context) {
            PayloadSamples.RECEIVED.add(value);
        }
        
        private static void record(String value, ServerHandlerContext context) {
            PayloadSamples.SENDERS_BY_VALUE.put(value, context.getPlayer());
        }
        
        private static void reply(String value, ServerHandlerContext context) {
            PayloadSamples.Registered.record(value, context);
            Objects.requireNonNull(PayloadSamples.clientbound).send(value, ClientRecipients.sender(context));
        }
        
        private static void onQuestion(String value, HandlerContext context) {
            PayloadSamples.playerFailure = Assertions.catchThrowable(context::getPlayer);
            Objects.requireNonNull(PayloadSamples.answer).reply(value, context);
        }
        
        private static void onAnswer(String value, ServerHandlerContext context) {
            PayloadSamples.ANSWERS.add(value);
            context.finishCurrentTask(PayloadSamples.QuestionTask.TYPE);
        }
    }
    
    private record QuestionTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        
        private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Nexus.getMod().id("payload_samples"));
        
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            Objects.requireNonNull(PayloadSamples.question).send(PayloadSamples.ASKED, ClientRecipients.listener(this.listener));
        }
        
        @Override
        public ConfigurationTask.Type type() {
            return PayloadSamples.QuestionTask.TYPE;
        }
    }
}
