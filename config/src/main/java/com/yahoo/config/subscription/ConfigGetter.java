// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;

/**
 * This is a simple config getter that retrieves a config with a given class and configId through a
 * simple method call. No subscription is retained when the config has been returned to the client.
 *
 * This class is mainly targeted to unit tests that do not want the extra complexity incurred by setting
 * up their own subscriber. Another use-case is clients that get config, do a task, and exit, e.g.
 * command-line tools.
 *
 * @author gjoranv
 * @deprecated Use config builders where possible
 */
@Deprecated
public class ConfigGetter<T extends ConfigInstance> {

    private final Class<T> clazz;

    /**
     * Creates a ConfigGetter for class <code>clazz</code>
     *
     * @param clazz a config class
     */
    public ConfigGetter(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Returns an instance of the config class specified in the constructor.
     *
     * @param configId a config id to use when getting the config
     * @return an instance of a config class
     */
    public synchronized T getConfig(String configId) {
        try (ConfigSubscriber subscriber = new ConfigSubscriber()) {
            ConfigHandle<T> handle = subscriber.subscribe(clazz, configId);
            subscriber.nextConfig(true);
            return handle.getConfig();
        }
    }

    /**
     * Creates a ConfigGetter instance and returns an instance of the config class <code>c</code>.
     *
     * @param c        a config class
     * @param configId a config id to use when getting the config
     * @return an instance of a config class
     */
    public static <T extends ConfigInstance> T getConfig(Class<T> c, String configId) {
        ConfigGetter<T> getter = new ConfigGetter<>(c);
        return getter.getConfig(configId);
    }

}
