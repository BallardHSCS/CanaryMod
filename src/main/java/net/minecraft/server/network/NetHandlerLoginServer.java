package net.minecraft.server.network;

import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionServiceModified;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.login.INetHandlerLoginServer;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.login.server.S00PacketDisconnect;
import net.minecraft.network.login.server.S01PacketEncryptionRequest;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.login.server.S03PacketEnableCompression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class NetHandlerLoginServer implements INetHandlerLoginServer, IUpdatePlayerListBox {
    private static final AtomicInteger b = new AtomicInteger(0);
    private static final Logger c = LogManager.getLogger();
    private static final Random d = new Random();
    private final byte[] e = new byte[4];
    private final MinecraftServer f;
    public final NetworkManager a;
    private NetHandlerLoginServer.LoginState g;
    private int h;
    private GameProfile i;
    private String j;
    private SecretKey k;

    public NetHandlerLoginServer(MinecraftServer minecraftserver, NetworkManager networkmanager) {
        this.g = NetHandlerLoginServer.LoginState.HELLO;
        this.j = "";
        this.f = minecraftserver;
        this.a = networkmanager;
        d.nextBytes(this.e);
    }

    public void c() {
        if (this.g == NetHandlerLoginServer.LoginState.READY_TO_ACCEPT) {
            this.b();
        }

        if (this.h++ == 600) {
            this.a("Took too long to log in");
        }
    }

    public void a(String s0) {
        try {
            c.info("Disconnecting " + this.d() + ": " + s0);
            ChatComponentText chatcomponenttext = new ChatComponentText(s0);

            this.a.a((Packet)(new S00PacketDisconnect(chatcomponenttext)));
            this.a.a((IChatComponent)chatcomponenttext);
        }
        catch (Exception exception) {
            c.error("Error whilst disconnecting player", exception);
        }
    }

    public void b() {
        if (!this.i.isComplete()) {
            this.i = this.a(this.i);
        }

        String s0 = this.f.an().a(this.a.b(), this.i);

        if (s0 != null) {
            this.a(s0);
        }
        else {
            this.g = NetHandlerLoginServer.LoginState.ACCEPTED;
            if (this.f.aI() >= 0 && !this.a.c()) {
                this.a.a(new S03PacketEnableCompression(this.f.aI()), new ChannelFutureListener() {

                             public void operationComplete(ChannelFuture p_operationComplete_1_) {
                                 NetHandlerLoginServer.this.a.a(NetHandlerLoginServer.this.f.aI());
                             }
                         }, new GenericFutureListener[0]
                        );
            }
            this.a.a((Packet)(new S02PacketLoginSuccess(this.i)));
            this.f.an().a(this.a, this.f.an().f(this.i));
        }
    }

    public void a(IChatComponent ichatcomponent) {
        c.info(this.d() + " lost connection: " + ichatcomponent.c());
    }

    public String d() {
        return this.i != null ? this.i.toString() + " (" + this.a.b().toString() + ")" : String.valueOf(this.a.b());
    }

    public void a(C00PacketLoginStart c00packetloginstart) {
        Validate.validState(this.g == NetHandlerLoginServer.LoginState.HELLO, "Unexpected hello packet", new Object[0]);
        this.i = c00packetloginstart.a();
        if (this.f.ae() && !this.a.c()) {
            this.g = NetHandlerLoginServer.LoginState.KEY;
            this.a.a((Packet)(new S01PacketEncryptionRequest(this.j, this.f.P().getPublic(), this.e)));
        }
        else {
            this.g = NetHandlerLoginServer.LoginState.READY_TO_ACCEPT;
        }
    }

    public void a(C01PacketEncryptionResponse c01packetencryptionresponse) {
        Validate.validState(this.g == NetHandlerLoginServer.LoginState.KEY, "Unexpected key packet", new Object[0]);
        PrivateKey s0 = this.f.P().getPrivate();

        if (!Arrays.equals(this.e, c01packetencryptionresponse.b(s0))) {
            throw new IllegalStateException("Invalid nonce!");
        }
        else {

            this.k = c01packetencryptionresponse.a(s0);
            this.g = NetHandlerLoginServer.LoginState.AUTHENTICATING;
            this.a.a(this.k);
            (new Thread("User Authenticator #" + b.incrementAndGet()) {
                public void run() {
                    GameProfile gameprofile = NetHandlerLoginServer.this.i;
                    try {
                        String s0 = (new BigInteger(CryptManager.a(NetHandlerLoginServer.this.j, NetHandlerLoginServer.this.f.P().getPublic(), NetHandlerLoginServer.this.k))).toString(16);
                        // CanaryMod: Pull user ip and add it to the session checks
                        String userIP = ((InetSocketAddress) NetHandlerLoginServer.this.a.b()).getAddress().getHostAddress();
                        // CanaryMod: Pass IP for possible session IP verification
                        NetHandlerLoginServer.this.i = ((YggdrasilMinecraftSessionServiceModified)NetHandlerLoginServer.this.f.aB()).hasJoinedServerVerifyIP(new GameProfile((UUID)null, gameprofile.getName()), s0, userIP);
                        //
                        if (NetHandlerLoginServer.this.i != null) {
                            NetHandlerLoginServer.c.info("UUID of player " + NetHandlerLoginServer.this.i.getName() + " is " + NetHandlerLoginServer.this.i.getId());
                            NetHandlerLoginServer.this.g = NetHandlerLoginServer.LoginState.READY_TO_ACCEPT;
                        }
                        else if (NetHandlerLoginServer.this.f.S()) {
                            NetHandlerLoginServer.c.warn("Failed to verify username but will let them in anyway!");
                            NetHandlerLoginServer.this.i = NetHandlerLoginServer.this.a(gameprofile);
                            NetHandlerLoginServer.this.g = NetHandlerLoginServer.LoginState.READY_TO_ACCEPT;
                        }
                        else {
                            NetHandlerLoginServer.this.a("Failed to verify username!");
                            NetHandlerLoginServer.c.error("Username \'" + /* NetHandlerLoginServer.this.i.getName() CanaryMod: Would this not be null?*/ gameprofile.getName() + "\' tried to join with an invalid session");
                        }
                    }
                    catch (AuthenticationUnavailableException authenticationunavailableexception) {
                        if (NetHandlerLoginServer.this.f.S()) {
                            NetHandlerLoginServer.c.warn("Authentication servers are down but will let them in anyway!");
                            NetHandlerLoginServer.this.i = NetHandlerLoginServer.this.a(gameprofile);
                            NetHandlerLoginServer.this.g = NetHandlerLoginServer.LoginState.READY_TO_ACCEPT;
                        }
                        else {
                            NetHandlerLoginServer.this.a("Authentication servers are down. Please try again later, sorry!");
                            NetHandlerLoginServer.c.error("Couldn\'t verify username because servers are unavailable");
                        }
                    }
                }
            }
            ).start();
        }
    }

    protected GameProfile a(GameProfile gameprofile) {
        UUID uuid;
        //CanaryMod Start - Bungeecord support!
        if (a.spoofedUUID != null) {
            uuid = a.spoofedUUID;
        }
        else {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + gameprofile.getName()).getBytes(Charsets.UTF_8));
        }
        gameprofile = new GameProfile(uuid, gameprofile.getName());
        if (a.spoofedProfile != null) {
            for (Property property : a.spoofedProfile) {
                this.i.getProperties().put(property.getName(), property);
            }
        }
        return gameprofile;
        //CanaryMod End - Bungeecord support!
    }

    static enum LoginState {
        HELLO("HELLO", 0),
        KEY("KEY", 1),
        AUTHENTICATING("AUTHENTICATING", 2),
        READY_TO_ACCEPT("READY_TO_ACCEPT", 3),
        ACCEPTED("ACCEPTED", 4);
        private static final LoginState[] $VALUES = { HELLO, KEY, AUTHENTICATING, READY_TO_ACCEPT, ACCEPTED };

        private LoginState(String p_i45297_1_, int p_i45297_2_) {
        }
    }
}