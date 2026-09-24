package dev.satherov.nexus.internal.dev;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

///
/// Ops the players that a dev run lists in the `nexus.dev.ops` system property when they log in.
///
/// Does nothing if the property is absent.
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public class DevOperators {
    
    ///
    /// The system property holding the names, comma separated.
    ///
    public static final String PROPERTY = "nexus.dev.ops";
    
    ///
    /// Ops the player if the property lists them and they are not an operator yet.
    ///
    /// @param event The login event.
    ///
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        String names = System.getProperty(DevOperators.PROPERTY);
        if (names == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        NameAndId profile = player.nameAndId();
        boolean listed = Arrays.stream(names.split(","))
                .map(String::trim)
                .anyMatch(profile.name()::equalsIgnoreCase);
        
        PlayerList players = player.level().getServer().getPlayerList();
        if (!listed || players.isOp(profile)) {
            return;
        }
        
        players.op(profile);
        DevOperators.log.info("Opped dev player '{}'", profile.name());
    }
}
