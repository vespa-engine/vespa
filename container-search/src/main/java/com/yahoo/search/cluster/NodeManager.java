// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import java.util.concurrent.Executor;

/**
 * Must be implemented by a node collection which wants
 * it's node state monitored by a ClusterMonitor
 *
 * @author  bratseth
 */
public interface NodeManager<T> {

    /** Called when a failed node is working (ready for production) again */
    void working(T node);

    /** Called when a working node fails */
    void failed(T node);

    /** 
     * Called when a node should be pinged. 
     * This *must* lead to either a call to NodeMonitor.failed or NodeMonitor.responded
     * @deprecated Use ping(ClusterMonitor<T> clusterMonitor, T node, Executor executor) instead.
     */
    @Deprecated
    default void ping(T node, Executor executor) {
        throw new IllegalStateException("If you have not overrriden ping(ClusterMonitor<T> clusterMonitor, T node, Executor executor), you should at least have overriden this method.");
    }

    /**
     * Called when a node should be pinged.
     * This *must* lead to either a call to ClusterMonitor.failed or ClusterMonitor.responded
     */
    default void ping(ClusterMonitor<T> clusterMonitor, T node, Executor executor) {
        ping(node, executor);
    }
    
    /** Called right after a ping has been issued to each node. This default implementation does nothing. */
    default void pingIterationCompleted() {}

}
