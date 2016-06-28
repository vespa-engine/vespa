// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.nodes.NodeRepositoryConfig;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.io.IOException;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

/**
 * A test utility for provisioning tests.
 *
 * @author bratseth
 */
public class ProvisioningTester implements AutoCloseable {

    private Curator curator = new MockCurator();
    private NodeFlavors nodeFlavors;
    private ManualClock clock;
    private NodeRepository nodeRepository;
    private NodeRepositoryProvisioner provisioner;
    private CapacityPolicies capacityPolicies;
    private ProvisionLogger provisionLogger;

    public ProvisioningTester(Zone zone) {
        try {
            nodeFlavors = new NodeFlavors(createConfig());
            clock = new ManualClock();
            nodeRepository = new NodeRepository(nodeFlavors, curator, clock);
            provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone, clock);
            capacityPolicies = new CapacityPolicies(zone, nodeFlavors);
            provisionLogger = new NullProvisionLogger();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private NodeRepositoryConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 4., 100, "BARE_METAL");
        b.addFlavor("small", 1., 2., 50, "BARE_METAL");
        b.addFlavor("docker1", 1., 1., 10, "DOCKER_CONTAINER");
        b.addFlavor("v-4-8-100", 4., 8., 100, "VIRTUAL_MACHINE");
        b.addFlavor("old-large1", 2., 4., 100, "BARE_METAL");
        b.addFlavor("old-large2", 2., 5., 100, "BARE_METAL");
        NodeRepositoryConfig.Flavor.Builder large = b.addFlavor("large", 4., 8., 100, "BARE_METAL");
        b.addReplaces("old-large1", large);
        b.addReplaces("old-large2", large);
        NodeRepositoryConfig.Flavor.Builder largeVariant = b.addFlavor("large-variant", 3., 9., 101, "BARE_METAL");
        b.addReplaces("large", largeVariant);
        NodeRepositoryConfig.Flavor.Builder largeVariantVariant = b.addFlavor("large-variant-variant", 4., 9., 101, "BARE_METAL");
        b.addReplaces("large-variant", largeVariantVariant);
        return b.build();
    }

    private NodeRepositoryConfig.Flavor.Builder addFlavor(String flavorName, NodeRepositoryConfig.Builder b) {
        NodeRepositoryConfig.Flavor.Builder flavor = new NodeRepositoryConfig.Flavor.Builder();
        flavor.name(flavorName);
        b.flavor(flavor);
        return flavor;
    }

    private void addReplaces(String replaces, NodeRepositoryConfig.Flavor.Builder flavor) {
        NodeRepositoryConfig.Flavor.Replaces.Builder flavorReplaces = new NodeRepositoryConfig.Flavor.Replaces.Builder();
        flavorReplaces.name(replaces);
        flavor.replaces(flavorReplaces);
    }

    @Override
    public void close() throws IOException {
        //testingServer.close();
    }

    public void advanceTime(TemporalAmount duration) { clock.advance(duration); }
    public NodeRepository nodeRepository() { return nodeRepository; }
    public ManualClock clock() { return clock; }
    public NodeRepositoryProvisioner provisioner() { return provisioner; }
    public CapacityPolicies capacityPolicies() { return capacityPolicies; }
    public NodeList getNodes(ApplicationId id, Node.State ... inState) { return new NodeList(nodeRepository.getNodes(id, inState)); }

    public void patchNode(Node node) { nodeRepository.write(node); }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, int groups, String flavor) {
        return prepare(application, cluster, Capacity.fromNodeCount(nodeCount, Optional.ofNullable(flavor)), groups);
    }
    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity capacity, int groups) {
        if (capacity.nodeCount() == 0) return Collections.emptyList();
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

    public Set<String> toHostNames(Set<HostSpec> hosts) {
        return hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
    }

    public Set<String> toHostNames(List<Node> nodes) {
        return nodes.stream().map(Node::hostname).collect(Collectors.toSet());
    }

    /**
     * Asserts that each active node in this application has a restart count equaling the
     * number of matches to the given filters
     */
    public void assertRestartCount(ApplicationId application, HostFilter... filters) {
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
        Node failedNode = nodeRepository.fail(host.hostname());
        assertTrue(nodeRepository.getNodes(Node.Type.tenant, Node.State.failed).contains(failedNode));
        assertEquals(beforeFailCount + 1, failedNode.status().failCount());
    }

    public void assertMembersOf(ClusterSpec requestedCluster, Collection<HostSpec> hosts) {
        Set<Integer> indices = new HashSet<>();
        for (HostSpec host : hosts) {
            ClusterSpec nodeCluster = host.membership().get().cluster();
            assertTrue(requestedCluster.equalsIgnoringGroup(nodeCluster));
            if (requestedCluster.group().isPresent())
                assertEquals(requestedCluster.group(), nodeCluster.group());
            else
                assertEquals("0", nodeCluster.group().get().value());

            indices.add(host.membership().get().index());
        }
        assertEquals("Indexes in " + requestedCluster + " are disjunct", hosts.size(), indices.size());
    }

    public HostSpec removeOne(Set<HostSpec> hosts) {
        Iterator<HostSpec> i = hosts.iterator();
        HostSpec removed = i.next();
        i.remove();
        return removed;
    }

    public ApplicationId makeApplicationId() {
        return ApplicationId.from(
                TenantName.from(UUID.randomUUID().toString()),
                ApplicationName.from(UUID.randomUUID().toString()),
                InstanceName.from(UUID.randomUUID().toString()));
    }

    public List<Node> makeReadyNodes(int n, String flavor) {
        List<Node> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(),
                                                UUID.randomUUID().toString(),
                                                Optional.empty(),
                                                new Configuration(nodeFlavors.getFlavorOrThrow(flavor)), Node.Type.tenant));
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
        return nodes;
    }

    /** Creates a set of virtual docker nodes on a single docker host */
    public List<Node> makeReadyDockerNodes(int n, String flavor, String dockerHostId) {
        return makeReadyVirtualNodes(n, flavor, Optional.of(dockerHostId));
    }

    /** Creates a set of virtual nodes on a single parent host */
    public List<Node> makeReadyVirtualNodes(int n, String flavor, Optional<String> parentHostId) {
        List<Node> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final String hostname = UUID.randomUUID().toString();
            nodes.add(nodeRepository.createNode("openstack-id", hostname, parentHostId,
                    new Configuration(nodeFlavors.getFlavorOrThrow(flavor)), Node.Type.tenant));
        }
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
        return nodes;
    }

    public List<Node> makeReadyVirtualNodes(int n, String flavor, String parentHostId) {
        return makeReadyVirtualNodes(n, flavor, Optional.of(parentHostId));
    }

    /** Returns the hosts from the input list which are not retired */
    public List<HostSpec> nonretired(Collection<HostSpec> hosts) {
        return hosts.stream().filter(host -> ! host.membership().get().retired()).collect(Collectors.toList());
    }

    private static class NullProvisionLogger implements ProvisionLogger {

        @Override
        public void log(Level level, String message) {
        }

    }

}
