package net.minecraft.server;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationServiceModified;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import net.canarymod.Canary;
import net.canarymod.Main;
import net.canarymod.api.CanaryConfigurationManager;
import net.canarymod.api.CanaryServer;
import net.canarymod.api.world.CanarySaveConverter;
import net.canarymod.api.world.CanaryWorld;
import net.canarymod.api.world.CanaryWorldManager;
import net.canarymod.api.world.DimensionType;
import net.canarymod.backbone.PermissionDataAccess;
import net.canarymod.config.Configuration;
import net.canarymod.config.WorldConfiguration;
import net.canarymod.database.Database;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.system.LoadWorldHook;
import net.canarymod.hook.system.ServerTickHook;
import net.canarymod.tasks.ServerTaskManager;
import net.canarymod.util.ShutdownLogger;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.network.NetworkSystem;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.profiler.IPlayerUsage;
import net.minecraft.profiler.PlayerUsageSnooper;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.storage.AnvilSaveConverter;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.demo.DemoWorldServer;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.visualillusionsent.utils.PropertiesFile;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public abstract class MinecraftServer implements ICommandSender, Runnable, IPlayerUsage {

    private static boolean notHeadless = !GraphicsEnvironment.isHeadless(); // CanaryMod
    private static final Logger j = LogManager.getLogger();
    public static final File a = new File("usercache.json");
    private static MinecraftServer k;
    private final ISaveFormat l;
    private final PlayerUsageSnooper m = new PlayerUsageSnooper("server", this, ax());
    private final File n;
    private final List o = Lists.newArrayList();
    private final ICommandManager p;
    public final Profiler b = new Profiler();
    private final NetworkSystem q;
    private final ServerStatusResponse r = new ServerStatusResponse();
    private final Random s = new Random();
    private String t;
    private int u = -1;
    // public WorldServer[] c; // XXX
    public ServerConfigurationManager v; // CanaryMod private -> public
    private boolean w = true;
    private boolean x;
    private int y;
    protected final Proxy d;
    public String e;
    public int f;
    private boolean z;
    private boolean A;
    private boolean B;
    private boolean C;
    private boolean D;
    private String E;
    private int F;
    private int G = 0;
    public final long[] g = new long[100];
    public long[][] h;
    private KeyPair H;
    private String I;
    private String J;
    private boolean L;
    private boolean M;
    private boolean N;
    private String O = "";
    private String P = "";
    private boolean Q;
    private long R;
    private String S;
    private boolean T;
    private boolean U;
    private final YggdrasilAuthenticationService V;
    private final MinecraftSessionService W;
    private long X = 0L;
    private final GameProfileRepository Y;
    private final PlayerProfileCache Z;
    protected final Queue i = Queues.newArrayDeque();
    private Thread aa;
    private long ab = ax();

    // CanaryMod start: Multiworld \o/
    public CanaryWorldManager worldManager = new CanaryWorldManager();
    private CanaryServer server;

    // CanaryMod start: Stop Message
    private String stopMsg;

    //

    public MinecraftServer(File file1, Proxy proxy, File file2) {
        this.d = proxy;
        k = this;
        this.n = file1;
        this.q = new NetworkSystem(this);
        this.Z = new PlayerProfileCache(this, file2);
        this.p = this.h();
        this.l = new AnvilSaveConverter(file1, DimensionType.NORMAL);
        // CanaryMod: Use Modified Session Service for IP verification checks (if enabled)
        this.V = new YggdrasilAuthenticationServiceModified(proxy, UUID.randomUUID().toString());
        this.W = ((YggdrasilAuthenticationServiceModified) this.V).createModifiedMinecraftSessionService();
        //
        this.Y = this.V.createProfileRepository();
        // CanaryMod
        this.server = new CanaryServer(this);
        Canary.setServer(server);
        //
    }

    protected ServerCommandManager h() {
        return new ServerCommandManager();
    }

    protected abstract boolean i() throws IOException;

    protected void a(String s0) {
        if (this.X().b(s0)) {
            j.info("Converting map!");
            this.b("menu.convertingLevel");
            this.X().a(s0, new IProgressUpdate() {

                           private long b = System.currentTimeMillis();

                           public void a(String s0) {
                           }

                           public void a(int s0) {
                               if (System.currentTimeMillis() - this.b >= 1000L) {
                                   this.b = System.currentTimeMillis();
                                   MinecraftServer.j.info("Converting... " + s0 + "%");
                               }
                           }

                           public void c(String s0) {
                           }
                       }
                      );
        }
    }

    protected synchronized void b(String s0) {
        this.S = s0;
    }

    // Used to initialize the "master" worlds
    protected void initWorld(String name, String also_name, long seed, WorldType nmsWt, net.canarymod.api.world.DimensionType dimType, String generatorSettings) {
        this.a(name); // Anvil Converter
        this.b("menu.loadingLevel");
        File worldFolder = new File("worlds/" + name);
        CanarySaveConverter converter = new CanarySaveConverter(worldFolder);

        if (converter.isVanillaFormat()) {
            Canary.log.info("World " + name + " is Vanilla. Will now attempt to convert.");
            converter.convert();
        }
        AnvilSaveHandler isavehandler = new AnvilSaveHandler(new File("worlds/"), name, true, dimType);
        WorldInfo worldinfo = isavehandler.d();
        WorldConfiguration config = Configuration.getWorldConfig(name + "_" + dimType.getName());

        WorldSettings worldsettings;
        WorldServer world;

        if (worldinfo == null) {
            worldsettings = new WorldSettings(seed, WorldSettings.GameType.a(config.getGameMode().getId()), config.generatesStructures(), false, nmsWt);
            if (config.getWorldSeed().isEmpty()) {
                // Ok to set the seed
                PropertiesFile worldRaw = config.getFile();
                worldRaw.setString("world-seed", String.valueOf(seed));
                worldRaw.save();
            }
            // CanaryMod those are flatworld settings, and they are likely unset
            if (generatorSettings != null) {
                worldsettings.a(generatorSettings);
            }
            //
            worldinfo = new WorldInfo(worldsettings, name, dimType);

            // initialize new perm file
            try {
                Database.get().updateSchema(new PermissionDataAccess(name + "_" + dimType.getName()));
            }
            catch (DatabaseWriteException e) {
                Canary.log.error("Failed to update database schema", e);
            }
        }
        else {
            worldinfo.a(name);
            worldsettings = new WorldSettings(worldinfo);
        }

        // CanaryMod: Force game type from config
        worldinfo.a(WorldSettings.GameType.a(config.getGameMode().getId()));
        // Set difficulty
        worldinfo.a(EnumDifficulty.a(config.getDifficulty().getId()));

        if (this.M) {
            worldsettings.a();
        }

        if (dimType.getId() == 0) {
            if (this.S()) {
                world = new DemoWorldServer(this, isavehandler, worldinfo, dimType.getId(), this.b);
            }
            else {
                world = (WorldServer)new WorldServer(this, isavehandler, worldinfo, dimType.getId(), this.b).b();
            }
            world.a(worldsettings);
        }
        else {
            world = (WorldServer)new WorldServerMulti(this, isavehandler, dimType.getId(), (WorldServer)((CanaryWorld)worldManager.getWorld(name, net.canarymod.api.world.DimensionType.NORMAL, true)).getHandle(), this.b, worldinfo).b();
            // CanaryMod: Spawnpoints for everybody!
            world.a(worldsettings);
        }

        world.a((IWorldAccess)(new WorldManager(this, world)));
        //if (!this.N()) { // CanaryMod: Always set game mode defaults
        world.P().a(WorldSettings.GameType.a(config.getGameMode().getId()));
        //}

        this.v.a(new WorldServer[]{ world }); // Init player data files
        this.k(world); // Generate terrain
        worldManager.addWorld(world.getCanaryWorld());
        new LoadWorldHook(world.getCanaryWorld()).call();
    }

    protected void k(WorldServer worldserver) { // CanaryMod: signature changed
        boolean flag0 = true;
        boolean flag1 = true;
        boolean flag2 = true;
        boolean flag3 = true;
        int i0 = 0;

        this.b("menu.generatingTerrain");
        byte b0 = 0;

        j.info("Preparing start region for level " + worldserver.getCanaryWorld().getFqName() + " ("+worldserver.P().getDimId()+")");
        BlockPos blockpos = worldserver.M();
        long i1 = ax();

        for (int i2 = -192; i2 <= 192 && this.t(); i2 += 16) {
            for (int i3 = -192; i3 <= 192 && this.t(); i3 += 16) {
                long i4 = ax();

                if (i4 - i1 > 1000L) {
                    this.a_("Preparing spawn area", i0 * 100 / 625);
                    i1 = i4;
                }

                ++i0;
                worldserver.b.c(blockpos.n() + i2 >> 4, blockpos.p() + i3 >> 4);
            }
        }

        this.q();
    }

    protected void a(String s0, ISaveHandler isavehandler) {
        File file1 = new File(isavehandler.b(), "resources.zip");

        if (file1.isFile()) {
            this.a_("level://" + s0 + "/" + file1.getName(), "");
        }
    }

    public abstract boolean l();

    public abstract WorldSettings.GameType m();

    public abstract EnumDifficulty n();

    public abstract boolean o();

    public abstract int p();

    protected void a_(String s0, int i0) {
        this.e = s0;
        this.f = i0;
        j.info(s0 + ": " + i0 + "%");
    }

    protected void q() {
        this.e = null;
        this.f = 0;
    }

    protected void a(boolean flag0) {
        a(flag0, j);
    }

    private void a(boolean flag0, Logger j) {
        if (!this.N) {
            // CanaryMod changed to use worldManager
            for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
                WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();

                if (worldserver != null) {
                    if (!flag0) {
                        j.info("Saving chunks for level \'" + worldserver.P().k() + "\'/" + worldserver.t.k());
                    }

                    try {
                        worldserver.a(true, (IProgressUpdate)null);
                    }
                    catch (MinecraftException minecraftexception) {
                        j.warn(minecraftexception.getMessage());
                    }
                }
                else {
                    j.warn("null world");
                }
            }
        }
    }

    public void r() {
        if (!this.N) {
            // CanaryMod start: If we're in the shutdown hook, we can't rely on log4j for logging.
            Logger log;
            if (Thread.currentThread().getName().equals("Server Shutdown Thread")) {
                log = new ShutdownLogger(MinecraftServer.class);
            }
            else {
                log = MinecraftServer.j;
            }

            log.info("Stopping server");
            if (this.ao() != null) {
                this.ao().b();
            }

            if (this.v != null) {
                log.info("Saving players");
                this.v.k();
                this.v.v(stopMsg);
            }

            log.info("Saving worlds");
            this.a(false, log);

            // CanaryMod Multiworld
            for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
                WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();

                worldserver.o();
            }

            if (this.m.d()) {
                this.m.e();
            }
            // CanaryMod disable plugins:
            Logger canaryLogger = log == j ? Canary.log : new ShutdownLogger("CanaryMod");
            canaryLogger.info("Disabling Plugins ...");
            Canary.pluginManager().disableAllPlugins(canaryLogger);
        }
    }

    public String s() {
        return this.t;
    }

    public void c(String s0) {
        this.t = s0;
    }

    public boolean t() {
        return this.w;
    }

    public void u() {
        this.w = false;
    }

    public void run() {
        try {
            if (this.i()) {
                this.ab = ax();
                long i0 = 0L;

                this.r.a((IChatComponent)(new ChatComponentText(this.E)));
                this.r.a(new ServerStatusResponse.MinecraftProtocolVersionIdentifier("1.8", 47));
                this.a(this.r);

                while (this.w) {
                    long i1 = ax();
                    long i2 = i1 - this.ab;

                    if (i2 > 2000L && this.ab - this.R >= 15000L) {
                        j.warn("Can\'t keep up! Did the system time change, or is the server overloaded? Running {}ms behind, skipping {} tick(s)", new Object[]{ Long.valueOf(i2), Long.valueOf(i2 / 50L) });
                        i2 = 2000L;
                        this.R = this.ab;
                    }

                    if (i2 < 0L) {
                        j.warn("Time ran backwards! Did the system time change?");
                        i2 = 0L;
                    }

                    i0 += i2;
                    this.ab = i1;
                    // CanaryMod start: multiworld sleeping
                    boolean allSleeping = true;

                    for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
                        allSleeping &= ((WorldServer)((CanaryWorld)w).getHandle()).f();
                    }
                    // CanaryMod end
                    if (allSleeping) {
                        this.y(); // Run tick
                        i0 = 0L;
                    }
                    else {
                        while (i0 > 50L) {
                            i0 -= 50L;
                            this.y();
                        }
                    }

                    Thread.sleep(Math.max(1L, 50L - i0));
                    this.Q = true;
                }
            }
            else {
                // this.a((CrashReport)null); // CanaryMod: Fix Server detonation hang
                while (Main.warningOpen()) {
                    Thread.sleep(50L);
                }
            }
        }
        catch (Throwable throwable) {
            j.error("Encountered an unexpected exception", throwable);
            CrashReport crashreport = null;

            if (throwable instanceof ReportedException) {
                crashreport = this.b(((ReportedException)throwable).a());
            }
            else {
                crashreport = this.b(new CrashReport("Exception in server tick loop", throwable));
            }

            File file1 = new File(new File(this.w(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");

            if (crashreport.a(file1)) {
                j.error("This crash report has been saved to: " + file1.getAbsolutePath());
            }
            else {
                j.error("We were unable to save this crash report to disk.");
            }

            this.a(crashreport);
        }
        finally {
            try {
                this.r();
                this.x = true;
            }
            catch (Throwable throwable1) {
                j.error("Exception stopping the server", throwable1);
            }
            finally {
                this.x();
            }
        }
    }

    private void a(ServerStatusResponse serverstatusresponse) {
        File file1 = this.d("server-icon.png");

        if (file1.isFile()) {
            ByteBuf bytebuf = Unpooled.buffer();

            try {
                BufferedImage bufferedimage = ImageIO.read(file1);

                Validate.validState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
                Validate.validState(bufferedimage.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
                ImageIO.write(bufferedimage, "PNG", new ByteBufOutputStream(bytebuf));
                ByteBuf bytebuf1 = Base64.encode(bytebuf);

                serverstatusresponse.a("data:image/png;base64," + bytebuf1.toString(Charsets.UTF_8));
            }
            catch (Exception exception) {
                j.error("Couldn\'t load server icon", exception);
            }
            finally {
                bytebuf.release();
            }
        }
    }

    public File w() {
        return new File(".");
    }

    protected void a(CrashReport crashreport) {
    }

    protected void x() {
    }

    protected void y() {
        ServerTaskManager.runTasks(); // CanaryMod: Run tasks
        long i0 = System.nanoTime();

        ++this.y;
        if (this.T) {
            this.T = false;
            this.b.a = true;
            this.b.a();
        }

        this.b.a("root");
        this.z();
        if (i0 - this.X >= 5000000000L) {
            this.X = i0;
            this.r.a(new ServerStatusResponse.PlayerCountData(this.H(), this.G()));
            GameProfile[] agameprofile = new GameProfile[Math.min(this.G(), 12)];
            int i1 = MathHelper.a(this.s, 0, this.G() - agameprofile.length);

            for (int i2 = 0; i2 < agameprofile.length; ++i2) {
                agameprofile[i2] = ((EntityPlayerMP)this.v.e.get(i1 + i2)).cc();
            }

            Collections.shuffle(Arrays.asList(agameprofile));
            this.r.b().a(agameprofile);
        }

        // if (this.y % 900 == 0) { // CanaryMod: use world auto-save interval from config
        if (this.y % Configuration.getServerConfig().getWorldAutoSaveInterval() == 0) {
            this.b.a("save");
            this.v.k();
            this.a(true);
            this.b.b();
        }

        this.b.a("tallying");
        this.g[this.y % 100] = System.nanoTime() - i0;
        this.b.b();
        this.b.a("snooper");
        if (!this.m.d() && this.y > 100) {
            this.m.a();
        }

        if (this.y % 6000 == 0) {
            this.m.b();
        }

        this.b.b();
        this.b.b();
    }

    // CanaryMod: ticks world
    private long previousTick = -1L; // Tick Time Tracker

    public void z() {
        new ServerTickHook(previousTick).call(); // CanaryMod: ServerTick
        long curTrack = System.nanoTime(); // CanaryMod: Start tick track

        this.b.a("jobs");
        Queue queue = this.i;

        synchronized (this.i) {
            while (!this.i.isEmpty()) {
                try {
                    ((FutureTask)this.i.poll()).run();
                }
                catch (Throwable throwable) {
                    j.fatal(throwable);
                }
            }
        }

        this.b.c("levels");

        int i0;

        // CanaryMod use worldManager instead, and copy into a new list (underlaying list may get modified)
        for (net.canarymod.api.world.World w : new ArrayList<net.canarymod.api.world.World>(worldManager.getAllWorlds())) {
            WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();
            //
            long i1 = System.nanoTime();

            this.b.a(worldserver.P().k());
            if (this.y % 20 == 0) {
                this.b.a("timeSync");
                // this.u.a((Packet) (new S03PacketTimeUpdate(worldserver.H(), worldserver.I(), worldserver.N().b("doDaylightCycle"))), worldserver.t.i);
                this.v.sendPacketToDimension(new S03PacketTimeUpdate(worldserver.K(), worldserver.L(), worldserver.Q().b("doDaylightCycle")), worldserver.getCanaryWorld().getName(), worldserver.t.q());
                this.b.b();
            }

            this.b.a("tick");

            CrashReport crashreport;

            try {
                worldserver.c();
            }
            catch (Throwable throwable1) {
                crashreport = CrashReport.a(throwable1, "Exception ticking world");
                worldserver.a(crashreport);
                throw new ReportedException(crashreport);
            }

            try {
                worldserver.i();
            }
            catch (Throwable throwable2) {
                crashreport = CrashReport.a(throwable2, "Exception ticking world entities");
                worldserver.a(crashreport);
                throw new ReportedException(crashreport);
            }

            this.b.b();
            this.b.a("tracker");
            worldserver.s().a();
            this.b.b();
            this.b.b();
            w.setNanoTick(this.y % 100, System.nanoTime() - i1);
            // this.k[i0][this.w % 100] = System.nanoTime() - i1;
        }

        this.b.c("connection");
        this.ao().c();
        this.b.c("players");
        this.v.e();
        this.b.c("tickables");

        for (i0 = 0; i0 < this.o.size(); ++i0) {
            ((IUpdatePlayerListBox)this.o.get(i0)).c();
        }

        this.b.b();

        // CanaryMod: set this ticks time
        previousTick = System.nanoTime() - curTrack;
        //
    }

    @Deprecated //CanaryMod: deprecate method
    public boolean A() {
        throw new UnsupportedOperationException("allow-nether has been moved to a per-world configuration!");
    }

    public void a(IUpdatePlayerListBox iupdateplayerlistbox) {
        this.o.add(iupdateplayerlistbox);
    }

    public static void main(String[] astring) {
        Bootstrap.c();

        try {
            // CanaryMod - Removed boolean flag0 = !GraphicsEnvironment.isHeadless();
            String s0 = null;
            String s1 = ".";
            String s2 = null;
            boolean flag1 = false;
            boolean flag2 = false;
            int i0 = -1;

            for (int i1 = 0; i1 < astring.length; ++i1) {
                String s3 = astring[i1];
                String s4 = i1 == astring.length - 1 ? null : astring[i1 + 1];
                boolean flag3 = false;

                // CanaryMod removed Nogui check, now done in Main
                if (s3.equals("--port") && s4 != null) {
                    flag3 = true;

                    try {
                        i0 = Integer.parseInt(s4);
                    }
                    catch (NumberFormatException numberformatexception) {
                        ;
                    }
                }
                else if (s3.equals("--singleplayer") && s4 != null) {
                    flag3 = true;
                    s0 = s4;
                }
                else if (s3.equals("--universe") && s4 != null) {
                    flag3 = true;
                    s1 = s4;
                }
                else if (s3.equals("--world") && s4 != null) {
                    flag3 = true;
                    s2 = s4;
                }
                else if (s3.equals("--demo")) {
                    flag1 = true;
                }
                else if (s3.equals("--bonusChest")) {
                    flag2 = true;
                }
                // CanaryMod removed else to Nogui check

                if (flag3) {
                    ++i1;
                }
            }

            final DedicatedServer dedicatedserver = new DedicatedServer(new File(s1));

            if (s0 != null) {
                dedicatedserver.j(s0);
            }

            if (s2 != null) {
                dedicatedserver.k(s2);
            }

            if (i0 >= 0) {
                dedicatedserver.b(i0);
            }

            if (flag1) {
                dedicatedserver.b(true);
            }

            if (flag2) {
                dedicatedserver.c(true);
            }

            // CanaryMod Removed old call to start GUI

            dedicatedserver.B();
            Runtime.getRuntime().addShutdownHook(new Thread("Server Shutdown Thread") {

                                                     public void run() {
                                                         dedicatedserver.r();
                                                     }
                                                 }
                                                );
        }
        catch (Exception exception) {
            //h.fatal("Failed to start the minecraft server", exception);
            throw new RuntimeException(exception); //We need to know something happen so we can terminate
        }
    }

    public void B() {
        this.aa = new Thread(this, "Server thread");
        this.aa.start();
    }

    public File d(String s0) {
        return new File(this.w(), s0);
    }

    public void e(String s0) {
        j.info(s0);
    }

    public void f(String s0) {
        j.warn(s0);
    }

    @Deprecated //CanaryMod: deprecate method
    public WorldServer a(int i0) {
        throw new UnsupportedOperationException("MinecraftServer.a(int) has" + " been replaced by MinecraftServer.getWorld(String, int).");
    }

    public WorldServer getWorld(String s, int i) {
        net.canarymod.api.world.World w = worldManager.getWorld(s, net.canarymod.api.world.DimensionType.fromId(i), true);

        if (w != null) {
            return (WorldServer)((CanaryWorld)w).getHandle();
        }
        return null;
    }

    public String C() {
        return this.t;
    }

    public int D() {
        return this.u;
    }

    public String E() {
        return this.E;
    }

    public String F() {
        return "1.8";
    }

    public int G() {
        return this.v.p();
    }

    public int H() {
        return this.v.q();
    }

    public String[] I() {
        return this.v.g();
    }

    public GameProfile[] J() {
        return this.v.h();
    }

    public String K() {
        return "";
    }

    public String g(String s0) {
        RConConsoleSource.h().i();
        this.p.a(RConConsoleSource.h(), s0);
        return RConConsoleSource.h().j();
    }

    public boolean L() {
        return false;
    }

    public void h(String s0) {
        j.error(s0);
    }

    public void i(String s0) {
        if (this.L()) {
            j.info(s0);
        }
    }

    public String getServerModName() {
        return "CanaryMod";
    }

    public CrashReport b(CrashReport crashreport) {
        crashreport.g().a("Profiler Position", new Callable() {

                              public String a() {
                                  return MinecraftServer.this.b.a ? MinecraftServer.this.b.c() : "N/A (disabled)";
                              }

                              public Object call() {
                                  return this.a();
                              }
                          }
                         );
        if (this.v != null) {
            crashreport.g().a("Player Count", new Callable() {

                                  public String call() {
                                      return MinecraftServer.this.v.p() + " / " + MinecraftServer.this.v.q() + "; " + MinecraftServer.this.v.e;
                                  }
                              }
                             );
        }

        return crashreport;
    }

    public List a(ICommandSender icommandsender, String s0, BlockPos blockpos) {
        ArrayList arraylist = Lists.newArrayList();

        if (s0.startsWith("/")) {
            s0 = s0.substring(1);
            boolean flag0 = !s0.contains(" ");

            List list = this.p.a(icommandsender, s0, blockpos);

            if (list != null) {
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    String s1 = (String)iterator.next();

                    if (flag0) {
                        arraylist.add("/" + s1);
                    }
                    else {
                        arraylist.add(s1);
                    }
                }
            }

            return arraylist;
        }
        else {
            String[] astring = s0.split(" ", -1);
            String s2 = astring[astring.length - 1];
            String[] astring1 = this.v.g();
            int i0 = astring1.length;

            for (int i1 = 0; i1 < i0; ++i1) {
                String s3 = astring1[i1];

                if (CommandBase.a(s2, s3)) {
                    arraylist.add(s3);
                }
            }

            return arraylist;
        }
    }

    public static MinecraftServer M() {
        return k;
    }

    public boolean N() {
        return this.n != null;
    }

    public String d_() {
        return "Server";
    }

    public void a(IChatComponent ichatcomponent) {
        j.info(ichatcomponent.c());
    }

    public boolean a(int i0, String s0) {
        return true;
    }

    public ICommandManager O() {
        return this.p;
    }

    public KeyPair P() {
        return this.H;
    }

    public int Q() {
        return this.u;
    }

    public void b(int i0) {
        this.u = i0;
    }

    public String R() {
        return this.I;
    }

    public void j(String s0) {
        this.I = s0;
    }

    public boolean S() {
        return this.I != null;
    }

    public String T() {
        return this.J;
    }

    public void k(String s0) {
        this.J = s0;
    }

    public void a(KeyPair keypair) {
        this.H = keypair;
    }

    public void a(EnumDifficulty enumdifficulty, WorldServer worldserver) { // CanaryMod: Signature change to include world
        // CanaryMod changes for Multiworld, Don't set every world the same.
        //WorldServer worldserver = (WorldServer) ((CanaryWorld) w).getHandle();
        if (worldserver != null) {
            // System.out.println(worldserver.getCanaryWorld().getName() + " Difficulty " + i0);
            boolean monsters = Configuration.getWorldConfig(worldserver.getCanaryWorld().getFqName()).canSpawnMonsters();
            boolean animals = Configuration.getWorldConfig(worldserver.getCanaryWorld().getFqName()).canSpawnAnimals();
            if (worldserver.P().t()) {
                worldserver.P().a(EnumDifficulty.HARD);
                worldserver.a(monsters, animals);
            }
            //else if (this.L()) { // NOT SINGLE PLAYER ANYWAYS
            //    worldserver.r = enumdifficulty;
            //    worldserver.a(monsters, animals);
            //}
            else {
                worldserver.P().a(enumdifficulty);
                worldserver.a(monsters, animals);
            }
        }
    }

    protected boolean V() {
        return true;
    }

    public boolean W() {
        return this.L;
    }

    public void b(boolean flag0) {
        this.L = flag0;
    }

    public void c(boolean flag0) {
        this.M = flag0;
    }

    public ISaveFormat X() {
        return this.l;
    }

    public void Z() {
        this.N = true;
        this.X().d();

        for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
            WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();

            if (worldserver != null) {
                worldserver.o();
            }

            if (w.getType().getId() == 0) {
                this.X().e(worldserver.O().g());
            }
        }

        this.u();
    }

    public String aa() {
        return this.O;
    }

    public String ab() {
        return this.P;
    }

    public void a_(String s0, String s1) {
        this.O = s0;
        this.P = s1;
    }

    public void a(PlayerUsageSnooper playerusagesnooper) {
        playerusagesnooper.a("whitelist_enabled", Boolean.valueOf(false));
        playerusagesnooper.a("whitelist_count", Integer.valueOf(0));
        if (this.v != null) {
            playerusagesnooper.a("players_current", Integer.valueOf(this.G()));
            playerusagesnooper.a("players_max", Integer.valueOf(this.H()));
            playerusagesnooper.a("players_seen", Integer.valueOf(this.v.r().length));
        }

        playerusagesnooper.a("uses_auth", Boolean.valueOf(this.z));
        playerusagesnooper.a("gui_state", this.aq() ? "enabled" : "disabled");
        playerusagesnooper.a("run_time", Long.valueOf((ax() - playerusagesnooper.g()) / 60L * 1000L));
        playerusagesnooper.a("avg_tick_ms", Integer.valueOf((int)(MathHelper.a(this.g) * 1.0E-6D)));
        int i0 = 0;

        for (net.canarymod.api.world.World cWorld : this.getWorldManager().getAllWorlds()) {
            WorldServer worldserver = (WorldServer)((CanaryWorld)cWorld).getHandle();
            //WorldServer worldserver = this.c[i1];
            WorldInfo worldinfo = worldserver.P();

            playerusagesnooper.a("world[" + i0 + "][dimension]", Integer.valueOf(worldserver.t.q()));
            playerusagesnooper.a("world[" + i0 + "][mode]", worldinfo.r());
            playerusagesnooper.a("world[" + i0 + "][difficulty]", worldserver.aa());
            playerusagesnooper.a("world[" + i0 + "][hardcore]", Boolean.valueOf(worldinfo.t()));
            playerusagesnooper.a("world[" + i0 + "][generator_name]", worldinfo.u().a());
            playerusagesnooper.a("world[" + i0 + "][generator_version]", Integer.valueOf(worldinfo.u().d()));
            playerusagesnooper.a("world[" + i0 + "][height]", Integer.valueOf(this.F));
            playerusagesnooper.a("world[" + i0 + "][chunks_loaded]", Integer.valueOf(worldserver.N().g()));
            ++i0;
            //}
        }

        playerusagesnooper.a("worlds", Integer.valueOf(i0));
    }

    public void b(PlayerUsageSnooper playerusagesnooper) {
        playerusagesnooper.b("singleplayer", Boolean.valueOf(this.S()));
        playerusagesnooper.b("server_brand", this.getServerModName());
        playerusagesnooper.b("gui_supported", GraphicsEnvironment.isHeadless() ? "headless" : "supported");
        playerusagesnooper.b("dedicated", Boolean.valueOf(this.ad()));
    }

    public boolean ac() {
        return true;
    }

    public abstract boolean ad();

    public boolean ae() {
        return this.z;
    }

    public void d(boolean flag0) {
        this.z = flag0;
    }

    public boolean af() {
        return this.A;
    }

    public void e(boolean flag0) {
        this.A = flag0;
    }

    public boolean ag() {
        return this.B;
    }

    public void f(boolean flag0) {
        this.B = flag0;
    }

    public boolean ah() {
        return this.C;
    }

    public void g(boolean flag0) {
        this.C = flag0;
    }

    public boolean ai() {
        return this.D;
    }

    public void h(boolean flag0) {
        this.D = flag0;
    }

    public abstract boolean aj();

    public String ak() {
        return this.E;
    }

    public void m(String s0) {
        this.E = s0;
    }

    public int al() {
        return this.F;
    }

    public void c(int i0) {
        this.F = i0;
    }

    public boolean am() {
        return this.x;
    }

    public ServerConfigurationManager an() {
        return this.v;
    }

    public void a(ServerConfigurationManager serverconfigurationmanager) {
        this.v = serverconfigurationmanager;
    }

    public void a(WorldSettings.GameType worldsettings_gametype) {
        /*
        for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
            WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();

            worldserver.P().a(worldsettings_gametype);
        }
        */
    }

    public NetworkSystem ao() {
        return this.q;
    }

    public boolean aq() {
        return false;
    }

    public abstract String a(WorldSettings.GameType worldsettings_gametype, boolean flag0);

    public int ar() {
        return this.y;
    }

    public void as() {
        this.T = true;
    }

    public BlockPos c() {
        return BlockPos.a;
    }

    public Vec3 d() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    public World e() {
        return ((CanaryWorld)Canary.getServer().getDefaultWorld()).getHandle(); // CanaryMod
    }

    public Entity f() {
        return null;
    }

    public int au() {
        return 16;
    }

    public boolean a(World world, BlockPos blockpos, EntityPlayer entityplayer) {
        return false;
    }

    public void i(boolean flag0) {
        this.U = flag0;
    }

    public boolean av() {
        return this.U;
    }

    public Proxy aw() {
        return this.d;
    }

    public static long ax() {
        return System.currentTimeMillis();
    }

    public int ay() {
        return this.G;
    }

    public void d(int i0) {
        this.G = i0;
    }

    public IChatComponent e_() {
        return new ChatComponentText(this.d_());
    }

    public boolean az() {
        return true;
    }

    public MinecraftSessionService aB() {
        return this.W;
    }

    public GameProfileRepository aC() {
        return this.Y;
    }

    public PlayerProfileCache aD() {
        return this.Z;
    }

    public ServerStatusResponse aE() {
        return this.r;
    }

    public void aF() {
        this.X = 0L;
    }

    public Entity a(UUID uuid) {

        /* CanaryMod: Modified for Multiworld
        WorldServer[] aworldserver = this.c;
        int i0 = aworldserver.length;

        for (int i1 = 0; i1 < i0; ++i1) {
            WorldServer worldserver = aworldserver[i1];
        */
        for (net.canarymod.api.world.World w : worldManager.getAllWorlds()) {
            WorldServer worldserver = (WorldServer)((CanaryWorld)w).getHandle();

            if (worldserver != null) {
                Entity entity = worldserver.a(uuid);

                if (entity != null) {
                    return entity;
                }
            }
        }

        return null;
    }

    public boolean t_() {
        /* CanaryMod: Modified for Multiworld
        return M().c[0].Q().b("sendCommandFeedback");
        */
        return ((CanaryWorld)Canary.getServer().getDefaultWorld()).getHandle().Q().b("sendCommandFeedback");
    }

    public void a(CommandResultStats.Type commandresultstats_type, int i0) {
    }

    public int aG() {
        return 29999984;
    }

    public ListenableFuture a(Callable callable) {
        Validate.notNull(callable);
        if (!this.aH()) {
            ListenableFutureTask listenablefuturetask = ListenableFutureTask.create(callable);
            Queue queue = this.i;

            synchronized (this.i) {
                this.i.add(listenablefuturetask);
                return listenablefuturetask;
            }
        }
        else {
            try {
                return Futures.immediateFuture(callable.call());
            }
            catch (Exception exception) {
                return Futures.immediateFailedCheckedFuture(exception);
            }
        }
    }

    public ListenableFuture a(Runnable runnable) {
        Validate.notNull(runnable);
        return this.a(Executors.callable(runnable));
    }

    public boolean aH() {
        return Thread.currentThread() == this.aa;
    }

    public int aI() {
        return 256;
    }

    public long aJ() {
        return this.ab;
    }

    public Thread aK() {
        return this.aa;
    }

    /**
     * Returns the canary world manager for this server instance
     *
     * @return
     */
    public CanaryWorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Reload configurations
     */
    public abstract void reload(); // look in DedicatedServer class

    // CanaryMod

    /**
     * Get the CanaryMod server handler
     *
     * @return
     */
    public CanaryServer getServer() {
        return server;
    }

    public CanaryConfigurationManager getConfigurationManager() {
        return v.getConfigurationManager();
    }

    public void initShutdown(String message) {
        this.stopMsg = message;
        this.u();
    }

    public boolean isRunning() {
        return this.w;
    }

    /**
     * Creates a new world with given name and seed.
     * This will load the default (NORMAL) world
     *
     * @param name
     * @param seed
     */
    public void loadWorld(String name, long seed) {
        loadWorld(name, seed, net.canarymod.api.world.DimensionType.NORMAL);
    }

    public void loadWorld(String name, long seed, net.canarymod.api.world.DimensionType type) {
        this.loadWorld(name, seed, type, net.canarymod.api.world.WorldType.DEFAULT);
    }

    public void loadWorld(String name, long seed, net.canarymod.api.world.DimensionType type, net.canarymod.api.world.WorldType typeGen) {
        this.initWorld(name, "I DONT KNOW WHAT THIS IS", seed, WorldType.a(typeGen.toString()), type, null);
    }

    public static boolean isHeadless() {
        return !MinecraftServer.notHeadless;
    }

    public static void setHeadless(boolean state) {
        if (!GraphicsEnvironment.isHeadless()) {
            MinecraftServer.notHeadless = !state;
        }
    }

    public void loadStartArea(WorldServer world){
        this.k(world);
    }

    public WorldServer defaultWorld() {
        return (WorldServer)((CanaryWorld)worldManager.getWorld(this.J, DimensionType.NORMAL, true)).getHandle();
    }

    public int getProtocolVersion() {
        return this.r.c().b();
    }

    public void setDefaultGameMode(WorldSettings.GameType worldsettings_gametype, World world) {
        WorldServer worldserver = (WorldServer)world;
        worldserver.P().a(worldsettings_gametype);
        Configuration.getWorldConfig(world.getCanaryWorld().getFqName()).getFile().setInt("gamemode", worldsettings_gametype.a());
    }
}
