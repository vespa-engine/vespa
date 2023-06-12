// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.protect.Error;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.dispatch.MockDispatcher;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the Fast searcher
 *
 * @author bratseth
 */
public class FastSearcherTestCase {

    @Test
    void testNullQuery() {
        Logger.getLogger(FastSearcher.class.getName()).setLevel(Level.ALL);
        FastSearcher fastSearcher = new FastSearcher("container.0",
                MockDispatcher.create(Collections.emptyList()),
                new SummaryParameters(null),
                new ClusterParams("testhittype"),
                documentdbInfoConfig("test"),
                schemaInfo("test"));

        String query = "?junkparam=ignored";
        Result result = doSearch(fastSearcher, new Query(query), 0, 10);
        ErrorMessage message = result.hits().getError();

        assertNotNull(message, "Got error");
        assertEquals("Null query", message.getMessage());
        assertEquals(query, message.getDetailedMessage());
        assertEquals(Error.NULL_QUERY.code, message.getCode());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    @Test
    void testSinglePassGroupingIsForcedWithSingleNodeGroups() {
        FastSearcher fastSearcher = new FastSearcher("container.0",
                MockDispatcher.create(List.of(new Node(0, "host0", 0))),
                new SummaryParameters(null),
                new ClusterParams("testhittype"),
                documentdbInfoConfig("test"),
                schemaInfo("test"));
        Query q = new Query("?query=foo");
        GroupingRequest request1 = GroupingRequest.newInstance(q);
        request1.setRootOperation(new AllOperation());

        GroupingRequest request2 = GroupingRequest.newInstance(q);
        AllOperation all = new AllOperation();
        all.addChild(new EachOperation());
        all.addChild(new EachOperation());
        request2.setRootOperation(all);

        assertForceSinglePassIs(false, q);
        fastSearcher.search(q, new Execution(Execution.Context.createContextStub()));
        assertForceSinglePassIs(true, q);
    }

    @Test
    void testRankProfileValidation() {
        FastSearcher fastSearcher = new FastSearcher("container.0",
                MockDispatcher.create(List.of(new Node(0, "host0", 0))),
                new SummaryParameters(null),
                new ClusterParams("testhittype"),
                documentdbInfoConfig("test"),
                schemaInfo("test"));
        assertFalse(searchError("?query=q", fastSearcher).contains("does not contain requested rank profile"));
        assertFalse(searchError("?query=q&ranking.profile=default", fastSearcher).contains("does not contain requested rank profile"));
        assertTrue(searchError("?query=q&ranking.profile=nosuch", fastSearcher).contains("does not contain requested rank profile"));
    }

    @Test
    void testSummaryNeedsQuery() {
        var documentDb = new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder().documentdb(new DocumentdbInfoConfig.Documentdb.Builder().name("test")));
        var schema = new Schema.Builder("test")
                .add(new DocumentSummary.Builder("default").build())
                .add(new RankProfile.Builder("default").setHasRankFeatures(false)
                        .setHasSummaryFeatures(false)
                        .build());
        FastSearcher backend = new FastSearcher("container.0",
                MockDispatcher.create(Collections.singletonList(new Node(0, "host0", 0))),
                new SummaryParameters(null),
                new ClusterParams("testhittype"),
                documentDb,
                new SchemaInfo(List.of(schema.build()), List.of()));
        Query q = new Query("?query=foo");
        Result result = doSearch(backend, q, 0, 10);
        assertFalse(backend.summaryNeedsQuery(q));

        q = new Query("?query=select+*+from+source+where+title+contains+%22foobar%22+and++geoLocation%28myfieldname%2C+63.5%2C+10.5%2C+%22999+km%22%29%3B");
        q.getModel().setType(Query.Type.YQL);
        result = doSearch(backend, q, 0, 10);
        assertTrue(backend.summaryNeedsQuery(q));
    }

    @Test
    void testSinglePassGroupingIsNotForcedWithSingleNodeGroups() {
        MockDispatcher dispatcher = MockDispatcher.create(List.of(new Node(0, "host0", 0), new Node(2, "host1", 0)));

        FastSearcher fastSearcher = new FastSearcher("container.0",
                dispatcher,
                new SummaryParameters(null),
                new ClusterParams("testhittype"),
                documentdbInfoConfig("test"),
                schemaInfo("test"));
        Query q = new Query("?query=foo");
        GroupingRequest request1 = GroupingRequest.newInstance(q);
        request1.setRootOperation(new AllOperation());

        GroupingRequest request2 = GroupingRequest.newInstance(q);
        AllOperation all = new AllOperation();
        all.addChild(new EachOperation());
        all.addChild(new EachOperation());
        request2.setRootOperation(all);

        assertForceSinglePassIs(false, q);
        fastSearcher.search(q, new Execution(Execution.Context.createContextStub()));
        assertForceSinglePassIs(false, q);
    }

    private void assertForceSinglePassIs(boolean expected, Query query) {
        for (GroupingRequest request : query.getSelect().getGrouping())
            assertForceSinglePassIs(expected, request.getRootOperation());
    }

    private void assertForceSinglePassIs(boolean expected, GroupingOperation operation) {
        assertEquals(expected, operation.getForceSinglePass(), "Force single pass is " + expected + " in " + operation);
        for (GroupingOperation child : operation.getChildren())
            assertForceSinglePassIs(expected, child);
    }

    @Test
    void testDispatchReconfig() {
        String clusterName = "a";
        var b = new QrSearchersConfig.Builder();
        var searchClusterB = new QrSearchersConfig.Searchcluster.Builder();
        searchClusterB.name(clusterName);
        b.searchcluster(searchClusterB);
        VipStatus vipStatus = new VipStatus(b.build());
        List<Node> nodes_1 = List.of(new Node(0, "host0", 0));
        RpcResourcePool rpcPool_1 = new RpcResourcePool(MockDispatcher.toDispatchConfig(), MockDispatcher.toNodesConfig(nodes_1));
        MockDispatcher dispatch_1 = MockDispatcher.create(nodes_1, rpcPool_1, vipStatus);
        dispatch_1.clusterMonitor.shutdown();
        vipStatus.addToRotation(clusterName);
        assertTrue(vipStatus.isInRotation());
        dispatch_1.deconstruct();
        assertTrue(vipStatus.isInRotation()); //Verify that deconstruct does not touch vipstatus
    }

    private String searchError(String query, Searcher searcher) {
        return search(query, searcher).hits().getError().getDetailedMessage();
    }

    private Result search(String query, Searcher searcher) {
        return searcher.search(new Query(query), new Execution(Execution.Context.createContextStub()));
    }

    private DocumentdbInfoConfig documentdbInfoConfig(String schemaName) {
        var db = new DocumentdbInfoConfig.Documentdb.Builder().name(schemaName);
        return new DocumentdbInfoConfig.Builder().documentdb(db).build();
    }

    private SchemaInfo schemaInfo(String schemaName) {
        var schema = new Schema.Builder(schemaName);
        schema.add(new RankProfile.Builder("default").build());
        return new SchemaInfo(List.of(schema.build()),  List.of());
    }

}
