// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class RebalancerTest {

    @Test
    public void testRebalancing() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        MetricsReporterTest.TestMetric metric = new MetricsReporterTest.TestMetric();
        Rebalancer rebalancer = new Rebalancer(tester.nodeRepository(),
                                               new IdentityHostResourcesCalculator(),
                                               Optional.empty(),
                                               metric,
                                               tester.clock(),
                                               Duration.ofMinutes(1));

        NodeResources cpuResources = new NodeResources(8, 4, 1, 0.1);
        NodeResources memResources = new NodeResources(4, 9, 1, 0.1);

        tester.makeReadyNodes(3, "flt", NodeType.host, 8);
        tester.deployZoneApp();

        // Cpu heavy application - causing 1 of these nodes to be skewed
        ApplicationId cpuApp = makeApplicationId("t1", "a1");
        deployApp(cpuApp, clusterSpec("c"), cpuResources, tester, 1);
        String cpuSkewedNodeHostname = tester.nodeRepository().getNodes(cpuApp).get(0).hostname();

        rebalancer.maintain();
        assertFalse("No better place to move the skewed node, so no action is taken",
                    tester.nodeRepository().getNode(cpuSkewedNodeHostname).get().status().wantToRetire());
        assertEquals(0.00325, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        tester.makeReadyNodes(1, "cpu", NodeType.host, 8);

        rebalancer.maintain();
        assertTrue("We can now move the node to the cpu skewed host to reduce skew",
                   tester.nodeRepository().getNode(cpuSkewedNodeHostname).get().status().wantToRetire());
        assertEquals("We're not actually moving the node here so skew remains steady",
                     0.00325, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        ApplicationId memApp = makeApplicationId("t2", "a2");
        deployApp(memApp, clusterSpec("c"), memResources, tester, 1);
        assertEquals("Assigned to a flat node as that causes least skew", "flt",
                     tester.nodeRepository().list().parentOf(tester.nodeRepository().getNodes(memApp).get(0)).get().flavor().name());
        String memSkewedNodeHostname = tester.nodeRepository().getNodes(memApp).get(0).hostname();

        tester.makeReadyNodes(1, "mem", NodeType.host, 8);
        rebalancer.maintain();
        assertEquals("Deploying the mem skewed app increased skew",
                     0.00752, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);
        assertFalse("The mem skewed node is not set want to retire as the cpu skewed node still is",
                    tester.nodeRepository().getNode(memSkewedNodeHostname).get().status().wantToRetire());

        Node cpuSkewedNode = tester.nodeRepository().getNode(cpuSkewedNodeHostname).get();
        tester.nodeRepository().write(cpuSkewedNode.withWantToRetire(false, Agent.system, tester.clock().instant()),
                                      tester.nodeRepository().lock(cpuSkewedNode));
        rebalancer.maintain();
        assertTrue("The mem skewed node is now scheduled for moving",
                    tester.nodeRepository().getNode(memSkewedNodeHostname).get().status().wantToRetire());
        assertFalse("(The cpu skewed node is not because it causes slightly less skew)",
                    tester.nodeRepository().getNode(cpuSkewedNodeHostname).get().status().wantToRetire());
    }

    private ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId), Version.fromString("6.42"), false);
    }

    private ApplicationId makeApplicationId(String tenant, String appName) {
        return ApplicationId.from(tenant, appName, "default");
    }

    private void deployApp(ApplicationId id, ClusterSpec spec, NodeResources flavor, ProvisioningTester tester, int nodeCount) {
        List<HostSpec> hostSpec = tester.prepare(id, spec, nodeCount, 1, flavor);
        tester.activate(id, new HashSet<>(hostSpec));
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 40, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

    private static class IdentityHostResourcesCalculator implements HostResourcesCalculator {

        @Override
        public NodeResources availableCapacityOf(NodeResources hostResources) {
            return hostResources;
        }

    }

}
