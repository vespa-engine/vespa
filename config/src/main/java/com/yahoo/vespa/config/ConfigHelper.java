// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;

/**
 * Helper class for config applications (currently ConfigManager and ConfigProxy).
 *
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
 */
public class ConfigHelper {
    private final JRTConnectionPool jrtConnectionPool;
    private final TimingValues timingValues;

    /**
     * @param configSourceSet  The set of config sources for this helper.
     */
    public ConfigHelper(ConfigSourceSet configSourceSet) {
        this(configSourceSet, new TimingValues());
    }

    /**
     * @param configSourceSet  The set of config sources for this helper.
     * @param timingValues values for timeouts and delays, see {@link TimingValues}
     */
    public ConfigHelper(ConfigSourceSet configSourceSet, TimingValues timingValues) {
        jrtConnectionPool = new JRTConnectionPool(configSourceSet);
        this.timingValues = timingValues;
    }

    /**
     * @return the config sources (remote servers and/or proxies) in this helper's connection pool.
     */
    public ConfigSourceSet getConfigSourceSet() {
        return jrtConnectionPool.getSourceSet();
    }

    /**
     * @return the connection pool for this config helper.
     */
    public JRTConnectionPool getConnectionPool() {
        return jrtConnectionPool;
    }

    /**
     * @return the timing values for this config helper.
     */
    public TimingValues getTimingValues() {
        return timingValues;
    }
}
