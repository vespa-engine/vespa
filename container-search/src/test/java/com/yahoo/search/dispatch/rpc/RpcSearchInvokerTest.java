// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.common.collect.ImmutableMap;
import com.yahoo.compress.CompressionType;
import com.yahoo.concurrent.Timer;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.InterleavedSearchInvoker;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.TopKEstimator;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.schema.MatchPhase;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.schema.SecondPhase;
import com.yahoo.vespa.config.search.DispatchConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.search.dispatch.searchcluster.MockSearchCluster.createDispatchConfig;
import static com.yahoo.search.dispatch.searchcluster.MockSearchCluster.createNodesConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Olli Virtanen
 */
public class RpcSearchInvokerTest {

    private final CompressService compressor = new CompressService();

    @Test
    void testProtobufSerialization() {
        var holders = new Holders();
        var invoker = createRpcInvoker(new Node("test", 7, "seven", 1), 1000, holders);

        Query q = new Query("search/?query=test&hits=10&offset=3");
        RpcSearchInvoker.SerializedQuery serialized1 = (RpcSearchInvoker.SerializedQuery) invoker.sendSearchRequest(q, 1.0, null);
        assertEquals(holders.length.get(), serialized1.compressedPayload.uncompressedSize());
        assertSame(serialized1.compressedPayload.data(), holders.payload.get());

        var request = decompress(holders);
        assertEquals(10, request.getHits());
        assertEquals(3, request.getOffset());
        assertFalse(request.getQueryTreeBlob().isEmpty());

        var invoker2 = createRpcInvoker(new Node("test", 8, "eight", 1), 1000, holders);
        RpcSearchInvoker.SerializedQuery serialized2 = (RpcSearchInvoker.SerializedQuery) invoker2.sendSearchRequest(q, 1.0, serialized1);
        assertSame(serialized1, serialized2);
        assertEquals(holders.length.get(), serialized1.compressedPayload.uncompressedSize());
        assertSame(serialized1.compressedPayload.data(), holders.payload.get());
    }

    @Test
    void testProtobufSerializationWithMaxHitsSet() {
        var holders = new Holders();
        int maxHits = 5;
        var invoker = createRpcInvoker(new Node("test", 7, "seven", 1), maxHits, holders);

        Query q = new Query("search/?query=test&hits=10&offset=3");
        invoker.sendSearchRequest(q, 1.0, null);
        assertEquals(maxHits, decompress(holders).getHits());
    }

    @Test
    void testUpdateOfRpcResourcePool() {
        RpcResourcePool rpcResourcePool = new RpcResourcePool(createDispatchConfig(), createNodesConfig(0, 0));
        verifyConnections(rpcResourcePool, 3,3, 0);
        verifyConnections(rpcResourcePool, 4,4, 6);
        verifyConnections(rpcResourcePool, 2,2, 14);
    }

    @Test
    void contentShareIsUsedToSetTargetHits() throws IOException {
        // Total target is distributed proportional to content share (by active document count)
        assertAdjustedTotalTargetHits(List.of(46, 55), List.of(1000, 1200));

        // Small differences (<5%) do not justify reserialization and so get the same value
        assertAdjustedTotalTargetHits(List.of(50, 50), List.of(1000, 1035));

        // Nodes with 0 documents get default content share: 1/nodes
        assertAdjustedTotalTargetHits(List.of(49, 49, 20, 1, 1), List.of(1000, 1035, 0, 1, 13));
    }

    @Test
    void contentShareIsUsedToSetMatchPhaseMaxHits() throws IOException {
        // total max hits in query is applied
        var query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getMatchPhase().setTotalMaxHits(100);
        assertAdjustedProperty("vespa.matchphase.degradation.maxhits",
                               query,
                               1,
                               schemaInfoWithMaxHits(OptionalLong.empty(), OptionalLong.empty()));

        // total max hits in schema is applied
        query = new Query("?query=ignored&ranking=myProfile");
        assertAdjustedProperty("vespa.matchphase.degradation.maxhits",
                               query,
                               1, // Schema info is used when not set in query
                               schemaInfoWithMaxHits(OptionalLong.empty(), OptionalLong.of(100)));

        // total max hits in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getMatchPhase().setTotalMaxHits(200);
        assertAdjustedProperty("vespa.matchphase.degradation.maxhits",
                               query,
                               2,
                               schemaInfoWithMaxHits(OptionalLong.empty(), OptionalLong.of(100)));

        // max hits in query overrides max hits in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getMatchPhase().setMaxHits(200);
        assertFlatProperty("vespa.matchphase.degradation.maxhits",
                           query,
                           200,
                           schemaInfoWithMaxHits(OptionalLong.empty(), OptionalLong.of(100)));

        // max hits in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getMatchPhase().setMaxHits(200);
        assertFlatProperty("vespa.matchphase.degradation.maxhits",
                           query,
                           200,
                           schemaInfoWithMaxHits(OptionalLong.of(100), OptionalLong.empty()));

        // total max hits in query overrides max hits in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getMatchPhase().setTotalMaxHits(200);
        assertAdjustedProperty("vespa.matchphase.degradation.maxhits",
                               query,
                               2,
                               schemaInfoWithMaxHits(OptionalLong.of(100), OptionalLong.empty()));
    }

