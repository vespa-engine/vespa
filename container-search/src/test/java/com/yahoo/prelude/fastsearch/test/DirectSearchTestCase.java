package com.yahoo.prelude.fastsearch.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.protect.Error;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests that FastSearcher will bypass dispatch when the conditions are right
 * 
 * @author bratseth
 */
public class DirectSearchTestCase {

    @Test
    public void testDirectSearch() {
        FastSearcherTester tester = new FastSearcherTester();
        assertEquals(null, tester.search("?query=test&dispatch.direct=true").hits().getError());
    }
    
    private static class FastSearcherTester {

        private final FastSearcher fastSearcher;
        
        public FastSearcherTester() {
            fastSearcher = new FastSearcher(new MockBackend(),
                                            new FS4ResourcePool(1),
                                            new MockDispatcher(),
                                            new SummaryParameters(null),
                                            new ClusterParams("testhittype"),
                                            new CacheParams(100, 1e64),
                                            new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()),
                                            1);
        }
        
        public Result search(String query) {
            return fastSearcher.search(new Query(query), new Execution(Execution.Context.createContextStub()));
        }
        
    }

}
