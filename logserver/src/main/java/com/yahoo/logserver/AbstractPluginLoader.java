// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import java.util.logging.Level;
import com.yahoo.plugin.Plugin;
import com.yahoo.plugin.SystemPropertyConfig;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/**
 * TODO: describe class
 *
 * @author Stig Bakken
 */
public abstract class AbstractPluginLoader implements PluginLoader {

    private static final Logger log = Logger.getLogger(AbstractPluginLoader.class.getName());

    public abstract void loadPlugins();

    protected void loadFromClass(Class<? extends Plugin> pluginClass) {
        Plugin plugin;
        try {
            plugin = pluginClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            log.log(Level.SEVERE, pluginClass.getName() + ": load failed: " + e);
            throw new RuntimeException(e);
        }

        String pname = plugin.getPluginName();
        String prefix = Server.APPNAME + "." + pname + ".";
        SystemPropertyConfig config = new SystemPropertyConfig(prefix);
        String enable = config.get("enable", "true");

        if (! enable.equals("true")) {
            log.log(Level.INFO, pname + ": plugin disabled by config");
            return;
        }

        try {
            plugin.initPlugin(config);
            log.log(Level.FINE, pname + ": plugin loaded");
        } catch (Exception e) {
            log.log(Level.SEVERE, pname + ": init failed", e);
        }
    }
}