    @Test
    void contentShareIsUsedToSetSecondPhaseRerankCount() throws IOException {
        // total rerank count in query is applied
        var query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getSecondPhase().setTotalRerankCount(100);
        assertAdjustedProperty("vespa.hitcollector.heapsize",
                               query,
                               1,
                               schemaInfoWithRerankCount(OptionalInt.empty(), OptionalInt.empty()));

        // total rerank count in schema is applied
        query = new Query("?query=ignored&ranking=myProfile");
        assertAdjustedProperty("vespa.hitcollector.heapsize",
                               query,
                               1, // Schema info is used when not set in query
                               schemaInfoWithRerankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // total rerank count in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getSecondPhase().setTotalRerankCount(200);
        assertAdjustedProperty("vespa.hitcollector.heapsize",
                               query,
                               2,
                               schemaInfoWithRerankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // rerank count in query overrides total rerank count in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getSecondPhase().setRerankCount(200);
        assertFlatProperty("vespa.hitcollector.heapsize",
                           query,
                          200,
                           schemaInfoWithRerankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // rerank count in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getSecondPhase().setRerankCount(200);
        assertFlatProperty("vespa.hitcollector.heapsize",
                           query,
                           200,
                           schemaInfoWithRerankCount(OptionalInt.of(100), OptionalInt.empty()));

        // total rerank count in query overrides rerank count in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().getSecondPhase().setTotalRerankCount(200);
        assertAdjustedProperty("vespa.hitcollector.heapsize",
                               query,
                               2,
                               schemaInfoWithRerankCount(OptionalInt.of(100), OptionalInt.empty()));
    }

    @Test
    void contentShareIsUsedToSetKeepRankCount() throws IOException {
        // total keep rank count in query is applied
        var query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().setTotalKeepRankCount(100);
        assertAdjustedProperty("vespa.hitcollector.arraysize",
                               query,
                               1,
                               schemaInfoWithKeepRankCount(OptionalInt.empty(), OptionalInt.empty()));

        // total keep rank count in schema is applied
        query = new Query("?query=ignored&ranking=myProfile");
        assertAdjustedProperty("vespa.hitcollector.arraysize",
                               query,
                               1,
                               schemaInfoWithKeepRankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // total keep rank count in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().setTotalKeepRankCount(200);
        assertAdjustedProperty("vespa.hitcollector.arraysize",
                               query,
                               2,
                               schemaInfoWithKeepRankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // keep rank count in query overrides total keep rank count in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().setKeepRankCount(200);
        assertFlatProperty("vespa.hitcollector.arraysize",
                           query,
                           200,
                           schemaInfoWithKeepRankCount(OptionalInt.empty(), OptionalInt.of(100)));

        // keep rank count in query overrides schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().setKeepRankCount(200);
        assertFlatProperty("vespa.hitcollector.arraysize",
                           query,
                           200,
                           schemaInfoWithKeepRankCount(OptionalInt.of(100), OptionalInt.empty()));

        // total keep rank count in query overrides keep rank count in schema
        query = new Query("?query=ignored&ranking=myProfile");
        query.getRanking().setTotalKeepRankCount(200);
        assertAdjustedProperty("vespa.hitcollector.arraysize",
                               query,
                               2,
                               schemaInfoWithKeepRankCount(OptionalInt.of(100), OptionalInt.empty()));
    }

    private List<Holders> queryGroup(Query query,
                                     SchemaInfo schemaInfo,
                                     List<Integer> activeDocs) throws IOException {
        // Necessary query preparation, in the order it will happen:
        query.prepare();
        query.getModel().getRestrict().add("mySchema");
        var schema = schemaInfo.newSession(query).schema("mySchema");
        ClusterSearcher.transferKeepRankCounts(query, schema);
        ClusterSearcher.transferRerankCounts(query, schema);
        ClusterSearcher.transferMatchPhaseMaxHits(query, schema);

        List<Node> nodes = new ArrayList<>();
        List<RpcSearchInvoker> nodeInvokers = new ArrayList<>();
        List<Holders> nodeHolders = new ArrayList<>();
        for (int i = 0; i < activeDocs.size(); i++) {
            Node node = new Node("test", i, "?", 0);
            node.setActiveDocuments(activeDocs.get(i));
            node.setWorking(true);
            var holders = new Holders();
            var invoker = createRpcInvoker(node, 10, holders);
            nodes.add(node);
            nodeInvokers.add(invoker);
            nodeHolders.add(holders);
        }
        Group group = new Group(0, nodes);
        group.aggregateNodeValues();

        try (InterleavedSearchInvoker invoker = createInterleavedSearchInvoker(group, nodeInvokers)) {
            invoker.search(query, 1.0);
        }
        return nodeHolders;
    }

    private void assertAdjustedProperty(String property, Query query, int multiplier, SchemaInfo schemaInfo) throws IOException {
        List<Holders> nodeHolders = queryGroup(query, schemaInfo, List.of(1000, 1035, 0, 1, 13));
        List<Integer> expected = List.of(49 * multiplier, 49 * multiplier, 20 * multiplier, 1, 1 * multiplier);
        var requests = nodeHolders.stream().map(this::decompress).toList();
        for (int i = 0; i < expected.size(); i++)
            assertProperty(property, expected.get(i), requests.get(i));
    }

    private void assertFlatProperty(String property, Query query, int value, SchemaInfo schemaInfo) throws IOException {
        List<Holders> nodeHolders = queryGroup(query, schemaInfo, List.of(1000, 1035, 0, 1, 13));
        var requests = nodeHolders.stream().map(this::decompress).toList();
        for (int i = 0; i < 5; i++)
            assertProperty(property, value, requests.get(i));
    }

    private void assertProperty(String name, int value, SearchProtocol.SearchRequest request) {
        for (int i = 0; i < request.getRankPropertiesCount(); i++) {
            var property = request.getRankProperties(i);
            if ( ! property.getName().equals(name)) continue;
            assertEquals(String.valueOf(value), property.getValues(0));
            return;
        }
        fail("Property '" + name + "' is not present");
    }

    private void assertAdjustedTotalTargetHits(List<Integer> expected, List<Integer> activeDocs) throws IOException {
        Query query = new Query();
        var root = new OrItem();

        var weakAnd = new WeakAndItem();
        weakAnd.setTotalTargetHits(100);
        weakAnd.addItem(new WordItem("foo", "myindex"));
        weakAnd.addItem(new WordItem("bar", "myindex"));
        root.addItem(weakAnd);

        var wandItem = new WandItem("myField");
        wandItem.setTotalTargetHits(100);
        wandItem.addToken(10L, 37);
        root.addItem(wandItem);

        var minTargetHits = 2;
        var nn = new NearestNeighborItem("myField", "myQueryTensor");
        nn.setTotalTargetHits(100);
        nn.setMinTargetHits(minTargetHits);
        root.addItem(nn);

        query.getModel().getQueryTree().setRoot(root);

        List<Holders> nodeHolders = queryGroup(query,
                                               schemaInfo(),
                                               activeDocs);
        var requests = nodeHolders.stream().map(this::decompress).toList();
        for (int i = 0; i < expected.size(); i++) {
            var or = requests.get(i).getQueryTree().getRoot().getItemOr();
            assertEquals(expected.get(i), or.getChildren(0).getItemWeakAnd().getTargetNumHits(),
                         "WeakAnd in node " + i);
            assertEquals(expected.get(i), or.getChildren(1).getItemLongWand().getTargetNumHits(),
                         "Wand in node " + i);
            assertEquals(Math.max(minTargetHits, expected.get(i)),
                         or.getChildren(2).getItemNearestNeighbor().getTargetNumHits(),
                         "NearestNeighbor in node " + i);
        }
    }

    private SchemaInfo schemaInfo() {
        var schema = new Schema.Builder("mySchema");
        var profile = new RankProfile.Builder("myProfile");
        schema.add(profile.build());
        return new SchemaInfo(List.of(schema.build()), List.of());
    }

    private SchemaInfo schemaInfoWithMaxHits(OptionalLong maxHits, OptionalLong totalMaxHits) {
        var schema = new Schema.Builder("mySchema");
        var profile = new RankProfile.Builder("myProfile");
        var matchPhase = new MatchPhase.Builder();
        maxHits.ifPresent(matchPhase::setMaxHits);
        totalMaxHits.ifPresent(matchPhase::setTotalMaxHits);
        profile.setMatchPhase(matchPhase.build());
        schema.add(profile.build());
        return new SchemaInfo(List.of(schema.build()), List.of());
    }

    private SchemaInfo schemaInfoWithRerankCount(OptionalInt rerankCount, OptionalInt totalRerankCount) {
        var schema = new Schema.Builder("mySchema");
        var profile = new RankProfile.Builder("myProfile");
        var secondPhase = new SecondPhase.Builder();
        rerankCount.ifPresent(secondPhase::setRerankCount);
        totalRerankCount.ifPresent(secondPhase::setTotalRerankCount);
        profile.setSecondPhase(secondPhase.build());
        schema.add(profile.build());
        return new SchemaInfo(List.of(schema.build()), List.of());
    }

    private SchemaInfo schemaInfoWithKeepRankCount(OptionalInt keepRankCount, OptionalInt totalKeepRankCount) {
        var schema = new Schema.Builder("mySchema");
        var profile = new RankProfile.Builder("myProfile");
        keepRankCount.ifPresent(profile::setKeepRankCount);
        totalKeepRankCount.ifPresent(profile::setTotalKeepRankCount);
        var secondPhase = new SecondPhase.Builder();
        profile.setSecondPhase(secondPhase.build());
        schema.add(profile.build());
        return new SchemaInfo(List.of(schema.build()), List.of());
    }

    private InterleavedSearchInvoker createInterleavedSearchInvoker(Group group, List<RpcSearchInvoker> nodeInvokers) {
        DispatchConfig dispatchConfig = new DispatchConfig.Builder().build();
        TopKEstimator hitEstimator = new TopKEstimator(30, dispatchConfig.topKProbability(), 0.05);
        List<SearchInvoker> invokers = new ArrayList<>(nodeInvokers);
        return new InterleavedSearchInvoker(Timer.monotonic, invokers, hitEstimator, dispatchConfig, group, Set.of());
    }

    RpcSearchInvoker createRpcInvoker(Node node, int maxHits, Holders holders) {
        var mockPool = new RpcResourcePool(ImmutableMap.of(node.key(), parameterCollectorClient(holders).createConnection(node.hostname(), 123)));
        return new RpcSearchInvoker(mockSearcher(), compressor, node, mockPool, maxHits, new QrSearchersConfig.Builder().build());
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
            assertInstanceOf(RpcClient.RpcNodeConnection.class, rpcResourcePool.getConnection(nodeId));
        }
        assertNull(rpcResourcePool.getConnection(numGroups*nodesPerGroup));
    }

    private Client parameterCollectorClient(Holders holders) {
        return new Client() {
            @Override
            public void close() { }
            @Override
            public NodeConnection createConnection(String hostname, int port) {
                return new NodeConnection() {
                    @Override
                    public void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                            ResponseReceiver responseReceiver, double timeoutSeconds) {
                        holders.compressionType.set(compression);
                        holders.payload.set(compressedPayload);
                        holders.length.set(uncompressedLength);
                        responseReceiver.receive(Client.ResponseOrError.fromError("mock"));
                    }

                    @Override
                    public void close() { }
                };
            }
        };
    }

    private VespaBackend mockSearcher() {
        return new VespaBackend(new ClusterParams("container.0")) {
            @Override
            protected Result doSearch2(String schema, Query query) {
                fail("Unexpected call");
                return null;
            }

            @Override
            protected void doPartialFill(Result result, String summaryClass) {
                fail("Unexpected call");
            }
        };
    }

    private ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.SearchRequest decompress(Holders holders) {
        try {
            var bytes = compressor.compressor().decompress(holders.payload.get(), holders.compressionType.get(), holders.length.get());
            return SearchProtocol.SearchRequest.newBuilder().mergeFrom(bytes).build();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    record Holders(AtomicReference<CompressionType> compressionType,
                   AtomicReference<byte[]> payload,
                   AtomicInteger length) {

        Holders() {
            this(new AtomicReference<>(), new AtomicReference<>(), new AtomicInteger());
        }

    }

}
