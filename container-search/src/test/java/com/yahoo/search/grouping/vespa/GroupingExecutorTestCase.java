// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.container.protect.Error;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.searchlib.aggregation.CountAggregationResult;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.HitsAggregationResult;
import com.yahoo.searchlib.aggregation.MaxAggregationResult;
import com.yahoo.searchlib.aggregation.MinAggregationResult;
import com.yahoo.searchlib.expression.AggregationRefNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.StringResultNode;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingExecutorTestCase {

    @Test
    public void requireThatNullRequestsPass() {
        Result res = newExecution(new GroupingExecutor()).search(newQuery());
        assertNotNull(res);
        assertEquals(0, res.hits().size());
    }

    @Test
    public void requireThatEmptyRequestsPass() {
        Query query = newQuery();
        GroupingRequest.newInstance(query).setRootOperation(new AllOperation());
        Result res = newExecution(new GroupingExecutor()).search(query);
        assertNotNull(res);
        assertEquals(0, res.hits().size());
    }

    @Test
    public void requireThatRequestsAreTransformed() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        try {
            newExecution(new GroupingExecutor(), new GroupingListThrower()).search(query);
            fail();
        } catch (GroupingListException e) {
            assertNotNull(e.lst);
            assertEquals(1, e.lst.size());
            Grouping grp = e.lst.get(0);
            assertNotNull(grp);
        }
    }

    @Test
    public void requireThatEachBelowAllDoesNotBlowUp() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(each(output(summary(bar))))"));
        Result res = newExecution(new GroupingExecutor()).search(query);
        assertNotNull(res);
        assertEquals(1, res.hits().size());
    }

    @Test
    public void requireThatSearchIsMultiPass() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        PassCounter cnt = new PassCounter();
        newExecution(new GroupingExecutor(), cnt).search(query);
        assertEquals(2, cnt.numPasses);
    }

    @Test
    public void requireThatPassRequestsSingleLevel() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        GroupingCollector clt = new GroupingCollector();
        newExecution(new GroupingExecutor(), clt).search(query);
        assertEquals(2, clt.lst.size());
        Grouping grp = clt.lst.get(0);
        assertEquals(0, grp.getFirstLevel());
        assertEquals(0, grp.getLastLevel());
        grp = clt.lst.get(1);
        assertEquals(1, grp.getFirstLevel());
        assertEquals(1, grp.getLastLevel());
    }

    @Test
    public void requireThatAggregationPerHitWithoutGroupingDoesNotWorkYet() {
        try {
            execute("each(output(strlen(customer)))");
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatAggregationWithoutGroupingWorks() {
        List<Grouping> groupings=execute("all(output(count()))");
        assertEquals(1,groupings.size());
        assertEquals(0, groupings.get(0).getLevels().size());
        assertEquals(ConstantNode.class, groupings.get(0).getRoot().getAggregationResults().get(0).getExpression().getClass());
    }

    @Test
    public void requireThatGroupingIsParallel() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))) as(max)" +
                                                          "               each(output(min(bar))) as(min))"));
        GroupingCounter cnt = new GroupingCounter();
        newExecution(new GroupingExecutor(), cnt).search(query);
        assertEquals(2, cnt.passList.size());
        assertEquals(2, cnt.passList.get(0).intValue());
        assertEquals(2, cnt.passList.get(1).intValue());
    }

    @Test
    public void requireThatParallelGroupingIsNotRedundant() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))) as(shallow)" +
                                                          "               each(group(baz) each(output(max(cox)))) as(deep))"));
        GroupingCounter cnt = new GroupingCounter();
        newExecution(new GroupingExecutor(), cnt).search(query);
        assertEquals(3, cnt.passList.size());
        assertEquals(2, cnt.passList.get(0).intValue());
        assertEquals(2, cnt.passList.get(1).intValue());
        assertEquals(1, cnt.passList.get(2).intValue());
    }

    @Test
    public void requireThatPassResultsAreMerged() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(min(bar), max(bar))))"));

        Grouping grpA = new Grouping(0);
        grpA.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("uniqueA")).addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(6)).setTag(4)))
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("common")).addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(9)).setTag(4)))
        );
        Grouping grpB = new Grouping(0);
        grpB.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("uniqueB")).addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(9)).setTag(4)))
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("common")).addAggregationResult(new MinAggregationResult().setMin(new IntegerResultNode(6)).setTag(3)))
        );
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(grpA), null),
                                              new GroupingListHit(Arrays.asList(grpB), null))));
        Group grp = req.getResultGroup(exec.search(query));
        assertEquals(1, grp.size());
        Hit hit = grp.get(0);
        assertTrue(hit instanceof GroupList);
        GroupList lst = (GroupList)hit;
        assertEquals(3, lst.size());
        assertNotNull(hit = lst.get("group:string:uniqueA"));
        assertEquals(6L, hit.getField("max(bar)"));
        assertNotNull(hit = lst.get("group:string:uniqueB"));
        assertEquals(9L, hit.getField("max(bar)"));
        assertNotNull(hit = lst.get("group:string:common"));
        assertEquals(6L, hit.getField("min(bar)"));
        assertEquals(9L, hit.getField("max(bar)"));
    }

    @Test
    public void requireThatUnexpectedGroupingResultsAreIgnored() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));

        Grouping grpExpected = new Grouping(0);
        grpExpected.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("expected")).addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(69)).setTag(3)))
        );
        Grouping grpUnexpected = new Grouping(1);
        grpUnexpected.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("unexpected")).addAggregationResult(new MaxAggregationResult().setMax(new IntegerResultNode(96)).setTag(3)))
        );
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(grpExpected), null),
                                              new GroupingListHit(Arrays.asList(grpUnexpected), null))));
        Group grp = req.getResultGroup(exec.search(query));
        assertEquals(1, grp.size());
        Hit hit = grp.get(0);
        assertTrue(hit instanceof GroupList);
        GroupList lst = (GroupList)hit;
        assertEquals(1, lst.size());
        assertNotNull(hit = lst.get("group:string:expected"));
        assertEquals(69L, hit.getField("max(bar)"));
        assertNull(lst.get("group:string:unexpected"));
    }

    @Test
    public void requireThatHitsAreFilled() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary(bar)))))"));

        Grouping grp0 = new Grouping(0);
        grp0.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar"))
        ));
        Grouping grp1 = new Grouping(0);
        grp1.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar").addHit(new com.yahoo.searchlib.aggregation.FS4Hit()))
        ));
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(grp0), null),
                                              new GroupingListHit(Arrays.asList(grp1), null))),
                                      new FillRequestThrower());
        Result res = exec.search(query);
        
        // Fill with summary specified in grouping
        try {
            exec.fill(res);
            fail();
        } catch (FillRequestException e) {
            assertEquals("bar", e.summaryClass);
        }

        // Fill again, with another summary
        try {
            exec.fill(res, "otherSummary");
            fail();
        } catch (FillRequestException e) {
            assertEquals("otherSummary", e.summaryClass);
        }
    }

    @Test
    public void requireThatUnfilledHitsRenderError() throws IOException {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary(bar)))))"));

        Grouping grp0 = new Grouping(0);
        grp0.setRoot(new com.yahoo.searchlib.aggregation.Group()
                     .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                               .addAggregationResult(new HitsAggregationResult(1, "bar"))));
        Grouping grp1 = new Grouping(0);
        grp1.setRoot(new com.yahoo.searchlib.aggregation.Group()
                     .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                                                                          .addAggregationResult(
                                                                                  new HitsAggregationResult(1, "bar")
                                                                                          .addHit(new com.yahoo.searchlib.aggregation.FS4Hit()))));
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(grp0), null),
                                              new GroupingListHit(Arrays.asList(grp1), null))),
                                      new FillErrorProvider());
        Result res = exec.search(query);
        exec.fill(res);
        assertNotNull(res.hits().getError());
    }

    @Test
    public void requireThatGroupRelevanceCanBeSynthesized() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) order(count()) each(output(count())))"));

        Grouping grp = new Grouping(0);
        grp.setRoot(new com.yahoo.searchlib.aggregation.Group()
                    .addChild(new com.yahoo.searchlib.aggregation.Group()
                                      .setId(new StringResultNode("foo"))
                                      .addAggregationResult(new CountAggregationResult(1))
                                      .addOrderBy(new AggregationRefNode(0), true))
                    .addChild(new com.yahoo.searchlib.aggregation.Group()
                                      .setId(new StringResultNode("bar"))
                                      .addAggregationResult(new CountAggregationResult(2))
                                      .addOrderBy(new AggregationRefNode(0), true)));
        Result res = newExecution(new GroupingExecutor(),
                                  new ResultProvider(Arrays.asList(
                                          new GroupingListHit(Arrays.asList(grp), null),
                                          new GroupingListHit(Arrays.asList(grp), null)))).search(query);

        GroupList groupList = (GroupList)req.getResultGroup(res).get(0);
        assertEquals(1.0, groupList.get(0).getRelevance().getScore(), 1E-6);
        assertEquals(0.5, groupList.get(1).getRelevance().getScore(), 1E-6);
    }

    @Test
    public void requireThatErrorsAreHandled() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary(bar)))))"));

        Grouping grp0 = new Grouping(0);
        grp0.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar"))
        ));
        Grouping grp1 = new Grouping(0);
        grp1.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar").addHit(new com.yahoo.searchlib.aggregation.FS4Hit()))
        ));

        ErrorProvider err = new ErrorProvider(1);
        Execution exec = newExecution(new GroupingExecutor(),
                                      err,
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(grp0), null),
                                              new GroupingListHit(Arrays.asList(grp1), null))));
        Result res = exec.search(query);
        assertTrue(res.hits().getError() != null);
        assertEquals(Error.TIMEOUT.code, res.hits().getError().getCode());
        assertFalse(err.continuedOnFail);

        err = new ErrorProvider(0);
        exec = newExecution(new GroupingExecutor(),
                            err,
                            new ResultProvider(Arrays.asList(
                                    new GroupingListHit(Arrays.asList(grp0), null),
                                    new GroupingListHit(Arrays.asList(grp1), null))));
        res = exec.search(query);
        assertTrue(res.hits().getError() != null);
        assertEquals(Error.TIMEOUT.code, res.hits().getError().getCode());
        assertFalse(err.continuedOnFail);
    }

    @Test
    public void requireThatHitsAreFilledWithCorrectSummary() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary(bar))) as(bar) " +
                                                          "                    each(output(summary(baz))) as(baz)))"));
        Grouping pass0A = new Grouping(0);
        pass0A.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar"))
        ));
        Grouping pass0B = new Grouping(1);
        pass0B.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "baz"))
        ));
        GlobalId gid1 = new GlobalId((new DocumentId("doc:test:1")).getGlobalId());
        GlobalId gid2 = new GlobalId((new DocumentId("doc:test:2")).getGlobalId());
        Grouping pass1A = new Grouping(0);
        pass1A.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar").addHit(new com.yahoo.searchlib.aggregation.FS4Hit(1, gid1, 3)))
        ));
        Grouping pass1B = new Grouping(1);
        pass1B.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "baz").addHit(new com.yahoo.searchlib.aggregation.FS4Hit(4, gid2, 6)))
        ));
        SummaryMapper sm = new SummaryMapper();
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(pass0A, pass0B), null),
                                              new GroupingListHit(Arrays.asList(pass1A, pass1B), null))),
                                      sm);
        exec.fill(exec.search(query), "default");
        assertEquals(2, sm.hitsBySummary.size());

        List<Hit> lst = sm.hitsBySummary.get("bar");
        assertNotNull(lst);
        assertEquals(1, lst.size());
        Hit hit = lst.get(0);
        assertTrue(hit instanceof FastHit);
        assertEquals(1, ((FastHit)hit).getPartId());
        assertEquals(gid1, ((FastHit)hit).getGlobalId());

        assertNotNull(lst = sm.hitsBySummary.get("baz"));
        assertNotNull(lst);
        assertEquals(1, lst.size());
        hit = lst.get(0);
        assertTrue(hit instanceof FastHit);
        assertEquals(4, ((FastHit)hit).getPartId());
        assertEquals(gid2, ((FastHit)hit).getGlobalId());
    }

    @Test
    public void requireThatDefaultSummaryNameFillsHitsWithNull() {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary()))) as(foo))"));

        Grouping pass0 = new Grouping(0);
        pass0.setRoot(new com.yahoo.searchlib.aggregation.Group()
                          .addChild(new com.yahoo.searchlib.aggregation.Group()
                              .setId(new StringResultNode("foo"))
                              .addAggregationResult(
                                  new HitsAggregationResult(1, ExpressionConverter.DEFAULT_SUMMARY_NAME))));
        Grouping pass1 = new Grouping(0);
        pass1.setRoot(new com.yahoo.searchlib.aggregation.Group()
                          .addChild(new com.yahoo.searchlib.aggregation.Group()
                              .setId(new StringResultNode("foo"))
                              .addAggregationResult(
                                  new HitsAggregationResult(1, ExpressionConverter.DEFAULT_SUMMARY_NAME)
                                      .addHit(new com.yahoo.searchlib.aggregation.FS4Hit()))));
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(
                                              new GroupingListHit(Arrays.asList(pass0), null),
                                              new GroupingListHit(Arrays.asList(pass1), null))));
        Result res = exec.search(query);
        exec.fill(res);

        Hit hit = ((HitList)((Group)((GroupList)req.getResultGroup(res).get(0)).get(0)).get(0)).get(0);
        assertTrue(hit instanceof FastHit);
        assertTrue(hit.isFilled(null));
    }

    @Test
    public void requireThatHitsAreAttachedToCorrectQuery() {
        Query queryA = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(queryA);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(each(output(summary(bar)))))"));

        Grouping grp = new Grouping(0);
        grp.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar"))
        ));
        GroupingListHit pass0 = new GroupingListHit(Arrays.asList(grp), null);

        GlobalId gid = new GlobalId((new DocumentId("doc:test:1")).getGlobalId());
        grp = new Grouping(0);
        grp.setRoot(new com.yahoo.searchlib.aggregation.Group()
                .addChild(new com.yahoo.searchlib.aggregation.Group().setId(new StringResultNode("foo"))
                .addAggregationResult(new HitsAggregationResult(1, "bar").addHit(new com.yahoo.searchlib.aggregation.FS4Hit(4, gid, 6)))
        ));
        GroupingListHit pass1 = new GroupingListHit(Arrays.asList(grp), null);
        Query queryB = newQuery(); /** required by {@link GroupingListHit#getSearchQuery()} */
        pass1.setQuery(queryB);

        QueryMapper qm = new QueryMapper();
        Execution exec = newExecution(new GroupingExecutor(),
                                      new ResultProvider(Arrays.asList(pass0, pass1)),
                                      qm);
        exec.fill(exec.search(queryA));
        assertEquals(1, qm.hitsByQuery.size());
        assertTrue(qm.hitsByQuery.containsKey(queryB));
    }

    /**
     * Tests the internal rewriting of rank properties which happens in the query.prepare() call
     * (triggered by the exc.search call in the below).
     */
    @Test
    public void testRankProperties() {
        Execution exc = newExecution(new GroupingExecutor());
        {
            Query query = new Query("?query=foo");
            exc.search(query);
        }
        {
            Query query = new Query("?query=foo&rankfeature.fieldMatch(foo)=2");
            assertEquals("2", query.getRanking().getFeatures().get("fieldMatch(foo)"));
            exc.search(query);
            assertEquals("2", query.getRanking().getFeatures().get("fieldMatch(foo)"));
        }
        {
            Query query = new Query("?query=foo&rankfeature.query(now)=4");
            assertEquals("4", query.getRanking().getFeatures().get("query(now)"));
            exc.search(query);
            assertEquals("4", query.getRanking().getProperties().get("now").get(0));
        }
        {
            Query query = new Query("?query=foo&rankfeature.$bar=8");
            assertEquals("8", query.getRanking().getFeatures().get("$bar"));
            exc.search(query);
            assertEquals("8", query.getRanking().getProperties().get("bar").get(0));
        }
        {
            Query query = new Query("?query=foo&rankproperty.bar=8");
            assertEquals("8", query.getRanking().getProperties().get("bar").get(0));
            exc.search(query);
            assertEquals("8", query.getRanking().getProperties().get("bar").get(0));
        }
        {
            Query query = new Query("?query=foo&rankfeature.fieldMatch(foo)=2&rankfeature.query(now)=4&rankproperty.bar=8");
            assertEquals("2", query.getRanking().getFeatures().get("fieldMatch(foo)"));
            assertEquals("4", query.getRanking().getFeatures().get("query(now)"));
            assertEquals("8", query.getRanking().getProperties().get("bar").get(0));
            exc.search(query);
            assertEquals("2", query.getRanking().getFeatures().get("fieldMatch(foo)"));
            assertEquals("4", query.getRanking().getProperties().get("now").get(0));
            assertEquals("8", query.getRanking().getProperties().get("bar").get(0));
        }
    }

    @Test
    public void testIllegalQuery() {
        Execution exc = newExecution(new GroupingExecutor());

        Query query = new Query();
        NotItem notItem = new NotItem();

        notItem.addNegativeItem(new WordItem("negative"));
        query.getModel().getQueryTree().setRoot(notItem);

        Result result = exc.search(query);
        com.yahoo.search.result.ErrorMessage message = result.hits().getError();

        assertNotNull("Got error", message);
        assertEquals("Illegal query", message.getMessage());
        assertEquals("Can not search for only negative items",
                message.getDetailedMessage());
        assertEquals(3, message.getCode());
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities
    //
    // --------------------------------------------------------------------------------

    private static Query newQuery() {
        return new Query("?query=dummy");
    }

    private static Execution newExecution(Searcher... searchers) {
        return new Execution(new SearchChain(new ComponentId("foo"), Arrays.asList(searchers)),
                             Execution.Context.createContextStub());
    }

    private List<Grouping> execute(String groupingExpression) {
        Query query = newQuery();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString(groupingExpression));
        GroupingCollector collector = new GroupingCollector();
        newExecution(new GroupingExecutor(), collector).search(query);
        return collector.lst;
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class FillRequestThrower extends Searcher {

        @Override
        public Result search(Query query, Execution exec) {
            return exec.search(query);
        }

        @Override
        public void fill(Result result, String summaryClass, Execution exec) {
            throw new FillRequestException(summaryClass);
        }
    }

    @SuppressWarnings("serial")
    private static class FillRequestException extends RuntimeException {

        final String summaryClass;

        FillRequestException(String summaryClass) {
            this.summaryClass = summaryClass;
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class GroupingListThrower extends Searcher {

        @Override
        public Result search(Query query, Execution exec) {
            throw new GroupingListException(GroupingExecutor.getGroupingList(query));
        }
    }

    @SuppressWarnings("serial")
    private static class GroupingListException extends RuntimeException {

        final List<Grouping> lst;

        GroupingListException(List<Grouping> lst) {
            this.lst = lst;
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class GroupingCollector extends Searcher {

        List<Grouping> lst = new ArrayList<>();

        @Override
        public Result search(Query query, Execution exec) {
            for (Grouping grp : GroupingExecutor.getGroupingList(query)) {
                lst.add(grp.clone());
            }
            return exec.search(query);
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class ErrorProvider extends Searcher {
        private final int failOnPassN;
        private int passnum;
        public boolean continuedOnFail;

        public ErrorProvider(int failOnPassN) {
            this.failOnPassN = failOnPassN;
            this.passnum = 0;
            this.continuedOnFail = false;
        }
        @Override
        public Result search(Query query, Execution exec) {
            Result ret = exec.search(query);
            if (passnum > failOnPassN) {
                continuedOnFail = true;
                return ret;
            }
            if (passnum == failOnPassN) {
                ret.hits().addError(ErrorMessage.createTimeout("timeout"));
            }
            passnum++;
            return ret;
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class PassCounter extends Searcher {

        int numPasses = 0;

        @Override
        public Result search(Query query, Execution exec) {
            ++numPasses;
            return exec.search(query);
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class GroupingCounter extends Searcher {

        List<Integer> passList = new ArrayList<>();

        @Override
        public Result search(Query query, Execution exec) {
            passList.add(GroupingExecutor.getGroupingList(query).size());
            return exec.search(query);
        }
    }

    private static class QueryMapper extends Searcher {

        final Map<Query, List<Hit>> hitsByQuery = new HashMap<>();

        @Override
        public Result search(Query query, Execution exec) {
            return exec.search(query);
        }

        @Override
        public void fill(Result result, String summaryClass, Execution exec) {
            for (Iterator<Hit> it = result.hits().deepIterator(); it.hasNext();) {
                Hit hit = it.next();
                Query query = hit.getQuery();
                List<Hit> lst = hitsByQuery.get(query);
                if (lst == null) {
                    lst = new LinkedList<>();
                    hitsByQuery.put(query, lst);
                }
                lst.add(hit);
            }
        }
    }


    @After (GroupingExecutor.COMPONENT_NAME)
    private static class SummaryMapper extends Searcher {

        final Map<String, List<Hit>> hitsBySummary = new HashMap<>();

        @Override
        public Result search(Query query, Execution exec) {
            return exec.search(query);
        }

        @Override
        public void fill(Result result, String summaryClass, Execution exec) {
            for (Iterator<Hit> it = result.hits().deepIterator(); it.hasNext();) {
                Hit hit = it.next();
                List<Hit> lst = hitsBySummary.get(summaryClass);
                if (lst == null) {
                    lst = new LinkedList<>();
                    hitsBySummary.put(summaryClass, lst);
                }
                lst.add(hit);
            }
        }
    }

    @After (GroupingExecutor.COMPONENT_NAME)
    private static class ResultProvider extends Searcher {

        final Queue<GroupingListHit> hits = new LinkedList<>();
        int pass = 0;

        ResultProvider(List<GroupingListHit> hits) {
            this.hits.addAll(hits);
        }

        @Override
        public Result search(Query query, Execution exec) {
            GroupingListHit hit = hits.poll();
            for (Grouping grp : hit.getGroupingList()) {
                grp.setFirstLevel(pass);
                grp.setLastLevel(pass);
            }
            ++pass;
            Result res = exec.search(query);
            res.hits().add(hit);
            return res;
        }
    }

    private static class FillErrorProvider extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }

        @Override
        public void fill(Result result, String summaryClass, Execution exec) {
            result.hits().addError(ErrorMessage.createInternalServerError("foo"));
        }
    }
}
