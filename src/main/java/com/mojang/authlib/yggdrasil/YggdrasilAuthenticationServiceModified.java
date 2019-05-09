package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.minecraft.MinecraftSessionService;

import java.net.Proxy;

/**
 * Modified YggdrasilAuthenticationService for disabling mismatched session ip
 */
public class YggdrasilAuthenticationServiceModified extends YggdrasilAuthenticationService {

    public YggdrasilAuthenticationServiceModified(Proxy proxy, String clientToken) {
        super(proxy, clientToken);
    }

    //CanaryMod: Create the modified SessionService for Session IP verification
    public MinecraftSessionService createModifiedMinecraftSessionService() {
        return new YggdrasilMinecraftSessionServiceModified(this);
    }
}
