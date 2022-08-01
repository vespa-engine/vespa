// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        final MetricUpdater metricUpdater = new MetricUpdater(mockReporter, 0, CLUSTER_NAME);
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
        HasMetricContext.Dimension controllerDim = withDimension("controller-index", "0");
        HasMetricContext.Dimension clusterDim = withDimension("cluster", CLUSTER_NAME);
        HasMetricContext.Dimension clusteridDim = withDimension("clusterid", CLUSTER_NAME);
        return new HasMetricContext.Dimension[] { controllerDim, clusterDim, clusteridDim };
    }

    private static HasMetricContext.Dimension[] withNodeTypeDimension(String type) {
        // Node type-specific dimension
        HasMetricContext.Dimension nodeType = withDimension("node-type", type);
        var otherDims = withClusterDimension();
        return new HasMetricContext.Dimension[] { otherDims[0], otherDims[1], otherDims[2], nodeType };
    }

    @Test
    void metrics_are_emitted_for_different_node_state_counts() {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(),
                ClusterState.stateFromString("distributor:10 .1.s:d storage:9 .1.s:d .2.s:m .4.s:d"),
                new ResourceUsageStats());

        verify(f.mockReporter).set(eq("cluster-controller.up.count"), eq(9),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq("cluster-controller.up.count"), eq(6),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq("cluster-controller.down.count"), eq(1),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));
        verify(f.mockReporter).set(eq("cluster-controller.down.count"), eq(3),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
        verify(f.mockReporter).set(eq("cluster-controller.maintenance.count"), eq(1),
                argThat(hasMetricContext(withNodeTypeDimension("storage"))));
    }

    private void doTestRatiosInState(String clusterState, double distributorRatio, double storageRatio) {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(), ClusterState.stateFromString(clusterState),
                new ResourceUsageStats());

        verify(f.mockReporter).set(eq("cluster-controller.available-nodes.ratio"),
                doubleThat(closeTo(distributorRatio, 0.0001)),
                argThat(hasMetricContext(withNodeTypeDimension("distributor"))));

        verify(f.mockReporter).set(eq("cluster-controller.available-nodes.ratio"),
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
                new ResourceUsageStats(0.5, 0.6, 5, 0.7, 0.8));

        verify(f.mockReporter).set(eq("cluster-controller.resource_usage.max_disk_utilization"),
                doubleThat(closeTo(0.5, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq("cluster-controller.resource_usage.max_memory_utilization"),
                doubleThat(closeTo(0.6, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq("cluster-controller.resource_usage.nodes_above_limit"),
                eq(5),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq("cluster-controller.resource_usage.disk_limit"),
                doubleThat(closeTo(0.7, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));

        verify(f.mockReporter).set(eq("cluster-controller.resource_usage.memory_limit"),
                doubleThat(closeTo(0.8, 0.0001)),
                argThat(hasMetricContext(withClusterDimension())));
    }

}
