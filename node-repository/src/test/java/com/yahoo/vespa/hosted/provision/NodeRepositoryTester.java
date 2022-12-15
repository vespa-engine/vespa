// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.autoscale.MemoryMetricsDb;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.EmptyProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author bratseth
 */
public class NodeRepositoryTester {

    private final NodeFlavors nodeFlavors;
    private final NodeRepository nodeRepository;
    private final ManualClock clock;
    private final MockCurator curator;

    public NodeRepositoryTester() {
        nodeFlavors = new NodeFlavors(createConfig());
        clock = new ManualClock();
        curator = new MockCurator();
        curator.setZooKeeperEnsembleConnectionSpec("server1:1234,server2:5678");
        nodeRepository = new NodeRepository(nodeFlavors,
                                            new EmptyProvisionServiceProvider(),
                                            curator,
                                            clock,
                                            Zone.defaultZone(),
                                            new MockNameResolver().mockAnyLookup(),
                                            DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"),
                                            Optional.empty(),
                                            Optional.empty(),
                                            new InMemoryFlagSource(),
                                            new MemoryMetricsDb(clock),
                                            new OrchestratorMock(),
                                            true,
                                            0, 1000);
    }
    
    public NodeRepository nodeRepository() { return nodeRepository; }
    public MockCurator curator() { return curator; }
    
    public List<Node> getNodes(NodeType type, Node.State ... inState) {
        return nodeRepository.nodes().list(inState).nodeType(type).asList();
    }

    public Node addHost(String id, String flavor) {
        return addNode(id, id, null, nodeFlavors.getFlavorOrThrow(flavor), NodeType.host);
    }

    public Node addHost(String id, String hostname, String flavor, NodeType type) {
        return addNode(id, hostname, null, nodeFlavors.getFlavorOrThrow(flavor), type);
    }

    public Node addNode(String id, String hostname, String parentHostname, String flavor, NodeType type) {
        return addNode(id, hostname, parentHostname, nodeFlavors.getFlavorOrThrow(flavor), type);
    }

    private Node addNode(String id, String hostname, String parentHostname, Flavor flavor, NodeType type) {
        Set<String> ips = nodeRepository.nameResolver().resolveAll(hostname);
        IP.Config ipConfig = new IP.Config(ips, type.isHost() ? ips : Set.of());
        Node node = Node.create(id, ipConfig, hostname, flavor, type).parentHostname(parentHostname).build();
        return nodeRepository.nodes().addNodes(List.of(node), Agent.system).get(0);
    }

    public void setNodeState(String hostname, Node.State state) {
        setNodeState(nodeRepository.nodes().node(hostname).orElseThrow(RuntimeException::new), state);
    }

    /**
     * Moves a node directly to the given state without doing any validation, useful
     * to create wanted test scenario without having to move every node through series
     * of valid state transitions
     */
    public void setNodeState(Node node, Node.State state) {
        nodeRepository.database().writeTo(state, node, Agent.system, Optional.empty());
    }

    private FlavorsConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 4., 100, 10, Flavor.Type.BARE_METAL).cost(3);
        b.addFlavor("small", 1., 2., 50, 5, Flavor.Type.BARE_METAL).cost(2);
        b.addFlavor("docker", 1., 2., 50, 1, Flavor.Type.DOCKER_CONTAINER).cost(1);
        return b.build();
    }

    public ManualClock clock() { return clock; }

}
