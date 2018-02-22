// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * A test utility for provisioning tests.
 *
 * @author bratseth
 */
public class ProvisioningTester {

    private final Curator curator;
    private final NodeFlavors nodeFlavors;
    private final ManualClock clock;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeRepositoryProvisioner provisioner;
    private final CapacityPolicies capacityPolicies;
    private final ProvisionLogger provisionLogger;
    private final List<AllocationSnapshot> allocationSnapshots = new ArrayList<>();

    private int nextHost = 0;
    private int nextIP = 0;

    public ProvisioningTester(Zone zone) {
        this(zone, createConfig());
    }

    public ProvisioningTester(Zone zone, FlavorsConfig config) {
        this(zone, config, new MockCurator(), new MockNameResolver().mockAnyLookup());
    }

    public ProvisioningTester(Zone zone, FlavorsConfig config, Curator curator, NameResolver nameResolver) {
        try {
            this.nodeFlavors = new NodeFlavors(config);
            this.clock = new ManualClock();
            this.curator = curator;
            this.nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone, nameResolver,
                                                     new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
            this.orchestrator = mock(Orchestrator.class);
            doThrow(new RuntimeException()).when(orchestrator).acquirePermissionToRemove(any());
            this.provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone, clock,
                    (x,y) -> allocationSnapshots.add(new AllocationSnapshot(new NodeList(x), "Provision tester", y)));
            this.capacityPolicies = new CapacityPolicies(zone, nodeFlavors);
            this.provisionLogger = new NullProvisionLogger();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static FlavorsConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 4., 100, Flavor.Type.BARE_METAL).cost(3);
        b.addFlavor("small", 1., 2., 50, Flavor.Type.BARE_METAL).cost(2);
        b.addFlavor("dockerSmall", 1., 1., 10, Flavor.Type.DOCKER_CONTAINER).cost(1);
        b.addFlavor("dockerLarge", 2., 1., 20, Flavor.Type.DOCKER_CONTAINER).cost(3);
        b.addFlavor("v-4-8-100", 4., 8., 100, Flavor.Type.VIRTUAL_MACHINE).cost(4);
        b.addFlavor("old-large1", 2., 4., 100, Flavor.Type.BARE_METAL).cost(6);
        b.addFlavor("old-large2", 2., 5., 100, Flavor.Type.BARE_METAL).cost(14);
        FlavorsConfig.Flavor.Builder large = b.addFlavor("large", 4., 8., 100, Flavor.Type.BARE_METAL).cost(10);
        b.addReplaces("old-large1", large);
        b.addReplaces("old-large2", large);
        FlavorsConfig.Flavor.Builder largeVariant = b.addFlavor("large-variant", 3., 9., 101, Flavor.Type.BARE_METAL).cost(9);
        b.addReplaces("large", largeVariant);
        FlavorsConfig.Flavor.Builder largeVariantVariant = b.addFlavor("large-variant-variant", 4., 9., 101, Flavor.Type.BARE_METAL).cost(11);
        b.addReplaces("large-variant", largeVariantVariant);
        return b.build();
    }

    public Curator getCurator() {
        return curator;
    }

    public void advanceTime(TemporalAmount duration) { clock.advance(duration); }
    public NodeRepository nodeRepository() { return nodeRepository; }
    public Orchestrator orchestrator() { return orchestrator; }
    public ManualClock clock() { return clock; }
    public NodeRepositoryProvisioner provisioner() { return provisioner; }
    public CapacityPolicies capacityPolicies() { return capacityPolicies; }
    public NodeList getNodes(ApplicationId id, Node.State ... inState) { return new NodeList(nodeRepository.getNodes(id, inState)); }

    public void patchNode(Node node) { nodeRepository.write(node); }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, int groups, String flavor) {
        return prepare(application, cluster, Capacity.fromNodeCount(nodeCount, Optional.ofNullable(flavor)), groups);
    }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity capacity, int groups) {
        Set<String> reservedBefore = toHostNames(nodeRepository.getNodes(application, Node.State.reserved));
        Set<String> inactiveBefore = toHostNames(nodeRepository.getNodes(application, Node.State.inactive));
        // prepare twice to ensure idempotence
        List<HostSpec> hosts1 = provisioner.prepare(application, cluster, capacity, groups, provisionLogger);
        List<HostSpec> hosts2 = provisioner.prepare(application, cluster, capacity, groups, provisionLogger);
        assertEquals(hosts1, hosts2);
        Set<String> newlyActivated = toHostNames(nodeRepository.getNodes(application, Node.State.reserved));
        newlyActivated.removeAll(reservedBefore);
        newlyActivated.removeAll(inactiveBefore);
        return hosts2;
    }

    public void activate(ApplicationId application, Set<HostSpec> hosts) {
        NestedTransaction transaction = new NestedTransaction();
        transaction.add(new CuratorTransaction(curator));
        provisioner.activate(transaction, application, hosts);
        transaction.commit();
        assertEquals(toHostNames(hosts), toHostNames(nodeRepository.getNodes(application, Node.State.active)));
    }

    public void deactivate(ApplicationId applicationId) {
        NestedTransaction deactivateTransaction = new NestedTransaction();
        nodeRepository.deactivate(applicationId, deactivateTransaction);
        deactivateTransaction.commit();
    }

    Set<String> toHostNames(Set<HostSpec> hosts) {
        return hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
    }

    Set<String> toHostNames(List<Node> nodes) {
        return nodes.stream().map(Node::hostname).collect(Collectors.toSet());
    }

    /**
     * Asserts that each active node in this application has a restart count equaling the
     * number of matches to the given filters
     */
    void assertRestartCount(ApplicationId application, HostFilter... filters) {
        for (Node node : nodeRepository.getNodes(application, Node.State.active)) {
            int expectedRestarts = 0;
            for (HostFilter filter : filters)
                if (NodeHostFilter.from(filter).matches(node))
                    expectedRestarts++;
            assertEquals(expectedRestarts, node.allocation().get().restartGeneration().wanted());
        }
    }

    public void fail(HostSpec host) {
        int beforeFailCount = nodeRepository.getNode(host.hostname(), Node.State.active).get().status().failCount();
        Node failedNode = nodeRepository.fail(host.hostname(), Agent.system, "Failing to unit test");
        assertTrue(nodeRepository.getNodes(NodeType.tenant, Node.State.failed).contains(failedNode));
        assertEquals(beforeFailCount + 1, failedNode.status().failCount());
    }

    void assertMembersOf(ClusterSpec requestedCluster, Collection<HostSpec> hosts) {
        Set<Integer> indices = new HashSet<>();
        for (HostSpec host : hosts) {
            ClusterSpec nodeCluster = host.membership().get().cluster();
            assertTrue(requestedCluster.equalsIgnoringGroupAndVespaVersion(nodeCluster));
            if (requestedCluster.group().isPresent())
                assertEquals(requestedCluster.group(), nodeCluster.group());
            else
                assertEquals(0, nodeCluster.group().get().index());

            indices.add(host.membership().get().index());
        }
        assertEquals("Indexes in " + requestedCluster + " are disjunct", hosts.size(), indices.size());
    }

    HostSpec removeOne(Set<HostSpec> hosts) {
        Iterator<HostSpec> i = hosts.iterator();
        HostSpec removed = i.next();
        i.remove();
        return removed;
    }

    ApplicationId makeApplicationId() {
        return ApplicationId.from(
                TenantName.from(UUID.randomUUID().toString()),
                ApplicationName.from(UUID.randomUUID().toString()),
                InstanceName.from(UUID.randomUUID().toString()));
    }

    public List<Node> makeReadyNodes(int n, String flavor) {
        return makeReadyNodes(n, flavor, NodeType.tenant);
    }

    List<Node> makeReadyNodes(int n, String flavor, NodeType type) {
        return makeReadyNodes(n, flavor, type, 0);
    }

    List<Node> makeProvisionedNodes(int n, String flavor, NodeType type, int additionalIps) {
        List<Node> nodes = new ArrayList<>(n);


        for (int i = 0; i < n; i++) {
            nextHost++;
            nextIP++;

            // One test involves two provision testers - to detect this we check if the
            // name resolver already contains the next host - if this is the case - bump the indices and move on
            String testIp = String.format("127.0.0.%d", nextIP);
            MockNameResolver nameResolver = (MockNameResolver)nodeRepository().nameResolver();
            if (nameResolver.getHostname(testIp).isPresent()) {
                nextHost += 100;
                nextIP += 100;
            }

            String hostname = String.format("host-%d.yahoo.com", nextHost);
            String ipv4 = String.format("127.0.0.%d", nextIP);
            String ipv6 = String.format("::%d", nextIP);

            nameResolver.addRecord(hostname, ipv4, ipv6);
            HashSet<String> hostIps = new HashSet<>();
            hostIps.add(ipv4);
            hostIps.add(ipv6);

            Set<String> addips = new HashSet<>();
            for (int ipSeq = 1; ipSeq < additionalIps; ipSeq++) {
                nextIP++;
                String ipv6node = String.format("::%d", nextIP);
                addips.add(ipv6node);
                nameResolver.addRecord(String.format("node-%d-of-%s",ipSeq, hostname), ipv6node);
            }

            nodes.add(nodeRepository.createNode(hostname,
                    hostname,
                    hostIps,
                    addips,
                    Optional.empty(),
                    nodeFlavors.getFlavorOrThrow(flavor),
                    type));
        }
        nodes = nodeRepository.addNodes(nodes);
        return nodes;
    }

    List<Node> makeReadyNodes(int n, String flavor, NodeType type, int additionalIps) {
        List<Node> nodes = makeProvisionedNodes(n, flavor, type, additionalIps);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        return nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    /** Creates a set of virtual docker nodes on a single docker host */
    List<Node> makeReadyDockerNodes(int n, String flavor, String dockerHostId) {
        return makeReadyVirtualNodes(n, flavor, Optional.of(dockerHostId));
    }

    /** Creates a set of virtual nodes on a single parent host */
    List<Node> makeReadyVirtualNodes(int n, String flavor, Optional<String> parentHostId) {
        List<Node> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final String hostname = UUID.randomUUID().toString();
            nodes.add(nodeRepository.createNode("openstack-id", hostname, parentHostId,
                                                nodeFlavors.getFlavorOrThrow(flavor), NodeType.tenant));
        }
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
        return nodes;
    }

    List<Node> makeReadyVirtualNodes(int n, String flavor, String parentHostId) {
        return makeReadyVirtualNodes(n, flavor, Optional.of(parentHostId));
    }

    /** Returns the hosts from the input list which are not retired */
    List<HostSpec> nonRetired(Collection<HostSpec> hosts) {
        return hosts.stream().filter(host -> ! host.membership().get().retired()).collect(Collectors.toList());
    }

    void assertNumberOfNodesWithFlavor(List<HostSpec> hostSpecs, String flavor, int expectedCount) {
        long actualNodesWithFlavor = hostSpecs.stream()
                .map(HostSpec::hostname)
                .map(this::getNodeFlavor)
                .map(Flavor::name)
                .filter(name -> name.equals(flavor))
                .count();
        assertEquals(expectedCount, actualNodesWithFlavor);
    }

    private Flavor getNodeFlavor(String hostname) {
        return nodeRepository.getNode(hostname).map(Node::flavor).orElseThrow(() -> new RuntimeException("No flavor for host " + hostname));
    }

    public static Set<HostSpec> toHostSpecs(List<Node> nodes) {
        return nodes.stream()
                .map(node -> new HostSpec(node.hostname(), node.allocation().map(Allocation::membership)))
                .collect(Collectors.toSet());
    }

    private static class NullProvisionLogger implements ProvisionLogger {

        @Override
        public void log(Level level, String message) {
        }

    }

}
