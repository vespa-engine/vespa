// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.common.collect.ImmutableMap;
import com.yahoo.compress.CompressionType;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.rpc.RpcFillInvoker.GetDocsumsResponseReceiver;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author ollivir
 */
public class RpcSearchInvokerTest {
    @Test
    public void testProtobufSerialization() throws IOException {
        var compressionTypeHolder = new AtomicReference<CompressionType>();
        var payloadHolder = new AtomicReference<byte[]>();
        var lengthHolder = new AtomicInteger();
        var mockClient = parameterCollectorClient(compressionTypeHolder, payloadHolder, lengthHolder);
        var mockPool = new RpcResourcePool(mockClient, ImmutableMap.of(7, () -> {}));
        @SuppressWarnings("resource")
        var invoker = new RpcSearchInvoker(mockSearcher(), new Node(7, "seven", 77, 1), mockPool);

        Query q = new Query("search/?query=test&hits=10&offset=3");
        invoker.sendSearchRequest(q, null);

        var bytes = mockPool.compressor().decompress(payloadHolder.get(), compressionTypeHolder.get(), lengthHolder.get());
//        var request = SearchProtocol.SearchRequest.newBuilder().mergeFrom(bytes).build();
//
//        assertThat(request.getHits(), equalTo(10));
//        assertThat(request.getOffset(), equalTo(3));
//        assertThat(request.getQueryTreeBlob().size(), greaterThan(0));
    }

    private Client parameterCollectorClient(AtomicReference<CompressionType> compressionTypeHolder, AtomicReference<byte[]> payloadHolder,
            AtomicInteger lengthHolder) {
        return new Client() {
            @Override
            public void search(NodeConnection node, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                    RpcSearchInvoker responseReceiver, double timeoutSeconds) {
                compressionTypeHolder.set(compression);
                payloadHolder.set(compressedPayload);
                lengthHolder.set(uncompressedLength);
            }

            @Override
            public void getDocsums(List<FastHit> hits, NodeConnection node, CompressionType compression, int uncompressedLength,
                    byte[] compressedSlime, GetDocsumsResponseReceiver responseReceiver, double timeoutSeconds) {
                fail("Unexpected call");
            }

            @Override
            public NodeConnection createConnection(String hostname, int port) {
                fail("Unexpected call");
                return null;
            }
        };
    }

    private VespaBackEndSearcher mockSearcher() {
        return new VespaBackEndSearcher() {
            @Override
            protected Result doSearch2(Query query, QueryPacket queryPacket, Execution execution) {
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
