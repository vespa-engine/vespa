package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeRebooterTest {

    @Test
    public void testRebootScheduling() throws InterruptedException {
        MaintenanceTester tester = new MaintenanceTester();
        tester.createReadyTenantNodes(15);
        tester.createReadyHostNodes(15);
        
        NodeRebooter rebooter = new NodeRebooter(tester.nodeRepository, tester.clock, Duration.ofMinutes(250));
        // No nodes have a reboot event - reboots should be scheduled for most nodes during 10 invocations
        // (the rebooter run interval is 25 minutes).
        maintenanceIterations(rebooter, tester, 5);
        assertEquals("About half of the nodes have reboot scheduled",
                     6,
                     withCurrentRebootGeneration(1L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());

        maintenanceIterations(rebooter, tester, 5);
        assertEquals("Most nodes have reboot scheduled", 
                     13, 
                     withCurrentRebootGeneration(1L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
        assertEquals("No nodes have 2 reboots scheduled",
                     0,
                     withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
        assertEquals("Host nodes are not rebooted",
                     0,
                     withCurrentRebootGeneration(1L, tester.nodeRepository.getNodes(NodeType.host, Node.State.ready)).size());

        maintenanceIterations(rebooter, tester, 11);
        assertEquals("Reboot interval is 10x iteration interval, so the same number of nodes are now rebooted twice",
                     13,
                     withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
        assertEquals("The last 2 nodes have had their first reboot",
                     2,
                     withCurrentRebootGeneration(1L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
    }
    
    private void maintenanceIterations(NodeRebooter rebooter, MaintenanceTester tester, int iterations) {
        for (int i = 0; i < iterations; i++) {
            rebooter.maintain();
            tester.clock.advance(Duration.ofMinutes(25));
            simulateReboot(tester);
        }
    }
    
    /** Set current reboot generation to the wanted reboot generation whenever it is larger (i.e record a reboot) */
    private void simulateReboot(MaintenanceTester tester) {
        for (Node node : tester.nodeRepository.getNodes(Node.State.ready, Node.State.active)) {
            if (node.status().reboot().wanted() > node.status().reboot().current())
                tester.nodeRepository.write(node.withCurrentRebootGeneration(node.status().reboot().wanted(), 
                                                                             tester.clock.instant()));
        }
    }
    
    /** Returns the subset of the give nodes which have the given current reboot generation */
    private List<Node> withCurrentRebootGeneration(long generation, List<Node> nodes) {
        return nodes.stream().filter(n -> n.status().reboot().current() == generation).collect(Collectors.toList());
    }

}
