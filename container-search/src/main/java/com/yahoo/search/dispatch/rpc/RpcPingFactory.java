package com.yahoo.search.dispatch.rpc;

import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;

public class RpcPingFactory implements PingFactory {
    private final RpcResourcePool rpcResourcePool;
    public RpcPingFactory(RpcResourcePool rpcResourcePool) {
        this.rpcResourcePool = rpcResourcePool;
    }
    @Override
    public Pinger createPinger(Node node, ClusterMonitor<Node> monitor, PongHandler pongHandler) {
        return new RpcPing(node, monitor, rpcResourcePool, pongHandler);
    }
}
