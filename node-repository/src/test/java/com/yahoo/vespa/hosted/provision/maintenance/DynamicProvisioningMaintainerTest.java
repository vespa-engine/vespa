// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.custom.HostCapacity;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.MockHostProvisioner.Behaviour;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 * @author mpolden
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
                   Stream.of(host3, host4, host41).map(node -> node.ipConfig().primary()).allMatch(Set::isEmpty));

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
        assertTrue("No IP addresses assigned", Stream.of(host4, host41).map(node -> node.ipConfig().primary()).allMatch(Set::isEmpty));

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
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(), List.of(new HostCapacity(1, 3, 2, 1)), HostCapacity.class);
        Optional<Node> failedHost = tester.nodeRepository.getNode("host2");
        assertTrue(failedHost.isPresent());

        tester.maintainer.maintain();
        assertTrue("Failed host is deprovisioned", tester.nodeRepository.getNode(failedHost.get().hostname()).isEmpty());
        assertEquals(1, tester.hostProvisioner.deprovisionedHosts);
    }

    @Test
    public void provision_deficit_and_deprovision_excess() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(),
                                       List.of(new HostCapacity(24, 64, 100, 2),
                                               new HostCapacity(16, 24, 100, 1)),
                                       HostCapacity.class);
        assertTrue(tester.nodeRepository.getNode("host2").isPresent());
        assertEquals(0 ,tester.hostProvisioner.provisionedHosts.size());

        // failed host2 is removed
        Optional<Node> failedHost = tester.nodeRepository.getNode("host2");
        assertTrue(failedHost.isPresent());
        tester.maintainer.maintain();
        assertTrue("Failed host is deprovisioned", tester.nodeRepository.getNode(failedHost.get().hostname()).isEmpty());
        assertTrue("Host with matching resources is kept", tester.nodeRepository.getNode("host3").isPresent());

        // Two more hosts are provisioned with expected resources
        NodeResources resources = new NodeResources(24, 64, 100, 1);
        assertEquals(2, tester.provisionedHostsMatching(resources));
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        var tester = new DynamicProvisioningTester();
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, DynamicProvisioningTester.tenantApp);
        tester.hostProvisioner.with(Behaviour.failDeprovisioning);

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.getNode(host2.hostname()).isPresent());
    }

    @Ignore // TODO (hakon): Enable as test of min-capacity specified in flag
    @Test
    public void provision_exact_capacity() {
        var tester = new DynamicProvisioningTester(Cloud.builder().dynamicProvisioning(true).build());
        NodeResources resources1 = new NodeResources(24, 64, 100, 1);
        NodeResources resources2 = new NodeResources(16, 24, 100, 1);
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(), List.of(new HostCapacity(resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(), 1),
                                                                           new HostCapacity(resources2.vcpu(), resources2.memoryGb(), resources2.diskGb(), 2)),
                                       HostCapacity.class);
        tester.maintainer.maintain();

        // Hosts are provisioned
        assertEquals(1, tester.provisionedHostsMatching(resources1));
        assertEquals(2, tester.provisionedHostsMatching(resources2));

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Target capacity is changed
        NodeResources resources3 = new NodeResources(48, 128, 1000, 1);
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(), List.of(new HostCapacity(resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(), 1),
                                                                           new HostCapacity(resources3.vcpu(), resources3.memoryGb(), resources3.diskGb(), 1)),
                                       HostCapacity.class);

        // Excess hosts are deprovisioned
        tester.maintainer.maintain();
        assertEquals(1, tester.provisionedHostsMatching(resources1));
        assertEquals(0, tester.provisionedHostsMatching(resources2));
        assertEquals(1, tester.provisionedHostsMatching(resources3));
        assertEquals(2, tester.nodeRepository.getNodes(Node.State.deprovisioned).size());

        // Activate hosts
        tester.maintainer.maintain(); // Resume provisioning of new hosts
        List<Node> provisioned = tester.nodeRepository.list().state(Node.State.provisioned).asList();
        tester.nodeRepository.setReady(provisioned, Agent.system, this.getClass().getSimpleName());
        tester.provisioningTester.activateTenantHosts();

        // Allocating nodes to a host does not result in provisioning of additional capacity
        ApplicationId application = ProvisioningTester.makeApplicationId();
        tester.provisioningTester.deploy(application,
                                         Capacity.from(new ClusterResources(2, 1, new NodeResources(4, 8, 50, 0.1))));
        assertEquals(2, tester.nodeRepository.list().owner(application).size());
        tester.assertNodesUnchanged();

        // Clearing flag does nothing
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(), List.of(), HostCapacity.class);
        tester.assertNodesUnchanged();

        // Capacity reduction does not remove host with children
        tester.flagSource.withListFlag(Flags.TARGET_CAPACITY.id(), List.of(new HostCapacity(resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(), 1)),
                                       HostCapacity.class);
        tester.assertNodesUnchanged();
    }

    @Test
    public void defer_writing_ip_addresses_until_dns_resolves() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.hostProvisioner.with(Behaviour.failDnsUpdate);

        Supplier<List<Node>> provisioning = () -> tester.nodeRepository.getNodes(NodeType.host, Node.State.provisioned);
        assertEquals(2, provisioning.get().size());
        tester.maintainer.maintain();

        assertTrue("No IP addresses written as DNS updates are failing",
                   provisioning.get().stream().allMatch(host -> host.ipConfig().pool().isEmpty()));

        tester.hostProvisioner.without(Behaviour.failDnsUpdate);
        tester.maintainer.maintain();
        assertTrue("IP addresses written as DNS updates are succeeding",
                   provisioning.get().stream().noneMatch(host -> host.ipConfig().pool().isEmpty()));
    }

    private static class DynamicProvisioningTester {

        private static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        private static final ApplicationId tenantHostApp = ApplicationId.from("vespa", "tenant-host", "default");
        private static final ApplicationId proxyHostApp = ApplicationId.from("vespa", "proxy-host", "default");
        private static final ApplicationId proxyApp = ApplicationId.from("vespa", "proxy", "default");
        private static final NodeFlavors flavors = FlavorConfigBuilder.createDummies("default", "docker", "host2", "host3", "host4");

        private final InMemoryFlagSource flagSource = new InMemoryFlagSource().withListFlag(Flags.TARGET_CAPACITY.id(),
                                                                                            List.of(),
                                                                                            HostCapacity.class);

        private final NodeRepository nodeRepository;
        private final MockHostProvisioner hostProvisioner;
        private final DynamicProvisioningMaintainer maintainer;
        private final ProvisioningTester provisioningTester;

        public DynamicProvisioningTester() {
            this(Cloud.builder().dynamicProvisioning(true).build());
        }

        public DynamicProvisioningTester(Cloud cloud) {
            MockNameResolver nameResolver = new MockNameResolver();
            this.hostProvisioner = new MockHostProvisioner(flavors, nameResolver);
            this.provisioningTester = new ProvisioningTester.Builder().zone(new Zone(cloud, SystemName.defaultSystem(),
                                                                                     Environment.defaultEnvironment(),
                                                                                     RegionName.defaultName()))
                                                                      .flavors(flavors.getFlavors())
                                                                      .nameResolver(nameResolver)
                                                                      .hostProvisioner(hostProvisioner)
                                                                      .build();
            this.nodeRepository = provisioningTester.nodeRepository();
            this.maintainer = new DynamicProvisioningMaintainer(nodeRepository,
                                                                Duration.ofDays(1),
                                                                hostProvisioner,
                                                                flagSource,
                                                                new TestMetric());
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
                .forEach(node -> nodeRepository.database().addNodesInState(List.of(node), node.state(), Agent.system));
            return this;
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state) {
            return addNode(hostname, parentHostname, nodeType, state, null);
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, ApplicationId application) {
            Node node = createNode(hostname, parentHostname, nodeType, state, Optional.ofNullable(application));
            return nodeRepository.database().addNodesInState(List.of(node), node.state(), Agent.system).get(0);
        }

        private Node createNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, Optional<ApplicationId> application) {
            Flavor flavor = nodeRepository.flavors().getFlavor(parentHostname.isPresent() ? "docker" : "host2").orElseThrow();
            Optional<Allocation> allocation = application
                    .map(app -> new Allocation(
                            app,
                            ClusterMembership.from("container/default/0/0", Version.fromString("7.3"), Optional.empty()),
                            flavor.resources(),
                            Generation.initial(),
                            false));
            Node.Builder builder = Node.create("fake-id-" + hostname, hostname, flavor, state, nodeType)
                    .ipConfig(state == Node.State.active ? Set.of("::1") : Set.of(), Set.of());
            parentHostname.ifPresent(builder::parentHostname);
            allocation.ifPresent(builder::allocation);
            return builder.build();
        }

        private long provisionedHostsMatching(NodeResources resources) {
            return hostProvisioner.provisionedHosts.stream()
                                                   .filter(host -> host.nodeResources().equals(resources))
                                                   .count();
        }

        private void assertNodesUnchanged() {
            List<Node> nodes = nodeRepository.getNodes();
            maintainer.maintain();
            assertEquals("Nodes are unchanged after maintenance run", nodes, nodeRepository.getNodes());
        }

    }

    static class MockHostProvisioner implements HostProvisioner {

        private final List<ProvisionedHost> provisionedHosts = new ArrayList<>();
        private final NodeFlavors flavors;
        private final MockNameResolver nameResolver;

        private int deprovisionedHosts = 0;
        private EnumSet<Behaviour> behaviours = EnumSet.noneOf(Behaviour.class);

        public MockHostProvisioner(NodeFlavors flavors, MockNameResolver nameResolver) {
            this.flavors = flavors;
            this.nameResolver = nameResolver;
        }

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources,
                                                    ApplicationId applicationId, Version osVersion, HostSharing sharing) {
            Flavor hostFlavor = flavors.getFlavors().stream()
                                       .filter(f -> !f.isDocker())
                                       .filter(f -> f.resources().compatibleWith(resources))
                                       .findFirst()
                                       .orElseThrow(() -> new IllegalArgumentException("No host flavor found satisfying " + resources));
            List<ProvisionedHost> hosts = new ArrayList<>();
            for (int index : provisionIndexes) {
                hosts.add(new ProvisionedHost("host" + index,
                                              "hostname" + index,
                                              hostFlavor,
                                              Optional.empty(),
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
            assertSame(Node.State.provisioned, host.state());
            List<Node> result = new ArrayList<>();
            result.add(withIpAssigned(host));
            for (var child : children) {
                assertSame(Node.State.reserved, child.state());
                result.add(withIpAssigned(child));
            }
            return result;
        }

        @Override
        public void deprovision(Node host) {
            if (behaviours.contains(Behaviour.failDeprovisioning)) throw new FatalProvisioningException("Failed to deprovision node");
            provisionedHosts.removeIf(provisionedHost -> provisionedHost.hostHostname().equals(host.hostname()));
            deprovisionedHosts++;
        }

        private MockHostProvisioner with(Behaviour first, Behaviour... rest) {
            this.behaviours = EnumSet.of(first, rest);
            return this;
        }

        private MockHostProvisioner without(Behaviour first, Behaviour... rest) {
            Set<Behaviour> behaviours = new HashSet<>(this.behaviours);
            behaviours.removeAll(EnumSet.of(first, rest));
            this.behaviours = behaviours.isEmpty() ? EnumSet.noneOf(Behaviour.class) : EnumSet.copyOf(behaviours);
            return this;
        }

        private Node withIpAssigned(Node node) {
            if (node.parentHostname().isPresent()) return node;
            int hostIndex = Integer.parseInt(node.hostname().replaceAll("^[a-z]+|-\\d+$", ""));
            Set<String> addresses = Set.of("::" + hostIndex + ":0");
            Set<String> pool = new HashSet<>();
            if (!behaviours.contains(Behaviour.failDnsUpdate)) {
                nameResolver.addRecord(node.hostname(), addresses.iterator().next());
                for (int i = 1; i <= 2; i++) {
                    String ip = "::" + hostIndex + ":" + i;
                    pool.add(ip);
                    nameResolver.addRecord(node.hostname() + "-" + i, ip);
                }
            }
            return node.with(node.ipConfig().with(addresses).with(IP.Pool.of(pool)));
        }

        enum Behaviour {
            failProvisioning,
            failDeprovisioning,
            failDnsUpdate,
        }

    }

}
