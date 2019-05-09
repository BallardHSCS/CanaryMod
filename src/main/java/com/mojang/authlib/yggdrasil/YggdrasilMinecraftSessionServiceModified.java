package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse;

import java.net.URL;
import java.util.HashMap;

/**
 * Modified SessionService query class for disabling mismatched session ip
 */
public class YggdrasilMinecraftSessionServiceModified extends YggdrasilMinecraftSessionService {
    private static final URL CHECK_URL = HttpAuthenticationService.constantURL("https://sessionserver.mojang.com/session/minecraft/hasJoined");

    protected YggdrasilMinecraftSessionServiceModified(YggdrasilAuthenticationServiceModified authenticationService) {
        super(authenticationService);
    }

    public GameProfile hasJoinedServerVerifyIP(GameProfile user, String serverId, String userIP) throws AuthenticationUnavailableException {
        HashMap<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("username", user.getName());
        arguments.put("serverId", serverId);
        // CanaryMod: Add IP check to session verification if enabled
        if (net.canarymod.config.Configuration.getServerConfig().verifyUserSessionIP()) {
            arguments.put("ip", userIP);
        }

        URL url = HttpAuthenticationService.concatenateURL(CHECK_URL, HttpAuthenticationService.buildQuery(arguments));

        try {
            HasJoinedMinecraftServerResponse e = this.getAuthenticationService().makeRequest(url, null, HasJoinedMinecraftServerResponse.class);
            if (e != null && e.getId() != null) {
                GameProfile result = new GameProfile(e.getId(), user.getName());
                if (e.getProperties() != null) {
                    result.getProperties().putAll(e.getProperties());
                }

                return result;
            }
            else {
                return null;
            }
        } catch (AuthenticationUnavailableException authenticationunavailableexception) {
            throw authenticationunavailableexception;
        } catch (AuthenticationException authenticationexception) {
            return null;
        }
    }
}
