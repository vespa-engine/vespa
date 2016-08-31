// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class FailedExpirerTest {

    private Curator curator = new MockCurator();

    @Test
    public void ensure_failed_nodes_are_deallocated_in_prod() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(Environment.prod);

        assertEquals(2, nodeRepository.getNodes(Node.Type.tenant, Node.State.failed).size());
        assertEquals(1, nodeRepository.getNodes(Node.Type.tenant, Node.State.dirty).size());
        assertEquals("node3", nodeRepository.getNodes(Node.Type.tenant, Node.State.dirty).get(0).hostname());
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_dev() throws InterruptedException {
        NodeRepository nodeRepository = failureScenarioIn(Environment.dev);

        assertEquals(1, nodeRepository.getNodes(Node.Type.tenant, Node.State.failed).size());
        assertEquals(2, nodeRepository.getNodes(Node.Type.tenant, Node.State.dirty).size());
        assertEquals("node2", nodeRepository.getNodes(Node.Type.tenant, Node.State.failed).get(0).hostname());
    }

    private NodeRepository failureScenarioIn(Environment environment) {
        ManualClock clock = new ManualClock();
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock);
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone(), clock);

        List<Node> nodes = new ArrayList<>(3);
        nodes.add(nodeRepository.createNode("node1", "node1", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodes.add(nodeRepository.createNode("node2", "node2", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodes.add(nodeRepository.createNode("node3", "node3", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodeRepository.addNodes(nodes);

        List<Node> hostNodes = new ArrayList<>(1);
        hostNodes.add(nodeRepository.createNode("parent1", "parent1", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.host));
        hostNodes.add(nodeRepository.createNode("parent2", "parent2", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.host));
        nodeRepository.addNodes(hostNodes);


        // Set node1 to have failed 4 times before
        Node node1 = nodeRepository.getNode("node1").get();
        node1 = node1.setStatus(node1.status().increaseFailCount());
        node1 = node1.setStatus(node1.status().increaseFailCount());
        node1 = node1.setStatus(node1.status().increaseFailCount());
        node1 = node1.setStatus(node1.status().increaseFailCount());
        nodeRepository.write(node1);

        // Set node2 to have a detected hardware failure
        Node node2 = nodeRepository.getNode("node2").get();
        node2 = node2.setStatus(node2.status().setHardwareFailure(true));
        nodeRepository.write(node2);

        // Allocate the nodes
        nodeRepository.setReady(nodeRepository.getNodes(Node.Type.tenant, Node.State.provisioned));
        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(3), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, asHosts(nodes));
        transaction.commit();
        assertEquals(3, nodeRepository.getNodes(Node.Type.tenant, Node.State.active).size());

        // Fail the nodes
        nodeRepository.fail("node1");
        nodeRepository.fail("node2");
        nodeRepository.fail("node3");
        assertEquals(3, nodeRepository.getNodes(Node.Type.tenant, Node.State.failed).size());

        // Failure times out
        clock.advance(Duration.ofDays(5));
        new FailedExpirer(nodeRepository, new Zone(environment, RegionName.from("us-west-1")), clock, Duration.ofDays(4)).run();

        return nodeRepository;
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
