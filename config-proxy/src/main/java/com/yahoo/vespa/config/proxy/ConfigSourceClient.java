// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.List;

/**
 * A client to a config source, which could be an RPC config server or some other backing for
 * getting config.
 *
 * @author hmusum
 * @since 5.1.9
 */
public abstract class ConfigSourceClient {
    static ConfigSourceClient createClient(ConfigSource source,
                                           ClientUpdater clientUpdater,
                                           MemoryCache memoryCache,
                                           TimingValues timingValues,
                                           DelayedResponses delayedResponses) {
        if (source instanceof ConfigSourceSet) {
            return new RpcConfigSourceClient((ConfigSourceSet) source, clientUpdater, memoryCache, timingValues, delayedResponses);
        } else if (source instanceof MapBackedConfigSource) {
            return (ConfigSourceClient) source;
        } else {
            throw new IllegalArgumentException("config source of type " + source.getClass().getName() + " is not allowed");
        }
    }

    abstract RawConfig getConfig(RawConfig input, JRTServerConfigRequest request);

    abstract void cancel();

    // TODO Should only be in rpc config source client
    abstract void shutdownSourceConnections();

    abstract String getActiveSourceConnection();

    abstract List<String> getSourceConnections();
}