// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.custom.PreprovisionCapacity;
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
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerTester.createNode;
import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerTester.proxyApp;
import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerTester.proxyHostApp;
import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerTester.tenantApp;
import static com.yahoo.vespa.hosted.provision.maintenance.DynamicProvisioningMaintainerTest.HostProvisionerTester.tenantHostApp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * @author freva
 */
public class DynamicProvisioningMaintainerTest {

    private final HostProvisionerTester tester = new HostProvisionerTester();
    private final HostProvisioner hostProvisioner = mock(HostProvisioner.class);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource()
            .withBooleanFlag(Flags.ENABLE_DYNAMIC_PROVISIONING.id(), true)
            .withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(), PreprovisionCapacity.class);
    private final DynamicProvisioningMaintainer maintainer = new DynamicProvisioningMaintainer(
            tester.nodeRepository, Duration.ofDays(1), hostProvisioner, flagSource);

    @Test
    public void delegates_to_host_provisioner_and_writes_back_result() {
        addNodes();
        Node host4 = tester.nodeRepository.getNode("host4").orElseThrow();
        Node host41 = tester.nodeRepository.getNode("host4-1").orElseThrow();
        assertTrue(Stream.of(host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));

        Node host4new = host4.with(host4.ipConfig().with(Set.of("::2")));
        Node host41new = host41.with(host4.ipConfig().with(Set.of("::4", "10.0.0.1")));
        when(hostProvisioner.provision(eq(host4), eq(Set.of(host41)))).thenReturn(List.of(host4new, host41new));

        maintainer.updateProvisioningNodes(tester.nodeRepository.list(), () -> {});
        verify(hostProvisioner).provision(eq(host4), eq(Set.of(host41)));
        verifyNoMoreInteractions(hostProvisioner);

        assertEquals(Optional.of(host4new), tester.nodeRepository.getNode("host4"));
        assertEquals(Optional.of(host41new), tester.nodeRepository.getNode("host4-1"));
    }

    @Test
    public void correctly_fails_if_irrecoverable_failure() {
        Node host4 = tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());
        Node host41 = tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));

        assertTrue(Stream.of(host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));
        when(hostProvisioner.provision(eq(host4), eq(Set.of(host41)))).thenThrow(new FatalProvisioningException("Fatal"));

        maintainer.updateProvisioningNodes(tester.nodeRepository.list(), () -> {});

        assertEquals(Set.of("host4", "host4-1"),
                tester.nodeRepository.getNodes(Node.State.failed).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    @Test
    public void finds_nodes_that_need_deprovisioning_without_pre_provisioning() {
        addNodes();

        maintainer.convergeToCapacity(tester.nodeRepository.list());
        verify(hostProvisioner).deprovision(argThatLambda(node -> node.hostname().equals("host2")));
        verify(hostProvisioner).deprovision(argThatLambda(node -> node.hostname().equals("host3")));
        verifyNoMoreInteractions(hostProvisioner);
        assertFalse(tester.nodeRepository.getNode("host2").isPresent());
        assertFalse(tester.nodeRepository.getNode("host3").isPresent());
    }

    @Test
    public void does_not_deprovision_when_preprovisioning_enabled() {
        flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(new PreprovisionCapacity(1, 3, 2, 1)), PreprovisionCapacity.class);
        addNodes();

        maintainer.convergeToCapacity(tester.nodeRepository.list());
        verify(hostProvisioner).deprovision(argThatLambda(node -> node.hostname().equals("host2"))); // host2 because it is failed
        verifyNoMoreInteractions(hostProvisioner);
    }

    @Test
    public void provision_deficit_and_deprovision_excess() {
        flagSource.withListFlag(Flags.PREPROVISION_CAPACITY.id(), List.of(new PreprovisionCapacity(1, 3, 2, 1), new PreprovisionCapacity(2, 3, 2, 2)), PreprovisionCapacity.class);
        addNodes();

        maintainer.convergeToCapacity(tester.nodeRepository.list());
        assertFalse(tester.nodeRepository.getNode("host2").isPresent());
        assertTrue(tester.nodeRepository.getNode("host3").isPresent());
        verify(hostProvisioner).deprovision(argThatLambda(node -> node.hostname().equals("host2")));
        verify(hostProvisioner, times(2)).provisionHosts(argThatLambda(list -> list.size() == 1), eq(new NodeResources(2, 3, 2, 1)), any());
        verifyNoMoreInteractions(hostProvisioner);
    }

    @Test
    public void does_not_remove_if_host_provisioner_failed() {
        Node host2 = tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp));
        doThrow(new RuntimeException()).when(hostProvisioner).deprovision(eq(host2));

        maintainer.convergeToCapacity(tester.nodeRepository.list());

        assertEquals(1, tester.nodeRepository.getNodes().size());
        verify(hostProvisioner).deprovision(eq(host2));
        verifyNoMoreInteractions(hostProvisioner);
    }

    public void addNodes() {
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
                .forEach(node -> tester.nodeRepository.database().addNodesInState(List.of(node), node.state()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T argThatLambda(Predicate<T> predicate) {
        return argThat(new BaseMatcher<T>() {
            @Override public boolean matches(Object item) { return predicate.test((T) item); }
            @Override public void describeTo(Description description) { }
        });
    }

    static class HostProvisionerTester {
        private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "docker");
        static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        static final ApplicationId tenantHostApp = ApplicationId.from("vespa", "tenant-host", "default");
        static final ApplicationId proxyHostApp = ApplicationId.from("vespa", "proxy-host", "default");
        static final ApplicationId proxyApp = ApplicationId.from("vespa", "proxy", "default");

        private final ManualClock clock = new ManualClock();
        private final NodeRepository nodeRepository = new NodeRepository(
                nodeFlavors, new MockCurator(), clock, Zone.defaultZone(), new MockNameResolver().mockAnyLookup(), DockerImage.fromString("docker-image"), true);

        Node addNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, Optional<ApplicationId> application) {
            Node node = createNode(hostname, parentHostname, nodeType, state, application);
            return nodeRepository.database().addNodesInState(List.of(node), node.state()).get(0);
        }

        static Node createNode(String hostname, Optional<String> parentHostname, NodeType nodeType, Node.State state, Optional<ApplicationId> application) {
            Flavor flavor = nodeFlavors.getFlavor(parentHostname.isPresent() ? "docker" : "default").orElseThrow();
            Optional<Allocation> allocation = application
                    .map(app -> new Allocation(
                            app,
                            ClusterMembership.from("container/default/0/0", Version.fromString("7.3")),
                            flavor.resources(),
                            Generation.initial(),
                            false));
            var ipConfig = new IP.Config(state == Node.State.active ? Set.of("::1") : Set.of(), Set.of());
            return new Node("fake-id-" + hostname, ipConfig, hostname, parentHostname, flavor, Status.initial(),
                    state, allocation, History.empty(), nodeType, new Reports(), Optional.empty());
        }
    }
}