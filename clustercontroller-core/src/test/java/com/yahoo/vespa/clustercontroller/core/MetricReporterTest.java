// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;
import org.junit.Test;

import java.util.Map;

import static com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext.hasMetricContext;
import static com.yahoo.vespa.clustercontroller.core.matchers.HasMetricContext.withDimension;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetricReporterTest {

    private static class Fixture {
        final MetricReporter mockReporter = mock(MetricReporter.class);
        final MetricUpdater metricUpdater = new MetricUpdater(mockReporter, 0);
        final ClusterFixture clusterFixture = ClusterFixture.forFlatCluster(10);

        Fixture() {
            when(mockReporter.createContext(any())).then(invocation ->
                    new HasMetricContext.MockContext((Map<String, ?>)invocation.getArguments()[0]));
        }
    }

    private static HasMetricContext.Dimension[] withNodeTypeDimension(String type) {
        // Dimensions that are always present
        HasMetricContext.Dimension controllerDim = withDimension("controller-index", "0");
        HasMetricContext.Dimension clusterDim = withDimension("cluster", "foo");
        // Node type-specific dimension
        HasMetricContext.Dimension nodeType = withDimension("node-type", type);
        return new HasMetricContext.Dimension[] { controllerDim, clusterDim, nodeType };
    }

    @Test
    public void metrics_are_emitted_for_different_node_state_counts() {
        Fixture f = new Fixture();
        f.metricUpdater.updateClusterStateMetrics(f.clusterFixture.cluster(),
                ClusterState.stateFromString("distributor:10 .1.s:d storage:9 .1.s:d .2.s:m .4.s:d"));

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

}
