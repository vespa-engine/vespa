// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import com.yahoo.container.StatisticsConfig;

/**
 * Interface used for registering statistics values and counters for logging.
 *
 * @author steinar
 * @author Tony Vaagenes
 */
public interface Statistics {
    /**
     * Add a new handle to be scheduled for periodic logging. If a handle
     * already exists with the same name, it will be cancelled and removed from
     * the internal state of this object.
     */
    void register(Handle h);

    /**
     * Remove a named handler from the set of working handlers.
     */
    void remove(String name);

    /**
     * Get current config used. This may be a null reference, depending on how
     * the instance was constructed.
     */
    StatisticsConfig getConfig();

    /**
     * Purges all cancelled Handles from internal Map and Timer.
     *
     * @return return value from java.util.Timer.purge()
     */
    int purge();

    /** A null implementation which ignores all calls and returns the default config */
    public static Statistics nullImplementation=new NullImplementation();

    static class NullImplementation implements Statistics {

        private StatisticsConfig nullConfig=new StatisticsConfig(new StatisticsConfig.Builder());

        @Override
        public void register(Handle h) { }

        @Override
        public void remove(String name) { }

        @Override
        public StatisticsConfig getConfig() { return nullConfig; }

        @Override
        public int purge() { return 0; }

    }

}
