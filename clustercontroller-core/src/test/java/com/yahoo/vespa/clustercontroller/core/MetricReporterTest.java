// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CLUSTER;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CLUSTER_ID;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.CONTROLLER_INDEX;
import static com.yahoo.vespa.clustercontroller.core.MetricDimensionNames.NODE_TYPE;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.AVAILABLE_NODES_RATIO;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.CLUSTER_CONTROLLER;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.NODES_NOT_CONVERGED;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.DISK_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MAX_DISK_UTILIZATION;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MAX_MEMORY_UTILIZATION;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.MEMORY_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.ResourceUsage.NODES_ABOVE_LIMIT;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.STORED_DOCUMENT_BYTES;
import static com.yahoo.vespa.clustercontroller.core.MetricNames.STORED_DOCUMENT_COUNT;
import static com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext.hasMetricContext;
import static com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext.withDimension;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.mockito.hamcrest.MockitoHamcrest.doubleThat;

public class MetricReporterTest {

    private static final String CLUSTER_NAME = "foo";

    private static class Fixture {
        final MetricReporter mockReporter = mock(MetricReporter.class);
        final FakeTimer timer = new FakeTimer();
        final MetricUpdater metricUpdater = new MetricUpdater(mockReporter, timer, 0, CLUSTER_NAME);
        final ClusterFixture clusterFixture;

        Fixture() {
            this(10);
        }

        Fixture(int nodes) {
            clusterFixture = ClusterFixture.forFlatCluster(nodes);
            when(mockReporter.createContext(any())).then(invocation -> {
                @SuppressWarnings("unchecked") Map<String, ?> arg = (Map<String, ?>)invocation.getArguments()[0];
                return new HasMetricContext.MockContext(arg);
            });
        }
    }

    private static HasMetricContext.Dimension[] withClusterDimension() {
        // Dimensions that are always present
        HasMetricContext.Dimension controllerDim = withDimension(CONTROLLER_INDEX, "0");
        HasMetricContext.Dimension clusterDim = withDimension(CLUSTER, CLUSTER_NAME);
        HasMetricContext.Dimension clusteridDim = withDimension(CLUSTER_ID, CLUSTER_NAME);
        return new HasMetricContext.Dimension[] { controllerDim, clusterDim, clusteridDim };
    }

    private static HasMetricContext.Dimension[] withNodeTypeDimension(String type) {
        // Node type-specific dimension
        HasMetricContext.Dimension nodeType = withDimension(NODE_TYPE, type);
        var otherDims = withClusterDimension();
        return new HasMetricContext.Dimension[] { otherDims[0], otherDims[1], otherDims[2], nodeType };
    }

    private static String metricName(String subName) {
        return "%s.%s".formatted(CLUSTER_CONTROLLER, subName);
    }

    @Test
    void metrics_are_emitted_for_different_node_state_counts() {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(),
                ClusterState.stateFromString("distributor:10 .1.s:d storage:9 .1.s:d .2.s:m .4.s:d"),
                new ResourceUsageStats(), Instant.ofEpochMilli(12345));

        verify(f.mockReporter).set(eq(metricName("up.count")), eq(9),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq(metricName("up.count")), eq(6),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq(metricName("down.count")), eq(1),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq(metricName("down.count")), eq(3),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq(metricName("maintenance.count")), eq(1),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
    }

    private void doTestRatiosInState(String clusterState, double distributorRatio, double storageRatio) {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(), ClusterState.stateFromString(clusterState),
                new ResourceUsageStats(), Instant.ofEpochMilli(12345));

