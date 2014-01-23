package net.canarymod.api.world;

import net.canarymod.Canary;
import net.canarymod.api.CanaryServer;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.hook.system.UnloadWorldHook;

import java.io.File;
import java.util.*;

/**
 * This is a container for all of the worlds.
 *
 * @author Jos Kuijpers
 * @author Chris Ksoll
 * @author Jason (darkdiplomat)
 */
public class CanaryWorldManager implements WorldManager {

    private Map<String, World> loadedWorlds;
    private List<String> existingWorlds;
    private Map<String, Boolean> markedForUnload;
    private static final Object worldLock = new Object();

    public CanaryWorldManager() {
        DimensionType.registerType("NORMAL", 0);
        DimensionType.registerType("NETHER", -1);
        DimensionType.registerType("END", 1);
        File worldsFolders = new File("worlds");

        if (!worldsFolders.exists()) {
            worldsFolders.mkdirs();
        }
        try {
            int worldNum = worldsFolders.listFiles().length;

            if (worldNum == 0) {
                worldNum = 1;
            }
            existingWorlds = new ArrayList<String>(worldNum);
            loadedWorlds = new HashMap<String, World>(worldNum);
            for (String f : worldsFolders.list()) {
                File world = new File(worldsFolders, f);
                for (File fqname : world.listFiles()) {
                    if (fqname.isDirectory() && fqname.getName().contains("_")) {
                        existingWorlds.add(fqname.getName());
                    }
                }
            }
            markedForUnload = new HashMap<String, Boolean>(1);
        }
        catch (NullPointerException e) {
            Canary.logDerp("Failed to initialise world manager!", e);
        }
    }

    /**
     * Implementation specific, do not call outside of NMS!
     * Adds an already prepared world to the world manager
     *
     * @param world
     */
    public void addWorld(CanaryWorld world) {
        Canary.logDebug(String.format("Adding new world to world manager, filed as %s_%s", world.getName(), world.getType()
                .getName()));
        loadedWorlds.put(world.getName() + "_" + world.getType().getName(), world);
    }

    @Override
    public World getWorld(String name, boolean autoload) {
        DimensionType t = DimensionType.fromName(name.substring(Math.max(0, name.lastIndexOf("_") + 1)));
        String nameOnly = name.substring(0, Math.max(0, name.lastIndexOf("_")));

        if (t != null) {
            return getWorld(nameOnly, t, autoload);
        }

        if (loadedWorlds.containsKey(name)) {
            return loadedWorlds.get(name);
        }
        else if (loadedWorlds.containsKey(name + "_NORMAL")) {
            return loadedWorlds.get(name + "_NORMAL");
        }
        else {
            if (autoload) {
                if (existingWorlds.contains(name)) {
                    return loadWorld(name, DimensionType.fromId(0));
                }
                else if (existingWorlds.contains(name + "_NORMAL")) {
                    return loadWorld(name, DimensionType.fromId(0));
                }
                else {
                    throw new UnknownWorldException("World " + name + " is unknown. Autoload was enabled for this call.");
                }
            }
            throw new UnknownWorldException("World " + name + " is not loaded. Autoload was disabled for this call.");
        }
    }

    @Override
    public World getWorld(String world, DimensionType type, boolean autoload) {
        if (worldIsLoaded(world + "_" + type.getName())) {
            return loadedWorlds.get(world + "_" + type.getName());
        }
        else {
            if (worldExists(world + "_" + type.getName()) && autoload) {
                Canary.logDebug("World exists but is not loaded. Loading ...");
                return loadWorld(world, type);
            }
            else {
                if (autoload) {
                    Canary.logDebug("World does not exist, we can autoload, will load!");
                    createWorld(world, type);
                    return loadedWorlds.get(world + "_" + type.getName());
                }
                else {
                    Canary.logSevere("Tried to get a non-existing world: " + world + " - you must create it before you can load it or pass autoload = true");
                    return null;
                }

            }
        }

    }

    private void updateExistingWorldsList(String name, DimensionType type) {
        existingWorlds.add(name + "_" + type.getName());
    }

    @Override
    public boolean createWorld(String name, long seed, DimensionType type) {
        ((CanaryServer) Canary.getServer()).getHandle().loadWorld(name, seed, type);
        updateExistingWorldsList(name, type);
        return true;
    }

    @Override
    public boolean createWorld(String name, DimensionType type) {
        Canary.logDebug("Creating a new world! " + name + "_" + type.getName());
        ((CanaryServer) Canary.getServer()).getHandle().loadWorld(name, new Random().nextLong(), type);
        updateExistingWorldsList(name, type);
        return true;
    }

