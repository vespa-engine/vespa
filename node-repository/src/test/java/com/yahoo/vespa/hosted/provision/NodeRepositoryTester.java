// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class NodeRepositoryTester {

    private final NodeFlavors nodeFlavors;
    private final NodeRepository nodeRepository;
    private final Clock clock;
    private final MockCurator curator;
    
    
    public NodeRepositoryTester() {
        nodeFlavors = new NodeFlavors(createConfig());
        clock = new ManualClock();
        curator = new MockCurator();
        curator.setZooKeeperEnsembleConnectionSpec("server1:1234,server2:5678");
        nodeRepository = new NodeRepository(nodeFlavors, curator, clock, Zone.defaultZone(),
                                            new MockNameResolver().mockAnyLookup(),
                                            new DockerImage("docker-registry.domain.tld:8080/dist/vespa"),
                                            true);
    }
    
    public NodeRepository nodeRepository() { return nodeRepository; }
    public MockCurator curator() { return curator; }
    
    public List<Node> getNodes(NodeType type, Node.State ... inState) {
        return nodeRepository.getNodes(type, inState);
    }
    
    public Node addNode(String id, String hostname, String flavor, NodeType type) {
        Node node = nodeRepository.createNode(id, hostname, Optional.empty(), 
                                              nodeFlavors.getFlavorOrThrow(flavor), type);
        return nodeRepository.addNodes(Collections.singletonList(node)).get(0);
    }

    public Node addNode(String id, String hostname, String parentHostname, String flavor, NodeType type) {
        Node node = nodeRepository.createNode(id, hostname, Optional.of(parentHostname),
                nodeFlavors.getFlavorOrThrow(flavor), type);
        return nodeRepository.addNodes(Collections.singletonList(node)).get(0);
    }

    private FlavorsConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 4., 100, Flavor.Type.BARE_METAL).cost(3);
        b.addFlavor("small", 1., 2., 50, Flavor.Type.BARE_METAL).cost(2);
        b.addFlavor("docker", 1., 2., 50, Flavor.Type.DOCKER_CONTAINER).cost(1);
        return b.build();
    }

}
