package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.NodeType;
import org.junit.Test;

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
        NodeRepositoryTester tester = new NodeRepositoryTester();
        assertEquals(0, tester.getNodes(NodeType.tenant).size());

        tester.addNode("id1", "host1", "default", NodeType.tenant);
        tester.addNode("id2", "host2", "default", NodeType.tenant);
        tester.addNode("id3", "host3", "default", NodeType.tenant);

        assertEquals(3, tester.getNodes(NodeType.tenant).size());
        
        tester.nodeRepository().park("host2");
        assertTrue(tester.nodeRepository().remove("host2"));

        assertEquals(2, tester.getNodes(NodeType.tenant).size());
    }
    
}
