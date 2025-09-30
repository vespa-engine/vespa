// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import static com.yahoo.config.provision.NodeResources.Architecture.arm64;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class CapacityPoliciesTest {

    private static final Zone prodZone = new Zone(SystemName.Public, Environment.prod, RegionName.from("foo"));
    private static final Exclusivity exclusivity = new Exclusivity(prodZone, SharedHosts.empty());
    private static final ClusterSpec clusterSpecOldVersion = ClusterSpec.request(ClusterSpec.Type.admin, ClusterSpec.Id.from("cluster-controllers"))
                                                                      .vespaVersion("8.577.12")
                                                                      .build();

    private static final ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.admin, ClusterSpec.Id.from("cluster-controllers"))
                                                              .vespaVersion("8.597.12")
                                                              .build();

    @Test
    void testClusterControllerMemory() {
        {
            int contentNodes = 0;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpecOldVersion);
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 8;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpecOldVersion);
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 49;
            assertClusterControllerMemory(1.5, contentNodes, clusterSpecOldVersion);
            assertClusterControllerMemory(1.5, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 50;
            assertClusterControllerMemory(1.65, contentNodes, clusterSpecOldVersion);
            assertClusterControllerMemory(1.7, contentNodes, clusterSpec);
        }

        {
            int contentNodes = 50;
            // Explicitly defined cluster controller memory, 1.9 GiB
            assertClusterControllerMemory(1.9, contentNodes, 1.9, clusterSpecOldVersion);
            assertClusterControllerMemory(1.9, contentNodes, 1.9, clusterSpec);
        }

        {
            int contentNodes = 1000;
            assertClusterControllerMemory(2.1, contentNodes, clusterSpecOldVersion);
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

}
