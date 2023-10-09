// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

/**
 * This interface specifies an API for implementing logserver plugin
 * loaders.  A plugin loader has two basic tasks: to load or unload
 * all of its knows plugins.  In addition, if a plugin loader's
 * canReload() method returns <code>true</code>, plugins may be loaded
 * again after they are unloaded.
 * <p> Plugins loaded through such reload-capable plugin loaders may
 * be upgraded without restarting the server.
 *
 * @author Stig Bakken
 */
public interface PluginLoader {
    /**
     * Load all plugins known to this loader.
     */
    void loadPlugins();
}
