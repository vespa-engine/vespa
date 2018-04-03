// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.google.inject.Inject;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A (stateful) curator wrapper for the config server. This simplifies Curator method calls used by the config server
 * and knows about how config content is mapped to node names and stored.
 * <p>
 * Usage details:
 * Config ids are stored as foo#bar#c0 instead of foo/bar/c0, for simplicity.
 * Keep the amount of domain-specific logic here to a minimum.
 * Data for one application x is stored on this form:
 * /vespa/config/apps/x/defconfigs
 * /vespa/config/apps/x/userapp
 * The different types of configs are stored on this form (ie. names of the ZK nodes under their respective
 * paths):
 * <p>
 * Def configs are stored on the form        name,version
 * The user application structure is exactly the same as in the user's app dir during deploy.
 * The current live app id (for example x) is stored in the node /vespa/config/liveapp
 * It is updated outside this class, typically in config server during reload-config.
 * Some methods have retries and/or reconnect. This is necessary because ZK will throw on certain scenarios,
 * even though it will recover from it itself, @see http://wiki.apache.org/hadoop/ZooKeeper/ErrorHandling
 *
 * @author Vegard Havdal
 * @author bratseth
 */
public class ConfigCurator {

    /** Path for def files, under one app */
    public static final String DEFCONFIGS_ZK_SUBPATH = "/defconfigs";

    /** Path for def files, under one app */
    public static final String USER_DEFCONFIGS_ZK_SUBPATH = "/userdefconfigs";

    /** Path for metadata about an application */
    public static final String META_ZK_PATH = "/meta";

    /** Path for the app package's dir structure, under one app */
    public static final String USERAPP_ZK_SUBPATH = "/userapp";

    /** Path for session state */
    public static final String SESSIONSTATE_ZK_SUBPATH = "/sessionState";

    protected static final FilenameFilter acceptsAllFileNameFilter = (dir, name) -> true;

    private final Curator curator;

