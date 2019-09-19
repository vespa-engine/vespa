// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.yolean.Exceptions;

import java.util.concurrent.Callable;

/**
 * @author bratseth
 * @author ollivir
 */
public class Pinger implements Callable<Pong> {
    private final Node node;
    private final ClusterMonitor<Node> clusterMonitor;
    private final FS4ResourcePool fs4ResourcePool;

    public Pinger(Node node, ClusterMonitor<Node> clusterMonitor, FS4ResourcePool fs4ResourcePool) {
        this.node = node;
        this.clusterMonitor = clusterMonitor;
        this.fs4ResourcePool = fs4ResourcePool;
    }

    public Pong call() {
        try {
            Pong pong = FastSearcher.ping(new Ping(clusterMonitor.getConfiguration().getRequestTimeout()),
                                          fs4ResourcePool.getBackend(node.hostname(), node.fs4port()), node.toString());
            return pong;
        } catch (RuntimeException e) {
            return new Pong(ErrorMessage.createBackendCommunicationError("Exception when pinging " + node + ": "
                            + Exceptions.toMessageString(e)));
        }
    }

}
