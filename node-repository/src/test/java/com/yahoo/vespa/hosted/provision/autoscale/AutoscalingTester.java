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
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

class AutoscalingTester {

    private final ProvisioningTester provisioningTester;
    private final Autoscaler autoscaler;
    private final NodeMetricsDb db;

    public AutoscalingTester(NodeResources... resources) {
        provisioningTester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                             .flavorsConfig(asConfig(resources))
                                                             .build();
        for (NodeResources nodeResources : resources)
            provisioningTester.makeReadyNodes(20, nodeResources);

        db = new NodeMetricsDb();
        autoscaler = new Autoscaler(db, nodeRepository());
    }

    public ApplicationId applicationId(String applicationName) {
        return ApplicationId.from("tenant1", applicationName, "instance1");
    }

    public ClusterSpec clusterSpec(ClusterSpec.Type type, String clusterId) {
        return ClusterSpec.request(type,
                                   ClusterSpec.Id.from(clusterId),
                                   Version.fromString("7"),
                                   false);
    }

    public void deploy(ApplicationId application, ClusterSpec cluster, int count, NodeResources resources) {
        List<HostSpec> hosts = provisioningTester.prepare(application, cluster, Capacity.fromCount(count, resources), 1);
        provisioningTester.activate(application, hosts);
    }

    public void addMeasurements(float value, int count, ApplicationId applicationId) {
        List<Node> nodes = nodeRepository().getNodes(applicationId);
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                for (Resource resource : Resource.values())
                    db.add(node, resource, clock().instant(), value);
            }
        }
    }

    public Optional<ClusterResources> autoscale(ApplicationId application, ClusterSpec cluster) {
        return autoscaler.autoscale(application, cluster, nodeRepository().getNodes(application));
    }

    public void assertResources(String message,
                                int nodeCount, double approxCpu, double approxMemory, double approxDisk,
                                Optional<ClusterResources> actualResources) {
        double delta = 0.0000000001;
        assertTrue(message, actualResources.isPresent());
        assertEquals("Node count " + message, nodeCount, actualResources.get().count());
        assertEquals("Cpu: "    + message, approxCpu,    Math.round(actualResources.get().resources().vcpu()     * 10) / 10.0, delta);
        assertEquals("Memory: " + message, approxMemory, Math.round(actualResources.get().resources().memoryGb() * 10) / 10.0, delta);
        assertEquals("Disk: "   + message, approxDisk,   Math.round(actualResources.get().resources().diskGb()   * 10) / 10.0, delta);
    }

    public ManualClock clock() {
        return provisioningTester.clock();
    }

    public NodeRepository nodeRepository() {
        return provisioningTester.nodeRepository();
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
