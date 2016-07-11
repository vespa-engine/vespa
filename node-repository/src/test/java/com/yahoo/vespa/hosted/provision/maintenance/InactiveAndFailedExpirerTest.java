// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class InactiveAndFailedExpirerTest {

    private Curator curator = new MockCurator();

    @Test
    public void ensure_inactive_and_failed_times_out() throws InterruptedException {
        ManualClock clock = new ManualClock();
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock);
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone(), clock);

        List<Node> nodes = new ArrayList<>(2);
        nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodeRepository.addNodes(nodes);

        List<Node> hostNodes = new ArrayList<>(2);
        hostNodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.host));
        hostNodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.host));
        nodeRepository.addNodes(hostNodes);

        // Allocate then deallocate 2 nodes
        nodeRepository.setReady(nodes);
        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, asHosts(nodes));
        transaction.commit();
        assertEquals(2, nodeRepository.getNodes(Node.Type.tenant, Node.State.active).size());
        NestedTransaction deactivateTransaction = new NestedTransaction();
        nodeRepository.deactivate(applicationId, deactivateTransaction);
        deactivateTransaction.commit();
        assertEquals(2, nodeRepository.getNodes(Node.Type.tenant, Node.State.inactive).size());

        // Inactive times out
        clock.advance(Duration.ofMinutes(14));
        new InactiveExpirer(nodeRepository, clock, Duration.ofMinutes(10)).run();

        assertEquals(0, nodeRepository.getNodes(Node.Type.tenant, Node.State.inactive).size());
        List<Node> dirty = nodeRepository.getNodes(Node.Type.tenant, Node.State.dirty);
        assertEquals(2, dirty.size());
        assertFalse(dirty.get(0).allocation().isPresent());
        assertFalse(dirty.get(1).allocation().isPresent());

        // One node is set back to ready
        Node ready = nodeRepository.setReady(Collections.singletonList(dirty.get(0))).get(0);
        assertEquals("Allocated history is removed on readying", 1, ready.history().events().size());
        assertEquals(History.Event.Type.readied, ready.history().events().iterator().next().type());

        // Dirty times out for the other one
        clock.advance(Duration.ofMinutes(14));
        new DirtyExpirer(nodeRepository, clock, Duration.ofMinutes(10)).run();
        assertEquals(0, nodeRepository.getNodes(Node.Type.tenant, Node.State.dirty).size());
        List<Node> failed = nodeRepository.getNodes(Node.Type.tenant, Node.State.failed);
        assertEquals(1, failed.size());
        assertEquals(1, failed.get(0).status().failCount());
    }

    private Set<HostSpec> asHosts(List<Node> nodes) {
        Set<HostSpec> hosts = new HashSet<>(nodes.size());
        for (Node node : nodes)
            hosts.add(new HostSpec(node.hostname(),
                                   node.allocation().isPresent() ? Optional.of(node.allocation().get().membership()) :
                                                                   Optional.empty()));
        return hosts;
    }

}
