// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.plugin;

/**
 * This interface specifies an API for configuring runtime-loadable
 * server plugins.
 *
 * @author Stig Bakken
 */
public abstract class Config {
    /**
     * @return a config value for the specified key
     */
    public abstract String get(String key, String defaultValue);

    /**
     * @return a config value as an integer
     */
    public int getInt(String key, String defaultValue) {
        return Integer.parseInt(get(key, defaultValue));
    }
}
