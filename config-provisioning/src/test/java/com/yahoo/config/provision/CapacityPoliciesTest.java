// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static com.yahoo.config.provision.NodeResources.Architecture.arm64;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class CapacityPoliciesTest {

    private static final Zone prodZone = new Zone(SystemName.Public, Environment.prod, RegionName.from("foo"));
    private static final Zone devZone = new Zone(Environment.dev, RegionName.from("us-east"));
    private static final Exclusivity exclusivity = new Exclusivity(prodZone, SharedHosts.empty());
    private static final ClusterSpec clusterSpec = ClusterSpec.builder(ClusterSpec.Type.admin,
                                                                       ClusterSpec.Id.from("cluster-controllers"),
                                                                       Capacity.unspecified(),
                                                                       new Version("8.597.12"))
                                                              .build();

    @Test
    void testClusterControllerMemory() {
        {
            int contentNodes = 0;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 8;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 49;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 50;
            assertClusterControllerMemory(1.7, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 50;
            // Explicitly defined cluster controller memory, 1.9 GiB
            assertClusterControllerMemory(1.9, contentNodes, 1.9, clusterSpec);
        }

        {
            int contentNodes = 1000;
            assertClusterControllerMemory(2.3, contentNodes, clusterSpec);
        }
    }

    @Test
    void testCapacityPoliciesForKubernetes() {
        var zone = new Zone(SystemName.kubernetes, Environment.prod, RegionName.from("foo"));
        var clusterController = new ClusterResources(2, 1, NodeResources.unspecified());
        var capacityPolicies = new CapacityPolicies(
                zone, exclusivity, ApplicationId.defaultId(), new CapacityPolicies.Tuning(arm64, 1, 0, 0));

        var clusterControllerMemoryGiB = capacityPolicies.specifyFully(clusterController, clusterSpec)
                .nodeResources()
                .memoryGiB();
        assertEquals(1.5, clusterControllerMemoryGiB, 0.01, "Expected the default cluster controller memory size for arm64");
    }

    void assertClusterControllerMemory(double expected, long contentNodes, ClusterSpec clusterSpec) {
        assertClusterControllerMemory(expected, contentNodes, 0.0, clusterSpec);
    }

    void assertClusterControllerMemory(double expected, long contentNodes, double clusterControllerMemoryOverride, ClusterSpec clusterSpec) {
        ApplicationId applicationId = ApplicationId.defaultId();
        var clusterController = new ClusterResources(3, 1, NodeResources.unspecified());
        var capacityPolicies = new CapacityPolicies(prodZone, exclusivity, applicationId,
                                                    new CapacityPolicies.Tuning(arm64, 0.0, clusterControllerMemoryOverride, contentNodes));
        assertEquals(expected, capacityPolicies.specifyFully(clusterController, clusterSpec)
                                          .nodeResources()
                                          .memoryGiB(),
                     0.01);
    }

    @Test
    void testUnspecifiedDiskIsIncreasedForContainerNodes() {
        var zone = new Zone(SystemName.Public, Environment.prod, RegionName.from("us-east"));
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0));
        var containerCluster = ClusterSpec.builder(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("container1"),
                                                   Capacity.unspecified(),
                                                   new Version("8.0"))
                                          .build();

        // Request 48 GB memory with unspecified disk (0)
        // Default would be 50GB, but minimum is 2x48 = 96GB
        NodeResources requestedResources = new NodeResources(2, 48, 0, 0.3);
        NodeResources specifiedResources = capacityPolicies.specifyFully(requestedResources, containerCluster);

        assertEquals(48, specifiedResources.memoryGiB(), 0.01);
        assertEquals(96, specifiedResources.diskGb(), 0.01, "Disk should be increased to 2x memory for container nodes");
    }

    @Test
    void testUnspecifiedDiskIsIncreasedForContentNodes() {
        var zone = new Zone(SystemName.Public, Environment.prod, RegionName.from("us-east"));
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0));
        var contentCluster = ClusterSpec.builder(ClusterSpec.Type.content,
                                                 ClusterSpec.Id.from("content1"),
                                                 Capacity.unspecified(),
                                                 new Version("8.0"))
                                        .build();

        // Request 128 GB memory with unspecified disk (0)
        // Default would be 300GB, but minimum is 3x128 = 384GB
        NodeResources requestedResources = new NodeResources(4, 128, 0, 0.3);
        NodeResources specifiedResources = capacityPolicies.specifyFully(requestedResources, contentCluster);

        assertEquals(128, specifiedResources.memoryGiB(), 0.01);
        assertEquals(384, specifiedResources.diskGb(), 0.01, "Disk should be increased to 3x memory for content nodes");
    }

    @Test
    void testUnspecifiedDiskUsesDefaultWhenSufficient() {
        var zone = new Zone(SystemName.Public, Environment.prod, RegionName.from("us-east"));
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0));
        var containerCluster = ClusterSpec.builder(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("container1"),
                                                   Capacity.unspecified(),
                                                   new Version("8.0"))
                                          .build();

        // Request 20 GB memory with unspecified disk (0)
        // Minimum is 2x20 = 40GB, default is 50GB -> default is sufficient
        NodeResources requestedResources = new NodeResources(2, 20, 0, 0.3);
        NodeResources specifiedResources = capacityPolicies.specifyFully(requestedResources, containerCluster);

        assertEquals(20, specifiedResources.memoryGiB(), 0.01);
        assertEquals(50, specifiedResources.diskGb(), 0.01, "Disk should use default when it's already sufficient");
    }

    @Test
    void testExplicitlySpecifiedDiskIsPreserved() {
        var zone = new Zone(SystemName.Public, Environment.prod, RegionName.from("us-east"));
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0));
        var containerCluster = ClusterSpec.builder(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("container1"),
                                                   Capacity.unspecified(),
                                                   new Version("8.0"))
                                          .build();

        // Request 48 GB memory with explicit 150 GB disk (more than 2x minimum of 96GB)
        NodeResources requestedResources = new NodeResources(2, 48, 150, 0.3);
        NodeResources specifiedResources = capacityPolicies.specifyFully(requestedResources, containerCluster);

        assertEquals(48, specifiedResources.memoryGiB(), 0.01);
        assertEquals(150, specifiedResources.diskGb(), 0.01, "Explicitly specified disk should be preserved");
    }

    @Test
    void testDevResourcesNoCpuCapSpecified() {
        var devExclusivity = new Exclusivity(devZone, SharedHosts.empty());
        var capacityPolicies = new CapacityPolicies(devZone, devExclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0), 0.0);
        NodeResources requestedResources = new NodeResources(4, 16, 50, 0.3);
        var capacity = Capacity.from(new ClusterResources(2, 1, requestedResources));
        var cluster = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), capacity, Version.emptyVersion)
                                 .exclusive(false)
                                 .build();
        var result = capacityPolicies.applyOn(cluster);

        assertEquals(0.1, result.minResources().nodeResources().vcpu(), 0.01, "CPU should be capped to 0.1 in dev when cpuCapInDev is false");
        assertEquals(0.1, result.minResources().nodeResources().bandwidthGbps(), 0.01, "Bandwidth should be capped to 0.1 in dev when cpuCapInDev is false");
    }

    @Test
    void testDevResourcesWithoutCpuCap() {
        var devExclusivity = new Exclusivity(devZone, SharedHosts.empty());
        double cpuCap = 0.0;
        var capacityPolicies = new CapacityPolicies(devZone, devExclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0), cpuCap);
        NodeResources requestedResources = new NodeResources(4, 16, 50, 0.3);
        var capacity = Capacity.from(new ClusterResources(2, 1, requestedResources));
        var cluster = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), capacity, Version.emptyVersion)
                                 .exclusive(false)
                                 .build();
        var result = capacityPolicies.applyOn(cluster);

        assertEquals(0.1, result.minResources().nodeResources().vcpu(), 0.01, "CPU should be capped to 0.1 in dev when cpuCapInDev is false");
        assertEquals(0.1, result.minResources().nodeResources().bandwidthGbps(), 0.01, "Bandwidth should be capped to 0.1 in dev when cpuCapInDev is false");
    }

    @Test
    void testDevResourcesWithCpuCap() {
        var devExclusivity = new Exclusivity(devZone, SharedHosts.empty());
        double cpuCap = 1.0;
        var capacityPolicies = new CapacityPolicies(devZone, devExclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0), cpuCap);
        NodeResources requestedResources = new NodeResources(4, 16, 50, 0.3);
        var capacity = Capacity.from(new ClusterResources(2, 1, requestedResources));
        var cluster = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), capacity, Version.emptyVersion)
                                 .exclusive(false)
                                 .build();
        var result = capacityPolicies.applyOn(cluster);

        assertEquals(4, result.minResources().nodeResources().vcpu(), 0.01, "CPU should not be capped in dev when cpuCapInDev is true");
        assertEquals(0.1, result.minResources().nodeResources().bandwidthGbps(), 0.01, "Bandwidth should still be capped to 0.1 in dev");
    }

    @Test
    void testExplicitlySpecifiedInsufficientDiskIsPreserved() {
        var zone = new Zone(SystemName.Public, Environment.prod, RegionName.from("us-east"));
        var capacityPolicies = new CapacityPolicies(zone, exclusivity, ApplicationId.defaultId(),
                                                    new CapacityPolicies.Tuning(arm64, 0.0, 0));
        var contentCluster = ClusterSpec.builder(ClusterSpec.Type.content,
                                                 ClusterSpec.Id.from("content1"),
                                                 Capacity.unspecified(),
                                                 new Version("8.0"))
                                         .build();

        // Request 128 GB memory with explicit 300 GB disk (less than 3x minimum of 384GB)
        NodeResources requestedResources = new NodeResources(4, 128, 300, 0.3);
        NodeResources specifiedResources = capacityPolicies.specifyFully(requestedResources, contentCluster);

        assertEquals(128, specifiedResources.memoryGiB(), 0.01);
        assertEquals(300, specifiedResources.diskGb(), 0.01, "Explicitly specified disk should be preserved even when insufficient");
    }

    @Test
    void testDevDownscalesToOneNodePerAvailabilityZone() {
        var container = new DevCluster(ClusterSpec.Type.container, 6, 1);
        assertEquals(1, container.inZones(1).nodes());
        assertEquals(2, container.inZones(2).nodes());
        assertEquals(3, container.inZones(3).nodes());
        assertEquals(1, container.inZones(3).groups());

        var content = new DevCluster(ClusterSpec.Type.content, 12, 4);
        assertEquals(1, content.inZones(1).nodes());
        assertEquals(1, content.inZones(1).groups());
        assertEquals(2, content.inZones(2).nodes());
        assertEquals(2, content.inZones(2).groups());
        assertEquals(4, content.inZones(4).nodes());
        assertEquals(4, content.inZones(4).groups());
    }

    private static class DevCluster {

        private final CapacityPolicies policies = new CapacityPolicies(devZone, new Exclusivity(devZone, SharedHosts.empty()),
                ApplicationId.defaultId(), new CapacityPolicies.Tuning(arm64, 0.0, 0));
        private final ClusterSpec cluster;

        DevCluster(ClusterSpec.Type type, int nodes, int groups) {
            var capacity = Capacity.from(new ClusterResources(nodes, groups, new NodeResources(4, 16, 50, 0.3)));
            cluster = ClusterSpec.builder(type, ClusterSpec.Id.from("cluster1"), capacity, new Version("8")).build();
        }

        ClusterResources inZones(int count) {
            var zones = IntStream.rangeClosed(1, count).mapToObj(i -> AzName.from("az" + i)).toList();
            return policies.applyOn(cluster.withAvailabilityZones(zones)).minResources();
        }

    }

}
