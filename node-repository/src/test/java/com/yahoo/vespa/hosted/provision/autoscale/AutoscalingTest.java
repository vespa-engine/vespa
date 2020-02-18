// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void testAutoscalingNodeCount() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod,
                                                                                   RegionName.from("us-east")))
                                                                    .flavorsConfig(asConfig(resources))
                                                                    .build();
        tester.makeReadyNodes(21, resources);


        ApplicationId application1 = tester.makeApplicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container,
                                                  ClusterSpec.Id.from("test"),
                                                  Version.fromString("7"),
                                                  false);

        // deploy
        List<HostSpec> hosts = tester.prepare(application1, cluster, Capacity.fromCount(5, resources), 1);
        tester.activate(application1, hosts);

        NodeMetricsDb db = new NodeMetricsDb();
        Autoscaler autoscaler = new Autoscaler(db, tester.nodeRepository());

        assertTrue("No metrics -> No change",
                   autoscaler.autoscale(application1, cluster, tester.nodeRepository().getNodes(application1)).isEmpty());

        addMeasurements( 0.3f, 60, tester.clock(), tester.nodeRepository().getNodes(application1), db);
        assertTrue("Too few metrics -> No change",
                   autoscaler.autoscale(application1, cluster, tester.nodeRepository().getNodes(application1)).isEmpty());

        addMeasurements( 0.3f, 60, tester.clock(), tester.nodeRepository().getNodes(application1), db);
        assertResources("Scaling to more nodes since we spend too much cpu and can't increase node size",
                        10, 2.5, 23.8, 23.8,
                        autoscaler.autoscale(application1, cluster, tester.nodeRepository().getNodes(application1)));
    }

    private void assertResources(String message,
                                 int nodeCount, double approxCpu, double approxMemory, double approxDisk,
                                 Optional<ClusterResources> actualResources) {
        double delta = 0.0000000001;
        assertTrue(message, actualResources.isPresent());
        assertEquals("Node count " + message, nodeCount, actualResources.get().count());
        assertEquals("Cpu: "    + message, approxCpu,    Math.round(actualResources.get().resources().vcpu()     * 10) / 10.0, delta);
        assertEquals("Memory: " + message, approxMemory, Math.round(actualResources.get().resources().memoryGb() * 10) / 10.0, delta);
        assertEquals("Disk: "   + message, approxDisk,   Math.round(actualResources.get().resources().diskGb()   * 10) / 10.0, delta);
    }

    private void addMeasurements(float value, int count, ManualClock clock, List<Node> nodes, NodeMetricsDb db) {
        for (int i = 0; i < count; i++) {
            clock.advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                for (Resource resource : Resource.values())
                    db.add(node, resource, clock.instant(), value);
            }
        }
    }

    private FlavorsConfig asConfig(NodeResources ... resources) {
        FlavorsConfig.Builder b = new FlavorsConfig.Builder();
        int i = 0;
        for (NodeResources nodeResources : resources) {
            FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
            flavor.name("flavor" + (i++));
            flavor.minCpuCores(nodeResources.vcpu());
            flavor.minMainMemoryAvailableGb(nodeResources.memoryGb());
            flavor.minDiskAvailableGb(nodeResources.diskGb());
            flavor.bandwidth(nodeResources.bandwidthGbps() * 1000);
            b.flavor(flavor);
        }
        return b.build();
    }

}
