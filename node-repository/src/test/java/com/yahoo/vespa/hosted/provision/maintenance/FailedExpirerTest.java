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
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class FailedExpirerTest {

    private final Curator curator = new MockCurator();

    @Test
    public void ensure_failed_nodes_are_deallocated_in_prod() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(SystemName.main, Environment.prod);

        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(1, nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).size());
        assertEquals("node3", nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).get(0).hostname());
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_dev() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(SystemName.main, Environment.dev);

        assertEquals(1, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).size());
        assertEquals("node2", nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname());
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_ci() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(SystemName.ci, Environment.prod);

        assertEquals(1, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).size());
        assertEquals("node2", nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname());
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_cd() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(SystemName.cd, Environment.prod);

        assertEquals(1, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).size());
        assertEquals("node2", nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname());
    }

    private NodeRepository failureScenarioIn(SystemName system, Environment environment) {
        ManualClock clock = new ManualClock();
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, Zone.defaultZone(),
                new MockNameResolver().mockAnyLookup());
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone(), clock);

        List<Node> nodes = new ArrayList<>(3);
        nodes.add(nodeRepository.createNode("node1", "node1", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes.add(nodeRepository.createNode("node2", "node2", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes.add(nodeRepository.createNode("node3", "node3", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodeRepository.addNodes(nodes);

        List<Node> hostNodes = new ArrayList<>(1);
        hostNodes.add(nodeRepository.createNode("parent1", "parent1", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        hostNodes.add(nodeRepository.createNode("parent2", "parent2", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodeRepository.addNodes(hostNodes);


        // Set node1 to have failed 4 times before
        Node node1 = nodeRepository.getNode("node1").get();
        node1 = node1.with(node1.status().withIncreasedFailCount());
        node1 = node1.with(node1.status().withIncreasedFailCount());
        node1 = node1.with(node1.status().withIncreasedFailCount());
        node1 = node1.with(node1.status().withIncreasedFailCount());
        nodeRepository.write(node1);

        // Set node2 to have a detected hardware failure
        Node node2 = nodeRepository.getNode("node2").get();
        node2 = node2.with(node2.status().withHardwareFailure(Optional.of(Status.HardwareFailureType.memory_mcelog)));
        nodeRepository.write(node2);

        // Allocate the nodes
        List<Node> provisioned = nodeRepository.getNodes(NodeType.tenant, Node.State.provisioned);
        nodeRepository.setReady(nodeRepository.setDirty(provisioned));
        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(3), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, ProvisioningTester.toHostSpecs(nodes));
        transaction.commit();
        assertEquals(3, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());

        // Fail the nodes
        nodeRepository.fail("node1");
        nodeRepository.fail("node2");
        nodeRepository.fail("node3");
        assertEquals(3, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());

        // Failure times out
        clock.advance(Duration.ofDays(5));
        new FailedExpirer(nodeRepository, new Zone(system, environment, RegionName.from("us-west-1")), clock, Duration.ofDays(4)).run();

        return nodeRepository;
    }
}
