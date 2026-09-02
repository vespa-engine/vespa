// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.Collection;
import java.util.List;

/**
 * Interface for getting a connection given a node id.
 *
 * @author balderersheim
 */
public interface RpcConnectionPool extends AutoCloseable {

    /** Returns a connection to the given node id. */
    Client.NodeConnection getConnection(int nodeId);

    /**
     * Returns an immutable view of the current node set, for use by a single generation of invokers.
     * Resolving connections through such a view keeps nodes removed by a later node set update
     * resolvable until the generation's queries have drained, while the underlying connections
     * are kept open by the delayed close of updateNodes.
     */
    default RpcConnectionPool snapshot() { return this; }

    /** Will return a list of items that need a delayed close when updating node set. */
    default Collection<? extends AutoCloseable> updateNodes(DispatchNodesConfig nodesConfig) { return List.of(); }

    /** Shuts down all connections in the pool, and the underlying RPC client. */
    @Override
    void close();

    default Collection<Integer> knownNodeIds() { return List.of(); }

}
