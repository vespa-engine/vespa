package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.prelude.Pong;
import com.yahoo.search.cluster.ClusterMonitor;

import java.util.concurrent.Callable;

public interface PingFactory {
    public abstract Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor);

}
