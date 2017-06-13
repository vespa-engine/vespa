// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import java.io.IOException;
import java.util.logging.Logger;

import com.yahoo.log.LogLevel;
import com.yahoo.logserver.Server;
import com.yahoo.plugin.Config;
import com.yahoo.plugin.Plugin;

public class ReplicatorPlugin implements Plugin {
    private static final String DEFAULT_PORT = "19083";
    private static final Logger log = Logger.getLogger(ReplicatorPlugin.class.getName());

    private Replicator replicator;
    private final Server server = Server.getInstance();

    public String getPluginName() {
        return "replicator";
    }

    /**
     * Initialize the replicator plugin
     */
    public void initPlugin(Config config) {
        if (replicator != null) {
            throw new IllegalStateException(
                    "plugin already initialized: " + getPluginName());
        }
        int listenPort = config.getInt("port", DEFAULT_PORT);
        String threadName = config.get("thread", getPluginName());
        try {
            replicator = new Replicator(listenPort);
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "init failed: " + e);
            return;
        }
        server.registerLogHandler(replicator, threadName);
    }

    /**
     * Shut down the replicator plugin.
     */
    public void shutdownPlugin() {

        if (replicator == null) {
            throw new IllegalStateException(
                    "plugin not initialized: " + getPluginName());
        }
        server.unregisterLogHandler(replicator);
        replicator = null;
    }

}
