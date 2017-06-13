// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import com.yahoo.log.LogLevel;
import com.yahoo.plugin.Plugin;
import com.yahoo.plugin.SystemPropertyConfig;

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
            plugin = (Plugin) pluginClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            log.log(LogLevel.ERROR, pluginClass.getName() + ": load failed: " + e);
            throw new RuntimeException(e);
        }

        String pname = plugin.getPluginName();
        String prefix = Server.APPNAME + "." + pname + ".";
        SystemPropertyConfig config = new SystemPropertyConfig(prefix);
        String enable = config.get("enable", "true");

        if (! enable.equals("true")) {
            log.log(LogLevel.INFO, pname + ": plugin disabled by config");
            return;
        }

        try {
            plugin.initPlugin(config);
            log.log(LogLevel.DEBUG, pname + ": plugin loaded");
        } catch (Exception e) {
            log.log(LogLevel.ERROR, pname + ": init failed", e);
        }
    }
}
