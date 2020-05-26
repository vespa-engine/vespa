// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.custom.HostCapacity;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerMock.Behaviour;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class DynamicProvisioningMaintainerTest {

    @Test
    public void delegates_to_host_provisioner_and_writes_back_result() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.hostProvisioner.with(Behaviour.failDeprovisioning); // To avoid deleting excess nodes

        Node host3 = tester.nodeRepository.getNode("host3").orElseThrow();
        Node host4 = tester.nodeRepository.getNode("host4").orElseThrow();
        Node host41 = tester.nodeRepository.getNode("host4-1").orElseThrow();
        assertTrue("No IP addresses assigned",
                   Stream.of(host3, host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));

        Node host3new = host3.with(host3.ipConfig().with(Set.of("::3:0")));
        Node host4new = host4.with(host4.ipConfig().with(Set.of("::4:0")));
        Node host41new = host41.with(host41.ipConfig().with(Set.of("::4:1", "::4:2")));

        tester.maintainer.maintain();
        assertEquals(host3new, tester.nodeRepository.getNode("host3").get());
        assertEquals(host4new, tester.nodeRepository.getNode("host4").get());
        assertEquals(host41new, tester.nodeRepository.getNode("host4-1").get());
    }

    @Test
    public void correctly_fails_if_irrecoverable_failure() {
        var tester = new DynamicProvisioningTester();
        tester.hostProvisioner.with(Behaviour.failProvisioning);
        Node host4 = tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned);
        Node host41 = tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, DynamicProvisioningTester.tenantApp);
        assertTrue("No IP addresses assigned", Stream.of(host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));

        tester.maintainer.maintain();
        assertEquals(Set.of("host4", "host4-1"),
                     tester.nodeRepository.getNodes(Node.State.failed).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    @Test
    public void finds_nodes_that_need_deprovisioning_without_pre_provisioning() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        assertTrue(tester.nodeRepository.getNode("host2").isPresent());
        assertTrue(tester.nodeRepository.getNode("host3").isPresent());

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.getNode("host2").isEmpty());
        assertTrue(tester.nodeRepository.getNode("host3").isEmpty());
    }

    @Test
    public void does_not_deprovision_when_preprovisioning_enabled() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(new HostCapacity(1, 3, 2, 1)), HostCapacity.class);
        Optional<Node> failedHost = tester.nodeRepository.getNode("host2");
        assertTrue(failedHost.isPresent());

        tester.maintainer.maintain();
        assertTrue("Failed host is deprovisioned", tester.nodeRepository.getNode(failedHost.get().hostname()).isEmpty());
        assertEquals(1, tester.hostProvisioner.deprovisionedHosts);
    }

    @Test
    public void provision_deficit_and_deprovision_excess() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                                       List.of(new HostCapacity(2, 4, 8, 1),
                                               new HostCapacity(2, 3, 2, 2)),
                                       HostCapacity.class);
        assertTrue(tester.nodeRepository.getNode("host2").isPresent());
        assertEquals(0 ,tester.hostProvisioner.provisionedHosts.size());

        // excessive host2 is removed
        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.getNode("host2").isEmpty());
        assertTrue(tester.nodeRepository.getNode("host3").isPresent());

        // Two more hosts are provisioned with expected resources
        NodeResources resources = new NodeResources(2, 3, 2, 1);
        assertEquals(2, tester.hostProvisioner.provisionedHosts.stream()
                                                               .filter(h -> h.nodeResources().equals(resources)).count());
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        var tester = new DynamicProvisioningTester();
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, DynamicProvisioningTester.tenantApp);
        tester.hostProvisioner.with(Behaviour.failDeprovisioning);

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.getNode(host2.hostname()).isPresent());
    }

    private static class DynamicProvisioningTester {

        private static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        private static final ApplicationId tenantHostApp = ApplicationId.from("vespa", "tenant-host", "default");
        private static final ApplicationId proxyHostApp = ApplicationId.from("vespa", "proxy-host", "default");
        private static final ApplicationId proxyApp = ApplicationId.from("vespa", "proxy", "default");
        private static final NodeFlavors flavors = FlavorConfigBuilder.createDummies("default", "docker");

        private final ManualClock clock = new ManualClock();
        private final InMemoryFlagSource flagSource = new InMemoryFlagSource()
                .withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(), HostCapacity.class);

        private final Zone zone;
        private final NodeRepository nodeRepository;
        private final HostProvisionerMock hostProvisioner;
        private final DynamicProvisioningMaintainer maintainer;

        public DynamicProvisioningTester() {
            this(Cloud.builder().dynamicProvisioning(true).build());
        }

        public DynamicProvisioningTester(Cloud cloud) {
            this.zone = new Zone(cloud, SystemName.defaultSystem(), Environment.defaultEnvironment(),
                                 RegionName.defaultName());
            this.nodeRepository = new NodeRepository(flavors,
                                                     new HostResourcesCalculatorMock(),
                                                     new MockCurator(),
                                                     clock,
                                                     zone,
                                                     new MockNameResolver().mockAnyLookup(),
                                                     DockerImage.fromString("docker-image"), true);
            this.hostProvisioner = new HostProvisionerMock(nodeRepository);
            this.maintainer = new DynamicProvisioningMaintainer(nodeRepository,
                                                                Duration.ofDays(1),
                                                                hostProvisioner,
                                                                flagSource);
        }

        private DynamicProvisioningTester addInitialNodes() {
            List.of(createNode("host1", Optional.empty(), NodeType.host, Node.State.active, Optional.of(tenantHostApp)),
                    createNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp)),
                    createNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, Optional.empty()),
                    createNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp)),
                    createNode("host2-1", Optional.of("host2"), NodeType.tenant, Node.State.failed, Optional.empty()),
                    createNode("host3", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty()),
                    createNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty()),
                    createNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp)),
                    createNode("proxyhost1", Optional.empty(), NodeType.proxyhost, Node.State.provisioned, Optional.empty()),
                    createNode("proxyhost2", Optional.empty(), NodeType.proxyhost, Node.State.active, Optional.of(proxyHostApp)),
                    createNode("proxy2", Optional.of("proxyhost2"), NodeType.proxy, Node.State.active, Optional.of(proxyApp)))
                .forEach(node -> nodeRepository.database().addNodesInState(List.of(node), node.state()));
            return this;
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state) {
            return addNode(hostname, parentHostname, nodeType, state, null);
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, ApplicationId application) {
            Node node = createNode(hostname, parentHostname, nodeType, state, Optional.ofNullable(application));
            return nodeRepository.database().addNodesInState(List.of(node), node.state()).get(0);
        }

        private Node createNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, Optional<ApplicationId> application) {
            Flavor flavor = nodeRepository.flavors().getFlavor(parentHostname.isPresent() ? "docker" : "default").orElseThrow();
            Optional<Allocation> allocation = application
                    .map(app -> new Allocation(
                            app,
                            ClusterMembership.from("container/default/0/0", Version.fromString("7.3"), Optional.empty()),
                            flavor.resources(),
                            Generation.initial(),
                            false));
            var ipConfig = new IP.Config(state == Node.State.active ? Set.of("::1") : Set.of(), Set.of());
            return new Node("fake-id-" + hostname, ipConfig, hostname, parentHostname, flavor, Status.initial(),
                            state, allocation, History.empty(), nodeType, new Reports(), Optional.empty(), Optional.empty());
        }

    }

    static class HostProvisionerMock implements HostProvisioner {

        private final NodeRepository nodeRepository;
        private final List<ProvisionedHost> provisionedHosts = new ArrayList<>();
        private int deprovisionedHosts = 0;
        private EnumSet<Behaviour> behaviours = EnumSet.noneOf(Behaviour.class);

        public HostProvisionerMock(NodeRepository nodeRepository) {
            this.nodeRepository = nodeRepository;
        }

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources, ApplicationId applicationId, Version osVersion) {
            Flavor hostFlavor = nodeRepository.flavors().getFlavorOrThrow("docker");
            List<ProvisionedHost> hosts = new ArrayList<>();
            for (int index : provisionIndexes) {
                hosts.add(new ProvisionedHost("host" + index,
                                              "hostname" + index,
                                              hostFlavor,
                                              "nodename" + index,
                                              resources,
                                              osVersion));
            }
            provisionedHosts.addAll(hosts);
            return hosts;
        }

        @Override
        public List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException {
            if (behaviours.contains(Behaviour.failProvisioning)) throw new FatalProvisioningException("Failed to provision node(s)");

            Optional<Node> existingHost = nodeRepository.getNode(host.hostname(), Node.State.provisioned);
            assertTrue(host + " is in " + Node.State.provisioned, existingHost.isPresent());
            List<Node> result = new ArrayList<>();
            result.add(assignIp(existingHost.get()));
            for (var child : children) {
                Optional<Node> existingChild = nodeRepository.getNode(child.hostname(), Node.State.reserved);
                assertTrue(child + " is in " + Node.State.reserved, existingChild.isPresent());
                result.add(assignIp(existingChild.get()));
            }
            return result;
        }

        @Override
        public void deprovision(Node host) {
            if (behaviours.contains(Behaviour.failDeprovisioning)) throw new FatalProvisioningException("Failed to deprovision node");
            deprovisionedHosts++;
        }

        private HostProvisionerMock with(Behaviour first, Behaviour... rest) {
            this.behaviours = EnumSet.of(first, rest);
            return this;
        }

        private Node assignIp(Node node) {
            int hostIndex = Integer.parseInt(node.hostname().replaceAll("^[a-z]+|-\\d+$", ""));
            Set<String> addresses;
            if (node.parentHostname().isEmpty()) {
                addresses = Set.of("::" + hostIndex + ":0");
            } else {
                addresses = Set.of("::" + hostIndex + ":1", "::" + hostIndex + ":2");
            }
            return node.with(node.ipConfig().with(addresses));
        }

        enum Behaviour {
            failProvisioning,
            failDeprovisioning,
        }

    }

    private static class HostResourcesCalculatorMock implements HostResourcesCalculator {

        @Override
        public NodeResources realResourcesOf(Node node, NodeRepository nodeRepository) {
            return node.flavor().resources();
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            if ("default".equals(flavor.name())) {
                return new NodeResources(2, 4, 8, 1);
            }
            return flavor.resources();
        }

    }

}
