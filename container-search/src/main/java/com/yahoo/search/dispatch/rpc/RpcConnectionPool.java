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


    /** Will return a list of items that need a delayed close when updating node set. */
    default Collection<? extends AutoCloseable> updateNodes(DispatchNodesConfig nodesConfig) { return List.of(); }

    /** Shuts down all connections in the pool, and the underlying RPC client. */
    @Override
    void close();

}
