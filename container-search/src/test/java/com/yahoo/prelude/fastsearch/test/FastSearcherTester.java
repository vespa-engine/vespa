package com.yahoo.prelude.fastsearch.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.net.HostName;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockBackend;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockFS4ResourcePool;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockFSChannel;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
class FastSearcherTester {

    public static final String selfHostname = HostName.getLocalhost();

    private final MockFS4ResourcePool mockFS4ResourcePool;
    private final FastSearcher fastSearcher;
    private final MockDispatcher mockDispatcher;

    public FastSearcherTester(int containerNodeCount, SearchCluster.Node searchNode) {
        this(containerNodeCount, Collections.singletonList(searchNode));
    }

    public FastSearcherTester(int containerNodeCount, String... hostAndPortAndGroupStrings) {
        this(containerNodeCount, toNodes(hostAndPortAndGroupStrings));
    }

    public FastSearcherTester(int containerNodeCount, List<SearchCluster.Node> searchNodes) {
        mockFS4ResourcePool = new MockFS4ResourcePool();
        mockDispatcher = new MockDispatcher(searchNodes, mockFS4ResourcePool);
        fastSearcher = new FastSearcher(new MockBackend(selfHostname, MockFSChannel::new),
                                        mockFS4ResourcePool,
                                        mockDispatcher,
                                        new SummaryParameters(null),
                                        new ClusterParams("testhittype"),
                                        new CacheParams(100, 1e64),
                                        new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()),
                                        containerNodeCount);
    }

    private static List<SearchCluster.Node> toNodes(String... hostAndPortAndGroupStrings) {
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

    /** Returns the number of times a backend for this hostname and port has been requested */
    public int requestCount(String hostname, int port) {
        return mockFS4ResourcePool.requestCount(hostname, port);
    }

    /** Sets the response status of a node and ping it to update the monitor status */
    public void setResponding(String hostname, boolean responding) {
        // Start/stop returning a failing backend
        mockFS4ResourcePool.setResponding(hostname, responding);

        // Make the search cluster monitor notice right now in this thread
        SearchCluster.Node node = mockDispatcher.searchCluster().nodesByHost().get(hostname).iterator().next();
        mockDispatcher.searchCluster().ping(node, MoreExecutors.directExecutor());
    }

    /** Sets the response status of a node and ping it to update the monitor status */
    public void setActiveDocuments(String hostname, long activeDocuments) {
        mockFS4ResourcePool.setActiveDocuments(hostname, activeDocuments);

        // Make the search cluster monitor notice right now in this thread
        SearchCluster.Node node = mockDispatcher.searchCluster().nodesByHost().get(hostname).iterator().next();
        mockDispatcher.searchCluster().ping(node, MoreExecutors.directExecutor());
        mockDispatcher.searchCluster().pingIterationCompleted();
    }

}
