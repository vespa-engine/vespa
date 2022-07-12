// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.Curator;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for providing data from an application subtree in zookeeper.
 * (i.e. /config/v2/tenants/x/session/&lt;session id for an application&gt;/).
 *
 * @author Tony Vaagenes
 */
public class ZKApplication {

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
    private final Path appPath;
    /** The maximum size of a ZooKeeper node */
    private final int maxNodeSize;

    ZKApplication(Curator curator, Path appPath, int maxNodeSize) {
        this.curator = curator;
        this.appPath = appPath;
        this.maxNodeSize = maxNodeSize;
    }

    ZKApplication(Curator curator, Path appPath) {
        this(curator, appPath, 10 * 1024 * 1024);
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
    List<NamedReader> getAllDataFromDirectory(Path path, String fileNameSuffix, boolean recursive) {
        return getAllDataFromDirectory(path, "", fileNameSuffix, recursive);
    }

    /**
     * As above, except
     *
     * @param namePrefix the prefix to prepend to the returned reader names
     */
    private List<NamedReader> getAllDataFromDirectory(Path path, String namePrefix, String fileNameSuffix, boolean recursive) {
        List<NamedReader> result = new ArrayList<>();
        List<String> children = getChildren(path);

        try {
            for (String child : children) {
                if (fileNameSuffix == null || child.endsWith(fileNameSuffix)) {
                    result.add(new NamedReader(namePrefix + child, reader(getData(path.append(child)))));
                }
                if (recursive)
                    result.addAll(getAllDataFromDirectory(path.append(child),
                                                          namePrefix + child + "/", fileNameSuffix, recursive));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Could not retrieve all data from '" + path + "' in zookeeper", e);
        }
    }

    /**
     * Retrieves a node relative to the node of the active application
     *
     * @param path a path relative to the currently active application
     * @return a Reader that can be used to get the data
     */
    Reader getDataReader(Path path) {
        return reader(getData(path));
    }

    NamedReader getNamedReader(String name, Path path) {
        return new NamedReader(name, reader(getData(path)));
    }

    public String getData(Path path) {
        return Utf8.toString(getBytesInternal(getFullPath(path)));
    }

    private byte[] getBytesInternal(Path path) {
        return curator.getData(path)
                      .orElseThrow(() -> new IllegalArgumentException("Could not get data from '" +
                                                                      path + "' in zookeeper"));
    }

    public byte[] getBytes(Path path) {
        return getBytesInternal(getFullPath(path));
    }

    void putData(Path path, String data) {
        byte[] bytes = Utf8.toBytes(data);
        ensureDataIsNotTooLarge(bytes, path);
        try {
            curator.set(getFullPath(path), bytes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not put data to node '" + getFullPath(path) + "' in zookeeper", e);
        }
    }

    private void ensureDataIsNotTooLarge(byte[] toPut, Path path) {
        if (toPut.length >= maxNodeSize) {
            throw new IllegalArgumentException("Error: too much zookeeper data in node: "
                                               + "[" + toPut.length + " bytes] (path " + path + ")");
        }
    }

    /**
     * Checks if the given node exists under path under this active app
     *
     * @param path a zookeeper path
     * @return true if the node exists in the path, false otherwise
     */
    public boolean exists(Path path) {
        return curator.exists(getFullPath(path));
    }

    private Path getFullPath(Path path) {
        Path fullPath = appPath;
        if (path != null) {
            fullPath = appPath.append(path);
        }
        return fullPath;
    }

    /**
     * Recursively delete given path
     *
     * @param path path to delete
     */
    void deleteRecurse(Path path) {
        curator.delete(getFullPath(path));
    }

    /**
     * Returns the full list of children (file names) in the given path.
     *
     * @param path a path relative to the currently active application
     * @return a list of file names, which is empty (never null) if the path does not exist
     */
    public List<String> getChildren(Path path) {
        return curator.getChildren(getFullPath(path));
    }

    private static Reader reader(String string) {
        return new StringReader(string);
    }

    public void create(Path path) {
        try {
            curator.create(getFullPath(path));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e);
        }
    }

}

