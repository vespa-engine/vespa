// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.search.dispatch.rpc.Client.ResponseReceiver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ovirtanen
 */
public class MockRpcResourcePoolBuilder {

    private Map<Integer, NodeConnection> nodeConnections = new HashMap<>();

    public MockRpcResourcePoolBuilder connection(int distKey) {
        nodeConnections.put(distKey, new MockNodeConnection(distKey));
        return this;
    }

    public RpcResourcePool build() {
        return new RpcResourcePool(nodeConnections);
    }

    private static class MockNodeConnection implements NodeConnection {
        private final int key;

        public MockNodeConnection(int key) {
            this.key = key;
        }

        @Override
        public void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                ResponseReceiver responseReceiver, double timeoutSeconds) {
            responseReceiver.receive(Client.ResponseOrError.fromError("request('"+rpcMethod+"', ..) attempted for node " + key));
        }

        @Override
        public void close() {
        }
    }

}