    @Override
    public boolean createWorld(String name, long seed, DimensionType worldType, WorldType genType) {
        ((CanaryServer) Canary.getServer()).getHandle().loadWorld(name + worldType.getName(), new Random().nextLong(), worldType, genType);
        updateExistingWorldsList(name, worldType);
        return true;
    }

    @Override
    public World loadWorld(String name, DimensionType type) {
        //TODO: Instead should we throw an exception to avoid loading trash worlds?
        if (!worldIsLoaded(name + "_" + type.getName())) {
            ((CanaryServer) Canary.getServer()).getHandle().loadWorld(name, new Random().nextLong(), type);
            return loadedWorlds.get(name + "_" + type.getName());
        }
        else {
            return loadedWorlds.get(name + "_" + type.getName());
        }
    }

    @Override
    public void destroyWorld(String name) {
        File file = new File("worlds/" + name);
        File dir = new File("worldsbackups/" + name);
        boolean success = file.renameTo(new File(dir, file.getName()));

        if (!success) {
            Canary.logSevere("Attempted to move world " + name + " but it appeared to be still in use! Worlds should get unloaded before they are removed!");
        }
    }

    @Override
    public Collection<World> getAllWorlds() {
        // before we return all the worlds, first check if there are any worlds marked for unload!
        if (markedForUnload.size() > 0) {
            Canary.logDebug("Processing worlds for unload");
            removeWorlds();
            // markedForUnload.clear();
        }
        // Canary.println("getAllWorlds");
        return Collections.unmodifiableCollection(this.loadedWorlds.values());
    }

    @Override
    public void unloadWorld(String name, DimensionType type, boolean force) {
        // This actually just schedules a world for unloading,
        // to prevent ConcurrentModificationExceptions as the values for world are iterated over constantly.
        // See getAllWorld for details
        markedForUnload.put(name + "_" + type.getName(), force);
    }

    /**
     * This'll actually remove all marked worlds from the system so that they may get GC'd soon after
     */
    private void removeWorlds() {
        Iterator<String> iter = markedForUnload.keySet().iterator();
        synchronized (worldLock) {
            while (iter.hasNext()) {
                String fqName = iter.next();
                CanaryWorld world = (CanaryWorld) loadedWorlds.get(fqName);
                boolean force = markedForUnload.get(fqName);
                if (world.getPlayerList().size() > 0) {
                    if (force) {
                        for (Player p : world.getPlayerList()) {
                            p.kick("Server scheduled world shutdown");
                        }
                    }
                    else {
                        Canary.logWarning(world.getFqName() + " was scheduled for unload but there were still players in it. Not unloading world!");
                        continue;
                    }
                }
                world.save();
                new UnloadWorldHook(world).call();
                loadedWorlds.remove(world.getFqName());
                iter.remove();

            }
        }
    }

    @Override
    public boolean worldIsLoaded(String name) {
        return loadedWorlds.containsKey(name);
    }

    @Override
    public boolean worldIsLoaded(String name, DimensionType type) {
        return loadedWorlds.containsKey(name.concat("_").concat(type.getName()));
    }

    @Override
    public boolean worldExists(String name) {
        return new File("worlds/" + name.split("_")[0] + "/" + name).isDirectory();
    }

    @Override
    public List<String> getExistingWorlds() {
        return Collections.unmodifiableList(existingWorlds); // TODO: This only reads base folders not the real dimension folders!
    }

    @Override
    public String[] getExistingWorldsArray() {
        return existingWorlds.toArray(new String[existingWorlds.size()]);
    }

    @Override
    public String[] getLoadedWorldsNames() {
        if (markedForUnload.size() > 0) {
            Canary.logDebug("Processing worlds for unload");
            removeWorlds();
        }
        Set<String> names = new HashSet<String>();
        for (String name : loadedWorlds.keySet()) {
            names.add(name);
        }
        return names.toArray(new String[names.size()]);
    }

    @Override
    public String[] getLoadedWorldsNamesOfDimension(DimensionType dimensionType) {
        if (markedForUnload.size() > 0) {
            Canary.logDebug("Processing worlds for unload");
            removeWorlds();
        }
        Set<String> worlds = new HashSet<String>();
        for (World world : loadedWorlds.values()) {
            if (world.getType() == dimensionType) {
                worlds.add(world.getFqName());
            }
        }
        return worlds.toArray(new String[worlds.size()]);
    }
}
