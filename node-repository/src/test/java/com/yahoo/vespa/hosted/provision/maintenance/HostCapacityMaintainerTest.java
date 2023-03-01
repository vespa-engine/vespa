// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.net.HostName;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.InfraDeployerImpl;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDuperModel;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ConfigServerHostApplication;
import com.yahoo.vespa.service.duper.ControllerApplication;
import com.yahoo.vespa.service.duper.ControllerHostApplication;
import com.yahoo.vespa.service.duper.InfraApplication;
import com.yahoo.vespa.service.duper.TenantHostApplication;
import org.junit.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner.Behaviour;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author freva
 * @author mpolden
 */
public class HostCapacityMaintainerTest {

    @Test
    public void finds_nodes_that_need_deprovisioning_without_pre_provisioning() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());

        tester.maintain();
        assertTrue(tester.nodeRepository.nodes().node("host2").isEmpty());
    }

    @Test
    public void does_not_deprovision_when_preprovisioning_enabled() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(), List.of(new ClusterCapacity(1, 1.0, 3.0, 2.0, 1.0, "fast", "local", "x86_64")), ClusterCapacity.class);
        Optional<Node> failedHost = tester.nodeRepository.nodes().node("host2");
        assertTrue(failedHost.isPresent());

        tester.maintain();
        assertTrue("Failed host is deprovisioned", tester.nodeRepository.nodes().node(failedHost.get().hostname()).isEmpty());
        assertEquals(1, tester.hostProvisioner.deprovisionedHosts());
    }

    @Test
    public void provision_deficit_and_deprovision_excess() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                                       List.of(new ClusterCapacity(2, 48.0, 128.0, 1000.0, 10.0, "fast", "local", "x86_64"),
                                               new ClusterCapacity(1, 16.0, 24.0, 100.0, 1.0, "fast", "local", "x86_64")),
                                       ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(9, tester.nodeRepository.nodes().list().size());
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host2-1").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host100").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("host101").isEmpty());

        tester.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        NodeList nodesAfter = tester.nodeRepository.nodes().list();
        assertEquals(9, nodesAfter.size());  // 2 removed, 2 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.nodes().node("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.nodes().node("host2-1").isEmpty());
        assertTrue("Host satisfying 16-24-100-1 is kept", tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("host100").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("host101").isPresent());
    }

    @Test
    public void preprovision_with_shared_host() {
        var tester = new DynamicProvisioningTester().addInitialNodes();
        // Makes provisioned hosts 48-128-1000-10
        tester.hostProvisioner.setHostFlavor("host4");
        var clusterCapacity = new ClusterCapacity(2, 1.0, 30.0, 20.0, 3.0, "fast", "local", "x86_64");
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(clusterCapacity),
                ClusterCapacity.class);

        assertEquals(0, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(9, tester.nodeRepository.nodes().list().size());
        assertTrue(tester.nodeRepository.nodes().node("host2").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host2-1").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host100").isEmpty());

        // The first cluster will be allocated to host3 and a new host host100.
        // host100 will be a large shared host specified above.
        tester.maintain();
        verifyFirstMaintain(tester);

        // Second maintain should be a no-op, otherwise we did wrong in the first maintain.
        tester.maintain();
        verifyFirstMaintain(tester);

        // Add a second cluster equal to the first.  It should fit on existing host3 and host100.
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(clusterCapacity,
                        clusterCapacity),
                ClusterCapacity.class);

        tester.maintain();
        verifyFirstMaintain(tester);

        // Change second cluster such that it doesn't fit on host3, but does on host100,
        // and with a size of 2 it should allocate a new shared host.
        // The node allocation code prefers to allocate to the shared hosts instead of host3 (at least
        // in this test, due to skew), so host3 will be deprovisioned when host101 is provisioned.
        // host3 is a 24-64-100-10 while host100 is 48-128-1000-10.

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(clusterCapacity,
                        new ClusterCapacity(2, 24.0, 64.0, 100.0, 1.0, "fast", "local", "x86_64")),
                ClusterCapacity.class);

        tester.maintain();

        assertEquals(2, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(2, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(8, tester.nodeRepository.nodes().list().size());  // 3 removed, 2 added
        assertTrue("preprovision capacity is prefered on shared hosts", tester.nodeRepository.nodes().node("host3").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("host100").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host101").isPresent());

        // If the preprovision capacity is reduced, we should see shared hosts deprovisioned.

        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(1, 1.0, 30.0, 20.0, 3.0, "fast", "local", "x86_64")),
                ClusterCapacity.class);

        tester.maintain();

        assertEquals("one provisioned host has been deprovisioned, so there are 2 -> 1 provisioned hosts",
                     1, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(7, tester.nodeRepository.nodes().list().size());  // 4 removed, 2 added
        if (tester.nodeRepository.nodes().node("host100").isPresent()) {
            assertTrue("host101 is superfluous and should have been deprovisioned",
                    tester.nodeRepository.nodes().node("host101").isEmpty());
        } else {
            assertTrue("host101 is required for preprovision capacity",
                    tester.nodeRepository.nodes().node("host101").isPresent());
        }

    }

    private void verifyFirstMaintain(DynamicProvisioningTester tester) {
        assertEquals(1, tester.hostProvisioner.provisionedHosts().size());
        assertEquals(1, tester.provisionedHostsMatching(new NodeResources(48, 128, 1000, 10)));
        assertEquals(8, tester.nodeRepository.nodes().list().size());  // 2 removed, 1 added
        assertTrue("Failed host 'host2' is deprovisioned", tester.nodeRepository.nodes().node("host2").isEmpty());
        assertTrue("Node on deprovisioned host removed", tester.nodeRepository.nodes().node("host2-1").isEmpty());
        assertTrue("One 1-30-20-3 node fits on host3", tester.nodeRepository.nodes().node("host3").isPresent());
        assertTrue("New 48-128-1000-10 host added", tester.nodeRepository.nodes().node("host100").isPresent());
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        var tester = new DynamicProvisioningTester();
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, DynamicProvisioningTester.tenantApp);
        tester.hostProvisioner.with(Behaviour.failDeprovisioning);

        tester.maintain();
        assertTrue(tester.nodeRepository.nodes().node(host2.hostname()).isPresent());
    }

    @Test
    public void test_minimum_capacity() {
        var tester = new DynamicProvisioningTester();
        NodeResources resources1 = new NodeResources(24, 64, 100, 10);
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, resources1.vcpu(), resources1.memoryGb(), resources1.diskGb(),
                                            resources1.bandwidthGbps(), resources1.diskSpeed().name(),
                                            resources1.storageType().name(), resources1.architecture().name())),
                ClusterCapacity.class);
        tester.maintain();

        // Hosts are provisioned
        assertEquals(2, tester.provisionedHostsMatching(resources1));
        assertEquals(0, tester.hostProvisioner.deprovisionedHosts());

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Pretend shared-host flag has been set to host4's flavor
        var sharedHostNodeResources = new NodeResources(48, 128, 1000, 10, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote);
        tester.hostProvisioner.setHostFlavor("host4");

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Must be able to allocate 2 nodes with "no resource requirement"
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(2, 0.0, 0.0, 0.0, 0.0, null, null, null)),
                ClusterCapacity.class);

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Activate hosts
        List<Node> provisioned = tester.nodeRepository.nodes().list().state(Node.State.provisioned).asList();
        tester.provisioningTester.move(Node.State.ready, provisioned);
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
                List.of(new ClusterCapacity(3, 0.0, 0.0, 0.0, 0.0, null, null, null)),
                ClusterCapacity.class);
        assertEquals(0, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("host102").isEmpty());
        tester.maintain();
        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("host102").isPresent());

        // Next maintenance run does nothing
        tester.assertNodesUnchanged();

        // Requiring >0 capacity does nothing as long as it fits on the 3 hosts
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu(),
                        resources1.memoryGb() - applicationNodeResources.memoryGb(),
                        resources1.diskGb() - applicationNodeResources.diskGb(),
                        resources1.bandwidthGbps() - applicationNodeResources.bandwidthGbps(),
                        resources1.diskSpeed().name(),
                        resources1.storageType().name(),
                        resources1.architecture().name())),
                ClusterCapacity.class);
        tester.assertNodesUnchanged();

        // But requiring a bit more in the cluster => provisioning of 2 shared hosts.
        tester.flagSource.withListFlag(PermanentFlags.PREPROVISION_CAPACITY.id(),
                List.of(new ClusterCapacity(3,
                        resources1.vcpu() - applicationNodeResources.vcpu() + 1,
                        resources1.memoryGb() - applicationNodeResources.memoryGb() + 1,
                        resources1.diskGb() - applicationNodeResources.diskGb() + 1,
                        resources1.bandwidthGbps(),
                        resources1.diskSpeed().name(),
                        resources1.storageType().name(),
                        resources1.architecture().name())),
                ClusterCapacity.class);

        assertEquals(1, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("host102").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host103").isEmpty());
        assertTrue(tester.nodeRepository.nodes().node("host104").isEmpty());
        tester.maintain();
        assertEquals(3, tester.provisionedHostsMatching(sharedHostNodeResources));
        assertTrue(tester.nodeRepository.nodes().node("host102").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host103").isPresent());
        assertTrue(tester.nodeRepository.nodes().node("host104").isPresent());
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
        tester.maintain();
        assertCfghost3IsActive(tester);

        // But when cfghost3 is moved to parked w/wantToDeprovision, maintain() should deprovision
        Node parkedWithWantToDeprovision = cfghost3.withWantToRetire(true, // wantToRetire
                                                                     true, // wantToDeprovision
                                                                     Agent.operator,
                                                                     Instant.now());
        tester.nodeRepository.database().writeTo(Node.State.parked, parkedWithWantToDeprovision, Agent.operator, Optional.empty());
        tester.maintain();
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

    private void replace_config_server_like(NodeType hostType) {
        final ApplicationId hostApp;
        final ApplicationId configSrvApp;
        switch (hostType) {
            case confighost -> {
                hostApp = new ConfigServerHostApplication().getApplicationId();
                configSrvApp = new ConfigServerApplication().getApplicationId();
            }
            case controllerhost -> {
                hostApp = new ControllerHostApplication().getApplicationId();
                configSrvApp = new ControllerApplication().getApplicationId();
            }
            default -> throw new IllegalArgumentException("Unexpected config server host like node type: " + hostType);
        }

        Cloud cloud = Cloud.builder().dynamicProvisioning(true).build();
        DynamicProvisioningTester dynamicProvisioningTester = new DynamicProvisioningTester(cloud, new MockNameResolver().mockAnyLookup());
        ProvisioningTester tester = dynamicProvisioningTester.provisioningTester;
        dynamicProvisioningTester.hostProvisioner.setHostFlavor("default");

        // Initial config server hosts are provisioned manually
        List<Node> provisionedHosts = tester.makeReadyNodes(3, "default", hostType, 1).stream()
                                            .sorted(Comparator.comparing(Node::hostname))
                                            .toList();
        tester.prepareAndActivateInfraApplication(hostApp, hostType);

        // Provision config servers
        for (int i = 0; i < provisionedHosts.size(); i++) {
            tester.makeReadyChildren(1, i + 1, new NodeResources(1.5, 8, 50, 0.3), hostType.childNodeType(),
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
        tester.nodeRepository().nodes().deprovision(hostToRemove.get().hostname(), Agent.system, tester.clock().instant());

        // Redeployment of config server application retires node
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        assertTrue("Redeployment retires node", nodeToRemove.get().allocation().get().membership().retired());

        // Config server becomes removable (done by RetiredExpirer in a real system) and redeployment moves it
        // to parked
        int removedIndex = nodeToRemove.get().allocation().get().membership().index();
        tester.nodeRepository().nodes().setRemovable(configSrvApp, List.of(nodeToRemove.get()), true);
        tester.nodeRepository().nodes().setRemovable(hostApp, List.of(hostToRemove.get()), true);
        tester.prepareAndActivateInfraApplication(configSrvApp, hostType.childNodeType());
        tester.prepareAndActivateInfraApplication(hostApp, hostType);
        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation(nodeToRemove.get().hostname(), Agent.operator, "Readied by host-admin");
        tester.nodeRepository().nodes().markNodeAvailableForNewAllocation(hostToRemove.get().hostname(), Agent.operator, "Readied by host-admin");
        assertEquals(2, tester.nodeRepository().nodes().list().nodeType(hostType.childNodeType()).state(Node.State.active).size());
        assertSame("Node moves to expected state", Node.State.parked, nodeToRemove.get().state());
        assertSame("Host moves to parked", Node.State.parked, hostToRemove.get().state());

        // deprovisioning host cannot be unparked
        try {
            tester.nodeRepository().nodes().deallocate(hostToRemove.get(), Agent.operator, getClass().getSimpleName());
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        // Host and child is removed
        dynamicProvisioningTester.maintain();
        allNodes = tester.nodeRepository().nodes().list();
        assertEquals(2, allNodes.nodeType(hostType).size());
        assertEquals(2, allNodes.nodeType(hostType.childNodeType()).size());

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
        dynamicProvisioningTester.maintain();
        List<ProvisionedHost> newHosts = dynamicProvisioningTester.hostProvisioner.provisionedHosts();
        assertEquals(1, newHosts.size());
        tester.move(Node.State.ready, newHosts.get(0).hostHostname());
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

    @Test
    public void custom_cloud_account() {
        DynamicProvisioningTester tester = new DynamicProvisioningTester(Cloud.builder().dynamicProvisioning(true).build(),
                                                                         new MockNameResolver().mockAnyLookup());
        ProvisioningTester provisioningTester = tester.provisioningTester;
        ApplicationId applicationId = ApplicationId.from("t1", "a1", "i1");

        // Deployment requests capacity in custom account
        ClusterSpec spec = ProvisioningTester.contentClusterSpec();
        ClusterResources resources = new ClusterResources(2, 1, new NodeResources(16, 24, 100, 1));
        CloudAccount cloudAccount0 = CloudAccount.from("000000000000");
        Capacity capacity0 = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(cloudAccount0), ClusterInfo.empty());
        List<HostSpec> prepared = provisioningTester.prepare(applicationId, spec, capacity0);

        // Hosts are provisioned in requested account
        provisionHostsIn(cloudAccount0, 2, tester);
        assertEquals(2, provisioningTester.activate(applicationId, prepared).size());
        NodeList allNodes0 = tester.nodeRepository.nodes().list();

        // Redeployment in different account provisions a new set of hosts
        CloudAccount cloudAccount1 = CloudAccount.from("100000000000");
        Capacity capacity1 = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(cloudAccount1), ClusterInfo.empty());
        prepared = provisioningTester.prepare(applicationId, spec, capacity1);
        provisionHostsIn(cloudAccount1, 2, tester);
        assertEquals(2, provisioningTester.activate(applicationId, prepared).size());

        // No nodes or hosts are reused
        NodeList allNodes1 = tester.nodeRepository.nodes().list();
        NodeList activeNodes0 = allNodes0.state(Node.State.active).owner(applicationId);
        NodeList activeNodes1 = allNodes1.state(Node.State.active).owner(applicationId);
        assertTrue("New set of nodes is activated",
                   Collections.disjoint(activeNodes0.asList(),
                                        activeNodes1.asList()));
        assertTrue("New set of parents are used",
                   Collections.disjoint(allNodes0.parentsOf(activeNodes0).asList(),
                                        allNodes1.parentsOf(activeNodes1).asList()));
    }

    @Test
    public void deprovision_parked_node_with_allocation() {
        var tester = new DynamicProvisioningTester();
        tester.hostProvisioner.with(Behaviour.failProvisioning);
        Node host4 = tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.parked);
        Node host41 = tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.parked, DynamicProvisioningTester.tenantApp);
        Node host42 = tester.addNode("host4-2", Optional.of("host4"), NodeType.tenant, Node.State.active, DynamicProvisioningTester.tenantApp);
        Node host43 = tester.addNode("host4-3", Optional.of("host4"), NodeType.tenant, Node.State.failed, DynamicProvisioningTester.tenantApp);

        // Host and children are marked for deprovisioning
        tester.nodeRepository.nodes().deprovision("host4", Agent.operator, Instant.now());
        for (var node : List.of(host4, host41, host42, host43)) {
            assertTrue(tester.nodeRepository.nodes().node(node.hostname()).map(n -> n.status().wantToDeprovision()).get());
        }

        // Host and children remain parked because one child is still active
        tester.maintain();
        for (var node : List.of(host4, host41)) {
            assertEquals(Node.State.parked, tester.nodeRepository.nodes().node(node.hostname()).get().state());
        }
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(host42.hostname()).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(host43.hostname()).get().state());

        // Last child is parked
        tester.nodeRepository.nodes().park(host42.hostname(), false, Agent.system, getClass().getSimpleName());

        // Host and children can now be removed
        tester.maintain();
        for (var node : List.of(host4, host41, host42, host43)) {
            assertTrue(node.hostname() + " removed", tester.nodeRepository.nodes().node(node.hostname()).isEmpty());
        }
    }

    private void provisionHostsIn(CloudAccount cloudAccount, int count, DynamicProvisioningTester tester) {
        tester.maintain();
        List<String> provisionedHostnames = tester.hostProvisioner.provisionedHosts().stream()
                                                                  .filter(host -> host.cloudAccount().equals(cloudAccount))
                                                                  .map(ProvisionedHost::hostHostname)
                                                                  .toList();
        assertEquals(count, provisionedHostnames.size());
        for (var hostname : provisionedHostnames) {
            tester.provisioningTester.move(Node.State.ready, hostname);
        }
        tester.provisioningTester.prepareAndActivateInfraApplication(DynamicProvisioningTester.tenantHostApp.getApplicationId(), NodeType.host);
        NodeList activeHosts = tester.provisioningTester.nodeRepository().nodes()
                                                        .list(Node.State.active)
                                                        .nodeType(NodeType.host)
                                                        .matching(host -> provisionedHostnames.contains(host.hostname()));
        assertTrue(activeHosts.stream().allMatch(host -> host.cloudAccount().equals(cloudAccount)));
        assertEquals(count, activeHosts.size());
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

        private static final InfraApplication tenantHostApp = new TenantHostApplication();
        private static final InfraApplication configServerHostApp = new ConfigServerHostApplication();
        private static final InfraApplication configServerApp = new ConfigServerApplication();
        private static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        private static final NodeFlavors flavors = FlavorConfigBuilder.createDummies("default", "docker", "host2", "host3", "host4");

        private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

        private final NodeRepository nodeRepository;
        private final MockHostProvisioner hostProvisioner;
        private final ProvisioningTester provisioningTester;
        private final HostCapacityMaintainer capacityMaintainer;
        private final HostResumeProvisioner resumeProvisioner;
        private final HostDeprovisioner deprovisioner;
        private final InfraDeployerImpl infraDeployer;

        public DynamicProvisioningTester() {
            this(Cloud.builder().dynamicProvisioning(true).build(), new MockNameResolver());
        }

        public DynamicProvisioningTester(Cloud cloud, MockNameResolver nameResolver) {
            Zone zone = new Zone(cloud, SystemName.defaultSystem(),
                                 Environment.defaultEnvironment(),
                                 RegionName.defaultName());
            this.hostProvisioner = new MockHostProvisioner(flavors.getFlavors(), nameResolver, 0);
            this.provisioningTester = new ProvisioningTester.Builder().zone(zone)
                                                                      .flavors(flavors.getFlavors())
                                                                      .nameResolver(nameResolver)
                                                                      .flagSource(flagSource)
                                                                      .hostProvisioner(hostProvisioner)
                                                                      .build();
            this.nodeRepository = provisioningTester.nodeRepository();
            this.capacityMaintainer = new HostCapacityMaintainer(
                    nodeRepository, Duration.ofDays(1), hostProvisioner, flagSource, new TestMetric());
            this.resumeProvisioner = new HostResumeProvisioner(nodeRepository, Duration.ofDays(1), new TestMetric(), hostProvisioner);
            this.deprovisioner = new HostDeprovisioner(nodeRepository, Duration.ofDays(1), new TestMetric(), hostProvisioner);

            MockDuperModel mockDuperModel = new MockDuperModel()
                    .support(configServerHostApp).support(tenantHostApp);
            this.infraDeployer = new InfraDeployerImpl(nodeRepository, provisioningTester.provisioner(), mockDuperModel);
        }

        private DynamicProvisioningTester addInitialNodes() {
            List.of(createNode("host1", Optional.empty(), NodeType.host, Node.State.active, tenantHostApp.getApplicationId()),
                    createNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, tenantApp),
                    createNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, tenantApp),
                    createNode("host2", Optional.empty(), NodeType.host, Node.State.failed, tenantHostApp.getApplicationId()),
                    createNode("host2-1", Optional.of("host2"), NodeType.tenant, Node.State.failed, tenantApp),
                    createNode("host3", Optional.empty(), NodeType.host, Node.State.provisioned, null,
                            "host3-1", "host3-2", "host3-3", "host3-4", "host3-5"),
                    createNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, null),
                    createNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, tenantApp),
                    createNode("host4-2", Optional.of("host4"), NodeType.tenant, Node.State.reserved, tenantApp))
                .forEach(node -> nodeRepository.database().addNodesInState(List.of(node), node.state(), Agent.system));
            return this;
        }

        private Node addCfghost(int index, boolean makeChild) {
            Node cfghost = addNode("cfghost" + index, Optional.empty(), NodeType.confighost,
                    Node.State.active, configServerHostApp.getApplicationId());

            if (makeChild) {
                addNode("cfg" + index, Optional.of("cfghost" + index), NodeType.config,
                        Node.State.active, configServerApp.getApplicationId());
            }

            return cfghost;
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state) {
            return addNode(hostname, parentHostname, nodeType, state, null);
        }

        private Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, ApplicationId application) {
            Node node = createNode(hostname, parentHostname, nodeType, state, application);
            return nodeRepository.database().addNodesInState(List.of(node), node.state(), Agent.system).get(0);
        }

        private Node createNode(String hostname, Optional<String> parentHostname, NodeType nodeType,
                                Node.State state, ApplicationId application, String... additionalHostnames) {
            Flavor flavor = nodeRepository.flavors().getFlavor(parentHostname.isPresent() ? "docker" : "host3").orElseThrow();
            Optional<Allocation> allocation = Optional.ofNullable(application)
                    .map(app -> new Allocation(
                            app,
                            ClusterMembership.from("container/default/0/0", Version.fromString("7.3"), Optional.empty()),
                            flavor.resources(),
                            Generation.initial(),
                            false));
            List<Address> addresses = Stream.of(additionalHostnames).map(Address::new).toList();
            Node.Builder builder = Node.create("fake-id-" + hostname, hostname, flavor, state, nodeType)
                    .ipConfig(new IP.Config(state == Node.State.active ? Set.of("::1") : Set.of(), Set.of(), addresses));
            parentHostname.ifPresent(builder::parentHostname);
            allocation.ifPresent(builder::allocation);
            if (hostname.equals("host2-1"))
                builder.status(Status.initial().withWantToRetire(true, true, false));
            return builder.build();
        }

        private long provisionedHostsMatching(NodeResources resources) {
            return hostProvisioner.provisionedHosts().stream()
                                  .filter(host -> host.generateHost().resources().compatibleWith(resources))
                                  .count();
        }

        private void assertNodesUnchanged() {
            NodeList nodes = nodeRepository.nodes().list();
            maintain();
            assertEquals("Nodes are unchanged after maintenance run", nodes, nodeRepository.nodes().list());
        }

        private void maintain() {
            resumeProvisioner.maintain();
            capacityMaintainer.maintain();
            infraDeployer.activateAllSupportedInfraApplications(true);
            deprovisioner.maintain();
        }

    }

}

