// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vdslib.VisitorStatistics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Carlin
 */
public class MetricsSearcherTestCase {

    private MetricsSearcher metricsSearcher = new MetricsSearcher();
    private MockBackend backend = new MockBackend();
    private Chain<Searcher> chain = new Chain<>(metricsSearcher, backend);
    private Execution.Context context = Execution.Context.createContextStub(null);
    private MetricsSearcher.Stats expStatsLt1 = new MetricsSearcher.Stats();
    private static final String LOADTYPE1 = "lt1";
    private MetricsSearcher.Stats expStatsLt2 = new MetricsSearcher.Stats();
    private static final String LOADTYPE2 = "lt2";

    private void verifySearch(String metricParam, String message, String detailedMessage) {
        Result result = new Execution(chain, context).search(new Query("?query=test&" + metricParam));
        assertEquals(1, result.hits().size());
        if (message == null) {
            assertEquals("news:0", result.hits().get(0).getId().toString());
        } else {
            assertNotNull(result.hits().getError());
            assertTrue("Expected '" + message + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getMessage() + "'",
                    result.hits().getErrorHit().errors().iterator().next().getMessage().contains(message));
            assertTrue("Expected '" + detailedMessage + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getDetailedMessage() + "'",
                    result.hits().getErrorHit().errors().iterator().next().getDetailedMessage().contains(detailedMessage));
        }

        if (metricParam == null) {
            return;
        }

        MetricsSearcher.Stats expStats;
        MetricsSearcher.Stats actualStats;
        if (metricParam.contains(LOADTYPE1)) {
            expStats = expStatsLt1;
            actualStats = metricsSearcher.statMap.get(LOADTYPE1);
        } else {
            expStats = expStatsLt2;
            actualStats = metricsSearcher.statMap.get(LOADTYPE2);
        }

        expStats.count++;
        if (message == null) {
            expStats.ok++;
        } else {
            expStats.failed++;
        }
        if (metricParam.contains(LOADTYPE1)) {
            expStats.dataStreamed += 16;
            expStats.documentsStreamed += 2;
        }

        assertEquals(expStats.count, actualStats.count);
        assertEquals(expStats.ok, actualStats.ok);
        assertEquals(expStats.failed, actualStats.failed);
        assertEquals(expStats.dataStreamed, actualStats.dataStreamed);
        assertEquals(expStats.documentsStreamed, actualStats.documentsStreamed);
    }

    @Test
    public void testBasics() {
        // Start counting at -1 since count is reset upon the first query by MetricsSearcher.search
        expStatsLt1.count--;
        String[] loadTypes = { LOADTYPE1, LOADTYPE2};
        for (String loadType : loadTypes) {
            verifySearch("streaming.loadtype="+loadType, null, null);
            verifySearch("metricsearcher.id="+loadType, null, null);
            verifySearch(null, null, null);
            verifySearch("streaming.loadtype="+loadType, "Backend communication error", "Detailed error message");
        }

    }

    @Test
    public void searcherDoesNotTryToDereferenceNullQueryContext() {
        backend.setImplicitlyCreateContext(false);
        // This will crash with an NPE if the searcher does not cope with null
        // query contexts.
        new Execution(chain, context).search(new Query("?query=test&streaming.loadtype=" + LOADTYPE1));
    }

    private static class MockBackend extends Searcher {
        private int sequenceNumber = 0;
        private VisitorStatistics visitorStats = new VisitorStatistics();
        private boolean implicitlyCreateContext = true;

        private MockBackend() {
            visitorStats.setBucketsVisited(1);
            visitorStats.setBytesReturned(8);
            visitorStats.setBytesVisited(16);
            visitorStats.setDocumentsReturned(1);
            visitorStats.setDocumentsVisited(2);
        }

        public void setImplicitlyCreateContext(boolean implicitlyCreateContext) {
            this.implicitlyCreateContext = implicitlyCreateContext;
        }

        @Override
        public Result search(Query query, Execution execution) {
            if (implicitlyCreateContext) {
                String loadType = query.properties().getString("streaming.loadtype");
                assignContextProperties(query, loadType);
            }

            Result result = new Result(query);
            if (sequenceNumber == 3 || sequenceNumber == 7) {
                result.hits().addError(ErrorMessage.createBackendCommunicationError("Detailed error message"));
            } else {
                result.hits().add(new Hit("news:0"));
            }
            sequenceNumber++;
            return result;
        }

        private void assignContextProperties(Query query, String loadType) {
            if (loadType != null && loadType.equals(LOADTYPE1)) {
                query.getContext(true).setProperty(VdsStreamingSearcher.STREAMING_STATISTICS, visitorStats);
            } else {
                query.getContext(true).setProperty(VdsStreamingSearcher.STREAMING_STATISTICS, null);
            }
        }
    }

}
