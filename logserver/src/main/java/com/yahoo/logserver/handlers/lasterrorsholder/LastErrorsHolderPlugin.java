// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.lasterrorsholder;

import com.yahoo.log.LogLevel;
import com.yahoo.logserver.Server;
import com.yahoo.plugin.Config;
import com.yahoo.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Logger;

public class LastErrorsHolderPlugin implements Plugin {
    private static final String DEFAULT_PORT = "19082";
    private static final Logger log = Logger.getLogger(LastErrorsHolderPlugin.class.getName());
    private LastErrorsHolder lastErrorsHolder;
    private final Server server = Server.getInstance();

    public String getPluginName() {
        return "last-errors-holder";
    }

    /**
     * Initialize the plugin
     */
    public void initPlugin(Config config) {
        if (lastErrorsHolder != null) {
            throw new IllegalStateException("plugin already initialized: " + getPluginName());
        }
        int listenPort = config.getInt("port", DEFAULT_PORT);
        String threadName = config.get("thread", getPluginName());
        try {
            lastErrorsHolder = new LastErrorsHolder(listenPort);
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "init failed: " + e);
            return;
        }
        server.registerLogHandler(lastErrorsHolder, threadName);
    }

    /**
     * Shut down the plugin.
     */
    public void shutdownPlugin() {

        if (lastErrorsHolder == null) {
            throw new IllegalStateException("plugin not initialized: " + getPluginName());
        }
        server.unregisterLogHandler(lastErrorsHolder);
        lastErrorsHolder = null;
    }
}
