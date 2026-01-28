// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Map;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public interface Subscriber {

    long waitNextGeneration(boolean isInitializing);
    long generation();

    boolean configChanged();
    Map<ConfigKey<ConfigInstance>, ConfigInstance> config();

    void close();

    /**
     * Whether the last generation should only be applied on restart, not immediately.
     * Once this is set it will not be unset, as no future generation should be applied
     * once there is a generation which require restart.
     */
    boolean applyOnRestart();
}
