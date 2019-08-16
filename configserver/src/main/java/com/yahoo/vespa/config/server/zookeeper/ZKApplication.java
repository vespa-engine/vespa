// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Responsible for providing data from an application subtree in zookeeper.
 * (i.e. /config/v2/tenants/x/session/&lt;session id for an application&gt;/).
 *
 * Takes care of
 *
 *
 * @author Tony Vaagenes
 */
public class ZKApplication {

    private final ConfigCurator zk;
    private final Path appPath;

    ZKApplication(ConfigCurator zk, Path appPath) {
        this.zk = zk;
        this.appPath = appPath;
    }

    /**
     * Returns a list of the files (as readers) in the given path. The readers <b>must</b>
     * be closed by the caller.
     *
     * @param path           a path relative to the session
     *                       (i.e. /config/v2/tenants/x/sessions/&lt;session id&gt;/).
     * @param fileNameSuffix the suffix of files to return, or null to return all
     * @param recursive      if true, all files from all subdirectories of this will also be returned
     * @return the files in the given path, or an empty list if the directory does not exist or is empty.
     *         The list gets owned by the caller and can be modified freely.
     */
    List<NamedReader> getAllDataFromDirectory(String path, String fileNameSuffix, boolean recursive) {
        return getAllDataFromDirectory(path, "", fileNameSuffix, recursive);
    }

    /**
     * As above, except
     *
     * @param namePrefix the prefix to prepend to the returned reader names
     */
    private List<NamedReader> getAllDataFromDirectory(String path, String namePrefix, String fileNameSuffix, boolean recursive) {
        String fullPath = getFullPath(path);
        List<NamedReader> result = new ArrayList<>();
        List<String> children = getChildren(path);

        try {
            for (String child : children) {
                if (fileNameSuffix == null || child.endsWith(fileNameSuffix)) {
                    result.add(new NamedReader(namePrefix + child, reader(zk.getData(fullPath, child))));
                }
                if (recursive)
                    result.addAll(getAllDataFromDirectory(path + "/" + child,
                                                          namePrefix + child + "/", fileNameSuffix, recursive));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Could not retrieve all data from '" + fullPath + "' in zookeeper", e);
        }
    }

    /**
     * Retrieves a node relative to the node of the live application
     *
     * @param path a path relative to the currently active application
     * @param node a path relative to the path above
     * @return a Reader that can be used to get the data
     */
    Reader getDataReader(String path, String node) {
        String data = getData(path, node);
        if (data == null) {
            throw new IllegalArgumentException("No node for " + getFullPath(path) + "/" + node + " exists");
        }
        return reader(data);
    }

    public String getData(String path, String node) {
        try {
            return zk.getData(getFullPath(path), node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not retrieve node '" +
                                               getFullPath(path) + "/" + node + "' in zookeeper", e);
        }
    }

    public String getData(String path) {
        try {
            return zk.getData(getFullPath(path));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not retrieve path '" + getFullPath(path) + "' in zookeeper", e);
        }
    }

    public byte[] getBytes(String path) {
        try {
            return zk.getBytes(getFullPath(path));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not retrieve path '" + getFullPath(path) + "' in zookeeper", e);
        }
    }

    void putData(String path, String data) {
        try {
            zk.putData(getFullPath(path), data);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not put data to node '" + getFullPath(path) + "' in zookeeper", e);
        }
    }

    /**
     * Checks if the given node exists under path under this live app
     *
     * @param path a zookeeper path
     * @param node a zookeeper node
     * @return true if the node exists in the path, false otherwise
     */
    public boolean exists(String path, String node) {
        return zk.exists(getFullPath(path), node);
    }

    /**
     * Checks if the given node exists under path under this live app
     *
     * @param path a zookeeper path
     * @return true if the node exists in the path, false otherwise
     */
    public boolean exists(String path) {
        return zk.exists(getFullPath(path));
    }

    private String getFullPath(String path) {
        Path fullPath = appPath;
        if (path != null) {
            fullPath = appPath.append(path);
        }
        return fullPath.getAbsolute();
    }

    /**
     * Recursively delete given path
     *
     * @param path path to delete
     */
    void deleteRecurse(String path) {
        zk.deleteRecurse(getFullPath(path));
    }

    /**
     * Returns the full list of children (file names) in the given path.
     *
     * @param path a path relative to the currently active application
     * @return a list of file names, which is empty (never null) if the path does not exist
     */
    public List<String> getChildren(String path) {
        String fullPath = getFullPath(path);
        if (! zk.exists(fullPath)) return Collections.emptyList();
        return zk.getChildren(fullPath);
    }

    private static Reader reader(String string) {
        return new StringReader(string);
    }

    public void create(String path) {
        if (path != null && !path.startsWith("/")) path = "/" + path;
        try {
            zk.createNode(getFullPath(path));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    Reader getDataReader(String path) {
        final String data = getData(path);
        if (data == null) {
            throw new IllegalArgumentException("No node for " + getFullPath(path) + " exists");
        }
        return reader(data);
    }

}

