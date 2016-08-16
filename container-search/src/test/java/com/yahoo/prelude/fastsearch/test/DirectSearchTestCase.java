package com.yahoo.prelude.fastsearch.test;

import com.yahoo.fs4.mplex.Backend;
import com.yahoo.net.LinuxInetAddress;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that FastSearcher will bypass dispatch when the conditions are right
 * 
 * @author bratseth
 */
public class DirectSearchTestCase {

    private static final String selfHostname = LinuxInetAddress.getLocalHost().getHostName();

    @Test
    public void testDirectSearch() {
        FastSearcherTester tester = new FastSearcherTester(new SearchCluster.Node(selfHostname, 9999, 0));
        Result result = tester.search("?query=test&dispatch.direct=true");
        assertEquals(null, result.hits().getError());
        assertTrue("The FastSearcher has used the local search node connection", tester.hasBeenRequested(selfHostname, 9999));
    }
    
    private static class FastSearcherTester {

        private final MockFS4ResourcePool mockFS4ResourcePool;
        private final FastSearcher fastSearcher;

        public FastSearcherTester(SearchCluster.Node node) {
            this(Collections.singletonList(node));
        }

        public FastSearcherTester(List<SearchCluster.Node> nodes) {
            mockFS4ResourcePool = new MockFS4ResourcePool();
            fastSearcher = new FastSearcher(new MockBackend(),
                                            mockFS4ResourcePool,
                                            new MockDispatcher(nodes),
                                            new SummaryParameters(null),
                                            new ClusterParams("testhittype"),
                                            new CacheParams(100, 1e64),
                                            new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()),
                                            1);
        }
        
        public Result search(String query) {
            return fastSearcher.search(new Query(query), new Execution(Execution.Context.createContextStub()));
        }
        
        /** Returns whether a particular "hostname:port" combination has been requested by the FastSearcher */
        public boolean hasBeenRequested(String hostname, int port) {
            return mockFS4ResourcePool.hasBeenRequested(hostname, port);
        }
        
    }
    
    private static class MockFS4ResourcePool extends FS4ResourcePool {
        
        private final Set<String> requestedBackends = new HashSet<>();
        
        public MockFS4ResourcePool() {
            super(1);
        }
        
        @Override
        public Backend getBackend(String hostname, int port) {
            requestedBackends.add(hostname + ":" + port);
            return new MockBackend();
        }

        /** Returns whether a particular "hostname:port" combination has been requested by the FastSearcher */
        public boolean hasBeenRequested(String hostname, int port) {
            return requestedBackends.contains(hostname + ":" + port);
        }

    }

}
