// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    public void testRebootScheduling() {
        Duration rebootInterval = Duration.ofMinutes(250);
        MaintenanceTester tester = new MaintenanceTester();
        tester.createReadyTenantNodes(15);
        tester.createReadyHostNodes(15);
        // New non-host nodes are rebooted when transitioning from dirty to ready. Advance the time so that additional
        // reboots will be performed.
        tester.clock.advance(rebootInterval);
        
        NodeRebooter rebooter = new NodeRebooter(tester.nodeRepository, tester.clock, rebootInterval);

        maintenanceIntervals(rebooter, tester, 1);
        assertEquals("All tenant nodes have reboot scheduled",
                     15,
                     withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
        assertEquals("No nodes have 2 reboots scheduled",
                     0,
                     withCurrentRebootGeneration(3L, tester.nodeRepository.getNodes(Node.State.ready)).size());

        maintenanceIntervals(rebooter, tester, 11);
        assertEquals("Reboot interval is 10x iteration interval, so tenant nodes are now rebooted 3 times",
                     15,
                     withCurrentRebootGeneration(3L, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready)).size());
        assertEquals("Reboot interval is 10x iteration interval, so host nodes are now rebooted twice",
                     15,
                     withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.host, Node.State.ready)).size());
    }
    
    private void maintenanceIntervals(NodeRebooter rebooter, MaintenanceTester tester, int iterations) {
        for (int i = 0; i < iterations; i++) {
            tester.clock.advance(Duration.ofMinutes(25));
            for (int j = 0; j < 60; j++) { // multiple runs to remove effects from the probabilistic smoothing in the reboot maintainer
                rebooter.maintain();
                simulateReboot(tester);
            }
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
