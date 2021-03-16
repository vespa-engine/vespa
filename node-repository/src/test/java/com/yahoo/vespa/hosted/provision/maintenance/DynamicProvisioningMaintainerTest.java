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
import com.yahoo.net.HostName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ConfigServerHostApplication;
import com.yahoo.vespa.service.duper.ControllerApplication;
import com.yahoo.vespa.service.duper.ControllerHostApplication;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner.Behaviour;
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

        Node host3 = tester.nodeRepository.nodes().node("host3").orElseThrow();
        Node host4 = tester.nodeRepository.nodes().node("host4").orElseThrow();
        Node host41 = tester.nodeRepository.nodes().node("host4-1").orElseThrow();
        assertTrue("No IP addresses assigned",
                   Stream.of(host3, host4, host41).map(node -> node.ipConfig().primary()).allMatch(Set::isEmpty));

        Node host3new = host3.with(host3.ipConfig().withPrimary(Set.of("::3:0")));
        Node host4new = host4.with(host4.ipConfig().withPrimary(Set.of("::4:0")));
        Node host41new = host41.with(host41.ipConfig().withPrimary(Set.of("::4:1", "::4:2")));

        tester.maintainer.maintain();
        assertEquals(host3new, tester.nodeRepository.nodes().node("host3").get());
        assertEquals(host4new, tester.nodeRepository.nodes().node("host4").get());
        assertEquals(host41new, tester.nodeRepository.nodes().node("host4-1").get());
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
                     tester.nodeRepository.nodes().list(Node.State.failed).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    @Test
    public void finds_nodes_that_need_deprovisioning_without_pre_provisioning() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.nodes().node("host2").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("host3").isEmpty());
    }

    @Test
    public void does_not_deprovision_when_preprovisioning_enabled() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(), List.of(new ClusterCapacity(1, 1, 3, 2, 1.0)), ClusterCapacity.class);
        Optional<Node> failedHost = tester.nodeRepository.nodes().node("host2");
        assertTrue(failedHost.isPresent());

        tester.maintainer.maintain();
        assertTrue("Failed host is deprovisioned", tester.nodeRepository.nodes().node(failedHost.get().hostname()).isEmpty());
        assertEquals(1, tester.hostProvisioner.deprovisionedHosts());
    }

    @Test
    public void provision_deficit_and_deprovision_excess() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                                       List.of(new ClusterCapacity(2, 48, 128, 1000, 10.0),
                                               new ClusterCapacity(1, 16, 24, 100, 1.0)),
                                       ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(11, tester.nodeRepository.nodes().list().size());
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host2-1").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname100").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("hostname101").isEmpty());

        tester.maintainer.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        NodeList nodesAfter = tester.nodeRepository.nodes().list();
        assertEquals(11, nodesAfter.size());  // 2 removed, 2 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.nodes().node("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.nodes().node("host2-1").isEmpty());
        assertTrue("Host satisfying 16-24-100-1 is kept", tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("hostname100").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("hostname101").isPresent());
    }

    @Test
    public void preprovision_with_shared_host() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        // Makes provisioned hosts 48-128-1000-10
        tester.hostProvisioner.overrideHostFlavor("host4");

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 1, 30, 20, 3.0)),
                ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(11, tester.nodeRepository.nodes().list().size());
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host2-1").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname100").isEmpty());

        // The first cluster will be allocated to host3 and a new host hostname100.
        // hostname100 will be a large shared host specified above.
        tester.maintainer.maintain();
        verifyFirstMaintain(tester);

        // Second maintain should be a no-op, otherwise we did wrong in the first maintain.
        tester.maintainer.maintain();
        verifyFirstMaintain(tester);

        // Add a second cluster equal to the first.  It should fit on existing host3 and hostname100.

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
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

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 1, 30, 20, 3.0),
                        new ClusterCapacity(2, 24, 64, 100, 1.0)),
                ClusterCapacity.class);

        tester.maintainer.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(10, tester.nodeRepository.nodes().list().size());  // 3 removed, 2 added
        assertTrue("preprovision capacity is prefered on shared hosts", tester.nodeRepository.nodes().node("host3").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("hostname100").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname101").isPresent());

        // If the preprovision capacity is reduced, we should see shared hosts deprovisioned.

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(1, 1, 30, 20, 3.0)),
                ClusterCapacity.class);

        tester.maintainer.maintain();

        assertEquals("one provisioned host has been deprovisioned, so there are 2 -> 1 provisioned hosts",
                     1, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(9, tester.nodeRepository.nodes().list().size());  // 4 removed, 2 added
        if (tester.nodeRepository.nodes().node("hostname100").isPresent()) {
            assertTrue("hostname101 is superfluous and should have been deprovisioned",
                    tester.nodeRepository.nodes().node("hostname101").isEmpty());
        } else {
            assertTrue("hostname101 is required for preprovision capacity",
                    tester.nodeRepository.nodes().node("hostname101").isPresent());
        }

    }

    private void verifyFirstMaintain(DynamicProvisioningTester tester) {
        assertEquals(1, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(10, tester.nodeRepository.nodes().list().size());  // 2 removed, 1 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.nodes().node("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.nodes().node("host2-1").isEmpty());
        assertTrue("One 1-30-20-3 node fits on host3", tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("hostname100").isPresent());
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
        tester.hostProvisioner.overrideHostFlavor("host4");

        tester.flagSource.withJacksonFlag(PermanentFlags.SHARED_HOST.id(), new SharedHost(null, minCount), SharedHost.class);
        tester.maintainer.maintain();
        assertEquals(provisionCount, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(deprovisionCount, tester.hostProvisioner.deprovisionedHosts());

        // Verify next maintain is a no-op
        tester.maintainer.maintain();
        assertEquals(provisionCount, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(deprovisionCount, tester.hostProvisioner.deprovisionedHosts());
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        var tester = new DynamicProvisioningTester();
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, DynamicProvisioningTester.tenantApp);
        tester.hostProvisioner.with(Behaviour.failDeprovisioning);

        tester.maintainer.maintain();
        assertTrue(tester.nodeRepository.nodes().node(host2.hostname()).isPresent());
    }

    @Test
    public void test_minimum_capacity() {
        var tester = new DynamicProvisioningTester();
        NodeResources resources1 = new NodeResources(24, 64, 100, 10);
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(), resources1.bandwidthGbps())),
                ClusterCapacity.class);
        tester.maintainer.maintain();

        // Hosts are provisioned
        assertEquals(2, tester.provisionedHostsMatching(resources1));
        assertEquals(0, tester.hostProvisioner.deprovisionedHosts());

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Pretend shared-host flag has been set to host4's flavor
        var sharedHostNodeResources = new NodeResources(48, 128, 1000, 10, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote);
        tester.hostProvisioner.overrideHostFlavor("host4");

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Must be able to allocate 2 nodes with "no resource requirement"
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 0, 0, 0, 0.0)),
                ClusterCapacity.class);

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Activate hosts
        List<Node> provisioned = tester.nodeRepository.nodes().list().state(Node.State.provisioned).asList();
        tester.nodeRepository.nodes().setReady(provisioned, Agent.system, this.getClass().getSimpleName());
        tester.provisioningTester.activateTenantHosts();

        // Allocating nodes to a host does not result in provisioning of additional capacity
        ApplicationId application = ProvisioningTester.applicationId();
        NodeResources applicationNodeResources = new NodeResources(4, 8, 50, 0.1);
        tester.provisioningTester.deploy(application,
                                         Capacity.from(new ClusterResources(2, 1, applicationNodeResources)));
        assertEquals(2, tester.nodeRepository.nodes().list().owner(application).size());
        tester.assertNodesUnchanged();

        // Clearing flag does nothing
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(), List.of(), ClusterCapacity.class);
        tester.assertNodesUnchanged();

        // Increasing the capacity provisions additional hosts
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3, 0, 0, 0, 0.0)),
                ClusterCapacity.class);
        assertEquals(0, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("hostname102").isEmpty());
        tester.maintainer.maintain();
        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("hostname102").isPresent());

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Requiring >0 capacity does nothing as long as it fits on the 3 hosts
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu(),
                        resources1.memoryGb() - applicationNodeResources.memoryGb(),
                        resources1.diskGb() - applicationNodeResources.diskGb(),
                        resources1.bandwidthGbps() - applicationNodeResources.bandwidthGbps())),
                ClusterCapacity.class);
        tester.assertNodesUnchanged();

        // But requiring a bit more in the cluster => provisioning of 2 shared hosts.
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu() + 1,
                        resources1.memoryGb() - applicationNodeResources.memoryGb() + 1,
                        resources1.diskGb() - applicationNodeResources.diskGb() + 1,
                        resources1.bandwidthGbps())),
                ClusterCapacity.class);

        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("hostname102").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname103").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("hostname104").isEmpty());
        tester.maintainer.maintain();
        assertEquals(3, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("hostname102").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname103").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("hostname104").isPresent());
    }

    @Test
    public void defer_writing_ip_addresses_until_dns_resolves() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.hostProvisioner.with(Behaviour.failDnsUpdate);

        Supplier<NodeList> provisioning = () -> tester.nodeRepository.nodes().list(Node.State.provisioned).nodeType(NodeType.host);
        assertEquals(2, provisioning.get().size());
        tester.maintainer.maintain();

        assertTrue("No IP addresses written as DNS updates are failing",
                   provisioning.get().stream().allMatch(host -> host.ipConfig().pool().getIpSet().isEmpty()));

        tester.hostProvisioner.without(Behaviour.failDnsUpdate);
        tester.maintainer.maintain();
        assertTrue("IP addresses written as DNS updates are succeeding",
                   provisioning.get().stream().noneMatch(host -> host.ipConfig().pool().getIpSet().isEmpty()));
    }

    @Test
    public void deprovision_empty_confighost() {
        // cfghost1, cfg1, cfghost2, cfg2, cfghost3, and NOT cfg3.
        var tester = new DynamicProvisioningTester();
        tester.addCfghost(1, true);
        tester.addCfghost(2, true);
        Node cfghost3 = tester.addCfghost(3, false);

        // cfghost3 is active before maintain, and active after:
        assertCfghost3IsActive(tester);
        tester.maintainer.maintain();
        assertCfghost3IsActive(tester);

        // But when cfghost3 is moved to parked w/wantToDeprovision, maintain() should deprovision
        Node parkedWithWantToDeprovision = cfghost3.withWantToRetire(true, // wantToRetire
                                                                     true, // wantToDeprovision
                                                                     Agent.operator,
                                                                     Instant.now());
        tester.nodeRepository.database().writeTo(Node.State.parked, parkedWithWantToDeprovision, Agent.operator, Optional.empty());
        tester.maintainer.maintain();
        assertCfghost3IsDeprovisioned(tester);
    }

    @Test
    public void replace_config_server() {
        replace_config_server_like(NodeType.confighost);
    }

    @Test
    public void replace_controller() {
        replace_config_server_like(NodeType.controllerhost);
    }

    public void replace_config_server_like(NodeType hostType) {
        final ApplicationId hostApp;
        final ApplicationId configSrvApp;
        switch (hostType) {
            case confighost:
                hostApp = new ConfigServerHostApplication().getApplicationId();
                configSrvApp = new ConfigServerApplication().getApplicationId();
                break;
            case controllerhost:
                hostApp = new ControllerHostApplication().getApplicationId();
                configSrvApp = new ControllerApplication().getApplicationId();
                break;
            default:
                throw new IllegalArgumentException("Unexpected config server host like node type: " + hostType);
        }

        Cloud cloud = Cloud.builder().dynamicProvisioning(true).build();
        DynamicProvisioningTester dynamicProvisioningTester = new DynamicProvisioningTester(cloud, new MockNameResolver().mockAnyLookup());
        ProvisioningTester tester = dynamicProvisioningTester.provisioningTester;
        dynamicProvisioningTester.hostProvisioner.overrideHostFlavor("default");
        dynamicProvisioningTester.flagSource.withBooleanFlag(Flags.DYNAMIC_CONFIG_SERVER_PROVISIONING.id(), true);

        // Initial config server hosts are provisioned manually
        List<Node> provisionedHosts = tester.makeReadyNodes(3, "default", hostType).stream()
                                            .sorted(Comparator.comparing(Node::hostname))
                                            .collect(Collectors.toList());
        tester.prepareAndActivateInfraApplication(hostApp, hostType);

        // Provision config servers
        for (int i = 0; i < provisionedHosts.size(); i++) {
            tester.makeReadyChildren(1, i + 1, NodeResources.unspecified(), hostType.childNodeType(),
                    provisionedHosts.get(i).hostname(), (nodeIndex) -> "cfg" + nodeIndex);
        }
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());

        // Expected number of hosts and children are provisioned
        NodeList allNodes = tester.nodeRepository().nodes().list();
        NodeList configHosts = allNodes.nodeType(hostType);
        NodeList configNodes = allNodes.nodeType(hostType.childNodeType());
        assertEquals(3, configHosts.size());
        assertEquals(3, configNodes.size());
        String hostnameToRemove = provisionedHosts.get(1).hostname();
        Supplier<Node> hostToRemove = () -> tester.nodeRepository().nodes().node(hostnameToRemove).get();
        Supplier<Node> nodeToRemove = () -> tester.nodeRepository().nodes().node(configNodes.childrenOf(hostnameToRemove).first().get().hostname()).get();

        // Set want to retire and deprovision on host and children
        tester.nodeRepository().nodes().deprovision(hostToRemove.get(), Agent.system, tester.clock().instant());

        // Redeployment of config server application retires node
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        assertTrue("Redeployment retires node", nodeToRemove.get().allocation().get().membership().retired());

        // Config server becomes removable (done by RetiredExpirer in a real system) and redeployment moves it
        // to inactive
        tester.nodeRepository().nodes().setRemovable(configSrvApp, List.of(nodeToRemove.get()));
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        assertEquals("Node moves to inactive", Node.State.inactive, nodeToRemove.get().state());

        // Node is completely removed (done by InactiveExpirer and host-admin in a real system)
        Node inactiveConfigServer = nodeToRemove.get();
        int removedIndex = inactiveConfigServer.allocation().get().membership().index();
        tester.nodeRepository().nodes().removeRecursively(inactiveConfigServer, true);
        assertEquals(2, tester.nodeRepository().nodes().list().nodeType(hostType.childNodeType()).size());

        // ExpiredRetirer moves host to inactive after child has moved to parked
        tester.nodeRepository().nodes().deallocate(hostToRemove.get(), Agent.system, getClass().getSimpleName());
        assertSame("Host moves to parked", Node.State.parked, hostToRemove.get().state());

        // Host is removed
        dynamicProvisioningTester.maintainer.maintain();
        assertEquals(2, tester.nodeRepository().nodes().list().nodeType(hostType).size());

        // Deployment by the removed host has no effect
        HostName.setHostNameForTestingOnly("cfg2.example.com");
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        assertEquals(List.of(), dynamicProvisioningTester.hostProvisioner.provisionedHosts());

        // Deployment on another config server starts provisioning a new host and child
        HostName.setHostNameForTestingOnly("cfg3.example.com");
        assertEquals(0, tester.nodeRepository().nodes().list(Node.State.reserved).nodeType(hostType.childNodeType()).size());
        assertEquals(2, tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType()).size());
        assertEquals(1, tester.nodeRepository().nodes().list(Node.State.reserved).nodeType(hostType.childNodeType()).size());
        Node newNode = tester.nodeRepository().nodes().list(Node.State.reserved).nodeType(hostType.childNodeType()).first().get();

        // Resume provisioning and activate host
        dynamicProvisioningTester.maintainer.maintain();
        List<ProvisionedHost> newHosts = dynamicProvisioningTester.hostProvisioner.provisionedHosts();
        assertEquals(1, newHosts.size());
        tester.nodeRepository().nodes().setReady(newHosts.get(0).hostHostname(), Agent.operator, getClass().getSimpleName());
        tester.prepareAndActivateInfraApplication(hostApp, hostType);
        assertEquals(3, tester.nodeRepository().nodes().list(Node.State.active).nodeType(hostType).size());

        // Redeployment of config server app actives new node
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        newNode = tester.nodeRepository().nodes().node(newNode.hostname()).get();
        assertSame(Node.State.active, newNode.state());
        assertEquals("Removed index is reused", removedIndex, newNode.allocation().get().membership().index());

        // Next redeployment does nothing
        NodeList nodesBefore = tester.nodeRepository().nodes().list().nodeType(hostType.childNodeType());
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        NodeList nodesAfter = tester.nodeRepository().nodes().list().nodeType(hostType.childNodeType());
        assertEquals(nodesBefore, nodesAfter);
    }

    private void assertCfghost3IsActive(DynamicProvisioningTester tester) {
        assertEquals(5, tester.nodeRepository.nodes().list(Node.State.active).size());
        assertEquals(3, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.confighost).size());
        Optional<Node> cfghost3 = tester.nodeRepository.nodes().node("cfghost3");
        assertTrue(cfghost3.isPresent());
        assertEquals(Node.State.active, cfghost3.get().state());
    }

    private void assertCfghost3IsDeprovisioned(DynamicProvisioningTester tester) {
        assertEquals(4, tester.nodeRepository.nodes().list(Node.State.active).size());
        assertEquals(2, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.confighost).size());
        assertTrue(tester.nodeRepository.nodes().node("cfghost3").isEmpty());
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
            this(Cloud.builder().dynamicProvisioning(true).build(), new MockNameResolver());
        }

        public DynamicProvisioningTester(Cloud cloud, MockNameResolver nameResolver) {
            this.hostProvisioner = new MockHostProvisioner(flavors.getFlavors(), nameResolver, 0);
            this.provisioningTester = new ProvisioningTester.Builder().zone(new Zone(cloud, SystemName.defaultSystem(),
                                                                                     Environment.defaultEnvironment(),
                                                                                     RegionName.defaultName()))
                                                                      .flavors(flavors.getFlavors())
                                                                      .nameResolver(nameResolver)
                                                                      .flagSource(flagSource)
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

        private Node addCfghost(int index, boolean makeChild) {
            Node cfghost = addNode("cfghost" + index, Optional.empty(), NodeType.confighost,
                    Node.State.active, new ConfigServerHostApplication().getApplicationId());

            if (makeChild) {
                addNode("cfg" + index, Optional.of("cfghost" + index), NodeType.config,
                        Node.State.active, new ConfigServerApplication().getApplicationId());
            }

            return cfghost;
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
            return hostProvisioner.provisionedHosts().stream()
                                  .filter(host -> host.generateHost().resources().compatibleWith(resources))
                                  .count();
        }

        private void assertNodesUnchanged() {
            NodeList nodes = nodeRepository.nodes().list();
            maintainer.maintain();
            assertEquals("Nodes are unchanged after maintenance run", nodes, nodeRepository.nodes().list());
        }

    }

}

