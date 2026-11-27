// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.PartialSummaryHandler;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Slime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialSummaryFillTest {

    private static final CompressService compressor = new CompressService();
    private static final DocumentDatabase documentDb = new DocumentDatabase(new Schema.Builder("product")
            .add(new DocumentSummary.Builder("collapse_sku")
                    .addField("sku", "string").addField("ocid", "string").build())
            .add(new DocumentSummary.Builder("product_summary")
                    .addField("sku", "string").addField("ocid", "string")
                    .addField("title", "string").addField("price", "string").build())
            .build());

    @ParameterizedTest
    @EnumSource(RpcProtobufFillInvoker.DecodePolicy.class)
    void finalFillFetchesOnlyMissingFieldsAndPreservesPreviousValues(RpcProtobufFillInvoker.DecodePolicy policy) {
        var node = new RecordingNode();
        var connections = Map.<Integer, Client.NodeConnection>of(0, node);
        var result = resultWithHits(2);
        invoker(connections, policy).fill(result, "collapse_sku");
        assertEquals("collapse_sku", node.requests.get(0).getSummaryClass());
        assertEquals(List.of(), node.requests.get(0).getFieldsList());
        assertEquals(2, node.requests.get(0).getGlobalIdsCount());

        // Collapse leaves one surviving hit for the final presentation fill.
        result.hits().trim(0, 1);
        invoker(connections, policy).fill(result, PartialSummaryHandler.PRESENTATION);
        assertNull(result.hits().getError());
        assertEquals(2, node.requests.size());
        var request = node.requests.get(1);
        assertEquals("product_summary", request.getSummaryClass());
        assertEquals(List.of("price", "title"), request.getFieldsList());
        assertEquals(1, request.getGlobalIdsCount());
        var hit = result.hits().get(0);
        assertEquals("sku-value", hit.getField("sku"));
        assertEquals("ocid-value", hit.getField("ocid"));
        assertEquals("Product title", hit.getField("title"));
        assertEquals("42", hit.getField("price"));
        assertTrue(hit.isFilled("product_summary"));

        invoker(connections, policy).fill(result, "product_summary");
        assertEquals(2, node.requests.size(), "A completed summary must not be fetched again");
    }

    @Test
    void mixedFillStatesStillFetchFieldsNeededByAnyHit() {
        var node = new RecordingNode();
        var connections = Map.<Integer, Client.NodeConnection>of(0, node);
        var result = resultWithHits(1);
        invoker(connections, RpcProtobufFillInvoker.DecodePolicy.EAGER).fill(result, "collapse_sku");
        result.hits().add(newHit(1, result.getQuery()));

        invoker(connections, RpcProtobufFillInvoker.DecodePolicy.EAGER).fill(result, "product_summary");
        assertNull(result.hits().getError());
        assertEquals(List.of(), node.requests.get(1).getFieldsList());
        assertEquals(2, node.requests.get(1).getGlobalIdsCount());
        assertEquals("refetched-sku", result.hits().get(1).getField("sku"));
        assertEquals("Product title", result.hits().get(1).getField("title"));
        assertTrue(result.hits().get(1).isFilled("product_summary"));
    }

    @Test
    void retriesKeepTheMissingFieldRestriction() {
        var original = new RecordingNode();
        var replica = new RecordingNode();
        var connections = Map.<Integer, Client.NodeConnection>of(0, original, 1, replica);
        var result = resultWithHits(1);
        invoker(connections, RpcProtobufFillInvoker.DecodePolicy.EAGER).fill(result, "collapse_sku");
        original.missing = true;

        invoker(connections, RpcProtobufFillInvoker.DecodePolicy.EAGER).fill(result, "product_summary");
        assertNull(result.hits().getError());
        assertEquals(List.of("price", "title"), original.requests.get(1).getFieldsList());
        assertEquals(1, replica.requests.size());
        assertEquals(List.of("price", "title"), replica.requests.get(0).getFieldsList());
        assertEquals("sku-value", result.hits().get(0).getField("sku"));
        assertEquals("Product title", result.hits().get(0).getField("title"));
        assertTrue(result.hits().get(0).isFilled("product_summary"));
    }

    private static RpcProtobufFillInvoker invoker(Map<Integer, Client.NodeConnection> connections,
                                                 RpcProtobufFillInvoker.DecodePolicy policy) {
        return new RpcProtobufFillInvoker(new RpcResourcePool(new HashMap<>(connections)), compressor,
                                          documentDb, "container.0", policy, false);
    }

    private static Result resultWithHits(int count) {
        var query = new Query("?query=test&presentation.summary=product_summary&timeout=60s");
        var result = new Result(query);
        for (int i = 0; i < count; ++i) result.hits().add(newHit(i, query));
        return result;
    }

    private static FastHit newHit(int id, Query query) {
        byte[] globalId = new byte[12];
        globalId[0] = (byte) id;
        var hit = new FastHit(globalId, 1.0, OptionalInt.empty(), 0, 0);
        hit.setQuery(query);
        hit.setFillable();
        return hit;
    }

    private static class RecordingNode implements Client.NodeConnection {
        final List<SearchProtocol.DocsumRequest> requests = new ArrayList<>();
        boolean missing = false;

        @Override
        public void request(String rpcMethod, CompressionType compression, int uncompressedLength,
                            byte[] compressedPayload, Client.ResponseReceiver receiver, double timeoutSeconds) {
            try {
                byte[] payload = compressor.compressor().decompress(compressedPayload, compression, uncompressedLength);
                var request = SearchProtocol.DocsumRequest.parseFrom(payload);
                requests.add(request);
                Map<String, String> values = request.getSummaryClass().equals("collapse_sku")
                        ? Map.of("sku", "sku-value", "ocid", "ocid-value")
                        : Map.of("sku", "refetched-sku", "ocid", "refetched-ocid", "title", "Product title", "price", "42");
                var slime = new Slime();
                var docsums = slime.setObject().setArray("docsums");
                for (int i = 0; i < request.getGlobalIdsCount(); ++i) {
                    var entry = docsums.addObject();
                    if (missing) continue;
                    var docsum = entry.setObject("docsum");
                    values.forEach((field, value) -> {
                        if (request.getFieldsCount() == 0 || request.getFieldsList().contains(field)) {
                            docsum.setString(field, value);
                        }
                    });
                }
                byte[] reply = SearchProtocol.DocsumReply.newBuilder()
                        .setSlimeSummaries(ByteString.copyFrom(BinaryFormat.encode(slime))).build().toByteArray();
                receiver.receive(Client.ResponseOrError.fromResponse(
                        new Client.ProtobufResponse(CompressionType.NONE.getCode(), reply.length, reply)));
            } catch (InvalidProtocolBufferException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void close() { }
    }
}
