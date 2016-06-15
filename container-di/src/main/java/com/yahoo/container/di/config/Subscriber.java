// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Map;

/**
 * @author tonytv
 * @author gjoranv
 */
public interface Subscriber {
    long waitNextGeneration();
    long generation();

    boolean configChanged();
    Map<ConfigKey<ConfigInstance>, ConfigInstance> config();

    void close();
}
