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
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
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
import java.util.stream.IntStream;
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

        Node host3new = host3.with(host3.ipConfig().withPrimary(Set.of("::3:0")));
        Node host4new = host4.with(host4.ipConfig().withPrimary(Set.of("::4:0")));
        Node host41new = host41.with(host41.ipConfig().withPrimary(Set.of("::4:1", "::4:2")));

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
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(new ClusterCapacity(1, 1, 3, 2, 1.0)), ClusterCapacity.class);
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
                                       List.of(new ClusterCapacity(2, 48, 128, 1000, 10.0),
                                               new ClusterCapacity(1, 16, 24, 100, 1.0)),
                                       ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(11, tester.nodeRepository.getNodes().size());
        assertTrue(tester.nodeRepository.getNode("host2").isPresent());
        assertTrue(tester.nodeRepository.getNode("host2-1").isPresent());
        assertTrue(tester.nodeRepository.getNode("host3").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname100").isEmpty());
        assertTrue(tester.nodeRepository.getNode("hostname101").isEmpty());

        tester.maintainer.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        List<Node> nodesAfter = tester.nodeRepository.getNodes();
        assertEquals(11, nodesAfter.size());  // 2 removed, 2 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.getNode("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.getNode("host2-1").isEmpty());
        assertTrue("Host satisfying 16-24-100-1 is kept", tester.nodeRepository.getNode("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.getNode("hostname100").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.getNode("hostname101").isPresent());
    }

    @Test
    public void preprovision_with_shared_host() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        // Makes provisioned hosts 48-128-1000-10
        tester.hostProvisioner.provisionSharedHost("host4");

        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 1, 30, 20, 3.0)),
                ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(11, tester.nodeRepository.getNodes().size());
        assertTrue(tester.nodeRepository.getNode("host2").isPresent());
        assertTrue(tester.nodeRepository.getNode("host2-1").isPresent());
        assertTrue(tester.nodeRepository.getNode("host3").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname100").isEmpty());

        // The first cluster will be allocated to host3 and a new host hostname100.
        // hostname100 will be a large shared host specified above.
        tester.maintainer.maintain();
        verifyFirstMaintain(tester);

        // Second maintain should be a no-op, otherwise we did wrong in the first maintain.
        tester.maintainer.maintain();
        verifyFirstMaintain(tester);

        // Add a second cluster equal to the first.  It should fit on existing host3 and hostname100.

        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 1, 30, 20, 3.0),
                        new ClusterCapacity(2, 1, 30, 20, 3.0)),
                ClusterCapacity.class);

        tester.maintainer.maintain();
        verifyFirstMaintain(tester);

        // Change second cluster such that it doesn't fit on host3, but does on hostname100,
        // and with a size of 2 it should allocate a new shared host.
        // The node allocation code prefers to allocate to the shared hosts instead of host3 (at least
        // in this test, due to skew), so host3 will be deprovisioned when hostname101 is provisioned.
        // host3 is a 24-64-100-10 while hostname100 is 48-128-1000-10.

        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 1, 30, 20, 3.0),
                        new ClusterCapacity(2, 24, 64, 100, 1.0)),
                ClusterCapacity.class);

        tester.maintainer.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(10, tester.nodeRepository.getNodes().size());  // 3 removed, 2 added
        assertTrue("preprovision capacity is prefered on shared hosts", tester.nodeRepository.getNode("host3").isEmpty());
        assertTrue(tester.nodeRepository.getNode("hostname100").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname101").isPresent());

        // If the preprovision capacity is reduced, we should see shared hosts deprovisioned.

        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(1, 1, 30, 20, 3.0)),
                ClusterCapacity.class);

        tester.maintainer.maintain();

        assertEquals("one provisioned host has been deprovisioned, so there are 2 -> 1 provisioned hosts",
                1, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(9, tester.nodeRepository.getNodes().size());  // 4 removed, 2 added
        if (tester.nodeRepository.getNode("hostname100").isPresent()) {
            assertTrue("hostname101 is superfluous and should have been deprovisioned",
                    tester.nodeRepository.getNode("hostname101").isEmpty());
        } else {
            assertTrue("hostname101 is required for preprovision capacity",
                    tester.nodeRepository.getNode("hostname101").isPresent());
        }

    }

    private void verifyFirstMaintain(DynamicProvisioningTester tester) {
        assertEquals(1, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(10, tester.nodeRepository.getNodes().size());  // 2 removed, 1 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.getNode("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.getNode("host2-1").isEmpty());
        assertTrue("One 1-30-20-3 node fits on host3", tester.nodeRepository.getNode("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.getNode("hostname100").isPresent());
    }

    @Test
    public void verify_min_count_of_shared_hosts() {
        // What's going on here?  We are trying to verify the impact of varying the minimum number of
        // shared hosts (SharedHost.minCount()).
        //
        // addInitialNodes() adds 4 tenant hosts:
        //   host1    shared   !removable   # not removable because it got child nodes w/allocation
        //   host2   !shared    removable   # not counted as a shared host because it is failed
        //   host3    shared    removable
        //   host4    shared   !removable   # not removable because it got child nodes w/allocation
        //
        // Hosts 1, 3, and 4 count as "shared hosts" with respect to the minCount lower boundary.
        // Hosts 3 and 4 are removable, that is they will be deprovisioned as excess hosts unless
        // prevented by minCount.

        // minCount=0: All (2) removable hosts are deprovisioned
        assertWithMinCount(0, 0, 2);
        // minCount=1: The same thing happens, because there are 2 shared hosts left
        assertWithMinCount(1, 0, 2);
        assertWithMinCount(2, 0, 2);
        // minCount=3: since we require 3 shared hosts, host3 is not deprovisioned.
        assertWithMinCount(3, 0, 1);
        // 4 shared hosts require we provision 1 shared host
        assertWithMinCount(4, 1, 1);
        // 5 shared hosts require we provision 2 shared hosts
        assertWithMinCount(5, 2, 1);
        assertWithMinCount(6, 3, 1);
    }

    private void assertWithMinCount(int minCount, int provisionCount, int deprovisionCount) {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.hostProvisioner.provisionSharedHost("host4");

        tester.flagSource.withJacksonFlag(Flags.SHARED_HOST.id(), new SharedHost(null, minCount), SharedHost.class);
        tester.maintainer.maintain();
        assertEquals(provisionCount, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(deprovisionCount, tester.hostProvisioner.deprovisionedHosts);

        // Verify next maintain is a no-op
        tester.maintainer.maintain();
        assertEquals(provisionCount, tester.hostProvisioner.provisionedHosts.size());
        assertEquals(deprovisionCount, tester.hostProvisioner.deprovisionedHosts);
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        var tester = new DynamicProvisioningTester();
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, DynamicProvisioningTester.tenantApp);
        tester.hostProvisioner.with(Behaviour.failDeprovisioning);

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.getNode(host2.hostname()).isPresent());
    }

    @Test
    public void test_minimum_capacity() {
        var tester = new DynamicProvisioningTester();
        NodeResources resources1 = new NodeResources(24, 64, 100, 10);
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(), resources1.bandwidthGbps())),
                ClusterCapacity.class);
        tester.maintainer.maintain();

        // Hosts are provisioned
        assertEquals(2, tester.provisionedHostsMatching(resources1));
        assertEquals(0, tester.hostProvisioner.deprovisionedHosts);

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Pretend shared-host flag has been set to host4's flavor
        var sharedHostNodeResources = new NodeResources(48, 128, 1000, 10, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote);
        tester.hostProvisioner.provisionSharedHost("host4");

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Must be able to allocate 2 nodes with "no resource requirement"
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 0, 0, 0, 0.0)),
                ClusterCapacity.class);

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Activate hosts
        List<Node> provisioned = tester.nodeRepository.list().state(Node.State.provisioned).asList();
        tester.nodeRepository.setReady(provisioned, Agent.system, this.getClass().getSimpleName());
        tester.provisioningTester.activateTenantHosts();

        // Allocating nodes to a host does not result in provisioning of additional capacity
        ApplicationId application = ProvisioningTester.applicationId();
        NodeResources applicationNodeResources = new NodeResources(4, 8, 50, 0.1);
        tester.provisioningTester.deploy(application,
                                         Capacity.from(new ClusterResources(2, 1, applicationNodeResources)));
        assertEquals(2, tester.nodeRepository.list().owner(application).size());
        tester.assertNodesUnchanged();

        // Clearing flag does nothing
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(), ClusterCapacity.class);
        tester.assertNodesUnchanged();

        // Increasing the capacity provisions additional hosts
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3, 0, 0, 0, 0.0)),
                ClusterCapacity.class);
        assertEquals(0, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.getNode("hostname102").isEmpty());
        tester.maintainer.maintain();
        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.getNode("hostname102").isPresent());

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Requiring >0 capacity does nothing as long as it fits on the 3 hosts
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu(),
                        resources1.memoryGb() - applicationNodeResources.memoryGb(),
                        resources1.diskGb() - applicationNodeResources.diskGb(),
                        resources1.bandwidthGbps() - applicationNodeResources.bandwidthGbps())),
                ClusterCapacity.class);
        tester.assertNodesUnchanged();

        // But requiring a bit more in the cluster => provisioning of 2 shared hosts.
        tester.flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu() + 1,
                        resources1.memoryGb() - applicationNodeResources.memoryGb() + 1,
                        resources1.diskGb() - applicationNodeResources.diskGb() + 1,
                        resources1.bandwidthGbps())),
                ClusterCapacity.class);

        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.getNode("hostname102").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname103").isEmpty());
        assertTrue(tester.nodeRepository.getNode("hostname104").isEmpty());
        tester.maintainer.maintain();
        assertEquals(3, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.getNode("hostname102").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname103").isPresent());
        assertTrue(tester.nodeRepository.getNode("hostname104").isPresent());
    }

    @Test
    public void defer_writing_ip_addresses_until_dns_resolves() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.hostProvisioner.with(Behaviour.failDnsUpdate);

        Supplier<List<Node>> provisioning = () -> tester.nodeRepository.getNodes(NodeType.host, Node.State.provisioned);
        assertEquals(2, provisioning.get().size());
        tester.maintainer.maintain();

        assertTrue("No IP addresses written as DNS updates are failing",
                   provisioning.get().stream().allMatch(host -> host.ipConfig().pool().getIpSet().isEmpty()));

        tester.hostProvisioner.without(Behaviour.failDnsUpdate);
        tester.maintainer.maintain();
        assertTrue("IP addresses written as DNS updates are succeeding",
                   provisioning.get().stream().noneMatch(host -> host.ipConfig().pool().getIpSet().isEmpty()));
    }

    private static class DynamicProvisioningTester {

        private static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        private static final ApplicationId tenantHostApp = ApplicationId.from("vespa", "tenant-host", "default");
        private static final ApplicationId proxyHostApp = ApplicationId.from("vespa", "proxy-host", "default");
        private static final ApplicationId proxyApp = ApplicationId.from("vespa", "proxy", "default");
        private static final NodeFlavors flavors = FlavorConfigBuilder.createDummies("default", "docker", "host2", "host3", "host4");

        private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

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
                    createNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantHostApp)),
                    createNode("host2-1", Optional.of("host2"), NodeType.tenant, Node.State.failed, Optional.empty()),
                    createNode("host3", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty(),
                            "host3-1", "host3-2", "host3-3", "host3-4", "host3-5"),
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

        private Node createNode(String hostname, Optional<String> parentHostname, NodeType nodeType,
                                Node.State state, Optional<ApplicationId> application, String... additionalHostnames) {
            Flavor flavor = nodeRepository.flavors().getFlavor(parentHostname.isPresent() ? "docker" : "host3").orElseThrow();
            Optional<Allocation> allocation = application
                    .map(app -> new Allocation(
                            app,
                            ClusterMembership.from("container/default/0/0", Version.fromString("7.3"), Optional.empty()),
                            flavor.resources(),
                            Generation.initial(),
                            false));
            List<Address> addresses = Stream.of(additionalHostnames).map(Address::new).collect(Collectors.toList());
            Node.Builder builder = Node.create("fake-id-" + hostname, hostname, flavor, state, nodeType)
                    .ipConfig(new IP.Config(state == Node.State.active ? Set.of("::1") : Set.of(), Set.of(), addresses));
            parentHostname.ifPresent(builder::parentHostname);
            allocation.ifPresent(builder::allocation);
            return builder.build();
        }

        private long provisionedHostsMatching(NodeResources resources) {
            return hostProvisioner.provisionedHosts.stream()
                                                   .filter(host -> host.generateHost().resources().compatibleWith(resources))
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
        private Optional<Flavor> provisionHostFlavor = Optional.empty();

        public MockHostProvisioner(NodeFlavors flavors, MockNameResolver nameResolver) {
            this.flavors = flavors;
            this.nameResolver = nameResolver;
        }

        public MockHostProvisioner provisionSharedHost(String flavorName) {
            provisionHostFlavor = Optional.of(flavors.getFlavorOrThrow(flavorName));
            return this;
        }

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources,
                                                    ApplicationId applicationId, Version osVersion, HostSharing sharing) {
            Flavor hostFlavor = provisionHostFlavor
                    .orElseGet(() -> flavors.getFlavors().stream()
                            .filter(f -> !f.isDocker())
                            .filter(f -> f.resources().compatibleWith(resources))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("No host flavor found satisfying " + resources)));

            List<ProvisionedHost> hosts = new ArrayList<>();
            for (int index : provisionIndexes) {
                hosts.add(new ProvisionedHost("host" + index,
                                              "hostname" + index,
                                              hostFlavor,
                                              Optional.empty(),
                                              createAddressesForHost(hostFlavor, index),
                                              resources,
                                              osVersion));
            }
            provisionedHosts.addAll(hosts);
            return hosts;
        }

        private List<Address> createAddressesForHost(Flavor flavor, int hostIndex) {
            long numAddresses = Math.max(1, Math.round(flavor.resources().bandwidthGbps()));
            return IntStream.range(0, (int) numAddresses)
                    .mapToObj(i -> new Address("nodename" + hostIndex + "_" + i))
                    .collect(Collectors.toList());
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
            Set<String> ipAddressPool = new HashSet<>();
            if (!behaviours.contains(Behaviour.failDnsUpdate)) {
                nameResolver.addRecord(node.hostname(), addresses.iterator().next());
                for (int i = 1; i <= 2; i++) {
                    String ip = "::" + hostIndex + ":" + i;
                    ipAddressPool.add(ip);
                    nameResolver.addRecord(node.hostname() + "-" + i, ip);
                }
            }

            IP.Pool pool = node.ipConfig().pool().withIpAddresses(ipAddressPool);
            return node.with(node.ipConfig().withPrimary(addresses).withPool(pool));
        }

        enum Behaviour {
            failProvisioning,
            failDeprovisioning,
            failDnsUpdate,
        }

    }

}
