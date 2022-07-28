// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class VipStatusTestCase {

    @Test
    void testInitializingOrDownRequireAllUp() {
        String[] clusters = {"cluster1", "cluster2", "cluster3"};
        verifyStatus(clusters, StateMonitor.Status.initializing);
        verifyStatus(clusters, StateMonitor.Status.down);
    }

    @Test
    void testUpRequireAllDown() {
        String[] clusters = {"cluster1", "cluster2", "cluster3"};

        VipStatus v = createVipStatus(clusters, StateMonitor.Status.initializing, true, new ClustersStatus(), new MetricMock());
        assertFalse(v.isInRotation());
        addToRotation(clusters, v);
        assertTrue(v.isInRotation());

        v.removeFromRotation(clusters[0]);
        assertTrue(v.isInRotation());
        v.removeFromRotation(clusters[1]);
        assertTrue(v.isInRotation());
        v.removeFromRotation(clusters[2]);
        assertFalse(v.isInRotation());  // All down
        v.addToRotation(clusters[1]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[0]);
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());   // All up
        v.removeFromRotation(clusters[0]);
        v.removeFromRotation(clusters[2]);
        assertTrue(v.isInRotation());
        v.addToRotation(clusters[0]);
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());
    }

    @Test
    void testNoClustersConfiguringInitiallyInRotationFalse() {
        String[] clusters = {};
        VipStatus v = createVipStatus(clusters, StateMonitor.Status.initializing, false, new ClustersStatus(), new MetricMock());
        assertFalse(v.isInRotation());
    }

    @Test
    void testNoClustersConfiguringInitiallyInRotationTrue() {
        String[] clusters = {};
        VipStatus v = createVipStatus(clusters, StateMonitor.Status.initializing, true, new ClustersStatus(), new MetricMock());
        assertTrue(v.isInRotation());
    }

    @Test
    void testInRotationMetricFollowsRotationState() {
        MetricMock metric = new MetricMock();
        String[] clusters = {"cluster1", "cluster2", "cluster3"};

        VipStatus v = createVipStatus(clusters, StateMonitor.Status.initializing, true, new ClustersStatus(), metric);
        assertFalse(v.isInRotation());
        assertEquals(0, metric.inRotation);
        addToRotation(clusters, v);
        assertTrue(v.isInRotation());
        assertEquals(1, metric.inRotation);
    }

    @Test
    void testClusterRemovalRemovedIsDown() {
        assertClusterRemoval(true, false);
    }

    @Test
    void testClusterRemovalRemovedIsUp() {
        assertClusterRemoval(false, false);
    }

    @Test
    void testClusterRemovalAnotherIsDown() {
        assertClusterRemoval(false, true);
    }

    private void assertClusterRemoval(boolean removedIsDown, boolean anotherIsDown) {
        ClustersStatus clustersStatus = new ClustersStatus();
        StateMonitor stateMonitor = createStateMonitor(StateMonitor.Status.initializing);

        String[] clusters = {"cluster1", "cluster2", "cluster3"};

        VipStatus v = createVipStatus(clusters, true, clustersStatus, stateMonitor, new MetricMock());
        assertFalse(v.isInRotation());
        assertEquals(StateMonitor.Status.initializing, stateMonitor.status());

        addToRotation(clusters, v);
        assertTrue(v.isInRotation());
        assertEquals(StateMonitor.Status.up, stateMonitor.status());

        String[] newClusters = {"cluster2", "cluster3"};
        if (removedIsDown)
            v.removeFromRotation("cluster1");
        if (anotherIsDown)
            v.removeFromRotation("cluster3");
        v = createVipStatus(newClusters, true, clustersStatus, stateMonitor, new MetricMock());
        assertTrue(v.isInRotation());
        assertEquals(StateMonitor.Status.up, stateMonitor.status());

        v.removeFromRotation(newClusters[0]);
        if ( ! anotherIsDown)
            assertTrue(v.isInRotation());

        v.removeFromRotation(newClusters[1]);
        assertFalse(v.isInRotation());  // Both remaining clusters are out
        assertEquals(StateMonitor.Status.down, stateMonitor.status());
    }

    private static QrSearchersConfig createSearchersConfig(String[] clusters) {
        var b = new QrSearchersConfig.Builder();
        for (String cluster : clusters) {
            var searchCluster = new QrSearchersConfig.Searchcluster.Builder();
            searchCluster.name(cluster);
            b.searchcluster(searchCluster);
        }
        return b.build();
    }

    private static VipStatus createVipStatus(String[] clusters,
                                             StateMonitor.Status startState,
                                             boolean initiallyInRotation,
                                             ClustersStatus clustersStatus,
                                             Metric metric) {
        return createVipStatus(clusters, initiallyInRotation, clustersStatus, createStateMonitor(startState), metric);
    }

    private static VipStatus createVipStatus(String[] clusters,
                                             boolean initiallyInRotation,
                                             ClustersStatus clustersStatus,
                                             StateMonitor stateMonitor,
                                             Metric metric) {
        return new VipStatus(createSearchersConfig(clusters),
                             new VipStatusConfig.Builder().initiallyInRotation(initiallyInRotation).build(),
                             clustersStatus,
                             stateMonitor,
                             metric);
    }

    private static StateMonitor createStateMonitor(StateMonitor.Status startState) {
        StateMonitor stateMonitor = StateMonitor.createForTesting();
        stateMonitor.status(startState);
        return stateMonitor;
    }

    private static void removeFromRotation(String[] clusters, VipStatus v) {
        for (String s : clusters)
            v.removeFromRotation(s);
    }

    private static void addToRotation(String[] clusters, VipStatus v) {
        for (String s : clusters)
            v.addToRotation(s);
   }

    private static void verifyStatus(String[] clusters, StateMonitor.Status status) {
        VipStatus v = createVipStatus(clusters, status, true, new ClustersStatus(), new MetricMock());
        removeFromRotation(clusters, v);
        // initial state
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[0]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[1]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());
    }

    private static class MetricMock implements Metric {

        int inRotation = 0;

        @Override
        public void add(String key, Number val, Context ctx) {
            throw new RuntimeException("Metric.add called unexpectedly");
        }

        @Override
        public void set(String key, Number val, Context ctx) {
            if ( ! key.equals("in_service"))
                throw new RuntimeException("Metric.set called with unexpected key " + key);
            inRotation = val.intValue();
        }

        @Override
        public Context createContext(Map<String, ?> properties) { return new EmptyContext(); }

        private static class EmptyContext implements Context {}

    }

}