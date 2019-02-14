package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NetworkPorts;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.createNode;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.proxyApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.proxyHostApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.tenantApp;
import static com.yahoo.vespa.hosted.provision.maintenance.HostProvisionMaintainerTest.HostProvisionerTester.tenantHostApp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class HostProvisionMaintainerTest {

    private final HostProvisionerTester tester = new HostProvisionerTester();
    private final HostProvisioner hostProvisioner = mock(HostProvisioner.class);
    private final HostProvisionMaintainer maintainer = new HostProvisionMaintainer(
            tester.nodeRepository(), Duration.ofDays(1), tester.jobControl(), hostProvisioner);

    @Test
    public void delegates_to_host_provisioner_and_writes_back_result() {
        tester.addNode("host1", Optional.empty(), NodeType.host, Node.State.active, Optional.of(tenantHostApp));
        tester.addNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));
        tester.addNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, Optional.empty());
        tester.addNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp));

        Node host4 = tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());
        Node host41 = tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));

        Node host4new = host4.withIpAddresses(Set.of("::2"));
        Node host41new = host41.withIpAddresses(Set.of("::4", "10.0.0.1"));
        assertTrue(Stream.of(host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));
        when(hostProvisioner.provision(eq(host4), eq(Set.of(host41)))).thenReturn(List.of(host4new, host41new));

        maintainer.maintain();
        verify(hostProvisioner).provision(eq(host4), eq(Set.of(host41)));
        verifyNoMoreInteractions(hostProvisioner);

        assertEquals(Optional.of(host4new), tester.nodeRepository().getNode("host4"));
        assertEquals(Optional.of(host41new), tester.nodeRepository().getNode("host4-1"));
    }

    @Test
    public void correctly_fails_if_irrecoverable_failure() {
        Node host4 = tester.addNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());
        Node host41 = tester.addNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));

        assertTrue(Stream.of(host4, host41).map(Node::ipAddresses).allMatch(Set::isEmpty));
        when(hostProvisioner.provision(eq(host4), eq(Set.of(host41)))).thenThrow(new FatalProvisioningException("Fatal"));

        maintainer.maintain();

        assertEquals(Set.of("host4", "host4-1"),
                tester.nodeRepository().getNodes(Node.State.failed).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    @Test
    public void finds_nodes_that_need_provisioning() {
        Node host4 = createNode("host4", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty());
        Node host41 = createNode("host4-1", Optional.of("host4"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp));

        List<Node> nodes = List.of(
                createNode("host1", Optional.empty(), NodeType.host, Node.State.active, Optional.of(tenantHostApp)),
                createNode("host1-1", Optional.of("host1"), NodeType.tenant, Node.State.reserved, Optional.of(tenantApp)),
                createNode("host1-2", Optional.of("host1"), NodeType.tenant, Node.State.failed, Optional.empty()),

                createNode("host2", Optional.empty(), NodeType.host, Node.State.failed, Optional.of(tenantApp)),

                createNode("host3", Optional.empty(), NodeType.host, Node.State.provisioned, Optional.empty()),

                host4, host41,

                createNode("proxyhost1", Optional.empty(), NodeType.proxyhost, Node.State.provisioned, Optional.empty()),

                createNode("proxyhost2", Optional.empty(), NodeType.proxyhost, Node.State.active, Optional.of(proxyHostApp)),
                createNode("proxy2", Optional.of("proxyhost2"), NodeType.proxy, Node.State.active, Optional.of(proxyApp)));

        Map<Node, Set<Node>> expected = Map.of(host4, Set.of(host41));
        Map<Node, Set<Node>> actual = HostProvisionMaintainer.candidates(new NodeList(nodes));
        assertEquals(expected, actual);
    }

    static class HostProvisionerTester {
        private static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "docker");
        static final ApplicationId tenantApp = ApplicationId.from("mytenant", "myapp", "default");
        static final ApplicationId tenantHostApp = ApplicationId.from("vespa", "tenant-host", "default");
        static final ApplicationId proxyHostApp = ApplicationId.from("vespa", "proxy-host", "default");
        static final ApplicationId proxyApp = ApplicationId.from("vespa", "proxy", "default");

        private final ManualClock clock = new ManualClock();
        private final NodeRepository nodeRepository = new NodeRepository(
                nodeFlavors, new MockCurator(), clock, Zone.defaultZone(), new MockNameResolver().mockAnyLookup(), new DockerImage("docker-image"), true);

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
                            Generation.initial(),
                            false));
            Set<String> ips = state == Node.State.active ? Set.of("::1") : Set.of();
            return new Node("fake-id-" + hostname, ips, Set.of(), hostname,
                    parentHostname, flavor, Status.initial(), state, allocation, History.empty(), nodeType, new Reports(), Optional.empty(), NetworkPorts.empty());
        }

        NodeRepository nodeRepository() {
            return nodeRepository;
        }

        JobControl jobControl() {
            return new JobControl(nodeRepository.database());
        }
    }
}
