// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.plugin;

/**
 * This class implements plugin config through system properties.
 * Each plugin typically has its own system property prefix, such as
 * "logserver.archiver.".  A request for the config key "foo" will
 * then return the contents of the "logserver.archiver.foo" system
 * property.
 *
 * @author Stig Bakken
 */
public class SystemPropertyConfig extends Config {

    private final String prefix;

    /**
     * @param prefix prefix string prepended to config keys
     *               as they are looked up as system properties.
     */
    public SystemPropertyConfig(String prefix) {
        this.prefix = prefix;
    }

    /** Returns a config value for the specified key */
    public String get(String key, String defaultValue) {
        return System.getProperty(prefix + key, defaultValue);
    }

    public String toString() {
        return "Prefix=" + prefix;
    }

}
