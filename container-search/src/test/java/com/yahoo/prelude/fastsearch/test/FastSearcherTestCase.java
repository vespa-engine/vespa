// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.protect.Error;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * Tests the Fast searcher
 *
 * @author bratseth
 */
public class FastSearcherTestCase {

    private final static DocumentdbInfoConfig documentdbInfoConfig = new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder());


    @Test
    public void testNullQuery() {
        Logger.getLogger(FastSearcher.class.getName()).setLevel(Level.ALL);
        FastSearcher fastSearcher = new FastSearcher("container.0",
                                                     MockDispatcher.create(Collections.emptyList()),
                                                     new SummaryParameters(null),
                                                     new ClusterParams("testhittype"),
                                                     documentdbInfoConfig);

        String query = "?junkparam=ignored";
        Result result = doSearch(fastSearcher,new Query(query), 0, 10);
        ErrorMessage message = result.hits().getError();

        assertNotNull("Got error", message);
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
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(Collections.emptyList()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher), context);
    }

    @Test
    public void testSinglePassGroupingIsForcedWithSingleNodeGroups() {
        FastSearcher fastSearcher = new FastSearcher("container.0",
                                                     MockDispatcher.create(Collections.singletonList(new Node(0, "host0", 0))),
                                                     new SummaryParameters(null),
                                                     new ClusterParams("testhittype"),
                                                     documentdbInfoConfig);
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
    public void testSinglePassGroupingIsNotForcedWithSingleNodeGroups() {
        MockDispatcher dispatcher = MockDispatcher.create(ImmutableList.of(new Node(0, "host0", 0), new Node(2, "host1", 0)));

        FastSearcher fastSearcher = new FastSearcher("container.0",
                                                     dispatcher,
                                                     new SummaryParameters(null),
                                                     new ClusterParams("testhittype"),
                                                     documentdbInfoConfig);
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
        assertEquals("Force single pass is " + expected + " in " + operation,
                     expected, operation.getForceSinglePass());
        for (GroupingOperation child : operation.getChildren())
            assertForceSinglePassIs(expected, child);
    }

}