        verify(f.mockReporter).set(eq(metricName(AVAILABLE_NODES_RATIO)),
                doubleThat(closeTo(distributorRatio, 0.0001)),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));

        verify(f.mockReporter).set(eq(metricName(AVAILABLE_NODES_RATIO)),
                doubleThat(closeTo(storageRatio, 0.0001)),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
    }

    @Test
    void metrics_are_emitted_for_partial_node_availability_ratio() {
        // Only Up, Init, Retired and Maintenance are counted as available states
        doTestRatiosInState("distributor:10 .1.s:d storage:9 .1.s:d .2.s:m .4.s:r .5.s:i .6.s:s", 0.9, 0.7);
    }

    @Test
    void metrics_are_emitted_for_full_node_availability_ratio() {
        doTestRatiosInState("distributor:10 storage:10", 1.0, 1.0);
    }

    @Test
    void metrics_are_emitted_for_zero_node_availability_ratio() {
        doTestRatiosInState("cluster:d", 0.0, 0.0);
    }

    @Test
    void maintenance_mode_is_counted_as_available() {
        doTestRatiosInState("distributor:10 storage:10 .0.s:m", 1.0, 1.0);
    }

    @Test
    void metrics_are_emitted_for_resource_usage() {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(),
                ClusterState.stateFromString("distributor:10 storage:10"),
                new ResourceUsageStats(0.5, 0.6, 5, 0.7, 0.8), Instant.ofEpochMilli(12345));

        verify(f.mockReporter).set(eq(metricName(MAX_DISK_UTILIZATION)),
                doubleThat(closeTo(0.5, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq(metricName(MAX_MEMORY_UTILIZATION)),
                doubleThat(closeTo(0.6, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq(metricName(NODES_ABOVE_LIMIT)),
                eq(5),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq(metricName(DISK_LIMIT)),
                doubleThat(closeTo(0.7, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq(metricName(MEMORY_LIMIT)),
                doubleThat(closeTo(0.8, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));
    }

    private static class ConvergenceFixture extends Fixture {

        String stateString;
        Instant stateBroadcastTime;

        ConvergenceFixture(String stateString) {
            super(5);
            this.stateString = stateString;
            if (!stateString.isEmpty()) {
                setUpFixturePendingVersions();
            }

            metricUpdater.setStateVersionConvergenceGracePeriod(Duration.ofSeconds(10));
            stateBroadcastTime = timer.getCurrentWallClockTime();
        }

        // Sets pending state versions for 5 distributors and storage nodes:
        //  - distributor: 2 converged, 3 not converged
        //  - storage: 3 converged, 2 not converged
        private void setUpFixturePendingVersions() {
            var pendingBundle = ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.withoutAnnotations(
                    ClusterState.stateFromString(stateString)));
            for (int i = 0; i < 5; ++i) {
                clusterFixture.cluster().getNodeInfo(Node.ofDistributor(i)).setClusterStateVersionBundleSent(pendingBundle);
                clusterFixture.cluster().getNodeInfo(Node.ofStorage(i)).setClusterStateVersionBundleSent(pendingBundle);
            }
            clusterFixture.cluster().getNodeInfo(Node.ofDistributor(0)).setClusterStateBundleVersionAcknowledged(100, false); // NACK
            clusterFixture.cluster().getNodeInfo(Node.ofDistributor(1)).setClusterStateBundleVersionAcknowledged(100, true);
            clusterFixture.cluster().getNodeInfo(Node.ofDistributor(4)).setClusterStateBundleVersionAcknowledged(100, true);
            // Heard nothing from distributors {2, 3} yet.
            clusterFixture.cluster().getNodeInfo(Node.ofStorage(0)).setClusterStateBundleVersionAcknowledged(100, true);
            clusterFixture.cluster().getNodeInfo(Node.ofStorage(1)).setClusterStateBundleVersionAcknowledged(100, true);
            clusterFixture.cluster().getNodeInfo(Node.ofStorage(2)).setClusterStateBundleVersionAcknowledged(100, true);
            // Heard nothing from storage {3, 4} yet.
        }

        void advanceTimeAndVerifyMetrics(Duration delta, int expectedDistr, int expectedStorage) {
            timer.advanceTime(delta.toMillis());
            metricUpdater.updateClusterStateMetrics(clusterFixture.cluster(),
                    ClusterState.stateFromString(stateString),
                    new ResourceUsageStats(), stateBroadcastTime);

            verify(mockReporter).set(eq(metricName(NODES_NOT_CONVERGED)), eq(expectedDistr),
                    argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
            verify(mockReporter).set(eq(metricName(NODES_NOT_CONVERGED)), eq(expectedStorage),
                    argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        }

    }

    @Test
    void nodes_not_converged_metric_not_incremented_when_within_grace_period() {
        var f = new ConvergenceFixture("version:100 distributor:5 storage:5");
        // 9 seconds passed since state broadcast; should not tag nodes as not converged
        f.advanceTimeAndVerifyMetrics(Duration.ofMillis(9000), 0, 0);
    }

    @Test
    void nodes_not_converged_metric_incremented_when_outside_grace_period() {
        var f = new ConvergenceFixture("version:100 distributor:5 storage:5");
        // 10+ seconds passed since state broadcast; _should_ tag nodes as not converged
        f.advanceTimeAndVerifyMetrics(Duration.ofMillis(10001), 3, 2);
    }

    @Test
    void only_count_nodes_in_available_states_as_non_converging() {
        var f = new ConvergenceFixture("version:100 distributor:5 .0.s:d .2.s:d .3.s:d storage:5 .3.s:m .4.s:d");
        // Should not count non-converged nodes, as they are not in an available state
        f.advanceTimeAndVerifyMetrics(Duration.ofMillis(10001), 0, 0);
    }

    @Test
    void do_not_treat_nodes_as_non_converged_if_no_state_published_yet() {
        var f = new ConvergenceFixture(""); // no versioned state
        f.advanceTimeAndVerifyMetrics(Duration.ofMillis(10001), 0, 0);
    }

    @Test
    void metrics_are_emitted_for_cluster_document_stats() {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterDocumentMetrics(12345, 6789000);

        // Metric dimensions are set indirectly via the metric updater's default context
        verify(f.mockReporter).set(eq(metricName(STORED_DOCUMENT_COUNT)), eq(12345L), any());
        verify(f.mockReporter).set(eq(metricName(STORED_DOCUMENT_BYTES)), eq(6789000L), any());
    }

    @Test
    void leadership_loss_edge_resets_cluster_metrics() {
        Fixture f = new Fixture();
        f.metricUpdater.updateMasterState(false);

        // Node state counts are reset
        verify(f.mockReporter).set(eq(metricName("up.count")), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq(metricName("up.count")), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq(metricName("down.count")), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq(metricName("down.count")), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq(metricName("maintenance.count")), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));

        // Non-convergence metrics are reset
        verify(f.mockReporter).set(eq(metricName(NODES_NOT_CONVERGED)), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq(metricName(NODES_NOT_CONVERGED)), eq(0),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
    }

}
