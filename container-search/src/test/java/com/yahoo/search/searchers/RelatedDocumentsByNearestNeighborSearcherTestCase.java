// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author andreer
 */
public class RelatedDocumentsByNearestNeighborSearcherTestCase {

    private static final TensorType EMBEDDING_TYPE = TensorType.fromSpec("tensor<float>(x[4])");
    private static final Tensor TEST_EMBEDDING = Tensor.from(EMBEDDING_TYPE, "[1.0, 2.0, 3.0, 4.0]");

    @Test
    void testNoRelatedToIdPassesThrough() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?query=test");
        var result = execute(searcher, query);

        assertNull(result.hits().getError());
    }

    @Test
    void testMissingEmbeddingFieldReturnsError() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?relatedTo.id=doc1");
        var result = executeWithMockBackend(searcher, query);

        assertNotNull(result.hits().getError(), "Expected error but got none");
        assertTrue(result.hits().getError().getDetailedMessage().contains("relatedTo.embeddingField is required"),
                "Error message was: " + result.hits().getError().getDetailedMessage());
    }

    @Test
    void testMissingQueryTensorNameReturnsError() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?relatedTo.id=doc1&relatedTo.embeddingField=embedding");
        var result = executeWithMockBackend(searcher, query);

        assertNotNull(result.hits().getError(), "Expected error but got none");
        assertTrue(result.hits().getError().getDetailedMessage().contains("relatedTo.queryTensorName is required"),
                "Error message was: " + result.hits().getError().getDetailedMessage());
    }

    @Test
    void testDocumentNotFoundReturnsError() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?relatedTo.id=doc1&relatedTo.embeddingField=embedding&relatedTo.queryTensorName=q");
        var result = executeWithMockBackend(searcher, query);

        assertNotNull(result.hits().getError(), "Expected error but got none");
        assertTrue(result.hits().getError().getDetailedMessage().contains("Could not find document"),
                "Error message was: " + result.hits().getError().getDetailedMessage());
    }

    @Test
    void testNearestNeighborQueryIsCreated() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?relatedTo.id=doc1&relatedTo.embeddingField=embedding&relatedTo.queryTensorName=q");

        var sourceHit = new Hit("source");
        sourceHit.setField("embedding", TEST_EMBEDDING);

        var capturedQuery = new Query[1];
        var result = executeWithCapture(searcher, query, sourceHit, capturedQuery);

        assertNull(result.hits().getError());
        assertNotNull(capturedQuery[0]);

        var root = capturedQuery[0].getModel().getQueryTree().getRoot();
        assertInstanceOf(NotItem.class, root, "Root should be NotItem for exclusion");
        var notItem = (NotItem) root;
        var positive = notItem.getPositiveItem();
        assertInstanceOf(NearestNeighborItem.class, positive, "Positive item should be NearestNeighborItem");

        var nnItem = (NearestNeighborItem) positive;
        assertEquals("embedding", nnItem.getIndexName());
        // Default: hits=10, offset=0 -> targetHits=10, exploreAdditionalHits=100
        assertEquals(10, nnItem.getTargetHits());
        assertEquals(100, nnItem.getHnswExploreAdditionalHits());
        assertTrue(nnItem.getAllowApproximate());
    }

    @Test
    void testExcludeSourceCanBeDisabled() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?relatedTo.id=doc1&relatedTo.embeddingField=embedding&relatedTo.queryTensorName=q&relatedTo.excludeSource=false");

        var sourceHit = new Hit("source");
        sourceHit.setField("embedding", TEST_EMBEDDING);

        var capturedQuery = new Query[1];
        var result = executeWithCapture(searcher, query, sourceHit, capturedQuery);

        assertNull(result.hits().getError());
        var root = capturedQuery[0].getModel().getQueryTree().getRoot();
        assertInstanceOf(NearestNeighborItem.class, root, "Root should be NearestNeighborItem when exclusion is disabled");
    }

    @Test
    void testTargetHitsBasedOnQueryHitsAndOffset() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        // hits=20, offset=5 -> targetHits=25, exploreAdditionalHits=50
        var query = new Query("?relatedTo.id=doc1&relatedTo.embeddingField=embedding&relatedTo.queryTensorName=q&relatedTo.exploreAdditionalHits=50&hits=20&offset=5");

        var sourceHit = new Hit("source");
        sourceHit.setField("embedding", TEST_EMBEDDING);

        var capturedQuery = new Query[1];
        executeWithCapture(searcher, query, sourceHit, capturedQuery);

        var root = capturedQuery[0].getModel().getQueryTree().getRoot();
        var nnItem = findNearestNeighborItem(root);
        assertNotNull(nnItem);
        assertEquals(25, nnItem.getTargetHits());
        assertEquals(50, nnItem.getHnswExploreAdditionalHits());
    }

    @Test
    void testCombinedWithExistingQuery() {
        var searcher = new RelatedDocumentsByNearestNeighborSearcher();
        var query = new Query("?query=test&relatedTo.id=doc1&relatedTo.embeddingField=embedding&relatedTo.queryTensorName=q&relatedTo.excludeSource=false");

        var sourceHit = new Hit("source");
        sourceHit.setField("embedding", TEST_EMBEDDING);

        var capturedQuery = new Query[1];
        executeWithCapture(searcher, query, sourceHit, capturedQuery);

        var root = capturedQuery[0].getModel().getQueryTree().getRoot();
        assertInstanceOf(AndItem.class, root, "Root should be AndItem when combined with existing query");
    }

    private NearestNeighborItem findNearestNeighborItem(com.yahoo.prelude.query.Item item) {
        if (item instanceof NearestNeighborItem nn) {
            return nn;
        }
        if (item instanceof com.yahoo.prelude.query.CompositeItem composite) {
            for (var child : composite.items()) {
                var found = findNearestNeighborItem(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Result execute(Searcher searcher, Query query) {
        return new Execution(searcher, Execution.Context.createContextStub()).search(query);
    }

    private Result executeWithMockBackend(Searcher searcher, Query query) {
        Searcher mockBackend = new Searcher() {
            @Override
            public Result search(Query q, Execution execution) {
                return new Result(q);
            }
            @Override
            public void fill(Result result, String summaryClass, Execution execution) {
            }
        };

        var chain = new com.yahoo.search.searchchain.SearchChain(
                new com.yahoo.component.ComponentId("test"),
                List.of(searcher, mockBackend));

        return new Execution(chain, Execution.Context.createContextStub()).search(query);
    }

    private Result executeWithCapture(Searcher searcher, Query query, Hit sourceHit, Query[] capturedQuery) {
        Searcher mockBackend = new Searcher() {
            private boolean firstCall = true;
            @Override
            public Result search(Query q, Execution execution) {
                if (firstCall) {
                    firstCall = false;
                    Result r = new Result(q);
                    r.hits().add(sourceHit);
                    return r;
                } else {
                    capturedQuery[0] = q;
                    return new Result(q);
                }
            }
            @Override
            public void fill(Result result, String summaryClass, Execution execution) {
            }
        };

        var chain = new com.yahoo.search.searchchain.SearchChain(
                new com.yahoo.component.ComponentId("test"),
                List.of(searcher, mockBackend));

        return new Execution(chain, Execution.Context.createContextStub()).search(query);
    }

}
