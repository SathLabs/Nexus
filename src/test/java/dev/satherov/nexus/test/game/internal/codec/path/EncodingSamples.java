package dev.satherov.nexus.test.game.internal.codec.path;

import lombok.SneakyThrows;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.internal.codec.path.Encoding;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;

///
/// The client tests that check the registries encoding uses on the client thread and drops once the world is left.
///
public class EncodingSamples {
    
    @ClientTest
    public static void holderOfTheClientLevelEncodesOnTheClientThread(Client client) {
        client.joinWorld();
        ClientLevel level = Objects.requireNonNull(client.minecraft().level);
        Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        HolderOwner<Enchantment> owner = Objects.requireNonNull(Encoding.owner(Registries.ENCHANTMENT));
        
        Assertions.assertThat(client.minecraft().isSameThread()).isTrue();
        Assertions.assertThat(owner).isSameAs(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
        Assertions.assertThat(sharpness.canSerializeIn(owner)).isTrue();
        Assertions.assertThat(Encoding.owner(Registries.ENCHANTMENT)).isSameAs(owner);
        Assertions.assertThat(Encoding.encode(NexusCodec.holder(Registries.ENCHANTMENT), sharpness)).isEqualTo(StringTag.valueOf("minecraft:sharpness"));
        client.leaveWorld();
    }
    
    @ClientTest
    public static void ownerOutsideAWorldIsTheBuiltInRegistry(Client client) {
        Assertions.assertThat(Encoding.owner(Registries.ITEM)).isSameAs(BuiltInRegistries.ITEM);
        Assertions.assertThat(Encoding.owner(Registries.ENCHANTMENT)).isNull();
    }
    
    @ClientTest
    public static void leavingTheWorldDropsTheRegistriesOfItsServer(Client client) {
        client.joinWorld();
        
        Assertions.assertThat(Encoding.owner(Registries.ENCHANTMENT)).isNotNull();
        Assertions.assertThat(EncodingSamples.lastRegistries()).isNotNull();
        client.leaveWorld();
        Assertions.assertThat(EncodingSamples.lastRegistries()).isNull();
    }
    
    @SneakyThrows
    private static @Nullable Object lastRegistries() {
        Field registries = Encoding.class.getDeclaredField("lastRegistries");
        registries.setAccessible(true);
        return registries.get(null);
    }
}
