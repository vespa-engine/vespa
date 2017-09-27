// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
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
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class FailedExpirerTest {

    private final Curator curator = new MockCurator();
    private final ManualClock clock = new ManualClock();
    private FailedExpirer failedExpirer;

    @Test
    public void ensure_failed_nodes_are_deallocated_in_prod() throws InterruptedException {
        failureScenarioIn(SystemName.main, Environment.prod, "default");
        clock.advance(Duration.ofDays(5));
        failedExpirer.run();

        assertNodeHostnames(Node.State.failed, "node1");
        assertNodeHostnames(Node.State.parked, "node2", "node3");
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_dev() throws InterruptedException {
        failureScenarioIn(SystemName.main, Environment.dev, "default");
        clock.advance(Duration.ofDays(5));
        failedExpirer.run();

        assertNodeHostnames(Node.State.parked, "node2", "node3");
        assertNodeHostnames(Node.State.dirty, "node1");
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_cd() throws InterruptedException {
        failureScenarioIn(SystemName.cd, Environment.prod, "default");
        clock.advance(Duration.ofDays(5));
        failedExpirer.run();

        assertNodeHostnames(Node.State.failed, "node1");
        assertNodeHostnames(Node.State.parked, "node2", "node3");
    }

    @Test
    public void ensure_failed_docker_nodes_are_deallocated() throws InterruptedException {
        failureScenarioIn(SystemName.main, Environment.prod, "docker");
        clock.advance(Duration.ofDays(5));
        failedExpirer.run();

        assertNodeHostnames(Node.State.parked, "node2", "node3");
        assertNodeHostnames(Node.State.dirty, "node1");
    }

    @Test
    public void ensure_parked_docker_host() throws InterruptedException {
        failureScenarioIn(SystemName.main, Environment.prod, "docker");

        failNode("parent2");
        setHWFailureForNode("parent2");

        clock.advance(Duration.ofDays(5));
        failedExpirer.run(); // Run twice because parent can only be parked after the child
        failedExpirer.run();

        assertNodeHostnames(Node.State.parked, "parent2", "node2", "node3");
    }

    @Test
    public void ensure_failed_docker_host_is_not_parked_unless_all_children_are() throws InterruptedException {
        failureScenarioIn(SystemName.cd, Environment.prod, "docker");

        failNode("parent1");
        setHWFailureForNode("parent1");
        clock.advance(Duration.ofDays(2));
        failNode("node4");
        failNode("node5");
        clock.advance(Duration.ofDays(3));

        failedExpirer.run(); // Run twice because parent can only be parked after the child
        failedExpirer.run();

        assertNodeHostnames(Node.State.failed, "parent1", "node4", "node5");
    }

    private void assertNodeHostnames(Node.State state, String... hostnames) {
        assertEquals(Stream.of(hostnames).collect(Collectors.toSet()),
                failedExpirer.nodeRepository().getNodes(state).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    private void setHWFailureForNode(String hostname) {
        Node node2 = failedExpirer.nodeRepository().getNode(hostname).get();
        node2 = node2.with(node2.status().withHardwareFailureDescription(Optional.of("memory_mcelog")));
        failedExpirer.nodeRepository().write(node2);
    }

    private void failNode(String hostname) {
        failedExpirer.nodeRepository().fail(hostname, Agent.system, "Failing to unit test");
    }

    private void failureScenarioIn(SystemName system, Environment environment, String flavorName) {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", flavorName);
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, Zone.defaultZone(),
                                                           new MockNameResolver().mockAnyLookup(),
                                                           new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone(), clock, (x,y) -> {});
        failedExpirer = new FailedExpirer(nodeRepository, new Zone(system, environment, RegionName.from("us-west-1")), clock, Duration.ofDays(4), new JobControl(nodeRepository.database()));

        Flavor defaultFlavor = nodeFlavors.getFlavorOrThrow("default");
        List<Node> hostNodes = new ArrayList<>(3);
        hostNodes.add(nodeRepository.createNode("parent1", "parent1", Optional.empty(), defaultFlavor, NodeType.host));
        hostNodes.add(nodeRepository.createNode("parent2", "parent2", Optional.empty(), defaultFlavor, NodeType.host));
        hostNodes.add(nodeRepository.createNode("parent3", "parent3", Optional.empty(), defaultFlavor, NodeType.host));
        nodeRepository.addNodes(hostNodes);

        Flavor flavor = nodeFlavors.getFlavorOrThrow(flavorName);
        List<Node> nodes = new ArrayList<>(3);
        Optional<String> parentHost1 = flavorName.equals("docker") ? Optional.of("parent1") : Optional.empty();
        Optional<String> parentHost2 = flavorName.equals("docker") ? Optional.of("parent2") : Optional.empty();
        Optional<String> parentHost3 = flavorName.equals("docker") ? Optional.of("parent3") : Optional.empty();
        nodes.add(nodeRepository.createNode("node1", "node1", parentHost1, flavor, NodeType.tenant));
        nodes.add(nodeRepository.createNode("node2", "node2", parentHost2, flavor, NodeType.tenant));
        nodes.add(nodeRepository.createNode("node3", "node3", parentHost3, flavor, NodeType.tenant));
        nodeRepository.addNodes(nodes);

        // Set node1 to have failed 4 times before
        Node node1 = nodeRepository.getNode("node1").get();
        node1 = node1.with(node1.status().setFailCount(4));
        nodeRepository.write(node1);

        // Set node2 to have a detected hardware failure
        setHWFailureForNode("node2");

        // Set node3 to have failed 8 times before and have a HW failure
        Node node3 = nodeRepository.getNode("node3").get();
        node3 = node1.with(node3.status().setFailCount(8));
        nodeRepository.write(node3);
        setHWFailureForNode("node3");

        // Allocate the nodes
        List<Node> provisioned = nodeRepository.getNodes(NodeType.tenant, Node.State.provisioned);
        nodeRepository.setReady(nodeRepository.setDirty(provisioned));
        nodeRepository.addNodes(Arrays.asList(
                nodeRepository.createNode("node4", "node4", parentHost1, flavor, NodeType.tenant),
                nodeRepository.createNode("node5", "node5", parentHost1, flavor, NodeType.tenant)));

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(3, flavorName), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, ProvisioningTester.toHostSpecs(nodes));
        transaction.commit();
        assertEquals(3, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());

        // Fail the nodes
        nodes.forEach(node -> failNode(node.hostname()));
        assertEquals(3, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
    }
}
