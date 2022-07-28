// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.document.GlobalId;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.searchlib.aggregation.ExpressionCountAggregationResult;
import com.yahoo.searchlib.aggregation.FS4Hit;
import com.yahoo.searchlib.aggregation.Group;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.HitsAggregationResult;
import com.yahoo.searchlib.aggregation.hll.SparseSketch;
import com.yahoo.searchlib.expression.StringResultNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class FlatteningSearcherTestCase {

    @Test
    void testFlatteningSearcher() {
        Query query = new Query("?query=test");
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) output(count()) each(each(output(summary(bar)))))"));

        Grouping group0 = new Grouping(0);
        group0.setRoot(new Group()
                .addAggregationResult(new ExpressionCountAggregationResult(new SparseSketch(), sketch -> 69))
                .addChild(new Group().setId(new StringResultNode("unique1"))
                        .addAggregationResult(new HitsAggregationResult(3, "bar")
                        )
                )
                .addChild(new Group().setId(new StringResultNode("unique2"))
                        .addAggregationResult(new HitsAggregationResult(3, "bar")
                        )
                ));
        Grouping group1 = new Grouping(0);
        group1.setRoot(new Group()
                .addChild(new Group().setId(new StringResultNode("unique1"))
                        .addAggregationResult(new HitsAggregationResult(3, "bar")
                                .addHit(fs4Hit(0.7))
                                .addHit(fs4Hit(0.6))
                                .addHit(fs4Hit(0.3))
                        )
                )
                .addChild(new Group().setId(new StringResultNode("unique2"))
                        .addAggregationResult(new HitsAggregationResult(3, "bar")
                                .addHit(fs4Hit(0.5))
                                .addHit(fs4Hit(0.4))
                        )
                ));
        Execution execution = newExecution(new FlatteningSearcher(),
                new GroupingExecutor(ComponentId.fromString("grouping")),
                new ResultProvider(Arrays.asList(
                        new GroupingListHit(List.of(group0), null),
                        new GroupingListHit(List.of(group1), null))));
        Result result = execution.search(query);
        assertEquals(5, result.hits().size());
        assertFlat(result);
        assertEquals(2, result.getTotalHitCount());
    }

    private void assertFlat(Result result) {
        for (var hit : result.hits())
            assertTrue(hit instanceof FastHit);
    }

    private FS4Hit fs4Hit(double relevance) {
        return new FS4Hit(0, new GlobalId(new byte[GlobalId.LENGTH]), relevance);
    }

    private void dump(Hit hit, String indent) {
        System.out.println(indent + hit + " (class " + hit.getClass() + ")");
        if (hit instanceof HitGroup) {
            for (var child : (HitGroup)hit)
                dump(child, indent + "    ");
        }
    }

    private static Execution newExecution(Searcher... searchers) {
        return new Execution(new SearchChain(new ComponentId("foo"), Arrays.asList(searchers)),
                             Execution.Context.createContextStub());
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
            for (Grouping group : hit.getGroupingList()) {
                group.setFirstLevel(pass);
                group.setLastLevel(pass);
            }
            ++pass;
            Result result = exec.search(query);
            result.hits().add(hit);
            return result;
        }
    }

}
