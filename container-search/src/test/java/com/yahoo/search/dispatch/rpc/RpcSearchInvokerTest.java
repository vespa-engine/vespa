// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.common.collect.ImmutableMap;
import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.search.dispatch.searchcluster.MockSearchCluster.createDispatchConfig;
import static com.yahoo.search.dispatch.searchcluster.MockSearchCluster.createNodesConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ollivir
 */
public class RpcSearchInvokerTest {

    private final CompressService compressor = new CompressService();
    @Test
    void testProtobufSerialization() throws IOException {
        var compressionTypeHolder = new AtomicReference<CompressionType>();
        var payloadHolder = new AtomicReference<byte[]>();
        var lengthHolder = new AtomicInteger();
        var mockClient = parameterCollectorClient(compressionTypeHolder, payloadHolder, lengthHolder);
        var mockPool = new RpcResourcePool(ImmutableMap.of(7, mockClient.createConnection("foo", 123)));
        var invoker = new RpcSearchInvoker(mockSearcher(), compressor, new Node("test", 7, "seven", 1), mockPool, 1000);

        Query q = new Query("search/?query=test&hits=10&offset=3");
        RpcSearchInvoker.RpcContext context = (RpcSearchInvoker.RpcContext) invoker.sendSearchRequest(q, null);
        assertEquals(lengthHolder.get(), context.compressedPayload.uncompressedSize());
        assertSame(context.compressedPayload.data(), payloadHolder.get());

        var bytes = compressor.compressor().decompress(payloadHolder.get(), compressionTypeHolder.get(), lengthHolder.get());
        var request = SearchProtocol.SearchRequest.newBuilder().mergeFrom(bytes).build();

        assertEquals(10, request.getHits());
        assertEquals(3, request.getOffset());
        assertTrue(request.getQueryTreeBlob().size() > 0);

        var invoker2 = new RpcSearchInvoker(mockSearcher(), compressor, new Node("test", 8, "eight", 1), mockPool, 1000);
        RpcSearchInvoker.RpcContext context2 = (RpcSearchInvoker.RpcContext) invoker2.sendSearchRequest(q, context);
        assertSame(context, context2);
        assertEquals(lengthHolder.get(), context.compressedPayload.uncompressedSize());
        assertSame(context.compressedPayload.data(), payloadHolder.get());
    }

    @Test
    void testProtobufSerializationWithMaxHitsSet() throws IOException {
        int maxHits = 5;
        var compressionTypeHolder = new AtomicReference<CompressionType>();
        var payloadHolder = new AtomicReference<byte[]>();
        var lengthHolder = new AtomicInteger();
        var mockClient = parameterCollectorClient(compressionTypeHolder, payloadHolder, lengthHolder);
        var mockPool = new RpcResourcePool(ImmutableMap.of(7, mockClient.createConnection("foo", 123)));
        var invoker = new RpcSearchInvoker(mockSearcher(), compressor, new Node("test", 7, "seven", 1), mockPool, maxHits);

        Query q = new Query("search/?query=test&hits=10&offset=3");
        invoker.sendSearchRequest(q, null);

        var bytes = compressor.compressor().decompress(payloadHolder.get(), compressionTypeHolder.get(), lengthHolder.get());
        var request = SearchProtocol.SearchRequest.newBuilder().mergeFrom(bytes).build();

        assertEquals(maxHits, request.getHits());
    }

    void verifyConnections(RpcResourcePool rpcResourcePool, int numGroups, int nodesPerGroup, int expectNeedCloseCount) {
        var toClose = rpcResourcePool.updateNodes(createNodesConfig(numGroups,nodesPerGroup));
        assertEquals(expectNeedCloseCount, toClose.size());
        toClose.forEach(item -> {
            try {
                item.close();
            } catch (Exception e) {}
        });
        for (int nodeId = 0; nodeId < numGroups*nodesPerGroup; nodeId++) {
            assertTrue(rpcResourcePool.getConnection(nodeId) instanceof RpcClient.RpcNodeConnection);
        }
        assertNull(rpcResourcePool.getConnection(numGroups*nodesPerGroup));
    }

    @Test
    void testUpdateOfRpcResourcePool() {
        RpcResourcePool rpcResourcePool = new RpcResourcePool(createDispatchConfig(), createNodesConfig(0, 0));
        verifyConnections(rpcResourcePool, 3,3, 0);
        verifyConnections(rpcResourcePool, 4,4, 6);
        verifyConnections(rpcResourcePool, 2,2, 14);
    }

    private Client parameterCollectorClient(AtomicReference<CompressionType> compressionTypeHolder, AtomicReference<byte[]> payloadHolder,
            AtomicInteger lengthHolder) {
        return new Client() {
            @Override
            public void close() { }
            @Override
            public NodeConnection createConnection(String hostname, int port) {
                return new NodeConnection() {
                    @Override
                    public void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                            ResponseReceiver responseReceiver, double timeoutSeconds) {
                        compressionTypeHolder.set(compression);
                        payloadHolder.set(compressedPayload);
                        lengthHolder.set(uncompressedLength);
                    }

                    @Override
                    public void close() { }
                };
            }
        };
    }

    private VespaBackEndSearcher mockSearcher() {
        return new VespaBackEndSearcher() {
            @Override
            protected Result doSearch2(Query query, Execution execution) {
                fail("Unexpected call");
                return null;
            }

            @Override
            protected void doPartialFill(Result result, String summaryClass) {
                fail("Unexpected call");
            }
        };
    }

}
