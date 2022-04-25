// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.List;
import java.util.Optional;

/**
 * A client to a config source, which could be an RPC config server or some other backing for
 * getting config.
 *
 * @author hmusum
 */
interface ConfigSourceClient {

    Optional<RawConfig> getConfig(RawConfig input, JRTServerConfigRequest request);

    void shutdown();

    void shutdownSourceConnections();

    String getActiveSourceConnection();

    List<String> getSourceConnections();

    DelayedResponses delayedResponses();

    MemoryCache memoryCache();

}
