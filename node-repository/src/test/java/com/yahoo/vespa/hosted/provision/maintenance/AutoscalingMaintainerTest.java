// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.autoscale.Metric;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the autoscaling maintainer integration.
 * The specific recommendations of the autoscaler are not tested here.
 *
 * @author bratseth
 */
public class AutoscalingMaintainerTest {

    @Test
    public void testAutoscalingMaintainer() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();

        ApplicationId app2 = AutoscalingMaintainerTester.makeApplicationId("app2");
        ClusterSpec cluster2 = AutoscalingMaintainerTester.containerClusterSpec();

        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(6.5, 9, 20, 0.1);

        AutoscalingMaintainerTester tester = new AutoscalingMaintainerTester(
                new MockDeployer.ApplicationContext(app1, cluster1, Capacity.from(new ClusterResources(2, 1, lowResources))),
                new MockDeployer.ApplicationContext(app2, cluster2, Capacity.from(new ClusterResources(2, 1, highResources))));


        tester.maintainer().maintain(); // noop
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty());
        assertTrue(tester.deployer().lastDeployTime(app2).isEmpty());

        tester.deploy(app1, cluster1, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    false, true));
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 9, 20, 0.1)),
                                                    false, true));

        tester.maintainer().maintain(); // noop
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty());
        assertTrue(tester.deployer().lastDeployTime(app2).isEmpty());

        tester.addMeasurements(Metric.cpu,    0.9f, 500, app1);
        tester.addMeasurements(Metric.memory, 0.9f, 500, app1);
        tester.addMeasurements(Metric.disk,   0.9f, 500, app1);
        tester.addMeasurements(Metric.cpu,    0.9f, 500, app2);
        tester.addMeasurements(Metric.memory, 0.9f, 500, app2);
        tester.addMeasurements(Metric.disk,   0.9f, 500, app2);

        tester.maintainer().maintain();
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty()); // since autoscaling is off
        assertTrue(tester.deployer().lastDeployTime(app2).isPresent());
    }

    @Test
    public void autoscaling_discards_metric_values_from_before_rescaling() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(8, 8, 20, 0.1);
        Capacity app1Capacity = Capacity.from(new ClusterResources(2, 1, lowResources),
                                              new ClusterResources(4, 2, highResources));
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, app1Capacity));

        // Initial deployment at time 0
        tester.deploy(app1, cluster1, app1Capacity);

        // Measure overload
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(Metric.cpu,    0.9f, 500, app1);
        tester.addMeasurements(Metric.memory, 0.9f, 500, app1);
        tester.addMeasurements(Metric.disk,   0.9f, 500, app1);

        // Causes autoscaling
        tester.clock().advance(Duration.ofSeconds(1));
        Instant firstMaintenanceTime = tester.clock().instant();
        tester.maintainer().maintain();
        assertTrue(tester.deployer().lastDeployTime(app1).isPresent());
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        assertEquals(1, tester.nodeMetricsDb().getEvents(app1).size());
        assertEquals(app1, tester.nodeMetricsDb().getEvents(app1).get(0).application());
        assertEquals(0, tester.nodeMetricsDb().getEvents(app1).get(0).generation());
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.nodeMetricsDb().getEvents(app1).get(0).time().toEpochMilli());

        // Measure overload still, since change is not applied, but metrics are discarded
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(Metric.cpu,    0.9f, 500, app1);
        tester.addMeasurements(Metric.memory, 0.9f, 500, app1);
        tester.addMeasurements(Metric.disk,   0.9f, 500, app1);
        tester.clock().advance(Duration.ofSeconds(1));
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Measure underload, but no autoscaling since we haven't measured we're on the new config generation
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(Metric.cpu,    0.1f, 500, app1);
        tester.addMeasurements(Metric.memory, 0.1f, 500, app1);
        tester.addMeasurements(Metric.disk,   0.1f, 500, app1);
        tester.clock().advance(Duration.ofSeconds(1));
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Add measurement of the expected generation, leading to rescaling
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(Metric.generation, 0, 1, app1);
        tester.addMeasurements(Metric.cpu,    0.1f, 500, app1);
        tester.addMeasurements(Metric.memory, 0.1f, 500, app1);
        tester.addMeasurements(Metric.disk,   0.1f, 500, app1);
        //tester.clock().advance(Duration.ofSeconds(1));
        Instant lastMaintenanceTime = tester.clock().instant();
        tester.maintainer().maintain();
        assertEquals(lastMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        assertEquals(2, tester.nodeMetricsDb().getEvents(app1).size());
        assertEquals(1, tester.nodeMetricsDb().getEvents(app1).get(1).generation());
    }

}
