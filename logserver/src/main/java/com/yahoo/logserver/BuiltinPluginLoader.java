// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import java.util.logging.Level;
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
        log.log(Level.FINE, "starting to load builtin plugins");

        loadFromClass(ArchiverPlugin.class);
        loadFromClass(LogMetricsPlugin.class);

        log.log(Level.FINE, "done loading builtin plugins");
    }

}
