// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import java.util.logging.Logger;

import com.yahoo.logserver.Server;
import com.yahoo.plugin.Config;
import com.yahoo.plugin.Plugin;


public class ArchiverPlugin implements Plugin {
    /**
     * Default log archive dir (relative to current directory
     * at startup).
     */
    private static final String DEFAULT_DIR = "logarchive";

    /**
     * Default max file size for archived log files.
     */
    private static final String DEFAULT_MAXFILESIZE = "20971520";

    private static final String DEFAULT_COMPRESSION = "gzip";

    private final Server server = Server.getInstance();
    private static final Logger log = Logger.getLogger(ArchiverPlugin.class.getName());
    private ArchiverHandler archiver;

    /**
     * @return the name of this plugin
     */
    public String getPluginName() {
        return "logarchive";
    }

    /**
     * Initialize the archiver plugin
     * <p>
     * Config keys used:
     * <p>
     * maxfilesize
     * dir            The root of the logarchive, make sure this does
     * <b>not</b> end with a '/' character.
     */
    public void initPlugin(Config config) {

        if (archiver != null) {
            log.finer("ArchivePlugin doubly initialized");
            throw new IllegalStateException("plugin already initialized: "
                                                    + getPluginName());
        }

        // Possible to disable logarchive for testing
        String rootDir = config.get("dir", DEFAULT_DIR);
        int maxFileSize = config.getInt("maxfilesize", DEFAULT_MAXFILESIZE);
        String threadName = config.get("thread", getPluginName());
        String zip = config.get("compression", DEFAULT_COMPRESSION);

        // register log handler and flusher
        archiver = new ArchiverHandler(rootDir, maxFileSize, zip);
        server.registerLogHandler(archiver, threadName);
        server.registerFlusher(archiver);
    }

    /**
     * Shut down the archiver plugin.
     */
    public void shutdownPlugin() {

        if (archiver == null) {
            log.finer("ArchiverPlugin shutdown before initialize");
            throw new IllegalStateException("plugin not initialized: "
                                                    + getPluginName());
        }
        server.unregisterLogHandler(archiver);
        server.unregisterFlusher(archiver);
        archiver.close();
        archiver = null;
    }
}
