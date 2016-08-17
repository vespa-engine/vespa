package com.yahoo.prelude.fastsearch.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.net.LinuxInetAddress;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockBackend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests that FastSearcher will bypass dispatch when the conditions are right
 * 
 * @author bratseth
 */
public class DirectSearchTestCase {

    private static final String selfHostname = LinuxInetAddress.getLocalHost().getHostName();

    @Test
    public void testDirectSearchEnabled() {
        FastSearcherTester tester = new FastSearcherTester(1, new SearchCluster.Node(selfHostname, 9999, 0));
        tester.search("?query=test&dispatch.direct=true");
        assertTrue("The FastSearcher has used the local search node connection", tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testDirectSearchDisabled() {
        FastSearcherTester tester = new FastSearcherTester(1, new SearchCluster.Node(selfHostname, 9999, 0));
        tester.search("?query=test&dispatch.direct=false");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testDirectSearchDisabledByDefault() {
        FastSearcherTester tester = new FastSearcherTester(1, new SearchCluster.Node(selfHostname, 9999, 0));
        tester.search("?query=test");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenMoreSearchNodesThanContainers() {
        FastSearcherTester tester = new FastSearcherTester(1, selfHostname + " + :9999:0", "otherhost:9999:0");
        tester.search("?query=test&dispatch.direct=true");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenMultipleNodesPerGroup() {
        FastSearcherTester tester = new FastSearcherTester(2, selfHostname + ":9999:0", "otherhost:9999:0");
        tester.search("?query=test&dispatch.direct=true");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testDirectSearchWhenMultipleGroupsAndEnoughContainers() {
        FastSearcherTester tester = new FastSearcherTester(2, selfHostname + ":9999:0", "otherhost:9999:1");
        tester.search("?query=test&dispatch.direct=true");
        assertTrue(tester.hasBeenRequested(selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenLocalNodeIsDown() {
        FastSearcherTester tester = new FastSearcherTester(2, selfHostname + ":9999:0", "otherhost:9999:1");
        tester.setResponding(selfHostname, false);
        tester.search("?query=test&dispatch.direct=true");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
        tester.setResponding(selfHostname, true);
        tester.search("?query=test&dispatch.direct=true");
        assertFalse(tester.hasBeenRequested(selfHostname, 9999));
    }

    private static class FastSearcherTester {

        private final MockFS4ResourcePool mockFS4ResourcePool;
        private final FastSearcher fastSearcher;
        private final MockDispatcher mockDispatcher;

        public FastSearcherTester(int containerNodeCount, SearchCluster.Node searchNode) {
            this(containerNodeCount, Collections.singletonList(searchNode));
        }
        
        public FastSearcherTester(int containerNodeCount, String ... hostAndPortAndGroupStrings) {
            this(containerNodeCount, toNodes(hostAndPortAndGroupStrings));
        }
        
        public FastSearcherTester(int containerNodeCount, List<SearchCluster.Node> searchNodes) {
            mockFS4ResourcePool = new MockFS4ResourcePool();
            mockDispatcher = new MockDispatcher(searchNodes);
            fastSearcher = new FastSearcher(new MockBackend(),
                                            mockFS4ResourcePool,
                                            mockDispatcher,
                                            new SummaryParameters(null),
                                            new ClusterParams("testhittype"),
                                            new CacheParams(100, 1e64),
                                            new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()),
                                            containerNodeCount);
        }

        private static List<SearchCluster.Node> toNodes(String ... hostAndPortAndGroupStrings) {
            List<SearchCluster.Node> nodes = new ArrayList<>();
            for (String s : hostAndPortAndGroupStrings) {
                String[] parts = s.split(":");
                nodes.add(new SearchCluster.Node(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
            return nodes;
        }

        public Result search(String query) {
            Result result = fastSearcher.search(new Query(query), new Execution(Execution.Context.createContextStub()));
            assertEquals(null, result.hits().getError());
            return result;
        }
        
        /** Returns whether a particular "hostname:port" combination has been requested by the FastSearcher */
        public boolean hasBeenRequested(String hostname, int port) {
            return mockFS4ResourcePool.hasBeenRequested(hostname, port);
        }
        
        public void setResponding(String hostname, boolean responding) {
            // Start/stop returning a failing backend
            mockFS4ResourcePool.setResponding(hostname, responding);
            
            // Make the search cluster monitor notice right now in this thread
            SearchCluster.Node node = mockDispatcher.searchCluster().nodesByHost().get(hostname).iterator().next();
            mockDispatcher.searchCluster().ping(node, MoreExecutors.directExecutor());
        }
        
    }
    
    private static class MockFS4ResourcePool extends FS4ResourcePool {
        
        private final Set<String> requestedBackends = new HashSet<>();
        private final Set<String> nonRespondingBackends = new HashSet<>();
        
        public MockFS4ResourcePool() {
            super(1);
        }
        
        @Override
        public Backend getBackend(String hostname, int port) {
            requestedBackends.add(hostname + ":" + port);
            boolean working = true;
            if (nonRespondingBackends.contains(hostname))
                return new MockBackend( ! working);
            else
                return new MockBackend(working);
        }

        /** Returns whether a particular "hostname:port" combination has been requested by the FastSearcher */
        public boolean hasBeenRequested(String hostname, int port) {
            return requestedBackends.contains(hostname + ":" + port);
        }

        public void setResponding(String hostname, boolean responding) {
            if (responding)
                nonRespondingBackends.remove(hostname);
            else
                nonRespondingBackends.add(hostname);
        }

    }

}
