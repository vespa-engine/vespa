// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;

public class RpcPingFactory implements PingFactory {

    private final RpcConnectionPool rpcResourcePool;
    private final Compressor compressor = new Compressor(CompressionType.LZ4, 5, 0.95, 512);

    public RpcPingFactory(RpcConnectionPool rpcResourcePool) {
        this.rpcResourcePool = rpcResourcePool;
    }

    @Override
    public Pinger createPinger(Node node, ClusterMonitor<Node> monitor, PongHandler pongHandler) {
        return new RpcPing(node, monitor, rpcResourcePool, pongHandler, compressor);
    }

}
