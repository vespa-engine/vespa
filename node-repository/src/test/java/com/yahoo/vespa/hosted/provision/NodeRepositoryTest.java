package com.yahoo.vespa.hosted.provision;

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
        assertEquals(0, tester.getNodes(Node.Type.tenant).size());

        tester.addNode("id1", "host1", "default", Node.Type.tenant);
        tester.addNode("id2", "host2", "default", Node.Type.tenant);
        tester.addNode("id3", "host3", "default", Node.Type.tenant);

        assertEquals(3, tester.getNodes(Node.Type.tenant).size());
        
        tester.nodeRepository().move("host2", Node.State.parked);
        assertTrue(tester.nodeRepository().remove("host2"));

        assertEquals(2, tester.getNodes(Node.Type.tenant).size());
    }
    
}
