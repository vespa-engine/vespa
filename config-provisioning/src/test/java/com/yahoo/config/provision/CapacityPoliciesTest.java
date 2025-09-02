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
    private static final ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.admin, ClusterSpec.Id.from("cluster-controllers"))
                                                                      .vespaVersion("8.577.12")
                                                                      .build();

    @Test
    void testClusterControllerMemory() {
        {
            int contentNodes = 0;
            assertClusterControllerMemory(1.5, contentNodes);
        }

        {
            int contentNodes = 8;
            assertClusterControllerMemory(1.5, contentNodes);
        }

        {
            int contentNodes = 51;
            assertClusterControllerMemory(1.65, contentNodes);
        }

        {
            int contentNodes = 51;
            // Explicitly defined cluster controller memory, 1.9 GiB
            assertClusterControllerMemory(1.9, contentNodes, 1.9);
        }

        {
            int contentNodes = 1000;
            assertClusterControllerMemory(2.1, contentNodes);
        }
    }

    void assertClusterControllerMemory(double expected, long contentNodes) {
        assertClusterControllerMemory(expected, contentNodes, 0.0);
    }

    void assertClusterControllerMemory(double expected, long contentNodes, double clusterControllerMemoryOverride) {
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