    public static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigCurator.class.getName());

    /** The number of zookeeper operations done with this ZKFacade instance */
    private final AtomicInteger operations = new AtomicInteger();

    /** The number of zookeeper read operations done with this ZKFacade instance */
    private final AtomicInteger readOperations = new AtomicInteger();

    /** The number of zookeeper write operations done with this ZKFacade instance */
    private final AtomicInteger writeOperations = new AtomicInteger();

    /** The maximum size of a ZooKeeper node */
    private final int maxNodeSize;

    /**
     * Sets up thread local zk access if not done before and returns a facade object
     *
     * @return a ZKFacade object
     */
    public static ConfigCurator create(Curator curator, int juteMaxBuffer) {
        return new ConfigCurator(curator, juteMaxBuffer);
    }

    public static ConfigCurator create(Curator curator) {
        return new ConfigCurator(curator, 1024*1024*10);
    }

    @Inject
    public ConfigCurator(Curator curator, ZooKeeperServer server) {
        this(curator, server.getZookeeperServerConfig().juteMaxBuffer());
    }

    private ConfigCurator(Curator curator, int maxNodeSize) {
        this.curator = curator;
        this.maxNodeSize = maxNodeSize;
        log.log(LogLevel.CONFIG, "Using jute max buffer size " + this.maxNodeSize);
        testZkConnection();
    }

    /** Returns the curator instance this wraps */
    public Curator curator() { return curator; }

    /** Cleans and creates a zookeeper completely */
    public void initAndClear(String path) {
        try {
            operations.incrementAndGet();
            readOperations.incrementAndGet();
            if (exists(path))
                deleteRecurse(path);
            createRecurse(path);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception clearing path " + path + " in ZooKeeper", e);
        }
    }

    /** Creates a path. If the path already exists this does nothing. */
    private void createRecurse(String path) {
        try {
            if (exists(path)) return;
            curator.framework().create().creatingParentsIfNeeded().forPath(path);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception creating path " + path + " in ZooKeeper", e);
        }
    }

    /** Returns the data at a path and node. Replaces / by # in node names. Returns null if the path doesn't exist. */
    public String getData(String path, String node) {
        return getData(createFullPath(path, node));
    }

    /** Returns the data at a path. Returns null if the path doesn't exist. */
    public String getData(String path) {
        byte[] data = getBytes(path);
        return (data == null) ? null : Utf8.toString(data);
    }

    /** Returns the data at a path and node. Replaces / by # in node names. Returns null if the path doesn't exist. */
    public byte[] getBytes(String path, String node) {
        return getBytes(createFullPath(path, node));
    }

    /**
     * Returns the data at a path, or null if the path does not exist.
     *
     * @param path a String with a pathname.
     * @return a byte array with data.
     */
    public byte[] getBytes(String path) {
        try {
            if ( ! exists(path)) return null; // TODO: Ugh
            operations.incrementAndGet();
            readOperations.incrementAndGet();
            return curator.framework().getData().forPath(path);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception reading from path " + path + " in ZooKeeper", e);
        }
    }

    /** Returns whether a path exists in zookeeper */
    public boolean exists(String path, String node) {
        return exists(createFullPath(path, node));
    }

    /** Returns whether a path exists in zookeeper */
    public boolean exists(String path) {
        try {
            return curator.framework().checkExists().forPath(path) != null;
        }
        catch (Exception e) {
            throw new RuntimeException("Exception checking existence of path " + path + " in ZooKeeper", e);
        }
    }

    /** Creates a Zookeeper node. If the node already exists this does nothing. */
    public void createNode(String path) {
        if ( ! exists(path))
            createRecurse(path);
        operations.incrementAndGet();
        writeOperations.incrementAndGet();
    }

    /** Creates a Zookeeper node synchronously. Replaces / by # in node names. */
    public void createNode(String path, String node) {
        createNode(createFullPath(path, node));
    }

    private String createFullPath(String path, String node) {
        return path + "/" + toConfigserverName(node);
    }

    /** Sets data at a given path and name. Replaces / by # in node names. Creates the node if it doesn't exist */
    public void putData(String path, String node, String data) {
        putData(path, node, Utf8.toBytes(data));
    }

    /** Sets data at a given path. Creates the node if it doesn't exist */
    public void putData(String path, String data) {
        putData(path, Utf8.toBytes(data));
    }

    private void ensureDataIsNotTooLarge(byte[] toPut, String path) {
        if (toPut.length >= maxNodeSize) {
            throw new IllegalArgumentException("Error: too much zookeeper data in node: "
                    + "[" + toPut.length + " bytes] (path " + path + ")");
        }
    }

    /** Sets data at a given path and name. Replaces / by # in node names. Creates the node if it doesn't exist */
    public void putData(String path, String node, byte[] data) {
        putData(createFullPath(path, node), data);
    }

    /** Sets data at a given path. Creates the path if it doesn't exist */
    public void putData(String path, byte[] data) {
        try {
            ensureDataIsNotTooLarge(data, path);
            operations.incrementAndGet();
            writeOperations.incrementAndGet();
            if (exists(path))
                curator.framework().setData().forPath(path, data);
            else
                curator.framework().create().creatingParentsIfNeeded().forPath(path, data);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception writing to path " + path + " in ZooKeeper", e);
        }
    }

    /** Sets data at an existing node. Replaces / by # in node names. */
    public void setData(String path, String node, String data) {
        setData(path, node, Utf8.toBytes(data));
    }

    /** Sets data at an existing node. Replaces / by # in node names. */
    public void setData(String path, String node, byte[] data) {
        setData(createFullPath(path, node), data);
    }

    /** Sets data at an existing node. Replaces / by # in node names. */
    public void setData(String path, byte[] data) {
        try {
            ensureDataIsNotTooLarge(data, path);
            operations.incrementAndGet();
            writeOperations.incrementAndGet();
            curator.framework().setData().forPath(path, data);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception writing to path " + path + " in ZooKeeper", e);
        }
    }

    /**
     * Replaces / with # in the given node.
     *
     * @param node a zookeeper node name
     * @return a config server node name
     */
    protected String toConfigserverName(String node) {
        if (node.startsWith("/")) node = node.substring(1);
        return node.replaceAll("/", "#");
    }

    /**
     * Lists thh children at the given path.
     *
     * @return the local names of the children at this path, or an empty list (never null) if none.
     */
    public List<String> getChildren(String path) {
        try {
            operations.incrementAndGet();
            readOperations.incrementAndGet();
            return curator.framework().getChildren().forPath(path);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception getting children of path " + path + " in ZooKeeper", e);
        }
    }

    /**
     * Puts config definition data and metadata into ZK.
     *
     * @param name    The config definition name (including namespace)
     * @param version The config definition version
     * @param path    /zoopath
     * @param data    The contents to write to ZK (as a byte array)
     */
    public void putDefData(String name, String version, String path, byte[] data) {
        if (version == null) {
            putData(path, name, data);
        } else {
            String fullPath = createFullPath(path, name + "," + version);
            if (exists(fullPath)) {
                // TODO This should not happen when all the compatibility hacks in 5.1 have been removed
                log.log(LogLevel.INFO, "There already exists a config definition '" + name + "', skipping feeding this one to ZooKeeper");
            }
            else {
                putData(fullPath, data);
            }
        }
    }

    /**
     * Takes for instance the dir /app  and puts the contents into the given ZK path. Ignores files starting with dot,
     * and dirs called CVS.
     *
     * @param dir            directory which holds the summary class part files
     * @param path           zookeeper path
     * @param filenameFilter A FilenameFilter which decides which files in dir are fed to zookeeper
     * @param recurse        recurse subdirectories
     */
    public void feedZooKeeper(File dir, String path, FilenameFilter filenameFilter, boolean recurse) {
        try {
            if (filenameFilter == null) {
                filenameFilter = acceptsAllFileNameFilter;
            }
            if (!dir.isDirectory()) {
                log.fine(dir.getCanonicalPath() + " is not a directory. Not feeding the files into ZooKeeper.");
                return;
            }
            for (File file : listFiles(dir, filenameFilter)) {
                if (file.getName().startsWith(".")) continue; //.svn , .git ...
                if ("CVS".equals(file.getName())) continue;
                if (file.isFile()) {
                    byte[] contents = IOUtils.readFileBytes(file);
                    putData(path, file.getName(), contents);
                } else if (recurse && file.isDirectory()) {
                    createNode(path, file.getName());
                    feedZooKeeper(file, path + '/' + file.getName(), filenameFilter, recurse);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Exception feeding ZooKeeper at path " + path, e);
        }
    }

    /**
     * Same as normal listFiles, but use the filter only for normal files
     *
     * @param dir    directory to list files in
     * @param filter A FilenameFilter which decides which files in dir are listed
     * @return an array of Files
     */
    protected File[] listFiles(File dir, FilenameFilter filter) {
        File[] rawList = dir.listFiles();
        List<File> ret = new ArrayList<>();
        if (rawList != null) {
            for (File f : rawList) {
                if (f.isDirectory()) {
                    ret.add(f);
                } else {
                    if (filter.accept(dir, f.getName())) {
                        ret.add(f);
                    }
                }
            }
        }
        return ret.toArray(new File[ret.size()]);
    }

    /** Deletes the node at the given path, and any children it may have. If the node does not exist this does nothing */
    public void deleteRecurse(String path) {
        try {
            if ( ! exists(path)) return;
            curator.framework().delete().deletingChildrenIfNeeded().forPath(path);
        }
        catch (Exception e) {
            throw new RuntimeException("Exception deleting path " + path, e);
        }
    }

    public Integer getNumberOfOperations() { return operations.intValue(); }

    public Integer getNumberOfReadOperations() {
        return readOperations.intValue();
    }

    public Integer getNumberOfWriteOperations() {
        return writeOperations.intValue();
    }


    private void testZkConnection() { // This is not necessary, but allows us to give a useful error message
        if (curator.connectionSpec().isEmpty()) return;
        try {
            curator.framework().checkExists().forPath("/dummy");
        }
        catch (Exception e) {
            log.log(LogLevel.ERROR, "Unable to contact ZooKeeper on " + curator.connectionSpec() +
                    ". Please verify for all configserver nodes that " +
                    "services.addr_configserver points to the correct configserver(s), " +
                    "the same configserver(s) as in services.xml, and that they are started. " +
                    "Check the log(s) for configserver errors. Aborting.", e);
        }
    }

}
