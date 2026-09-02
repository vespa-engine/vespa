// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.search.dispatch.rpc.RpcClient.RpcNodeConnection;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author andreer
 */
public class RpcResourcePoolTest {

    @Test
    void snapshotKeepsRemovedNodesResolvableWhileItsGenerationDrains() {
        var pool = new RpcResourcePool(dispatchConfig(), nodesConfig(0, 1));
        var oldGeneration = pool.snapshot();

        pool.updateNodes(nodesConfig(0)); // Node 1 removed; its connections stay open for delayed close

        // Queries in flight against the old topology must still be able to reach node 1,
        // which is still up: only the config no longer includes it.
        assertNotNull(oldGeneration.getConnection(1));
        // New generations must not see it.
        assertNull(pool.snapshot().getConnection(1));
        assertEquals(Set.of(0), Set.copyOf(pool.snapshot().knownNodeIds()));
        pool.close();
    }

    @Test
    void reAddedNodeGetsFreshConnectionsIndependentOfTheOldGeneration() {
        var pool = new RpcResourcePool(dispatchConfig(), nodesConfig(0, 1));
        var oldGeneration = pool.snapshot();
        var oldConnection = oldGeneration.getConnection(1);

        pool.updateNodes(nodesConfig(0));    // remove node 1
        pool.updateNodes(nodesConfig(0, 1)); // then add it back

        var newConnection = pool.snapshot().getConnection(1);
        assertNotNull(newConnection);
        assertNotSame(oldConnection, newConnection);
        assertNotNull(oldGeneration.getConnection(1), "the old generation keeps its own view");
        pool.close();
    }

    @Test
    void nodeChangingAddressIsReplacedInNewSnapshotsOnly() {
        var pool = new RpcResourcePool(dispatchConfig(), nodesConfig(0, 1));
        var oldGeneration = pool.snapshot();

        pool.updateNodes(new DispatchNodesConfig.Builder()
                                 .node(node(0))
                                 .node(new DispatchNodesConfig.Node.Builder()
                                               .key(1).host("host-b1").port(19101).group(0))
                                 .build());

        assertEquals("host-b1", ((RpcNodeConnection) pool.snapshot().getConnection(1)).getHostname());
        assertEquals("host1", ((RpcNodeConnection) oldGeneration.getConnection(1)).getHostname());
        pool.close();
    }

    private DispatchConfig dispatchConfig() {
        return new DispatchConfig.Builder().numJrtConnectionsPerNode(1).numJrtTransportThreads(1).build();
    }

    private DispatchNodesConfig nodesConfig(int... keys) {
        var builder = new DispatchNodesConfig.Builder();
        for (int key : keys) builder.node(node(key));
        return builder.build();
    }

    private DispatchNodesConfig.Node.Builder node(int key) {
        return new DispatchNodesConfig.Node.Builder().key(key).host("host" + key).port(19100 + key).group(0);
    }

}
