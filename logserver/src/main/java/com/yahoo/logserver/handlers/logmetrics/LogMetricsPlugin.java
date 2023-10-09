// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 */
package com.yahoo.logserver.handlers.logmetrics;

import java.util.logging.Logger;

import com.yahoo.logserver.Server;
import com.yahoo.plugin.Config;
import com.yahoo.plugin.Plugin;


public class LogMetricsPlugin implements Plugin {
    private static final Logger log = Logger.getLogger(LogMetricsPlugin.class.getName());
    private LogMetricsHandler logMetricsHandler;
    private final Server server = Server.getInstance();

    public String getPluginName() {
        return "logmetrics";
    }

    /**
     * Initialize the logmetrics plugin
     *
     * @param config Plugin config object, keys used:
     *               <code>thread</code> - name of handler thread this plugin runs in
     */
    public void initPlugin(Config config) {
        if (logMetricsHandler != null) {
            log.finer("LogMetricsPlugin doubly initialized");
            throw new IllegalStateException(
                    "plugin already initialized: " + getPluginName());
        }
        String threadName = config.get("thread", getPluginName());
        logMetricsHandler = new LogMetricsHandler();
        server.registerLogHandler(logMetricsHandler, threadName);
    }

    /**
     * Shut down the logmetrics plugin.
     */
    public void shutdownPlugin() {
        if (logMetricsHandler == null) {
            log.finer("LogMetricsPlugin shutdown before initialize");
            throw new IllegalStateException("plugin not initialized: " +
                                                    getPluginName());
        }
        server.unregisterLogHandler(logMetricsHandler);
        logMetricsHandler = null;
    }
}
