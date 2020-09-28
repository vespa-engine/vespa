// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.Metric;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetrics;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.Resource;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .build();

        ApplicationId app1 = tester.makeApplicationId("app1");
        ClusterSpec cluster1 = tester.containerClusterSpec();

        ApplicationId app2 = tester.makeApplicationId("app2");
        ClusterSpec cluster2 = tester.containerClusterSpec();

        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(6.5, 9, 20, 0.1);

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, cluster1, Capacity.from(new ClusterResources(2, 1, lowResources))),
                app2, new MockDeployer.ApplicationContext(app2, cluster2, Capacity.from(new ClusterResources(2, 1, highResources))));
        MockDeployer deployer = new MockDeployer(tester.provisioner(), tester.clock(), apps);

        NodeMetricsDb nodeMetricsDb = new NodeMetricsDb(tester.nodeRepository());
        AutoscalingMaintainer maintainer = new AutoscalingMaintainer(tester.nodeRepository(),
                                                                     nodeMetricsDb,
                                                                     deployer,
                                                                     new TestMetric(),
                                                                     Duration.ofMinutes(1));
        maintainer.maintain(); // noop
        assertTrue(deployer.lastDeployTime(app1).isEmpty());
        assertTrue(deployer.lastDeployTime(app2).isEmpty());

        tester.makeReadyNodes(20, "flt", NodeType.host, 8);
        tester.deployZoneApp();

        tester.deploy(app1, cluster1, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    false, true));
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 9, 20, 0.1)),
                                                    false, true));

        maintainer.maintain(); // noop
        assertTrue(deployer.lastDeployTime(app1).isEmpty());
        assertTrue(deployer.lastDeployTime(app2).isEmpty());

        addMeasurements(Metric.cpu,    0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.cpu,    0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);

        maintainer.maintain();
        assertTrue(deployer.lastDeployTime(app1).isEmpty()); // since autoscaling is off
        assertTrue(deployer.lastDeployTime(app2).isPresent());
    }

    @Test
    public void autoscaling_discards_metric_values_from_before_rescaling() {
        // Setup
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .build();
        tester.clock().setInstant(Instant.ofEpochMilli(0));
        ApplicationId app1 = tester.makeApplicationId("app1");
        ClusterSpec cluster1 = tester.containerClusterSpec();
        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(8, 8, 20, 0.1);
        Capacity app1Capacity = Capacity.from(new ClusterResources(2, 1, lowResources),
                                              new ClusterResources(4, 2, highResources));
        Map<ApplicationId, MockDeployer.ApplicationContext> apps =
                Map.of(app1, new MockDeployer.ApplicationContext(app1, cluster1, app1Capacity));
        MockDeployer deployer = new MockDeployer(tester.provisioner(), tester.clock(), apps);
        NodeMetricsDb nodeMetricsDb = new NodeMetricsDb(tester.nodeRepository());
        AutoscalingMaintainer maintainer = new AutoscalingMaintainer(tester.nodeRepository(),
                                                                     nodeMetricsDb,
                                                                     deployer,
                                                                     new TestMetric(),
                                                                     Duration.ofMinutes(1));
        tester.makeReadyNodes(20, "flt", NodeType.host, 8);
        tester.deployZoneApp();

        // Initial deployment at time 0
        tester.deploy(app1, cluster1, app1Capacity);

        // Measure overload
        tester.clock().advance(Duration.ofSeconds(1));
        addMeasurements(Metric.cpu,    0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);

        // Causes autoscaling
        tester.clock().advance(Duration.ofSeconds(1));
        Instant firstMaintenanceTime = tester.clock().instant();
        maintainer.maintain();
        assertTrue(deployer.lastDeployTime(app1).isPresent());
        assertEquals(firstMaintenanceTime.toEpochMilli(), deployer.lastDeployTime(app1).get().toEpochMilli());
        assertEquals(1, nodeMetricsDb.getEvents(app1).size());
        assertEquals(app1, nodeMetricsDb.getEvents(app1).get(0).application());
        assertEquals(0, nodeMetricsDb.getEvents(app1).get(0).generation());
        assertEquals(firstMaintenanceTime.toEpochMilli(), nodeMetricsDb.getEvents(app1).get(0).time().toEpochMilli());

        // Measure overload still, since change is not applied, but metrics are discarded
        tester.clock().advance(Duration.ofSeconds(1));
        addMeasurements(Metric.cpu,    0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        tester.clock().advance(Duration.ofSeconds(1));
        maintainer.maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), deployer.lastDeployTime(app1).get().toEpochMilli());

        // Measure underload, but no autoscaling since we haven't measured we're on the new config generation
        tester.clock().advance(Duration.ofSeconds(1));
        addMeasurements(Metric.cpu,    0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        tester.clock().advance(Duration.ofSeconds(1));
        maintainer.maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), deployer.lastDeployTime(app1).get().toEpochMilli());

        // Add measurement of the expected generation, leading to rescaling
        tester.clock().advance(Duration.ofSeconds(1));
        addMeasurements(Metric.generation, 0, 1, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.cpu,    0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.memory, 0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Metric.disk,   0.1f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        //tester.clock().advance(Duration.ofSeconds(1));
        Instant lastMaintenanceTime = tester.clock().instant();
        maintainer.maintain();
        assertEquals(lastMaintenanceTime.toEpochMilli(), deployer.lastDeployTime(app1).get().toEpochMilli());
        assertEquals(2, nodeMetricsDb.getEvents(app1).size());
        assertEquals(1, nodeMetricsDb.getEvents(app1).get(1).generation());
    }

    public void addMeasurements(Metric metric, float value, int count, ApplicationId applicationId,
                                NodeRepository nodeRepository, NodeMetricsDb db) {
        List<Node> nodes = nodeRepository.getNodes(applicationId, Node.State.active);
        for (int i = 0; i < count; i++) {
            for (Node node : nodes)
                db.add(List.of(new NodeMetrics.MetricValue(node.hostname(),
                                                           metric.fullName(),
                                                           nodeRepository.clock().instant().getEpochSecond(),
                                                          value * 100))); // the metrics are in %
        }
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 40, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

}
