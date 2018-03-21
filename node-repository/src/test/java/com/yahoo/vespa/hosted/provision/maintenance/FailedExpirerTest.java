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
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 * @author mpolden
 */
public class FailedExpirerTest {

    @Test
    public void ensure_failed_nodes_are_deallocated_in_prod() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.prod)
                .withNode("node1")
                .withNode("node2")
                .withNode("node3")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, "node1", "node2", "node3")
                .failNode(4, "node1")
                .failWithHardwareFailure("node2", "node3");

        scenario.clock().advance(Duration.ofDays(3));
        scenario.expirer().run();
        scenario.assertNodesIn(Node.State.failed, "node1", "node2", "node3"); // None moved yet

        scenario.clock().advance(Duration.ofDays(2));
        scenario.expirer().run();
        scenario.assertNodesIn(Node.State.failed, "node1");
        scenario.assertNodesIn(Node.State.parked, "node2", "node3");
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_dev() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.dev)
                .withNode("node1")
                .withNode("node2")
                .withNode("node3")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, "node1", "node2", "node3")
                .failNode(4, "node1")
                .failWithHardwareFailure("node2", "node3");

        scenario.clock().advance(Duration.ofDays(5));
        scenario.expirer().run();

        scenario.assertNodesIn(Node.State.parked, "node2", "node3");
        scenario.assertNodesIn(Node.State.dirty, "node1");
    }

    @Test
    public void ensure_failed_nodes_are_deallocated_in_cd() {
        FailureScenario scenario = new FailureScenario(SystemName.cd, Environment.prod)
                .withNode("node1")
                .withNode("node2")
                .withNode("node3")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, "node1", "node2", "node3")
                .failNode(4, "node1")
                .failWithHardwareFailure("node2", "node3");

        scenario.clock().advance(Duration.ofHours(2));
        scenario.expirer().run();

        scenario.assertNodesIn(Node.State.failed, "node1");
        scenario.assertNodesIn(Node.State.parked, "node2", "node3");
    }

    @Test
    public void ensure_failed_docker_nodes_are_deallocated() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.prod)
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent1")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent2")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent3")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node1", "parent1")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node2", "parent2")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node3", "parent3")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, FailureScenario.dockerFlavor, "node1", "node2", "node3")
                .failNode(4, "node1")
                .failWithHardwareFailure("node2", "node3");

        scenario.clock().advance(Duration.ofDays(5));
        scenario.expirer().run();

        scenario.assertNodesIn(Node.State.parked, "node2", "node3");
        scenario.assertNodesIn(Node.State.dirty, "node1");
    }

    @Test
    public void ensure_parked_docker_host() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.prod)
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent1")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent2")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent3")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node1", "parent1")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node2", "parent2")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node3", "parent3")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, FailureScenario.dockerFlavor, "node1", "node2", "node3")
                .failNode(8, "node3")
                .failWithHardwareFailure("node2", "node3")
                .failWithHardwareFailure("parent2");

        scenario.clock.advance(Duration.ofDays(5));
        scenario.expirer().run(); // Run twice because parent can only be parked after the child
        scenario.expirer().run();
        scenario.assertNodesIn(Node.State.parked, "parent2", "node2", "node3");
    }

    @Test
    public void ensure_failed_docker_host_is_not_parked_unless_all_children_are() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.prod)
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent1")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent2")
                .withNode(NodeType.host, FailureScenario.defaultFlavor, "parent3")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node1", "parent1")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node2", "parent2")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node3", "parent3")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node4", "parent1")
                .withNode(NodeType.tenant, FailureScenario.dockerFlavor, "node5", "parent1")
                .setReady("node1", "node2", "node3")
                .allocate(ClusterSpec.Type.content, FailureScenario.dockerFlavor, "node1", "node2", "node3")
                .failWithHardwareFailure("parent1");

        scenario.clock().advance(Duration.ofDays(2));
        scenario.failNode(1, "node4", "node5");
        scenario.clock().advance(Duration.ofDays(3));

        scenario.expirer().run(); // Run twice because parent can only be parked after the child
        scenario.expirer().run();

        scenario.assertNodesIn(Node.State.failed, "parent1", "node4", "node5");
    }

    @Test
    public void ensure_container_nodes_are_recycled_early() {
        FailureScenario scenario = new FailureScenario(SystemName.main, Environment.prod)
                .withNode("node1")
                .withNode("node2")
                .withNode("node3")
                .withNode("node4")
                .withNode("node5")
                .withNode("node6")
                .setReady("node1", "node2", "node3", "node4", "node5", "node6")
                .allocate(ClusterSpec.Type.content, "node1", "node2", "node3")
                .allocate(ClusterSpec.Type.container, "node4", "node5", "node6");

        // Vespa container fails
        scenario.failNode(1, "node4");

        // 30 minutes pass, nothing happens
        scenario.clock().advance(Duration.ofMinutes(30));
        scenario.expirer().run();
        scenario.assertNodesIn(Node.State.dirty);

        // Recycles container when more than 1 hour passes
        scenario.clock().advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
        scenario.expirer().run();
        scenario.assertNodesIn(Node.State.dirty, "node4");
    }

    private static class FailureScenario {

        private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "docker");
        public static final Flavor defaultFlavor = nodeFlavors.getFlavorOrThrow("default");
        public static final Flavor dockerFlavor = nodeFlavors.getFlavorOrThrow("docker");
        
        private final MockCurator curator = new MockCurator();
        private final ManualClock clock = new ManualClock();
        private final ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"),
                                                                       ApplicationName.from("bar"),
                                                                       InstanceName.from("default"));
        
        private final NodeRepository nodeRepository;
        private final NodeRepositoryProvisioner provisioner;
        private final FailedExpirer expirer;

        public FailureScenario(SystemName system, Environment environment) {
            Zone zone = new Zone(system, environment, RegionName.defaultName());
            this.nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                                                     new MockNameResolver().mockAnyLookup(),
                                                     new DockerImage("docker-image"));
            this.provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, Zone.defaultZone());
            this.expirer = new FailedExpirer(nodeRepository, zone, clock, Duration.ofMinutes(30),
                                             new JobControl(nodeRepository.database()));
        }

        public ManualClock clock() {
            return clock;
        }

        public FailedExpirer expirer() {
            return expirer;
        }

        public Node get(String hostname) {
            return nodeRepository.getNode(hostname)
                                 .orElseThrow(() -> new IllegalArgumentException("No such node: " + hostname));
        }

        public FailureScenario withNode(NodeType type, Flavor flavor, String hostname, String parentHostname) {
            nodeRepository.addNodes(Collections.singletonList(
                    nodeRepository.createNode(UUID.randomUUID().toString(), hostname,
                                              Optional.ofNullable(parentHostname), flavor, type)
            ));
            return this;
        }

        public FailureScenario withNode(NodeType type, Flavor flavor, String hostname) {
            return withNode(type, flavor, hostname,null);
        }

        public FailureScenario withNode(String hostname) {
            return withNode(NodeType.tenant, defaultFlavor, hostname, null);
        }

        public FailureScenario failNode(int times, String... hostname) {
            Stream.of(hostname).forEach(h -> {
                Node node = get(h);
                nodeRepository.write(node.with(node.status().setFailCount(times)));
                nodeRepository.fail(h, Agent.system, "Failed by unit test");
            });
            return this;
        }

        public FailureScenario failWithHardwareFailure(String... hostname) {
            Stream.of(hostname).forEach(h -> {
                Node node = get(h);
                nodeRepository.write(node.with(node.status().withHardwareFailureDescription(
                        Optional.of("memory_mcelog"))));
                nodeRepository.fail(h, Agent.system, "Failed by unit test");
            });
            return this;
        }

        public FailureScenario setReady(String... hostname) {
            List<Node> nodes = Stream.of(hostname)
                                     .map(this::get)
                                     .collect(Collectors.toList());
            nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
            nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
            return this;
        }

        public FailureScenario allocate(ClusterSpec.Type clusterType, String... hostname) {
            return allocate(clusterType, defaultFlavor, hostname);
        }

        public FailureScenario allocate(ClusterSpec.Type clusterType, Flavor flavor, String... hostname) {
            ClusterSpec clusterSpec = ClusterSpec.request(clusterType,
                                                          ClusterSpec.Id.from("test"),
                                                          Version.fromString("6.42"),
                                                          false);
            List<HostSpec> preparedNodes = provisioner.prepare(applicationId,
                                                               clusterSpec,
                                                               Capacity.fromNodeCount(hostname.length, Optional.of(flavor.name()),
                                                                                      false),
                                1, null);
            NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
            provisioner.activate(transaction, applicationId, new HashSet<>(preparedNodes));
            transaction.commit();
            return this;
        }

        public void assertNodesIn(Node.State state, String... hostnames) {
            assertEquals(Stream.of(hostnames).collect(Collectors.toSet()),
                         nodeRepository.getNodes(state).stream()
                                       .map(Node::hostname)
                                       .collect(Collectors.toSet()));
        }
    }

}
