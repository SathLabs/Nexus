package dev.satherov.nexus.test.game.internal.dev;

import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.dev.DevOperators;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

///
/// Checks that a dev run ops the players it lists for as long as they are logged in.
///
public class DevOperatorsSamples {
    
    private static final String LISTED = "DevOpSample";
    private static final String UNLISTED = "NotADevOpSample";
    
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
    }
    
    private static void logIn(ServerPlayer player) {
        String previous = System.getProperty(DevOperators.PROPERTY);
        System.setProperty(DevOperators.PROPERTY, "Dev," + DevOperatorsSamples.LISTED);
        try {
            DevOperators.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
        } finally {
            if (previous == null) {
                System.clearProperty(DevOperators.PROPERTY);
            } else {
                System.setProperty(DevOperators.PROPERTY, previous);
            }
        }
    }
    
    private static void logOut(ServerPlayer player) {
        DevOperators.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
    }
    
    @ServerTest
    public static void opsAListedPlayerUntilTheyLogOut(GameTestHelper helper) {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        ServerPlayer player = DevOperatorsSamples.player(helper, DevOperatorsSamples.LISTED);
        DevOperatorsSamples.logIn(player);
        helper.assertTrue(players.isOp(player.nameAndId()), "The listed player was not opped on login");
        
        DevOperatorsSamples.logOut(player);
        helper.assertFalse(players.isOp(player.nameAndId()), "The listed player was still an operator after logging out");
        helper.succeed();
    }
    
    @ServerTest
    public static void leavesAnUnlistedPlayerAlone(GameTestHelper helper) {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        ServerPlayer player = DevOperatorsSamples.player(helper, DevOperatorsSamples.UNLISTED);
        DevOperatorsSamples.logIn(player);
        helper.assertFalse(players.isOp(player.nameAndId()), "The unlisted player was opped on login");
        helper.succeed();
    }
    
    @ServerTest
    public static void keepsAnOperatorOppedBeforeLogin(GameTestHelper helper) {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        ServerPlayer player = DevOperatorsSamples.player(helper, DevOperatorsSamples.LISTED);
        NameAndId profile = player.nameAndId();
        players.op(profile);
        DevOperatorsSamples.logIn(player);
        DevOperatorsSamples.logOut(player);
        helper.assertTrue(players.isOp(profile), "The operator opped before login was deopped on logout");
        
        players.deop(profile);
        helper.succeed();
    }
    
    @ServerTest
    public static void deopsAPlayerLeftOppedByAnUncleanStopOnTheNextStart(GameTestHelper helper) {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        ServerPlayer player = DevOperatorsSamples.player(helper, DevOperatorsSamples.LISTED);
        DevOperatorsSamples.logIn(player);
        DevOperators.onServerStarting(new ServerStartingEvent(helper.getLevel().getServer()));
        helper.assertFalse(players.isOp(player.nameAndId()), "The player opped before the unclean stop was still an operator after the next start");
        
        DevOperatorsSamples.logOut(player);
        helper.succeed();
    }
}
