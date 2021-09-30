// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class DirtyExpirerTest {

    @Test
    public void assert_allocation_after_expiry() {
        assertAllocationAfterExpiry(true);
        assertAllocationAfterExpiry(false);
    }

    private void assertAllocationAfterExpiry(boolean dynamicProvisioning) {
        Zone zone = new Zone(Cloud.builder().dynamicProvisioning(dynamicProvisioning).build(), SystemName.main, Environment.prod, RegionName.from("us-east"));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                .hostProvisioner(dynamicProvisioning ? new MockHostProvisioner(List.of()) : null)
                .build();

        Node node = Node.create("id", "node1.domain.tld", new Flavor(NodeResources.unspecified()), Node.State.dirty, NodeType.tenant)
                .allocation(new Allocation(ProvisioningTester.applicationId(),
                        ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3"), Optional.empty()),
                        NodeResources.unspecified(),
                        Generation.initial(),
                        false))
                .build();

        tester.nodeRepository().database().addNodesInState(List.of(node), node.state(), Agent.system);

        Duration expiryTimeout = Duration.ofMinutes(30);
        DirtyExpirer expirer = new DirtyExpirer(tester.nodeRepository(), expiryTimeout, new TestMetric());

        assertEquals(Node.State.dirty, tester.nodeRepository().nodes().list().first().get().state());
        expirer.run();
        assertEquals(Node.State.dirty, tester.nodeRepository().nodes().list().first().get().state());

        tester.clock().advance(expiryTimeout.plusSeconds(1));
        expirer.run();
        assertEquals(Node.State.failed, tester.nodeRepository().nodes().list().first().get().state());
        assertEquals(dynamicProvisioning, tester.nodeRepository().nodes().list().first().get().allocation().isEmpty());
    }

}