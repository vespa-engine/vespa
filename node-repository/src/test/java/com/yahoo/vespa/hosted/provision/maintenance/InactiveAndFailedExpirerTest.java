// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class InactiveAndFailedExpirerTest {

    private final ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"),
            InstanceName.from("fuz"));

    @Test
    public void ensure_inactive_and_failed_times_out() throws InterruptedException {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        List<Node> nodes = tester.makeReadyNodes(2, "default");

        // Allocate then deallocate 2 nodes
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
        tester.activate(applicationId, ProvisioningTester.toHostSpecs(nodes));
        assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        tester.deactivate(applicationId);
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(2, inactiveNodes.size());

        // Inactive times out
        tester.advanceTime(Duration.ofMinutes(14));
        new InactiveExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10)).run();
        assertEquals(0, tester.nodeRepository().getNodes(Node.State.inactive).size());
        List<Node> dirty = tester.nodeRepository().getNodes(Node.State.dirty);
        assertEquals(2, dirty.size());
        assertFalse(dirty.get(0).allocation().isPresent());
        assertFalse(dirty.get(1).allocation().isPresent());

        // One node is set back to ready
        Node ready = tester.nodeRepository().setReady(Collections.singletonList(dirty.get(0))).get(0);
        assertEquals("Allocated history is removed on readying", 1, ready.history().events().size());
        assertEquals(History.Event.Type.readied, ready.history().events().iterator().next().type());

        // Dirty times out for the other one
        tester.advanceTime(Duration.ofMinutes(14));
        new DirtyExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10)).run();
        assertEquals(0, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.dirty).size());
        List<Node> failed = tester.nodeRepository().getNodes(NodeType.tenant, Node.State.failed);
        assertEquals(1, failed.size());
        assertEquals(1, failed.get(0).status().failCount());
    }

    @Test
    public void ensure_reboot_generation_is_increased_when_node_moves_to_dirty() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        List<Node> nodes = tester.makeReadyNodes(1, "default");

        // Allocate and deallocate a single node
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        tester.prepare(applicationId, cluster, Capacity.fromNodeCount(1), 1);
        tester.activate(applicationId, ProvisioningTester.toHostSpecs(nodes));
        assertEquals(1, tester.getNodes(applicationId, Node.State.active).size());
        tester.deactivate(applicationId);
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(1, inactiveNodes.size());

        // Check reboot generation before node is moved. New nodes transistion from provisioned to dirty, so their
        // wanted reboot generation will always be 1.
        long wantedRebootGeneration = inactiveNodes.get(0).status().reboot().wanted();
        assertEquals(1, wantedRebootGeneration);

        // Inactive times out and node is moved to dirty
        tester.advanceTime(Duration.ofMinutes(14));
        new InactiveExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10)).run();
        List<Node> dirty = tester.nodeRepository().getNodes(Node.State.dirty);
        assertEquals(1, dirty.size());

        // Reboot generation is increased
        assertEquals(wantedRebootGeneration + 1, dirty.get(0).status().reboot().wanted());
    }
}
