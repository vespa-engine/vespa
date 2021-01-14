// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;

import java.util.List;
import java.util.logging.Level;

/**
 * A (stateful) curator wrapper for the config server. This simplifies Curator method calls used by the config server
 * and knows about how config content is mapped to node names and stored.
 * <p>
 * Usage details:
 * Config ids are stored as foo#bar#c0 instead of foo/bar/c0, for simplicity.
 * Keep the amount of domain-specific logic here to a minimum.
 * Data for one application x is stored on this form:
 * /config/v2/tenants/x/sessions/y/defconfigs
 * /config/v2/tenants/x/sessions/y/userapp
 * <p>
 * The user application structure is exactly the same as in the user's app dir during deploy.
 * The current live app id (for example y) is stored in the node //config/v2/tenants/x/applications/&lt;application-id&gt;
 * It is updated outside this class, typically in config server when activating config
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

    private final Curator curator;

    public static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigCurator.class.getName());

    /** The maximum size of a ZooKeeper node */
    private final int maxNodeSize;

    public static ConfigCurator create(Curator curator) {
        return new ConfigCurator(curator, 1024*1024*10);
    }

    @Inject
    public ConfigCurator(Curator curator, ZookeeperServerConfig config) {
        this(curator, config.juteMaxBuffer());
    }

    private ConfigCurator(Curator curator, int maxNodeSize) {
        this.curator = curator;
        this.maxNodeSize = maxNodeSize;
        log.log(Level.CONFIG, "Using jute max buffer size " + this.maxNodeSize);
        testZkConnection();
    }

    /** Returns the curator instance this wraps */
    public Curator curator() { return curator; }

    /** Cleans and creates a zookeeper completely */
    void initAndClear(String path) {
        try {
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

    /**
     * Returns the data at a path, or null if the path does not exist.
     *
     * @param path a String with a pathname.
     * @return a byte array with data.
     */
    public byte[] getBytes(String path) {
        try {
            if ( ! exists(path)) return null; // TODO: Ugh
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
    private void putData(String path, String node, byte[] data) {
        putData(createFullPath(path, node), data);
    }

    /** Sets data at a given path. Creates the path if it doesn't exist */
    public void putData(String path, byte[] data) {
        try {
            ensureDataIsNotTooLarge(data, path);
            if (exists(path))
                curator.framework().setData().forPath(path, data);
            else
                curator.framework().create().creatingParentsIfNeeded().forPath(path, data);
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
    private String toConfigserverName(String node) {
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
     * @param path    /zoopath
     * @param data    The contents to write to ZK (as a byte array)
     */
    public void putDefData(String name, String path, byte[] data) {
            putData(path, name, data);
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

    private void testZkConnection() { // This is not necessary, but allows us to give a useful error message
        if (curator.connectionSpec().isEmpty()) return;
        try {
            curator.framework().checkExists().forPath("/dummy");
        }
        catch (Exception e) {
            log.log(Level.SEVERE, "Unable to connect to ZooKeeper on " + curator.connectionSpec() +
                    ". Please verify that VESPA_CONFIGSERVERS points to the correct configserver(s) " +
                    "on all config server nodes and are the same config server(s) as in services.xml, " +
                    "and that they are started. " +
                    "Check the log(s) for config server errors. Aborting.", e);
        }
    }

}
