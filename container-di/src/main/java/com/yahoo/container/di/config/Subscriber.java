// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Map;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public interface Subscriber {

    long waitNextGeneration();
    long generation();
    boolean internalRedeploy();

    boolean configChanged();
    Map<ConfigKey<ConfigInstance>, ConfigInstance> config();

    void close();

}
