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
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetrics;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.Resource;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3"))).flavorsConfig(flavorsConfig()).build();

        ApplicationId app1 = tester.makeApplicationId("app1");
        ClusterSpec cluster1 = tester.clusterSpec();

        ApplicationId app2 = tester.makeApplicationId("app2");
        ClusterSpec cluster2 = tester.clusterSpec();

        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(6.5, 9, 20, 0.1);

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, cluster1, Capacity.from(new ClusterResources(2, 1, lowResources))),
                app2, new MockDeployer.ApplicationContext(app2, cluster2, Capacity.from(new ClusterResources(2, 1, highResources))));
        MockDeployer deployer = new MockDeployer(tester.provisioner(), tester.clock(), apps);

        NodeMetricsDb nodeMetricsDb = new NodeMetricsDb();
        AutoscalingMaintainer maintainer = new AutoscalingMaintainer(tester.nodeRepository(),
                                                                     tester.identityHostResourcesCalculator(),
                                                                     nodeMetricsDb,
                                                                     deployer,
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

        addMeasurements(Resource.cpu,    0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Resource.memory, 0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Resource.disk,   0.9f, 500, app1, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Resource.cpu,    0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Resource.memory, 0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);
        addMeasurements(Resource.disk,   0.9f, 500, app2, tester.nodeRepository(), nodeMetricsDb);

        maintainer.maintain();
        assertTrue(deployer.lastDeployTime(app1).isEmpty()); // since autoscaling is off
        assertTrue(deployer.lastDeployTime(app2).isPresent());
    }

    public void addMeasurements(Resource resource, float value, int count, ApplicationId applicationId,
                                NodeRepository nodeRepository, NodeMetricsDb db) {
        List<Node> nodes = nodeRepository.getNodes(applicationId, Node.State.active);
        for (int i = 0; i < count; i++) {
            for (Node node : nodes)
                db.add(List.of(new NodeMetrics.MetricValue(node.hostname(),
                                                           resource.metricName(),
                                                           nodeRepository.clock().instant().toEpochMilli(),
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
