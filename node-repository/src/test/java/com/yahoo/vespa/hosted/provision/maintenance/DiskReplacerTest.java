// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class DiskReplacerTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final MockHostProvisioner hostProvisioner = new MockHostProvisioner(List.of());
    private final DiskReplacer diskReplacer = new DiskReplacer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), hostProvisioner);

    @Test
    public void rebuild_host() {
        tester.makeReadyHosts(2, new NodeResources(2, 8, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote))
              .activateTenantHosts();
        List<Node> nodes = tester.deploy(ProvisioningTester.applicationId(), ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c")).vespaVersion("8").build(),
                                         Capacity.from(new ClusterResources(2, 1, new NodeResources(2, 8, 100, 1))));

        // No rebuilds in initial run
        diskReplacer.maintain();
        assertEquals(0, tester.nodeRepository().nodes().list().rebuilding(true).size());

        // Host starts rebuilding
        String host0 = "host-1.yahoo.com";
        tester.nodeRepository().nodes().rebuild(host0, true, Agent.RebuildingOsUpgrader,
                                                tester.nodeRepository().clock().instant());
        diskReplacer.maintain();
        assertEquals(1, tester.nodeRepository().nodes().list().rebuilding(true).size());

        // Host creates a snapshot of its children
        nodes.forEach(node -> {
            Snapshot snapshot = tester.nodeRepository().snapshots().create(node.hostname(), tester.clock().instant());
            tester.nodeRepository().snapshots().move(snapshot.id(), node.hostname(), Snapshot.State.created);
        });

        // Rebuild completes
        hostProvisioner.completeRebuildOf(host0);
        diskReplacer.maintain();
        nodes = tester.nodeRepository().nodes().list().childrenOf(host0).asList();
        assertFalse(nodes.isEmpty());
        assertTrue(nodes.stream().allMatch(node -> node.status().snapshot().isPresent() &&
                                                   node.status().snapshot().get().state() == Snapshot.State.restoring), "Snapshot restore is triggered for all children");
        assertEquals(0, tester.nodeRepository().nodes().list().rebuilding(true).size());
    }

}
