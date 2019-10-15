// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
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

        scheduleOsUpgrade(tester);
        maintenanceIntervals(rebooter, tester, 8);
        assertEquals(15, withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.host, Node.State.ready)).size());
        simulateOsUpgrade(tester);
        maintenanceIntervals(rebooter, tester, 1);
        assertEquals("Host nodes are not rebooted as they recently rebooted due to OS upgrade",
                     15, withCurrentRebootGeneration(2L, tester.nodeRepository.getNodes(NodeType.host, Node.State.ready)).size());
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
                                                                             tester.clock.instant()), () -> {});
        }
    }

    /** Schedule OS upgrade for all host nodes */
    private void scheduleOsUpgrade(MaintenanceTester tester) {
        tester.nodeRepository.osVersions().setTarget(NodeType.host, Version.fromString("7.0"), false);
    }

    /** Simulate completion of an OS upgrade */
    private void simulateOsUpgrade(MaintenanceTester tester) {
        var wantedOsVersion = tester.nodeRepository.osVersions().targetFor(NodeType.host);
        if (wantedOsVersion.isEmpty()) return;
        for (Node node : tester.nodeRepository.getNodes(Node.State.ready, Node.State.active)) {
            if (wantedOsVersion.get().version().isAfter(node.status().osVersion().orElse(Version.emptyVersion)))
                tester.nodeRepository.write(node.withCurrentOsVersion(wantedOsVersion.get().version(),
                                                                      tester.clock.instant()), () -> {
                });
        }
    }
    
    /** Returns the subset of the given nodes which have the given current reboot generation */
    private List<Node> withCurrentRebootGeneration(long generation, List<Node> nodes) {
        return nodes.stream().filter(n -> n.status().reboot().current() == generation).collect(Collectors.toList());
    }

    /** Returns the subset of the given nodes which have the given current OS version */
    private List<Node> withOsVersion(Version version, List<Node> nodes) {
        return nodes.stream().filter(n -> n.status().osVersion().isPresent() &&
                                          n.status().osVersion().get().equals(version))
                    .collect(Collectors.toList());
    }

}
