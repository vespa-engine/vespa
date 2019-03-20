// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import com.yahoo.log.LogLevel;
import com.yahoo.logserver.handlers.archive.ArchiverPlugin;
import com.yahoo.logserver.handlers.logmetrics.LogMetricsPlugin;

import java.util.logging.Logger;

/**
 * Load a set of builtin plugins
 *
 * @author Stig Bakken
 */
public class BuiltinPluginLoader extends AbstractPluginLoader {

    private static final Logger log = Logger.getLogger(BuiltinPluginLoader.class.getName());

    public void loadPlugins() {
        log.log(LogLevel.DEBUG, "starting to load builtin plugins");

        loadFromClass(ArchiverPlugin.class);
        loadFromClass(LogMetricsPlugin.class);

        log.log(LogLevel.DEBUG, "done loading builtin plugins");
    }

}
