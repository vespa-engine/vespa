// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.data.access.simple.Value;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.search.searchers.ChunkLimitingSearcher;
import com.yahoo.tensor.Tensor;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkLimitingSearcherTestCase {

    @Test
    void testChunkLimiting() {

        Query testQuery = new Query();
        testQuery.properties().set("chunk.limit.max", 3);
        testQuery.properties().set("chunk.limit.field", "paragraphs");
        testQuery.properties().set("chunk.limit.tensor", "paragraphScores");
        testQuery.getTrace().setLevel(10);

        Result mockResult = new Result(testQuery);
        mockResult.hits().add(makeHit(1, 0.9)); // no chunks
        mockResult.hits().add(makeHit(2, 0.8, "first")); // below limit
        mockResult.hits().add(makeHit(3, 0.7, "first", "second", "third")); // at limit
        mockResult.hits().add(makeHit(3, 0.7, "first", "second", "third", "fourth"));
        mockResult.hits().add(makeHit(4, 0.6, "first", "second", "third", "fourth", "fifth"));

        DocumentSourceSearcher docSource = new DocumentSourceSearcher();
        docSource.addResult(testQuery, mockResult);
        Chain<Searcher> myChain = new Chain<>(new ChunkLimitingSearcher(), docSource);
        Execution.Context context = Execution.Context.createContextStub();
        Execution execution = new Execution(myChain, context);

        Result result = execution.search(testQuery);
        execution.fill(result);
        printTrace(execution);

        assertEquals(5, result.hits().size());

        assertParagraphs(result, 1);
        assertParagraphs(result, 2, "first");
        assertParagraphs(result, 3, "first", "second", "third");
        assertParagraphs(result, 4, "first", "second", "third");
        assertParagraphs(result, 5, "first", "third", "fifth");
    }

    private void assertParagraphs(Result result, int i, String... paragraphs) {
        Value.ArrayValue expected = new Value.ArrayValue();
        for (String p : paragraphs) {
            expected.add(p);
        }

        Value.ArrayValue fromHit = (Value.ArrayValue) result.hits().get(i - 1).getField("paragraphs");

        assertEquals(expected.toJson(), fromHit.toJson());
    }

    private static Hit makeHit(int id, double relevance, String... paragraphs) {
        Hit newHit = new Hit("hit:" + id, relevance);

        Value.ArrayValue stringArrayFieldValue = new Value.ArrayValue();
        for (String p : paragraphs) {
            stringArrayFieldValue.add(p);
        }

        StringBuilder tensorString = new StringBuilder("tensor(p{}):{");
        for (int i = 0; i < paragraphs.length; i++) {
            tensorString.append(i).append(": ").append(paragraphs[i].hashCode() / (double) Integer.MAX_VALUE);
            if (i < paragraphs.length - 1) tensorString.append(", ");
        }
        tensorString.append("}");
        Tensor scoreTensor = Tensor.from(tensorString.toString());

        FeatureData featureData = new FeatureData(Map.of("paragraphScores", scoreTensor));

        newHit.setField("paragraphs", stringArrayFieldValue);
        newHit.setField("summaryfeatures", featureData);
        return newHit;
    }

    private static void printTrace(Execution execution) {
        execution.trace().accept(new TraceVisitor() {
            @Override
            public void visit(TraceNode node) {
                System.out.println(node);
            }
        });
    }
}
