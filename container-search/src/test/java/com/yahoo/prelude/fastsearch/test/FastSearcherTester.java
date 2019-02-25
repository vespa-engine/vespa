// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.net.HostName;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockBackend;
import com.yahoo.prelude.fastsearch.test.fs4mock.MockFS4ResourcePool;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
class FastSearcherTester {

    public static final String selfHostname = HostName.getLocalhost();

    private final MockFS4ResourcePool mockFS4ResourcePool;
    private final FastSearcher fastSearcher;
    private final MockDispatcher mockDispatcher;
    private final VipStatus vipStatus;

    public FastSearcherTester(int containerClusterSize, Node searchNode) {
        this(containerClusterSize, Collections.singletonList(searchNode));
    }

    public FastSearcherTester(int containerClusterSize, String... hostAndPortAndGroupStrings) {
        this(containerClusterSize, toNodes(hostAndPortAndGroupStrings));
    }

    public FastSearcherTester(int containerClusterSize, List<Node> searchNodes) {
        String clusterId = "a";

        var b = new QrSearchersConfig.Builder();
        var searchClusterB = new QrSearchersConfig.Searchcluster.Builder();
        searchClusterB.name(clusterId);
        b.searchcluster(searchClusterB);
        vipStatus = new VipStatus(b.build());

        mockFS4ResourcePool = new MockFS4ResourcePool();
        mockDispatcher = new MockDispatcher(clusterId, searchNodes, mockFS4ResourcePool, containerClusterSize, vipStatus);
        fastSearcher = new FastSearcher(new MockBackend(selfHostname, 0L, true),
                                        mockFS4ResourcePool,
                                        mockDispatcher,
                                        new SummaryParameters(null),
                                        new ClusterParams("testhittype"),
                                        new CacheParams(100, 1e64),
                                        new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()));
    }

    private static List<Node> toNodes(String... hostAndPortAndGroupStrings) {
        List<Node> nodes = new ArrayList<>();
        int key = 0;
        for (String s : hostAndPortAndGroupStrings) {
            String[] parts = s.split(":");
            nodes.add(new Node(key++, parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
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

    public MockDispatcher dispatcher() { return mockDispatcher; }

    /** Sets the response status of a node and ping it to update the monitor status */
    public void setResponding(String hostname, boolean responding) {
        // Start/stop returning a failing backend
        mockFS4ResourcePool.setResponding(hostname, responding);

        // Make the search cluster monitor notice right now in this thread
        Node node = mockDispatcher.searchCluster().nodesByHost().get(hostname).iterator().next();
        mockDispatcher.searchCluster().ping(node, MoreExecutors.directExecutor());
    }

    /** Sets the response status of a node and ping it to update the monitor status */
    public void setActiveDocuments(String hostname, long activeDocuments) {
        mockFS4ResourcePool.setActiveDocuments(hostname, activeDocuments);

        // Make the search cluster monitor notice right now in this thread
        Node node = mockDispatcher.searchCluster().nodesByHost().get(hostname).iterator().next();
        mockDispatcher.searchCluster().ping(node, MoreExecutors.directExecutor());
        mockDispatcher.searchCluster().pingIterationCompleted();
    }

    public VipStatus vipStatus() { return vipStatus; }

    /** Retrying is needed because earlier pings from the monitoring thread may interfere with the testing thread */
    public void waitForInRotationIs(boolean expectedRotationStatus) {
        int triesLeft = 9000;
        while (vipStatus.isInRotation() != expectedRotationStatus && triesLeft > 0) {
            triesLeft--;
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
        if (triesLeft == 0)
            fail("Did not reach VIP in rotation status = " + expectedRotationStatus + " after trying for 90 seconds");
    }

}
