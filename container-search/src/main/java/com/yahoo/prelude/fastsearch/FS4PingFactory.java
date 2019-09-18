// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;

import java.util.concurrent.Callable;

/**
 * FS4PingFactory constructs {@link Pinger} objects that communicate with
 * content nodes or dispatchers over the fnet/FS4 protocol
 *
 * @author ollivir
 */
public class FS4PingFactory implements PingFactory {
    private final FS4ResourcePool fs4ResourcePool;

    public FS4PingFactory(FS4ResourcePool fs4ResourcePool) {
        this.fs4ResourcePool = fs4ResourcePool;
    }

    @Override
    public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
        return new Pinger(node, monitor, fs4ResourcePool);
    }
}
