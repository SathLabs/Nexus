package dev.satherov.nexus.test.game.api.registry.menu;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.menu.MenuEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks the menu types that menu builders register, the title their menus open under, and the extra data they write.
///
public class MenuBuilderSamples {

    private static @Nullable MenuEntry<NumberMenu> number;

    @ServerTest
    public static void namesTheTitleAfterItsKey(GameTestHelper helper) {
        MenuEntry<NumberMenu> number = Objects.requireNonNull(MenuBuilderSamples.number);
        helper.assertValueEqual(number.getTranslation().getKey(), "container.nexus.menu_builder_samples_number", "translation key");
        helper.assertValueEqual(number.getTranslation().getEnglish(), "Number Sample", "English title");
        helper.succeed();
    }

    @ServerTest
    public static void opensTheMenuUnderItsTitle(GameTestHelper helper) {
        MenuEntry<NumberMenu> number = Objects.requireNonNull(MenuBuilderSamples.number);
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = MenuBuilderSamples.createPlayer(helper, sent);
        number.open(player, (id, _, _) -> new NumberMenu(number.get(), id, 0));
        helper.assertValueEqual(player.containerMenu.getType(), number.get(), "type of the open menu");

        ClientboundOpenScreenPacket opened = sent.stream()
                .filter(ClientboundOpenScreenPacket.class::isInstance)
                .map(ClientboundOpenScreenPacket.class::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("The player wasn't sent a screen to open"));

        helper.assertValueEqual(opened.getType(), number.get(), "type of the opened screen");
        helper.assertValueEqual(opened.getTitle(), number.component(), "title");
        helper.succeed();
    }

    @ServerTest
    public static void writesTheExtraDataTheFactoryReads(GameTestHelper helper) {
        MenuEntry<NumberMenu> number = Objects.requireNonNull(MenuBuilderSamples.number);
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = MenuBuilderSamples.createPlayer(helper, sent);
        number.open(player, (id, _, _) -> new NumberMenu(number.get(), id, 0), buffer -> buffer.writeVarInt(42));
        helper.assertValueEqual(player.containerMenu.getType(), number.get(), "type of the open menu");

        AdvancedOpenScreenPayload opened = sent.stream()
                .filter(ClientboundCustomPayloadPacket.class::isInstance)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(AdvancedOpenScreenPayload.class::isInstance)
                .map(AdvancedOpenScreenPayload.class::cast)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("The player wasn't sent a screen to open with extra data"));

        helper.assertValueEqual(opened.menuType(), number.get(), "type of the opened screen");
        helper.assertValueEqual(opened.name(), number.component(), "title");
        RegistryFriendlyByteBuf extra = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(opened.additionalData()), helper.getLevel().registryAccess());
        helper.assertValueEqual(number.get().create(opened.windowId(), player.getInventory(), extra).getNumber(), 42, "number the factory read");
        helper.succeed();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, List<Packet<?>> sent) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "menu_builder_samples"), ClientInformation.createDefault());
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {

            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
                sent.add(packet);
            }
        };
        return player;
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            MenuBuilderSamples.number = registrations
                    .menu("menu_builder_samples_number", "Number Sample", (id, _, buffer) -> new NumberMenu(Objects.requireNonNull(MenuBuilderSamples.number).get(), id, buffer.readVarInt()))
                    .register();
        }
    }
}
