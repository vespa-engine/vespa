package com.yahoo.vespa.hosted.provision;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.nodes.NodeRepositoryConfig;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;
import org.junit.Test;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * tests basic operation of the node repository
 * 
 * @author bratseth
 */
public class NodeRepositoryTest {

    @Test
    public void nodeRepositoryTest() {
        NodeFlavors nodeFlavors = new NodeFlavors(createConfig());
        Clock clock = new ManualClock();
        MockCurator curator = new MockCurator();
        curator.setConnectionSpec("server1:1234,server2:5678");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock);

        assertEquals(0, nodeRepository.getNodes(Node.Type.tenant).size());

        List<Node> nodes = new ArrayList<>();
        nodes.add(nodeRepository.createNode("id1", "host1", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodes.add(nodeRepository.createNode("id2", "host2", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodes.add(nodeRepository.createNode("id3", "host3", Optional.empty(), new Configuration(nodeFlavors.getFlavorOrThrow("default")), Node.Type.tenant));
        nodeRepository.addNodes(nodes);

        assertEquals(3, nodeRepository.getNodes(Node.Type.tenant).size());
        assertEquals(asSet("host1,host2,host3,server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));
        
        nodeRepository.move("host2", Node.State.parked);
        assertTrue(nodeRepository.remove("host2"));

        assertEquals(2, nodeRepository.getNodes(Node.Type.tenant).size());
        assertEquals(asSet("host1,host3,server1,server2"), asSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_CLIENTS_PROPERTY)));
    }
    
    private Set<String> asSet(String s) {
        return new HashSet<>(Arrays.asList(s.split(",")));
    }

    private NodeRepositoryConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 4., 100, "BARE_METAL").cost(3);
        b.addFlavor("small", 1., 2., 50, "BARE_METAL").cost(2);
        return b.build();
    }

}
